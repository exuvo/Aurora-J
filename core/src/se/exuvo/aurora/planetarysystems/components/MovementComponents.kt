package se.exuvo.aurora.planetarysystems.components

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Vector2
import se.exuvo.aurora.utils.Vector2L

//TODO implement Poolable

// In m
data class PositionComponent(val position: Vector2L = Vector2L()) : Component {
	fun getXinKM(): Long {
		return (500 + position.x) / 1000L
	}

	fun getYinKM(): Long {
		return (500 + position.y) / 1000L
	}
}

// In N
data class ThrustComponent(var thrust: Float = 0f) : Component

// In m/s
data class VelocityComponent(var velocity: Vector2 = Vector2(), var thrustAngle: Float = 0f) : Component

enum class ApproachType {
	BRACHISTOCHRONE, // Arrive at target using a Brachistochrone trajectory
	BALLISTIC // Arrive at target as quickly as possible
}

data class MoveToEntityComponent(var target: Entity, var approach: ApproachType = ApproachType.BRACHISTOCHRONE) : Component
data class MoveToPositionComponent(var target: Vector2L, var approach: ApproachType = ApproachType.BRACHISTOCHRONE) : Component