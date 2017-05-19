package se.exuvo.aurora

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGeneratorLoader
import com.badlogic.gdx.graphics.g2d.freetype.FreetypeFontLoader
import com.badlogic.gdx.graphics.g2d.freetype.FreetypeFontLoader.FreeTypeFontLoaderParameter
import com.badlogic.gdx.utils.Disposable
import org.apache.log4j.Logger
import se.exuvo.aurora.utils.GameServices
import kotlin.properties.Delegates

object Assets : Disposable {

	val log = Logger.getLogger(this.javaClass)
	private val manager by lazy { GameServices[AssetManager::class.java] }

	var fontMap by Delegates.notNull<BitmapFont>()
	var fontUI by Delegates.notNull<BitmapFont>()

	fun startLoad() {
		val resolver = InternalFileHandleResolver()
		manager.setLoader(FreeTypeFontGenerator::class.java, FreeTypeFontGeneratorLoader(resolver))
		manager.setLoader(BitmapFont::class.java, ".ttf", FreetypeFontLoader(resolver))
		manager.setLoader(BitmapFont::class.java, ".otf", FreetypeFontLoader(resolver))

		val font32LoadParams = FreeTypeFontLoaderParameter().apply {
			fontFileName = "PrintClearly.otf"
			fontParameters.apply {
				size = 64
				color = Color.WHITE
				minFilter = TextureFilter.Linear
				magFilter = TextureFilter.Linear
			}
		}

		manager.load("fontUI.otf", BitmapFont::class.java, font32LoadParams);
		
		// Load font now as it is used on the loading screen
		manager.finishLoading()
		fontUI = manager.get("fontUI.otf", BitmapFont::class.java)
		fontUI.data.setScale(0.5f)
		
		val font16LoadParams = FreeTypeFontLoaderParameter().apply {
			fontFileName = "PrintClearly.otf"
			fontParameters.apply {
				size = 64
				color = Color.WHITE
				minFilter = TextureFilter.Linear
				magFilter = TextureFilter.Linear
			}
		}

		manager.load("fontMap.otf", BitmapFont::class.java, font16LoadParams);
		
		log.info("Queued ${manager.queuedAssets} assets for loading")
	}
	
	fun finishLoad() {
		fontMap = manager.get("fontMap.otf", BitmapFont::class.java)
		fontMap.data.setScale(0.3f)
	}

	override fun dispose() {
		manager.dispose()
	}
}