package se.exuvo.aurora.planetarysystems.components

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Vector2
import se.exuvo.aurora.utils.Vector2L

//TODO implement Poolable

// In N and degrees
data class ThrustComponent(var thrust: Float = 0f,
													 var maxThrust: Float = 0f,
													 var thrustAngle: Float = 0f,
													 var thrusting: Boolean = false
) : Component

enum class ApproachType {
	BRACHISTOCHRONE, // Arrive at target using a Brachistochrone trajectory
	BALLISTIC // Arrive at target as quickly as possible
}

data class MoveToEntityComponent(var target: Entity,
																 var approach: ApproachType = ApproachType.BRACHISTOCHRONE
) : Component
data class MoveToPositionComponent(var target: Vector2L,
																	 var approach: ApproachType = ApproachType.BRACHISTOCHRONE
) : Component
