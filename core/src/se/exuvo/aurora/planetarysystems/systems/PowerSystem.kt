package se.exuvo.aurora.planetarysystems.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntityListener
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import org.apache.log4j.Logger
import se.exuvo.aurora.galactic.Battery
import se.exuvo.aurora.galactic.ChargedPart
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.PoweredPart
import se.exuvo.aurora.galactic.PoweringPart
import se.exuvo.aurora.galactic.Reactor
import se.exuvo.aurora.galactic.SolarPanel
import se.exuvo.aurora.planetarysystems.components.PowerComponent
import se.exuvo.aurora.planetarysystems.components.ShipComponent
import se.exuvo.aurora.planetarysystems.components.SolarIrradianceComponent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.planetarysystems.components.PoweringPartState
import se.exuvo.aurora.planetarysystems.components.PowerScheme
import se.exuvo.aurora.planetarysystems.components.PoweredPartState
import se.exuvo.aurora.planetarysystems.components.ChargedPartState
import se.exuvo.aurora.galactic.FueledPart
import se.exuvo.aurora.utils.printID
import se.exuvo.aurora.planetarysystems.components.FueledPartState
import se.exuvo.aurora.galactic.FuelWastePart
import se.exuvo.aurora.utils.TimeUnits

class PowerSystem : IteratingSystem(FAMILY), EntityListener {
	companion object {
		val FAMILY = Family.all(ShipComponent::class.java, PowerComponent::class.java).get()
		val SHIP_FAMILY = Family.all(ShipComponent::class.java).get()
	}

	val log = Logger.getLogger(this.javaClass)

	private val shipMapper = ComponentMapper.getFor(ShipComponent::class.java)
	private val powerMapper = ComponentMapper.getFor(PowerComponent::class.java)
	private val irradianceMapper = ComponentMapper.getFor(SolarIrradianceComponent::class.java)

	private val galaxy = GameServices[Galaxy::class.java]

	// All solar panels assumed to be 5cm thick
	private val SOLAR_PANEL_THICKNESS = 5

	override fun addedToEngine(engine: Engine) {
		super.addedToEngine(engine)

		engine.addEntityListener(SHIP_FAMILY, this)
	}

	override fun removedFromEngine(engine: Engine) {
		super.removedFromEngine(engine)

		engine.removeEntityListener(this)
	}

	override fun entityAdded(entity: Entity) {

		var powerComponent = powerMapper.get(entity)

		if (powerComponent == null) {

			val ship = shipMapper.get(entity)

			powerComponent = PowerComponent(ship.shipClass.powerScheme)
			entity.add(powerComponent)

			ship.shipClass.parts.forEach {
				val part = it

				if (part is PoweringPart) {
					powerComponent.poweringParts.add(part)
				}

				if (part is PoweredPart) {
					powerComponent.poweredParts.add(part)
				}

				if (part is ChargedPart) {
					powerComponent.chargedParts.add(part)
				}
			}

			powerComponent.poweringParts.sortWith(Comparator { a, b ->

				val aInt = ship.shipClass.powerScheme.getPowerTypeValue(a)
				val bInt = ship.shipClass.powerScheme.getPowerTypeValue(b)

				aInt.compareTo(bInt)
			})
		}
	}

	override fun entityRemoved(entity: Entity) {
	}

	override fun processEntity(entity: Entity, deltaGameTimeF: Float) {
		val deltaGameTime = deltaGameTimeF.toInt()

		val powerComponent = powerMapper.get(entity)
		val ship = shipMapper.get(entity)

		val powerWasStable = powerComponent.totalAvailiablePower > powerComponent.totalUsedPower
		val oldTotalAvailiablePower = powerComponent.totalAvailiablePower

		// Pre checks
		powerComponent.poweringParts.forEach({
			val part = it
			val poweringState = ship.getPartState(part)[PoweringPartState::class]

			if (part is SolarPanel) { // Update solar power

				val irradiance = irradianceMapper.get(entity).irradiance
				val solarPanelArea = part.getVolume() / SOLAR_PANEL_THICKNESS
				val producedPower = (((solarPanelArea * irradiance.toLong()) / 100) * part.efficiency).toInt()

//				println("solarPanelArea ${solarPanelArea / 100} m2, irradiance $irradiance W/m2, producedPower $producedPower W")

				powerComponent.totalAvailiablePower += producedPower - poweringState.availiablePower
				poweringState.availiablePower = producedPower

			} else if (part is Reactor) {

				if (part is FueledPart) { // Running out of fuel

					val fueledState = ship.getPartState(part)[FueledPartState::class]

					val remainingFuel = ship.getCargoAmount(part.fuel)
					fueledState.totalFuelEnergyRemaining = fueledState.fuelEnergyRemaining + part.power.toLong() * part.fuelTime * remainingFuel 
					val availiablePower = Math.min(part.power.toLong(), fueledState.totalFuelEnergyRemaining).toInt()

//					println("remainingFuel ${remainingFuel} g, max power ${part.power} W, remaining power ${remainingFuel / part.fuelConsumption} Ws")

					if (powerWasStable && poweringState.producedPower < availiablePower) {
						powerComponent.stateChanged = true
					}

					powerComponent.totalAvailiablePower += availiablePower - poweringState.availiablePower
					poweringState.availiablePower = availiablePower
				}

			} else if (part is Battery) { // Charge empty or full

				val poweredState = ship.getPartState(part)[PoweredPartState::class]
				val chargedState = ship.getPartState(part)[ChargedPartState::class]

				// Charge empty
				if (poweringState.producedPower > 0 && poweringState.producedPower > chargedState.charge) {
					powerComponent.stateChanged = true
				}

				// Charge full
				if (poweredState.givenPower > 0 && chargedState.charge + poweredState.givenPower > part.capacitor) {
					powerComponent.stateChanged = true
				}
			}
		})

		if (powerWasStable) {
			if (powerComponent.totalAvailiablePower < powerComponent.totalUsedPower) {
				powerComponent.stateChanged = true
			}
		} else if (powerComponent.totalAvailiablePower != oldTotalAvailiablePower) {
			powerComponent.stateChanged = true
		}

		// Calculate usage
		if (powerComponent.stateChanged) {
			powerComponent.stateChanged = false

			// Summarise availiable power
			powerComponent.totalAvailiablePower = 0
			var availiableBatteryChargingPower = 0

			powerComponent.poweringParts.forEach({
				val part = it

				if (ship.isPartEnabled(part)) {
					val poweringState = ship.getPartState(part)[PoweringPartState::class]

					powerComponent.totalAvailiablePower += poweringState.availiablePower

					if (part is SolarPanel || (part is Reactor && powerComponent.powerScheme.chargeBatteryFromReactor)) {
						availiableBatteryChargingPower += poweringState.availiablePower
					}
				}
			})

			// Sort powered parts lowest to highest
			powerComponent.poweredParts.sortWith(Comparator { a, b ->
				val poweredStateA = ship.getPartState(a).get(PoweredPartState::class)
				val poweredStateB = ship.getPartState(b).get(PoweredPartState::class)

				poweredStateA.requestedPower.compareTo(poweredStateB.requestedPower)
			})

			powerComponent.totalRequestedPower = 0

			// Allocate power
			powerComponent.poweredParts.forEach({
				val part = it

				if (part !is Battery) {

					val poweredState = ship.getPartState(part)[PoweredPartState::class]

					if (ship.isPartEnabled(part)) {

						if (powerComponent.totalRequestedPower + poweredState.requestedPower <= powerComponent.totalAvailiablePower) {

							poweredState.givenPower = poweredState.requestedPower

						} else {

							poweredState.givenPower = Math.max(0, powerComponent.totalAvailiablePower - powerComponent.totalRequestedPower)
						}

						powerComponent.totalRequestedPower += poweredState.requestedPower

					} else {
						poweredState.givenPower = 0
					}
				}
			})

			powerComponent.poweredParts.forEach({
				val part = it

				if (part is Battery) {
					val poweredState = ship.getPartState(part)[PoweredPartState::class]

					if (ship.isPartEnabled(part)) {

						val chargedState = ship.getPartState(part)[ChargedPartState::class]
						val leftToCharge = part.capacitor - chargedState.charge

						poweredState.requestedPower = Math.min(leftToCharge, part.powerConsumption)

						if (powerComponent.totalRequestedPower + poweredState.requestedPower <= availiableBatteryChargingPower) {

							poweredState.givenPower = poweredState.requestedPower

						} else {

							poweredState.givenPower = Math.max(0, availiableBatteryChargingPower - powerComponent.totalRequestedPower)
						}

						powerComponent.totalRequestedPower += poweredState.requestedPower
						
					} else {
						poweredState.givenPower = 0
					}
				}
			})

			var neededPower = powerComponent.totalRequestedPower

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

			if (powerComponent.totalAvailiablePower > powerComponent.totalRequestedPower) {

				powerComponent.totalUsedPower = powerComponent.totalRequestedPower

			} else {

				powerComponent.totalUsedPower = powerComponent.totalAvailiablePower
			}
		}

		// Consume fuel, consume batteries
		powerComponent.poweringParts.forEach({
			val part = it
			val poweringState = ship.getPartState(part)[PoweringPartState::class]

			if (part is Reactor) {

				if (poweringState.producedPower > 0) {

					if (part is FueledPart) {

						val fueledState = ship.getPartState(part)[FueledPartState::class]

						var fuelEnergyConsumed = poweringState.producedPower.toLong()
//						println("fuelEnergyConsumed $fuelEnergyConsumed, fuelTime ${TimeUnits.secondsToString(part.fuelTime.toLong())}, power ${poweringState.producedPower.toDouble() / part.power}%")

						if (fuelEnergyConsumed > fueledState.fuelEnergyRemaining) {

							fuelEnergyConsumed -= fueledState.fuelEnergyRemaining

							var fuelConsumed = part.fuelConsumption

							if (fuelEnergyConsumed > part.power * part.fuelTime.toLong()) {

								fuelConsumed *= (1 + fuelEnergyConsumed / (part.power * part.fuelTime.toLong())).toInt()
							}

							fueledState.fuelEnergyRemaining = part.power.toLong() * part.fuelTime * fuelConsumed - fuelEnergyConsumed

							val removedFuel = ship.retrieveCargo(part.fuel, fuelConsumed)

							if (removedFuel != fuelConsumed) {
								log.warn("Entity ${entity.printID()} was expected to consume $fuelConsumed but only had $removedFuel left")
							}

							if (part is FuelWastePart) {
								//TODO delay initial waste
								ship.addCargo(part.waste, removedFuel)
							}

						} else {

							fueledState.fuelEnergyRemaining -= fuelEnergyConsumed
						}
					}
				}

			} else if (part is Battery) {

				val chargedState = ship.getPartState(part)[ChargedPartState::class]

				if (poweringState.producedPower > 0) {
					chargedState.charge -= poweringState.producedPower
				}
			}
		})

		// Charge capacitors and batteries
		powerComponent.poweredParts.forEach({
			val part = it
			val poweredState = ship.getPartState(part)[PoweredPartState::class]

			if (part is ChargedPart) {

				val chargedState = ship.getPartState(part)[ChargedPartState::class]

				if (poweredState.givenPower > 0) {
					chargedState.charge += poweredState.givenPower
				}
			}
		})
	}
}