package se.exuvo.aurora.components

import com.badlogic.ashley.core.Component
import se.exuvo.aurora.utils.Vector2Long

// In km
data class PositionComponent(val position: Vector2Long = Vector2Long()) : Component {
	
}