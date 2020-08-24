package se.exuvo.aurora.starsystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.starsystems.StarSystem
import com.artemis.annotations.Wire
import com.artemis.BaseEntitySystem
import java.util.PriorityQueue
import com.artemis.EntitySubscription
import se.exuvo.aurora.starsystems.components.TimedMovementComponent
import com.artemis.World
import com.artemis.utils.IntBag
import com.badlogic.gdx.math.Vector2
import glm_.pow
import net.mostlyoriginal.api.event.common.Subscribe
import se.exuvo.aurora.galactic.SimpleMunitionHull
import se.exuvo.aurora.starsystems.components.CircleComponent
import se.exuvo.aurora.starsystems.components.LaserShotComponent
import se.exuvo.aurora.starsystems.components.MissileComponent
import se.exuvo.aurora.starsystems.components.MovementValues
import se.exuvo.aurora.starsystems.components.RailgunShotComponent
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.components.SpatialPartitioningComponent
import se.exuvo.aurora.starsystems.events.NonLinearMovementEvent
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.utils.quadtree.QuadtreeAABB
import se.exuvo.aurora.utils.quadtree.QuadtreeAABBStatic
import se.exuvo.aurora.utils.quadtree.QuadtreePoint
import kotlin.math.roundToInt

class SpatialPartitioningSystem : BaseEntitySystem(ASPECT) {
	companion object {
		@JvmField val ASPECT = Aspect.all(TimedMovementComponent::class.java).one(ShipComponent::class.java, RailgunShotComponent::class.java, LaserShotComponent::class.java, MissileComponent::class.java)
		
		@JvmField val log = LogManager.getLogger(SpatialPartitioningSystem::class.java)
		
		const val SCALE: Int = 2_000 // in m , min 1000
		const val MAX: Int = Int.MAX_VALUE
		const val DESIRED_MIN_SQUARE_SIZE: Long = 100_000_000 // in m
		@JvmField val RAW_DEPTH: Double = Math.log(SCALE * MAX.toDouble() / DESIRED_MIN_SQUARE_SIZE) / Math.log(2.0)
		@JvmField val DEPTH: Int = RAW_DEPTH.roundToInt()
		@JvmField val MIN_SQUARE_SIZE = (SCALE * MAX.toLong()) / 2.pow(DEPTH)
		const val MAX_ELEMENTS: Int = 8
		/*
			square_size = SCALE * MAX / 2.pow(DEPTH)
			2.pow(DEPTH) = SCALE * MAX / sq
			log 2.pow(DEPTH) = log (SCALE * MAX / sq)
			DEPTH * log 2 = log (SCALE * MAX / sq)
			DEPTH * log 2 = log scale + log MAX - log sq
			DEPTH = log (SCALE * MAX / sq) / log 2
			DEPTH = log2 (SCALE * MAX / sq)
		 */
		
		@JvmStatic fun query(tree: QuadtreePoint, pos1: Vector2L, pos2: Vector2L): IntBag = query(tree, pos1.x, pos1.y, pos2.x, pos2.y)
		
		@JvmStatic fun query(tree: QuadtreePoint, x1: Long, y1: Long, x2: Long, y2: Long): IntBag {
			return tree.query((x1 / SCALE + MAX/2).toInt(), (y1 / SCALE + MAX/2).toInt(), (x2 / SCALE + MAX/2).toInt(), (y2 / SCALE + MAX/2).toInt())
		}
	}

	lateinit var canAccelerateAspect: Aspect
	
	private val galaxy = GameServices[Galaxy::class]

	@Wire
	lateinit private var system: StarSystem

	lateinit private var movementMapper: ComponentMapper<TimedMovementComponent>
	lateinit private var spatialPartitioningMapper: ComponentMapper<SpatialPartitioningComponent>

	val tree = QuadtreePoint(MAX, MAX, MAX_ELEMENTS, DEPTH)
	
	private var updateQueue = PriorityQueue<Int>(Comparator<Int> { a, b ->
		val partitioningA = spatialPartitioningMapper.get(a)
		val partitioningB = spatialPartitioningMapper.get(b)
		
		val timeA = partitioningA.nextExpectedUpdate
		val timeB = partitioningB.nextExpectedUpdate
		
		timeA.compareTo(timeB)
	})
	
	override fun setWorld(world: World) {
		super.setWorld(world)
		
//		println("RAW_DEPTH $RAW_DEPTH, DEPTH $DEPTH, MIN_SQUARE_SIZE $MIN_SQUARE_SIZE, total size ${SCALE * MAX.toLong()}m ${(SCALE * MAX.toLong()) / (Units.AU * 1000)} AU")

		val canAccelerateSubscription = world.getAspectSubscriptionManager().get(MovementSystem.CAN_ACCELERATE_FAMILY)
		canAccelerateAspect = canAccelerateSubscription.aspect
		
		canAccelerateSubscription.addSubscriptionListener(object : EntitySubscription.SubscriptionListener{
			override fun inserted(entities: IntBag) {
				entities.forEachFast { entityID ->
					val partitioning = spatialPartitioningMapper.get(entityID)
					
					if (partitioning != null && partitioning.nextExpectedUpdate == -1L) {
//						println("accelerate inserted entityID $entityID")
						update(entityID)
					}
				}
			}
			
			override fun removed(entities: IntBag) {
			
			}
		})
	}
	
	override fun inserted(entityID: Int): Unit {
//		println("inserted $entityID")
		update(entityID)
	}
	
	fun update(entityID: Int) {
		val movement = movementMapper.get(entityID).get(galaxy.time).value
		val nextExpectedUpdate = updateNextExpectedUpdate(entityID, movement)
		
		val partitioning = spatialPartitioningMapper.create(entityID)
		partitioning.nextExpectedUpdate = nextExpectedUpdate
		system.changed(entityID, spatialPartitioningMapper)
		
		if (nextExpectedUpdate != -1L) {
			updateQueue.add(entityID)
		}
		
		// in Mm
		val x = movement.position.x / SCALE + MAX/2
		val y = movement.position.y / SCALE + MAX/2
		
//		println("insert at $x $y ${movement.getXinKM()} ${movement.getYinKM()}")
		
		val profilerEvents = system.workingShadow.profilerEvents
		
		if (partitioning.elementID != -1) {
			profilerEvents.start("remove")
			tree.remove(partitioning.elementID)
			profilerEvents.end()
		}
		profilerEvents.start("insert")
		partitioning.elementID = tree.insert(entityID, x.toInt(), y.toInt())
		system.workingShadow.quadtreeShipsChanged = true
		profilerEvents.end()
	}
	
	override fun removed(entityID: Int): Unit {
//		println("removed $entityID")
		updateQueue.remove(entityID)
		
		val partitioning = spatialPartitioningMapper.get(entityID)
		tree.remove(partitioning.elementID)
		system.workingShadow.quadtreeShipsChanged = true
	}
	
	private fun updateNextExpectedUpdate(entityID: Int, movement: MovementValues): Long {
		var nextExpectedUpdate = galaxy.time
		
		if (!movement.velocity.isZero) {
		
			//TODO val distance = distance from edge of smallest quadtree square
			
////			// at^2 + vt = distance
//			val a: Double = movement.acceleration.len()
//			val b: Double = movement.velocity.len()
//			val c: Double = -0.001 * Units.AU * 1000
//
//			val t: Double
//
//			if (a == 0.0) {
//				t = -c / b
//			} else {
//				t = WeaponSystem.getPositiveRootOfQuadraticEquation(a, b, c)
//			}
//
//			println("entityID $entityID: t $t a $a b $b c $c")
//
//			nextExpectedUpdate += maxOf(1, t.toLong())
			nextExpectedUpdate += 10

		} else if(canAccelerateAspect.isInterested(entityID)) {
			nextExpectedUpdate += 60

		} else {
			nextExpectedUpdate = -1
		}
		
		if (nextExpectedUpdate == -1L) {
//			println("entityID $entityID: nextExpectedUpdate $nextExpectedUpdate")
		} else {
//			println("entityID $entityID: nextExpectedUpdate +${nextExpectedUpdate - galaxy.time}")
		}
		
		return nextExpectedUpdate
	}
	
	@Subscribe
	fun nonLinearMovementEvent(event: NonLinearMovementEvent) {
		if (subscription.aspect.isInterested(event.entityID)) {
			update(event.entityID)
		}
	}
	
	override fun processSystem() {
		
		val profilerEvents = system.workingShadow.profilerEvents
		
		while(true) {
			val entityID = updateQueue.peek()
			
			if (entityID != null) {
				
				val partitioning = spatialPartitioningMapper.get(entityID)
				
//				println("eval $entityID ${partitioning.nextExpectedUpdate}")
				
				if (galaxy.time >= partitioning.nextExpectedUpdate) {
				
//					println("process $entityID ${partitioning.nextExpectedUpdate}")
					
					updateQueue.poll()
					
					profilerEvents.start("update $entityID")
					update(entityID)
					profilerEvents.end()
					
				} else {
					break;
				}
				
			} else {
				break
			}
		}
		
		profilerEvents.start("cleanup")
		if (tree.cleanupFull()) {
			system.workingShadow.quadtreeShipsChanged = true
		}
		profilerEvents.end()
	}
}
