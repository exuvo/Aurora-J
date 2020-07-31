package se.exuvo.aurora

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application.GLDebugMessageSeverity

class AuroraGameMainWindow() : ApplicationListener {

	override fun create() {
	}

	override fun resize(width: Int, height: Int) {
		println("resize width $width height $height bbw ${Gdx.graphics.getBackBufferWidth()} bbh ${Gdx.graphics.getBackBufferHeight()}")
	}

	fun update() {
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
