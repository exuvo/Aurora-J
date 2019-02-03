package se.exuvo.aurora.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Disposable
import se.exuvo.aurora.Assets
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Units
import se.exuvo.settings.Settings
import se.unlogic.standardutils.threads.ThreadUtils
import java.util.LinkedList
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20

class GameScreenService : Disposable, InputProcessor {
	private val inputMultiplexer = InputMultiplexer()
	private val spriteBatch by lazy (LazyThreadSafetyMode.NONE) { GameServices[SpriteBatch::class] }
	val uiCamera = OrthographicCamera()
	private val screens = LinkedList<GameScreen>()
	private val addQueue = LinkedList<GameScreen>()

	fun <T : GameScreen> add(screen: T) {
		addQueue.add(screen)
	}

	val frameDelay = Units.NANO_SECOND / Settings.getInt("Window/FrameLimit", 60)
	var accumulator = 0L
	var lastRun = System.nanoTime()
	var lastDrawStart = System.nanoTime()
	var frameStartTime = 0L
	
	fun update(deltaRealTime: Float): Boolean {

		if (addQueue.isNotEmpty()) {

			for (screen in addQueue) {
				screens.addFirst(screen)
				screen.show()
				screen.resize(Gdx.graphics.width, Gdx.graphics.height)

				if (screen is InputProcessor) {
					inputMultiplexer.addProcessor(screen)
				}
			}

			addQueue.clear()
		}

		val it = screens.iterator()
		var firstRealScreenFound = false

		while (it.hasNext()) {
			val screen = it.next()

			if (!screen.overlay) {
				if (firstRealScreenFound) {

					it.remove()
					screen.hide()
					screen.dispose()

					if (screen is InputProcessor) {
						inputMultiplexer.removeProcessor(screen)
					}

				} else {
					firstRealScreenFound = true
				}
			}
		}

		screens.forEach { it.update(deltaRealTime) }
		
		var shouldRender = false
		val now = System.nanoTime()
		accumulator += now - lastRun;

		if (accumulator >= frameDelay) {
			accumulator -= frameDelay

			if (accumulator > frameDelay) {
				accumulator = frameDelay
			}

//			println("frameDelay $frameDelay, diff $frameTime, accumulator $accumulator")

			shouldRender = true
			
		} else if (accumulator < frameDelay && frameDelay > Units.NANO_MILLI) {

			var sleepTime = (frameDelay - accumulator) / Units.NANO_MILLI

			if (sleepTime > 1) {
				ThreadUtils.sleep(sleepTime - 1)
			} else {
				Thread.yield()
			}
		}

		lastRun = now;
		
		return shouldRender
	}
	
	private val clearColor = Color.BLACK
	
	fun render() {
		val now = System.nanoTime()
		
		frameStartTime = now - lastDrawStart
		lastDrawStart = now
		
		Gdx.gl.glClearColor(clearColor.r, clearColor.g, clearColor.b, clearColor.a)
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

		screens.forEach { it.draw() }

		spriteBatch.projectionMatrix = uiCamera.combined
		spriteBatch.begin()

		Assets.fontUI.draw(spriteBatch, "${Gdx.graphics.framesPerSecond} ${formatFrameTime(frameStartTime)}", 2f, uiCamera.viewportHeight - 3f)
		spriteBatch.end()
	}
	
	private fun formatFrameTime(nanotime: Long): String {
		val centinanos = ((nanotime / 10000) % 100).toInt()
		val milli = (nanotime / Units.NANO_MILLI).toInt()
		
		return String.format("%02d.%02dms", milli, centinanos)
	}

	fun pause() {
		screens.forEach { it.pause() }
	}

	fun resize(width: Int, height: Int) {
		uiCamera.setToOrtho(false, width.toFloat(), height.toFloat())
		screens.forEach { it.resize(width, height) }
	}

	fun resume() {
		screens.forEach { it.resume() }
	}

	override fun dispose() {
		screens.forEach { it.dispose() }
	}

	override fun keyDown(keycode: Int): Boolean {
//		println("keyDown $keycode ${Input.Keys.toString(keycode)}")

		return inputMultiplexer.keyDown(keycode)
	}

	override fun keyUp(keycode: Int): Boolean {
		return inputMultiplexer.keyUp(keycode)
	}

	override fun keyTyped(character: Char): Boolean {
//		println("keyTyped $character")

		return inputMultiplexer.keyTyped(character)
	}

	override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
		return inputMultiplexer.touchDown(screenX, screenY, pointer, button)
	}

	override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
		return inputMultiplexer.touchUp(screenX, screenY, pointer, button)
	}

	override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
		return inputMultiplexer.touchDragged(screenX, screenY, pointer)
	}

	override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
		return inputMultiplexer.mouseMoved(screenX, screenY)
	}

	override fun scrolled(amount: Int): Boolean {
		return inputMultiplexer.scrolled(amount)
	}

}
