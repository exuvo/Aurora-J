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

//TODO hyperspace openings makes instant large push

/* 
 * TODO Accelerate with GPU
 *  https://www.gamedevelopment.blog/glsl-shader-language-libgdx/
 *  https://github.com/libgdx/libgdx/wiki/Shaders
 *  https://www.gamefromscratch.com/post/2014/07/08/LibGDX-Tutorial-Part-12-Using-GLSL-Shaders-and-creating-a-Mesh.aspx
**/
class GravimetricSensorSystem : GalaxyTimeIntervalSystem(1) { // (SQUARE_SIZE_KM / Units.C).toLong()
	companion object {
		val SENSOR_ASPECT = Aspect.all(GravimetricSensorsComponent::class.java)
		val EMISSION_ASPECT = Aspect.all(MassComponent::class.java, TimedMovementComponent::class.java)
		val SHIP_ASPECT = Aspect.all(ShipComponent::class.java)
		
		val MAX_AU = 5;
		val SQUARES_PER_AU = 50
		val H_SQUARE_SIZE_KM = Units.AU / SQUARES_PER_AU
		val H_SQUARE_SIZE_KMf = H_SQUARE_SIZE_KM.toFloat()
		val WATER_SIZE = (MAX_AU * SQUARES_PER_AU).toInt()
		
		val WAVE_ATTENUATION = 0.999f
		val C_WAVE_SPEED = Units.C.toFloat()
		val MAX_INTERVAL = (H_SQUARE_SIZE_KM / C_WAVE_SPEED).toInt()
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
	
	val waveHeight = Array<Array<Float>>(WATER_SIZE, {Array<Float>(WATER_SIZE, {0f})})
	val waveVelocity = Array<Array<Float>>(WATER_SIZE, {Array<Float>(WATER_SIZE, {0f})})
	
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
		
		println("WATER_SIZE $WATER_SIZE, cells ${WATER_SIZE * WATER_SIZE}, stepSpeed $stepSpeed, H_SQUARE_SIZE_KM $H_SQUARE_SIZE_KM")
	}


	/* Water surface simulation using height fields
	 *  https://gamedev.stackexchange.com/questions/79318/simple-water-surface-simulation-problems-gdc2008-matthias-muller-hello-world
	 *  https://archive.org/details/GDC2008Fischer
	 */
	override fun processSystem() {

		var deltaGameTime = (galaxy.time - lastProcess).toInt()
		lastProcess = galaxy.time
		
		while (deltaGameTime > 0) {
			var delta = Math.min(deltaGameTime, MAX_INTERVAL)
			deltaGameTime -= MAX_INTERVAL
	
			if (delta >= H_SQUARE_SIZE_KM / C_WAVE_SPEED) {
				throw RuntimeException("Interval to large")
			}
	
			for (x in 1..WATER_SIZE-2) {
				for (y in 1..WATER_SIZE-2) {
					var velocity = stepSpeed * ((waveHeight[x-1][y] + waveHeight[x+1][y] + waveHeight[x][y-1] + waveHeight[x][y+1]) - 4 * waveHeight[x][y])
					waveVelocity[x][y] += velocity * delta
					waveVelocity[x][y] *= WAVE_ATTENUATION
				}
			}
			
			// Edges with no reflections
			for (x in 1..WATER_SIZE - 2) {
				run {
					val y = 0
					val u = waveHeight[x][1]
					waveHeight[x][y] = (C_WAVE_SPEED * delta * u + waveHeight[x][y] * H_SQUARE_SIZE_KMf) / (H_SQUARE_SIZE_KMf + C_WAVE_SPEED * delta)
				}
				run {
					val y = WATER_SIZE - 1
					val u = waveHeight[x][WATER_SIZE - 2]
					waveHeight[x][y] = (C_WAVE_SPEED * delta * u + waveHeight[x][y] * H_SQUARE_SIZE_KMf) / (H_SQUARE_SIZE_KMf + C_WAVE_SPEED * delta)
				}
			}
			
			for (y in 1..WATER_SIZE-2) {
				run {
					val x = 0
					val u = waveHeight[1][y]
					waveHeight[x][y] = (C_WAVE_SPEED * delta * u + waveHeight[x][y] * H_SQUARE_SIZE_KMf) / (H_SQUARE_SIZE_KMf + C_WAVE_SPEED * delta)
				}
				run {
					val x = WATER_SIZE - 1
					val u = waveHeight[WATER_SIZE - 2][y]
					waveHeight[x][y] = (C_WAVE_SPEED * delta * u + waveHeight[x][y] * H_SQUARE_SIZE_KMf) / (H_SQUARE_SIZE_KMf + C_WAVE_SPEED * delta)
				}
			}
			
			for (x in 1..WATER_SIZE-2) {
				for (y in 1..WATER_SIZE-2) {
					waveHeight[x][y] += waveVelocity[x][y] * delta
				}
			}
		}
	}
	
	private val shapeRenderer by lazy { GameServices[ShapeRenderer::class] }
	
	private val lowColor = Color.BLUE
	private val highColor = Color.RED
	private val middleColor = Color(0f, 0f, 0f, 0f)
	private val color = Color()
	
	fun render(cameraOffset: Vector2L) {
		val renderer = shapeRenderer
		shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

		for (x in 0..WATER_SIZE - 1) {
			for (y in 0..WATER_SIZE - 1) {
				val height = waveHeight[x][y]

				lerpColors(height, lowColor, middleColor, highColor, color)
				shapeRenderer.color = color

				val x1 = ((x - WATER_SIZE / 2) * H_SQUARE_SIZE_KM - cameraOffset.x).toFloat()
				val y1 = ((y - WATER_SIZE / 2) * H_SQUARE_SIZE_KM - cameraOffset.y).toFloat()
				
				shapeRenderer.rect(x1, y1, H_SQUARE_SIZE_KMf, H_SQUARE_SIZE_KMf)
			}
		}
		shapeRenderer.end();
	}
	
}
