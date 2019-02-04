package se.exuvo.aurora.planetarysystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.EntitySubscription
import com.artemis.EntitySubscription.SubscriptionListener
import com.artemis.systems.IteratingSystem
import com.artemis.utils.IntBag
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.particles.emitters.Emitter
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.viewport.Viewport
import org.apache.log4j.Logger
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.PassiveSensor
import se.exuvo.aurora.planetarysystems.components.DetectionComponent
import se.exuvo.aurora.planetarysystems.components.EmissionsComponent
import se.exuvo.aurora.planetarysystems.components.OwnerComponent
import se.exuvo.aurora.planetarysystems.components.PassiveSensorsComponent
import se.exuvo.aurora.planetarysystems.components.PoweredPartState
import se.exuvo.aurora.planetarysystems.components.ShipComponent
import se.exuvo.aurora.planetarysystems.components.TimedMovementComponent
import se.exuvo.aurora.planetarysystems.components.UUIDComponent
import se.exuvo.aurora.planetarysystems.systems.OrbitSystem.OrbitCache
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.utils.forEach
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.planetarysystems.components.MassComponent
import se.exuvo.aurora.planetarysystems.components.GravimetricSensorsComponent
import se.exuvo.aurora.utils.lerpColors
import se.exuvo.aurora.planetarysystems.PlanetarySystem
import com.artemis.annotations.Wire
import java.lang.RuntimeException
import se.exuvo.aurora.Assets
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL30

class GravimetricSensorSystem : GalaxyTimeIntervalSystem((H_SQUARE_SIZE_KM / Units.C).toLong()) { // 
	companion object {
		val SENSOR_ASPECT = Aspect.all(GravimetricSensorsComponent::class.java)
		val EMISSION_ASPECT = Aspect.all(MassComponent::class.java, TimedMovementComponent::class.java)
		val SHIP_ASPECT = Aspect.all(ShipComponent::class.java)

		const val MAX_AU = 10;
		const val SQUARES_PER_AU = 20
		const val H_SQUARE_SIZE_KM = Units.AU / SQUARES_PER_AU
		const val H_SQUARE_SIZE_KMf = H_SQUARE_SIZE_KM.toFloat()
		const val WATER_SIZE = (MAX_AU * SQUARES_PER_AU).toInt()

		const val WAVE_ATTENUATION =
			0.999f //TODO lower if high SQUARES_PER_AU (Math.pow(0.999, SQUARES_PER_AU.toDouble()).toFloat())
		const val C_WAVE_SPEED = Units.C.toFloat()
		const val MAX_INTERVAL = 5 //(H_SQUARE_SIZE_KM / C_WAVE_SPEED).toInt()
		
		const val MAX_VERTICES = 500 * 4 * 3
		const val MAX_INDICES = (MAX_VERTICES / 4) * 6
	}

	val log = Logger.getLogger(this.javaClass)

	lateinit private var movementMapper: ComponentMapper<TimedMovementComponent>
	lateinit private var sensorsMapper: ComponentMapper<PassiveSensorsComponent>
	lateinit private var emissionsMapper: ComponentMapper<EmissionsComponent>
	lateinit private var detectionMapper: ComponentMapper<DetectionComponent>
	lateinit private var ownerMapper: ComponentMapper<OwnerComponent>
	lateinit private var shipMapper: ComponentMapper<ShipComponent>
	lateinit private var uuidMapper: ComponentMapper<UUIDComponent>

	@Wire
	lateinit private var planetarySystem: PlanetarySystem
	lateinit private var sensorsSubscription: EntitySubscription
	lateinit private var emissionsSubscription: EntitySubscription

	val waveHeight = FloatArray(WATER_SIZE * WATER_SIZE)
	val waveVelocity = FloatArray(WATER_SIZE * WATER_SIZE)

	// c² / h²
	val stepSpeed = ((C_WAVE_SPEED * C_WAVE_SPEED) / (H_SQUARE_SIZE_KM * H_SQUARE_SIZE_KM)).toFloat()
	var lastProcess: Long = galaxy.time

	//TODO singleton per galaxy
	val shader: ShaderProgram
	val mesh: Mesh
	val vertices: FloatArray
	val indices: ShortArray
	val strideSize: Int

	init {
		if (MAX_INDICES / 6 * 4 != MAX_VERTICES) {
			throw RuntimeException("MAX_VERTICES not evenly divisible by 4")
		}
		
		shader = Assets.gravimetricShaderProgram
		mesh = Mesh(
			false, MAX_VERTICES, MAX_INDICES,
			VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, ShaderProgram.COLOR_ATTRIBUTE),
			VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE)
		);
		strideSize = mesh.getVertexAttributes().vertexSize / 4
		vertices = FloatArray(MAX_VERTICES * (mesh.getVertexAttributes().vertexSize / 4));
		indices = ShortArray(MAX_INDICES);
		
//		val colorSize = mesh.getVertexAttribute(VertexAttributes.Usage.ColorPacked).sizeInBytes / 4
//		val positionSize = mesh.getVertexAttribute(VertexAttributes.Usage.Position).sizeInBytes / 4
		
//		println("colorSize $colorSize, positionSize $positionSize, compiled ${shader.isCompiled}")
		
		if (!shader.isCompiled || shader.getLog().length != 0) {
			println("shader errors: ${shader.getLog()}")
			throw RuntimeException()
		}
	}
	
	override fun dispose() {
		mesh.dispose()
	}

	override fun initialize() {
		sensorsSubscription = world.getAspectSubscriptionManager().get(SHIP_ASPECT)
		sensorsSubscription.addSubscriptionListener(object : SubscriptionListener {
			override fun inserted(entities: IntBag) {
				entities.forEach({ entityID ->
					var sensorComponent = sensorsMapper.get(entityID)

					if (sensorComponent == null) {

						val ship = shipMapper.get(entityID)
						val sensors = ship.shipClass[PassiveSensor::class]

						if (sensors.isNotEmpty()) {

							sensorComponent = sensorsMapper.create(entityID)
							sensorComponent.sensors = sensors

							sensors.forEach({
								val sensor = it
								if (ship.isPartEnabled(sensor)) {
									val poweredState = ship.getPartState(sensor)[PoweredPartState::class]
									poweredState.requestedPower = sensor.part.powerConsumption
								}
							})
						}
					}
				})
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

	private val shapeRenderer by lazy(LazyThreadSafetyMode.NONE) { GameServices[ShapeRenderer::class] }

	private val lowColor = Color.BLUE
	private val highColor = Color.RED
	private val middleColor = Color(0f, 0f, 0f, 0f)
	private val color = Color()
	private var expAverage = 0.0

	//TODO add interpolation and smooth edges
	// https://www.gamedevelopment.blog/glsl-shader-language-libgdx/
	// https://github.com/libgdx/libgdx/wiki/Shaders
	// https://www.gamefromscratch.com/post/2014/07/08/LibGDX-Tutorial-Part-12-Using-GLSL-Shaders-and-creating-a-Mesh.aspx
	fun render(viewport: Viewport, cameraOffset: Vector2L) {
		val start = System.nanoTime()

//		val renderer = shapeRenderer
//		renderer.begin(ShapeRenderer.ShapeType.Filled)
//
//		for (x in 0..WATER_SIZE - 1) {
//			for (y in 0..WATER_SIZE - 1) {
//				val height = waveHeight[index(x, y)]
//
//				lerpColors(height, lowColor, middleColor, highColor, color)
//				renderer.color = color
//
//				val x1 = ((x - WATER_SIZE / 2) * H_SQUARE_SIZE_KM - cameraOffset.x).toFloat()
//				val y1 = ((y - WATER_SIZE / 2) * H_SQUARE_SIZE_KM - cameraOffset.y).toFloat()
//
//				renderer.rect(x1, y1, H_SQUARE_SIZE_KMf, H_SQUARE_SIZE_KMf)
//			}
//		}
//		renderer.end();

		var vertexIdx = 0
		var indiceIdx = 0

		fun color(colorBits: Float) {
			vertices[vertexIdx++] = colorBits
		}

		fun vertex(x: Float, y: Float) {
			vertices[vertexIdx++] = x;
			vertices[vertexIdx++] = y;
		}

		val projectionMatrix = viewport.camera.combined
		
		shader.begin();
		shader.setUniformMatrix("u_projTrans", projectionMatrix);
		
		//TODO render only visible squares
		for (i in 0..WATER_SIZE - 1) {
			val x = ((i - WATER_SIZE / 2) * H_SQUARE_SIZE_KM - cameraOffset.x).toFloat()
			
			for (j in 0..WATER_SIZE - 1) {
				val height = waveHeight[index(i, j)]
				
				// works decently even though height never goes down completly
//				if (Math.abs(height) < 0.05f) {
//					continue
//				}

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

		val time = System.nanoTime() - start
		
		if (expAverage == 0.0) {
			expAverage = time.toDouble()
		}
		
		//https://en.wikipedia.org/wiki/Moving_average#Application_to_measuring_computer_performance
		expAverage = time + Math.pow(Math.E, -1/30.0) * (expAverage - time)
		
		println("Took ${Units.nanoToString(time)}, expAverage ${Units.nanoToString(expAverage.toLong())}")
	}

}
