package se.exuvo.aurora.starsystems.components

import com.badlogic.gdx.math.Vector2
import se.exuvo.aurora.galactic.systems.GalacticRenderSystem
import se.exuvo.aurora.starsystems.StarSystem
import se.exuvo.aurora.utils.Vector2L
import com.artemis.Component

//TODO copy TimedMovementComponent
// In milli light years
class GalacticPositionComponent() : Component() {
	val position: Vector2L = Vector2L()
	
	fun set(position: Vector2L): GalacticPositionComponent {
		this.position.set(position)
		return this
	}
	
	fun getXinRender(): Float {
		return position.x / GalacticRenderSystem.RENDER_SCALE.toFloat()
	}

	fun getYinRender(): Float {
		return position.y / GalacticRenderSystem.RENDER_SCALE.toFloat()
	}
}

// In milli light years/s
data class GalacticVelocityComponent(var velocity: Vector2L = Vector2L(), var thrustAngle: Float = 0f) : Component()
