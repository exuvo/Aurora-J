package se.exuvo.aurora.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.Vector2
import org.apache.log4j.Logger
import se.exuvo.aurora.components.ApproachType
import se.exuvo.aurora.components.MassComponent
import se.exuvo.aurora.components.MoveToEntityComponent
import se.exuvo.aurora.components.NameComponent
import se.exuvo.aurora.components.PositionComponent
import se.exuvo.aurora.components.ShipComponent
import se.exuvo.aurora.components.ThrustComponent
import se.exuvo.aurora.components.VelocityComponent
import se.exuvo.aurora.utils.Vector2L

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

		val shipComponent = shipMapper.get(entity)
		val mass = massMapper.get(entity).mass
		val thrust = thrustMapper.get(entity).thrust

		//TODO calculate power need, availability and drain resources from reactors
	}
}