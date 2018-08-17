package se.exuvo.aurora.planetarysystems.components

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Vector2
import se.exuvo.aurora.utils.Vector2L
import java.lang.RuntimeException

data class TimedValue<T>(val value: T, var time: Long) {
}

abstract class TimedComponent<T>() : Component {
	abstract fun get(time: Long): TimedValue<T>
}

//abstract class SteppedComponent<T>(var current: TimedValue<T>) : TimedComponent<T>() {
//
//	fun set(value: T, time: Long) {
//		if (time >= current.time) {
//			current.value = value;
//			current.time = time
//		}
//	}
//
//	override fun get(time: Long): T {
//		return current.value
//	}
//}

//TODO save all old points
abstract class InterpolatedComponent<T>(val initial: TimedValue<T>) : TimedComponent<T>() {
	var previous: TimedValue<T> = initial
	var interpolated: TimedValue<T>? = null
	var next: TimedValue<T>? = null

	abstract fun setValue(timedValue: TimedValue<T>, newValue: T)

	fun set(value: T, time: Long) {

		if (previous.time > time) {
			return
		}

		val next2 = next

		if (next2 != null && time >= next2.time) {
			next = null
		}

		setValue(previous, value)
		previous.time = time
	}

	open fun setPrediction(value: T, time: Long): Boolean {

		if (previous.time >= time) {
			next = null
			return false
		}

		val next2 = next

		if (next2 == null) {

			next = TimedValue<T>(value, time)

		} else {

			setValue(next2, value)
			next2.time = time
			next = next2
		}

		return true
	}

	abstract fun interpolate(time: Long)

	override fun get(time: Long): TimedValue<T> {

		val next2 = next

		if (time <= previous.time || next == null) {
			return previous;
		}

		if (next2 != null && time >= next2.time) {
			return next2
		}

		val interpolated2 = interpolated

		if (interpolated2 == null || interpolated2.time != time) {
			interpolate(time)
			interpolated!!.time = time
		}

		return interpolated!!
	}
}

// In m
data class MovementValues(val position: Vector2L, val velocity: Vector2) {
	fun getXinKM(): Long {
		return (500 + position.x) / 1000L
	}

	fun getYinKM(): Long {
		return (500 + position.y) / 1000L
	}
}

class TimedMovementComponent(val initialPosition: Vector2L = Vector2L(), val initialVelocity: Vector2 = Vector2(), val initialTime: Long = 0) : InterpolatedComponent<MovementValues>(TimedValue(MovementValues(initialPosition, initialVelocity), initialTime)) {
	var approach: ApproachType? = null
	var startAcceleration: Double? = null
	var finalAcceleration: Double? = null

	override fun setValue(timedValue: TimedValue<MovementValues>, newValue: MovementValues) {
		timedValue.value.position.set(newValue.position)
		timedValue.value.velocity.set(newValue.velocity)
	}

	fun set(position: Vector2L, velocity: Vector2, time: Long) {

		if (previous.time > time) {
			return
		}

		val next2 = next

		if (next2 != null && time >= next2.time) {
			next = null
		}

		previous.value.position.set(position)
		previous.value.velocity.set(velocity)
		previous.time = time
	}

	override fun setPrediction(value: MovementValues, time: Long): Boolean {
		val updated = super.setPrediction(value, time);

		if (updated) {
			approach = null
			startAcceleration = null
			finalAcceleration = null
		}

		return updated
	}

	fun setPredictionBrachistocrone(value: MovementValues, startAcceleration: Double, finalAcceleration: Double, time: Long): Boolean {

		if (setPrediction(value, time)) {
			approach = ApproachType.BRACHISTOCHRONE;
			this.startAcceleration = startAcceleration
			this.finalAcceleration = finalAcceleration
			return true
		}

		return false
	}

	// Only works with static targets and initial velocity in the target direction
	fun setPredictionBallistic(value: MovementValues, startAcceleration: Double, finalAcceleration: Double, time: Long): Boolean {

		if (setPrediction(value, time)) {
			approach = ApproachType.BALLISTIC;
			this.startAcceleration = startAcceleration
			this.finalAcceleration = finalAcceleration
			return true
		}

		return false
	}

	override fun interpolate(time: Long) {

		var interpolated = this.interpolated

		if (interpolated == null) {
			interpolated = TimedValue(MovementValues(Vector2L(), Vector2()), time)
			this.interpolated = interpolated
		}

		val startPosition = previous.value.position
		val endPosition = next!!.value.position
		val startTime = previous.time
		val endTime = next!!.time
		val startVelocity = previous.value.velocity
		val finalVelocity = next!!.value.velocity
		val startAcceleration = startAcceleration!!
		val finalAcceleration = finalAcceleration!!

		val position = interpolated.value.position
		val velocity = interpolated.value.velocity

		position.set(startPosition).sub(endPosition)
		val totalDistance = position.len()
		val travelTime = endTime - startTime
		val traveledTime = time - startTime

		if (traveledTime > travelTime) {
			throw RuntimeException("Invalid state: startTime $startTime, endTime $endTime, traveledTime $traveledTime");
		}

		when (approach) {
			ApproachType.BALLISTIC -> {
				val acceleration = startAcceleration + (finalAcceleration - startAcceleration) * (traveledTime / travelTime)
				var distanceTraveled = startVelocity.len().toDouble() * traveledTime.toDouble() + 0.5 * acceleration * traveledTime * traveledTime

				position.set(startPosition).sub(endPosition)
				val angle = position.angleRad() + Math.PI

				position.set(distanceTraveled.toLong(), 0).rotateRad(angle)
				position.add(startPosition)

				velocity.set(1f, 0f).rotateRad(angle.toFloat()).scl((acceleration * traveledTime).toFloat()).add(startVelocity)
			}
			ApproachType.BRACHISTOCHRONE -> {
				//TODO implement
			}
			else -> {
				throw RuntimeException("Unknown approach type: " + approach)
			}
		}
	}
}
