package se.exuvo.aurora.planetarysystems.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import org.apache.log4j.Logger
import se.exuvo.aurora.galactic.ElectricalThruster
import se.exuvo.aurora.galactic.FueledThruster
import se.exuvo.aurora.galactic.ThrustingPart
import se.exuvo.aurora.planetarysystems.components.MassComponent
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.planetarysystems.components.ShipComponent
import se.exuvo.aurora.planetarysystems.components.ThrustComponent
import se.exuvo.aurora.planetarysystems.components.PoweredPartState
import se.exuvo.aurora.planetarysystems.components.FueledPartState
import se.exuvo.aurora.utils.consumeFuel

class ShipSystem : IteratingSystem(FAMILY) {
	companion object {
		val FAMILY = Family.all(ShipComponent::class.java).get()
	}

	val log = Logger.getLogger(this.javaClass)

	private val massMapper = ComponentMapper.getFor(MassComponent::class.java)
	private val shipMapper = ComponentMapper.getFor(ShipComponent::class.java)
	private val thrustMapper = ComponentMapper.getFor(ThrustComponent::class.java)
	private val nameMapper = ComponentMapper.getFor(NameComponent::class.java)
	
	override fun processEntity(entity: Entity, deltaGameTime: Float) {

		val ship = shipMapper.get(entity)
		
		var thrust = 0f
		var maxThrust = 0f
		val thrusters = ship.shipClass.getPartRefs().filter({it.part is ThrustingPart})

		if (thrusters.isNotEmpty()) {
			val thrustComponent = thrustMapper.get(entity)
			
			for (thruster in thrusters) {
				if (ship.isPartEnabled(thruster)) {
					val part = thruster.part
					
					if (part is ElectricalThruster) {
						val poweredState = ship.getPartState(thruster)[PoweredPartState::class]

						maxThrust += part.thrust
						thrust += part.thrust * poweredState.givenPower / poweredState.requestedPower
						
						if (thrustComponent != null && thrustComponent.thrusting) {
							poweredState.requestedPower = part.powerConsumption
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
							consumeFuel(deltaGameTime.toInt(), entity, ship, thruster, part.thrust.toLong(), part.thrust.toLong())
						}
					}
				}
			}
		}
		
		if (thrust == 0f) {
			
			if (thrustMapper.has(entity)) {
				entity.remove(ThrustComponent::class.java)
			}
			
		} else {
			
			if (!thrustMapper.has(entity)) {
				entity.add(ThrustComponent())
			}

			val thrustComponent = thrustMapper.get(entity)			
			thrustComponent.thrust = thrust
		}
				
		val massComponent = massMapper.get(entity)

	}
}