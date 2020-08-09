package se.exuvo.aurora.galactic

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.ComponentType
import com.artemis.EntitySubscription
import com.artemis.World
import com.artemis.WorldConfigurationBuilder
import com.artemis.utils.Bag
import com.artemis.utils.BitVector
import com.artemis.utils.IntBag
import com.badlogic.gdx.utils.Disposable
import se.exuvo.aurora.empires.components.ColonyComponent
import se.exuvo.aurora.starsystems.StarSystem
import se.exuvo.aurora.starsystems.components.CircleComponent
import se.exuvo.aurora.starsystems.components.CloneableComponent
import se.exuvo.aurora.starsystems.components.EmissionsComponent
import se.exuvo.aurora.starsystems.components.EntityReference
import se.exuvo.aurora.starsystems.components.EntityUUID
import se.exuvo.aurora.starsystems.components.GalacticPositionComponent
import se.exuvo.aurora.starsystems.components.MassComponent
import se.exuvo.aurora.starsystems.components.MoveToEntityComponent
import se.exuvo.aurora.starsystems.components.NameComponent
import se.exuvo.aurora.starsystems.components.OrbitComponent
import se.exuvo.aurora.starsystems.components.OwnerComponent
import se.exuvo.aurora.starsystems.components.StarSystemComponent
import se.exuvo.aurora.starsystems.components.RenderComponent
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.components.SolarIrradianceComponent
import se.exuvo.aurora.starsystems.components.Spectrum
import se.exuvo.aurora.starsystems.components.StrategicIconComponent
import se.exuvo.aurora.starsystems.components.SunComponent
import se.exuvo.aurora.starsystems.components.TimedMovementComponent
import se.exuvo.aurora.starsystems.components.TintComponent
import se.exuvo.aurora.starsystems.components.UUIDComponent
import se.exuvo.aurora.ui.ProfilerWindow
import se.exuvo.aurora.utils.forEachFast
import kotlin.reflect.full.isSuperclassOf

// World with no systems only entities
class ShadowGalaxy(val galaxy: Galaxy) : Disposable {
	val world: World
	val added = BitVector()
	val changed = BitVector()
	val deleted = BitVector()
	
	var time = 0L
		set(newTime) {
			field = newTime
			day = (time / (24L * 60L * 60L)).toInt()
		}
	var day = 0
	
	val profilerEvents = ProfilerWindow.ProfilerBag()
	
	lateinit var positionMapper: ComponentMapper<GalacticPositionComponent>
	lateinit var uuidMapper: ComponentMapper<UUIDComponent>
	lateinit var renderMapper: ComponentMapper<RenderComponent>
	lateinit var nameMapper: ComponentMapper<NameComponent>
	lateinit var strategicIconMapper: ComponentMapper<StrategicIconComponent>
	lateinit var shipMapper: ComponentMapper<ShipComponent>
	lateinit var ownerMapper: ComponentMapper<OwnerComponent>
	
//	val allSubscription: EntitySubscription
	
	private val mappersByTypeIndex = Bag<ComponentMapper<*>>(galaxy.world.componentManager.componentTypes.size())
	
	private val tmpBV = BitVector()
	private val tmpBag = IntBag()
	
	init {
		val worldBuilder = WorldConfigurationBuilder()
		worldBuilder.register()
		
		val worldConfig = worldBuilder.build()
		world = World(worldConfig)
		
		world.inject(this)
		
		galaxy.world.componentManager.componentTypes.forEachFast { type: ComponentType ->
			if (CloneableComponent::class.isSuperclassOf(type.type.kotlin)) {
				mappersByTypeIndex[type.index] = world.getMapper(type.type)
			}
		}
		
		
//		allSubscription = world.getAspectSubscriptionManager().get(Aspect.all())
		
		galaxy.world.entityManager.registerEntityStore(added)
		galaxy.world.entityManager.registerEntityStore(changed)
		galaxy.world.entityManager.registerEntityStore(deleted)
	}
	
	fun update() {
		tmpBV.ensureCapacity(added.length())
		
		profilerEvents.start("deleted")
		tmpBV.clear()
		tmpBV.or(deleted)
		tmpBV.or(galaxy.shadow.deleted)
		tmpBV.toIntBag(tmpBag)
		tmpBag.forEachFast { entityID ->
			world.delete(entityID)
		}
		profilerEvents.end()
		
		profilerEvents.start("process")
		world.process()
		profilerEvents.end()
		
		val em = world.entityManager
		val scm = galaxy.world.componentManager
		
		changed.andNot(added) // BatchProcessor component changes includes added too. Also skip created and modified in same tick
		
		tmpBV.clear()
		tmpBV.or(changed)
		tmpBV.andNot(galaxy.shadow.added) // Skip otherShadow added as they will be handled as added below
		tmpBV.or(galaxy.shadow.changed)
		tmpBV.andNot(deleted) // Skip now deleted from otherShadow changed
		tmpBV.andNot(galaxy.shadow.deleted)
		
		profilerEvents.start("changed")
		tmpBV.toIntBag(tmpBag)
		tmpBag.forEachFast { entityID ->
			if (!em.isActive(entityID)) {
				throw IllegalStateException("entity id $entityID does not exist")
			}
			
			val systemMappers = scm.componentMappers(entityID)
			
			systemMappers?.forEachFast { systemMapper ->
				val shadowMapper = mappersByTypeIndex[systemMapper.type.index]
				
				if (shadowMapper != null) {
					var systemComponent = systemMapper.get(entityID) as CloneableComponent<*>
					val shadowComponent = shadowMapper.create(entityID)
					systemComponent.copy2(shadowComponent)
				}
			}
		}
		profilerEvents.end()
		
		tmpBV.clear()
		tmpBV.or(added)
		tmpBV.or(galaxy.shadow.added)
		
		profilerEvents.start("added")
		tmpBV.toIntBag(tmpBag)
		tmpBag.forEachFast { entityID ->
			em.setNextID(entityID)
			val newID = world.create()
			if (newID != entityID) {
				throw IllegalStateException("wrong entity id created $newID != $entityID")
			}
			
			val systemMappers = scm.componentMappers(entityID)
			
			systemMappers?.forEachFast { systemMapper ->
				val shadowMapper = mappersByTypeIndex[systemMapper.type.index]
				
				if (shadowMapper != null) {
					var systemComponent = systemMapper.get(entityID) as CloneableComponent<*>
					val shadowComponent = shadowMapper.create(entityID)
					systemComponent.copy2(shadowComponent)
				}
			}
		}
		profilerEvents.end()
		
		profilerEvents.start("process")
		world.process()
		profilerEvents.end()
	}
	
	fun resolveEntityReference(entityReference: EntityReference): EntityReference? {
		
		if (entityReference.system.shadow.isEntityReferenceValid(entityReference)) {
			return entityReference
		}
		
		return getEntityReferenceByUUID(entityReference.entityUUID, entityReference)
	}
	
	fun getEntityReferenceByUUID(entityUUID: EntityUUID, oldEntityReference: EntityReference? = null): EntityReference? {
		
		galaxy.systems.forEachFast{ system ->
			val entityID = system.shadow.getEntityByUUID(entityUUID)
			
			if (entityID != null) {
				if (oldEntityReference != null) {
					return system.shadow.updateEntityReference(entityID, oldEntityReference)
				} else {
					return system.shadow.getEntityReference(entityID)
				}
			}
		}
		
		return null
	}
	
	override fun dispose() {
	
	}
}