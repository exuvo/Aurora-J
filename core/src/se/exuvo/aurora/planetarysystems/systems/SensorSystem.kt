package se.exuvo.aurora.planetarysystems.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import org.apache.log4j.Logger
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.planetarysystems.components.DetectionComponent
import se.exuvo.aurora.planetarysystems.components.EmissionsComponent
import se.exuvo.aurora.planetarysystems.components.SensorsComponent
import se.exuvo.aurora.planetarysystems.components.TimedMovementComponent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.planetarysystems.components.DetectionHit

class SensorSystem : IteratingSystem(FAMILY) {
	companion object {
		val FAMILY = Family.all(SensorsComponent::class.java).get()
		val EMISSION_FAMILY = Family.all(EmissionsComponent::class.java).get()
	}

	val log = Logger.getLogger(this.javaClass)
	private val galaxy = GameServices[Galaxy::class.java]

	private val movementMapper = ComponentMapper.getFor(TimedMovementComponent::class.java)
	private val sensorsMapper = ComponentMapper.getFor(SensorsComponent::class.java)
	private val emissionsMapper = ComponentMapper.getFor(EmissionsComponent::class.java)
	private val detectionMapper = ComponentMapper.getFor(DetectionComponent::class.java)

	var emitters: List<Emitter> = emptyList()

	override fun update(deltaTime: Float) {

		val emissionEntities = engine.getEntitiesFor(EMISSION_FAMILY)

		if (emissionEntities.size() == 0) {

			if (emitters.isNotEmpty()) {
				emitters = emptyList()
			}

		} else if (emissionEntities.size() != emitters.size) {

			val mutableSuns = ArrayList<Emitter>(emissionEntities.size())

			for (entity in emissionEntities) {

				var emissions = emissionsMapper.get(entity)
				var position = movementMapper.get(entity).get(galaxy.time).value.position

				mutableSuns.add(Emitter(entity, position, emissions))
			}

			emitters = mutableSuns
		}

		super.update(deltaTime)
	}

	override fun processEntity(entity: Entity, deltaGameTime: Float) {

		val movement = movementMapper.get(entity)
		val sensorPosition = movement.get(galaxy.time).value.position

		val sensors = sensorsMapper.get(entity).sensors

		val detections = HashMap<Entity, MutableList<DetectionHit>>()

		for (sensor in sensors) {
			for (emitter in emitters) {

				val emission = emitter.emissions.emissions[sensor.spectrum];

				if (emission != null) {

					val distance: Double = sensorPosition.dst(emitter.position) / 1000

					// https://en.wikipedia.org/wiki/Inverse-square_law
					val signalStrength = emission / (4 * Math.PI * Math.pow(distance / 2, 2.0))

//					println("${sensor.spectrum} signalStrength $signalStrength")
					
					if (signalStrength >= sensor.sensitivity) {

						var detectionHits: MutableList<DetectionHit>? = detections[emitter.entity]

						if (detectionHits == null) {
							
							detectionHits = ArrayList<DetectionHit>()
							detections[emitter.entity] = detectionHits
						}

						detectionHits.add(DetectionHit(sensor, signalStrength))
					}
				}
			}
		}

		var detectionComponent = detectionMapper.get(entity)

		if (detections.isEmpty()) {

			if (detectionComponent != null) {
				entity.remove(DetectionComponent::class.java)
			}

		} else {

			if (detectionComponent != null) {

				detectionComponent.detections = detections

			} else {

				entity.add(DetectionComponent(detections))
			}

		}

	}

	data class Emitter(val entity: Entity, val position: Vector2L, val emissions: EmissionsComponent)
}
