package se.exuvo.aurora.starsystems

import com.artemis.ComponentMapper
import com.artemis.ComponentType
import com.artemis.EntitySubscription
import com.artemis.World
import com.artemis.WorldConfigurationBuilder
import com.artemis.utils.Bag
import com.artemis.utils.BitVector
import com.artemis.utils.IntBag
import com.badlogic.gdx.utils.Disposable
import se.exuvo.aurora.empires.components.ActiveTargetingComputersComponent
import se.exuvo.aurora.empires.components.ColonyComponent
import se.exuvo.aurora.empires.components.IdleTargetingComputersComponent
import se.exuvo.aurora.galactic.Empire
import se.exuvo.aurora.starsystems.components.ArmorComponent
import se.exuvo.aurora.starsystems.components.CargoComponent
import se.exuvo.aurora.starsystems.components.CircleComponent
import se.exuvo.aurora.starsystems.components.CloneableComponent
import se.exuvo.aurora.starsystems.components.EmissionsComponent
import se.exuvo.aurora.starsystems.components.EntityReference
import se.exuvo.aurora.starsystems.components.EntityUUID
import se.exuvo.aurora.starsystems.components.MassComponent
import se.exuvo.aurora.starsystems.components.MoveToEntityComponent
import se.exuvo.aurora.starsystems.components.NameComponent
import se.exuvo.aurora.starsystems.components.OrbitComponent
import se.exuvo.aurora.starsystems.components.EmpireComponent
import se.exuvo.aurora.starsystems.components.HPComponent
import se.exuvo.aurora.starsystems.components.PartStatesComponent
import se.exuvo.aurora.starsystems.components.PartsHPComponent
import se.exuvo.aurora.starsystems.components.PowerComponent
import se.exuvo.aurora.starsystems.components.RenderComponent
import se.exuvo.aurora.starsystems.components.ShieldComponent
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.components.ShipOrder
import se.exuvo.aurora.starsystems.components.SolarIrradianceComponent
import se.exuvo.aurora.starsystems.components.StarSystemComponent
import se.exuvo.aurora.starsystems.components.StrategicIconComponent
import se.exuvo.aurora.starsystems.components.SunComponent
import se.exuvo.aurora.starsystems.components.TimedMovementComponent
import se.exuvo.aurora.starsystems.components.TintComponent
import se.exuvo.aurora.starsystems.components.UUIDComponent
import se.exuvo.aurora.starsystems.systems.RenderSystem
import se.exuvo.aurora.starsystems.systems.SpatialPartitioningPlanetoidsSystem
import se.exuvo.aurora.starsystems.systems.SpatialPartitioningSystem
import se.exuvo.aurora.ui.ProfilerWindow
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.utils.quadtree.QuadtreeAABB
import se.exuvo.aurora.utils.quadtree.QuadtreePoint
import uk.co.omegaprime.btreemap.LongObjectBTreeMap
import java.lang.IllegalStateException
import kotlin.reflect.full.isSuperclassOf

// World with no systems only entities
class ShadowStarSystem(val system: StarSystem) : Disposable {
	
	val world: World
	val added = BitVector()
	val changed = BitVector()
	val changedComponents = Array<BitVector>(system.world.componentManager.componentTypes.size(), { BitVector() })
	val deleted = BitVector()
	
	var time = 0L
		set(newTime) {
			field = newTime
			day = (time / (24L * 60L * 60L)).toInt()
		}
	var day = 0
	
	val empireShips = LinkedHashMap<Empire, LongObjectBTreeMap<IntBag>>()
	val empireOrders = LinkedHashMap<Empire, Bag<ShipOrder>>()
	
	val profilerEvents = ProfilerWindow.ProfilerBag()
	
	var quadtreeShipsChanged = true
	var quadtreePlanetoidsChanged = true
	val quadtreeShips = QuadtreePoint(SpatialPartitioningSystem.MAX, SpatialPartitioningSystem.MAX, SpatialPartitioningSystem.MAX_ELEMENTS, SpatialPartitioningSystem.DEPTH)
	val quadtreePlanetoids = QuadtreeAABB(SpatialPartitioningPlanetoidsSystem.MAX, SpatialPartitioningPlanetoidsSystem.MAX, SpatialPartitioningPlanetoidsSystem.MAX_ELEMENTS, SpatialPartitioningPlanetoidsSystem.DEPTH)
	
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
	lateinit var empireMapper: ComponentMapper<EmpireComponent>
	lateinit var idleTargetingComputersComponentMapper: ComponentMapper<IdleTargetingComputersComponent>
	lateinit var activeTargetingComputersComponentMapper: ComponentMapper<ActiveTargetingComputersComponent>
	lateinit var partStatesMapper: ComponentMapper<PartStatesComponent>
	lateinit var shieldMapper: ComponentMapper<ShieldComponent>
	lateinit var armorMapper: ComponentMapper<ArmorComponent>
	lateinit var partsHPMapper: ComponentMapper< PartsHPComponent>
	lateinit var hpMapper: ComponentMapper<HPComponent>
	lateinit var cargoMapper: ComponentMapper<CargoComponent>
	
//	val allSubscription: EntitySubscription
	val uuidSubscription: EntitySubscription
	
	private val mappersByTypeIndex = Bag<ComponentMapper<*>>(system.world.componentManager.componentTypes.size())
	
	private val tmpBV = BitVector()
	private val tmpBVs = Array<BitVector>(changedComponents.size, { BitVector() })
	private val tmpBag = IntBag()
	
	init {
		val worldBuilder = WorldConfigurationBuilder()
		worldBuilder.with(RenderSystem())
		
		val worldConfig = worldBuilder.build()
		worldConfig.register(system)
		worldConfig.register(this)
		world = World(worldConfig)
		
		world.inject(this)
		
		system.world.componentManager.componentTypes.forEachFast { type: ComponentType ->
			if (CloneableComponent::class.isSuperclassOf(type.type.kotlin)) {
				mappersByTypeIndex[type.index] = world.getMapper(type.type)
			}
		}
		
//		allSubscription = world.getAspectSubscriptionManager().get(Aspect.all())
		uuidSubscription = world.getAspectSubscriptionManager().get(StarSystem.UUID_ASPECT)
		
		system.world.entityManager.registerEntityStore(added)
		system.world.entityManager.registerEntityStore(changed)
		system.world.entityManager.registerEntityStore(deleted)
		system.world.entityManager.registerEntityStore(tmpBV)
		
		changedComponents.forEachFast { bitVector ->
			system.world.entityManager.registerEntityStore(bitVector)
		}
		tmpBVs.forEachFast { bitVector ->
			system.world.entityManager.registerEntityStore(bitVector)
		}
	}
	
	fun update() {
		//TODO add calls to send changes over network
		
		// Skip added and deleted in same tick
		tmpBV.set(added)
		tmpBV.and(deleted)
		added.andNot(tmpBV)
		changed.andNot(tmpBV)
		deleted.andNot(tmpBV)
		
		changed.andNot(added) // Skip created and modified in same tick
		
		profilerEvents.start("deleted")
		tmpBV.set(deleted)
		tmpBV.or(system.shadow.deleted)
		tmpBV.toIntBag(tmpBag)
//		println("deleted $tmpBag")
		tmpBag.forEachFast { entityID ->
			world.delete(entityID)
		}
		profilerEvents.end()
		
		profilerEvents.start("process")
		world.process()
		profilerEvents.end()
		
		val em = world.entityManager
		val scm = system.world.componentManager
		
		tmpBV.set(changed)
		tmpBV.or(system.shadow.changed)
		tmpBV.andNot(system.shadow.added) // Skip other added as they will be handled as added below
		tmpBV.andNot(deleted) // Skip now deleted from other changed
		tmpBV.andNot(system.shadow.deleted) // Skip other deleted from other changed
		
		tmpBV.toIntBag(tmpBag)
		
		tmpBVs.forEachFast { index, bitVector ->
			bitVector.set(changedComponents[index])
			bitVector.or(system.shadow.changedComponents[index])
		}
		
//		println("changed $tmpBag")
		profilerEvents.start("changed")
		tmpBag.forEachFast { entityID ->
			profilerEvents.start("$entityID")
			if (!em.isActive(entityID)) {
				throw IllegalStateException("entity id $entityID does not exist")
			}
			
			val systemMappers = scm.componentMappers(entityID) // Only includes current components
			
			systemMappers?.forEachFast { systemMapper ->
				val typeIndex = systemMapper.type.index
				
				if (tmpBVs[typeIndex].unsafeGet(entityID)) {
					tmpBVs[typeIndex].unsafeClear(entityID)
					
					val shadowMapper = mappersByTypeIndex[typeIndex]
					
					if (shadowMapper != null) {
						profilerEvents.start("copy ${systemMapper.type.type.simpleName}")
						var systemComponent = systemMapper.get(entityID) as CloneableComponent<*>
						val shadowComponent = shadowMapper.create(entityID)
						systemComponent.copy2(shadowComponent)
						profilerEvents.end()
					}
				}
			}
			
			// Removed components
			tmpBVs.forEachFast { index, bitVector ->
				if (bitVector.unsafeGet(entityID)) {
					mappersByTypeIndex[index]?.remove(entityID)
				}
			}
			profilerEvents.end()
		}
		profilerEvents.end()
		
		tmpBV.set(added)
		tmpBV.or(system.shadow.added)
		tmpBV.andNot(deleted) // Skip other added that are now deleted
		
		profilerEvents.start("added")
		tmpBV.toIntBag(tmpBag)
//		println("added $tmpBag")
		tmpBag.forEachFast { entityID ->
			profilerEvents.start("$entityID")
			
			world.createSpecific(entityID)
			
			val systemMappers = scm.componentMappers(entityID)
			
			systemMappers?.forEachFast { systemMapper ->
				val shadowMapper = mappersByTypeIndex[systemMapper.type.index]
				
				if (shadowMapper != null) {
					profilerEvents.start("copy ${systemMapper.type.type.simpleName}")
					var systemComponent = systemMapper.get(entityID) as CloneableComponent<*>
					val shadowComponent = shadowMapper.create(entityID)
					systemComponent.copy2(shadowComponent)
					profilerEvents.end()
				}
			}
			profilerEvents.end()
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
						val shadowShipIDs = IntBag(maxOf(ships.size, 64))
						shadowShipIDs.addAll(shipIDs)
						shadowShips[mass] = shadowShipIDs
					}
					
					empireShips[empire] = shadowShips
				}
			}
		}
		profilerEvents.end()
		
		profilerEvents.start("shadowOrders")
		val empireOrdersIterator = empireOrders.iterator()
		while (empireOrdersIterator.hasNext()) {
			val (empire, shadowOrders) = empireOrdersIterator.next()
			val orders = system.empireOrders[empire]
			
			if (orders == null) {
				empireOrdersIterator.remove()
				
			} else {
				shadowOrders.set(orders)
			}
		}
		
		if (empireOrders.size != system.empireOrders.size) {
			system.empireOrders.forEach { (empire, orders) ->
				if (empireOrders[empire] == null) {
					val shadowOrders = Bag<ShipOrder>(maxOf(orders.size(), 64))
					shadowOrders.set(orders)
					empireOrders[empire] = shadowOrders
				}
			}
		}
		profilerEvents.end()
		
		if (quadtreeShipsChanged || system.shadow.quadtreeShipsChanged) {
			profilerEvents.start("copy quadtree ships")
			quadtreeShips.copy(system.spatialPartitioningSystem.tree)
			profilerEvents.end()
		}
		
		if (quadtreePlanetoidsChanged || system.shadow.quadtreePlanetoidsChanged) {
			profilerEvents.start("copy quadtree planetoids")
			quadtreePlanetoids.copy(system.spatialPartitioningPlanetoidsSystem.tree)
			profilerEvents.end()
		}
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
		
		if (entityReference.system.shadow.isEntityReferenceValid(entityReference)) {
			return entityReference
		}
		
		return getEntityReferenceByUUID(entityReference.entityUUID, entityReference)
	}
	
	fun getEntityReferenceByUUID(entityUUID: EntityUUID, oldEntityReference: EntityReference? = null): EntityReference? {
		
		system.galaxy.systems.forEachFast{ system ->
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