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

			powerComponent = PowerComponent()
			entity.add(powerComponent)

			val ship = shipMapper.get(entity)

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
		}
	}

	override fun entityRemoved(entity: Entity) {
	}

	override fun processEntity(entity: Entity, deltaGameTime: Float) {

		val powerComponent = powerMapper.get(entity)
		val ship = shipMapper.get(entity)
		val shipClass = ship.shipClass

		val powerWasStable = powerComponent.totalAvailiablePower > powerComponent.totalUsedPower
		
		powerComponent.poweringParts.forEach({
			val part = it
			val poweringState = ship.getPartState(part).get(PoweringPartState::class) 

			if (part is SolarPanel) {

				val irradiance = irradianceMapper.get(entity).irradiance
				val producedPower = ((100L * part.getVolume() * irradiance) / SOLAR_PANEL_THICKNESS * part.efficiency).toInt()
				
				powerComponent.totalAvailiablePower += producedPower - poweringState.availiablePower
				
				poweringState.availiablePower = producedPower
				poweringState.producedPower = producedPower

			} else if (part is Reactor) {


			} else if (part is Battery) {


			}
		})
		
		if (powerWasStable && powerComponent.totalAvailiablePower < powerComponent.totalUsedPower) {
			powerComponent.stateChanged = true
		}

		if (powerComponent.stateChanged) {


			powerComponent.stateChanged = false
		}
	}
}