package se.exuvo.aurora.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.MathUtils

data class RenderComponent(var zOrder: Float = .5f) : Component

data class CircleComponent(var radius: Float = 1f) : Component

data class LineComponent(var x: Float = 100f, var y: Float = 0f) : Component {

	fun setByAngle(length: Float, angle: Float) {
		x = length * MathUtils.cos(angle)
		y = length * MathUtils.sin(angle)
	}
}