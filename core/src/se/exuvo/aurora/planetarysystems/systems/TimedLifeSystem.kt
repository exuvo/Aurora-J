package se.exuvo.aurora.planetarysystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.systems.IteratingSystem
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.planetarysystems.components.TimedLifeComponent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.planetarysystems.PlanetarySystem
import com.artemis.annotations.Wire

class TimedLifeSystem : IteratingSystem(ASPECT) {
	companion object {
		val ASPECT = Aspect.all(TimedLifeComponent::class.java)
	}

	val log = LogManager.getLogger(this.javaClass)
	private val galaxy = GameServices[Galaxy::class]
	
	@Wire
	lateinit private var planetarySystem: PlanetarySystem

	lateinit private var timedLifeMapper: ComponentMapper<TimedLifeComponent>

	override fun process(entityID: Int) {
		val timedLife = timedLifeMapper.get(entityID)

		if (timedLife.endTimes >= galaxy.time) {
			planetarySystem.destroyEntity(entityID)
		}
	}
}
