package se.exuvo.aurora.planetarysystems.systems

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
import se.exuvo.aurora.planetarysystems.components.CircleComponent
import se.exuvo.aurora.planetarysystems.components.LineComponent
import se.exuvo.aurora.planetarysystems.components.MoveToEntityComponent
import se.exuvo.aurora.planetarysystems.components.MoveToPositionComponent
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.planetarysystems.components.PositionComponent
import se.exuvo.aurora.planetarysystems.components.RenderComponent
import se.exuvo.aurora.planetarysystems.components.StrategicIconComponent
import se.exuvo.aurora.planetarysystems.components.TagComponent
import se.exuvo.aurora.planetarysystems.components.TextComponent
import se.exuvo.aurora.planetarysystems.components.TintComponent
import se.exuvo.aurora.planetarysystems.components.VelocityComponent
import se.exuvo.aurora.screens.GameScreenService
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Vector2L
import java.util.Comparator

class RenderSystem : SortedIteratingSystem(FAMILY, ZOrderComparator()) {
	companion object {
		val FAMILY = Family.all(PositionComponent::class.java, RenderComponent::class.java).one(CircleComponent::class.java, LineComponent::class.java).get()
		val STRATEGIC_ICON_SIZE = 24f
	}

	private val circleMapper = ComponentMapper.getFor(CircleComponent::class.java)
	private val lineMapper = ComponentMapper.getFor(LineComponent::class.java)
	private val tintMapper = ComponentMapper.getFor(TintComponent::class.java)
	private val positionMapper = ComponentMapper.getFor(PositionComponent::class.java)
	private val tagMapper = ComponentMapper.getFor(TagComponent::class.java)
	private val textMapper = ComponentMapper.getFor(TextComponent::class.java)
	private val nameMapper = ComponentMapper.getFor(NameComponent::class.java)
	private val moveToEntityMapper = ComponentMapper.getFor(MoveToEntityComponent::class.java)
	private val moveToPositionMapper = ComponentMapper.getFor(MoveToPositionComponent::class.java)
	private val strategicIconMapper = ComponentMapper.getFor(StrategicIconComponent::class.java)
	private val velocityMapper = ComponentMapper.getFor(VelocityComponent::class.java)

	private val shapeRenderer = GameServices[ShapeRenderer::class.java]
	private val spriteBatch = GameServices[SpriteBatch::class.java]
	private val uiCamera = GameServices[GameScreenService::class.java].uiCamera
	private val groupSystem by lazy { engine.getSystem(GroupSystem::class.java) }

	override fun checkProcessing() = false

	override fun processEntity(entity: Entity, renderDelta: Float) {}

	fun drawEntities(entities: ImmutableArray<Entity>, viewport: Viewport, cameraOffset: Vector2L) {

		shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

		val zoom = (viewport.camera as OrthographicCamera).zoom

		for (entity in entities) {
			val position = positionMapper.get(entity)
			val tintComponent = if (tintMapper.has(entity)) tintMapper.get(entity) else null
			val x = (position.getXinKM() - cameraOffset.x).toFloat()
			val y = (position.getYinKM() - cameraOffset.y).toFloat()

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

		shapeRenderer.end()
	}

	fun drawEntityCenters(entities: ImmutableArray<Entity>, viewport: Viewport, cameraOffset: Vector2L) {

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
		shapeRenderer.color = Color.PINK

		val zoom = (viewport.camera as OrthographicCamera).zoom

		for (entity in entities) {
			val position = positionMapper.get(entity)
			val x = (position.getXinKM() - cameraOffset.x).toFloat()
			val y = (position.getYinKM() - cameraOffset.y).toFloat()

			if (circleMapper.has(entity)) {
				val circle = circleMapper.get(entity)
				shapeRenderer.circle(x, y, circle.radius * 0.01f, getCircleSegments(circle.radius * 0.01f, zoom))
			}
		}

		shapeRenderer.end()
	}

	fun inStrategicView(entity: Entity, zoom: Float): Boolean {

		val radius = circleMapper.get(entity).radius
		return radius / zoom < 5f
	}

	fun drawStrategicEntities(entities: ImmutableArray<Entity>, viewport: Viewport, cameraOffset: Vector2L) {

		val zoom = (viewport.camera as OrthographicCamera).zoom

		spriteBatch.begin()

		for (entity in entities) {
			if (strategicIconMapper.has(entity) && circleMapper.has(entity)) {

				if (inStrategicView(entity, zoom)) {

					val position = positionMapper.get(entity)
					val tintComponent = if (tintMapper.has(entity)) tintMapper.get(entity) else null
					var x = (position.getXinKM() - cameraOffset.x).toFloat()
					var y = (position.getYinKM() - cameraOffset.y).toFloat()
					val texture = strategicIconMapper.get(entity).texture

					val color = Color(tintComponent?.color ?: Color.WHITE)

					// https://github.com/libgdx/libgdx/wiki/Spritebatch%2C-Textureregions%2C-and-Sprites
					spriteBatch.setColor(color.r, color.g, color.b, color.a);

					val width = zoom * STRATEGIC_ICON_SIZE
					val height = zoom * STRATEGIC_ICON_SIZE
					x = x - width / 2
					y = y - height / 2

					if (velocityMapper.has(entity)) {

						val thrustAngle = velocityMapper.get(entity).thrustAngle

						val originX = width / 2
						val originY = height / 2
						val scale = 1f

						spriteBatch.draw(texture, x, y, originX, originY, width, height, scale, scale, thrustAngle)

					} else {

						spriteBatch.draw(texture, x, y, width, height)
					}

				}
			}
		}

		spriteBatch.end()
	}

	fun drawSelections(entities: ImmutableArray<Entity>, viewport: Viewport, cameraOffset: Vector2L) {

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
		shapeRenderer.color = Color.RED

		val selectedEntities = groupSystem.get(GroupSystem.SELECTED)
		val zoom = (viewport.camera as OrthographicCamera).zoom

		for (entity in entities) {
			if (selectedEntities.contains(entity)) {
				val position = positionMapper.get(entity)
				val x = (position.getXinKM() - cameraOffset.x).toFloat()
				val y = (position.getYinKM() - cameraOffset.y).toFloat()

				if (strategicIconMapper.has(entity) && inStrategicView(entity, zoom)) {
					val radius = zoom * STRATEGIC_ICON_SIZE / 2 + 3 * zoom
					val segments = getCircleSegments(radius, zoom)
					shapeRenderer.circle(x, y, radius, segments)

				} else if (circleMapper.has(entity)) {
					val circle = circleMapper.get(entity)
					val radius = circle.radius + 3 * zoom
					val segments = getCircleSegments(radius, zoom)
					shapeRenderer.circle(x, y, radius, segments)
				}
			}
		}

		shapeRenderer.end()
	}

	fun drawSelectionMoveTargets(entities: ImmutableArray<Entity>, cameraOffset: Vector2L) {

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
		shapeRenderer.color = Color(0.8f, 0.8f, 0.8f, 0.5f)

		val selectedEntities = groupSystem.get(GroupSystem.SELECTED)

		for (entity in entities) {
			if (selectedEntities.contains(entity)) {
				val position = positionMapper.get(entity)
				val x = (position.getXinKM() - cameraOffset.x).toFloat()
				val y = (position.getYinKM() - cameraOffset.y).toFloat()

				if (moveToEntityMapper.has(entity)) {
					val targetEntity = moveToEntityMapper.get(entity).target
					val targetPosition = positionMapper.get(targetEntity)
					val x2 = (targetPosition.getXinKM() - cameraOffset.x).toFloat()
					val y2 = (targetPosition.getYinKM() - cameraOffset.y).toFloat()
					shapeRenderer.line(x, y, x2, y2)

				} else if (moveToPositionMapper.has(entity)) {
					val targetPosition = moveToPositionMapper.get(entity).target
					val x2 = (getXinKM(targetPosition) - cameraOffset.x).toFloat()
					val y2 = (getYinKM(targetPosition) - cameraOffset.y).toFloat()
					shapeRenderer.line(x, y, x2, y2)
				}
			}
		}

		shapeRenderer.end()
	}

	fun render(viewport: Viewport, cameraOffset: Vector2L) {

		val sortedEntities = getEntities()

		viewport.apply()
		shapeRenderer.projectionMatrix = viewport.camera.combined

		drawEntities(sortedEntities, viewport, cameraOffset)
		drawEntityCenters(sortedEntities, viewport, cameraOffset)
		drawSelections(sortedEntities, viewport, cameraOffset)
		drawSelectionMoveTargets(sortedEntities, cameraOffset)

		spriteBatch.projectionMatrix = viewport.camera.combined

		drawStrategicEntities(sortedEntities, viewport, cameraOffset)

		spriteBatch.projectionMatrix = uiCamera.combined
		spriteBatch.begin()

		val font = Assets.fontMap
		font.color = Color.GREEN
		val zoom = (viewport.camera as OrthographicCamera).zoom
		val screenPosition = Vector3()

		entities.filter { nameMapper.has(it) }.forEach {
			val position = positionMapper.get(it)
			val name = nameMapper.get(it).name!!

			var radius = 0f
			
			if (inStrategicView(it, zoom)) {
				
				radius = zoom * STRATEGIC_ICON_SIZE / 2
				
			} else if (circleMapper.has(it)) {

				val circleComponent = circleMapper.get(it)
				radius = circleComponent.radius

			} else if (lineMapper.has(it)) {

				val lineComponent = lineMapper.get(it)
				radius = lineComponent.y
			}

			val x = (position.getXinKM() - cameraOffset.x).toFloat()
			val y = (position.getYinKM() - cameraOffset.y).toFloat()

			screenPosition.set(x, y - radius * 1.1f, 0f)
			viewport.camera.project(screenPosition)

			font.draw(spriteBatch, name, screenPosition.x - name.length * font.spaceWidth * .5f, screenPosition.y - font.lineHeight / zoom)
		}

		spriteBatch.end()
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
}

fun getCircleSegments(radius: Float, zoom: Float): Int {
	return Math.max(3, (10 * Math.cbrt((radius / zoom).toDouble())).toInt())
}

fun getXinKM(position: Vector2L): Long {
	return (500 + position.x) / 1000L
}

fun getYinKM(position: Vector2L): Long {
	return (500 + position.y) / 1000L
}

class ZOrderComparator : Comparator<Entity> {

	private val renderMapper = ComponentMapper.getFor(RenderComponent::class.java)

	override fun compare(o1: Entity, o2: Entity): Int {
		val r1 = renderMapper.get(o1)
		val r2 = renderMapper.get(o2)

		return r1.zOrder.compareTo(r2.zOrder)
	}
}
