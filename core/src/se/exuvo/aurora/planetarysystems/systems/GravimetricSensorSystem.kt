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
import com.badlogic.gdx.math.MathUtils
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.planetarysystems.components.MassComponent
import se.exuvo.aurora.planetarysystems.components.GravimetricSensorsComponent
import se.exuvo.aurora.utils.lerpColors
import se.exuvo.aurora.planetarysystems.PlanetarySystem
import com.artemis.annotations.Wire
import java.lang.RuntimeException

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
		
		const val WAVE_ATTENUATION = 0.999f //TODO lower if high SQUARES_PER_AU (Math.pow(0.999, SQUARES_PER_AU.toDouble()).toFloat())
		const val C_WAVE_SPEED = Units.C.toFloat()
		const val MAX_INTERVAL = 5 //(H_SQUARE_SIZE_KM / C_WAVE_SPEED).toInt()
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
	
			for (x in 1..WATER_SIZE-2) {
				for (y in 1..WATER_SIZE-2) {
					var velocity = stepSpeed * ((waveHeight[index(x-1, y)] + waveHeight[index(x+1, y)] + waveHeight[index(x, y-1)] + waveHeight[index(x, y+1)]) - 4 * waveHeight[index(x, y)])
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
			
			for (y in 1..WATER_SIZE-2) {
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
			
			for (x in 1..WATER_SIZE-2) {
				for (y in 1..WATER_SIZE-2) {
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
	
	private val shapeRenderer by lazy { GameServices[ShapeRenderer::class] }
	
	private val lowColor = Color.BLUE
	private val highColor = Color.RED
	private val middleColor = Color(0f, 0f, 0f, 0f)
	private val color = Color()
	
	//TODO render with custom shader to add interpolation and smooth edges
	// https://www.gamedevelopment.blog/glsl-shader-language-libgdx/
	// https://github.com/libgdx/libgdx/wiki/Shaders
	// https://www.gamefromscratch.com/post/2014/07/08/LibGDX-Tutorial-Part-12-Using-GLSL-Shaders-and-creating-a-Mesh.aspx
	fun render(cameraOffset: Vector2L) {
		val renderer = shapeRenderer
		renderer.begin(ShapeRenderer.ShapeType.Filled)

		for (x in 0..WATER_SIZE - 1) {
			for (y in 0..WATER_SIZE - 1) {
				val height = waveHeight[index(x, y)]

				lerpColors(height, lowColor, middleColor, highColor, color)
				renderer.color = color

				val x1 = ((x - WATER_SIZE / 2) * H_SQUARE_SIZE_KM - cameraOffset.x).toFloat()
				val y1 = ((y - WATER_SIZE / 2) * H_SQUARE_SIZE_KM - cameraOffset.y).toFloat()
				
				renderer.rect(x1, y1, H_SQUARE_SIZE_KMf, H_SQUARE_SIZE_KMf)
			}
		}
		renderer.end();
	}
	
}
