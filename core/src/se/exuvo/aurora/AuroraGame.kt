package se.exuvo.aurora

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application.GLDebugMessageSeverity
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.profiling.GLProfiler
import org.apache.logging.log4j.LogManager
import java.util.concurrent.locks.ReentrantReadWriteLock
import com.badlogic.gdx.Input

interface AuroraGame : ApplicationListener {
	fun update()
	var shapeRenderer: ShapeRenderer
	var spriteBatch: SpriteBatch
	
	companion object {
		public lateinit var currentWindow: AuroraGame
	}
}

interface Resizable {
	fun resize(width: Int, height: Int)
}

class AuroraGameMainWindow() : AuroraGame {
	val log = LogManager.getLogger(this.javaClass)
	override lateinit var shapeRenderer: ShapeRenderer
	override lateinit var spriteBatch: SpriteBatch

	override fun create() {
		Lwjgl3Application.setGLDebugMessageControl(GLDebugMessageSeverity.HIGH, true)
		Lwjgl3Application.setGLDebugMessageControl(GLDebugMessageSeverity.MEDIUM, true)
		Lwjgl3Application.setGLDebugMessageControl(GLDebugMessageSeverity.LOW, true)
		Lwjgl3Application.setGLDebugMessageControl(GLDebugMessageSeverity.NOTIFICATION, true)

		shapeRenderer = ShapeRenderer()
		spriteBatch = SpriteBatch()
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
