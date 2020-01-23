package se.exuvo.aurora.starsystems.systems

import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.utils.GameServices
import com.artemis.Aspect.Builder
import com.artemis.systems.IteratingSystem
import com.artemis.Aspect
import com.artemis.BaseSystem

//TODO add preProcessEntity

abstract class DailyIteratingSystem(aspect: Aspect.Builder) : IteratingSystem(aspect) {

	val galaxy = GameServices[Galaxy::class]
	var lastDay: Int = -1

	override fun checkProcessing(): Boolean {
		if (galaxy.day > lastDay) {
			lastDay = galaxy.day
			return true
		}
		
		return false
	}
}

abstract class DailySystem() : BaseSystem() {

	val galaxy = GameServices[Galaxy::class]
	var lastDay: Int = galaxy.day - 1

	override fun checkProcessing(): Boolean {
		if (galaxy.day > lastDay) {
			lastDay = galaxy.day
			return true
		}
		
		return false
	}
	
	fun runOnNextUpdate() {
		lastDay = galaxy.day - 1
	}
}

abstract class GalaxyTimeIntervalIteratingSystem(aspect: Aspect.Builder, val interval: Long) : IteratingSystem(aspect) {

	val galaxy = GameServices[Galaxy::class]
	var lastTime: Long = galaxy.time - interval - 1

	override fun checkProcessing(): Boolean {
		if (galaxy.time - lastTime >= interval) {
			lastTime = galaxy.time
			return true
		}
		
		return false
	}

	fun runOnNextUpdate() {
		lastTime = galaxy.time - interval - 1
	}
}

abstract class GalaxyTimeIntervalSystem(val interval: Long) : BaseSystem() {

	val galaxy = GameServices[Galaxy::class]
	var lastTime: Long = galaxy.time - interval - 1

	override fun checkProcessing(): Boolean {
		if (galaxy.time - lastTime >= interval) {
			lastTime = galaxy.time
			return true
		}
		
		return false
	}
	
	fun runOnNextUpdate() {
		lastTime = galaxy.time - interval - 1
	}
}