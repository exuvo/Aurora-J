package se.exuvo.aurora

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.profiling.GLErrorListener
import com.badlogic.gdx.graphics.profiling.GLProfiler
import com.esotericsoftware.kryonet.MinlogTolog4j
import org.apache.log4j.Logger
import se.exuvo.aurora.history.History
import se.exuvo.aurora.planetarysystems.systems.GroupSystem
import se.exuvo.aurora.screens.GameScreenService
import se.exuvo.aurora.screens.LoadingScreen
import se.exuvo.aurora.utils.GameServices
import se.exuvo.settings.Settings
import java.util.concurrent.locks.ReentrantReadWriteLock
import se.exuvo.aurora.utils.keys.KeyMappings
import se.exuvo.aurora.utils.keys.KeyMapping
import se.exuvo.aurora.utils.Units
import se.unlogic.standardutils.threads.ThreadUtils
import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics
import se.exuvo.aurora.galactic.Galaxy
import org.lwjgl.opengl.GLUtil
import java.io.PrintStream
import org.lwjgl.system.Configuration
import org.lwjgl.system.Callback
import org.lwjgl.opengl.GL
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application.GLDebugMessageSeverity

class AuroraGame(val assetsRoot: String) : ApplicationListener {
	val log = Logger.getLogger(this.javaClass)
	val screenService = GameScreenService()

	override fun create() {
		com.esotericsoftware.minlog.Log.setLogger(MinlogTolog4j())
		com.esotericsoftware.minlog.Log.set(com.esotericsoftware.minlog.Log.LEVEL_WARN)
		
		Lwjgl3Application.setGLDebugMessageControl(GLDebugMessageSeverity.HIGH, true)
		Lwjgl3Application.setGLDebugMessageControl(GLDebugMessageSeverity.MEDIUM, true)
		Lwjgl3Application.setGLDebugMessageControl(GLDebugMessageSeverity.LOW, true)
		Lwjgl3Application.setGLDebugMessageControl(GLDebugMessageSeverity.NOTIFICATION, true)
		
		KeyMappings.load()
		
		GameServices.put(GLProfiler(Gdx.graphics))
		GameServices.put(AssetManager(AuroraAssetsResolver(assetsRoot)))
		GameServices.put(ShapeRenderer())
		GameServices.put(SpriteBatch())
		GameServices.put(GroupSystem(ReentrantReadWriteLock()))
		GameServices.put(History())
		GameServices.put(screenService)

		Gdx.input.setInputProcessor(screenService)

		screenService.add(LoadingScreen())
	}

	override fun resize(width: Int, height: Int) {
		screenService.resize(width, height)
	}
	
	fun update(): Boolean {
		return screenService.update(Gdx.graphics.deltaTime)
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
		Assets.dispose()
		GameServices.dispose()
		KeyMappings.save()
		Settings.save()
	}
}

class AuroraAssetsResolver(val assetsRoot: String) : FileHandleResolver {

	override fun resolve(fileName: String): FileHandle {
		return Gdx.files.internal(assetsRoot + fileName)
	}
}