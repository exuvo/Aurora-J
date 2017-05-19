package com.thedeadpixelsociety.ld34.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.thedeadpixelsociety.ld34.systems.RenderSystem
import se.exuvo.aurora.Assets
import se.exuvo.aurora.Galaxy
import se.exuvo.aurora.SolarSystem
import se.exuvo.aurora.utils.GameServices
import se.exuvo.settings.Settings
import kotlin.properties.Delegates

class SystemScreen(val system: SolarSystem) : GameScreenImpl(), InputProcessor {

	private val batch by lazy { GameServices[SpriteBatch::class.java] }
	private val renderer by lazy { GameServices[ShapeRenderer::class.java] }
	private val galaxy by lazy { GameServices[Galaxy::class.java] }
	private val uiCamera = OrthographicCamera()
	private var viewport by Delegates.notNull<Viewport>()
	private var camera by Delegates.notNull<OrthographicCamera>()

	var zoomLevel = 0
	var zoomSensitivity = Settings.getFloat("UI.zoomSensitivity").toDouble()
	val maxZoom = 1E5f

	override fun show() {

		viewport = ExtendViewport(500f, 500f)
		camera = viewport.camera as OrthographicCamera

		viewport.update(Gdx.graphics.width, Gdx.graphics.height)
	}

	override fun resize(width: Int, height: Int) {
		uiCamera.setToOrtho(false, width.toFloat(), height.toFloat())
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
			camera.translate(hDirection, vDirection)
		}
	}

	override fun draw() {
		super.draw()

		system.lock.readLock().lock()
		system.engine.getSystem(RenderSystem::class.java).render(viewport)
		system.lock.readLock().unlock()

		drawUI()
	}

	private fun drawUI() {
		batch.projectionMatrix = uiCamera.combined
		batch.begin()
		Assets.fontUI.draw(batch, "System view, zoomLevel $zoomLevel, day ${galaxy.day}, time ${secondsToString(galaxy.time)}", 8f, 32f)
		batch.end()
	}

	private fun secondsToString(time: Long): String {
		val hours = (time / 3600) % 24
		val minutes = (time / 60) % 60
		val seconds = time % 60
		return String.format("%02d:%02d:%02d", hours, minutes, seconds)
	}

	override fun keyDown(keycode: Int): Boolean {

		if (keycode == Input.Keys.PLUS) {

			galaxy.speed /= 5

			if (galaxy.speed == 0L) {
				galaxy.speed = 1
			}

			if (galaxy.sleeping) {
				galaxy.thread!!.interrupt()
			}

			return true

		} else if (keycode == Input.Keys.MINUS) {

			galaxy.speed *= 5

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

	override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {

		if (button == Input.Buttons.MIDDLE || (button == Input.Buttons.LEFT /* && nothingUnderMouse */)) {
			moveWindow = true;

			dragX = screenX;
			dragY = screenY;
			return true;
		}

		return false;
	}

	override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {

		if (moveWindow) {
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

			camera.position.sub(diff);
			camera.update();

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
		return Vector3(Gdx.input.getX(0).toFloat(), Gdx.input.getY(0).toFloat(), 0f)
	}

	fun getMouseInWorldCordinates(): Vector3 {
		// unproject screen coordinates to corresponding world position
		return camera.unproject(getMouseInScreenCordinates());
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

			var diff = camera.position.cpy().sub(getMouseInWorldCordinates());
			camera.position.sub(diff.sub(diff.cpy().scl(1 / oldZoom).scl(camera.zoom)));

			//TODO ensure camera position is always inside the solar system
		}

		camera.update();

		return true;
	}

	override fun dispose() {
	}
}
