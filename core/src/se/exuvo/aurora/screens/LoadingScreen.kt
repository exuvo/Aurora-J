package se.exuvo.aurora.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import se.exuvo.aurora.Assets
import se.exuvo.aurora.SolarSystem
import se.exuvo.aurora.utils.GameServices

class LoadingScreen() : GameScreenImpl() {

	private val assetManager by lazy { GameServices[AssetManager::class.java] }
	private val batch by lazy { GameServices[SpriteBatch::class.java] }
	private val uiCamera = OrthographicCamera()

	override fun show() {
		Assets.startLoad()
	}

	override fun resize(width: Int, height: Int) {
		uiCamera.setToOrtho(false, width.toFloat(), height.toFloat())
	}

	override fun update(deltaRealTime: Float) {
		if (Gdx.input.isKeyPressed(Input.Keys.ESCAPE)) {
			Gdx.app.exit()
    }
		
		if (assetManager.update()) {
			Assets.finishLoad()
			GameServices[GameScreenService::class.java].add(MainMenuScreen())
		}
	}

	override fun draw() {
		super.draw()

		val progress = String.format("%.0f%%", assetManager.progress * 100) 
		
		batch.projectionMatrix = uiCamera.combined
		batch.begin()
		Assets.fontUI.draw(batch, "Loading: $progress", 8f, 32f)
		batch.end()
	}
}
