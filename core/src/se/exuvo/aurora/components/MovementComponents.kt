package se.exuvo.aurora.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector2
import se.exuvo.aurora.utils.Vector2L

// In km
data class PositionComponent(val position: Vector2L = Vector2L()) : Component

// In km/s
data class SpeedComponent(var speed: Long = 0) : Component