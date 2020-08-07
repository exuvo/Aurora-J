package se.exuvo.aurora.starsystems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.ComponentType
import com.artemis.EntitySubscription
import com.artemis.ShadowWorld
import com.artemis.World
import com.artemis.WorldConfigurationBuilder
import com.badlogic.gdx.utils.Disposable
import se.exuvo.aurora.empires.components.ColonyComponent
import se.exuvo.aurora.starsystems.components.CircleComponent
import se.exuvo.aurora.starsystems.components.EmissionsComponent
import se.exuvo.aurora.starsystems.components.EntityReference
import se.exuvo.aurora.starsystems.components.EntityUUID
import se.exuvo.aurora.starsystems.components.MassComponent
import se.exuvo.aurora.starsystems.components.MoveToEntityComponent
import se.exuvo.aurora.starsystems.components.NameComponent
import se.exuvo.aurora.starsystems.components.OrbitComponent
import se.exuvo.aurora.starsystems.components.OwnerComponent
import se.exuvo.aurora.starsystems.components.RenderComponent
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.components.SolarIrradianceComponent
import se.exuvo.aurora.starsystems.components.StarSystemComponent
import se.exuvo.aurora.starsystems.components.StrategicIconComponent
import se.exuvo.aurora.starsystems.components.SunComponent
import se.exuvo.aurora.starsystems.components.TimedMovementComponent
import se.exuvo.aurora.starsystems.components.TintComponent
import se.exuvo.aurora.starsystems.components.UUIDComponent
import se.exuvo.aurora.utils.forEachFast

// World with no systems only entities
class ShadowStarSystem(val system: StarSystem) : Disposable {
	val world: ShadowWorld
	var time = 0L
		set(newTime) {
			field = newTime
			day = (time / (24L * 60L * 60L)).toInt()
		}
	var day = 0
	
	lateinit var solarSystemMapper: ComponentMapper<StarSystemComponent>
	lateinit var uuidMapper: ComponentMapper<UUIDComponent>
	lateinit var timedMovementMapper: ComponentMapper<TimedMovementComponent>
	lateinit var renderMapper: ComponentMapper<RenderComponent>
	lateinit var circleMapper: ComponentMapper<CircleComponent>
	lateinit var massMapper: ComponentMapper<MassComponent>
	lateinit var nameMapper: ComponentMapper<NameComponent>
	lateinit var orbitMapper: ComponentMapper<OrbitComponent>
	lateinit var sunMapper: ComponentMapper<SunComponent>
	lateinit var solarIrradianceMapper: ComponentMapper<SolarIrradianceComponent>
	lateinit var tintMapper: ComponentMapper<TintComponent>
	lateinit var strategicIconMapper: ComponentMapper<StrategicIconComponent>
	lateinit var emissionsMapper: ComponentMapper<EmissionsComponent>
	lateinit var moveToEntityMapper: ComponentMapper<MoveToEntityComponent>
	lateinit var shipMapper: ComponentMapper<ShipComponent>
	lateinit var colonyMapper: ComponentMapper<ColonyComponent>
	lateinit var ownerMapper: ComponentMapper<OwnerComponent>
	
	val allSubscription: EntitySubscription
	val uuidSubscription: EntitySubscription
	
	init {
		val worldBuilder = WorldConfigurationBuilder()
		val worldConfig = worldBuilder.build()
		world = ShadowWorld(worldConfig)
		
		// copy system component order
		system.world.componentManager.componentTypes.forEachFast { type: ComponentType ->
			world.getMapper(type.type)
		}
		
		world.inject(this)
		
		allSubscription = world.getAspectSubscriptionManager().get(Aspect.all())
		uuidSubscription = world.getAspectSubscriptionManager().get(StarSystem.UUID_ASPECT)
	}
	
	fun update() {
		
		allSubscription.entities.forEachFast { entityID ->
			world.delete(entityID)
		}
		
		world.process()
		world.entityManager.reset() // produces a lot of garbage
		world.process()
		
		system.allSubscription.entities.forEachFast { entityID ->
			//TODO create entity with specific ID
		}
		
		world.process()
	}
	
	fun updateEntityReference(entityID: Int, entityReference: EntityReference): EntityReference {
		
		return entityReference.set(system, entityID, uuidMapper.get(entityID).uuid)
	}
	
	fun isEntityReferenceValid(entityReference: EntityReference): Boolean {
		
		if (entityReference.system != system || !world.getEntityManager().isActive(entityReference.entityID)) {
			return false
		}
		
		val uuid = uuidMapper.get(entityReference.entityID).uuid
		
		return entityReference.entityUUID.hashCode() == uuid.hashCode()
	}
	
	fun getEntityByUUID(entityUUID: EntityUUID): Int? {
		
		uuidSubscription.getEntities().forEachFast { entityID ->
			val uuid = uuidMapper.get(entityID).uuid
			
			if (uuid.hashCode() == entityUUID.hashCode()) {
				return entityID
			}
		}
		
		return null
	}
	
	override fun dispose() {
	
	}
}