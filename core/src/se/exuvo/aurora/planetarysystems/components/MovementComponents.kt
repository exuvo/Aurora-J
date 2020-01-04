package se.exuvo.aurora.planetarysystems.components

import com.badlogic.gdx.math.Vector2
import se.exuvo.aurora.utils.Vector2L
import com.artemis.Component
import com.artemis.Entity
import com.artemis.annotations.PooledWeaver
import com.artemis.PooledComponent

// In N and degrees
class ThrustComponent() : PooledComponent() {
	var thrust: Long = 0
	var maxThrust: Long = 0
	var thrustAngle: Float = 0f
	var thrusting: Boolean = false

	fun set(thrust: Long,
					maxThrust: Long,
					thrustAngle: Float,
					thrusting: Boolean
	): ThrustComponent {
		this.thrust = thrust
		this.maxThrust = maxThrust
		this.thrustAngle = thrustAngle
		this.thrusting = thrusting
		return this
	}
	
	override fun reset(): Unit {}
}

enum class ApproachType {
	BRACHISTOCHRONE, // Arrive at target using a Brachistochrone trajectory
	BALLISTIC, // Arrive at target as quickly as possible
	COAST
}

class MoveToEntityComponent() : PooledComponent() {
	var targetID: Int = -1
	lateinit var approach: ApproachType

	fun set(targetID: Int,
					approach: ApproachType = ApproachType.BRACHISTOCHRONE
	): MoveToEntityComponent {
		this.targetID = targetID
		this.approach = approach
		return this
	}
	
	override fun reset(): Unit {}
}

class MoveToPositionComponent() : PooledComponent() {
	lateinit var target: Vector2L
	lateinit var approach: ApproachType

	fun set(target: Vector2L,
					approach: ApproachType = ApproachType.BRACHISTOCHRONE
	): MoveToPositionComponent {
		this.target = target
		this.approach = approach
		return this
	}
	
	override fun reset(): Unit {}
}

class OnPredictedMovementComponent() : PooledComponent() {
	override fun reset(): Unit {}
}