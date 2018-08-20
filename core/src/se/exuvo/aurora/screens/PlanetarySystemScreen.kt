package se.exuvo.aurora.screens

import com.artemis.ComponentMapper
import com.artemis.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.utils.viewport.Viewport
import se.exuvo.aurora.Assets
import se.exuvo.aurora.empires.components.WeaponsComponent
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.planetarysystems.PlanetarySystem
import se.exuvo.aurora.planetarysystems.PlanetarySystemGeneration
import se.exuvo.aurora.planetarysystems.components.ApproachType
import se.exuvo.aurora.planetarysystems.components.CircleComponent
import se.exuvo.aurora.planetarysystems.components.MoveToEntityComponent
import se.exuvo.aurora.planetarysystems.components.MoveToPositionComponent
import se.exuvo.aurora.planetarysystems.components.PlanetarySystemComponent
import se.exuvo.aurora.planetarysystems.components.RenderComponent
import se.exuvo.aurora.planetarysystems.components.ShipComponent
import se.exuvo.aurora.planetarysystems.components.TargetingComputerState
import se.exuvo.aurora.planetarysystems.components.TimedMovementComponent
import se.exuvo.aurora.planetarysystems.systems.GroupSystem
import se.exuvo.aurora.planetarysystems.systems.MovementSystem
import se.exuvo.aurora.planetarysystems.systems.RenderSystem
import se.exuvo.aurora.planetarysystems.systems.WeaponSystem
import se.exuvo.aurora.utils.*
import se.exuvo.aurora.utils.keys.KeyActions_PlanetarySystemScreen
import se.exuvo.aurora.utils.keys.KeyMappings
import se.exuvo.settings.Settings
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.properties.Delegates
import com.artemis.Aspect

class PlanetarySystemScreen(val system: PlanetarySystem) : GameScreenImpl(), InputProcessor {
	companion object {

		fun secondsToString(time: Long): String {
			val hours = (time / 3600) % 24
			val minutes = (time / 60) % 60
			val seconds = time % 60
			return String.format("%02d:%02d:%02d", hours, minutes, seconds)
		}
	}

	private val spriteBatch = GameServices[SpriteBatch::class]
	private val shapeRenderer = GameServices[ShapeRenderer::class]
	private val galaxy by lazy { GameServices[Galaxy::class] }
	private val galaxyGroupSystem by lazy { GameServices[GroupSystem::class] }
	private val systemGroupSystem by lazy { system.world.getSystem(GroupSystem::class.java) }
	private val renderSystem by lazy { system.world.getSystem(RenderSystem::class.java) }
	private val movementSystem by lazy { system.world.getSystem(MovementSystem::class.java) }

	private val uiCamera = GameServices[GameScreenService::class].uiCamera
	private var viewport by Delegates.notNull<Viewport>()
	private var camera by Delegates.notNull<OrthographicCamera>()
	private val cameraOffset = Vector2L()

	private val circleMapper = ComponentMapper.getFor(CircleComponent::class.java, system.world)
	private val movementMapper = ComponentMapper.getFor(TimedMovementComponent::class.java, system.world)
	private val planetarySystemMapper = ComponentMapper.getFor(PlanetarySystemComponent::class.java, system.world)
	private val weaponsComponentMapper = ComponentMapper.getFor(WeaponsComponent::class.java, system.world)
	private val shipMapper = ComponentMapper.getFor(ShipComponent::class.java, system.world)

	var zoomLevel = 0
	var zoomSensitivity = Settings.getFloat("UI/zoomSensitivity", 1.25f).toDouble()
	val maxZoom = 1E8f
	var selectedAction: KeyActions_PlanetarySystemScreen? = null

	init {
		viewport = ScreenViewport()
		camera = viewport.camera as OrthographicCamera

		viewport.update(Gdx.graphics.width, Gdx.graphics.height)
		camera.zoom = 1E6f;
		zoomLevel = (Math.log(camera.zoom.toDouble()) / Math.log(zoomSensitivity)).toInt();
	}

	override fun resize(width: Int, height: Int) {
		viewport.update(width, height)
	}

	override fun update(deltaRealTime: Float) {

		if (Gdx.input.isKeyPressed(Input.Keys.ESCAPE)) {
			Gdx.app.exit()
		}

		var hDirection = 0f
		var vDirection = 0f

		if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
			hDirection--
		}

		if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
			hDirection++
		}

		if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
			vDirection--
		}

		if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
			vDirection++
		}

		if (hDirection != 0f || vDirection != 0f) {
			cameraOffset.add((camera.zoom * hDirection).toLong(), (camera.zoom * vDirection).toLong())
		}
	}

	override fun draw() {
		super.draw()

		system.lock.read {
			renderSystem.render(viewport, cameraOffset)
		}

		if (dragSelect) {
			shapeRenderer.projectionMatrix = uiCamera.combined
			shapeRenderer.color = Color.WHITE
			shapeRenderer.begin(ShapeRenderer.ShapeType.Line)

			val dragSelection = getDragSelection(false)
			shapeRenderer.rect(dragSelection.x.toFloat(), (dragSelection.y).toFloat(), dragSelection.width.toFloat(), dragSelection.height.toFloat())

			shapeRenderer.end()
		}

		drawUI()
	}

	private fun drawUI() {
		spriteBatch.projectionMatrix = uiCamera.combined
		spriteBatch.begin()
		Assets.fontUI.draw(spriteBatch, "System view, zoomLevel $zoomLevel, day ${galaxy.day}, time ${secondsToString(galaxy.time)}, speed ${Units.NANO_SECOND / galaxy.speed}", 8f, 32f)
		spriteBatch.end()
	}

	fun keyAction(action: KeyActions_PlanetarySystemScreen): Boolean {

		if (action == KeyActions_PlanetarySystemScreen.GENERATE_SYSTEM) {

			PlanetarySystemGeneration(system).generateRandomSystem()

		} else if (action == KeyActions_PlanetarySystemScreen.SPEED_UP) {
			//TODO something smarter here
			var speed = galaxy.speed / 4

			if (speed < Units.NANO_MILLI / 60) {
				speed = Units.NANO_MILLI / 60
			}

			galaxy.speed = speed

			if (galaxy.sleeping) {
				galaxy.thread!!.interrupt()
			}

			return true

		} else if (action == KeyActions_PlanetarySystemScreen.SPEED_DOWN) {

			var speed = galaxy.speed * 4

			if (speed > 1 * Units.NANO_SECOND) {
				speed = 1 * Units.NANO_SECOND
			}

			galaxy.speed = speed

			if (galaxy.sleeping) {
				galaxy.thread!!.interrupt()
			}

			return true

		} else if (action == KeyActions_PlanetarySystemScreen.PAUSE) {

			galaxy.paused = !galaxy.paused

			if (galaxy.sleeping) {
				galaxy.thread!!.interrupt()
			}

			return true

		} else if (action == KeyActions_PlanetarySystemScreen.MAP) {

			val galaxyScreen = GameServices[GalaxyScreen::class]
			galaxyScreen.centerOnPlanetarySystem(system)
			GameServices[GameScreenService::class].add(galaxyScreen)

		} else if (action == KeyActions_PlanetarySystemScreen.ATTACK) {

			system.lock.read {
				if (galaxyGroupSystem.get(GroupSystem.SELECTED).isNotEmpty()) {
					selectedAction = KeyActions_PlanetarySystemScreen.ATTACK
					println("Selected action " + action)
				} else {
					println("Unable to select action " + action + ", no selection")
				}
			}
		}

		return false
	}

	override fun keyDown(keycode: Int): Boolean {

		val action = KeyMappings.getRaw(keycode, PlanetarySystemScreen::class)

		if (action != null) {
			return keyAction(action as KeyActions_PlanetarySystemScreen)
		}

		return false;
	}

	override fun keyUp(keycode: Int): Boolean {
		return false;
	}

	override fun keyTyped(character: Char): Boolean {

		val action = KeyMappings.getTranslated(character, PlanetarySystemScreen::class)

		if (action != null) {
			return keyAction(action as KeyActions_PlanetarySystemScreen)
		}

		return false;
	}

	var moveWindow = false

	var dragSelectPotentialStart = false
	var dragSelect = false
	val selectionFamily = system.world.getAspectSubscriptionManager().get(Aspect.all(TimedMovementComponent::class.java, RenderComponent::class.java).one(CircleComponent::class.java))
	val weaponFamily = WeaponSystem.FAMILY.build(system.world)
	val movementFamily = MovementSystem.CAN_ACCELERATE_FAMILY.build(system.world)

	var dragX = 0
	var dragY = 0

	override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {

		if (!moveWindow && !dragSelect) {

			when (button) {
				Input.Buttons.LEFT -> {

					system.lock.read {

						val mouseInGameCoordinates = toWorldCordinates(getMouseInScreenCordinates(screenX, screenY))
						val entitiesUnderMouse = ArrayList<Entity>()
						val entityIDs = selectionFamily.entities
						val testCircle = CircleL()
						val zoom = camera.zoom

						// Exact check first
						entityIDs.forEach { entityID ->
							val position = movementMapper.get(entityID).get(galaxy.time).value.position
							val radius: Float

							if (renderSystem.inStrategicView(entityID, zoom)) {

								radius = zoom * RenderSystem.STRATEGIC_ICON_SIZE / 2

							} else {

								radius = circleMapper.get(entityID).radius
							}

							testCircle.set(position, radius * 1000)

							if (testCircle.contains(mouseInGameCoordinates)) {
								entitiesUnderMouse.add(system.world.getEntity(entityID))
							}
						}

						// Lenient check if empty
						if (entitiesUnderMouse.isEmpty()) {
							entityIDs.forEach { entityID ->
								val position = movementMapper.get(entityID).get(galaxy.time).value.position
								val radius: Float

								if (renderSystem.inStrategicView(entityID, zoom)) {

									radius = zoom * RenderSystem.STRATEGIC_ICON_SIZE / 2

								} else {

									radius = circleMapper.get(entityID).radius * 1.1f + 2 * camera.zoom
								}

								testCircle.set(position, radius * 1000)

								if (testCircle.contains(mouseInGameCoordinates)) {
									entitiesUnderMouse.add(system.world.getEntity(entityID))
								}
							}

							if (entitiesUnderMouse.isNotEmpty()) {
//								println("lenient selected ${entitiesUnderMouse.size} entities")
							}

						} else {
//							println("strict selected ${entitiesUnderMouse.size} entities")
						}

						if (selectedAction == null) {

							if (entitiesUnderMouse.isNotEmpty()) {

								dragSelectPotentialStart = false;

								if (galaxyGroupSystem.get(GroupSystem.SELECTED).isNotEmpty() && !Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
									galaxyGroupSystem.clear(GroupSystem.SELECTED)
//								println("cleared selection")
								}

								galaxyGroupSystem.add(entitiesUnderMouse, GroupSystem.SELECTED)

							} else {

								dragSelectPotentialStart = true;
								dragX = screenX;
								dragY = screenY;

//						println("drag select potential dragX $dragX, dragY $dragY")
							}

						} else if (selectedAction == KeyActions_PlanetarySystemScreen.ATTACK) {
							selectedAction = null

							if (galaxyGroupSystem.get(GroupSystem.SELECTED).isNotEmpty()) {
								val selectedEntities = galaxyGroupSystem.get(GroupSystem.SELECTED).filter {

									val planetarySystem = planetarySystemMapper.get(it)
									system == planetarySystem?.system && weaponFamily.isInterested(it)
								}

								var target: Entity? = null
								
								if (entitiesUnderMouse.isNotEmpty()) {
									target = entitiesUnderMouse[0]
									println("Attacking ${target.printID()}")
									
								} else {
									println("Clearing attack target")
								}

								system.lock.write {
									for (entity in selectedEntities) {
										val ship = shipMapper.get(entity)
										var weaponsComponent = weaponsComponentMapper.get(entity)

										for (tc in weaponsComponent.targetingComputers) {
											val tcState = ship.getPartState(tc)[TargetingComputerState::class]

											tcState.target = target
										}
									}
								}
							}
						}

						return true;
					}
				}
				Input.Buttons.MIDDLE -> {
					selectedAction = null
					moveWindow = true
					dragX = screenX
					dragY = screenY
					return true
				}
				Input.Buttons.RIGHT -> {
					selectedAction = null

					if (galaxyGroupSystem.get(GroupSystem.SELECTED).isNotEmpty()) {

						val selectedEntities = galaxyGroupSystem.get(GroupSystem.SELECTED).filter {

							val planetarySystem = planetarySystemMapper.get(it)
							system == planetarySystem?.system && movementFamily.isInterested(it)
						}

						if (selectedEntities.isNotEmpty()) {

							system.lock.read {

								val mouseInGameCoordinates = toWorldCordinates(getMouseInScreenCordinates(screenX, screenY))
								val entitiesUnderMouse = ArrayList<Entity>()
								val entityIDs = selectionFamily.entities
								val testCircle = CircleL()
								val zoom = camera.zoom

								// Exact check first
								entityIDs.forEach { entityID ->
									val position = movementMapper.get(entityID).get(galaxy.time).value.position
									val radius: Float

									if (renderSystem.inStrategicView(entityID, zoom)) {

										radius = zoom * RenderSystem.STRATEGIC_ICON_SIZE / 2

									} else {

										radius = circleMapper.get(entityID).radius
									}

									testCircle.set(position, radius * 1000)

									if (testCircle.contains(mouseInGameCoordinates)) {
										entitiesUnderMouse.add(system.world.getEntity(entityID))
									}
								}

								// Lenient check if empty
								if (entitiesUnderMouse.isEmpty()) {
									entityIDs.forEach { entityID ->
										val position = movementMapper.get(entityID).get(galaxy.time).value.position
										val radius: Float

										if (renderSystem.inStrategicView(entityID, zoom)) {

											radius = zoom * RenderSystem.STRATEGIC_ICON_SIZE / 2

										} else {

											radius = circleMapper.get(entityID).radius * 1.1f + 2 * camera.zoom
										}

										testCircle.set(position, radius * 1000)

										if (testCircle.contains(mouseInGameCoordinates)) {
											entitiesUnderMouse.add(system.world.getEntity(entityID))
										}
									}
								}

								if (entitiesUnderMouse.isNotEmpty()) {

//									println("Issuing move to entity order")

									val targetEntity = entitiesUnderMouse.get(0)
									var approachType = ApproachType.BRACHISTOCHRONE

									if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
										approachType = ApproachType.BALLISTIC
									}

									for (entity in selectedEntities) {

										movementSystem.moveToEntity(entity.id, targetEntity.id, approachType)
									}

								} else {

//									println("Issuing move to position order")

									val targetPosition = mouseInGameCoordinates
									var approachType = ApproachType.BRACHISTOCHRONE

									if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
										approachType = ApproachType.BALLISTIC
									}

									for (entity in selectedEntities) {

										movementSystem.moveToPosition(entity.id, targetPosition, approachType)
									}
								}

								return true;
							}
						}
					}
				}
			}

		} else {

			if (dragSelect && button != Input.Buttons.LEFT) {
				dragSelect = false;
				return true
			}

			if (moveWindow && button != Input.Buttons.MIDDLE) {
				moveWindow = false;
				return true
			}
		}

		return false;
	}

	override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {

		if (moveWindow && button == Input.Buttons.MIDDLE) {
			moveWindow = false;
			return true
		}

		if (dragSelect && button == Input.Buttons.LEFT) {

			if (galaxyGroupSystem.get(GroupSystem.SELECTED).isNotEmpty() && !Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
				galaxyGroupSystem.clear(GroupSystem.SELECTED)
//				println("cleared selection")
			}

			val dragSelection = getDragSelection()
//			println("dragSelection $dragSelection")

			val p1GameCoordinates = toWorldCordinates(Vector3(dragSelection.x.toFloat(), (viewport.screenHeight - dragSelection.y).toFloat(), 0f))
			val p2GameCoordinates = toWorldCordinates(Vector3((dragSelection.x + dragSelection.width).toFloat(), (viewport.screenHeight - (dragSelection.y + dragSelection.height)).toFloat(), 0f))
//			println("p1GameCoordinates $p1GameCoordinates, p2GameCoordinates $p2GameCoordinates")

			val entitiesInSelection = ArrayList<Entity>()
			val entityIDs = selectionFamily.entities
			val testRectangle = RectangleL(p1GameCoordinates.x, p1GameCoordinates.y, p2GameCoordinates.x - p1GameCoordinates.x, p2GameCoordinates.y - p1GameCoordinates.y)
//			println("testRectangle $testRectangle")

			// Exact check first
			entityIDs.forEach { entityID ->
				val position = movementMapper.get(entityID).get(galaxy.time).value.position

				if (testRectangle.contains(position)) {
					entitiesInSelection.add(system.world.getEntity(entityID))
				}
			}

			if (entitiesInSelection.isNotEmpty()) {
				galaxyGroupSystem.add(entitiesInSelection, GroupSystem.SELECTED)
//				println("drag selected ${entitiesInSelection.size} entities")
			}

			dragSelect = false;
			return true
		}

		if (dragSelectPotentialStart && button == Input.Buttons.LEFT) {
			if (galaxyGroupSystem.get(GroupSystem.SELECTED).isNotEmpty() && !Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
				galaxyGroupSystem.clear(GroupSystem.SELECTED)
//				println("cleared selection")
			}
		}

		return false;
	}

	override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {

		if (moveWindow) {
			var mouseScreenNow = Vector3(screenX.toFloat(), screenY.toFloat(), 0f)
			var mouseWorldNow = camera.unproject(mouseScreenNow.cpy())
			var mouseWorldBefore = camera.unproject(mouseScreenNow.cpy().add(Vector3((dragX - screenX).toFloat(), (dragY - screenY).toFloat(), 0f)))

			var diff = mouseWorldNow.cpy().sub(mouseWorldBefore)

			cameraOffset.sub(diff.x.toLong(), diff.y.toLong())

			//TODO ensure camera position is always inside the solar system

			dragX = screenX;
			dragY = screenY;

			return true;
		}

		if (dragSelectPotentialStart) {

			val dx = dragX - screenX
			val dy = dragY - screenY

			if (Math.sqrt((dx * dx + dy * dy).toDouble()) > 10) {

				dragSelectPotentialStart = false
				dragSelect = true
//				println("drag select start")
			}
		}

		return false;
	}

	override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
		return false;
	}

	// Converts from input coordinates to screen coordinates
	private fun getDragSelection(alwaysPositive: Boolean = true): RectangleL {

		var x = dragX
		var y = viewport.screenHeight - dragY
		var width = Gdx.input.getX(0) - dragX
		var height = dragY - Gdx.input.getY(0)

		if (alwaysPositive) {
			if (width < 0) {
				x += width
				width = -width
			}

			if (height < 0) {
				y += height
				height = -height
			}
		}

		return RectangleL(x.toLong(), y.toLong(), width.toLong(), height.toLong())
	}

	fun getMouseInScreenCordinates(): Vector3 {
		return getMouseInScreenCordinates(Gdx.input.getX(0), Gdx.input.getY(0))
	}

	fun getMouseInScreenCordinates(screenX: Int, screenY: Int): Vector3 {
		return Vector3(screenX.toFloat(), screenY.toFloat(), 0f)
	}

	fun toWorldCordinates(screenCoordinates: Vector3): Vector2L {
		// unproject screen coordinates to corresponding world position
		val cameraRelativeWorldPosition = camera.unproject(screenCoordinates);
		val worldPosition = Vector2L(cameraRelativeWorldPosition.x.toLong(), cameraRelativeWorldPosition.y.toLong()).add(cameraOffset)
		worldPosition.scl(1000) // km to m
		return worldPosition
	}

	// -1 for zoom-in. 1 for zoom out
	override fun scrolled(amount: Int): Boolean {

		var oldZoom = camera.zoom

		zoomLevel += amount;
		if (zoomLevel < 0) {
			zoomLevel = 0
		}

		// camera.zoom >= 1
		camera.zoom = Math.pow(zoomSensitivity, zoomLevel.toDouble()).toFloat()

//		System.out.println("zoom:" + camera.zoom + "  zoomLevel:" + zoomLevel);

		if (camera.zoom > maxZoom) {
			camera.zoom = maxZoom;
			zoomLevel = (Math.log(camera.zoom.toDouble()) / Math.log(zoomSensitivity)).toInt();
		}

		if (amount < 0) {
//			Det som var under musen innan scroll ska fortsätta vara där efter zoom
//			http://stackoverflow.com/questions/932141/zooming-an-object-based-on-mouse-position

			var diff = camera.position.cpy().sub(camera.unproject(getMouseInScreenCordinates()));
			diff = diff.sub(diff.cpy().scl(1 / oldZoom).scl(camera.zoom))
			cameraOffset.sub(diff.x.toLong(), diff.y.toLong())

			//TODO ensure cameraOffset is always inside the solar system
		}

		camera.update();

		return true;
	}

	override fun dispose() {
	}
}
