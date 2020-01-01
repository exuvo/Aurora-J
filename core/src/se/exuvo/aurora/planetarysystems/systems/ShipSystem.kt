package se.exuvo.aurora.planetarysystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.systems.IteratingSystem
import net.mostlyoriginal.api.event.common.EventSystem
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.galactic.ElectricalThruster
import se.exuvo.aurora.galactic.FueledThruster
import se.exuvo.aurora.galactic.ThrustingPart
import se.exuvo.aurora.planetarysystems.components.FueledPartState
import se.exuvo.aurora.planetarysystems.components.MassComponent
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.planetarysystems.components.PoweredPartState
import se.exuvo.aurora.planetarysystems.components.ShipComponent
import se.exuvo.aurora.planetarysystems.components.ThrustComponent
import se.exuvo.aurora.planetarysystems.events.PowerEvent
import se.exuvo.aurora.utils.consumeFuel
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.planetarysystems.PlanetarySystem
import com.artemis.annotations.Wire

class ShipSystem : IteratingSystem(FAMILY), PreSystem {
	companion object {
		val FAMILY = Aspect.all(ShipComponent::class.java)
	}

	val log = LogManager.getLogger(this.javaClass)

	lateinit private var massMapper: ComponentMapper<MassComponent>
	lateinit private var shipMapper: ComponentMapper<ShipComponent>
	lateinit private var thrustMapper: ComponentMapper<ThrustComponent>
	lateinit private var nameMapper: ComponentMapper<NameComponent>
	lateinit private var events: EventSystem
	
	@Wire
	lateinit private var planetarySystem: PlanetarySystem

	override fun preProcessSystem() {
		subscription.getEntities().forEachFast { entityID ->
			val ship = shipMapper.get(entityID)
			val thrusters = ship.hull.getPartRefs().filter({ it.part is ThrustingPart })
			val thrustComponent = thrustMapper.get(entityID)

			thrusters.forEachFast{ thruster ->
				val part = thruster.part

				if (part is ElectricalThruster && ship.isPartEnabled(thruster)) {
					val poweredState = ship.getPartState(thruster)[PoweredPartState::class]

					if (thrustComponent != null && thrustComponent.thrusting) {
						if (poweredState.requestedPower != part.powerConsumption) {
							poweredState.requestedPower = part.powerConsumption
							events.dispatch(planetarySystem.getEvent(PowerEvent::class).set(entityID))
						}
					} else {
						if (poweredState.requestedPower > 0) {
							poweredState.requestedPower = 0
							events.dispatch(planetarySystem.getEvent(PowerEvent::class).set(entityID))
						}
					}
				}
			}

			if (!massMapper.has(entityID) || ship.cargoChanged) {
				massMapper.create(entityID).set(ship.getMass().toDouble())
				ship.cargoChanged = false
			}
		}
	}

	override fun process(entityID: Int) {
		val deltaGameTime = world.getDelta().toInt()

		val ship = shipMapper.get(entityID)

		var thrust = 0L
		var maxThrust = 0L
		val thrusters = ship.hull.getPartRefs().filter({ it.part is ThrustingPart })

		if (thrusters.isNotEmpty()) {
			val thrustComponent = thrustMapper.get(entityID)

			thrusters.forEachFast{ thruster ->
				if (ship.isPartEnabled(thruster)) {
					val part = thruster.part

					if (part is ElectricalThruster) {
						val poweredState = ship.getPartState(thruster)[PoweredPartState::class]

						maxThrust += part.thrust

						if (poweredState.requestedPower > 0) {
							thrust += part.thrust * poweredState.givenPower / poweredState.requestedPower
						}

					} else if (part is FueledThruster) {
						val fueledState = ship.getPartState(thruster)[FueledPartState::class]

						val remainingFuel = ship.getCargoAmount(part.fuel)
						fueledState.totalFuelEnergyRemaining = fueledState.fuelEnergyRemaining + part.thrust.toLong() * part.fuelTime * remainingFuel

						if (fueledState.totalFuelEnergyRemaining > 0) {
							maxThrust += part.thrust
							thrust += part.thrust
						}

						if (thrustComponent != null && thrustComponent.thrusting) {
							consumeFuel(deltaGameTime, world.getEntity(entityID), ship, thruster, part.thrust.toLong(), part.thrust.toLong())
						}
					}
				}
			}
		}

		if (maxThrust == 0L) {

			if (thrustMapper.has(entityID)) {
				thrustMapper.remove(entityID)
			}

		} else {

			val thrustComponent: ThrustComponent

			if (!thrustMapper.has(entityID)) {
				thrustComponent = thrustMapper.create(entityID)

			} else {
				thrustComponent = thrustMapper.get(entityID)
			}

			thrustComponent.thrust = thrust
			thrustComponent.maxThrust = maxThrust
		}
	}
}