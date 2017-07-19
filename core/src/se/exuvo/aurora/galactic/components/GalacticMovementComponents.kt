package se.exuvo.aurora.planetarysystems.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector2
import se.exuvo.aurora.galactic.systems.GalacticRenderSystem
import se.exuvo.aurora.planetarysystems.PlanetarySystem
import se.exuvo.aurora.utils.Vector2L

// In milli light years
data class GalacticPositionComponent(val position: Vector2L = Vector2L()) : Component {
	fun getXinRender(): Float {
		return position.x / GalacticRenderSystem.RENDER_SCALE.toFloat()
	}

	fun getYinRender(): Float {
		return position.y / GalacticRenderSystem.RENDER_SCALE.toFloat()
	}
}

// In light years/s
data class GalacticVelocityComponent(var velocity: Vector2 = Vector2(), var thrustAngle: Float = 0f) : Component

data class WarpToPlanetarySystemComponent(var target: PlanetarySystem) : Component
