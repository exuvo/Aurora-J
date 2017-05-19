package se.exuvo.aurora

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer
import com.thedeadpixelsociety.ld34.screens.GameScreenService
import com.thedeadpixelsociety.ld34.screens.LoadingScreen
import org.apache.log4j.Logger
import se.exuvo.aurora.utils.GameServices
import se.exuvo.settings.Settings

class AuroraGame : ApplicationAdapter() {
	
	val log = Logger.getLogger(this.javaClass)
	val screenService = GameScreenService()

	override fun create() {
		
		val preferences = Gdx.app.getPreferences("Aurora J");
		
		GameServices.put(preferences, Preferences::class.java)
		GameServices.put(AssetManager())
		GameServices.put(ShapeRenderer())
		GameServices.put(SpriteBatch())
		GameServices.put(screenService)
		
		Gdx.input.setInputProcessor(screenService)

		screenService.push(LoadingScreen())
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
		GameServices.dispose()
		Assets.dispose();
		Settings.save()
	}
	
}