package se.exuvo.aurora.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.profiling.GLErrorListener
import com.badlogic.gdx.graphics.profiling.GLProfiler
import com.badlogic.gdx.tools.texturepacker.TexturePacker
import se.exuvo.aurora.Assets
import se.exuvo.aurora.galactic.Technology
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

	private val assetManager = GameServices[AssetManager::class]
	private val batch by lazy { GameServices[SpriteBatch::class] }
	private val uiCamera = OrthographicCamera()
	private var texturePackerTask = TexturePackerTask(assetManager)

	override fun show() {
		val profiler = GameServices[GLProfiler::class]
		profiler.enable()

		/* TODO fix throws GL_INVALID_ENUM
 
 			https://www.khronos.org/opengl/wiki/Common_Mistakes#Checking_for_OpenGL_Errors
			The magnification filter can't specify the use of mipmaps; only the minification filter can do that.
 
		 	at com.badlogic.gdx.graphics.GLTexture.setFilter(GLTexture.java:164)
			at com.badlogic.gdx.assets.loaders.TextureLoader.loadSync(TextureLoader.java:87)
			at com.badlogic.gdx.assets.loaders.TextureLoader.loadSync(TextureLoader.java:41)
			at com.badlogic.gdx.assets.AssetLoadingTask.handleAsyncLoader(AssetLoadingTask.java:125)
			at com.badlogic.gdx.assets.AssetLoadingTask.update(AssetLoadingTask.java:90)
			at com.badlogic.gdx.assets.AssetManager.updateTask(AssetManager.java:507)
			at com.badlogic.gdx.assets.AssetManager.update(AssetManager.java:381)
		 */
		
//		profiler.setListener(GLErrorListener.THROWING_LISTENER)

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
			Technology.initTech()
			GameServices[GameScreenService::class].add(MainMenuScreen())
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

		// if last modified on atlas is newer than files, skip generation

		val images = assetManager.getFileHandleResolver().resolve("images")
		val atlasName = "aurora.atlas"
		val existingAtlas = assetManager.getFileHandleResolver().resolve("images/$atlasName")

		if (existingAtlas.exists()) {

			val atlasLastModified = existingAtlas.lastModified()
			var foundNewerFile = false

			outer@ for (directory: FileHandle in images.list()) {

				if (directory.isDirectory) {
					for (file in directory.list()) {

						if (file.lastModified() > atlasLastModified) {
							foundNewerFile = true
							break@outer
						}
					}
				}
			}

			if (foundNewerFile) {

				println("Found existing old atlas, regenerating")

			} else {

				done = true
				println("Found existing up to date atlas, skipping generation")
				return
			}

		} else {

			println("Generating new atlas")
		}

		val realOut = System.out

		System.setOut(PrintStream(output, true))

		try {
			val absolutePath = images.file().absolutePath

			// Packer supports .png .jpg .jpeg
			// https://github.com/libgdx/libgdx/wiki/Texture-packer
			TexturePacker.process(absolutePath, absolutePath, atlasName)
			done = true

		} catch(e: Exception) {
			exception = e

		} finally {
			System.out.flush()
			System.setOut(realOut)
		}
	}
}

