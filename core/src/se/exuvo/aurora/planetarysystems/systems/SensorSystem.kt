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
import se.exuvo.aurora.planetarysystems.components.OwnerComponent
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.EntityListener
import se.exuvo.aurora.planetarysystems.components.ShipComponent
import se.exuvo.aurora.planetarysystems.components.PoweredPartState
import se.exuvo.aurora.planetarysystems.components.PassiveSensorState
import jdk.nashorn.internal.ir.annotations.Ignore
import se.exuvo.aurora.planetarysystems.components.UUIDComponent
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.galactic.PartRef

class PassiveSensorSystem : IteratingSystem(FAMILY), EntityListener {
	companion object {
		val FAMILY = Family.all(PassiveSensorsComponent::class.java).get()
		val EMISSION_FAMILY = Family.all(EmissionsComponent::class.java).get()
		val SHIP_FAMILY = Family.all(ShipComponent::class.java).get()
	}

	val log = Logger.getLogger(this.javaClass)
	private val galaxy = GameServices[Galaxy::class.java]

	private val movementMapper = ComponentMapper.getFor(TimedMovementComponent::class.java)
	private val sensorsMapper = ComponentMapper.getFor(PassiveSensorsComponent::class.java)
	private val emissionsMapper = ComponentMapper.getFor(EmissionsComponent::class.java)
	private val detectionMapper = ComponentMapper.getFor(DetectionComponent::class.java)
	private val ownerMapper = ComponentMapper.getFor(OwnerComponent::class.java)
	private val shipMapper = ComponentMapper.getFor(ShipComponent::class.java)
	private val uuidMapper = ComponentMapper.getFor(UUIDComponent::class.java)

	override fun addedToEngine(engine: Engine) {
		super.addedToEngine(engine)
		engine.addEntityListener(SHIP_FAMILY, this)

		engine.addEntityListener(EMISSION_FAMILY, object : EntityListener {
			override fun entityRemoved(entity: Entity) {
				emissionFamilyChanged = true
			}

			override fun entityAdded(entity: Entity) {
				emissionFamilyChanged = true
			}
		})
	}

	var emitters: List<Emitter> = emptyList()
	var emissionFamilyChanged = true

	override fun entityAdded(entity: Entity) {
		var sensorComponent = sensorsMapper.get(entity)

		if (sensorComponent == null) {

			val ship = shipMapper.get(entity)
			val sensors = ship.shipClass[PassiveSensor::class]

			if (sensors.isNotEmpty()) {
				sensorComponent = PassiveSensorsComponent(sensors)
				entity.add(sensorComponent)

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

	override fun entityRemoved(entity: Entity) {
	}

	override fun update(deltaTime: Float) {

		if (emissionFamilyChanged) {
			emissionFamilyChanged = false

			val emissionEntities = engine.getEntitiesFor(EMISSION_FAMILY)

			if (emissionEntities.size() == 0) {

				emitters = emptyList()

			} else {

				val tempEmitters = ArrayList<Emitter>(emissionEntities.size())

				for (entity in emissionEntities) {
					var emissions = emissionsMapper.get(entity)
					var position = movementMapper.get(entity).get(galaxy.time).value.position

					tempEmitters.add(Emitter(entity, position, emissions))
				}

				emitters = tempEmitters
			}
		}

		super.update(deltaTime)
	}

	override fun processEntity(entity: Entity, deltaGameTime: Float) {

		val ship = shipMapper.get(entity)
		val movement = movementMapper.get(entity)
		val sensorPosition = movement.get(galaxy.time).value.position
		val owner = ownerMapper.get(entity)

		val sensors = sensorsMapper.get(entity).sensors
		var detectionComponent = detectionMapper.get(entity)

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

					if (emitter.entity == entity) {
						continue
					}

					if (owner != null && ownerMapper.has(emitter.entity) && owner.empire == ownerMapper.get(emitter.entity).empire) {
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

								if (shipMapper.has(emitter.entity)) {
									val hash = 37 * shipMapper.get(emitter.entity).shipClass.hashCode() + sensor.hashCode()
//									println("hash $hash, uuid ${uuidMapper.get(emitter.entity).uuid.dispersedHash}, sensor ${sensor.hashCode()}")
									temp.rotate((hash % 360).toFloat())

								} else if (uuidMapper.has(emitter.entity)) {
									val hash = 37 * uuidMapper.get(emitter.entity).uuid.dispersedHash + sensor.hashCode()
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
								detectionHit = DetectionHit(0.0, ArrayList<Entity>(), ArrayList<Vector2L>())
								distanceSteps[distanceStep] = detectionHit
							}

							detectionHit.signalStrength += signalStrength
							detectionHit.entities.add(emitter.entity)
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
