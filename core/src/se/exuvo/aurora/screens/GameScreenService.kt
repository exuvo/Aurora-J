package se.exuvo.aurora.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Disposable
import se.exuvo.aurora.Assets
import se.exuvo.aurora.utils.GameServices
import java.util.LinkedList
import com.badlogic.gdx.Input

class GameScreenService : Disposable, InputProcessor {
	private val inputMultiplexer = InputMultiplexer()
	private val spriteBatch by lazy { GameServices[SpriteBatch::class.java] }
	val uiCamera = OrthographicCamera()
	private val screens = LinkedList<GameScreen>()
	private val addQueue = LinkedList<GameScreen>()

	fun <T : GameScreen> add(screen: T) {
		addQueue.add(screen)
	}

	private fun update(deltaRealTime: Float) {

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
