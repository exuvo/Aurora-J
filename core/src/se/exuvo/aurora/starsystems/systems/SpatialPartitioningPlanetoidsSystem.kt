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
import se.exuvo.aurora.starsystems.components.TimedMovementComponent
import com.artemis.World
import glm_.pow
import net.mostlyoriginal.api.event.common.Subscribe
import se.exuvo.aurora.starsystems.components.AsteroidComponent
import se.exuvo.aurora.starsystems.components.CircleComponent
import se.exuvo.aurora.starsystems.components.MovementValues
import se.exuvo.aurora.starsystems.components.OrbitComponent
import se.exuvo.aurora.starsystems.components.SpatialPartitioningPlanetoidsComponent
import se.exuvo.aurora.starsystems.components.SunComponent
import se.exuvo.aurora.starsystems.events.NonLinearMovementEvent
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.utils.quadtree.QuadtreeAABB
import kotlin.math.roundToInt

class SpatialPartitioningPlanetoidsSystem : BaseEntitySystem(ASPECT) {
	companion object {
		@JvmField val ASPECT = Aspect.all(TimedMovementComponent::class.java, CircleComponent::class.java).one(OrbitComponent::class.java, SunComponent::class.java, AsteroidComponent::class.java)
		
		@JvmField val log = LogManager.getLogger(SpatialPartitioningPlanetoidsSystem::class.java)
		
		const val SCALE: Int = 2_000 // in m , min 1000
		const val MAX: Int = Int.MAX_VALUE
		const val DESIRED_MIN_SQUARE_SIZE: Long = 149597870700L // AU in m
		@JvmField val RAW_DEPTH: Double = Math.log(SCALE * MAX.toDouble() / DESIRED_MIN_SQUARE_SIZE) / Math.log(2.0)
		@JvmField val DEPTH: Int = RAW_DEPTH.roundToInt() // 5
		@JvmField val MIN_SQUARE_SIZE = (SCALE * MAX.toLong()) / 2.pow(DEPTH)
		/*
			square_size = SCALE * MAX / 2.pow(DEPTH)
			2.pow(DEPTH) = SCALE * MAX / sq
			log 2.pow(DEPTH) = log (SCALE * MAX / sq)
			DEPTH * log 2 = log (SCALE * MAX / sq)
			DEPTH * log 2 = log scale + log MAX - log sq
			DEPTH = log (SCALE * MAX / sq) / log 2
			DEPTH = log2 (SCALE * MAX / sq)
		 */
	}

	private val galaxy = GameServices[Galaxy::class]

	@Wire
	lateinit private var system: StarSystem

	lateinit private var movementMapper: ComponentMapper<TimedMovementComponent>
	lateinit private var circleMapper: ComponentMapper<CircleComponent>
	lateinit private var spatialPartitioningMapper: ComponentMapper<SpatialPartitioningPlanetoidsComponent>

	val tree = QuadtreeAABB(MAX, MAX, 4, DEPTH)
	
	private var updateQueue = PriorityQueue<Int>(Comparator<Int> { a, b ->
		val partitioningA = spatialPartitioningMapper.get(a)
		val partitioningB = spatialPartitioningMapper.get(b)
		
		val timeA = partitioningA.nextExpectedUpdate
		val timeB = partitioningB.nextExpectedUpdate
		
		timeA.compareTo(timeB)
	})
	
	override fun setWorld(world: World) {
		super.setWorld(world)
		
		println("RAW_DEPTH $RAW_DEPTH, DEPTH $DEPTH, MIN_SQUARE_SIZE $MIN_SQUARE_SIZE, total size ${SCALE * MAX.toLong()}m ${(SCALE * MAX.toLong()) / (Units.AU * 1000)} AU")
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
		
		val radius: Long
		
		val circleC = circleMapper.get(entityID)
		
		if (circleC != null) {
			radius = (circleC.radius / SCALE).toLong()
		} else {
			radius = 1
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
		partitioning.elementID = tree.insert(entityID, (x - radius).toInt(), (y - radius).toInt(), (x + radius).toInt(), (y + radius).toInt())
		profilerEvents.end()
	}
	
	override fun removed(entityID: Int): Unit {
//		println("removed $entityID")
		updateQueue.remove(entityID)
		
		tree.remove(entityID)
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
			nextExpectedUpdate += 10 * 60

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
		tree.cleanupFull()
		profilerEvents.end()
	}
}
