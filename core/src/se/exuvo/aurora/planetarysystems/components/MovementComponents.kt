package se.exuvo.aurora.planetarysystems.components

import com.badlogic.gdx.math.Vector2
import se.exuvo.aurora.utils.Vector2L
import com.artemis.Component
import com.artemis.Entity
import com.artemis.annotations.PooledWeaver

// In N and degrees
class ThrustComponent() : Component() {
	var thrust: Float = 0f
	var maxThrust: Float = 0f
	var thrustAngle: Float = 0f
	var thrusting: Boolean = false

	fun set(thrust: Float,
					maxThrust: Float,
					thrustAngle: Float,
					thrusting: Boolean
	): ThrustComponent {
		this.thrust = thrust
		this.maxThrust = maxThrust
		this.thrustAngle = thrustAngle
		this.thrusting = thrusting
		return this
	}
}

enum class ApproachType {
	BRACHISTOCHRONE, // Arrive at target using a Brachistochrone trajectory
	BALLISTIC // Arrive at target as quickly as possible
}

class MoveToEntityComponent() : Component() {
	var targetID: Int = -1
	lateinit var approach: ApproachType

	fun set(targetID: Int,
					approach: ApproachType = ApproachType.BRACHISTOCHRONE
	): MoveToEntityComponent {
		this.targetID = targetID
		this.approach = approach
		return this
	}
}

class MoveToPositionComponent() : Component() {
	lateinit var target: Vector2L
	lateinit var approach: ApproachType

	fun set(target: Vector2L,
					approach: ApproachType = ApproachType.BRACHISTOCHRONE
	): MoveToPositionComponent {
		this.target = target
		this.approach = approach
		return this
	}
}
