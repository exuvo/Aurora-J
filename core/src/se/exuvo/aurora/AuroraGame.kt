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
import com.esotericsoftware.kryonet.MinlogTolog4j
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.history.History
import se.exuvo.aurora.planetarysystems.PlanetarySystem
import se.exuvo.aurora.planetarysystems.systems.GroupSystem
import se.exuvo.aurora.screens.GameScreenService
import se.exuvo.aurora.screens.LoadingScreen
import se.exuvo.aurora.screens.PlanetarySystemScreen
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.keys.KeyMappings
import se.exuvo.settings.Settings
import java.util.concurrent.locks.ReentrantReadWriteLock
import se.exuvo.aurora.utils.Storage
import se.exuvo.aurora.screens.ImGuiScreen

interface AuroraGame : ApplicationListener {
	fun update()
	var screenService: GameScreenService
	var shapeRenderer: ShapeRenderer
	var spriteBatch: SpriteBatch
	val storage: Storage
	
	companion object {
		public lateinit var currentWindow: AuroraGame
	}
}

class AuroraGameMainWindow(val assetsRoot: String) : AuroraGame {
	val log = LogManager.getLogger(this.javaClass)
	override lateinit var screenService: GameScreenService
	override lateinit var shapeRenderer: ShapeRenderer
	override lateinit var spriteBatch: SpriteBatch
	override val storage = Storage()

	override fun create() {
		com.esotericsoftware.minlog.Log.setLogger(MinlogTolog4j())
		com.esotericsoftware.minlog.Log.set(com.esotericsoftware.minlog.Log.LEVEL_WARN)

		Lwjgl3Application.setGLDebugMessageControl(GLDebugMessageSeverity.HIGH, true)
		Lwjgl3Application.setGLDebugMessageControl(GLDebugMessageSeverity.MEDIUM, true)
		Lwjgl3Application.setGLDebugMessageControl(GLDebugMessageSeverity.LOW, true)
		Lwjgl3Application.setGLDebugMessageControl(GLDebugMessageSeverity.NOTIFICATION, true)

		KeyMappings.load()

		GameServices + GLProfiler(Gdx.graphics)
		GameServices + AssetManager(AuroraAssetsResolver(assetsRoot))
		GameServices + GroupSystem(ReentrantReadWriteLock())
		GameServices + History()

		screenService = GameScreenService()
		shapeRenderer = ShapeRenderer()
		spriteBatch = SpriteBatch()
		
		Gdx.input.setInputProcessor(screenService)

		screenService.add(LoadingScreen())
	}

	override fun resize(width: Int, height: Int) {
		screenService.resize(width, height)
	}

	override fun update() {
		screenService.update(Gdx.graphics.deltaTime)
	}

	override fun render() {
		screenService.render()
	}

	override fun pause() {
		screenService.pause()
	}

	override fun resume() {
		screenService.resume()
	}

	override fun dispose() {
		//TODO close all other windows first
		storage.dispose()
		Assets.dispose()
		GameServices.dispose()
		KeyMappings.save()
		Settings.save()
	}
}

class AuroraGameSecondaryWindow(val system: PlanetarySystem) : AuroraGame {
	val log = LogManager.getLogger(this.javaClass)
	override lateinit var screenService: GameScreenService
	override lateinit var shapeRenderer: ShapeRenderer
	override lateinit var spriteBatch: SpriteBatch
	override val storage = Storage()

	override fun create() {
		
		screenService = GameScreenService()
		shapeRenderer = ShapeRenderer()
		spriteBatch = SpriteBatch()
		
		Gdx.input.setInputProcessor(screenService)

		screenService.add(ImGuiScreen())
		screenService.add(PlanetarySystemScreen(system))
	}

	override fun resize(width: Int, height: Int) {
		screenService.resize(width, height)
	}

	override fun update() {
		screenService.update(Gdx.graphics.deltaTime)
	}

	override fun render() {
		screenService.render()
	}

	override fun pause() {
		screenService.pause()
	}

	override fun resume() {
		screenService.resume()
	}

	override fun dispose() {
		storage.dispose()
		screenService.dispose()
	}
}

class AuroraAssetsResolver(val assetsRoot: String) : FileHandleResolver {

	override fun resolve(fileName: String): FileHandle {
		return Gdx.files.internal(assetsRoot + fileName)
	}
}