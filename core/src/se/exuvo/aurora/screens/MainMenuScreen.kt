package se.exuvo.aurora.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import se.exuvo.aurora.Assets
import se.exuvo.aurora.Galaxy
import se.exuvo.aurora.SolarSystem
import se.exuvo.aurora.utils.GameServices
import java.util.Collections

class MainMenuScreen() : GameScreenImpl() {

	private val assetManager by lazy { GameServices[AssetManager::class.java] }
	private val batch by lazy { GameServices[SpriteBatch::class.java] }
	private val uiCamera = OrthographicCamera()

	override fun show() {
	}

	override fun resize(width: Int, height: Int) {
		uiCamera.setToOrtho(false, width.toFloat(), height.toFloat())
	}

	override fun update(delta: Float) {
		if (Gdx.input.isKeyPressed(Input.Keys.ESCAPE)) {
			Gdx.app.exit()
    }
		
//		if (Gdx.input.isKeyPressed(Input.Keys.ENTER)) {
			
			val system = SolarSystem()
			val galaxy = Galaxy(Collections.singletonList(system), 0) //Int.MAX_VALUE.toLong()
			galaxy.init()
		
			GameServices[GameScreenService::class.java].push(SolarSystemScreen(system))
//    }
	}

	override fun draw() {
		super.draw()

		batch.projectionMatrix = uiCamera.combined
		batch.begin()
		Assets.fontUI.draw(batch, "Main Menu", 8f, 32f)
		batch.end()
	}
}
