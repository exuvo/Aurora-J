package se.exuvo.aurora.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.tools.texturepacker.TexturePacker
import se.exuvo.aurora.Assets
import se.exuvo.aurora.utils.GameServices

class LoadingScreen() : GameScreenImpl() {

	private val assetManager by lazy { GameServices[AssetManager::class.java] }
	private val batch by lazy { GameServices[SpriteBatch::class.java] }
	private val uiCamera = OrthographicCamera()
	private var texturesPacked = false

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

		if (!texturesPacked) {

			draw()
			val imagesPath = assetManager.getFileHandleResolver().resolve("images").file().absolutePath

			// Packer supports .png .jpg .jpeg
			// https://github.com/libgdx/libgdx/wiki/Texture-packer
			TexturePacker.process(imagesPath, imagesPath, "aurora.atlas")
			
			texturesPacked = true
		}

		if (assetManager.update()) {
			Assets.finishLoad()
			GameServices[GameScreenService::class.java].add(MainMenuScreen())
		}
	}

	override fun draw() {
		super.draw()

		batch.projectionMatrix = uiCamera.combined
		batch.begin()

		if (!texturesPacked) {

			Assets.fontUI.draw(batch, "Packing textures..", Gdx.graphics.width / 2f - 8 * Assets.fontUI.spaceWidth, Gdx.graphics.height / 2f + Assets.fontUI.lineHeight / 2)

		} else {

			val progress = String.format("%.0f%%", assetManager.progress * 100)
			Assets.fontUI.draw(batch, "Loading: $progress", Gdx.graphics.width / 2f - 8 * Assets.fontUI.spaceWidth, Gdx.graphics.height / 2f + Assets.fontUI.lineHeight / 2)
		}

		batch.end()
	}
}
