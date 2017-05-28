package se.exuvo.aurora.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.SortedIteratingSystem
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.Viewport
import se.exuvo.aurora.Assets
import se.exuvo.aurora.components.CircleComponent
import se.exuvo.aurora.components.GroupComponent
import se.exuvo.aurora.components.LineComponent
import se.exuvo.aurora.components.NameComponent
import se.exuvo.aurora.components.PositionComponent
import se.exuvo.aurora.components.RenderComponent
import se.exuvo.aurora.components.TagComponent
import se.exuvo.aurora.components.TextComponent
import se.exuvo.aurora.components.TintComponent
import se.exuvo.aurora.screens.GameScreenService
import se.exuvo.aurora.utils.GameServices
import java.util.Comparator

class RenderSystem : SortedIteratingSystem(RenderSystem.FAMILY, ZOrderComparator()) {
	companion object {
		val FAMILY = Family.all(PositionComponent::class.java, RenderComponent::class.java).one(CircleComponent::class.java, LineComponent::class.java).get()
	}

	private val circleMapper = ComponentMapper.getFor(CircleComponent::class.java)
	private val lineMapper = ComponentMapper.getFor(LineComponent::class.java)
	private val tintMapper = ComponentMapper.getFor(TintComponent::class.java)
	private val positionMapper = ComponentMapper.getFor(PositionComponent::class.java)
	private val tagMapper = ComponentMapper.getFor(TagComponent::class.java)
	private val groupMapper = ComponentMapper.getFor(GroupComponent::class.java)
	private val textMapper = ComponentMapper.getFor(TextComponent::class.java)
	private val nameMapper = ComponentMapper.getFor(NameComponent::class.java)
	private val shapeRenderer by lazy { GameServices[ShapeRenderer::class.java] }
	private val batch by lazy { GameServices[SpriteBatch::class.java] }
	private val uiCamera by lazy { GameServices[GameScreenService::class.java].uiCamera }

	override fun checkProcessing() = false

	override fun processEntity(entity: Entity, deltaTime: Float) {

		val positionComponent = positionMapper.get(entity)
		val tintComponent = tintMapper.get(entity)

		val color = Color(tintComponent?.color ?: Color.WHITE)
		shapeRenderer.color = color

		if (circleMapper.has(entity)) {

			val circle = circleMapper.get(entity)
			shapeRenderer.circle(positionComponent.x.toFloat(), positionComponent.y.toFloat(), circle.radius)

		} else if (lineMapper.has(entity)) {

			val line = lineMapper.get(entity)
			shapeRenderer.line(positionComponent.x.toFloat(), positionComponent.y.toFloat(), positionComponent.x.toFloat() + line.x, positionComponent.y.toFloat() + line.y)
		}
	}

	fun render(viewport: Viewport) {
		begin(viewport)
		super.update(0f)
		shapeRenderer.end()

		batch.projectionMatrix = uiCamera.combined
		batch.begin()
		shapeRenderer.color = Color.WHITE
		
		val font = Assets.fontMap
		val zoom = (viewport.camera as OrthographicCamera).zoom
		val screenPosition = Vector3()
		
		entities.filter { nameMapper.has(it) }.forEach {
			val positionComponent = positionMapper.get(it)
			val nameComponent = nameMapper.get(it)

			val text = nameComponent.name!!
			var height = 0f

			if (circleMapper.has(it)) {

				val circleComponent = circleMapper.get(it)
				height = circleComponent.radius

			} else if (lineMapper.has(it)) {

				val lineComponent = lineMapper.get(it)
				height = lineComponent.y
			}

			screenPosition.set(positionComponent.x.toFloat(), positionComponent.y.toFloat() - height * .5f, 0f)
			viewport.camera.project(screenPosition)

			font.draw(batch, text, screenPosition.x  - text.length * font.spaceWidth * .5f, screenPosition.y - font.lineHeight / zoom)
		}

		batch.end()
	}
	
//	private fun drawDottedLine(dotDist: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
//		
//		val vec2 = Vector2(x2, y2).sub(Vector2(x1, y1))
//		val length = vec2.len();
//		shapeRenderer.begin(ShapeRenderer.ShapeType.Point);
//
//		var i = 0f
//		while (i < length) {
//			vec2.clamp(length - i, length - i);
//			shapeRenderer.point(x1 + vec2.x, y1 + vec2.y, 0f);
//			i += dotDist
//		}
//		shapeRenderer.end();
//	}

	private fun begin(viewport: Viewport) {
		viewport.apply()
		shapeRenderer.projectionMatrix = viewport.camera.combined
		shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
	}

	private fun beginBatch(viewport: Viewport) {
		batch.projectionMatrix = viewport.camera.combined
		batch.begin()
	}
}

class ZOrderComparator : Comparator<Entity> {

	private val renderMapper = ComponentMapper.getFor(RenderComponent::class.java)

	override fun compare(o1: Entity, o2: Entity): Int {
		val r1 = renderMapper.get(o1)
		val r2 = renderMapper.get(o2)

		return r1.zOrder.compareTo(r2.zOrder)
	}
}
