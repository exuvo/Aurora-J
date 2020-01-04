package se.exuvo.aurora.planetarysystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.systems.IteratingSystem
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.planetarysystems.components.TimedLifeComponent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.planetarysystems.PlanetarySystem
import com.artemis.annotations.Wire
import com.artemis.BaseEntitySystem
import java.util.PriorityQueue
import se.unlogic.standardutils.reflection.ReflectionUtils
import com.artemis.EntitySubscription
import com.artemis.utils.BitVector

class TimedLifeSystem : BaseEntitySystem(ASPECT) {
	companion object {
		val ASPECT = Aspect.all(TimedLifeComponent::class.java)
		
		@JvmStatic
		val log = LogManager.getLogger(TimedLifeSystem::class.java)
	}

	private val galaxy = GameServices[Galaxy::class]

	@Wire
	lateinit private var planetarySystem: PlanetarySystem

	lateinit private var timedLifeMapper: ComponentMapper<TimedLifeComponent>
	
	private var selfRemovedEntityIDs = BitVector()
	
	private var queue = PriorityQueue<Int>(object : Comparator<Int> {
		override fun compare(a: Int, b: Int): Int {
			val timedLifeA = timedLifeMapper.get(a).endTime
			val timedLifeB = timedLifeMapper.get(b).endTime
			
			return timedLifeA.compareTo(timedLifeB)
		}
	})
	
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
				
				val timedLife = timedLifeMapper.get(entityID)
				
				if (galaxy.time >= timedLife.endTime) {
					
					queue.poll()
//					println("destroying $entityID")
					
					planetarySystem.destroyEntity(entityID)
					
					// We don't want to get notified by our own deletions
					selfRemovedEntityIDs.unsafeSet(entityID)
					
				} else {
					break;
				}
				
			} else {
				break
			}
		}
	}
}
