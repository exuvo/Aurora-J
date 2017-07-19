package se.exuvo.aurora.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import se.exuvo.aurora.Assets
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.planetarysystems.PlanetarySystem
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Vector2L

class MainMenuScreen() : GameScreenImpl() {

	private val assetManager by lazy { GameServices[AssetManager::class.java] }
	private val batch by lazy { GameServices[SpriteBatch::class.java] }
	private val uiCamera = OrthographicCamera()

	override fun show() {
	}

	override fun resize(width: Int, height: Int) {
		uiCamera.setToOrtho(false, width.toFloat(), height.toFloat())
	}

	override fun update(deltaRealTime: Float) {
		if (Gdx.input.isKeyPressed(Input.Keys.ESCAPE)) {
			Gdx.app.exit()
		}

//		if (Gdx.input.isKeyPressed(Input.Keys.ENTER)) {

		val system = PlanetarySystem("s1", Vector2L(0, 0))
		val system2 = PlanetarySystem("s2", Vector2L(4367, 0))
		val system3 = PlanetarySystem("s3", Vector2L(-2000, -5000))
		val galaxy = Galaxy(listOf(system, system2, system3), 0)
		galaxy.init()

		val systemView = PlanetarySystemScreen(system)
		GameServices.put(GalaxyScreen(systemView))
		GameServices[GameScreenService::class.java].add(UIScreen())
		GameServices[GameScreenService::class.java].add(systemView)
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
