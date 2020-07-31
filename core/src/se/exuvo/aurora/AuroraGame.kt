package se.exuvo.aurora

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application.GLDebugMessageSeverity

interface AuroraGame : ApplicationListener {
	fun update()
	
	companion object {
		public lateinit var currentWindow: AuroraGame
	}
}

interface Resizable {
	fun resize(width: Int, height: Int)
}

class AuroraGameMainWindow() : AuroraGame {

	override fun create() {
		Lwjgl3Application.setGLDebugMessageControl(GLDebugMessageSeverity.HIGH, true)
		Lwjgl3Application.setGLDebugMessageControl(GLDebugMessageSeverity.MEDIUM, true)
		Lwjgl3Application.setGLDebugMessageControl(GLDebugMessageSeverity.LOW, true)
		Lwjgl3Application.setGLDebugMessageControl(GLDebugMessageSeverity.NOTIFICATION, true)
	}

	override fun resize(width: Int, height: Int) {
		println("resize width $width height $height bbw ${Gdx.graphics.getBackBufferWidth()} bbh ${Gdx.graphics.getBackBufferHeight()}")
		
	}

	override fun update() {
		if (Gdx.input.isKeyPressed(Input.Keys.ESCAPE)) {
			Gdx.app.exit()
		}
	}

	override fun render() {
	}

	override fun pause() {
	}

	override fun resume() {
	}

	override fun dispose() {
	}
}
