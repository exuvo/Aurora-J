package se.exuvo.aurora.planetarysystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.Entity
import com.artemis.systems.IteratingSystem
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.Viewport
import se.exuvo.aurora.Assets
import se.exuvo.aurora.empires.components.WeaponsComponent
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.planetarysystems.components.CircleComponent
import se.exuvo.aurora.planetarysystems.components.DetectionComponent
import se.exuvo.aurora.planetarysystems.components.MoveToEntityComponent
import se.exuvo.aurora.planetarysystems.components.MoveToPositionComponent
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.planetarysystems.components.PassiveSensorsComponent
import se.exuvo.aurora.planetarysystems.components.RenderComponent
import se.exuvo.aurora.planetarysystems.components.ShipComponent
import se.exuvo.aurora.planetarysystems.components.Spectrum
import se.exuvo.aurora.planetarysystems.components.StrategicIconComponent
import se.exuvo.aurora.planetarysystems.components.TargetingComputerState
import se.exuvo.aurora.planetarysystems.components.TextComponent
import se.exuvo.aurora.planetarysystems.components.ThrustComponent
import se.exuvo.aurora.planetarysystems.components.TimedMovementComponent
import se.exuvo.aurora.planetarysystems.components.TintComponent
import se.exuvo.aurora.screens.GameScreenService
import se.exuvo.aurora.screens.PlanetarySystemScreen
import se.exuvo.aurora.utils.*
import se.exuvo.settings.Settings
import com.artemis.utils.IntBag

class RenderSystem : IteratingSystem(FAMILY) {
	companion object {
		val FAMILY = Aspect.all(TimedMovementComponent::class.java, RenderComponent::class.java, CircleComponent::class.java)
		val STRATEGIC_ICON_SIZE = 24f

		var debugPassiveSensors = Settings.getBol("System/Render/debugPassiveSensors", false)
		var debugDisableStrategicView = Settings.getBol("System/Render/debugDisableStrategicView", false)
		var debugDrawPassiveSensors = Settings.getBol("System/Render/debugDrawPassiveSensors", true)
	}

	lateinit private var circleMapper: ComponentMapper<CircleComponent>
	lateinit private var tintMapper: ComponentMapper<TintComponent>
	lateinit private var textMapper: ComponentMapper<TextComponent>
	lateinit private var nameMapper: ComponentMapper<NameComponent>
	lateinit private var moveToEntityMapper: ComponentMapper<MoveToEntityComponent>
	lateinit private var moveToPositionMapper: ComponentMapper<MoveToPositionComponent>
	lateinit private var strategicIconMapper: ComponentMapper<StrategicIconComponent>
	lateinit private var movementMapper: ComponentMapper<TimedMovementComponent>
	lateinit private var thrustMapper: ComponentMapper<ThrustComponent>
	lateinit private var detectionMapper: ComponentMapper<DetectionComponent>
	lateinit private var sensorsMapper: ComponentMapper<PassiveSensorsComponent>
	lateinit private var shipMapper: ComponentMapper<ShipComponent>
	lateinit private var weaponsComponentMapper: ComponentMapper<WeaponsComponent>

	private val shapeRenderer = GameServices[ShapeRenderer::class]
	private val spriteBatch = GameServices[SpriteBatch::class]
	private val uiCamera = GameServices[GameScreenService::class].uiCamera
	private val galaxyGroupSystem by lazy { GameServices[GroupSystem::class] }
	private val galaxy = GameServices[Galaxy::class]
	lateinit private var groupSystem: GroupSystem
	lateinit private var orbitSystem: OrbitSystem
	lateinit private var familyAspect: Aspect

	override fun initialize() {
		super.initialize()

		familyAspect = FAMILY.build(world)
	}

	override fun checkProcessing() = false

	override fun process(entityID: Int) {}

	fun drawEntities(entityIDs: IntBag, viewport: Viewport, cameraOffset: Vector2L) {

		val zoom = (viewport.camera as OrthographicCamera).zoom

		shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

		entityIDs.forEach { entityID ->

			if (!strategicIconMapper.has(entityID) || !inStrategicView(entityID, zoom)) {

				val movement = movementMapper.get(entityID).get(galaxy.time).value
				val tintComponent = if (tintMapper.has(entityID)) tintMapper.get(entityID) else null
				val x = (movement.getXinKM() - cameraOffset.x).toFloat()
				val y = (movement.getYinKM() - cameraOffset.y).toFloat()

				val color = Color(tintComponent?.color ?: Color.WHITE)
				shapeRenderer.color = color

				val circle = circleMapper.get(entityID)
				shapeRenderer.circle(x, y, circle.radius, getCircleSegments(circle.radius, zoom))
			}
		}

		shapeRenderer.end()
	}

	fun drawEntityCenters(entityIDs: IntBag, viewport: Viewport, cameraOffset: Vector2L) {

		val zoom = (viewport.camera as OrthographicCamera).zoom

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
		shapeRenderer.color = Color.PINK

		entityIDs.forEach { entityID ->

			if (!strategicIconMapper.has(entityID) || !inStrategicView(entityID, zoom)) {

				val movement = movementMapper.get(entityID).get(galaxy.time).value
				val x = (movement.getXinKM() - cameraOffset.x).toFloat()
				val y = (movement.getYinKM() - cameraOffset.y).toFloat()

				val circle = circleMapper.get(entityID)
				shapeRenderer.circle(x, y, circle.radius * 0.01f, getCircleSegments(circle.radius * 0.01f, zoom))
			}
		}

		shapeRenderer.end()
	}

	fun inStrategicView(entityID: Int, zoom: Float): Boolean {

		if (debugDisableStrategicView) {
			return false
		}

		val radius = circleMapper.get(entityID).radius
		return radius / zoom < 5f
	}

	fun drawStrategicEntities(entityIDs: IntBag, viewport: Viewport, cameraOffset: Vector2L) {

		val zoom = (viewport.camera as OrthographicCamera).zoom

		spriteBatch.begin()

		entityIDs.forEach { entityID ->

			if (strategicIconMapper.has(entityID) && inStrategicView(entityID, zoom)) {

				val movement = movementMapper.get(entityID).get(galaxy.time).value
				val tintComponent = if (tintMapper.has(entityID)) tintMapper.get(entityID) else null
				var x = (movement.getXinKM() - cameraOffset.x).toFloat()
				var y = (movement.getYinKM() - cameraOffset.y).toFloat()
				val texture = strategicIconMapper.get(entityID).texture

				val color = Color(tintComponent?.color ?: Color.WHITE)

				// https://github.com/libgdx/libgdx/wiki/Spritebatch%2C-Textureregions%2C-and-Sprites
				spriteBatch.setColor(color.r, color.g, color.b, color.a);

				val width = zoom * STRATEGIC_ICON_SIZE
				val height = zoom * STRATEGIC_ICON_SIZE
				x = x - width / 2
				y = y - height / 2

				if (thrustMapper.has(entityID)) {

					val thrustAngle = thrustMapper.get(entityID).thrustAngle

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

	fun drawSelections(selectedEntityIDs: List<Int>, viewport: Viewport, cameraOffset: Vector2L) {

		val zoom = (viewport.camera as OrthographicCamera).zoom

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
		shapeRenderer.color = Color.RED

		for (entityID in selectedEntityIDs) {

			val movement = movementMapper.get(entityID).get(galaxy.time).value
			val x = (movement.getXinKM() - cameraOffset.x).toFloat()
			val y = (movement.getYinKM() - cameraOffset.y).toFloat()

			if (strategicIconMapper.has(entityID) && inStrategicView(entityID, zoom)) {

				val radius = zoom * STRATEGIC_ICON_SIZE / 2 + 3 * zoom
				val segments = getCircleSegments(radius, zoom)
				shapeRenderer.circle(x, y, radius, segments)

			} else {

				val circle = circleMapper.get(entityID)
				val radius = circle.radius + 3 * zoom
				val segments = getCircleSegments(radius, zoom)
				shapeRenderer.circle(x, y, radius, segments)
			}
		}

		shapeRenderer.end()
	}

	fun drawTimedMovement(entityIDs: IntBag, selectedEntityIDs: List<Int>, viewport: Viewport, cameraOffset: Vector2L) {

		val zoom = (viewport.camera as OrthographicCamera).zoom

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)

		entityIDs.forEach { entityID ->

			if (movementMapper.has(entityID)) {

				val strategic = strategicIconMapper.has(entityID) && inStrategicView(entityID, zoom)
				val movement = movementMapper.get(entityID)

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

						if (selectedEntityIDs.contains(entityID)) {
							shapeRenderer.color = Color.GREEN
							shapeRenderer.circle(x, y, radius, segments)
						}

						shapeRenderer.color = Color.PINK
						shapeRenderer.circle(x2, y2, radius, segments)

					} else {

						val circle = circleMapper.get(entityID)
						val radius = circle.radius + 3 * zoom
						val segments = getCircleSegments(radius, zoom)

						if (selectedEntityIDs.contains(entityID)) {
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

	fun drawSelectionMoveTargets(selectedEntityIDs: List<Int>, cameraOffset: Vector2L) {

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
		shapeRenderer.color = Color(0.8f, 0.8f, 0.8f, 0.5f)

		for (entityID in selectedEntityIDs) {
			val movement = movementMapper.get(entityID).get(galaxy.time).value
			val x = (movement.getXinKM() - cameraOffset.x).toFloat()
			val y = (movement.getYinKM() - cameraOffset.y).toFloat()

			if (moveToEntityMapper.has(entityID)) {

				val targetEntity = moveToEntityMapper.get(entityID).targetID
				val targetMovement = movementMapper.get(targetEntity).get(galaxy.time).value
				val x2 = (targetMovement.getXinKM() - cameraOffset.x).toFloat()
				val y2 = (targetMovement.getYinKM() - cameraOffset.y).toFloat()
				shapeRenderer.line(x, y, x2, y2)

			} else if (moveToPositionMapper.has(entityID)) {

				val targetPosition = moveToPositionMapper.get(entityID).target
				val x2 = (getXinKM(targetPosition) - cameraOffset.x).toFloat()
				val y2 = (getYinKM(targetPosition) - cameraOffset.y).toFloat()
				shapeRenderer.line(x, y, x2, y2)
			}
		}

		shapeRenderer.end()
	}

	fun drawAttackTargets(selectedEntityIDs: List<Int>, cameraOffset: Vector2L) {

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
		shapeRenderer.color = Color(0.8f, 0f, 0f, 0.5f)

		val usedTargets = HashSet<Entity>()

		for (entityID in selectedEntityIDs) {
			if (weaponsComponentMapper.has(entityID)) {

				val movement = movementMapper.get(entityID).get(galaxy.time).value
				val x = (movement.getXinKM() - cameraOffset.x).toFloat()
				val y = (movement.getYinKM() - cameraOffset.y).toFloat()

				val ship = shipMapper.get(entityID)
				val tcs = ship.shipClass[TargetingComputer::class]

				usedTargets.clear()

				for (tc in tcs) {
					val tcState = ship.getPartState(tc)[TargetingComputerState::class]
					val target = tcState.target

					if (target != null && usedTargets.add(target)) {

						val targetMovement = movementMapper.get(target).get(galaxy.time).value
						val x2 = (targetMovement.getXinKM() - cameraOffset.x).toFloat()
						val y2 = (targetMovement.getYinKM() - cameraOffset.y).toFloat()
						shapeRenderer.line(x, y, x2, y2)
					}
				}
			}
		}

		shapeRenderer.end()
	}

	fun drawDetections(entityIDs: IntBag, viewport: Viewport, cameraOffset: Vector2L) {

		val zoom = (viewport.camera as OrthographicCamera).zoom

		//TODO if multiple detections with the same strength overlap (ie they see the same things), only draw overlap

		fun drawDetectionsInner() {
			entityIDs.forEach { entityID ->
				if (detectionMapper.has(entityID)) {
					val movementValues = movementMapper.get(entityID).get(galaxy.time).value
					val x = (movementValues.getXinKM() - cameraOffset.x).toDouble()
					val y = (movementValues.getYinKM() - cameraOffset.y).toDouble()

					val detection = detectionMapper.get(entityID)

					for (sensorEntry in detection.detections.entries) {

						val sensor = sensorEntry.key
						val arcWidth = 360.0 / sensor.part.arcSegments

						if (shapeRenderer.getCurrentType() == ShapeRenderer.ShapeType.Line) {

							when (sensor.part.spectrum) {

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

							when (sensor.part.spectrum) {

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

						val angleSteps = sensorEntry.value

						for (angleEntry in angleSteps.entries) {

							val angleStep = angleEntry.key
							val arcAngle = sensor.part.angleOffset + angleStep * arcWidth

							for (distanceEntry in angleEntry.value) {

								val distanceStep = distanceEntry.key

								val minRadius = distanceStep * sensor.part.distanceResolution
								val maxRadius = minRadius + sensor.part.distanceResolution
								val segments = Math.min(100, Math.max(3, getCircleSegments(maxRadius.toFloat(), zoom) / 4))

								shapeRenderer.scanCircleSector(x, y, maxRadius, minRadius, arcAngle, arcWidth, segments)
							}
						}
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

		if (debugPassiveSensors) {
			shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
			shapeRenderer.color = Color.PINK

			entityIDs.forEach { entityID ->
				if (detectionMapper.has(entityID)) {
					val detection = detectionMapper.get(entityID)

					for (sensorEntry in detection.detections.entries) {
						for (angleEntry in sensorEntry.value.entries) {
							for (distanceEntry in angleEntry.value) {
								for (hitPosition in distanceEntry.value.hitPositions) {

									val x = ((500 + hitPosition.x) / 1000L - cameraOffset.x).toFloat()
									val y = ((500 + hitPosition.y) / 1000L - cameraOffset.y).toFloat()

									val radius = 10 + 3 * zoom
									val segments = getCircleSegments(radius, zoom)
									shapeRenderer.circle(x, y, radius, segments)
								}
							}
						}
					}
				}
			}

			shapeRenderer.end()
		}
	}

	fun drawSelectionDetectionZones(selectedEntityIDs: List<Int>, viewport: Viewport, cameraOffset: Vector2L) {

		val zoom = (viewport.camera as OrthographicCamera).zoom
		val sensorEntitites = selectedEntityIDs.filter { sensorsMapper.has(it) }

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)

		sensorEntitites.forEach {

			val entityID = it

			val movement = movementMapper.get(entityID).get(galaxy.time).value
			val x = (movement.getXinKM() - cameraOffset.x).toDouble()
			val y = (movement.getYinKM() - cameraOffset.y).toDouble()

			val sensors = sensorsMapper.get(it)

			for (sensor in sensors.sensors) {

				when (sensor.part.spectrum) {

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

				val arcWidth = 360.0 / sensor.part.arcSegments
				val minRadius = sensor.part.distanceResolution
				val maxRadius = minRadius + sensor.part.distanceResolution
				val segments = Math.min(100, Math.max(3, getCircleSegments(maxRadius.toFloat(), zoom) / 4))

				var i = 0;
				while (i < sensor.part.arcSegments) {

					val arcAngle = i * arcWidth + sensor.part.angleOffset

					shapeRenderer.scanCircleSector(x, y, maxRadius, minRadius, arcAngle, arcWidth, segments)
					i++
				}
			}
		}

		shapeRenderer.end()
	}

	fun drawSelectionDetectionStrength(selectedEntityIDs: List<Int>, viewport: Viewport, cameraOffset: Vector2L) {

		val screenPosition = Vector3()

		val font = Assets.fontMap
		font.color = Color.WHITE

		selectedEntityIDs.filter { detectionMapper.has(it) }.forEach {

			var textRow = 0

			val movementValues = movementMapper.get(it).get(galaxy.time).value
			val sensorX = (movementValues.getXinKM() - cameraOffset.x).toDouble()
			val sensorY = (movementValues.getYinKM() - cameraOffset.y).toDouble()

			val detection = detectionMapper.get(it)

			for (sensorEntry in detection.detections.entries) {

				val sensor = sensorEntry.key
				val arcWidth = 360.0 / sensor.part.arcSegments

				val angleSteps = sensorEntry.value

				for (angleEntry in angleSteps.entries) {

					val angleStep = angleEntry.key
					val angle = sensor.part.angleOffset + angleStep * arcWidth + 0.5 * arcWidth

					for (distanceEntry in angleEntry.value) {

						val distanceStep = distanceEntry.key
						val hit = distanceEntry.value

						val minRadius = distanceStep * sensor.part.distanceResolution
						val maxRadius = minRadius + sensor.part.distanceResolution
						val radius = (minRadius + maxRadius) / 2

						val text = "${sensor.part.spectrum} ${String.format("%.2e", hit.signalStrength)} - ${sensor.part.name}"

						val angleRad = Math.toRadians(angle)
						val x = (sensorX + radius * Math.cos(angleRad)).toFloat()
						val y = (sensorY + radius * Math.sin(angleRad)).toFloat()

						screenPosition.set(x, y, 0f)
						viewport.camera.project(screenPosition)

						font.color = Color.GREEN
						font.draw(spriteBatch, text, screenPosition.x - text.length * font.spaceWidth * .5f, screenPosition.y - textRow * font.lineHeight)
					}
				}

				textRow++
			}
		}
	}

	fun drawNames(entityIDs: IntBag, viewport: Viewport, cameraOffset: Vector2L) {

		val zoom = (viewport.camera as OrthographicCamera).zoom
		val screenPosition = Vector3()

		val font = Assets.fontMap
		font.color = Color.WHITE

		entityIDs.forEach { entityID ->
			if (nameMapper.has(entityID)) {
				val movement = movementMapper.get(entityID).get(galaxy.time).value
				val name = nameMapper.get(entityID).name

				var radius = 0f

				if (inStrategicView(entityID, zoom)) {

					radius = zoom * STRATEGIC_ICON_SIZE / 2

				} else if (circleMapper.has(entityID)) {

					val circleComponent = circleMapper.get(entityID)
					radius = circleComponent.radius
				}

				val x = (movement.getXinKM() - cameraOffset.x).toFloat()
				val y = (movement.getYinKM() - cameraOffset.y).toFloat()

				screenPosition.set(x, y - radius * 1.2f, 0f)
				viewport.camera.project(screenPosition)

				font.draw(spriteBatch, name, screenPosition.x - name.length * font.spaceWidth * .5f, screenPosition.y - 0.5f * font.lineHeight)
			}
		}
	}

	fun drawMovementTimes(entityIDs: IntBag, selectedEntityIDs: List<Int>, viewport: Viewport, cameraOffset: Vector2L) {

		val zoom = (viewport.camera as OrthographicCamera).zoom
		val screenPosition = Vector3()

		val font = Assets.fontMap
		font.color = Color.WHITE

		entityIDs.forEach { entityID ->
			if (movementMapper.has(entityID)) {

				val movement = movementMapper.get(entityID)

				var radius = 0f

				if (inStrategicView(entityID, zoom)) {

					radius = zoom * STRATEGIC_ICON_SIZE / 2

				} else if (circleMapper.has(entityID)) {

					val circleComponent = circleMapper.get(entityID)
					radius = circleComponent.radius
				}

				if (movement.next != null && movement.previous.time != galaxy.time) {

					if (selectedEntityIDs.contains(entityID)) {
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
		}
	}

	fun render(viewport: Viewport, cameraOffset: Vector2L) {

		val entityIDs = subscription.getEntities()
		val selectedEntityIDs = galaxyGroupSystem.get(GroupSystem.SELECTED).filter { familyAspect.isInterested(it) }.map { it.id }

		viewport.apply()
		shapeRenderer.projectionMatrix = viewport.camera.combined

		drawDetections(entityIDs, viewport, cameraOffset)

		if (Gdx.input.isKeyPressed(Input.Keys.C)) {
			drawSelectionDetectionZones(selectedEntityIDs, viewport, cameraOffset)
		}

		orbitSystem.render(viewport, cameraOffset)

		drawEntities(entityIDs, viewport, cameraOffset)
		drawEntityCenters(entityIDs, viewport, cameraOffset)
		drawTimedMovement(entityIDs, selectedEntityIDs, viewport, cameraOffset)

		drawSelections(selectedEntityIDs, viewport, cameraOffset)
		drawSelectionMoveTargets(selectedEntityIDs, cameraOffset)
		drawAttackTargets(selectedEntityIDs, cameraOffset)

		spriteBatch.projectionMatrix = viewport.camera.combined

		drawStrategicEntities(entityIDs, viewport, cameraOffset)

		spriteBatch.projectionMatrix = uiCamera.combined
		spriteBatch.begin()

		drawSelectionDetectionStrength(selectedEntityIDs, viewport, cameraOffset)
		drawNames(entityIDs, viewport, cameraOffset)
		drawMovementTimes(entityIDs, selectedEntityIDs, viewport, cameraOffset)

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
