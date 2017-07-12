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
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.Disposable
import org.apache.log4j.Logger
import se.exuvo.aurora.utils.GameServices
import kotlin.properties.Delegates

object Assets : Disposable {

	val log = Logger.getLogger(this.javaClass)
	private val manager by lazy { GameServices[AssetManager::class.java] }

	var fontMap by Delegates.notNull<BitmapFont>()
	var fontMapSmall by Delegates.notNull<BitmapFont>()
	var fontUI by Delegates.notNull<BitmapFont>()
	var skinUI by Delegates.notNull<Skin>()
	var textures by Delegates.notNull<TextureAtlas>()

	fun startLoad() {
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
		
		// Load font now as it is used on the loading screen
		manager.finishLoading()
		fontUI = manager.get("fontUI.otf", BitmapFont::class.java)
		fontUI.data.setScale(0.5f)
		
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
		
		val uiSkinLoaderParams = SkinLoader.SkinParameter("ui/uiskin.atlas")
		manager.load("ui/uiskin.json", Skin::class.java, uiSkinLoaderParams);
		
		manager.load("images/aurora.atlas", TextureAtlas::class.java);
		
		log.info("Queued ${manager.queuedAssets} assets for loading")
	}
	
	fun finishLoad() {
		fontMap = manager.get("fontMap.ttf", BitmapFont::class.java)
		fontMapSmall = manager.get("fontMapSmall.ttf", BitmapFont::class.java)
		skinUI = manager.get("ui/uiskin.json", Skin::class.java)
		textures = manager.get("images/aurora.atlas")
	}

	override fun dispose() {
		manager.dispose()
	}
}
