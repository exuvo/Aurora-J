package se.exuvo.aurora.planetarysystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.EntitySubscription
import com.artemis.EntitySubscription.SubscriptionListener
import com.artemis.systems.IteratingSystem
import com.artemis.utils.IntBag
import com.badlogic.gdx.graphics.g3d.particles.emitters.Emitter
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
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Vector2L

/* mass pushes down on map over time
 * moving mass then makes trail but of smaller amount than if stood still
 * hyperspace openings makes instant large push
 * natural mass (planets) makes discerning small masses nearby difficult
 *
**/
class GravimetricSensorSystem : IteratingSystem(FAMILY) {
	companion object {
		val FAMILY = Aspect.all(PassiveSensorsComponent::class.java)
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

	override fun initialize() {
		emissionsSubscription = world.getAspectSubscriptionManager().get(EMISSION_FAMILY)
		emissionsSubscription.addSubscriptionListener(object: SubscriptionListener {
			override fun inserted(entities: IntBag) {
				emissionFamilyChanged = true
			}

			override fun removed(entities: IntBag) {
				emissionFamilyChanged = true
			}
		})
	}

	var emitters: List<Emitter> = emptyList()
	var emissionFamilyChanged = true

	override fun inserted(entityID: Int) {
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

	override fun begin() {

	}

	override fun process(entityID: Int) {
	}
}
