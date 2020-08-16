package se.exuvo.aurora.starsystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.artemis.systems.IteratingSystem
import net.mostlyoriginal.api.event.common.EventSystem
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.galactic.ElectricalThruster
import se.exuvo.aurora.galactic.FueledThruster
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.Shield
import se.exuvo.aurora.galactic.ThrustingPart
import se.exuvo.aurora.starsystems.PreSystem
import se.exuvo.aurora.starsystems.StarSystem
import se.exuvo.aurora.starsystems.components.CargoComponent
import se.exuvo.aurora.starsystems.components.ChargedPartState
import se.exuvo.aurora.starsystems.components.FueledPartState
import se.exuvo.aurora.starsystems.components.MassComponent
import se.exuvo.aurora.starsystems.components.NameComponent
import se.exuvo.aurora.starsystems.components.PartStatesComponent
import se.exuvo.aurora.starsystems.components.PoweredPartState
import se.exuvo.aurora.starsystems.components.ShieldComponent
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.components.ThrustComponent
import se.exuvo.aurora.starsystems.events.PowerEvent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.forEachFast

class ShipSystem : IteratingSystem(FAMILY), PreSystem {
	companion object {
		@JvmField val FAMILY = Aspect.all(ShipComponent::class.java)
		@JvmField val log = LogManager.getLogger(ShipSystem::class.java)
	}

	lateinit private var massMapper: ComponentMapper<MassComponent>
	lateinit private var shipMapper: ComponentMapper<ShipComponent>
	lateinit private var thrustMapper: ComponentMapper<ThrustComponent>
	lateinit private var nameMapper: ComponentMapper<NameComponent>
	lateinit private var partStatesMapper: ComponentMapper<PartStatesComponent>
	lateinit private var cargoMapper: ComponentMapper<CargoComponent>
	lateinit private var shieldMapper: ComponentMapper<ShieldComponent>
	
	lateinit private var events: EventSystem
	lateinit private var powerSystem: PowerSystem
	
	@Wire
	lateinit private var starSystem: StarSystem
	private val galaxy = GameServices[Galaxy::class]

	override fun preProcessSystem() {
		subscription.getEntities().forEachFast { entityID ->
			val ship = shipMapper.get(entityID)
			val cargo = cargoMapper.get(entityID)
			val partStates = partStatesMapper.get(entityID)
			val thrustComponent = thrustMapper.get(entityID)
			
			var powerChanged = false
			
			ship.hull.thrusters.forEachFast{ thruster ->
				val part = thruster.part

				if (part is ElectricalThruster && partStates.isPartEnabled(thruster)) {
					val poweredState = partStates[thruster][PoweredPartState::class]

					if (thrustComponent != null && thrustComponent.thrusting) {
						if (poweredState.requestedPower != part.powerConsumption) {
							poweredState.requestedPower = part.powerConsumption
							powerChanged = true
						}
					} else {
						if (poweredState.requestedPower > 0) {
							poweredState.requestedPower = 0
							powerChanged = true
						}
					}
				}
			}
			
			ship.hull.shields.forEachFast { shield ->
				val part = shield.part as Shield
				val poweredState = partStates[shield][PoweredPartState::class]
				val chargedState = partStates[shield][ChargedPartState::class]
				
				val leftToCharge = part.capacitor - chargedState.charge
				val requestedPower = maxOf(0, minOf((leftToCharge * 100) / part.efficiency, part.powerConsumption))
				
				if (requestedPower != poweredState.requestedPower) {
					poweredState.requestedPower = requestedPower
					
					if (requestedPower > 0) {
						chargedState.expectedFullAt = galaxy.time + (leftToCharge + requestedPower - 1) / requestedPower
					} else {
						chargedState.expectedFullAt = 0
					}
					
					powerChanged = true
				}
			}
			
			if (!massMapper.has(entityID) || cargo.cargoChanged) {
				massMapper.create(entityID).set((ship.hull.emptyMass + cargo.mass).toDouble())
				cargo.cargoChanged = false
			}
			
			if (powerChanged) {
				events.dispatch(starSystem.getEvent(PowerEvent::class).set(entityID))
			}
		}
	}

	override fun process(entityID: Int) {
		val deltaGameTime = world.getDelta().toInt()

		val ship = shipMapper.get(entityID)
		val cargo = cargoMapper.get(entityID)
		val partStates = partStatesMapper.get(entityID)
		
		var thrust = 0L
		var maxThrust = 0L

		if (ship.hull.thrusters.isNotEmpty()) {
			val thrustComponent = thrustMapper.get(entityID)
			
			ship.hull.thrusters.forEachFast{ thruster ->
				if (partStates.isPartEnabled(thruster)) {
					val part = thruster.part

					if (part is ElectricalThruster) {
						val poweredState = partStates[thruster][PoweredPartState::class]

						maxThrust += part.thrust

						if (poweredState.requestedPower > 0) {
							thrust += part.thrust * poweredState.givenPower / poweredState.requestedPower
						}

					} else if (part is FueledThruster) {
						val fueledState = partStates[thruster][FueledPartState::class]

						val remainingFuel = cargo.getCargoAmount(part.fuel)
						fueledState.totalFuelEnergyRemaining = fueledState.fuelEnergyRemaining + part.thrust * part.fuelTime * remainingFuel

						if (fueledState.totalFuelEnergyRemaining > 0) {
							maxThrust += part.thrust
							thrust += part.thrust
						}

						if (thrustComponent != null && thrustComponent.thrusting) {
							powerSystem.consumeFuel(deltaGameTime, entityID, thruster, part.thrust, part.thrust, partStates, cargo)
							starSystem.changed(entityID, cargoMapper)
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

			if (thrustComponent.thrust != thrust || thrustComponent.maxThrust != maxThrust) {
				thrustComponent.thrust = thrust
				thrustComponent.maxThrust = maxThrust
				
				starSystem.changed(entityID, thrustMapper)
			}
		}
		
		var shieldsChanged = false
		
		for (i in 0 until ship.hull.shields.size) {
			val shield = ship.hull.shields[i]
			val poweredState = partStates[shield][PoweredPartState::class]
			
			if (poweredState.givenPower > 0) {
				shieldsChanged = true
				break
			}
		}
		
		if (shieldsChanged) {
			val shieldC = shieldMapper.get(entityID)
			var shieldAmount = 0L
			
			for (i in 0 until ship.hull.shields.size) {
				val shield = ship.hull.shields[i]
				val chargedState = partStates[shield][ChargedPartState::class]
				shieldAmount += chargedState.charge
			}
			
			shieldC.shieldHP = shieldAmount
			starSystem.changed(entityID, shieldMapper)
		}
	}
}