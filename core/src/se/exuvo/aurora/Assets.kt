package se.exuvo.aurora

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.SkinLoader
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGeneratorLoader
import com.badlogic.gdx.graphics.g2d.freetype.FreetypeFontLoader
import com.badlogic.gdx.graphics.g2d.freetype.FreetypeFontLoader.FreeTypeFontLoaderParameter
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.Disposable
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.utils.GameServices
import java.lang.RuntimeException
import kotlin.properties.Delegates

object Assets : Disposable {

	val log = LogManager.getLogger(this.javaClass)
	private val manager = GameServices[AssetManager::class]
	
	lateinit var fontMap: BitmapFont
	lateinit var fontMapSmall: BitmapFont
	lateinit var fontUI: BitmapFont
	lateinit var textures: TextureAtlas
	val shaders = HashMap<String, ShaderProgram>()

	fun earlyLoad() {
		val resolver = manager.getFileHandleResolver()
		manager.setLoader(FreeTypeFontGenerator::class.java, FreeTypeFontGeneratorLoader(resolver))
		manager.setLoader(BitmapFont::class.java, ".ttf", FreetypeFontLoader(resolver))
		manager.setLoader(BitmapFont::class.java, ".otf", FreetypeFontLoader(resolver))
		
		val fontUILoadParams = FreeTypeFontLoaderParameter().apply {
			fontFileName = "fonts/PrintClearly.otf"
			fontParameters.apply {
				size = 64
				color = Color.WHITE
				minFilter = TextureFilter.Linear
				magFilter = TextureFilter.Linear
			}
		}
		
		manager.load("fontUI.otf", BitmapFont::class.java, fontUILoadParams);
		manager.load("shaders/gamma.vert", ShaderProgram::class.java)
		
		manager.finishLoading()
		
		shaders["gamma"] = manager.get("shaders/gamma.vert")
		fontUI = manager.get("fontUI.otf", BitmapFont::class.java)
		fontUI.data.setScale(0.5f)
		fontUI.setFixedWidthGlyphs("0123456789") //TODO get font with fixed size numbers
	}
	
	fun startLoad() {
		
		val fontMapLoadParams = FreeTypeFontLoaderParameter().apply {
			fontFileName = "fonts/13PXBUS.TTF"
			fontParameters.apply {
				size = 13
				color = Color.WHITE
				minFilter = TextureFilter.Linear
				magFilter = TextureFilter.Linear
			}
		}
		
		val fontMapSmallLoadParams = FreeTypeFontLoaderParameter().apply {
			fontFileName = "fonts/11PX2BUS.TTF"
			fontParameters.apply {
				size = 11
				color = Color.WHITE
				minFilter = TextureFilter.Linear
				magFilter = TextureFilter.Linear
			}
		}

		manager.load("fontMap.ttf", BitmapFont::class.java, fontMapLoadParams);
		manager.load("fontMapSmall.ttf", BitmapFont::class.java, fontMapSmallLoadParams);
		
		manager.load("images/aurora.atlas", TextureAtlas::class.java);
		
		manager.getFileHandleResolver().resolve("shaders").list().forEach {file ->
			if (file.nameWithoutExtension() != "gamma") {
				manager.load("shaders/" + file.name(), ShaderProgram::class.java)
			}
		}
		
//		manager.load("shaders/circle.vert", ShaderProgram::class.java)
//		manager.load("shaders/disk.vert", ShaderProgram::class.java)
//		manager.load("shaders/gravimetric.vert", ShaderProgram::class.java)
//		manager.load("shaders/strategic.vert", ShaderProgram::class.java)
		
		log.info("Queued ${manager.queuedAssets} assets for loading")
	}
	
	fun finishLoad() {
		fontMap = manager.get("fontMap.ttf", BitmapFont::class.java)
		fontMapSmall = manager.get("fontMapSmall.ttf", BitmapFont::class.java)
		
		textures = manager.get("images/aurora.atlas")
		
		manager.getFileHandleResolver().resolve("shaders").list().forEach {file ->
			val name = file.nameWithoutExtension()
			if (name != "gamma") {
				shaders[name] = manager.get("shaders/$name.vert")
			}
		}
		
		if (shaders.size < 2) {
			throw RuntimeException("shaders not loaded")
		}
	}

	override fun dispose() {
		// Already disposed of by game services
		// manager.dispose()
	}
}
