package se.exuvo.aurora.starsystems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.ComponentType
import com.artemis.EntitySubscription
import com.artemis.World
import com.artemis.WorldConfigurationBuilder
import com.artemis.utils.Bag
import com.artemis.utils.IntBag
import com.badlogic.gdx.utils.Disposable
import se.exuvo.aurora.empires.components.ActiveTargetingComputersComponent
import se.exuvo.aurora.empires.components.ColonyComponent
import se.exuvo.aurora.empires.components.IdleTargetingComputersComponent
import se.exuvo.aurora.galactic.Empire
import se.exuvo.aurora.starsystems.components.CircleComponent
import se.exuvo.aurora.starsystems.components.CloneableComponent
import se.exuvo.aurora.starsystems.components.EmissionsComponent
import se.exuvo.aurora.starsystems.components.EntityReference
import se.exuvo.aurora.starsystems.components.EntityUUID
import se.exuvo.aurora.starsystems.components.MassComponent
import se.exuvo.aurora.starsystems.components.MoveToEntityComponent
import se.exuvo.aurora.starsystems.components.NameComponent
import se.exuvo.aurora.starsystems.components.OrbitComponent
import se.exuvo.aurora.starsystems.components.OwnerComponent
import se.exuvo.aurora.starsystems.components.PowerComponent
import se.exuvo.aurora.starsystems.components.RenderComponent
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.components.SolarIrradianceComponent
import se.exuvo.aurora.starsystems.components.StarSystemComponent
import se.exuvo.aurora.starsystems.components.StrategicIconComponent
import se.exuvo.aurora.starsystems.components.SunComponent
import se.exuvo.aurora.starsystems.components.TimedMovementComponent
import se.exuvo.aurora.starsystems.components.TintComponent
import se.exuvo.aurora.starsystems.components.UUIDComponent
import se.exuvo.aurora.starsystems.systems.RenderSystem
import se.exuvo.aurora.ui.ProfilerWindow
import se.exuvo.aurora.utils.forEachFast
import uk.co.omegaprime.btreemap.LongObjectBTreeMap
import java.lang.IllegalStateException
import kotlin.reflect.full.isSuperclassOf

// World with no systems only entities
class ShadowStarSystem(val system: StarSystem) : Disposable {
	
	val world: World
	
	var time = 0L
		set(newTime) {
			field = newTime
			day = (time / (24L * 60L * 60L)).toInt()
		}
	var day = 0
	
	val empireShips = LinkedHashMap<Empire, LongObjectBTreeMap<IntBag>>()
	val profilerEvents = ProfilerWindow.ProfilerBag()
	
	lateinit var starSystemMapper: ComponentMapper<StarSystemComponent>
	lateinit var uuidMapper: ComponentMapper<UUIDComponent>
	lateinit var movementMapper: ComponentMapper<TimedMovementComponent>
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
	lateinit var powerMapper: ComponentMapper<PowerComponent>
	lateinit var colonyMapper: ComponentMapper<ColonyComponent>
	lateinit var ownerMapper: ComponentMapper<OwnerComponent>
	lateinit var idleTargetingComputersComponentMapper: ComponentMapper<IdleTargetingComputersComponent>
	lateinit var activeTargetingComputersComponentMapper: ComponentMapper<ActiveTargetingComputersComponent>
	
	val allSubscription: EntitySubscription
	val uuidSubscription: EntitySubscription
	
	val mapperPairs = Bag<MapperPair>(system.world.componentManager.componentTypes.size())
	data class MapperPair(val systemMapper: ComponentMapper<*>, val shadowMapper: ComponentMapper<*>)
	
	init {
		val worldBuilder = WorldConfigurationBuilder()
		worldBuilder.with(RenderSystem())
		
		val worldConfig = worldBuilder.build()
		worldConfig.register(system)
		worldConfig.register(this)
		world = World(worldConfig)
		
		world.inject(this)
		
		system.world.componentManager.componentTypes.forEachFast { type: ComponentType ->
			val classType = type.type
			if (CloneableComponent::class.isSuperclassOf(classType.kotlin)) {
				mapperPairs.add(MapperPair(system.world.getMapper(classType), world.getMapper(classType)))
			}
		}
		
		allSubscription = world.getAspectSubscriptionManager().get(Aspect.all())
		uuidSubscription = world.getAspectSubscriptionManager().get(StarSystem.UUID_ASPECT)
	}
	
	fun update() {
		//TODO only delete deleted entities. use BitVector's for removed, added, changed
		profilerEvents.start("clear entites")
		allSubscription.entities.forEachFast { entityID ->
			world.delete(entityID)
		}
		profilerEvents.end()
		
		profilerEvents.start("process")
		world.process()
		profilerEvents.end()
		val em = world.entityManager
		
		profilerEvents.start("copy")
		system.allSubscription.entities.forEachFast { entityID ->
			if (!em.isActive(entityID)) {
				em.setNextID(entityID)
				val newID = world.create()
				if (newID != entityID) {
					throw IllegalStateException("wrong entity id created $newID != $entityID")
				}
			}
			
			//TODO use ComponentManager.componentMappers(int entityId) to know which mappers to use. replace mapperPairs with map
			mapperPairs.forEachFast { pair ->
				var existingComponent = pair.systemMapper.get(entityID) as CloneableComponent<*>?
				
				if (existingComponent != null) {
					val newComponent = pair.shadowMapper.create(entityID)
					existingComponent.copy2(newComponent)
				}
			}
		}
		profilerEvents.end()
		
		profilerEvents.start("process")
		world.process()
		profilerEvents.end()
		
		profilerEvents.start("empireShips")
		val empireShipsIterator = empireShips.iterator()
		while (empireShipsIterator.hasNext()) {
			val (empire, shadowShips) = empireShipsIterator.next()
			val ships = system.empireShips[empire]
			
			if (ships == null) {
				empireShipsIterator.remove()
				
			} else if (ships.hashCode() != shadowShips.hashCode()) {
				shadowShips.clear()
				
				ships.forEach { (mass, shipIDs) ->
					val shadowShipIDs = IntBag(ships.size)
					shadowShipIDs.addAll(shipIDs)
					shadowShips[mass] = shadowShipIDs
				}
			}
		}
		
		if (empireShips.size != system.empireShips.size) {
			system.empireShips.forEach { (empire, ships) ->
				if (empireShips[empire] == null) {
					val shadowShips = LongObjectBTreeMap.create<IntBag>()!!
					
					ships.forEach { (mass, shipIDs) ->
						val shadowShipIDs = IntBag(ships.size)
						shadowShipIDs.addAll(shipIDs)
						shadowShips[mass] = shadowShipIDs
					}
					
					empireShips[empire] = shadowShips
				}
			}
		}
		profilerEvents.end()
	}
	
	fun getEntityReference(entityID: Int): EntityReference {
		
		return EntityReference().set(system, entityID, uuidMapper.get(entityID).uuid)
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
	
	fun resolveEntityReference(entityReference: EntityReference): EntityReference? {
		
		if (entityReference.system.uiShadow.isEntityReferenceValid(entityReference)) {
			return entityReference
		}
		
		return getEntityReferenceByUUID(entityReference.entityUUID, entityReference)
	}
	
	fun getEntityReferenceByUUID(entityUUID: EntityUUID, oldEntityReference: EntityReference? = null): EntityReference? {
		
		system.galaxy.systems.forEachFast{ system ->
			val entityID = system.uiShadow.getEntityByUUID(entityUUID)
			
			if (entityID != null) {
				if (oldEntityReference != null) {
					return system.uiShadow.updateEntityReference(entityID, oldEntityReference)
				} else {
					return system.uiShadow.getEntityReference(entityID)
				}
			}
		}
		
		return null
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