package se.exuvo.aurora

import com.artemis.utils.Bag
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ktx.assets.async.AssetStorage
import ktx.async.KtxAsync
import ktx.freetype.async.loadFreeTypeFont
import ktx.freetype.async.registerFreeTypeFontLoaders
import ktx.freetype.freeTypeFontParameters
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.utils.GameServices
import java.lang.RuntimeException
import kotlin.properties.Delegates

object Assets : Disposable {

	val log = LogManager.getLogger(this.javaClass)
	private val manager = GameServices[AssetStorage::class]
	
	lateinit var fontMap: BitmapFont
	lateinit var fontMapSmall: BitmapFont
	lateinit var fontUI: BitmapFont
	lateinit var textures: TextureAtlas
	val shaders = HashMap<String, ShaderProgram>()
	
	fun earlyLoad() {
		manager.registerFreeTypeFontLoaders()
		
		manager.apply {
			fontUI = loadSync<BitmapFont>(getAssetDescriptor("fonts/PrintClearly.otf", freeTypeFontParameters("fonts/PrintClearly.otf") {
				size = 64
				color = Color.WHITE
				minFilter = TextureFilter.Linear
				magFilter = TextureFilter.Linear
			}))
			fontUI.data.setScale(0.5f)
			fontUI.setFixedWidthGlyphs("0123456789") //TODO get font with fixed size numbers
			
			shaders["gamma"] = loadSync<ShaderProgram>("shaders/gamma.vert")
		}
	}
	
	fun startLoad(): Bag<Job> {
		val jobs = Bag<Job>(8)
		
		jobs.add(KtxAsync.launch {
			manager.apply {
				
				fontMap = loadFreeTypeFont("fonts/13PXBUS.ttf"){
					size = 13
					color = Color.WHITE
					minFilter = TextureFilter.Linear
					magFilter = TextureFilter.Linear
				}
				
				fontMapSmall = loadFreeTypeFont("fonts/11PX2BUS.ttf"){
					size = 11
					color = Color.WHITE
					minFilter = TextureFilter.Linear
					magFilter = TextureFilter.Linear
				}
				
				textures = load<TextureAtlas>("images/aurora.atlas")
			}
		})
		
		jobs.add(KtxAsync.launch {
			manager.apply {
				
				fileResolver.resolve("shaders").list().forEach {file ->
					val name = file.nameWithoutExtension()
					if (name != "gamma" && file.extension() == "frag") {
						shaders[name] = load<ShaderProgram>("shaders/" + file.name())
					}
				}
				
				if (shaders.size < 2) {
					throw RuntimeException("shaders not loaded")
				}
			}
		})
		
		return jobs
	}
	
	override fun dispose() {
		// Already disposed of by game services
		// manager.dispose()
	}
}
