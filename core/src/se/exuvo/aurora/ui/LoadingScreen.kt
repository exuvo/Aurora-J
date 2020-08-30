package se.exuvo.aurora.ui

import com.artemis.utils.Bag
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
import com.badlogic.gdx.utils.StreamUtils
import kotlinx.coroutines.Job
import ktx.assets.async.AssetStorage
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.Assets
import se.exuvo.aurora.galactic.Technology
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.OutputStreamListener
import java.io.PrintStream
import se.exuvo.aurora.AuroraGame
import se.exuvo.aurora.starsystems.systems.RenderSystem
import se.unlogic.standardutils.io.CloseUtils
import java.io.File
import java.io.FilenameFilter
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption

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
	companion object {
		@JvmField val log = LogManager.getLogger(LoadingScreen::class.java)
	}

	private val assetManager = GameServices[AssetStorage::class]
	private var uiCamera: OrthographicCamera = AuroraGame.currentWindow.screenService.uiCamera
	private var texturePackerTask = TexturePackerTask(assetManager)
	private var queuedLoadingJobs: Bag<Job>? = null

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
	}

	override fun update(deltaRealTime: Float) {
		if (Gdx.input.isKeyPressed(Input.Keys.ESCAPE)) {
			Gdx.app.exit()
		}

		if (texturePackerTask.getState() == Thread.State.NEW) {
			texturePackerTask.start()

		} else if (texturePackerTask.exception != null) {
			throw texturePackerTask.exception!!

		} else if (texturePackerTask.done) {
			val jobs = queuedLoadingJobs
			
			if (jobs == null) {
				queuedLoadingJobs = Assets.startLoad()
				Technology.initTech()
				
			} else {
				var allLoaded = true
				
				for (job in jobs) {
					if (!job.isCompleted) {
						allLoaded = false
						break
					}
				}
				
				if (allLoaded) {
					AuroraGame.currentWindow.screenService.add(MainMenuScreen())
				}
			}
		}
	}

	override fun draw() {
		val batch = AuroraGame.currentWindow.spriteBatch
		
		batch.projectionMatrix = uiCamera.combined
		batch.begin()

		if (!texturePackerTask.done) {

			var text = "Packing textures.. ${texturePackerTask.packingProgress}%"
			Assets.fontUI.draw(batch, text, Gdx.graphics.width / 2f - getFontWidth(Assets.fontUI, text) / 2, Gdx.graphics.height / 2f + Assets.fontUI.lineHeight / 2)

			text = texturePackerTask.output.toString()
			Assets.fontUI.draw(batch, text, Gdx.graphics.width / 2f - getFontWidth(Assets.fontUI, text) / 2, Gdx.graphics.height / 2f + Assets.fontUI.lineHeight / 2 - Assets.fontUI.lineHeight)

		} else {

			var text = "Loading assets: ${assetManager.progress.loaded} / ${assetManager.progress.total}"
			val failed = assetManager.progress.failed
			
			if (failed > 0) {
				text += ", failed $failed"
			}
			
			Assets.fontUI.draw(batch, text, Gdx.graphics.width / 2f - getFontWidth(Assets.fontUI, text) / 2, Gdx.graphics.height / 2f + Assets.fontUI.lineHeight / 2)
		}

		batch.end()
	}
}

class TexturePackerTask(val assetManager: AssetStorage) : Thread() {
	var exception: Exception? = null
	var done = false
	val output = OutputStreamListener()
	var packingProgress = 0f

	override fun run() {

		// if last modified on atlas is newer than files, skip generation

		val images = assetManager.fileResolver.resolve("images")
		val atlasName = "aurora.atlas"
		val existingAtlas = assetManager.fileResolver.resolve("images/$atlasName")

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
			val settings = TexturePacker.Settings()
			settings.limitMemory = false
			
			TexturePacker.process(settings, absolutePath, absolutePath, atlasName, object : TexturePacker.ProgressListener() {
				override fun progress(progress: Float) {
					packingProgress = progress
				}
			}, object : FilenameFilter {
				override fun accept(dir: File, name: String): Boolean {
					if (dir.endsWith("strategic") && "strategic.png" == name) {
						return false;
					}
					
					return true
				}
			})
			
			// Append strategic icons to atlas
			realOut.println("Appending strategic icons to atlas")
			val strategicIconsAtlas = assetManager.fileResolver.resolve("images/strategic/strategic.atlas")
			var inStream: InputStream? = null
			var outStream: OutputStream? = null
			try {
				inStream = strategicIconsAtlas.read()
				outStream = Files.newOutputStream(existingAtlas.file().toPath(), StandardOpenOption.APPEND)
				
				StreamUtils.copyStream(inStream, outStream)
			} finally {
				CloseUtils.close(inStream)
				CloseUtils.close(outStream)
			}
			
			done = true

		} catch(e: Exception) {
			exception = e
			LoadingScreen.log.error("Error generating atlas", e)

		} finally {
			System.out.flush()
			System.setOut(realOut)
		}
	}
}

