package se.exuvo.aurora.planetarysystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.systems.IteratingSystem
import net.mostlyoriginal.api.event.common.EventSystem
import org.apache.log4j.Logger
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
import se.exuvo.aurora.planetarysystems.events.getEvent
import se.exuvo.aurora.utils.*

class ShipSystem : IteratingSystem(FAMILY), PreSystem {
	companion object {
		val FAMILY = Aspect.all(ShipComponent::class.java)
	}

	val log = Logger.getLogger(this.javaClass)

	lateinit private var massMapper: ComponentMapper<MassComponent>
	lateinit private var shipMapper: ComponentMapper<ShipComponent>
	lateinit private var thrustMapper: ComponentMapper<ThrustComponent>
	lateinit private var nameMapper: ComponentMapper<NameComponent>
	lateinit private var events: EventSystem

	override fun preProcessSystem() {
		subscription.getEntities().forEach { entityID ->
			val ship = shipMapper.get(entityID)
			val thrusters = ship.shipClass.getPartRefs().filter({ it.part is ThrustingPart })
			val thrustComponent = thrustMapper.get(entityID)

			for (thruster in thrusters) {
				val part = thruster.part

				if (part is ElectricalThruster && ship.isPartEnabled(thruster)) {
					val poweredState = ship.getPartState(thruster)[PoweredPartState::class]

					if (thrustComponent != null && thrustComponent.thrusting) {
						if (poweredState.requestedPower != part.powerConsumption) {
							poweredState.requestedPower = part.powerConsumption
							events.dispatch(getEvent(PowerEvent::class).set(entityID))
						}
					} else {
						if (poweredState.requestedPower > 0) {
							poweredState.requestedPower = 0
							events.dispatch(getEvent(PowerEvent::class).set(entityID))
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

		var thrust = 0f
		var maxThrust = 0f
		val thrusters = ship.shipClass.getPartRefs().filter({ it.part is ThrustingPart })

		if (thrusters.isNotEmpty()) {
			val thrustComponent = thrustMapper.get(entityID)

			for (thruster in thrusters) {
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

		if (maxThrust == 0f) {

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