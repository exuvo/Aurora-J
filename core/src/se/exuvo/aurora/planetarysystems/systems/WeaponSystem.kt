package se.exuvo.aurora.planetarysystems.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntityListener
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import org.apache.log4j.Logger
import se.exuvo.aurora.empires.components.WeaponsComponent
import se.exuvo.aurora.galactic.AmmunitionPart
import se.exuvo.aurora.galactic.BeamWeapon
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.MissileLauncher
import se.exuvo.aurora.galactic.Railgun
import se.exuvo.aurora.galactic.ReloadablePart
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.planetarysystems.components.AmmunitionPartState
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.planetarysystems.components.PoweredPartState
import se.exuvo.aurora.planetarysystems.components.ReloadablePartState
import se.exuvo.aurora.planetarysystems.components.ShipComponent
import se.exuvo.aurora.planetarysystems.components.TargetingComputerState
import se.exuvo.aurora.planetarysystems.components.UUIDComponent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.planetarysystems.components.ChargedPartState
import se.exuvo.aurora.galactic.ChargedPart
import se.exuvo.aurora.galactic.PoweredPart
import se.exuvo.aurora.planetarysystems.components.PowerComponent

class WeaponSystem : IteratingSystem(FAMILY), EntityListener {
	companion object {
		val FAMILY = Family.all(WeaponsComponent::class.java).get()
		val SHIP_FAMILY = Family.all(ShipComponent::class.java).get()
	}

	val log = Logger.getLogger(this.javaClass)

	private val weaponsComponentMapper = ComponentMapper.getFor(WeaponsComponent::class.java)
	private val powerMapper = ComponentMapper.getFor(PowerComponent::class.java)
	private val shipMapper = ComponentMapper.getFor(ShipComponent::class.java)
	private val uuidMapper = ComponentMapper.getFor(UUIDComponent::class.java)
	private val nameMapper = ComponentMapper.getFor(NameComponent::class.java)

	private val galaxy = GameServices[Galaxy::class.java]

	override fun addedToEngine(engine: Engine) {
		super.addedToEngine(engine)

		engine.addEntityListener(SHIP_FAMILY, this)
	}

	override fun removedFromEngine(engine: Engine) {
		super.removedFromEngine(engine)

		engine.removeEntityListener(this)
	}

	override fun entityAdded(entity: Entity) {

		var weaponsComponent = weaponsComponentMapper.get(entity)

		if (weaponsComponent == null) {

			val ship = shipMapper.get(entity)
			val targetingComputers = ship.shipClass[TargetingComputer::class]

			if (targetingComputers.isNotEmpty()) {
				weaponsComponent = WeaponsComponent(targetingComputers)
				entity.add(weaponsComponent)

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

	override fun entityRemoved(entity: Entity) {

	}

	override fun processEntity(entity: Entity, deltaGameTime: Float) {

		val ship = shipMapper.get(entity)
		val weaponsComponent = weaponsComponentMapper.get(entity)
		val powerComponent = powerMapper.get(entity)

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
								println("Wrong ammo size for $part: ${ammoState.type!!.getRadius()} != ${part.ammunitionSize}")
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
									powerComponent.stateChanged = true
								}
							} else {
								if (poweredState.requestedPower != idlePower) {
									poweredState.requestedPower = idlePower
									powerComponent.stateChanged = true
								}
							}

						} else if (part is ReloadablePart) {
							val reloadState = ship.getPartState(weapon)[ReloadablePartState::class]
							
							if (!reloadState.loaded) {
								if (poweredState.requestedPower != part.powerConsumption) {
									poweredState.requestedPower = part.powerConsumption
									powerComponent.stateChanged = true
								}
							} else {
								if (poweredState.requestedPower != idlePower) {
									poweredState.requestedPower = idlePower
									powerComponent.stateChanged = true
								}
							}
						}
						
					} else {
						
						if (poweredState.requestedPower != 0L) {
							poweredState.requestedPower = 0
							powerComponent.stateChanged = true

							if (part is ChargedPart) {
								val chargedState = ship.getPartState(weapon)[ChargedPartState::class]
								chargedState.charge = 0
							}
						}
					}
				}
			}

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