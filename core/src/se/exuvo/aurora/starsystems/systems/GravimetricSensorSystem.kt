package se.exuvo.aurora.starsystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.EntitySubscription
import com.artemis.EntitySubscription.SubscriptionListener
import com.artemis.annotations.Wire
import com.artemis.utils.IntBag
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.profiling.GLErrorListener
import com.badlogic.gdx.utils.viewport.Viewport
import org.apache.logging.log4j.LogManager
import org.lwjgl.opengl.GL11
import se.exuvo.aurora.Assets
import se.exuvo.aurora.galactic.PassiveSensor
import se.exuvo.aurora.starsystems.StarSystem
import se.exuvo.aurora.starsystems.components.DetectionComponent
import se.exuvo.aurora.starsystems.components.EmissionsComponent
import se.exuvo.aurora.starsystems.components.GravimetricSensorsComponent
import se.exuvo.aurora.starsystems.components.MassComponent
import se.exuvo.aurora.starsystems.components.OwnerComponent
import se.exuvo.aurora.starsystems.components.PassiveSensorsComponent
import se.exuvo.aurora.starsystems.components.PoweredPartState
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.components.TimedMovementComponent
import se.exuvo.aurora.starsystems.components.UUIDComponent
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.utils.exponentialAverage
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.utils.lerpColors
import java.nio.FloatBuffer
import se.exuvo.aurora.AuroraGame
import com.badlogic.gdx.utils.Disposable
import se.exuvo.aurora.AuroraGameMainWindow

//TODO render as displacement of elastic plane weighted down by mass
@Suppress("DEPRECATION")
class GravimetricSensorSystem : GalaxyTimeIntervalSystem((H_SQUARE_SIZE_KM / Units.C).toLong()) { // 
	companion object {
		val SENSOR_ASPECT = Aspect.all(GravimetricSensorsComponent::class.java)
		val EMISSION_ASPECT = Aspect.all(MassComponent::class.java, TimedMovementComponent::class.java)
		val SHIP_ASPECT = Aspect.all(ShipComponent::class.java)

		const val MAX_AU = 10;
		const val SQUARES_PER_AU = 20
		const val H_SQUARE_SIZE_KM = Units.AU / SQUARES_PER_AU
		const val H_SQUARE_SIZE_KMf = H_SQUARE_SIZE_KM.toFloat()
		const val WATER_SIZE = MAX_AU * SQUARES_PER_AU

		const val WAVE_ATTENUATION =
			0.999f //TODO lower if high SQUARES_PER_AU (Math.pow(0.999, SQUARES_PER_AU.toDouble()).toFloat())
		const val C_WAVE_SPEED = Units.C.toFloat()
		const val MAX_INTERVAL = 5 //(H_SQUARE_SIZE_KM / C_WAVE_SPEED).toInt()
		
		const val MAX_VERTICES = 500 * 4 * 3
		const val MAX_INDICES = (MAX_VERTICES / 4) * 6
	}

	val log = LogManager.getLogger(this.javaClass)

	lateinit private var movementMapper: ComponentMapper<TimedMovementComponent>
	lateinit private var sensorsMapper: ComponentMapper<PassiveSensorsComponent>
	lateinit private var emissionsMapper: ComponentMapper<EmissionsComponent>
	lateinit private var detectionMapper: ComponentMapper<DetectionComponent>
	lateinit private var ownerMapper: ComponentMapper<OwnerComponent>
	lateinit private var shipMapper: ComponentMapper<ShipComponent>
	lateinit private var uuidMapper: ComponentMapper<UUIDComponent>

	@Wire
	lateinit private var starSystem: StarSystem
	lateinit private var sensorsSubscription: EntitySubscription
	lateinit private var emissionsSubscription: EntitySubscription

	// c² / h²
	val stepSpeed = ((C_WAVE_SPEED * C_WAVE_SPEED) / (H_SQUARE_SIZE_KM * H_SQUARE_SIZE_KM)).toFloat()
	
	val waveHeight = FloatArray(WATER_SIZE * WATER_SIZE) // , {Math.random().toFloat()}
	val waveVelocity = FloatArray(WATER_SIZE * WATER_SIZE)
//	val waveHeightWrapper = FloatBuffer.wrap(waveHeight)

	var lastProcess: Long = galaxy.time
	val strideSize: Int

	class GravGlobalData(window: GravWindowData): Disposable {
		val shader: ShaderProgram
		val vertices: FloatArray
		val indices: ShortArray

		init {
			shader = Assets.gravimetricShaderProgram

			if (!shader.isCompiled || shader.getLog().length != 0) {
				println("shader errors: ${shader.getLog()}")
				throw RuntimeException("Shader compile error: ${shader.getLog()}")
			}
			
			vertices = FloatArray(MAX_VERTICES * (window.mesh.getVertexAttributes().vertexSize / 4));
			indices = ShortArray(MAX_INDICES);
		}
		
		override fun dispose() {}
	}
	
	class GravWindowData(): Disposable {

		val mesh: Mesh

		init {
			mesh = Mesh(false, MAX_VERTICES, MAX_INDICES,
			            VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, ShaderProgram.COLOR_ATTRIBUTE),
			            VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE)
//			            ,VertexAttribute.TexCoords(0)
			);
		}

		override fun dispose() {
			mesh.dispose()
		}
	}

//	val texture: Texture
	
//	val textureData = object: TextureData {
//		override fun prepare() {}
//
//		override fun getType(): TextureData.TextureDataType? = TextureData.TextureDataType.Custom
//		override fun getFormat(): Pixmap.Format? = throw GdxRuntimeException("not used")
//
//		override fun useMipMaps(): Boolean = false
//		override fun isPrepared(): Boolean = true
//		override fun isManaged(): Boolean = true
//
//		override fun getWidth(): Int = WATER_SIZE
//		override fun getHeight(): Int = WATER_SIZE
//
//		override fun disposePixmap(): Boolean = throw GdxRuntimeException("This TextureData implementation does not return a Pixmap")
//		override fun consumePixmap(): Pixmap? = throw GdxRuntimeException("This TextureData implementation does not return a Pixmap")
//
//		override fun consumeCustomData(target: Int) {
//			if (!Gdx.graphics.isGL30Available()) {
//				if (!Gdx.graphics.supportsExtension("GL_ARB_texture_float"))
//					throw GdxRuntimeException("Extension GL_ARB_texture_float not supported!");
//			}
//			
//			Gdx.gl.glPixelStorei(GL30.GL_UNPACK_ALIGNMENT, 4);
//			Gdx.gl.glPixelStorei(GL30.GL_UNPACK_ROW_LENGTH, 0);
//			Gdx.gl.glPixelStorei(GL30.GL_UNPACK_SKIP_PIXELS, 0);
//			Gdx.gl.glPixelStorei(GL30.GL_UNPACK_SKIP_ROWS, 0);
//			
//			Gdx.gl.glTexParameteri(target, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_LINEAR)
//			Gdx.gl.glTexParameteri(target, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_LINEAR)
//			Gdx.gl.glTexParameteri(target, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE);
//			Gdx.gl.glTexParameteri(target, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE);
//			
//			// https://www.khronos.org/registry/OpenGL-Refpages/es3.0/html/glTexImage2D.xhtml
////			Gdx.gl.glTexImage2D(target, 0, GL30.GL_R32F, WATER_SIZE, WATER_SIZE, 0, GL30.GL_RED, GL30.GL_FLOAT, waveHeightWrapper);
//			
//			
//			
////			val buf = ByteBuffer.allocate(4 * 3)
////			for (i in 1..4) {
////				buf.put(128.toByte())
////				buf.put(128.toByte())
////				buf.put(128.toByte())
////			}
////			buf.flip()
////			
////			Gdx.gl.glPixelStorei(GL30.GL_UNPACK_ALIGNMENT, 1);
////			Gdx.gl.glTexImage2D(target, 0, GL30.GL_RGB8, 2, 2, 0, GL30.GL_RGB, GL30.GL_UNSIGNED_BYTE, buf);
//			
//			
//			
//			val floats = floatArrayOf(0.0f, 0.0f, 0.0f,   1.0f, 1.0f, 1.0f,
//			                          1.0f, 1.0f, 1.0f,   0.0f, 0.0f, 0.0f)
//			val buf = FloatBuffer.wrap(floats)
//			Gdx.gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL20.GL_RGB, 2, 2, 0, GL20.GL_RGB, GL20.GL_FLOAT, buf);
//			
//			
//			var err = Gdx.gl.glGetError();
//			while (err != GL20.GL_NO_ERROR) {
//				GLErrorListener.LOGGING_LISTENER.onError(err)
//				err = Gdx.gl.glGetError()
//			}
//		}
//	}

	fun wData(): GravWindowData {
		var wData = AuroraGame.currentWindow.storage(GravWindowData::class)
		
		if (wData == null) {
			wData = GravWindowData()
			AuroraGame.currentWindow.storage + wData
		}
		
		return wData
	}
	
	fun gData() = AuroraGame.storage[GravGlobalData::class]
	
	init {
		if (MAX_INDICES / 6 * 4 != MAX_VERTICES) {
			throw RuntimeException("MAX_VERTICES not evenly divisible by 4")
		}
		
		var windowData = AuroraGame.currentWindow.storage(GravWindowData::class)
		
		if (windowData == null) {
			windowData = GravWindowData()
			AuroraGame.currentWindow.storage + windowData
		}
		
		var globalData = AuroraGame.storage(GravGlobalData::class)
		
		if (globalData == null) {
			globalData = GravGlobalData(windowData)
			AuroraGame.storage + globalData
		}
		
		strideSize = windowData.mesh.getVertexAttributes().vertexSize / 4
		

//		texture = Texture(textureData)
////		texture = Texture(GameServices[AssetManager::class].getFileHandleResolver().resolve("images/aurora.png"))
//		texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
//		texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
	}
	
	override fun dispose() {}

	override fun initialize() {
		sensorsSubscription = world.getAspectSubscriptionManager().get(SHIP_ASPECT)
		sensorsSubscription.addSubscriptionListener(object : SubscriptionListener {
			override fun inserted(entities: IntBag) {
				entities.forEachFast{ entityID ->
					var sensorComponent = sensorsMapper.get(entityID)

					if (sensorComponent == null) {

						val ship = shipMapper.get(entityID)
						val sensors = ship.hull[PassiveSensor::class]

						if (sensors.isNotEmpty()) {

							sensorComponent = sensorsMapper.create(entityID)
							sensorComponent.sensors = sensors

							sensors.forEachFast{ sensor ->
								if (ship.isPartEnabled(sensor)) {
									val poweredState = ship.getPartState(sensor)[PoweredPartState::class]
									poweredState.requestedPower = sensor.part.powerConsumption
								}
							}
						}
					}
				}
			}

			override fun removed(entities: IntBag) {}
		})
		
		println("WATER_SIZE $WATER_SIZE, cells ${WATER_SIZE * WATER_SIZE}, stepSpeed $stepSpeed, H_SQUARE_SIZE_KM $H_SQUARE_SIZE_KM, interval $interval")
	}

	//TODO register mass movement. hyperspace openings makes instant large push

//	@Subscribe
//	fun powerEvent(event: MovementEvent) {
//		val powerComponent = movementMapper.get(event.entityID)
//		powerComponent.stateChanged = true
//	}

	private fun index(x: Int, y: Int) = x * WATER_SIZE + y

	//TODO Accelerate with GPU
	/* Water surface simulation using height fields
	 *  https://gamedev.stackexchange.com/questions/79318/simple-water-surface-simulation-problems-gdc2008-matthias-muller-hello-world
	 *  https://archive.org/details/GDC2008Fischer
	 */
	override fun processSystem() {
//		val start = System.nanoTime()

		var deltaGameTime = (galaxy.time - lastProcess).toInt()
		lastProcess = galaxy.time

		while (deltaGameTime > 0) {
			var delta = Math.min(deltaGameTime, MAX_INTERVAL)
			deltaGameTime -= MAX_INTERVAL

//			println("delta $delta, deltaGameTime $deltaGameTime")

			if (delta >= H_SQUARE_SIZE_KM / C_WAVE_SPEED) {
				throw RuntimeException("Interval to large")
			}

			var attenuation = WAVE_ATTENUATION

			//TODO attenuation should give same wave pattern regardless of speed
//			for (i in 2..delta) {
//				attenuation *= WAVE_ATTENUATION
//			}

			for (x in 1..WATER_SIZE - 2) {
				for (y in 1..WATER_SIZE - 2) {
					var velocity = stepSpeed * ((waveHeight[index(x - 1, y)] + waveHeight[index(x + 1, y)] + waveHeight[index(x, y - 1)] + waveHeight[index(x, y + 1)]) - 4 * waveHeight[index(x, y)])
					waveVelocity[index(x, y)] = (waveVelocity[index(x, y)] + velocity * delta) * attenuation
				}
			}

			val waveSpeed = C_WAVE_SPEED * delta

			// Edges with no reflections
			for (x in 1..WATER_SIZE - 2) {
				run {
					val y = 0
					val u = waveHeight[index(x, 1)]
					waveHeight[index(x, y)] = (waveSpeed * u + waveHeight[index(x, y)] * H_SQUARE_SIZE_KMf) / (H_SQUARE_SIZE_KMf + waveSpeed)
				}
				run {
					val y = WATER_SIZE - 1
					val u = waveHeight[index(x, WATER_SIZE - 2)]
					waveHeight[index(x, y)] = (waveSpeed * u + waveHeight[index(x, y)] * H_SQUARE_SIZE_KMf) / (H_SQUARE_SIZE_KMf + waveSpeed)
				}
			}

			for (y in 1..WATER_SIZE - 2) {
				run {
					val x = 0
					val u = waveHeight[index(1, y)]
					waveHeight[index(x, y)] = (waveSpeed * u + waveHeight[index(x, y)] * H_SQUARE_SIZE_KMf) / (H_SQUARE_SIZE_KMf + waveSpeed)
				}
				run {
					val x = WATER_SIZE - 1
					val u = waveHeight[index(WATER_SIZE - 2, y)]
					waveHeight[index(x, y)] = (waveSpeed * u + waveHeight[index(x, y)] * H_SQUARE_SIZE_KMf) / (H_SQUARE_SIZE_KMf + waveSpeed)
				}
			}

			for (x in 1..WATER_SIZE - 2) {
				for (y in 1..WATER_SIZE - 2) {
					waveHeight[index(x, y)] += waveVelocity[index(x, y)] * delta
				}
			}
		}

		// Avoid redshift
		for (x in 1..WATER_SIZE - 2) {
			for (y in 1..WATER_SIZE - 2) {
				waveHeight[index(x, y)] *= 0.998f
			}
		}

//		val time = System.nanoTime() - start
//		println("Took $time")
	}
	
	private val lowColor = Color.BLUE
	private val highColor = Color.RED
	private val middleColor = Color(0f, 0f, 0f, 0f)
	private val color = Color()
	private var expAverage = 0.0

	fun render(viewport: Viewport, cameraOffset: Vector2L) {
		val start = System.nanoTime()

		normalRender(viewport, cameraOffset)
//		textureRender(viewport, cameraOffset)

		val time = System.nanoTime() - start
		
		if (expAverage == 0.0) {
			expAverage = time.toDouble()
		}
		
		//https://en.wikipedia.org/wiki/Moving_average#Application_to_measuring_computer_performance
		expAverage = exponentialAverage(time.toDouble(), expAverage, 30.0)
		
//		println("Took ${Units.nanoToString(time)}, expAverage ${Units.nanoToString(expAverage.toLong())}")
	}

	fun normalRender(viewport: Viewport, cameraOffset: Vector2L) {
		
		val projectionMatrix = viewport.camera.combined
		
		val gData = gData()
		val wData = wData()
		
		val vertices = gData.vertices
		val indices = gData.indices
		val shader = gData.shader
		val mesh = wData.mesh
		
		var vertexIdx = 0
		var indiceIdx = 0

		fun color(colorBits: Float) {
			vertices[vertexIdx++] = colorBits
		}

		fun vertex(x: Float, y: Float) {
			vertices[vertexIdx++] = x;
			vertices[vertexIdx++] = y;
		}
		
		shader.begin();
		shader.setUniformMatrix("u_projTrans", projectionMatrix);
		
		//TODO render only visible squares
		for (i in 0..WATER_SIZE - 1) {
			val x = ((i - WATER_SIZE / 2) * H_SQUARE_SIZE_KM - cameraOffset.x).toFloat()
			
			for (j in 0..WATER_SIZE - 1) {
				val height = waveHeight[index(i, j)]
				
				// works decently even though height never goes down completly
				if (Math.abs(height) < 0.05f) {
					continue
				}

				lerpColors(height, lowColor, middleColor, highColor, color)
				val colorBits = color.toFloatBits()

				val y = ((j - WATER_SIZE / 2) * H_SQUARE_SIZE_KM - cameraOffset.y).toFloat()

				if (vertices.size - vertexIdx < 4 * strideSize) {
					mesh.setVertices(vertices, 0, vertexIdx);
					mesh.setIndices(indices, 0, indiceIdx)
					mesh.render(shader, GL20.GL_TRIANGLES);
					shader.end();
					
					vertexIdx = 0
					indiceIdx = 0
					shader.begin();
					shader.setUniformMatrix("u_projTrans", projectionMatrix);
				}
				
				val strideIdx = vertexIdx / strideSize
				
				// Triangle 1
				indices[indiceIdx++] = (strideIdx + 1).toShort()
				indices[indiceIdx++] = (strideIdx + 0).toShort()
				indices[indiceIdx++] = (strideIdx + 2).toShort()
				
				// Triangle 2
				indices[indiceIdx++] = (strideIdx + 0).toShort()
				indices[indiceIdx++] = (strideIdx + 3).toShort()
				indices[indiceIdx++] = (strideIdx + 2).toShort()
				
				color(colorBits);
				vertex(x, y);
				color(colorBits);
				vertex(x + H_SQUARE_SIZE_KMf, y);
				color(colorBits);
				vertex(x + H_SQUARE_SIZE_KMf, y + H_SQUARE_SIZE_KMf);
				color(colorBits);
				vertex(x, y + H_SQUARE_SIZE_KMf);
			}
		}

		mesh.setVertices(vertices, 0, vertexIdx);
		mesh.setIndices(indices, 0, indiceIdx)
		mesh.render(shader, GL20.GL_TRIANGLES);
		shader.end();
	}

	//TODO add interpolation and smooth edges
	// https://www.gamedevelopment.blog/glsl-shader-language-libgdx/
	// https://github.com/libgdx/libgdx/wiki/Shaders
	// https://www.gamefromscratch.com/post/2014/07/08/LibGDX-Tutorial-Part-12-Using-GLSL-Shaders-and-creating-a-Mesh.aspx
	fun textureRender(viewport: Viewport, cameraOffset: Vector2L) {
		
		val gData = gData()
		val wData = wData()
		
		val vertices = gData.vertices
		val indices = gData.indices
		val shader = gData.shader
		val mesh = wData.mesh
		
		//		texture.load(textureData)
		
		Gdx.gl.glClearColor(0.2f, 0.3f, 0.4f, 0.8f)
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
		
		val id = Gdx.gl.glGenTexture()
		if (id == 0) {
			println("id $id")
		}
		
		Gdx.gl.glDisable(GL20.GL_BLEND);
		
		Gdx.gl.glActiveTexture(GL20.GL_TEXTURE1)
		Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, id)
		
		var width: Int
		var height: Int
		
//		width = GL11.glGetTexLevelParameteri(GL20.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH)
//		height = GL11.glGetTexLevelParameteri(GL20.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT)
//		println("width $width, height $height")
		
//		Gdx.gl.glPixelStorei(GL30.GL_UNPACK_ALIGNMENT, 1)
//		Gdx.gl.glPixelStorei(GL30.GL_UNPACK_ROW_LENGTH, 0)
//		Gdx.gl.glPixelStorei(GL30.GL_UNPACK_SKIP_PIXELS, 0)
//		Gdx.gl.glPixelStorei(GL30.GL_UNPACK_SKIP_ROWS, 0)
		
		Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_NEAREST)
		Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_NEAREST)
		Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE)
		Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE)
		
		//TODO not working
//		Gdx.gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL30.GL_R32F, WATER_SIZE, WATER_SIZE, 0, GL30.GL_RED, GL30.GL_FLOAT, waveHeightWrapper)
		
		
		val floats = floatArrayOf(1.0f, 0.3f, 0.6f, 0.2f)
		val buf = FloatBuffer.wrap(floats)
		GL11.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_R32F, 2, 2, 0, GL30.GL_RED, GL30.GL_FLOAT, buf);
//		GL11.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_R16F, 2, 2, 0, GL30.GL_RED, GL30.GL_FLOAT, buf);

//		val bytes = byteArrayOf(-1, 9,127, 0)
//		val buf = ByteBuffer.wrap(bytes)
		
//		val buf = ByteBuffer.allocate(4)
//		for(i in 1..4) {
//			buf.put(127)
//		}
//		buf.flip()
//		GL11.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL30.GL_R8, 2, 2, 0, GL30.GL_RED, GL30.GL_UNSIGNED_BYTE, buf);
		
//		println("w ${buf[0]} ${buf[1]} ${buf[2]} ${buf[3]}")
		
		var err = Gdx.gl.glGetError();
		while (err != GL20.GL_NO_ERROR) {
			GLErrorListener.THROWING_LISTENER.onError(err)
			err = Gdx.gl.glGetError()
		}
		
		width = GL11.glGetTexLevelParameteri(GL20.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH)
		height = GL11.glGetTexLevelParameteri(GL20.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT)
//		println("width $width, height $height")

				
		val ret = FloatArray(width * height, {0.1f})
		GL11.glGetTexImage(GL20.GL_TEXTURE_2D, 0, GL30.GL_RED, GL30.GL_FLOAT, ret)
		
		
//		Gdx.gl.glPixelStorei(GL30.GL_PACK_ALIGNMENT, 1)
//		val packAlign = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT)
//		
//		val ret = ByteBuffer.allocate(width * height + height * (packAlign - 1))
//		for(i in 1..ret.capacity()) {
//			ret.put(5)
//		}
//		ret.clear()
//		GL11.glGetTexImage(GL20.GL_TEXTURE_2D, 0, GL30.GL_RED, GL30.GL_UNSIGNED_BYTE, ret)
		
//		println("r ${ret[0]} ${ret[1]} ${ret[2]} ${ret[3]}")
		
		err = Gdx.gl.glGetError();
		while (err != GL20.GL_NO_ERROR) {
			GLErrorListener.THROWING_LISTENER.onError(err)
			err = Gdx.gl.glGetError()
		}
		 
		shader.begin();
		shader.setUniformMatrix("u_projTrans", viewport.camera.combined);
		shader.setUniformi("u_texture", 1);
		
//		texture.bind()
		
		var vertexIdx = 0
		var indiceIdx = 0
		val strideIdx = vertexIdx / strideSize
		
		fun vertex(x: Float, y: Float) {
			vertices[vertexIdx++] = x;
			vertices[vertexIdx++] = y;
		}
		
		fun texCoords(s: Float, t: Float) {
			vertices[vertexIdx++] = s;
			vertices[vertexIdx++] = t;
		}
		
		// Triangle 1
		indices[indiceIdx++] = (strideIdx + 1).toShort()
		indices[indiceIdx++] = (strideIdx + 0).toShort()
		indices[indiceIdx++] = (strideIdx + 2).toShort()
		
		// Triangle 2
		indices[indiceIdx++] = (strideIdx + 0).toShort()
		indices[indiceIdx++] = (strideIdx + 3).toShort()
		indices[indiceIdx++] = (strideIdx + 2).toShort()
		
		val x = ((-WATER_SIZE / 2) * H_SQUARE_SIZE_KM - cameraOffset.x).toFloat()
		val y = ((-WATER_SIZE / 2) * H_SQUARE_SIZE_KM - cameraOffset.y).toFloat()
		val max = (MAX_AU * Units.AU).toFloat()
		
		vertex(x, y);
		texCoords(0f, 0f)
		vertex(x + max, y);
		texCoords(1f, 0f)
		vertex(x + max, y + max);
		texCoords(1f, 1f)
		vertex(x, y + max);
		texCoords(0f, 1f)
		
		mesh.setVertices(vertices, 0, vertexIdx);
		mesh.setIndices(indices, 0, indiceIdx)
		mesh.render(shader, GL20.GL_TRIANGLES);
		shader.end();
		
		Gdx.gl.glDeleteTexture(id);
		Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
	}
	
}
