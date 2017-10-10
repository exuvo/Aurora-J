package se.exuvo.aurora.planetarysystems.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import org.apache.log4j.Logger
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.planetarysystems.components.DetectionComponent
import se.exuvo.aurora.planetarysystems.components.EmissionsComponent
import se.exuvo.aurora.planetarysystems.components.PassiveSensorsComponent
import se.exuvo.aurora.planetarysystems.components.TimedMovementComponent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.planetarysystems.components.DetectionHit
import se.exuvo.aurora.galactic.PassiveSensor

class SensorSystem : IteratingSystem(FAMILY) {
	companion object {
		val FAMILY = Family.all(PassiveSensorsComponent::class.java).get()
		val EMISSION_FAMILY = Family.all(EmissionsComponent::class.java).get()
	}

	val log = Logger.getLogger(this.javaClass)
	private val galaxy = GameServices[Galaxy::class.java]

	private val movementMapper = ComponentMapper.getFor(TimedMovementComponent::class.java)
	private val sensorsMapper = ComponentMapper.getFor(PassiveSensorsComponent::class.java)
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

		val detections = HashMap<PassiveSensor, HashMap<Int, HashMap<Int, DetectionHit>>>()

		for (sensor in sensors) {
			
			val arcWidth = 360.0 / sensor.arcSegments
			
			for (emitter in emitters) {

				val emitterPosition = emitter.position
				val emission = emitter.emissions.emissions[sensor.spectrum];

				if (emission != null) {

					val distanceInKM: Double = sensorPosition.dst(emitterPosition) / 1000

					// https://en.wikipedia.org/wiki/Inverse-square_law
					val signalStrength = emission / (4 * Math.PI * Math.pow(distanceInKM / 2, 2.0))

					if (signalStrength >= sensor.sensitivity) {
						
						val targetAngle = sensorPosition.angleTo(emitterPosition)
						
						val arcAngleStep = Math.floor((targetAngle - sensor.angleOffset) / arcWidth).toInt()
						val distanceStep = Math.floor(distanceInKM / sensor.distanceResolution).toInt()

						var angleSteps: HashMap<Int, HashMap<Int, DetectionHit>>? = detections[sensor]

						if (angleSteps == null) {
							
							angleSteps = HashMap<Int, HashMap<Int, DetectionHit>>()
							detections[sensor] = angleSteps
						}
						
						var distanceSteps: HashMap<Int, DetectionHit>? = angleSteps[arcAngleStep]

						if (distanceSteps == null) {
							
							distanceSteps = HashMap<Int, DetectionHit>()
							angleSteps[arcAngleStep] = distanceSteps
						}
						
						var detectionHit: DetectionHit? = distanceSteps[distanceStep]

						if (detectionHit == null) {
							
							detectionHit = DetectionHit(0.0, ArrayList<Entity>())
							distanceSteps[distanceStep] = detectionHit
						}

						detectionHit.signalStrength += signalStrength
						detectionHit.entities.add(emitter.entity)
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
