package se.exuvo.aurora.planetarysystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.systems.IteratingSystem
import org.apache.log4j.Logger
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.planetarysystems.components.TimedLifeComponent
import se.exuvo.aurora.utils.GameServices

class TimedLifeSystem : IteratingSystem(ASPECT) {
	companion object {
		val ASPECT = Aspect.all(TimedLifeComponent::class.java)
	}

	val log = Logger.getLogger(this.javaClass)
	private val galaxy = GameServices[Galaxy::class]

	lateinit private var timedLifeMapper: ComponentMapper<TimedLifeComponent>

	override fun process(entityID: Int) {
		val timedLife = timedLifeMapper.get(entityID)

		if (timedLife.endTimes >= galaxy.time) {
			world.delete(entityID)
		}
	}
}
