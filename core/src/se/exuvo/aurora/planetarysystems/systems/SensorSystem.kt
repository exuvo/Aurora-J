package se.exuvo.aurora.planetarysystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.EntitySubscription
import com.artemis.EntitySubscription.SubscriptionListener
import com.artemis.systems.IteratingSystem
import com.artemis.utils.IntBag
import net.mostlyoriginal.api.event.common.EventSystem
import org.apache.log4j.Logger
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.PartRef
import se.exuvo.aurora.galactic.PassiveSensor
import se.exuvo.aurora.planetarysystems.components.DetectionComponent
import se.exuvo.aurora.planetarysystems.components.DetectionHit
import se.exuvo.aurora.planetarysystems.components.EmissionsComponent
import se.exuvo.aurora.planetarysystems.components.OwnerComponent
import se.exuvo.aurora.planetarysystems.components.PassiveSensorState
import se.exuvo.aurora.planetarysystems.components.PassiveSensorsComponent
import se.exuvo.aurora.planetarysystems.components.PoweredPartState
import se.exuvo.aurora.planetarysystems.components.ShipComponent
import se.exuvo.aurora.planetarysystems.components.TimedMovementComponent
import se.exuvo.aurora.planetarysystems.components.UUIDComponent
import se.exuvo.aurora.planetarysystems.events.PowerEvent
import se.exuvo.aurora.planetarysystems.events.getEvent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.utils.forEach

class PassiveSensorSystem : IteratingSystem(FAMILY) {
	companion object {
		val FAMILY = Aspect.all(PassiveSensorsComponent::class.java)
		val SHIP_ASPECT = Aspect.all(ShipComponent::class.java)
		val EMISSION_FAMILY = Aspect.all(EmissionsComponent::class.java)
	}

	val log = Logger.getLogger(this.javaClass)
	private val galaxy = GameServices[Galaxy::class]

	lateinit private var movementMapper: ComponentMapper<TimedMovementComponent>
	lateinit private var sensorsMapper: ComponentMapper<PassiveSensorsComponent>
	lateinit private var emissionsMapper: ComponentMapper<EmissionsComponent>
	lateinit private var detectionMapper: ComponentMapper<DetectionComponent>
	lateinit private var ownerMapper: ComponentMapper<OwnerComponent>
	lateinit private var shipMapper: ComponentMapper<ShipComponent>
	lateinit private var uuidMapper: ComponentMapper<UUIDComponent>
	lateinit private var emissionsSubscription: EntitySubscription
	lateinit private var events: EventSystem

	var emitters: List<Emitter> = emptyList()
	var emissionFamilyChanged = true
	
	override fun initialize() {
		emissionsSubscription = world.getAspectSubscriptionManager().get(EMISSION_FAMILY)
		emissionsSubscription.addSubscriptionListener(object : SubscriptionListener {
			override fun inserted(entities: IntBag) {
				emissionFamilyChanged = true
			}

			override fun removed(entities: IntBag) {
				emissionFamilyChanged = true
			}
		})
		world.getAspectSubscriptionManager().get(SHIP_ASPECT).addSubscriptionListener(object : SubscriptionListener {
			override fun inserted(entities: IntBag) {
				entities.forEach { entityID ->
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
				}
			}

			override fun removed(entities: IntBag) {
			}
		})
	}

	override fun begin() {

		if (emissionFamilyChanged) {
			emissionFamilyChanged = false

			val emissionEntities = emissionsSubscription.getEntities()

			if (emissionEntities.size() == 0) {

				emitters = emptyList()

			} else {

				val tempEmitters = ArrayList<Emitter>(emissionEntities.size())

				emissionEntities.forEach { entityID ->
					var emissions = emissionsMapper.get(entityID)
					var position = movementMapper.get(entityID).get(galaxy.time).value.position

					tempEmitters.add(Emitter(entityID, position, emissions))
				}

				emitters = tempEmitters
			}
		}
	}

	override fun process(entityID: Int) {

		val ship = shipMapper.get(entityID)
		val movement = movementMapper.get(entityID)
		val sensorPosition = movement.get(galaxy.time).value.position
		val owner = ownerMapper.get(entityID)

		val sensors = sensorsMapper.get(entityID).sensors
		var detectionComponent = detectionMapper.get(entityID)

		val detections = HashMap<PartRef<PassiveSensor>, HashMap<Int, HashMap<Int, DetectionHit>>>()

		for (sensor in sensors) {

			if (!ship.isPartEnabled(sensor)) {
				continue
			}

			val poweredState = ship.getPartState(sensor)[PoweredPartState::class]

			if (poweredState.givenPower == 0L) {
				continue
			}

			val powerRatio = poweredState.givenPower / poweredState.requestedPower.toDouble()

			val sensorState = ship.getPartState(sensor)[PassiveSensorState::class]

			if (galaxy.time >= sensorState.lastScan + sensor.part.refreshDelay) {
				sensorState.lastScan = galaxy.time

				val arcWidth = 360.0 / sensor.part.arcSegments

				for (emitter in emitters) {

					if (emitter.entityID == entityID) {
						continue
					}

					if (owner != null && ownerMapper.has(emitter.entityID) && owner.empire == ownerMapper.get(emitter.entityID).empire) {
						continue
					}

					var emitterPosition = emitter.position
					val emission = emitter.emissions.emissions[sensor.part.spectrum];

					if (emission != null) {

						val trueDistanceInKM: Double = sensorPosition.dst(emitterPosition) / 1000

						// https://en.wikipedia.org/wiki/Inverse-square_law
						val signalStrength = emission / (4 * Math.PI * Math.pow(trueDistanceInKM / 2, 2.0))

						if (signalStrength * powerRatio >= sensor.part.sensitivity) {

							if (sensor.part.accuracy != 1.0) {
								val temp = emitterPosition.cpy().sub(sensorPosition)
								temp.set(temp.len().toLong(), 0).scl(Math.random() * (1 - sensor.part.accuracy))

								if (shipMapper.has(emitter.entityID)) {
									val hash = 37 * shipMapper.get(emitter.entityID).shipClass.hashCode() + sensor.hashCode()
//									println("hash $hash, uuid ${uuidMapper.get(emitter.entity).uuid.dispersedHash}, sensor ${sensor.hashCode()}")
									temp.rotate((hash % 360).toFloat())

								} else if (uuidMapper.has(emitter.entityID)) {
									val hash = 37 * uuidMapper.get(emitter.entityID).uuid.dispersedHash + sensor.hashCode()
//									println("hash $hash, uuid ${uuidMapper.get(emitter.entity).uuid.dispersedHash}, sensor ${sensor.hashCode()}")
									temp.rotate((hash % 360).toFloat())


								} else {
									temp.rotateRad(2 * Math.PI * Math.random())
								}

								emitterPosition = emitterPosition.cpy().add(temp)
							}

							val distanceInKM: Double = sensorPosition.dst(emitterPosition) / 1000
							val targetAngle = sensorPosition.angleTo(emitterPosition)

							val arcAngleStep = Math.floor((targetAngle - sensor.part.angleOffset) / arcWidth).toInt()
							val distanceStep = Math.floor(distanceInKM / sensor.part.distanceResolution).toInt()

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
								detectionHit = DetectionHit(0.0, ArrayList<Int>(), ArrayList<Vector2L>())
								distanceSteps[distanceStep] = detectionHit
							}

							detectionHit.signalStrength += signalStrength
							detectionHit.entities.add(emitter.entityID)
							detectionHit.hitPositions.add(emitterPosition)
						}
					}
				}

			} else {

				if (detectionComponent != null) {

					val previousDetection = detectionComponent.detections[sensor]

					if (previousDetection != null) {
						@Suppress("UNCHECKED_CAST")
						detections[sensor] = previousDetection as HashMap<Int, HashMap<Int, DetectionHit>>
					}
				}
			}
		}

		if (detections.isEmpty()) {

			if (detectionComponent != null) {
				detectionMapper.remove(entityID)
			}

		} else {

			if (detectionComponent == null) {
				detectionComponent = detectionMapper.create(entityID)
			}

			detectionComponent.detections = detections
		}
	}

	data class Emitter(val entityID: Int, val position: Vector2L, val emissions: EmissionsComponent)
}
