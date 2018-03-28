package se.exuvo.aurora.screens

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.utils.viewport.Viewport
import se.exuvo.aurora.Assets
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.systems.GalacticRenderSystem
import se.exuvo.aurora.planetarysystems.PlanetarySystem
import se.exuvo.aurora.planetarysystems.components.GalacticPositionComponent
import se.exuvo.aurora.planetarysystems.systems.GroupSystem
import se.exuvo.aurora.utils.CircleL
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.settings.Settings
import kotlin.concurrent.read
import kotlin.properties.Delegates

class GalaxyScreen(var lastSystemScreen: PlanetarySystemScreen) : GameScreenImpl(), InputProcessor {

	private val spriteBatch = GameServices[SpriteBatch::class.java]
	private val shapeRenderer = GameServices[ShapeRenderer::class.java]
	private val galaxy by lazy { GameServices[Galaxy::class.java] }
	private val galaxyGroupSystem by lazy { GameServices[GroupSystem::class.java] }
	private val renderSystem by lazy { galaxy.engine.getSystem(GalacticRenderSystem::class.java) }

	private val uiCamera = GameServices[GameScreenService::class.java].uiCamera
	private var viewport by Delegates.notNull<Viewport>()
	private var camera by Delegates.notNull<OrthographicCamera>()
	private val cameraOffset = Vector2L()

	private val positionMapper = ComponentMapper.getFor(GalacticPositionComponent::class.java)

	var zoomLevel = 0
	var zoomSensitivity = Settings.getFloat("UI.zoomSensitivity").toDouble()
	val maxZoom = 1.5E1f

	init {
		viewport = ScreenViewport()
		camera = viewport.camera as OrthographicCamera

		viewport.update(Gdx.graphics.width, Gdx.graphics.height)
		camera.zoom = 0.3E1f;
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

		galaxy.engineLock.read {
			renderSystem.render(viewport, cameraOffset)
		}

		drawUI()
	}

	private fun drawUI() {
		spriteBatch.projectionMatrix = uiCamera.combined
		spriteBatch.begin()
		Assets.fontUI.draw(spriteBatch, "System view, zoomLevel $zoomLevel, day ${galaxy.day}, time ${secondsToString(galaxy.time)}, speed ${Units.NANO_SECOND / galaxy.speed}", 8f, 32f)
		spriteBatch.end()
	}

	private fun secondsToString(time: Long): String {
		val hours = (time / 3600) % 24
		val minutes = (time / 60) % 60
		val seconds = time % 60
		return String.format("%02d:%02d:%02d", hours, minutes, seconds)
	}

	override fun keyDown(keycode: Int): Boolean {

		if (keycode == Input.Keys.M) {
			GameServices[GameScreenService::class.java].add(lastSystemScreen)
		}

		if (keycode == Input.Keys.PLUS) {

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

		} else if (keycode == Input.Keys.MINUS) {

			var speed = galaxy.speed * 4

			if (speed > 1 * Units.NANO_SECOND) {
				speed = 1 * Units.NANO_SECOND
			}

			galaxy.speed = speed

			if (galaxy.sleeping) {
				galaxy.thread!!.interrupt()
			}

			return true

		} else if (keycode == Input.Keys.SPACE) {

			galaxy.paused = !galaxy.paused

			if (galaxy.sleeping) {
				galaxy.thread!!.interrupt()
			}

			return true
		}

		return false;
	}

	override fun keyUp(keycode: Int): Boolean {
		return false;
	}

	override fun keyTyped(character: Char): Boolean {
		return false;
	}

	var moveWindow = false
	var dragX = 0
	var dragY = 0

	val selectionFamily = GalacticRenderSystem.FAMILY

	override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {

		if (!moveWindow) {

			when (button) {
				Input.Buttons.LEFT -> {

					galaxy.engineLock.read {

						val mouseInGalacticCoordinates = toGalacticWorldCordinates(getMouseInScreenCordinates(screenX, screenY))
						val entities = galaxy.engine.getEntitiesFor(selectionFamily)
						val testCircle = CircleL()
						val radius = GalacticRenderSystem.RENDER_SCALE * camera.zoom * GalacticRenderSystem.STRATEGIC_ICON_SIZE / 2
						
						for (entity in entities) {
							val position = positionMapper.get(entity).position

							testCircle.set(position, radius)

							if (testCircle.contains(mouseInGalacticCoordinates)) {
								
								val system = entity as PlanetarySystem
								lastSystemScreen = PlanetarySystemScreen(system)
								GameServices[GameScreenService::class.java].add(lastSystemScreen)
	
								return true;
							}
						}
					}
				}
				Input.Buttons.MIDDLE -> {
					moveWindow = true;
					dragX = screenX;
					dragY = screenY;
					return true;
				}
//				Input.Buttons.RIGHT -> {
//
//					if (galaxyGroupSystem.get(GroupSystem.SELECTED).isNotEmpty()) {
//
//						val selectedEntities = galaxyGroupSystem.get(GroupSystem.SELECTED).filter { MovementSystem.CAN_ACCELERATE_FAMILY.matches(it) }
//
//						if (selectedEntities.isNotEmpty()) {
//
//							galaxy.engineLock.read {
//
//								val mouseInGalacticCoordinates = toGalacticWorldCordinates(getMouseInScreenCordinates(screenX, screenY))
//								val entitiesUnderMouse = ArrayList<Entity>()
//								val entities = galaxy.engine.getEntitiesFor(selectionFamily)
//								val testCircle = CircleL()
//								val zoom = camera.zoom
//
//								// Exact check first
//								for (entity in entities) {
//									val position = positionMapper.get(entity).position
//									val radius = zoom * RenderSystem.STRATEGIC_ICON_SIZE / 2
//
//									testCircle.set(position, radius * 1.1f)
//
//									if (testCircle.contains(mouseInGalacticCoordinates)) {
//										entitiesUnderMouse.add(entity)
//									}
//								}
//
//								// Lenient check if empty
//								if (entitiesUnderMouse.isEmpty()) {
//									for (entity in entities) {
//										val position = positionMapper.get(entity).position
//										val radius = zoom * RenderSystem.STRATEGIC_ICON_SIZE / 2
//
//										testCircle.set(position, radius * 1.1f)
//
//										if (testCircle.contains(mouseInGalacticCoordinates)) {
//											entitiesUnderMouse.add(entity)
//										}
//									}
//								}
//
//								if (entitiesUnderMouse.isNotEmpty()) {
//
//									println("Issuing move to solar system order")
//
////									val targetEntity = entitiesUnderMouse.get(0)
////									var approachType = ApproachType.BRACHISTOCHRONE
////
////									if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
////										approachType = ApproachType.BALLISTIC
////									}
////
////									for (entity in selectedEntities) {
////
////										var moveToPositionComponent = moveToPositionMapper.get(entity)
////
////										if (moveToPositionComponent != null) {
////											entity.remove(moveToPositionComponent::class.java)
////										}
////
////										var moveToEntityComponent = moveToEntityMapper.get(entity)
////
////										if (moveToEntityComponent != null) {
////											moveToEntityComponent.apply { target = targetEntity; approach = approachType }
////
////										} else {
////											moveToEntityComponent = MoveToEntityComponent(targetEntity, approachType)
////											entity.add(moveToEntityComponent)
////										}
////									}
//									
//									return true;
//								}
//							}
//						}
//					}
//				}
			}

		} else {

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

		return false;
	}

	override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
		return false;
	}

	fun getMouseInScreenCordinates(): Vector3 {
		return getMouseInScreenCordinates(Gdx.input.getX(0), Gdx.input.getY(0))
	}

	fun getMouseInScreenCordinates(screenX: Int, screenY: Int): Vector3 {
		return Vector3(screenX.toFloat(), screenY.toFloat(), 0f)
	}

	fun toGalacticWorldCordinates(screenCoordinates: Vector3): Vector2L {
		// unproject screen coordinates to corresponding world position
		val cameraRelativeWorldPosition = camera.unproject(screenCoordinates);
		val worldPosition = Vector2L(cameraRelativeWorldPosition.x.toLong(), cameraRelativeWorldPosition.y.toLong()).add(cameraOffset)
		worldPosition.scl(GalacticRenderSystem.RENDER_SCALE.toLong())
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
	
	fun centerOnPlanetarySystem(system: PlanetarySystem) {
		
	}
	
	override fun dispose() {
	}
}
