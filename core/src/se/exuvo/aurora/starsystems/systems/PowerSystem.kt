package se.exuvo.aurora.starsystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.EntitySubscription.SubscriptionListener
import com.artemis.systems.IteratingSystem
import com.artemis.utils.IntBag
import net.mostlyoriginal.api.event.common.Subscribe
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.galactic.Battery
import se.exuvo.aurora.galactic.ChargedPart
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.PoweredPart
import se.exuvo.aurora.galactic.PoweringPart
import se.exuvo.aurora.galactic.Reactor
import se.exuvo.aurora.galactic.SolarPanel
import se.exuvo.aurora.starsystems.components.ChargedPartState
import se.exuvo.aurora.starsystems.components.FueledPartState
import se.exuvo.aurora.starsystems.components.PowerComponent
import se.exuvo.aurora.starsystems.components.PoweredPartState
import se.exuvo.aurora.starsystems.components.PoweringPartState
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.components.SolarIrradianceComponent
import se.exuvo.aurora.starsystems.events.PowerEvent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.consumeFuel
import se.exuvo.aurora.utils.forEachFast
import java.lang.IllegalStateException
import se.exuvo.aurora.galactic.PartRef
import se.exuvo.aurora.galactic.Part
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.starsystems.components.TargetingComputerState
import se.exuvo.aurora.galactic.WeaponPart
import se.exuvo.aurora.starsystems.components.WeaponPartState
import se.exuvo.aurora.galactic.AmmunitionPart
import se.exuvo.aurora.starsystems.components.AmmunitionPartState
import se.exuvo.aurora.galactic.Railgun

class PowerSystem : IteratingSystem(FAMILY), PreSystem {
	companion object {
		val FAMILY = Aspect.all(ShipComponent::class.java, PowerComponent::class.java)
		val SHIP_FAMILY = Aspect.all(ShipComponent::class.java)
	}

	val log = LogManager.getLogger(this.javaClass)

	lateinit private var shipMapper: ComponentMapper<ShipComponent>
	lateinit private var powerMapper: ComponentMapper<PowerComponent>
	lateinit private var irradianceMapper: ComponentMapper<SolarIrradianceComponent>

	private val galaxy = GameServices[Galaxy::class]

	// All solar panels assumed to be 5cm thick
	private val SOLAR_PANEL_THICKNESS = 5

	override fun initialize() {
		super.initialize()

		world.getAspectSubscriptionManager().get(SHIP_FAMILY).addSubscriptionListener(object : SubscriptionListener {
			override fun inserted(entityIDs: IntBag) {
				entityIDs.forEachFast { entityID ->
					var powerComponent = powerMapper.get(entityID)

					if (powerComponent == null) {

						val ship = shipMapper.get(entityID)

						powerComponent = powerMapper.create(entityID).set(ship.hull.powerScheme)

						ship.hull.getPartRefs().forEachFast { partRef ->
							if (partRef.part is PoweringPart) {
								powerComponent.poweringParts.add(partRef)
							}

							if (partRef.part is PoweredPart) {
								powerComponent.poweredParts.add(partRef)
							}

							if (partRef.part is ChargedPart) {
								powerComponent.chargedParts.add(partRef)
							}
						}

						powerComponent.poweringParts.sortWith(Comparator { aRef, bRef ->
							val aInt = ship.hull.powerScheme.getPowerTypeValue(aRef.part)
							val bInt = ship.hull.powerScheme.getPowerTypeValue(bRef.part)

							aInt.compareTo(bInt)
						})
					}
				}
			}

			override fun removed(entities: IntBag) {}
		})
	}
	
	fun activatePart(entityID: Int, ship: ShipComponent, partRef: PartRef<Part>) {
		
		if (ship.isPartEnabled(partRef)) {
			throw IllegalStateException();
		}
		
		ship.setPartEnabled(partRef, true)
		
		val part = partRef.part
		
		if (part is TargetingComputer) {
			
			val tcState = ship.getPartState(partRef)[TargetingComputerState::class]
			
			for (weapon in tcState.linkedWeapons) {
				
				activatePart(entityID, ship, weapon)
			}
			
		} else if (part is WeaponPart) {
			
			val weaponState = ship.getPartState(partRef)[WeaponPartState::class]
			val targetingComputer = weaponState.targetingComputer
			
			if (targetingComputer != null) {
				
				val tcState = ship.getPartState(targetingComputer)[TargetingComputerState::class]
				
				tcState.disabledWeapons.remove(partRef)
				
				if (part is ChargedPart) {
					tcState.chargingWeapons.add(partRef)
				}
				
				if (part is AmmunitionPart) {
					
					val ammoState = ship.getPartState(partRef)[AmmunitionPartState::class]
					
					if (ammoState.amount > 0 && part !is Railgun) {
						tcState.readyWeapons.add(partRef)
					}
					
					var freeSpace = part.ammunitionAmount - ammoState.amount

					if (freeSpace > 0) {
						
						if (ammoState.reloadedAt != 0L) {
							ammoState.reloadedAt += galaxy.time
						}
					
						tcState.reloadingWeapons.add(partRef)
					}
				}
			}
		}
	}
	
	fun deactivatePart(entityID: Int, ship: ShipComponent, partRef: PartRef<Part>) {
		
		if (!ship.isPartEnabled(partRef)) {
			throw IllegalStateException();
		}
		
		ship.setPartEnabled(partRef, false)
		
		val part = partRef.part
		
		if (part is PoweredPart) {
			
			val poweredState = ship.getPartState(partRef)[PoweredPartState::class]
			
			if (poweredState.requestedPower > 0) {
				poweredState.requestedPower = 0
				
				val powerComponent = powerMapper.get(entityID)
				powerComponent.stateChanged = true
			}
		}
		
		if (part is ChargedPart) {
			val chargedState = ship.getPartState(partRef)[ChargedPartState::class]
			chargedState.charge = 0
			chargedState.expectedFullAt = 0
		}
		
		if (part is TargetingComputer) {
			
			val tcState = ship.getPartState(partRef)[TargetingComputerState::class]
			
			for (weapon in tcState.linkedWeapons) {
				
				deactivatePart(entityID, ship, weapon)
			}
			
		} else if (part is WeaponPart) {
			
			val weaponState = ship.getPartState(partRef)[WeaponPartState::class]
			val targetingComputer = weaponState.targetingComputer
			
			if (targetingComputer != null) {
				
				val tcState = ship.getPartState(targetingComputer)[TargetingComputerState::class]
				
				tcState.readyWeapons.remove(partRef)
				tcState.disabledWeapons.add(partRef)
				
				if (part is ChargedPart) {
					tcState.chargingWeapons.remove(partRef)
				}
				
				if (part is AmmunitionPart) {
					tcState.reloadingWeapons.remove(partRef)
					
					val ammoState = ship.getPartState(partRef)[AmmunitionPartState::class]
					
					if (ammoState.reloadedAt != 0L) {
						ammoState.reloadedAt -= galaxy.time
					}
				}
			}
		}
	}

	@Subscribe
	fun powerEvent(event: PowerEvent) {
		val powerComponent = powerMapper.get(event.entityID)
		powerComponent.stateChanged = true
	}

	override fun preProcessSystem() {
		subscription.getEntities().forEachFast { entityID ->
			val powerComponent = powerMapper.get(entityID)
			val ship = shipMapper.get(entityID)

			val powerWasStable = powerComponent.totalAvailiablePower > powerComponent.totalUsedPower
			val powerWasSolarStable = powerComponent.totalAvailiableSolarPower > powerComponent.totalRequestedPower
			val oldTotalAvailiablePower = powerComponent.totalAvailiablePower

			// Pre checks
			powerComponent.poweringParts.forEachFast{ partRef ->
				val part = partRef.part
				val poweringState = ship.getPartState(partRef)[PoweringPartState::class]

				if (part is SolarPanel) { // Update solar power

					val irradiance = irradianceMapper.get(entityID).irradiance
					val solarPanelArea = part.volume / SOLAR_PANEL_THICKNESS
					val producedPower = (solarPanelArea * irradiance * part.efficiency) / (100 * 100)

//				println("solarPanelArea ${solarPanelArea / 100} m2, irradiance $irradiance W/m2, producedPower $producedPower W")

					if (powerWasStable && producedPower < poweringState.producedPower) {
						powerComponent.stateChanged = true
					}

					if (!powerWasSolarStable && producedPower != poweringState.producedPower) {
						powerComponent.stateChanged = true
					}

					powerComponent.totalAvailiablePower += producedPower - poweringState.availiablePower
					powerComponent.totalAvailiableSolarPower += producedPower - poweringState.availiablePower
					poweringState.availiablePower = producedPower

				} else if (part is Reactor) {

					val fueledState = ship.getPartState(partRef)[FueledPartState::class]

					val remainingFuel = ship.getCargoAmount(part.fuel)
					fueledState.totalFuelEnergyRemaining = fueledState.fuelEnergyRemaining + part.power * part.fuelTime * remainingFuel
					val availiablePower = Math.min(part.power, fueledState.totalFuelEnergyRemaining)

//					println("remainingFuel ${remainingFuel} g, max power ${part.power} W, remaining power ${remainingFuel / part.fuelConsumption} Ws")

					if (powerWasStable && availiablePower < poweringState.producedPower) {
						powerComponent.stateChanged = true
					}

					powerComponent.totalAvailiablePower += availiablePower - poweringState.availiablePower
					poweringState.availiablePower = availiablePower

				} else if (part is Battery) { // Charge empty or full

					val poweredState = ship.getPartState(partRef)[PoweredPartState::class]
					val chargedState = ship.getPartState(partRef)[ChargedPartState::class]

					// Charge empty
					if (poweringState.producedPower > chargedState.charge) {
						powerComponent.stateChanged = true
					}

					// Charge full
					if (poweredState.givenPower > 0 && chargedState.charge + (poweredState.givenPower * part.efficiency) / 100 > part.capacitor) {
						powerComponent.stateChanged = true
					}

					val availiablePower = Math.min(part.power, chargedState.charge)

					powerComponent.totalAvailiablePower += availiablePower - poweringState.availiablePower
					poweringState.availiablePower = availiablePower
				}
			}

			if (!powerComponent.stateChanged) {

				if (powerWasSolarStable && powerComponent.totalAvailiableSolarPower < powerComponent.totalRequestedPower) {
					powerComponent.stateChanged = true
				}

				if (powerWasStable) {
					if (powerComponent.totalAvailiablePower < powerComponent.totalUsedPower) {
						powerComponent.stateChanged = true
					}
				} else if (powerComponent.totalAvailiablePower != oldTotalAvailiablePower) {
					powerComponent.stateChanged = true
				}
			}

			// Calculate usage
			if (powerComponent.stateChanged) {
				powerComponent.stateChanged = false

				// Summarise availiable power
				powerComponent.totalAvailiablePower = 0
				powerComponent.totalAvailiableSolarPower = 0
				var availiableChargingPower = 0L

				powerComponent.poweringParts.forEach({
					val partRef = it
					val part = partRef.part

					if (ship.isPartEnabled(partRef)) {
						val poweringState = ship.getPartState(partRef)[PoweringPartState::class]

						powerComponent.totalAvailiablePower += poweringState.availiablePower

						if (part is SolarPanel) {
							powerComponent.totalAvailiableSolarPower += poweringState.availiablePower
						}

						if (part is SolarPanel || (part is Reactor && powerComponent.powerScheme.chargeBatteryFromReactor)) {
							availiableChargingPower += poweringState.availiablePower
						}
					}
				})

				// Sort powered parts batteries last, then lowest to highest, except if requested power is 0 then last
				powerComponent.poweredParts.sortWith(Comparator { aRef, bRef ->
					val aBattery = if (aRef.part is Battery) 1 else 0
					val bBattery = if (bRef.part is Battery) 1 else 0

					if (aBattery == bBattery) {

						var requestedPowerA = ship.getPartState(aRef).get(PoweredPartState::class).requestedPower
						var requestedPowerB = ship.getPartState(bRef).get(PoweredPartState::class).requestedPower

						if (requestedPowerA == 0L) {
							requestedPowerA = Long.MAX_VALUE
						}

						if (requestedPowerB == 0L) {
							requestedPowerB = Long.MAX_VALUE
						}

						requestedPowerA.compareTo(requestedPowerB)

					} else {

						aBattery.compareTo(bBattery)
					}
				})

				powerComponent.totalRequestedPower = 0
				var givenPower = 0L

				// Allocate power
				powerComponent.poweredParts.forEach({
					val partRef = it
					val part = partRef.part
					val poweredState = ship.getPartState(partRef)[PoweredPartState::class]

					if (ship.isPartEnabled(partRef)) {

						if (part !is Battery) {

							if (powerComponent.totalRequestedPower + poweredState.requestedPower <= powerComponent.totalAvailiablePower) {

								poweredState.givenPower = poweredState.requestedPower

							} else {

								poweredState.givenPower = Math.max(0, powerComponent.totalAvailiablePower - powerComponent.totalRequestedPower)
							}

							givenPower += poweredState.givenPower
							powerComponent.totalRequestedPower += poweredState.requestedPower

						} else {

							val chargedState = ship.getPartState(partRef)[ChargedPartState::class]
							val leftToCharge = part.capacitor - chargedState.charge

							poweredState.requestedPower = Math.min((leftToCharge * 100) / part.efficiency, part.powerConsumption)

							if (powerComponent.totalRequestedPower + poweredState.requestedPower <= availiableChargingPower) {

								poweredState.givenPower = poweredState.requestedPower

							} else {

								poweredState.givenPower = Math.max(0, availiableChargingPower - powerComponent.totalRequestedPower)
							}

							givenPower += poweredState.givenPower
							powerComponent.totalRequestedPower += poweredState.requestedPower
						}

					} else {
						poweredState.givenPower = 0
					}
				})

				var neededPower = givenPower

				// Supply power
				powerComponent.poweringParts.forEach({
					val part = it
					val poweringState = ship.getPartState(part)[PoweringPartState::class]

					if (ship.isPartEnabled(part)) {

						if (neededPower > poweringState.availiablePower) {

							poweringState.producedPower = poweringState.availiablePower
							neededPower -= poweringState.availiablePower

						} else {

							poweringState.producedPower = neededPower
							neededPower -= neededPower
						}

					} else {
						poweringState.producedPower = 0
					}
				})

				powerComponent.totalUsedPower = givenPower
			}

			val deltaGameTime = world.getDelta().toInt()

			// Consume fuel, consume batteries
			powerComponent.poweringParts.forEach({
				val partRef = it
				val part = partRef.part
				val poweringState = ship.getPartState(partRef)[PoweringPartState::class]

				if (part is Reactor) {

					// deltaGameTime: Int, entityID: Entity, ship: ShipComponent, partRef: PartRef<Part>, energyConsumed: Long, fuelEnergy: Long
					consumeFuel(deltaGameTime, entityID, world, ship, partRef, poweringState.producedPower.toLong(), part.power)

				} else if (part is Battery) {

					val chargedState = ship.getPartState(partRef)[ChargedPartState::class]

					if (poweringState.producedPower > 0) {
						chargedState.charge -= Math.min(deltaGameTime * poweringState.producedPower, chargedState.charge)
					}
				}
			})

			// Charge capacitors and batteries
			powerComponent.poweredParts.forEach({
				val partRef = it
				val part = partRef.part
				val poweredState = ship.getPartState(partRef)[PoweredPartState::class]

				if (part is ChargedPart) {

					val chargedState = ship.getPartState(partRef)[ChargedPartState::class]

					if (poweredState.givenPower > 0) {

						var chargeToAdd = deltaGameTime * poweredState.givenPower

						if (part is Battery) {
							chargeToAdd = (chargeToAdd * part.efficiency) / 100;
							
							if (chargedState.charge + chargeToAdd > part.capacitor) {
								chargedState.charge = part.capacitor
	
							} else {
								chargedState.charge += chargeToAdd
							}
							
						} else {
							chargedState.charge += chargeToAdd
						}
					}
				}
			})
		}
	}

	override fun process(entityID: Int) {}
}