package se.exuvo.aurora.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector2

// In km
data class PositionComponent(var x: Long = 0, var y: Long = 0) : Component {
	
	
	fun getVector2(): Vector2 {
		
		return Vector2(x.toFloat(), y.toFloat())
	}
}