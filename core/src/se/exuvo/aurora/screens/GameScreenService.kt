package se.exuvo.aurora.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Disposable
import se.exuvo.aurora.Assets
import se.exuvo.aurora.utils.GameServices
import java.util.Stack

class GameScreenService : Disposable, InputProcessor {
	private val screens = Stack<GameScreen>()
	private val inputMultiplexer = InputMultiplexer()
	private val spriteBatch by lazy { GameServices[SpriteBatch::class.java] }
	val uiCamera = OrthographicCamera()

	fun <T : GameScreen> push(screen: T) {
		screens.push(screen)
		screen.show()
		screen.resize(Gdx.graphics.width, Gdx.graphics.height)

		if (screen is InputProcessor) {
			inputMultiplexer.addProcessor(screen)
		}
	}

	private fun <T : GameScreen> remove(screen: T) {
		screens.remove(screen)
		screen.hide()
		screen.dispose()

		if (screen is InputProcessor) {
			inputMultiplexer.removeProcessor(screen)
		}
	}

	private fun update(deltaRealTime: Float) {

		if (screens.size == 0) return

		var top = true
		for (i in 0..screens.size - 1) {
			val screen = screens[screens.size - 1 - i]

			if (top) {
				screen.update(deltaRealTime)

			} else {
				remove(screen)
			}

			if (!screen.overlay) {
				top = false
			}
		}
	}

	private fun draw() {
		screens.forEach { it.draw() }
		
		spriteBatch.projectionMatrix = uiCamera.combined
		spriteBatch.begin()
		Assets.fontUI.draw(spriteBatch, "" + Gdx.graphics.framesPerSecond, 2f, uiCamera.viewportHeight - 3f)
		spriteBatch.end()
	}

	fun pause() {
		screens.forEach { it.pause() }
	}

	fun resize(width: Int, height: Int) {
		uiCamera.setToOrtho(false, width.toFloat(), height.toFloat())
		screens.forEach { it.resize(width, height) }
	}

	fun render(deltaRealTime: Float) {
		update(deltaRealTime)
		draw()
	}

	fun resume() {
		screens.forEach { it.resume() }
	}

	override fun dispose() {
		screens.forEach { it.dispose() }
	}

	override fun keyDown(keycode: Int): Boolean {
		return inputMultiplexer.keyDown(keycode)
	}

	override fun keyUp(keycode: Int): Boolean {
		return inputMultiplexer.keyUp(keycode)
	}

	override fun keyTyped(character: Char): Boolean {
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
