package se.exuvo.aurora.planetarysystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.EntitySubscription.SubscriptionListener
import com.artemis.systems.IteratingSystem
import com.artemis.utils.IntBag
import net.mostlyoriginal.api.event.common.EventSystem
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.empires.components.WeaponsComponent
import se.exuvo.aurora.galactic.AmmunitionPart
import se.exuvo.aurora.galactic.BeamWeapon
import se.exuvo.aurora.galactic.ChargedPart
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.MissileLauncher
import se.exuvo.aurora.galactic.PoweredPart
import se.exuvo.aurora.galactic.Railgun
import se.exuvo.aurora.galactic.ReloadablePart
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.planetarysystems.components.AmmunitionPartState
import se.exuvo.aurora.planetarysystems.components.ChargedPartState
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.planetarysystems.components.PoweredPartState
import se.exuvo.aurora.planetarysystems.components.ReloadablePartState
import se.exuvo.aurora.planetarysystems.components.ShipComponent
import se.exuvo.aurora.planetarysystems.components.TargetingComputerState
import se.exuvo.aurora.planetarysystems.components.UUIDComponent
import se.exuvo.aurora.planetarysystems.events.PowerEvent
import se.exuvo.aurora.planetarysystems.events.getEvent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.forEach

class WeaponSystem : IteratingSystem(FAMILY), PreSystem {
	companion object {
		val FAMILY = Aspect.all(WeaponsComponent::class.java)
		val SHIP_FAMILY = Aspect.all(ShipComponent::class.java)
	}

	val log = LogManager.getLogger(this.javaClass)

	lateinit private var weaponsComponentMapper: ComponentMapper<WeaponsComponent>
	lateinit private var shipMapper: ComponentMapper<ShipComponent>
	lateinit private var uuidMapper: ComponentMapper<UUIDComponent>
	lateinit private var nameMapper: ComponentMapper<NameComponent>
	lateinit private var events: EventSystem

	private val galaxy = GameServices[Galaxy::class]

	override fun initialize() {
		super.initialize()

		world.getAspectSubscriptionManager().get(SHIP_FAMILY).addSubscriptionListener(object : SubscriptionListener {
			override fun inserted(entities: IntBag) {
				entities.forEach { entityID ->
					var weaponsComponent = weaponsComponentMapper.get(entityID)

					if (weaponsComponent == null) {

						val ship = shipMapper.get(entityID)
						val targetingComputers = ship.hull[TargetingComputer::class]

						if (targetingComputers.isNotEmpty()) {
							weaponsComponent = weaponsComponentMapper.create(entityID)
							weaponsComponent.targetingComputers = targetingComputers

							targetingComputers.forEach({
								val tc = it
								if (ship.isPartEnabled(tc)) {
									val poweredState = ship.getPartState(tc)[PoweredPartState::class]
									poweredState.requestedPower = tc.part.powerConsumption
								}
							})
						}
					}
				}
			}

			override fun removed(entities: IntBag) {}
		})
	}

	override fun preProcessSystem() {
		subscription.getEntities().forEach { entityID ->
			val ship = shipMapper.get(entityID)
			val weaponsComponent = weaponsComponentMapper.get(entityID)
			val tcs = weaponsComponent.targetingComputers

			for (tc in tcs) {
				val tcState = ship.getPartState(tc)[TargetingComputerState::class]

				if (tcState.target != null && tcState.lockCompletionAt == 0L) { // Start targeting
					tcState.lockCompletionAt = galaxy.time + tc.part.lockingTime

				} else if (tcState.target == null && tcState.lockCompletionAt != 0L) { // Stop targeting
					tcState.lockCompletionAt = 0
				}

				// Reload ammo weapons
				for (weapon in tcState.linkedWeapons) {
					if (ship.isPartEnabled(weapon) && weapon.part is PoweredPart) {
						val part = weapon.part
						val poweredState = ship.getPartState(weapon)[PoweredPartState::class]
						var powerWanted = tcState.target != null

						if (part is AmmunitionPart) {
							val ammoState = ship.getPartState(weapon)[AmmunitionPartState::class]

							if (ammoState.type != null) {

								if (ammoState.type!!.getRadius() != part.ammunitionSize) {
									log.warn("Wrong ammo size for $part: ${ammoState.type!!.getRadius()} != ${part.ammunitionSize}")
								}

								val freeSpace = part.ammunitionAmount - ammoState.amount

								if (freeSpace > 0) {
									val removedAmmo = ship.retrieveCargo(ammoState.type!!, freeSpace)

									if (removedAmmo > 0) {
										ammoState.amount += removedAmmo

									} else if (ammoState.amount == 0 && (!(part is ReloadablePart) || !ship.getPartState(weapon)[ReloadablePartState::class].loaded)) {

										powerWanted = false

										if (poweredState.requestedPower != 0L) {
											println("Unpowering $part due to no more ammo")
										}
									}
								}

								if (part is ReloadablePart) {

									val reloadState = ship.getPartState(weapon)[ReloadablePartState::class]

									if (!reloadState.loaded && ammoState.amount > 0) {

										if (reloadState.reloadPowerRemaining == -1L) {
											reloadState.reloadPowerRemaining = part.reloadTime * part.powerConsumption

										} else if (reloadState.reloadPowerRemaining >= 0L) {

											reloadState.reloadPowerRemaining -= Math.min(poweredState.givenPower, reloadState.reloadPowerRemaining)

											if (reloadState.reloadPowerRemaining == 0L) {
												reloadState.loaded = true
												reloadState.reloadPowerRemaining = -1
												ammoState.amount -= 1
											}
										}
									}
								}
							} else {
								powerWanted = false
							}
						}

						if (powerWanted) {

							val idlePower = (0.1 * part.powerConsumption).toLong()

							if (part is ChargedPart) {
								val chargedState = ship.getPartState(weapon)[ChargedPartState::class]

								if (chargedState.charge < part.capacitor) {
									val wantedPower = Math.min(part.powerConsumption, part.capacitor - chargedState.charge)
									if (poweredState.requestedPower != wantedPower) {
										poweredState.requestedPower = wantedPower
										events.dispatch(getEvent(PowerEvent::class).set(entityID))
									}
								} else {
									if (poweredState.requestedPower != idlePower) {
										poweredState.requestedPower = idlePower
										events.dispatch(getEvent(PowerEvent::class).set(entityID))
									}
								}

							} else if (part is ReloadablePart) {
								val reloadState = ship.getPartState(weapon)[ReloadablePartState::class]

								if (!reloadState.loaded) {
									if (poweredState.requestedPower != part.powerConsumption) {
										poweredState.requestedPower = part.powerConsumption
										events.dispatch(getEvent(PowerEvent::class).set(entityID))
									}
								} else {
									if (poweredState.requestedPower != idlePower) {
										poweredState.requestedPower = idlePower
										events.dispatch(getEvent(PowerEvent::class).set(entityID))
									}
								}
							}

						} else {

							if (poweredState.requestedPower != 0L) {
								poweredState.requestedPower = 0
								events.dispatch(getEvent(PowerEvent::class).set(entityID))

								if (part is ChargedPart) {
									val chargedState = ship.getPartState(weapon)[ChargedPartState::class]
									chargedState.charge = 0
								}
							}
						}
					}
				}
			}
		}
	}

	override fun process(entityID: Int) {

		val ship = shipMapper.get(entityID)
		val weaponsComponent = weaponsComponentMapper.get(entityID)

		val tcs = weaponsComponent.targetingComputers

		for (tc in tcs) {
			val tcState = ship.getPartState(tc)[TargetingComputerState::class]

			// Fire
			if (tcState.target != null && galaxy.time > tcState.lockCompletionAt) {

				for (weapon in tcState.linkedWeapons) {
					if (ship.isPartEnabled(weapon)) {
						val part = weapon.part

						when (part) {
							is BeamWeapon -> {
								val chargedState = ship.getPartState(weapon)[ChargedPartState::class]

								if (chargedState.charge >= part.capacitor) {
									chargedState.charge = 0

									//TODO calculate intercept angle, fire
								}
							}
							is Railgun -> {
								val ammoState = ship.getPartState(weapon)[AmmunitionPartState::class]
								val chargedState = ship.getPartState(weapon)[ChargedPartState::class]

								if (chargedState.charge >= part.capacitor && ammoState.amount > 0) {
									chargedState.charge = 0
									ammoState.amount -= 1

									//TODO calculate intercept angle, fire
									val munitionClass = ammoState.type
								}
							}
							is MissileLauncher -> {
								val ammoState = ship.getPartState(weapon)[AmmunitionPartState::class]
								val reloadState = ship.getPartState(weapon)[ReloadablePartState::class]

								if (reloadState.loaded) {
									reloadState.loaded = false

									//TODO fire
									val munitionClass = ammoState.type
								}
							}
							else -> RuntimeException("Unsupported weapon")
						}
					}
				}
			}
		}

		/* Determine closest approach
      * https://math.stackexchange.com/questions/1256660/shortest-distance-between-two-moving-points
      * https://stackoverflow.com/questions/32218356/how-to-calculate-shortest-distance-between-two-moving-objects
     */

	}
}