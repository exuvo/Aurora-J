package se.exuvo.aurora.galactic

import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Units


class Player(var name: String) {
	companion object {
		@JvmField var current = Player("local")
	}

	private val log = LogManager.getLogger(this.javaClass)
	private val galaxy by lazy (LazyThreadSafetyMode.NONE) { GameServices[Galaxy::class] }

	var empire: Empire? = null
	var speedSteps = listOf(1, 4, 10, 50, 200, 1000, 5000, 25000, 60000)
	var speedIndex = 0
	var requestedSpeed = Units.NANO_SECOND / speedSteps[speedIndex]

	init {
		//TODO read speedSteps from config
	}
	
	fun increaseSpeed() {
		if (speedIndex < speedSteps.size - 1) {
			speedIndex++
		}
		
		requestedSpeed = java.lang.Long.signum(requestedSpeed) * Units.NANO_SECOND / speedSteps[speedIndex]
		galaxy.updateSpeed()
	}
	
	fun decreaseSpeed() {
		if (speedIndex > 0) {
			speedIndex--
		}
		
		requestedSpeed = java.lang.Long.signum(requestedSpeed) * Units.NANO_SECOND / speedSteps[speedIndex]
		galaxy.updateSpeed()
	}
	
	fun pauseSpeed() {
		requestedSpeed = -requestedSpeed
		galaxy.updateSpeed()
	}
	
}