package se.exuvo.aurora.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Disposable
import se.exuvo.aurora.Assets
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Units
import se.exuvo.settings.Settings
import se.unlogic.standardutils.threads.ThreadUtils
import java.util.LinkedList
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import se.exuvo.aurora.utils.exponentialAverage
import se.exuvo.aurora.AuroraGame
import kotlin.reflect.KClass
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import org.apache.logging.log4j.LogManager
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL30
import se.exuvo.aurora.AuroraGameMainWindow
import se.exuvo.aurora.Resizable
import se.exuvo.aurora.starsystems.systems.RenderSystem
import java.nio.IntBuffer

class GameScreenService : Disposable, InputProcessor {
	companion object {
		val log = LogManager.getLogger(GameScreenService::class.java)
		
		const val MAX_VERTICES = 16
		const val MAX_INDICES = 6
		
		var gamma = Settings.getFloat("Systems/Render/gamma", 2.2f)
	}
	
	private val inputMultiplexer = InputMultiplexer()
	val uiCamera = OrthographicCamera()
	private var fbo: FrameBuffer = createFBO()
	private val screens = LinkedList<GameScreen>()
	private val addQueue = LinkedList<GameScreen>()
	
	init {
		var globalData = AuroraGame.storage(GameScreenServiceGlobalData::class)
		
		if (globalData == null) {
			globalData = GameScreenServiceGlobalData()
			AuroraGame.storage + globalData
		}
	}
	
	private fun createFBO() = FrameBuffer(Pixmap.Format.RGBA16, Gdx.graphics.getWidth(),Gdx.graphics.getHeight(), false)
	
	fun gData() = AuroraGame.storage[GameScreenServiceGlobalData::class]
	
	class GameScreenServiceGlobalData(): Disposable {
		val gammaShader: ShaderProgram = Assets.shaders["gamma"]!!
		val vertices: FloatArray
		val indices: ShortArray
		val mesh: Mesh
		
		init {
			if (!gammaShader.isCompiled || gammaShader.getLog().isNotEmpty()) {
				throw RuntimeException("Shader gammaShader compile error ${gammaShader.getLog()}")
			}
			
			vertices = FloatArray(MAX_VERTICES);
			indices = ShortArray(MAX_INDICES)
			
			mesh = Mesh(false, MAX_VERTICES, MAX_INDICES,
					VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE),
					VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE)
			);
		}
		
		override fun dispose() {
			mesh.dispose()
		}
	}
	
	fun <T : GameScreen> add(screen: T) {
		addQueue.add(screen)
	}
	
	@Suppress("UNCHECKED_CAST")
	operator fun <T : GameScreen> get(screenClass: KClass<T>): T {
		
		screens.forEach { screen ->
			if (screenClass.isInstance(screen)) {
				return screen as T
			}
		}
		
		throw NullPointerException()
	}
	
	@Suppress("UNCHECKED_CAST")
	operator fun <T : GameScreen> invoke(screenClass: KClass<T>): T? {
		
		screens.forEach { screen ->
			if (screenClass.isInstance(screen)) {
				return screen as T
			}
		}
		
		return null
	}

	var lastDrawStart = System.nanoTime()
	var frameStartTime = 0L
	
	fun update(deltaRealTime: Float) {

		if (addQueue.isNotEmpty()) {

			for (screen in addQueue) {
				screens.addFirst(screen)

				if (screen is InputProcessor) {
					inputMultiplexer.addProcessor(screen)
				}
			}
			
			for (screen in addQueue) {
				screen.show()
				screen.resize(Gdx.graphics.width, Gdx.graphics.height)
			}

			addQueue.clear()
		}

		val it = screens.iterator()
		var firstRealScreenFound = false

		while (it.hasNext()) {
			val screen = it.next()

			if (!screen.overlay) {
				if (firstRealScreenFound) {

					it.remove()
					screen.hide()
					screen.dispose()

					if (screen is InputProcessor) {
						inputMultiplexer.removeProcessor(screen)
					}

				} else {
					firstRealScreenFound = true
				}
			}
		}

		screens.forEach { it.update(deltaRealTime) }
	}
	
	private val clearColor = Color.BLACK
	var renderTimeAverage = 0.0
	var frameTimeAverage = 0.0
	
	// gamma correction https://github.com/ocornut/imgui/issues/578#issuecomment-379467586
	fun render() {
		val now = System.nanoTime()
		
		frameStartTime = now - lastDrawStart
		lastDrawStart = now
		
		frameTimeAverage = exponentialAverage(frameStartTime.toDouble(), frameTimeAverage, 15.0)
		
		val gData = gData()
		val vertices = gData.vertices
		val indices = gData.indices
		val mesh = gData.mesh
		val fbo = fbo
		val gammaShader = gData.gammaShader
		
		fbo.begin()
		Gdx.gl.glClearColor(clearColor.r, clearColor.g, clearColor.b, clearColor.a)
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

		screens.forEach { it.draw() }
		
		fbo.end()
		
		Gdx.gl.glDisable(GL20.GL_BLEND);
		Gdx.gl.glDisable(GL30.GL_FRAMEBUFFER_SRGB)
		
		gammaShader.bind()
		gammaShader.setUniformMatrix("u_projTrans", uiCamera.combined);
		gammaShader.setUniformi("u_texture", 15);
		gammaShader.setUniformf("u_gamma", gamma);

		val fboTex = fbo.getColorBufferTexture()
		Gdx.gl.glActiveTexture(GL20.GL_TEXTURE15)
		fboTex.bind()
		Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)

		var vertexIdx = 0
		var indiceIdx = 0

		fun vertex(x: Float, y: Float, u: Float, v: Float) {
			vertices[vertexIdx++] = x;
			vertices[vertexIdx++] = y;
			vertices[vertexIdx++] = u;
			vertices[vertexIdx++] = v;
		}

		// Triangle 1
		indices[indiceIdx++] = 1.toShort()
		indices[indiceIdx++] = 0.toShort()
		indices[indiceIdx++] = 2.toShort()

		// Triangle 2
		indices[indiceIdx++] = 0.toShort()
		indices[indiceIdx++] = 3.toShort()
		indices[indiceIdx++] = 2.toShort()

		val maxX = Gdx.graphics.width.toFloat()
		val maxY = Gdx.graphics.height.toFloat()

		vertex(0f, 0f, 0f, 0f);
		vertex(maxX, 0f, 1f, 0f);
		vertex(maxX, maxY, 1f, 1f);
		vertex(0f, maxY, 0f, 1f);

		mesh.setVertices(vertices, 0, vertexIdx)
		mesh.setIndices(indices, 0, indiceIdx)
		mesh.render(gammaShader, GL20.GL_TRIANGLES)
		
		val renderTime = System.nanoTime() - now
		renderTimeAverage = exponentialAverage(renderTime.toDouble(), renderTimeAverage, 10.0)
		
		val spriteBatch = AuroraGame.currentWindow.spriteBatch
		
		spriteBatch.projectionMatrix = uiCamera.combined
		spriteBatch.begin()

		Assets.fontUI.draw(spriteBatch, "${Gdx.graphics.framesPerSecond} ${formatFrameTime(frameStartTime)} ${formatFrameTime(frameTimeAverage.toLong())}, ${formatFrameTime(renderTimeAverage.toLong())}", 2f, uiCamera.viewportHeight - 3f)
		spriteBatch.end()
	}
	
	private fun formatFrameTime(nanotime: Long): String {
		val centinanos = ((nanotime / 10000) % 100).toInt()
		val milli = (nanotime / Units.NANO_MILLI).toInt()
		
		return String.format("%02d.%02dms", milli, centinanos)
	}

	fun pause() {
		screens.forEach { it.pause() }
	}

	fun resize(width: Int, height: Int) {
		fbo.dispose()
		fbo = createFBO()
		
		uiCamera.setToOrtho(false, width.toFloat(), height.toFloat())
		
		screens.forEach { it.resize(width, height) }
	}

	fun resume() {
		screens.forEach { it.resume() }
	}

	override fun dispose() {
		screens.forEach { it.dispose() }
		fbo.dispose()
	}

	override fun keyDown(keycode: Int): Boolean {
//		println("keyDown $keycode ${Input.Keys.toString(keycode)}")

		return inputMultiplexer.keyDown(keycode)
	}

	override fun keyUp(keycode: Int): Boolean {
		return inputMultiplexer.keyUp(keycode)
	}

	override fun keyTyped(character: Char): Boolean {
//		println("keyTyped $character")

		return inputMultiplexer.keyTyped(character)
	}

	override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
		return inputMultiplexer.touchDown(screenX, screenY, pointer, button)
	}

	override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
		return inputMultiplexer.touchUp(screenX, screenY, pointer, button)
	}

	override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
		return inputMultiplexer.touchDragged(screenX, screenY, pointer)
	}

	override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
		return inputMultiplexer.mouseMoved(screenX, screenY)
	}

	override fun scrolled(amount: Int): Boolean {
		return inputMultiplexer.scrolled(amount)
	}

}

interface GameScreen : Screen {
    val overlay: Boolean

    fun update(deltaRealTime: Float)
    fun draw()
}

abstract class GameScreenImpl : GameScreen {
    override val overlay = false

    override fun show() {
    }

    override fun hide() {
    }

    override fun resize(width: Int, height: Int) {
    }

    override fun update(deltaRealTime: Float) {
    }

    override fun draw() {
    }

    override fun pause() {
    }

    override fun resume() {
    }

    override fun dispose() {
    }

    override fun render(deltaRealTime: Float) {
        throw UnsupportedOperationException()
    }
}