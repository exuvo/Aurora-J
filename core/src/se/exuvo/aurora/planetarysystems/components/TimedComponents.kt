package se.exuvo.aurora.planetarysystems.components

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Vector2
import se.exuvo.aurora.utils.Vector2L

data class TimedValue<T>(var value: T, var time: Long) {
}

abstract class TimedComponent<T>() : Component {
	abstract fun get(time: Long): T
}

abstract class SteppedComponent<T>(var current: TimedValue<T>) : TimedComponent<T>() {

	fun set(value: T, time: Long) {
		if (time >= current.time) {
			current.value = value;
			current.time = time
		}
	}

	override fun get(time: Long): T {
		return current.value
	}
}

abstract class InterpolatedComponent<T>(val initial: TimedValue<T>) : TimedComponent<T>() {
	var previous: TimedValue<T> = initial
	var interpolated: TimedValue<T> = previous
	var next: TimedValue<T>? = null

	fun set(value: T, time: Long) {

		if (previous.time > time) {
			return
		}

		val next2 = next

		if (next2 != null && next2.time >= time) {
			next = null
		}

		previous.value = value
		previous.time = time
	}

	fun setPrediction(value: T, time: Long): Boolean {

		if (previous.time >= time) {
			next = null
			return false
		}

		val next2 = next

		if (next2 == null) {

			next = TimedValue<T>(value, time)

		} else {

			next2.value = value
			next2.time = time
			next = next2
		}

		return true
	}

	abstract fun interpolate(time: Long)

	override fun get(time: Long): T {

		val next2 = next

		if (next2 != null && next2.time >= time) {
			return next2.value
		}

		if (previous.time >= time) {
			return previous.value;
		}

		if (interpolated.time != time) {
			interpolate(time)
		}

		return interpolated.value
	}
}

class TimedPositionComponent(val initialPosition: Vector2L = Vector2L(), val initialTime: Long) : InterpolatedComponent<Vector2L>(TimedValue(initialPosition, initialTime)) {
	var approach: ApproachType? = null
	var startVelocity: Vector2? = null
	var finalVelocity: Vector2? = null

	private fun resetPrediction() {
		approach = null
		startVelocity = null
		finalVelocity = null
	}
	
	fun setPredictionBrachistocrone(value: Vector2L, time: Long, startVelocity: Vector2, finalVelocity: Vector2): Boolean {
		
		if (setPrediction(value, time)) {
			approach = ApproachType.BRACHISTOCHRONE;
			this.startVelocity = startVelocity
			this.finalVelocity = finalVelocity
			return true
		}

		return false
	}
	
	fun setPredictionBallistic(value: Vector2L, time: Long, startVelocity: Vector2, finalVelocity: Vector2): Boolean {
		
		if (setPrediction(value, time)) {
			approach = ApproachType.BALLISTIC;
			this.startVelocity = startVelocity
			this.finalVelocity = finalVelocity
			return true
		}

		return false
	}

	override fun interpolate(time: Long) {
		
		val startPosition = previous.value
		val endPosition = next!!.value
		val startTime = previous.time
		val endTime = next!!.time
		val startVelocity = this.startVelocity!!
		val finalVelocity = this.finalVelocity!!
		
		val tempPosition = Vector2L()
		
		tempPosition.set(startPosition).sub(endPosition)
		val distance = tempPosition.len()
		val travelTime = endTime - startTime
		
		when (approach) {
			ApproachType.BRACHISTOCHRONE -> {


			}
			ApproachType.BALLISTIC -> {


			}
			else -> {
				throw RuntimeException("Unknown approach type: " + approach)
			}
		}
	}
}
