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
import net.mostlyoriginal.api.event.common.Subscribe
import se.exuvo.aurora.starsystems.components.CircleComponent
import se.exuvo.aurora.starsystems.components.MovementValues
import se.exuvo.aurora.starsystems.components.SpatialPartitioningComponent
import se.exuvo.aurora.starsystems.events.NonLinearMovementEvent
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.utils.quadtree.QuadtreeAABB

class SpatialPartitioningSystem : BaseEntitySystem(ASPECT) {
	companion object {
		@JvmField val ASPECT = Aspect.all(TimedMovementComponent::class.java)
		
		@JvmField val log = LogManager.getLogger(SpatialPartitioningSystem::class.java)
		
//		const val SCALE: Int = 1000_0000 // in m , min 1000
		const val SCALE: Int = 2_000 // in m , min 1000
//		const val MAX: Int = (15 * Units.AU / (SCALE / 1000)).toInt()
		const val MAX: Int = Int.MAX_VALUE
		//TODO calculate depth from scale and max to keep min subdivision size constant
	}

	lateinit var canAccelerateAspect: Aspect
	
	private val galaxy = GameServices[Galaxy::class]

	@Wire
	lateinit private var system: StarSystem

	lateinit private var movementMapper: ComponentMapper<TimedMovementComponent>
	lateinit private var circleMapper: ComponentMapper<CircleComponent>
	lateinit private var spatialPartitioningMapper: ComponentMapper<SpatialPartitioningComponent>

	val tree = QuadtreeAABB(MAX, MAX, 8, 8)
	
	private var updateQueue = PriorityQueue<Int>(Comparator<Int> { a, b ->
		val partitioningA = spatialPartitioningMapper.get(a)
		val partitioningB = spatialPartitioningMapper.get(b)
		
		val timeA = partitioningA.nextExpectedUpdate
		val timeB = partitioningB.nextExpectedUpdate
		
		timeA.compareTo(timeB)
	})
	
	override fun setWorld(world: World) {
		super.setWorld(world)

		val canAccelerateSubscription = world.getAspectSubscriptionManager().get(MovementSystem.CAN_ACCELERATE_FAMILY)
		canAccelerateAspect = canAccelerateSubscription.aspect
		
		canAccelerateSubscription.addSubscriptionListener(object : EntitySubscription.SubscriptionListener{
			override fun inserted(entities: IntBag) {
				entities.forEachFast { entityID ->
					val partitioning = spatialPartitioningMapper.get(entityID)
					
					if (partitioning != null && partitioning.nextExpectedUpdate == -1L) {
						println("accelerate inserted entityID $entityID")
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
		
		val radius: Int
		
		val circleC = circleMapper.get(entityID)
		
		if (circleC != null) {
			radius = (circleC.radius / SCALE).toInt()
		} else {
			radius = 1
		}
		
		// in Mm
		val x = movement.position.x / SCALE + MAX/2
		val y = movement.position.y / SCALE + MAX/2
		
//		println("insert at $x $y ${movement.getXinKM()} ${movement.getYinKM()}")
		
		if (partitioning.elementID != -1) {
			tree.remove(partitioning.elementID)
		}
		partitioning.elementID = tree.insert(entityID, (x - radius).toInt(), (y - radius).toInt(), (x + radius).toInt(), (y + radius).toInt())
	}
	
	override fun removed(entityID: Int): Unit {
//		println("removed $entityID")
		updateQueue.remove(entityID)
		
		tree.remove(entityID)
	}
	
	private fun updateNextExpectedUpdate(entityID: Int, movement: MovementValues): Long {
		var nextExpectedUpdate = galaxy.time
		
		if (!movement.velocity.isZero) {
		
//			// at^2 + vt = distance
			val a: Double = movement.acceleration.len()
			val b: Double = movement.velocity.len()
			val c: Double = -0.001 * Units.AU * 1000

			val t: Double

			if (a == 0.0) {
				t = -c / b
			} else {
				t = WeaponSystem.getPositiveRootOfQuadraticEquation(a, b, c)
			}

//			println("entityID $entityID: t $t a $a b $b c $c")

			nextExpectedUpdate += maxOf(1, t.toLong())
//			nextExpectedUpdate += 10

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
		update(event.entityID)
	}
	
	override fun processSystem() {
		
		while(true) {
			val entityID = updateQueue.peek()
			
			if (entityID != null) {
				
				val partitioning = spatialPartitioningMapper.get(entityID)
				
//				println("eval $entityID ${partitioning.nextExpectedUpdate}")
				
				if (galaxy.time >= partitioning.nextExpectedUpdate) {
					
//					println("process $entityID ${partitioning.nextExpectedUpdate}")
					
					updateQueue.poll()
					
					update(entityID)
					
				} else {
					break;
				}
				
			} else {
				break
			}
		}
		
		tree.cleanup()
	}
}
