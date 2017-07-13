package se.exuvo.aurora.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.tools.texturepacker.TexturePacker
import se.exuvo.aurora.Assets
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.OutputStreamListener
import java.io.PrintStream

// See GlyphLayout::setText and BitmapFont$BitmapFontData::getGlyphs
fun getFontWidth(font: BitmapFont, text: String): Float {

	var width = 0f
	var prevGlyph: Glyph? = null

	for (char in text) {
		var glyph = font.data.getGlyph(char)

		if (glyph == null) {
			if (font.data.missingGlyph == null) continue;
			glyph = font.data.missingGlyph;
		}

		if (prevGlyph != null) {
			width += (prevGlyph.xadvance + prevGlyph.getKerning(char)) * font.data.scaleX
		}
		prevGlyph = glyph;
	}

	if (prevGlyph != null) {
		width += prevGlyph.xadvance * font.data.scaleX
	}

	return width
}

class LoadingScreen() : GameScreenImpl() {

	private val assetManager = GameServices[AssetManager::class.java]
	private val batch by lazy { GameServices[SpriteBatch::class.java] }
	private val uiCamera = OrthographicCamera()
	private var texturePackerTask = TexturePackerTask(assetManager)

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

		if (texturePackerTask.getState() == Thread.State.NEW) {
			texturePackerTask.start()

		} else if (texturePackerTask.exception != null) {
			throw texturePackerTask.exception!!

		} else if (texturePackerTask.done && assetManager.update()) {
			Assets.finishLoad()
			GameServices[GameScreenService::class.java].add(MainMenuScreen())
		}
	}

	override fun draw() {
		super.draw()

		batch.projectionMatrix = uiCamera.combined
		batch.begin()

		if (!texturePackerTask.done) {

			var text = "Packing textures.."
			Assets.fontUI.draw(batch, text, Gdx.graphics.width / 2f - getFontWidth(Assets.fontUI, text) / 2, Gdx.graphics.height / 2f + Assets.fontUI.lineHeight / 2)
			
			text = texturePackerTask.output.toString()
			Assets.fontUI.draw(batch, text, Gdx.graphics.width / 2f - getFontWidth(Assets.fontUI, text) / 2, Gdx.graphics.height / 2f + Assets.fontUI.lineHeight / 2 - Assets.fontUI.lineHeight)

		} else {

			val progress = String.format("%.0f%%", assetManager.progress * 100)
			val text = "Loading assets: $progress"
			Assets.fontUI.draw(batch, text, Gdx.graphics.width / 2f - getFontWidth(Assets.fontUI, text) / 2, Gdx.graphics.height / 2f + Assets.fontUI.lineHeight / 2)
		}

		batch.end()
	}
}

class TexturePackerTask(val assetManager: AssetManager) : Thread() {
	var exception: Exception? = null
	var done = false
	val output = OutputStreamListener()

	override fun run() {

		val realOut = System.out

		System.setOut(PrintStream(output, true))

		try {
			val imagesPath = assetManager.getFileHandleResolver().resolve("images").file().absolutePath

			// Packer supports .png .jpg .jpeg
			// https://github.com/libgdx/libgdx/wiki/Texture-packer
			TexturePacker.process(imagesPath, imagesPath, "aurora.atlas")
			done = true

		} catch(e: Exception) {
			exception = e

		} finally {
			System.out.flush()
			System.setOut(realOut)
		}
	}
}

