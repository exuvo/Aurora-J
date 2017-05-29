package se.exuvo.aurora.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.SortedIteratingSystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.Viewport
import se.exuvo.aurora.Assets
import se.exuvo.aurora.components.CircleComponent
import se.exuvo.aurora.components.LineComponent
import se.exuvo.aurora.components.NameComponent
import se.exuvo.aurora.components.PositionComponent
import se.exuvo.aurora.components.RenderComponent
import se.exuvo.aurora.components.TagComponent
import se.exuvo.aurora.components.TextComponent
import se.exuvo.aurora.components.TintComponent
import se.exuvo.aurora.screens.GameScreenService
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Vector2L
import java.util.Comparator

fun getCircleSegments(radius: Float, zoom: Float): Int {
	return Math.max(3, (10 * Math.cbrt((radius / zoom).toDouble())).toInt())
}

class RenderSystem : SortedIteratingSystem(RenderSystem.FAMILY, ZOrderComparator()) {
	companion object {
		val FAMILY = Family.all(PositionComponent::class.java, RenderComponent::class.java).one(CircleComponent::class.java, LineComponent::class.java).get()
	}

	private val circleMapper = ComponentMapper.getFor(CircleComponent::class.java)
	private val lineMapper = ComponentMapper.getFor(LineComponent::class.java)
	private val tintMapper = ComponentMapper.getFor(TintComponent::class.java)
	private val positionMapper = ComponentMapper.getFor(PositionComponent::class.java)
	private val tagMapper = ComponentMapper.getFor(TagComponent::class.java)
	private val textMapper = ComponentMapper.getFor(TextComponent::class.java)
	private val nameMapper = ComponentMapper.getFor(NameComponent::class.java)
	private val shapeRenderer = GameServices[ShapeRenderer::class.java]
	private val batch = GameServices[SpriteBatch::class.java]
	private val uiCamera = GameServices[GameScreenService::class.java].uiCamera
	private val groupSystem by lazy { engine.getSystem(GroupSystem::class.java) }

	override fun checkProcessing() = false

	override fun processEntity(entity: Entity, renderDelta: Float) {}


	fun drawEntities(entities: ImmutableArray<Entity>, viewport: Viewport, cameraOffset: Vector2L) {

		val zoom = (viewport.camera as OrthographicCamera).zoom

		for (entity in entities) {
			val position = positionMapper.get(entity).position
			val tintComponent = tintMapper.get(entity)
			val x = (position.x - cameraOffset.x).toFloat()
			val y = (position.y - cameraOffset.y).toFloat()

			val color = Color(tintComponent?.color ?: Color.WHITE)
			shapeRenderer.color = color

			if (circleMapper.has(entity)) {

				val circle = circleMapper.get(entity)
				shapeRenderer.circle(x, y, circle.radius, getCircleSegments(circle.radius, zoom))

			} else if (lineMapper.has(entity)) {

				val line = lineMapper.get(entity)
				shapeRenderer.line(x, y, x + line.x, y + line.y)
			}
		}
	}

	fun drawSelections(entities: ImmutableArray<Entity>, viewport: Viewport, cameraOffset: Vector2L) {

		val selectedEntities = groupSystem.get(GroupSystem.SELECTED)
		val zoom = (viewport.camera as OrthographicCamera).zoom

		for (entity in entities) {
			if (selectedEntities.contains(entity)) {
				val position = positionMapper.get(entity).position
				val x = (position.x - cameraOffset.x).toFloat()
				val y = (position.y - cameraOffset.y).toFloat()

				shapeRenderer.color = Color.RED

				if (circleMapper.has(entity)) {

					val circle = circleMapper.get(entity)
					val radius = circle.radius * 1.1f + 2 * zoom
					val segments = getCircleSegments(radius, zoom)
					println("radius $radius, segments $segments")
					shapeRenderer.circle(x, y, radius, segments)
				}
			}
		}
	}

	fun render(viewport: Viewport, cameraOffset: Vector2L) {

		val sortedEntities = getEntities()

		viewport.apply()
		shapeRenderer.projectionMatrix = viewport.camera.combined

		shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
		drawEntities(sortedEntities, viewport, cameraOffset)
		shapeRenderer.end()

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
		drawSelections(sortedEntities, viewport, cameraOffset)
		shapeRenderer.end()

		batch.projectionMatrix = uiCamera.combined
		batch.begin()

		val font = Assets.fontMap
		font.color = Color.GREEN
		val zoom = (viewport.camera as OrthographicCamera).zoom
		val screenPosition = Vector3()

		entities.filter { nameMapper.has(it) }.forEach {
			val position = positionMapper.get(it).position
			val name = nameMapper.get(it).name!!

			var height = 0f

			if (circleMapper.has(it)) {

				val circleComponent = circleMapper.get(it)
				height = circleComponent.radius

			} else if (lineMapper.has(it)) {

				val lineComponent = lineMapper.get(it)
				height = lineComponent.y
			}

			val x = (position.x - cameraOffset.x).toFloat()
			val y = (position.y - cameraOffset.y).toFloat()

			screenPosition.set(x, y - height * .5f, 0f)
			viewport.camera.project(screenPosition)

			font.draw(batch, name, screenPosition.x - name.length * font.spaceWidth * .5f, screenPosition.y - font.lineHeight / zoom)
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
