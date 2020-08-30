package se.exuvo.aurora

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Files.FileType
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application.GLDebugMessageSeverity
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3FileHandle
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.profiling.GLProfiler
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.GdxRuntimeException
import com.esotericsoftware.kryonet.MinlogTolog4j
import ktx.assets.async.AssetStorage
import ktx.async.KtxAsync
import ktx.async.newAsyncContext
import org.apache.logging.log4j.LogManager
import org.lwjgl.glfw.GLFW
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.history.History
import se.exuvo.aurora.starsystems.StarSystem
import se.exuvo.aurora.starsystems.systems.GroupSystem
import se.exuvo.aurora.ui.GameScreenService
import se.exuvo.aurora.ui.LoadingScreen
import se.exuvo.aurora.ui.StarSystemScreen
import se.exuvo.aurora.ui.UIScreen
import se.exuvo.aurora.ui.keys.KeyMappings
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Storage
import se.exuvo.settings.Settings
import java.io.File
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantReadWriteLock

interface AuroraGame : ApplicationListener {
	fun update()
	var screenService: GameScreenService
	var shapeRenderer: ShapeRenderer
	var spriteBatch: SpriteBatch
	val storage: Storage
	
	companion object {
		lateinit var currentWindow: AuroraGame
		val storage = Storage()
	}
}

interface Resizable {
	fun resize(width: Int, height: Int)
}

class AuroraGameMainWindow() : AuroraGame {
	val log = LogManager.getLogger(this.javaClass)
	override lateinit var screenService: GameScreenService
	override lateinit var shapeRenderer: ShapeRenderer
	override lateinit var spriteBatch: SpriteBatch
	override val storage = Storage()

	override fun create() {
		com.esotericsoftware.minlog.Log.setLogger(MinlogTolog4j())
		com.esotericsoftware.minlog.Log.set(com.esotericsoftware.minlog.Log.LEVEL_WARN)
		
		Lwjgl3Application.setGLDebugMessageControl(GLDebugMessageSeverity.HIGH, true)
		Lwjgl3Application.setGLDebugMessageControl(GLDebugMessageSeverity.MEDIUM, true)
		Lwjgl3Application.setGLDebugMessageControl(GLDebugMessageSeverity.LOW, true)
		Lwjgl3Application.setGLDebugMessageControl(GLDebugMessageSeverity.NOTIFICATION, true)

		KeyMappings.load()

		KtxAsync.initiate()
		
		GameServices + GLProfiler(Gdx.graphics)
		GameServices + AssetStorage(newAsyncContext(4, "AssetStorage-Thread"), AuroraAssetsResolver())
		GameServices + GroupSystem(ReentrantReadWriteLock())
		GameServices + History()
		
		Assets.earlyLoad()

		screenService = GameScreenService()
		shapeRenderer = ShapeRenderer()
		spriteBatch = SpriteBatch()
		
		Gdx.input.setInputProcessor(screenService)

		screenService.add(LoadingScreen())
	}

	override fun resize(width: Int, height: Int) {
		screenService.resize(width, height)
		
		storage.forEach {
			if (it is Resizable) {
				it.resize(width, height)
			}
		}
	}

	override fun update() {
		screenService.update(Gdx.graphics.deltaTime)
	}

	override fun render() {
		screenService.render()
	}

	override fun pause() {
		screenService.pause()
	}

	override fun resume() {
		screenService.resume()
	}

	override fun dispose() {
		//TODO close all other windows first
		
		(GameServices[AssetStorage::class].fileResolver as AuroraAssetsResolver).dispose()
		GameServices(Galaxy::class)?.dispose()
		storage.dispose()
		Assets.dispose()
		GameServices.dispose()
		KeyMappings.save()
		Settings.save()
	}
}

class AuroraGameSecondaryWindow(val system: StarSystem) : AuroraGame {
	val log = LogManager.getLogger(this.javaClass)
	override lateinit var screenService: GameScreenService
	override lateinit var shapeRenderer: ShapeRenderer
	override lateinit var spriteBatch: SpriteBatch
	override val storage = Storage()

	override fun create() {
		
		screenService = GameScreenService()
		shapeRenderer = ShapeRenderer()
		spriteBatch = SpriteBatch()
		
		Gdx.input.setInputProcessor(screenService)

		screenService.add(UIScreen())
		screenService.add(StarSystemScreen(system))
	}

	override fun resize(width: Int, height: Int) {
		screenService.resize(width, height)
		
		storage.forEach {
			if (it is Resizable) {
				it.resize(width, height)
			}
		}
	}

	override fun update() {
		screenService.update(Gdx.graphics.deltaTime)
	}

	override fun render() {
		screenService.render()
	}

	override fun pause() {
		screenService.pause()
	}

	override fun resume() {
		screenService.resume()
	}

	override fun dispose() {
		storage.dispose()
		screenService.dispose()
	}
}

class AuroraAssetsResolver() : FileHandleResolver, Disposable {
	companion object {
		@JvmField var runningFromJAR = false
		@JvmField var jarFileSystem: FileSystem? = null
	}
	
	init {
		val uri: URI = AuroraAssetsResolver::class.java.getResource("AuroraAssetsResolver.class").toURI()
		
		if (uri.getScheme() == "jar") {
			runningFromJAR = true
			jarFileSystem = FileSystems.newFileSystem(uri, emptyMap<String, Object>());
			
		} else {
			runningFromJAR = false
		}
	}

	override fun resolve(fileName: String): FileHandle {
		return AuroraFileHandle("assets/$fileName")
	}
	
	override fun dispose() {
		jarFileSystem?.close()
	}
	
	class AuroraFileHandle : FileHandle {
		constructor(fileName: String) : super(fileName, FileType.Internal)
		constructor(file: File, type: FileType) : super(file, type)
		
		override fun list(): Array<FileHandle> {
			if (type == FileType.Classpath) {
				throw GdxRuntimeException("Cannot list a classpath directory: $file")
			}
			
			val handles = LinkedHashMap<String, FileHandle>()
			
			if (runningFromJAR) {
				val rootPath = jarFileSystem!!.getPath(file.path)
				
				if (Files.exists(rootPath)) {
					Files.walk(rootPath, 1).use { stream ->
						val it = stream.iterator()
						it.next() // skip self
						
						for (path: Path in it) {
							val relativePath = rootPath.relativize(path)
							handles[relativePath.toString()] = child(relativePath.toString())
						}
					}
				}
			}
			
			val relativePaths: Array<String>? = file().list()
			
			relativePaths?.forEach { path ->
				handles[path] = child(path)
			}
			
			if (handles.isEmpty()) {
				return emptyArray()
			}
			
			return handles.values.toTypedArray()
		}
		
		override fun child(name: String): FileHandle {
			if (file.path.isEmpty()) {
				return AuroraFileHandle(File(name), type)
			} else  {
				return AuroraFileHandle(File(file, name), type)
			}
		}
		
		override fun sibling(name: String): FileHandle {
			if (file.path.isEmpty()) throw GdxRuntimeException("Cannot get the sibling of the root.")
			return AuroraFileHandle(File(file.parent, name), type)
		}
		
		override fun parent(): FileHandle? {
			var parent = file.parentFile
			if (parent == null) {
				if (type == FileType.Absolute) {
					parent = File("/")
				} else {
					parent = File("")
				}
			}
			return AuroraFileHandle(parent, type)
		}
		
		override fun file(): File {
			if (type == FileType.External) {
				return File(Lwjgl3Files.externalPath, file.path)
			}
			
			if (type == FileType.Local)  {
				return File(Lwjgl3Files.localPath, file.path)
			}
			
			return file
		}
	}
}