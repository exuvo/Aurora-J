package se.exuvo.aurora.starsystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.EntitySubscription.SubscriptionListener
import com.artemis.annotations.Wire
import com.artemis.systems.IteratingSystem
import com.artemis.utils.IntBag
import net.mostlyoriginal.api.event.common.Subscribe
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.galactic.AmmunitionPart
import se.exuvo.aurora.galactic.Battery
import se.exuvo.aurora.galactic.ChargedPart
import se.exuvo.aurora.galactic.FuelWastePart
import se.exuvo.aurora.galactic.FueledPart
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.Part
import se.exuvo.aurora.galactic.PartRef
import se.exuvo.aurora.galactic.PoweredPart
import se.exuvo.aurora.galactic.PoweringPart
import se.exuvo.aurora.galactic.Railgun
import se.exuvo.aurora.galactic.Reactor
import se.exuvo.aurora.galactic.SolarPanel
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.galactic.WeaponPart
import se.exuvo.aurora.starsystems.PreSystem
import se.exuvo.aurora.starsystems.StarSystem
import se.exuvo.aurora.starsystems.components.AmmunitionPartState
import se.exuvo.aurora.starsystems.components.CargoComponent
import se.exuvo.aurora.starsystems.components.ChargedPartState
import se.exuvo.aurora.starsystems.components.FueledPartState
import se.exuvo.aurora.starsystems.components.PartStatesComponent
import se.exuvo.aurora.starsystems.components.PowerComponent
import se.exuvo.aurora.starsystems.components.PoweredPartState
import se.exuvo.aurora.starsystems.components.PoweringPartState
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.components.SolarIrradianceComponent
import se.exuvo.aurora.starsystems.components.TargetingComputerState
import se.exuvo.aurora.starsystems.components.WeaponPartState
import se.exuvo.aurora.starsystems.events.PowerEvent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.utils.printEntity
import kotlin.math.ceil

class PowerSystem : IteratingSystem(FAMILY), PreSystem {
	companion object {
		@JvmField val FAMILY = Aspect.all(ShipComponent::class.java, PowerComponent::class.java)
		@JvmField val SHIP_FAMILY = Aspect.all(ShipComponent::class.java)
		@JvmField val log = LogManager.getLogger(PowerSystem::class.java)
	}

	lateinit private var shipMapper: ComponentMapper<ShipComponent>
	lateinit private var powerMapper: ComponentMapper<PowerComponent>
	lateinit private var irradianceMapper: ComponentMapper<SolarIrradianceComponent>
	lateinit private var partStatesMapper: ComponentMapper<PartStatesComponent>
	lateinit private var cargoMapper: ComponentMapper<CargoComponent>

	@Wire
	lateinit private var system: StarSystem
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
	
	fun activatePart(entityID: Int, partRef: PartRef<Part>, partStates: PartStatesComponent = partStatesMapper.get(entityID)) {
		
		if (partStates.isPartEnabled(partRef)) {
			throw IllegalStateException();
		}
		
		partStates.setPartEnabled(partRef, true)
		system.changed(entityID, partStatesMapper)
		
		val part = partRef.part
		
		if (part is TargetingComputer) {
			
			val tcState = partStates[partRef][TargetingComputerState::class]
			
			for (weapon in tcState.linkedWeapons) {
				
				activatePart(entityID, weapon, partStates)
			}
			
		} else if (part is WeaponPart) {
			
			val weaponState = partStates[partRef][WeaponPartState::class]
			val targetingComputer = weaponState.targetingComputer
			
			if (targetingComputer != null) {
				
				val tcState = partStates[targetingComputer][TargetingComputerState::class]
				
				tcState.disabledWeapons.remove(partRef)
				
				if (part is ChargedPart) {
					tcState.chargingWeapons.add(partRef)
				}
				
				if (part is AmmunitionPart) {
					
					val ammoState = partStates[partRef][AmmunitionPartState::class]
					
					if (ammoState.amount > 0 && part !is Railgun) {
						tcState.readyWeapons.add(partRef)
					}
					
					val freeSpace = part.ammunitionAmount - ammoState.amount

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
	
	fun deactivatePart(entityID: Int, partRef: PartRef<Part>, partStates: PartStatesComponent = partStatesMapper.get(entityID)) {
		
		if (!partStates.isPartEnabled(partRef)) {
			throw IllegalStateException();
		}
		
		partStates.setPartEnabled(partRef, false)
		system.changed(entityID, partStatesMapper)
		
		val part = partRef.part
		
		if (part is PoweredPart) {
			
			val poweredState = partStates[partRef][PoweredPartState::class]
			
			if (poweredState.requestedPower > 0) {
				poweredState.requestedPower = 0
				
				val powerComponent = powerMapper.get(entityID)
				powerComponent.stateChanged = true
			}
		}
		
		if (part is ChargedPart) {
			val chargedState = partStates[partRef][ChargedPartState::class]
			chargedState.charge = 0
			chargedState.expectedFullAt = 0
		}
		
		if (part is TargetingComputer) {
			
			val tcState = partStates[partRef][TargetingComputerState::class]
			
			for (weapon in tcState.linkedWeapons) {
				
				deactivatePart(entityID, weapon, partStates)
			}
			
		} else if (part is WeaponPart) {
			
			val weaponState = partStates[partRef][WeaponPartState::class]
			val targetingComputer = weaponState.targetingComputer
			
			if (targetingComputer != null) {
				
				val tcState = partStates[targetingComputer][TargetingComputerState::class]
				
				tcState.readyWeapons.remove(partRef)
				tcState.disabledWeapons.add(partRef)
				
				if (part is ChargedPart) {
					tcState.chargingWeapons.remove(partRef)
				}
				
				if (part is AmmunitionPart) {
					tcState.reloadingWeapons.remove(partRef)
					
					val ammoState = partStates[partRef][AmmunitionPartState::class]
					
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
		system.changed(event.entityID, powerMapper, partStatesMapper)
	}

	val tmpPowerComponent = PowerComponent()
	
	override fun preProcessSystem() {
		subscription.getEntities().forEachFast { entityID ->
			val powerComponent = powerMapper.get(entityID)
			val partStates = partStatesMapper.get(entityID)
			val cargo = cargoMapper.get(entityID)
			
			powerComponent.simpleCopy(tmpPowerComponent)
			
			val powerWasStable = powerComponent.totalAvailablePower > powerComponent.totalUsedPower
			val powerWasSolarStable = powerComponent.totalAvailableSolarPower > powerComponent.totalRequestedPower
			val oldTotalAvailablePower = powerComponent.totalAvailablePower

			// Pre checks
			powerComponent.poweringParts.forEachFast{ partRef ->
				val part = partRef.part
				val poweringState = partStates[partRef][PoweringPartState::class]

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

					powerComponent.totalAvailablePower += producedPower - poweringState.availiablePower
					powerComponent.totalAvailableSolarPower += producedPower - poweringState.availiablePower
					poweringState.availiablePower = producedPower

				} else if (part is Reactor) {

					val fueledState = partStates[partRef][FueledPartState::class]

					val remainingFuel = cargo.getCargoAmount(part.fuel)
					fueledState.totalFuelEnergyRemaining = fueledState.fuelEnergyRemaining + part.power * part.fuelTime * remainingFuel
					val availablePower = minOf(part.power, fueledState.totalFuelEnergyRemaining)

//					println("remainingFuel ${remainingFuel} g, max power ${part.power} W, remaining power ${remainingFuel / part.fuelConsumption} Ws")

					if (powerWasStable && availablePower < poweringState.producedPower) {
						powerComponent.stateChanged = true
					}

					powerComponent.totalAvailablePower += availablePower - poweringState.availiablePower
					poweringState.availiablePower = availablePower

				} else if (part is Battery) { // Charge empty or full

					val poweredState = partStates[partRef][PoweredPartState::class]
					val chargedState = partStates[partRef][ChargedPartState::class]

					// Charge empty
					if (poweringState.producedPower > chargedState.charge) {
						powerComponent.stateChanged = true
					}

					// Charge full
					if (poweredState.givenPower > 0 && chargedState.charge + (poweredState.givenPower * part.efficiency) / 100 > part.capacitor) {
						powerComponent.stateChanged = true
					}

					val availablePower = minOf(part.power, chargedState.charge)

					powerComponent.totalAvailablePower += availablePower - poweringState.availiablePower
					poweringState.availiablePower = availablePower
				}
			}

			if (!powerComponent.stateChanged) {

				if (powerWasSolarStable && powerComponent.totalAvailableSolarPower < powerComponent.totalRequestedPower) {
					powerComponent.stateChanged = true
				}

				if (powerWasStable) {
					if (powerComponent.totalAvailablePower < powerComponent.totalUsedPower) {
						powerComponent.stateChanged = true
					}
				} else if (powerComponent.totalAvailablePower != oldTotalAvailablePower) {
					powerComponent.stateChanged = true
				}
			}

			// Calculate usage
			if (powerComponent.stateChanged) {
				powerComponent.stateChanged = false

				// Summarise available power
				powerComponent.totalAvailablePower = 0
				powerComponent.totalAvailableSolarPower = 0
				var availableChargingPower = 0L

				powerComponent.poweringParts.forEach({
					val partRef = it
					val part = partRef.part

					if (partStates.isPartEnabled(partRef)) {
						val poweringState = partStates[partRef][PoweringPartState::class]

						powerComponent.totalAvailablePower += poweringState.availiablePower

						if (part is SolarPanel) {
							powerComponent.totalAvailableSolarPower += poweringState.availiablePower
						}

						if (part is SolarPanel || (part is Reactor && powerComponent.powerScheme.chargeBatteryFromReactor)) {
							availableChargingPower += poweringState.availiablePower
						}
					}
				})

				// Sort powered parts batteries last, then lowest to highest, except if requested power is 0 then last
				powerComponent.poweredParts.sortWith(Comparator { aRef, bRef ->
					val aBattery = if (aRef.part is Battery) 1 else 0
					val bBattery = if (bRef.part is Battery) 1 else 0

					if (aBattery == bBattery) {

						var requestedPowerA = partStates[aRef][PoweredPartState::class].requestedPower
						var requestedPowerB = partStates[bRef][PoweredPartState::class].requestedPower

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
					val poweredState = partStates[partRef][PoweredPartState::class]

					if (partStates.isPartEnabled(partRef)) {

						if (part !is Battery) {

							if (powerComponent.totalRequestedPower + poweredState.requestedPower <= powerComponent.totalAvailablePower) {

								poweredState.givenPower = poweredState.requestedPower

							} else {

								poweredState.givenPower = maxOf(0, powerComponent.totalAvailablePower - powerComponent.totalRequestedPower)
							}

							givenPower += poweredState.givenPower
							powerComponent.totalRequestedPower += poweredState.requestedPower

						} else {

							val chargedState = partStates[partRef][ChargedPartState::class]
							val leftToCharge = part.capacitor - chargedState.charge

							poweredState.requestedPower = minOf((leftToCharge * 100) / part.efficiency, part.powerConsumption)

							if (powerComponent.totalRequestedPower + poweredState.requestedPower <= availableChargingPower) {

								poweredState.givenPower = poweredState.requestedPower

							} else {

								poweredState.givenPower = maxOf(0, availableChargingPower - powerComponent.totalRequestedPower)
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
					val poweringState = partStates[part][PoweringPartState::class]

					if (partStates.isPartEnabled(part)) {

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
				val poweringState = partStates[partRef][PoweringPartState::class]

				if (part is Reactor) {

					// deltaGameTime: Int, entityID: Entity, ship: ShipComponent, partRef: PartRef<Part>, energyConsumed: Long, fuelEnergy: Long
					consumeFuel(deltaGameTime, entityID, partRef, poweringState.producedPower, part.power, partStates, cargo)

				} else if (part is Battery) {

					val chargedState = partStates[partRef][ChargedPartState::class]

					if (poweringState.producedPower > 0) {
						chargedState.charge -= minOf(deltaGameTime * poweringState.producedPower, chargedState.charge)
					}
				}
			})

			// Charge capacitors and batteries
			powerComponent.poweredParts.forEach({
				val partRef = it
				val part = partRef.part
				val poweredState = partStates[partRef][PoweredPartState::class]

				if (part is ChargedPart) {

					val chargedState = partStates[partRef][ChargedPartState::class]

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
			
			if (!powerComponent.simpleEquals(tmpPowerComponent)) {
				system.changed(entityID, partStatesMapper, powerMapper)
			} else {
				system.changed(entityID, partStatesMapper)
			}
		}
	}

	override fun process(entityID: Int) {}
	
	fun consumeFuel(deltaGameTime: Int, entityID: Int, partRef: PartRef<Part>, energyConsumed: Long, fuelEnergy: Long,
									partStates: PartStatesComponent = partStatesMapper.get(entityID),
									cargo: CargoComponent = cargoMapper.get(entityID))
	{
		val part = partRef.part
		if (part is FueledPart) {
			val fueledState = partStates[partRef][FueledPartState::class]
			var cargoChanged = false
			
			var fuelEnergyConsumed = deltaGameTime * energyConsumed
//					println("fuelEnergyConsumed $fuelEnergyConsumed, fuelTime ${TimeUnits.secondsToString(part.fuelTime.toLong())}, power ${poweringState.producedPower.toDouble() / part.power}%")
			
			if (fuelEnergyConsumed > fueledState.fuelEnergyRemaining) {
				
				fuelEnergyConsumed -= fueledState.fuelEnergyRemaining
				
				var fuelRequired = part.fuelConsumption.toLong()
				
				if (fuelEnergyConsumed > fuelEnergy * part.fuelTime) {
					
					fuelRequired *= (1 + fuelEnergyConsumed / (fuelEnergy * part.fuelTime)).toInt()
				}
				
				val remainingFuel = cargo.getCargoAmount(part.fuel)
				fuelRequired = minOf(fuelRequired, remainingFuel)
				
				if (part is FuelWastePart) {
					
					var fuelConsumed = ceil((part.fuelConsumption * fueledState.fuelEnergyRemaining).toDouble() / (fuelEnergy * part.fuelTime)).toLong()
					
					if (fuelEnergyConsumed > fuelEnergy * part.fuelTime) {
						
						fuelConsumed += fuelRequired / part.fuelConsumption - 1
					}

//							println("fuelConsumed $fuelConsumed")
					if (fuelConsumed > 0) {
						cargo.addCargo(part.waste, fuelConsumed)
						cargoChanged = true
					}
				}
				
				fueledState.fuelEnergyRemaining = maxOf(fuelEnergy * part.fuelTime * fuelRequired - fuelEnergyConsumed, 0)
				
				val removedFuel = cargo.retrieveCargo(part.fuel, fuelRequired)
				
				if (removedFuel != fuelRequired) {
					log.warn("Entity ${printEntity(entityID, world)} was expected to consume $fuelRequired but only had $removedFuel left")
				}
				
				if (removedFuel > 0) {
					cargoChanged = true
				}
				
			} else {
				
				if (part is FuelWastePart) {
					
					val fuelRemainingPre = ceil((part.fuelConsumption * fueledState.fuelEnergyRemaining).toDouble() / (fuelEnergy * part.fuelTime)).toLong()
					val fuelRemainingPost = ceil((part.fuelConsumption * (fueledState.fuelEnergyRemaining - fuelEnergyConsumed)).toDouble() / (fuelEnergy * part.fuelTime)).toLong()
					val fuelConsumed = fuelRemainingPre - fuelRemainingPost;

//							println("fuelRemainingPre $fuelRemainingPre, fuelRemainingPost $fuelRemainingPost, fuelConsumed $fuelConsumed")
					
					if (fuelConsumed > 0) {
						cargo.addCargo(part.waste, fuelConsumed)
						cargoChanged = true
					}
				}
				
				fueledState.fuelEnergyRemaining -= fuelEnergyConsumed
			}
			
			if (cargoChanged) {
				system.changed(entityID, partStatesMapper, cargoMapper)
			} else {
				system.changed(entityID, partStatesMapper)
			}
		}
	}
}