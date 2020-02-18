package se.exuvo.aurora.ui

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
import se.exuvo.aurora.utils.exponentialAverage
import se.exuvo.aurora.AuroraGame
import kotlin.reflect.KClass
import com.badlogic.gdx.Screen

class GameScreenService : Disposable, InputProcessor {
	private val inputMultiplexer = InputMultiplexer()
	val uiCamera = OrthographicCamera()
	private val screens = LinkedList<GameScreen>()
	private val addQueue = LinkedList<GameScreen>()

	fun <T : GameScreen> add(screen: T) {
		addQueue.add(screen)
	}
	
	@Suppress("UNCHECKED_CAST")
	operator fun <T : GameScreen> get(screenClass: KClass<T>): T {
		
		screens.forEach { screen ->
			if (screenClass.isInstance(screen)) {
				return screen as T
			}
		}
		
		throw NullPointerException()
	}
	
	@Suppress("UNCHECKED_CAST")
	operator fun <T : GameScreen> invoke(screenClass: KClass<T>): T? {
		
		screens.forEach { screen ->
			if (screenClass.isInstance(screen)) {
				return screen as T
			}
		}
		
		return null
	}

	var lastDrawStart = System.nanoTime()
	var frameStartTime = 0L
	
	fun update(deltaRealTime: Float) {

		if (addQueue.isNotEmpty()) {

			for (screen in addQueue) {
				screens.addFirst(screen)

				if (screen is InputProcessor) {
					inputMultiplexer.addProcessor(screen)
				}
			}
			
			for (screen in addQueue) {
				screen.show()
				screen.resize(Gdx.graphics.width, Gdx.graphics.height)
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
	}
	
	private val clearColor = Color.BLACK
	var renderTimeAverage = 0.0
	
	fun render() {
		val spriteBatch = AuroraGame.currentWindow.spriteBatch
		val now = System.nanoTime()
		
		frameStartTime = now - lastDrawStart
		lastDrawStart = now
		
		Gdx.gl.glClearColor(clearColor.r, clearColor.g, clearColor.b, clearColor.a)
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

		screens.forEach { it.draw() }
		
		val renderTime = System.nanoTime() - now
		renderTimeAverage = exponentialAverage(renderTime.toDouble(), renderTimeAverage, 10.0)

		spriteBatch.projectionMatrix = uiCamera.combined
		spriteBatch.begin()

		Assets.fontUI.draw(spriteBatch, "${Gdx.graphics.framesPerSecond} ${formatFrameTime(frameStartTime)}, ${formatFrameTime(renderTimeAverage.toLong())}", 2f, uiCamera.viewportHeight - 3f)
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

interface GameScreen : Screen {
    val overlay: Boolean

    fun update(deltaRealTime: Float)
    fun draw()
}

abstract class GameScreenImpl : GameScreen {
    override val overlay = false

    override fun show() {
    }

    override fun hide() {
    }

    override fun resize(width: Int, height: Int) {
    }

    override fun update(deltaRealTime: Float) {
    }

    override fun draw() {
    }

    override fun pause() {
    }

    override fun resume() {
    }

    override fun dispose() {
    }

    override fun render(deltaRealTime: Float) {
        throw UnsupportedOperationException()
    }
}