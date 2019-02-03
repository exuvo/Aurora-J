package se.exuvo.aurora.galactic

import org.apache.log4j.Logger
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import com.artemis.Entity
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Units


class Player(var name: String) {
	companion object {
		var current = Player("local")
	}

	private val log = Logger.getLogger(this.javaClass)
	private val galaxy by lazy { GameServices[Galaxy::class] }

	val empire: Empire? = null
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