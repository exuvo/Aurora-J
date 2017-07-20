package se.exuvo.aurora

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.esotericsoftware.kryonet.MinlogTolog4j
import org.apache.log4j.Logger
import se.exuvo.aurora.planetarysystems.systems.GroupSystem
import se.exuvo.aurora.screens.GameScreenService
import se.exuvo.aurora.screens.LoadingScreen
import se.exuvo.aurora.utils.GameServices
import se.exuvo.settings.Settings
import java.util.concurrent.locks.ReentrantReadWriteLock

class AuroraGame(val assetsRoot: String) : ApplicationAdapter() {

	val log = Logger.getLogger(this.javaClass)
	val screenService = GameScreenService()

	override fun create() {
		com.esotericsoftware.minlog.Log.setLogger(MinlogTolog4j())
		com.esotericsoftware.minlog.Log.set(com.esotericsoftware.minlog.Log.LEVEL_WARN)

		GameServices.put(AssetManager(AuroraAssetsResolver(assetsRoot)))
		GameServices.put(ShapeRenderer())
		GameServices.put(SpriteBatch())
		GameServices.put(GroupSystem(ReentrantReadWriteLock()))
		GameServices.put(screenService)

		Gdx.input.setInputProcessor(screenService)

		screenService.add(LoadingScreen())
	}

	override fun resize(width: Int, height: Int) {
		screenService.resize(width, height)
	}

	override fun render() {
		screenService.render(Gdx.graphics.deltaTime)
	}

	override fun pause() {
		screenService.pause()
	}

	override fun resume() {
		screenService.resume()
	}

	override fun dispose() {
		screenService.dispose()
		Assets.dispose()
		GameServices.dispose()
		Settings.save()
	}

}

class AuroraAssetsResolver(val assetsRoot: String) : FileHandleResolver {

	override fun resolve(fileName: String): FileHandle {
		return Gdx.files.internal(assetsRoot + fileName)
	}
}