package se.exuvo.aurora.planetarysystems.components

import com.artemis.Component
import com.badlogic.gdx.math.Vector2
import se.exuvo.aurora.utils.Vector2L
import java.lang.RuntimeException
import org.apache.commons.math3.util.FastMath

data class TimedValue<T>(val value: T, var time: Long) {
}

abstract class TimedComponent<T>() : Component() {
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
abstract class InterpolatedComponent<T>(initial: TimedValue<T>) : TimedComponent<T>() {
	var previous: TimedValue<T> = initial
	var interpolated: TimedValue<T>? = null
	var next: TimedValue<T>? = null

	protected abstract fun setValue(timedValue: TimedValue<T>, newValue: T)

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
			next = null // why?
			return false
		}

		val next2 = next

		if (next2 == null) {

			next = TimedValue<T>(value, time)

		} else {

			setValue(next2, value)
			next2.time = time
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

// In m, cm/s, cm/s²
data class MovementValues(val position: Vector2L, val velocity: Vector2L, val acceleration: Vector2L) {
	fun getXinKM(): Long {
		return (500 + position.x) / 1000L
	}

	fun getYinKM(): Long {
		return (500 + position.y) / 1000L
	}
}

class TimedMovementComponent() : InterpolatedComponent<MovementValues>(TimedValue(MovementValues(Vector2L(), Vector2L(), Vector2L()), 0L)) {
	var approach: ApproachType? = null
	var startAcceleration: Long? = null
	var finalAcceleration: Long? = null
	var aimTarget: Vector2L? = null

	override protected fun setValue(timedValue: TimedValue<MovementValues>, newValue: MovementValues) {
		timedValue.value.position.set(newValue.position)
		timedValue.value.velocity.set(newValue.velocity)
		timedValue.value.acceleration.set(newValue.acceleration)
	}
	
	fun set(x: Long, y: Long, vx: Long, vy: Long, ax: Long, ay: Long, time: Long): TimedMovementComponent {

		if (previous.time > time) {
			return this
		}

		val next2 = next

		if (next2 != null && time >= next2.time) {
			next = null
		}

		previous.value.position.set(x, y)
		previous.value.velocity.set(vx, vy)
		previous.value.acceleration.set(ax, ay)
		previous.time = time
		return this
	}
	
	fun set(position: Vector2L, velocity: Vector2L, acceleration: Vector2L, time: Long): TimedMovementComponent {

		if (previous.time > time) {
			return this
		}

		val next2 = next

		if (next2 != null && time >= next2.time) {
			next = null
		}

		previous.value.position.set(position)
		previous.value.velocity.set(velocity)
		previous.value.acceleration.set(acceleration)
		previous.time = time
		
		return this
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

	fun setPredictionBrachistocrone(value: MovementValues, startAcceleration: Long, time: Long): Boolean {

		if (setPrediction(value, time)) {
			approach = ApproachType.BRACHISTOCHRONE;
			this.startAcceleration = startAcceleration
			this.finalAcceleration = value.acceleration.len().toLong()
			return true
		}

		return false
	}

	// Only works with static targets and initial velocity in the target direction
	fun setPredictionBallistic(value: MovementValues, aimTarget: Vector2L, startAcceleration: Long, time: Long): Boolean {

		if (setPrediction(value, time)) {
			approach = ApproachType.BALLISTIC;
			this.startAcceleration = startAcceleration
			this.finalAcceleration = value.acceleration.len().toLong()
			this.aimTarget = aimTarget
			return true
		}

		return false
	}

	override fun interpolate(time: Long) {

		var interpolated = this.interpolated

		if (interpolated == null) {
			interpolated = TimedValue(MovementValues(Vector2L(), Vector2L(), Vector2L()), time)
			this.interpolated = interpolated
		}

		val startPosition = previous.value.position
//		val endPosition = next!!.value.position
		val aimPosition = aimTarget!!
		val startTime = previous.time
		val endTime = next!!.time
		val startVelocity = previous.value.velocity
//		val finalVelocity = next!!.value.velocity
		val startAcceleration = startAcceleration!!
		val finalAcceleration = finalAcceleration!!

		val position = interpolated.value.position
		val velocity = interpolated.value.velocity
		val acceleration = interpolated.value.acceleration

//		position.set(startPosition).sub(endPosition)
//		val totalDistance = position.len()
		val travelTime = endTime - startTime
		val traveledTime = time - startTime

		if (traveledTime > travelTime) {
			throw RuntimeException("Invalid state: startTime $startTime, endTime $endTime, traveledTime $traveledTime");
		}

		when (approach) {
			ApproachType.BALLISTIC -> {
				
				val averageAcceleration = (startAcceleration + finalAcceleration) / 2
				
				//TODO does not work well
				position.set(startVelocity).scl(traveledTime)
				
				var distanceTraveled = (averageAcceleration * traveledTime * traveledTime) / 2
				
				val angle = startPosition.angleToRad(aimPosition)

				val x = distanceTraveled * FastMath.cos(angle)
				val y = distanceTraveled * FastMath.sin(angle)
				
				position.add(x.toLong(), y.toLong())
				
				position.div(100).add(startPosition)

				velocity.set(averageAcceleration * traveledTime, 0).rotateRad(angle).add(startVelocity)
				
				acceleration.set(averageAcceleration, 0).rotateRad(angle)
			}
			ApproachType.BRACHISTOCHRONE -> {
				//TODO implement
				println("BRACHISTOCHRONE approach not implemented")
			}
			else -> {
				throw RuntimeException("Unknown approach type: " + approach)
			}
		}
	}
}
