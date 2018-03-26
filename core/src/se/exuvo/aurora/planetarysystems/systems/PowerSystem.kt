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

				val aInt = ship.shipClass.powerScheme.powerTypeCompareMap.get(a::class)!!
				val bInt = ship.shipClass.powerScheme.powerTypeCompareMap.get(b::class)!!

				aInt.compareTo(bInt)
			})
		}
	}

	override fun entityRemoved(entity: Entity) {
	}

	override fun processEntity(entity: Entity, deltaGameTime: Float) {

		val powerComponent = powerMapper.get(entity)
		val ship = shipMapper.get(entity)

		val powerWasStable = powerComponent.totalAvailiablePower > powerComponent.totalUsedPower

		// Pre checks: out of fuel
		powerComponent.poweringParts.forEach({
			val part = it
			val poweringState = ship.getPartState(part).get(PoweringPartState::class)

			if (part is SolarPanel) {

				val irradiance = irradianceMapper.get(entity).irradiance
				val solarPanelArea = part.getVolume() / SOLAR_PANEL_THICKNESS
				val producedPower = (((solarPanelArea * irradiance.toLong()) / 100) * part.efficiency).toInt()

//				println("solarPanelArea ${solarPanelArea / 100} m2, irradiance $irradiance W/m2, producedPower $producedPower W")
				
				powerComponent.totalAvailiablePower += producedPower - poweringState.availiablePower

				if (powerWasStable && producedPower > poweringState.producedPower) {
					powerComponent.stateChanged = true
				}

				poweringState.availiablePower = producedPower
				poweringState.producedPower = producedPower

			} else if (part is Reactor) {

				if (part is FueledPart) {

					val remainingFuel = ship.getCargoAmount(part.fuel)
					val availiablePower = Math.min(part.power, remainingFuel / part.fuelConsumption)

					if (powerWasStable && poweringState.producedPower < availiablePower) {
						powerComponent.stateChanged = true
					}

					poweringState.availiablePower = availiablePower
				}

			} else if (part is Battery) {

				if (poweringState.producedPower > 0 && poweringState.producedPower > part.capacitor) { // Running out of stored power
					powerComponent.stateChanged = true
				}
			}
		})

		if (powerWasStable && powerComponent.totalAvailiablePower < powerComponent.totalUsedPower) {
			powerComponent.stateChanged = true
		}

		// Calculate usage on: Requested power changed, availiable power went below requested
		if (powerComponent.stateChanged) {
			powerComponent.stateChanged = false

			powerComponent.poweredParts.sortWith(Comparator { a, b ->
				val poweredStateA = ship.getPartState(a).get(PoweredPartState::class)
				val poweredStateB = ship.getPartState(b).get(PoweredPartState::class)

				poweredStateA.requestedPower.compareTo(poweredStateB.requestedPower)
			})

			powerComponent.totalAvailiablePower = 0

			powerComponent.poweringParts.forEach({
				val part = it
				val poweringState = ship.getPartState(part).get(PoweringPartState::class)

				powerComponent.totalAvailiablePower += poweringState.availiablePower
			})

			powerComponent.totalRequestedPower = 0

			powerComponent.poweredParts.forEach({
				val part = it
				val poweredState = ship.getPartState(part).get(PoweredPartState::class)

				if (powerComponent.totalRequestedPower + poweredState.requestedPower <= powerComponent.totalAvailiablePower) {

					poweredState.givenPower = poweredState.requestedPower

				} else {

					poweredState.givenPower = powerComponent.totalAvailiablePower - powerComponent.totalRequestedPower
				}

				powerComponent.totalRequestedPower += poweredState.requestedPower
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
			val poweringState = ship.getPartState(part).get(PoweringPartState::class)

			if (part is Reactor) {

				if (poweringState.producedPower > 0) {

					if (part is FueledPart) {

						// 1 gram of fissile material yields about 1 megawatt-day (MWd) of heat energy. https://www.nuclear-power.net/nuclear-power-plant/nuclear-fuel/fuel-consumption-of-conventional-reactor/
						val fuelConsumed = (part.fuelConsumption * poweringState.producedPower) / part.power
						val removedFuel = ship.retrieveCargo(part.fuel, fuelConsumed)
						
						if (removedFuel != fuelConsumed){
							log.warn("Entity ${entity.printID()} was expected to consume $fuelConsumed but only had $removedFuel left")
						}
					}
				}

			} else if (part is Battery) {

				val chargedState = ship.getPartState(part).get(ChargedPartState::class)

				if (poweringState.producedPower > 0) {
					chargedState.charge -= poweringState.producedPower
				}
			}
		})

		// Charge capacitors and batteries
		powerComponent.poweredParts.forEach({
			val part = it
			val poweredState = ship.getPartState(part).get(PoweredPartState::class)

			if (part is ChargedPart) {

				val chargedState = ship.getPartState(part).get(ChargedPartState::class)

				if (poweredState.givenPower > 0) {
					chargedState.charge += poweredState.givenPower
				}
			}
		})
	}
}