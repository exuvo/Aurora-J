package se.exuvo.aurora.screens

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.badlogic.gdx.utils.viewport.Viewport
import se.exuvo.aurora.Assets
import se.exuvo.aurora.Galaxy
import se.exuvo.aurora.SolarSystem
import se.exuvo.aurora.components.CircleComponent
import se.exuvo.aurora.components.PositionComponent
import se.exuvo.aurora.components.RenderComponent
import se.exuvo.aurora.systems.GroupSystem
import se.exuvo.aurora.systems.OrbitSystem
import se.exuvo.aurora.systems.RenderSystem
import se.exuvo.aurora.utils.CircleL
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.NanoTimeUnits
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.settings.Settings
import kotlin.properties.Delegates

class SolarSystemScreen(val system: SolarSystem) : GameScreenImpl(), InputProcessor {

	private val spriteBatch = GameServices[SpriteBatch::class.java]
	private val renderer = GameServices[ShapeRenderer::class.java]
	private val galaxy by lazy { GameServices[Galaxy::class.java] }
	private val uiCamera = GameServices[GameScreenService::class.java].uiCamera
	private var viewport by Delegates.notNull<Viewport>()
	private var camera by Delegates.notNull<OrthographicCamera>()
	private val cameraOffset = Vector2L()

	private val circleMapper = ComponentMapper.getFor(CircleComponent::class.java)
	private val positionMapper = ComponentMapper.getFor(PositionComponent::class.java)
	private val groupSystem by lazy { system.engine.getSystem(GroupSystem::class.java) }

	var zoomLevel = 0
	var zoomSensitivity = Settings.getFloat("UI.zoomSensitivity").toDouble()
	val maxZoom = 1E8f

	override fun show() {

		viewport = ExtendViewport(500f, 500f)
		camera = viewport.camera as OrthographicCamera

		viewport.update(Gdx.graphics.width, Gdx.graphics.height)
		camera.zoom = 1E6f;
		zoomLevel = (Math.log(camera.zoom.toDouble()) / Math.log(zoomSensitivity)).toInt();
	}

	override fun resize(width: Int, height: Int) {
		viewport.update(width, height)
	}

	override fun update(delta: Float) {

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

		system.lock.readLock().lock()
		try {
			system.engine.getSystem(OrbitSystem::class.java).render(viewport, cameraOffset)
			system.engine.getSystem(RenderSystem::class.java).render(viewport, cameraOffset)
		} finally {
			system.lock.readLock().unlock()
		}

		drawUI()
	}

	private fun drawUI() {
		spriteBatch.projectionMatrix = uiCamera.combined
		spriteBatch.begin()
		Assets.fontUI.draw(spriteBatch, "System view, zoomLevel $zoomLevel, day ${galaxy.day}, time ${secondsToString(galaxy.time)}, speed ${NanoTimeUnits.SECOND / galaxy.speed}", 8f, 32f)
		spriteBatch.end()
	}

	private fun secondsToString(time: Long): String {
		val hours = (time / 3600) % 24
		val minutes = (time / 60) % 60
		val seconds = time % 60
		return String.format("%02d:%02d:%02d", hours, minutes, seconds)
	}

	override fun keyDown(keycode: Int): Boolean {

		if (keycode == Input.Keys.PLUS) {

			var speed = galaxy.speed / 4

			if (speed == 0L) {
				speed = 1
			}

			galaxy.speed = speed

			if (galaxy.sleeping) {
				galaxy.thread!!.interrupt()
			}

			return true

		} else if (keycode == Input.Keys.MINUS) {

			var speed = galaxy.speed * 4

			if (speed > 1 * NanoTimeUnits.SECOND) {
				speed = 1 * NanoTimeUnits.SECOND
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

	var dragSelectPotentialStart = false
	var dragSelect = false
	val selectionFamily = Family.all(PositionComponent::class.java, RenderComponent::class.java).one(CircleComponent::class.java).get()

	var dragX = 0
	var dragY = 0

	override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {

		if (!moveWindow && !dragSelect) {

			if (button == Input.Buttons.LEFT) {

				system.lock.readLock().lock()
				try {

					if (groupSystem.get(GroupSystem.SELECTED).isNotEmpty() && !Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
						groupSystem.clear(GroupSystem.SELECTED)
						println("cleared selection")
					}

					val mouseInGameCoordinates = getMouseInWorldCordinates(getMouseInScreenCordinates(screenX, screenY))
					val entitiesUnderMouse = ArrayList<Entity>()
					val entities = system.engine.getEntitiesFor(selectionFamily)
					val testCircle = CircleL()

					// Exact check first
					for (entity in entities) {
						val position = positionMapper.get(entity).position
						val radius = circleMapper.get(entity).radius
						testCircle.set(position, radius)

						if (testCircle.contains(mouseInGameCoordinates)) {
							entitiesUnderMouse.add(entity)
						}
					}

					// Lenient check if empty
					if (entitiesUnderMouse.isEmpty()) {
						for (entity in entities) {
							val position = positionMapper.get(entity).position
							val radius = circleMapper.get(entity).radius * 1.1f + 2 * camera.zoom
							testCircle.set(position, radius)

							if (testCircle.contains(mouseInGameCoordinates)) {
								entitiesUnderMouse.add(entity)
							}
						}

						if (entitiesUnderMouse.isNotEmpty()) {
							println("lenient selected ${entitiesUnderMouse.size} entities")
						}

					} else {
						println("strict selected ${entitiesUnderMouse.size} entities")
					}

					if (entitiesUnderMouse.isNotEmpty()) {

						groupSystem.add(entitiesUnderMouse, GroupSystem.SELECTED)

					} else {

						dragSelectPotentialStart = true;
						dragX = screenX;
						dragY = screenY;
					}

					return true;

				} finally {
					system.lock.readLock().unlock()
				}
			}

			if (button == Input.Buttons.MIDDLE) {
				moveWindow = true;
				dragX = screenX;
				dragY = screenY;
				return true;
			}
		}

		return false;
	}

	override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {

		if (moveWindow && button == Input.Buttons.MIDDLE) {
			moveWindow = false;
			return true
		}

		if (dragSelect) {

			if (button == Input.Buttons.LEFT) {

				//TODO select things
			}

			dragSelect = false;
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

		if (dragSelectPotentialStart && Math.abs(dragX - screenX) * Math.abs(dragY - screenY) > 50) {
			dragSelectPotentialStart = false
			dragSelect = true
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

	fun getMouseInWorldCordinates(screenCoordinates: Vector3): Vector2L {
		// unproject screen coordinates to corresponding world position
		val cameraRelativeWorldPosition = camera.unproject(screenCoordinates);
		val worldPosition = Vector2L(cameraRelativeWorldPosition.x.toLong(), cameraRelativeWorldPosition.y.toLong()).add(cameraOffset)
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
