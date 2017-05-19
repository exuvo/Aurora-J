package se.exuvo.aurora.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.thedeadpixelsociety.ld34.components.OrbitComponent
import com.thedeadpixelsociety.ld34.components.PositionComponent
import se.exuvo.aurora.Galaxy
import se.exuvo.aurora.utils.GameServices

class OrbitSystem : DailyIteratingSystem(OrbitSystem.FAMILY) {
	companion object {
		val FAMILY = Family.all(OrbitComponent::class.java, PositionComponent::class.java).get()
	}
	
	private val orbitMapper = ComponentMapper.getFor(OrbitComponent::class.java)
	private val positionMapper = ComponentMapper.getFor(PositionComponent::class.java)

	override fun processEntity(entity: Entity) {

		val orbit = orbitMapper.get(entity);
		val position = positionMapper.get(entity);
		
		val parentEntity = orbit.parent;
		
		val day = galaxy.day
	}
}