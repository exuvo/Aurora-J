package se.exuvo.aurora.starsystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.systems.IteratingSystem
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.starsystems.components.TimedLifeComponent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.starsystems.StarSystem
import com.artemis.annotations.Wire
import com.artemis.BaseEntitySystem
import java.util.PriorityQueue
import se.unlogic.standardutils.reflection.ReflectionUtils
import com.artemis.EntitySubscription
import com.artemis.utils.BitVector
import se.exuvo.aurora.starsystems.components.OnPredictedMovementComponent
import se.exuvo.aurora.starsystems.components.TimedMovementComponent
import com.artemis.World
import se.exuvo.aurora.starsystems.components.MoveToEntityComponent
import se.exuvo.aurora.starsystems.components.MoveToPositionComponent
import se.exuvo.aurora.starsystems.components.OrbitComponent

class MovementPredictedSystem : BaseEntitySystem(ASPECT) {
	companion object {
		@JvmField val ASPECT = Aspect.all(TimedMovementComponent::class.java, OnPredictedMovementComponent::class.java).exclude(OrbitComponent::class.java)
		
		@JvmField val log = LogManager.getLogger(MovementPredictedSystem::class.java)
	}

	private val galaxy = GameServices[Galaxy::class]

	@Wire
	lateinit private var system: StarSystem

	lateinit private var movementMapper: ComponentMapper<TimedMovementComponent>
	lateinit private var predictedMovementMapper: ComponentMapper<OnPredictedMovementComponent>
	lateinit private var moveToEntityMapper: ComponentMapper<MoveToEntityComponent>
	lateinit private var moveToPositionMapper: ComponentMapper<MoveToPositionComponent>
	
	private var selfRemovedEntityIDs = BitVector()
	
	lateinit var CAN_ACCELERATE_ASPECT: Aspect
	
	private var queue = PriorityQueue<Int>(object : Comparator<Int> {
		override fun compare(a: Int, b: Int): Int {
			val movA = movementMapper.get(a);
			val movB = movementMapper.get(b);
			val nextA = movA.next
			val nextB = movB.next
			
			if (nextA == null && nextB == null) {
				return 0
			}
			
			if (nextA == null) {
				return 1
			}
			
			if (nextB == null) {
				return -1
			}
			
			val timeA = nextA.time
			val timeB = nextB.time
			
			return timeA.compareTo(timeB)
		}
	})
	
	override fun setWorld(world: World) {
		super.setWorld(world)

		CAN_ACCELERATE_ASPECT = MovementSystem.CAN_ACCELERATE_FAMILY.build(world)
	}
	
	override fun inserted(entityID: Int): Unit {
//		println("inserted $entityID")
		queue.add(entityID)
		
		selfRemovedEntityIDs.ensureCapacity(entityID)
	}
	
	override fun removed(entityID: Int): Unit {
		if (!selfRemovedEntityIDs.unsafeGet(entityID)) {
//			println("removed $entityID")
			queue.remove(entityID)
			
		} else {
			selfRemovedEntityIDs.unsafeClear(entityID)
		}
	}

	override fun processSystem() {
		
		while(true) {
			val entityID = queue.peek()
			
			if (entityID != null) {
				
				val movement = movementMapper.get(entityID)
				val next = movement.next
				
				if (next == null) {
					throw RuntimeException("next movement is null for $entityID")
				}
				
//				println("eval $entityID")
				
				if (galaxy.time >= next.time) {
					
					queue.poll()
					
					if (CAN_ACCELERATE_ASPECT.isInterested(entityID)) {
						println("Movement: target reached predicted time for $entityID")
					}
					
					movement.previous = next
					movement.next = null
					movement.aimTarget = null
					movement.startAcceleration = null
					movement.finalAcceleration = null
	
					moveToEntityMapper.remove(entityID)
					moveToPositionMapper.remove(entityID)
					predictedMovementMapper.remove(entityID)
					
					// We don't want to get notified by our own removals
					selfRemovedEntityIDs.unsafeSet(entityID)
					
					system.changed(entityID, movementMapper)
					
				} else {
					break;
				}
				
			} else {
				break
			}
		}
		
		//TODO periodically reevaluate validity of predicted paths from here or from OrderSystem
	}
}
