package se.exuvo.aurora.planetarysystems.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.SortedIteratingSystem
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.Viewport
import se.exuvo.aurora.Assets
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.planetarysystems.components.CircleComponent
import se.exuvo.aurora.planetarysystems.components.DetectionComponent
import se.exuvo.aurora.planetarysystems.components.DetectionHit
import se.exuvo.aurora.planetarysystems.components.MoveToEntityComponent
import se.exuvo.aurora.planetarysystems.components.MoveToPositionComponent
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.planetarysystems.components.RenderComponent
import se.exuvo.aurora.planetarysystems.components.SensorsComponent
import se.exuvo.aurora.planetarysystems.components.Spectrum
import se.exuvo.aurora.planetarysystems.components.StrategicIconComponent
import se.exuvo.aurora.planetarysystems.components.TagComponent
import se.exuvo.aurora.planetarysystems.components.TextComponent
import se.exuvo.aurora.planetarysystems.components.ThrustComponent
import se.exuvo.aurora.planetarysystems.components.TimedMovementComponent
import se.exuvo.aurora.planetarysystems.components.TintComponent
import se.exuvo.aurora.screens.GameScreenService
import se.exuvo.aurora.screens.PlanetarySystemScreen
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.utils.scanArc
import java.util.Comparator

class RenderSystem : SortedIteratingSystem(FAMILY, ZOrderComparator()) {
	companion object {
		val FAMILY = Family.all(TimedMovementComponent::class.java, RenderComponent::class.java, CircleComponent::class.java).get()
		val STRATEGIC_ICON_SIZE = 24f
	}

	private val circleMapper = ComponentMapper.getFor(CircleComponent::class.java)
	private val tintMapper = ComponentMapper.getFor(TintComponent::class.java)
	private val tagMapper = ComponentMapper.getFor(TagComponent::class.java)
	private val textMapper = ComponentMapper.getFor(TextComponent::class.java)
	private val nameMapper = ComponentMapper.getFor(NameComponent::class.java)
	private val moveToEntityMapper = ComponentMapper.getFor(MoveToEntityComponent::class.java)
	private val moveToPositionMapper = ComponentMapper.getFor(MoveToPositionComponent::class.java)
	private val strategicIconMapper = ComponentMapper.getFor(StrategicIconComponent::class.java)
	private val movementMapper = ComponentMapper.getFor(TimedMovementComponent::class.java)
	private val thrustMapper = ComponentMapper.getFor(ThrustComponent::class.java)
	private val detectionMapper = ComponentMapper.getFor(DetectionComponent::class.java)
	private val sensorsMapper = ComponentMapper.getFor(SensorsComponent::class.java)

	private val shapeRenderer = GameServices[ShapeRenderer::class.java]
	private val spriteBatch = GameServices[SpriteBatch::class.java]
	private val uiCamera = GameServices[GameScreenService::class.java].uiCamera
	private val groupSystem by lazy { engine.getSystem(GroupSystem::class.java) }
	private val galaxyGroupSystem by lazy { GameServices[GroupSystem::class.java] }
	private val galaxy = GameServices[Galaxy::class.java]
	private val orbitSystem by lazy { engine.getSystem(OrbitSystem::class.java) }

	override fun checkProcessing() = false

	override fun processEntity(entity: Entity, renderDelta: Float) {}

	fun drawEntities(entities: Iterable<Entity>, viewport: Viewport, cameraOffset: Vector2L) {

		val zoom = (viewport.camera as OrthographicCamera).zoom

		shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

		for (entity in entities) {

			if (!strategicIconMapper.has(entity) || !inStrategicView(entity, zoom)) {

				val movement = movementMapper.get(entity).get(galaxy.time).value
				val tintComponent = if (tintMapper.has(entity)) tintMapper.get(entity) else null
				val x = (movement.getXinKM() - cameraOffset.x).toFloat()
				val y = (movement.getYinKM() - cameraOffset.y).toFloat()

				val color = Color(tintComponent?.color ?: Color.WHITE)
				shapeRenderer.color = color

				val circle = circleMapper.get(entity)
				shapeRenderer.circle(x, y, circle.radius, getCircleSegments(circle.radius, zoom))
			}
		}

		shapeRenderer.end()
	}

	fun drawEntityCenters(entities: Iterable<Entity>, viewport: Viewport, cameraOffset: Vector2L) {

		val zoom = (viewport.camera as OrthographicCamera).zoom

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
		shapeRenderer.color = Color.PINK

		for (entity in entities) {

			if (!strategicIconMapper.has(entity) || !inStrategicView(entity, zoom)) {

				val movement = movementMapper.get(entity).get(galaxy.time).value
				val x = (movement.getXinKM() - cameraOffset.x).toFloat()
				val y = (movement.getYinKM() - cameraOffset.y).toFloat()

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

	fun drawStrategicEntities(entities: Iterable<Entity>, viewport: Viewport, cameraOffset: Vector2L) {

		val zoom = (viewport.camera as OrthographicCamera).zoom

		spriteBatch.begin()

		for (entity in entities) {

			if (strategicIconMapper.has(entity) && inStrategicView(entity, zoom)) {

				val movement = movementMapper.get(entity).get(galaxy.time).value
				val tintComponent = if (tintMapper.has(entity)) tintMapper.get(entity) else null
				var x = (movement.getXinKM() - cameraOffset.x).toFloat()
				var y = (movement.getYinKM() - cameraOffset.y).toFloat()
				val texture = strategicIconMapper.get(entity).texture

				val color = Color(tintComponent?.color ?: Color.WHITE)

				// https://github.com/libgdx/libgdx/wiki/Spritebatch%2C-Textureregions%2C-and-Sprites
				spriteBatch.setColor(color.r, color.g, color.b, color.a);

				val width = zoom * STRATEGIC_ICON_SIZE
				val height = zoom * STRATEGIC_ICON_SIZE
				x = x - width / 2
				y = y - height / 2

				if (thrustMapper.has(entity)) {

					val thrustAngle = thrustMapper.get(entity).thrustAngle

					val originX = width / 2
					val originY = height / 2
					val scale = 1f

					spriteBatch.draw(texture, x, y, originX, originY, width, height, scale, scale, thrustAngle)

				} else {

					spriteBatch.draw(texture, x, y, width, height)
				}
			}
		}

		spriteBatch.end()
	}

	fun drawSelections(entities: Iterable<Entity>, viewport: Viewport, cameraOffset: Vector2L) {

		val zoom = (viewport.camera as OrthographicCamera).zoom

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
		shapeRenderer.color = Color.RED

		for (entity in entities) {

			val movement = movementMapper.get(entity).get(galaxy.time).value
			val x = (movement.getXinKM() - cameraOffset.x).toFloat()
			val y = (movement.getYinKM() - cameraOffset.y).toFloat()

			if (strategicIconMapper.has(entity) && inStrategicView(entity, zoom)) {

				val radius = zoom * STRATEGIC_ICON_SIZE / 2 + 3 * zoom
				val segments = getCircleSegments(radius, zoom)
				shapeRenderer.circle(x, y, radius, segments)

			} else {

				val circle = circleMapper.get(entity)
				val radius = circle.radius + 3 * zoom
				val segments = getCircleSegments(radius, zoom)
				shapeRenderer.circle(x, y, radius, segments)
			}
		}

		shapeRenderer.end()
	}

	fun drawTimedMovement(entities: Iterable<Entity>, viewport: Viewport, cameraOffset: Vector2L) {

		val zoom = (viewport.camera as OrthographicCamera).zoom

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)

		val selectedEntities = galaxyGroupSystem.get(GroupSystem.SELECTED)

		for (entity in entities) {

			if (movementMapper.has(entity)) {

				val strategic = strategicIconMapper.has(entity) && inStrategicView(entity, zoom)
				val movement = movementMapper.get(entity)

				if (movement.previous.time != galaxy.time && movement.next != null) {

					val movementValues = movement.previous.value
					val x = (movementValues.getXinKM() - cameraOffset.x).toFloat()
					val y = (movementValues.getYinKM() - cameraOffset.y).toFloat()

					val nextMovementValues = movement.next!!.value
					val x2 = (nextMovementValues.getXinKM() - cameraOffset.x).toFloat()
					val y2 = (nextMovementValues.getYinKM() - cameraOffset.y).toFloat()

					if (strategic) {

						val radius = zoom * STRATEGIC_ICON_SIZE / 2 + 4 * zoom
						val segments = getCircleSegments(radius, zoom)

						if (selectedEntities.contains(entity)) {
							shapeRenderer.color = Color.GREEN
							shapeRenderer.circle(x, y, radius, segments)
						}

						shapeRenderer.color = Color.PINK
						shapeRenderer.circle(x2, y2, radius, segments)

					} else {

						val circle = circleMapper.get(entity)
						val radius = circle.radius + 3 * zoom
						val segments = getCircleSegments(radius, zoom)

						if (selectedEntities.contains(entity)) {
							shapeRenderer.color = Color.GREEN
							shapeRenderer.circle(x, y, radius, segments)
						}

						shapeRenderer.color = Color.PINK
						shapeRenderer.circle(x2, y2, radius, segments)
					}
				}
			}
		}

		shapeRenderer.end()
	}

	fun drawSelectionMoveTargets(entities: Iterable<Entity>, cameraOffset: Vector2L) {

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
		shapeRenderer.color = Color(0.8f, 0.8f, 0.8f, 0.5f)

		for (entity in entities) {
			val movement = movementMapper.get(entity).get(galaxy.time).value
			val x = (movement.getXinKM() - cameraOffset.x).toFloat()
			val y = (movement.getYinKM() - cameraOffset.y).toFloat()

			if (moveToEntityMapper.has(entity)) {

				val targetEntity = moveToEntityMapper.get(entity).target
				val targetMovement = movementMapper.get(targetEntity).get(galaxy.time).value
				val x2 = (targetMovement.getXinKM() - cameraOffset.x).toFloat()
				val y2 = (targetMovement.getYinKM() - cameraOffset.y).toFloat()
				shapeRenderer.line(x, y, x2, y2)

			} else if (moveToPositionMapper.has(entity)) {

				val targetPosition = moveToPositionMapper.get(entity).target
				val x2 = (getXinKM(targetPosition) - cameraOffset.x).toFloat()
				val y2 = (getYinKM(targetPosition) - cameraOffset.y).toFloat()
				shapeRenderer.line(x, y, x2, y2)
			}
		}

		shapeRenderer.end()
	}

	fun drawDetections(entities: Iterable<Entity>, viewport: Viewport, cameraOffset: Vector2L) {

		val zoom = (viewport.camera as OrthographicCamera).zoom
		val tempPosition = Vector2L()
		val detectionEntitites = entities.filter { detectionMapper.has(it) }

		fun drawDetectionsInner() {
			detectionEntitites.forEach {

				val movementValues = movementMapper.get(it).get(galaxy.time).value
				val position = movementValues.position
				val detection = detectionMapper.get(it)

				val x = (movementValues.getXinKM() - cameraOffset.x).toDouble()
				val y = (movementValues.getYinKM() - cameraOffset.y).toDouble()

				for (entry in detection.detections.entries) {

					val entity = entry.key
					val targetPosition = movementMapper.get(entity).get(galaxy.time).value.position
					val targetAngle = position.angleTo(targetPosition)

					tempPosition.set(targetPosition).sub(position)

					// In km
					val distance = tempPosition.len().div(1000)

					for (hit: DetectionHit in entry.value) {

						val sensor = hit.sensor

						if (shapeRenderer.getCurrentType() == ShapeRenderer.ShapeType.Line) {
							when (sensor.spectrum) {

								Spectrum.Thermal -> {
									shapeRenderer.color = Color.CORAL
								}

								Spectrum.Electromagnetic -> {
									shapeRenderer.color = Color.VIOLET
								}

								else -> {
									shapeRenderer.color = Color.WHITE
								}
							}
						} else {
							when (sensor.spectrum) {

								Spectrum.Thermal -> {
									shapeRenderer.color = Color.CORAL.cpy()
									shapeRenderer.color.a = 0.2f
								}

								Spectrum.Electromagnetic -> {
									shapeRenderer.color = Color.VIOLET.cpy()
									shapeRenderer.color.a = 0.3f
								}

								else -> {
									shapeRenderer.color = Color.WHITE.cpy()
									shapeRenderer.color.a = 0.2f
								}
							}
						}

						val arcWidth = 360.0 / sensor.arcSegments
						val arcAngle = sensor.angleOffset + Math.floor((targetAngle - sensor.angleOffset) / arcWidth) * arcWidth

						val minRadius = Math.floor(distance / sensor.distanceResolution) * sensor.distanceResolution
						val maxRadius = minRadius + sensor.distanceResolution
						val segments = Math.min(100, Math.max(3, getCircleSegments(maxRadius.toFloat(), zoom) / 4))

						shapeRenderer.scanArc(x, y, maxRadius, minRadius, arcAngle, arcWidth, segments)
					}
				}
			}
		}

		// https://stackoverflow.com/questions/25347456/how-to-do-blending-in-libgdx
		Gdx.gl.glEnable(GL30.GL_BLEND);
		Gdx.gl.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA);

		shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
		drawDetectionsInner()
		shapeRenderer.end()

		Gdx.gl.glDisable(GL30.GL_BLEND);

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
		drawDetectionsInner()
		shapeRenderer.end()
	}

	fun drawSelectionDetectionZones(selectedEntities: Iterable<Entity>, viewport: Viewport, cameraOffset: Vector2L) {

		val zoom = (viewport.camera as OrthographicCamera).zoom
		val sensorEntitites = selectedEntities.filter { sensorsMapper.has(it) }

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)

		sensorEntitites.forEach {

			val entity = it

			val movement = movementMapper.get(entity).get(galaxy.time).value
			val x = (movement.getXinKM() - cameraOffset.x).toDouble()
			val y = (movement.getYinKM() - cameraOffset.y).toDouble()

			val sensors = sensorsMapper.get(it)

			for (sensor in sensors.sensors) {

				when (sensor.spectrum) {

					Spectrum.Thermal -> {
						shapeRenderer.color = Color.CORAL.cpy()
						shapeRenderer.color.a = 0.2f
					}

					Spectrum.Electromagnetic -> {
						shapeRenderer.color = Color.VIOLET.cpy()
						shapeRenderer.color.a = 0.3f
					}

					else -> {
						shapeRenderer.color = Color.WHITE.cpy()
						shapeRenderer.color.a = 0.2f
					}
				}

				val arcWidth = 360.0 / sensor.arcSegments
				val minRadius = sensor.distanceResolution
				val maxRadius = minRadius + sensor.distanceResolution
				val segments = Math.min(100, Math.max(3, getCircleSegments(maxRadius.toFloat(), zoom) / 4))

				var i = 0;
				while (i < sensor.arcSegments) {

					val arcAngle = i * arcWidth + sensor.angleOffset

					shapeRenderer.scanArc(x, y, maxRadius, minRadius, arcAngle, arcWidth, segments)

					i++
				}
			}
		}

		shapeRenderer.end()
	}

	fun drawSelectionDetectionStrength(selectedEntities: Iterable<Entity>, viewport: Viewport, cameraOffset: Vector2L) {

		val zoom = (viewport.camera as OrthographicCamera).zoom
		val screenPosition = Vector3()

		val font = Assets.fontMap
		font.color = Color.WHITE

		selectedEntities.filter { detectionMapper.has(it) }.forEach {

			val detection = detectionMapper.get(it)

			for (entry in detection.detections.entries) {

				val entity = entry.key

				val movement = movementMapper.get(entity)
				var radius = 0f

				if (inStrategicView(entity, zoom)) {

					radius = zoom * STRATEGIC_ICON_SIZE / 2

				} else if (circleMapper.has(entity)) {

					val circleComponent = circleMapper.get(entity)
					radius = circleComponent.radius
				}

				var offset = 1.5f;

				for (hit in entry.value) {

					val text = "${hit.sensor.spectrum} ${String.format("%.2e", hit.signalStrength)} - ${hit.sensor.name}"
					val movementValues = movement.previous.value
					val x = (movementValues.getXinKM() - cameraOffset.x).toFloat()
					val y = (movementValues.getYinKM() - cameraOffset.y).toFloat()

					screenPosition.set(x, y - radius * 1.2f, 0f)
					viewport.camera.project(screenPosition)

					font.color = Color.GREEN
					font.draw(spriteBatch, text, screenPosition.x - text.length * font.spaceWidth * .5f, screenPosition.y - offset++ * font.lineHeight)
				}
			}
		}
	}

	fun render(viewport: Viewport, cameraOffset: Vector2L) {

		val sortedEntities = getEntities()
		val selectedEntities = galaxyGroupSystem.get(GroupSystem.SELECTED)
		val sortedAndSelectedEntities = sortedEntities.filter { selectedEntities.contains(it) }

		viewport.apply()
		shapeRenderer.projectionMatrix = viewport.camera.combined

		drawDetections(sortedEntities, viewport, cameraOffset)

		if (Gdx.input.isKeyPressed(Input.Keys.C)) {
			drawSelectionDetectionZones(sortedAndSelectedEntities, viewport, cameraOffset)
		}

		orbitSystem.render(viewport, cameraOffset)

		drawEntities(sortedEntities, viewport, cameraOffset)
		drawEntityCenters(sortedEntities, viewport, cameraOffset)
		drawTimedMovement(sortedEntities, viewport, cameraOffset)

		drawSelections(sortedAndSelectedEntities, viewport, cameraOffset)
		drawSelectionMoveTargets(sortedAndSelectedEntities, cameraOffset)

		spriteBatch.projectionMatrix = viewport.camera.combined

		drawStrategicEntities(sortedEntities, viewport, cameraOffset)

		spriteBatch.projectionMatrix = uiCamera.combined
		spriteBatch.begin()

		drawSelectionDetectionStrength(sortedAndSelectedEntities, viewport, cameraOffset)

		val font = Assets.fontMap
		font.color = Color.WHITE
		val zoom = (viewport.camera as OrthographicCamera).zoom
		val screenPosition = Vector3()

		entities.filter { nameMapper.has(it) }.forEach {
			val movement = movementMapper.get(it).get(galaxy.time).value
			val name = nameMapper.get(it).name!!

			var radius = 0f

			if (inStrategicView(it, zoom)) {

				radius = zoom * STRATEGIC_ICON_SIZE / 2

			} else if (circleMapper.has(it)) {

				val circleComponent = circleMapper.get(it)
				radius = circleComponent.radius
			}

			val x = (movement.getXinKM() - cameraOffset.x).toFloat()
			val y = (movement.getYinKM() - cameraOffset.y).toFloat()

			screenPosition.set(x, y - radius * 1.2f, 0f)
			viewport.camera.project(screenPosition)

			font.draw(spriteBatch, name, screenPosition.x - name.length * font.spaceWidth * .5f, screenPosition.y - 0.5f * font.lineHeight)
		}

		entities.filter { movementMapper.has(it) }.forEach {

			val movement = movementMapper.get(it)

			var radius = 0f

			if (inStrategicView(it, zoom)) {

				radius = zoom * STRATEGIC_ICON_SIZE / 2

			} else if (circleMapper.has(it)) {

				val circleComponent = circleMapper.get(it)
				radius = circleComponent.radius
			}

			if (movement.next != null && movement.previous.time != galaxy.time) {

				if (selectedEntities.contains(it)) {
					val text = "${(movement.previous.time / (24L * 60L * 60L)).toInt()} ${PlanetarySystemScreen.secondsToString(movement.previous.time)}"
					val movementValues = movement.previous.value
					val x = (movementValues.getXinKM() - cameraOffset.x).toFloat()
					val y = (movementValues.getYinKM() - cameraOffset.y).toFloat()

					screenPosition.set(x, y - radius * 1.2f, 0f)
					viewport.camera.project(screenPosition)

					font.color = Color.GREEN
					font.draw(spriteBatch, text, screenPosition.x - text.length * font.spaceWidth * .5f, screenPosition.y - 1.5f * font.lineHeight)
				}

				run {
					val text = "${(movement.next!!.time / (24L * 60L * 60L)).toInt()} ${PlanetarySystemScreen.secondsToString(movement.next!!.time)}"
					val movementValues = movement.next!!.value
					val x = (movementValues.getXinKM() - cameraOffset.x).toFloat()
					val y = (movementValues.getYinKM() - cameraOffset.y).toFloat()

					screenPosition.set(x, y - radius * 1.2f, 0f)
					viewport.camera.project(screenPosition)

					font.color = Color.RED
					font.draw(spriteBatch, text, screenPosition.x - text.length * font.spaceWidth * .5f, screenPosition.y - 1.5f * font.lineHeight)
				}
			}
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
