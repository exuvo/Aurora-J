package se.exuvo.aurora.starsystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.EntitySubscription.SubscriptionListener
import com.artemis.systems.IteratingSystem
import com.artemis.utils.IntBag
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.ai.goap.planner.ReGoapPlannerSettings
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.components.ShipOrdersComponent
import se.exuvo.aurora.starsystems.components.ShipWorldState
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.ai.goap.interfaces.IReGoapGoal
import se.exuvo.aurora.ai.goap.interfaces.IReGoapAction
import se.exuvo.aurora.ai.goap.interfaces.ReGoapActionState
import java.util.Queue

class ShipOrdersSystem : IteratingSystem(FAMILY) {
	companion object {
		val FAMILY = Aspect.all(ShipComponent::class.java, ShipOrdersComponent::class.java)
		val SHIP_FAMILY = Aspect.all(ShipComponent::class.java)
	}

	val log = LogManager.getLogger(this.javaClass)

	lateinit private var shipMapper: ComponentMapper<ShipComponent>
	lateinit private var ordersMapper: ComponentMapper<ShipOrdersComponent<ShipWorldState>>

	private val galaxy = GameServices[Galaxy::class]
	val plannerSettings = ReGoapPlannerSettings()

	override fun initialize() {
		super.initialize()

		world.getAspectSubscriptionManager().get(SHIP_FAMILY).addSubscriptionListener(object : SubscriptionListener {
			override fun inserted(entityIDs: IntBag) {
				entityIDs.forEachFast { entityID ->

					if (!ordersMapper.has(entityID)) {
						ordersMapper.create(entityID)
					}
				}
			}

			override fun removed(entities: IntBag) {}
		})
	}

	override fun process(entityID: Int) {
		val ship = shipMapper.get(entityID)
		var ordersComponent = ordersMapper.get(entityID)
		
		
	}
	
	fun ShipOrdersComponent<ShipWorldState>.updatePossibleGoals() {
		allowedGoalsDirty = false;
		
		if (blacklistedGoalsMap.isNotEmpty()) {
			allowedGoals.clear();

			automaticGoals.forEachFast{ goal ->
				val blackListedGoalTime = blacklistedGoalsMap.get(goal)
				
				if (blackListedGoalTime == null) {
					allowedGoals.add(goal);
					
				} else if (blackListedGoalTime < galaxy.time) {
					blacklistedGoalsMap.remove(goal);
					allowedGoals.add(goal);
				}
			}
			
			possibleGoals = allowedGoals;
			
		} else {
			possibleGoals = automaticGoals;
		}
	}
	
	fun ShipOrdersComponent<ShipWorldState>.requestPlanning(forced: Boolean = false): Boolean {
		if (needsPlanning) return false
		if (!forced && galaxy.time - lastPlanning <= 10) return false

		lastPlanning = galaxy.time
		abortOnNextActionTransition = false
		needsPlanning = true
		
		updatePossibleGoals()
		
		return true
	}
	
	fun ShipOrdersComponent<ShipWorldState>.planningDone(entityID: Int, newGoal: IReGoapGoal<ShipWorldState, Any?>?) {
		needsPlanning = false
		
		if (newGoal == null) {
			if (currentGoal == null) {
				log.warn("Could not find a plan for $entityID")
			}
			return
		}
		
		currentActionState?.action!!.exit(null)
		currentActionState = null
		currentGoal = newGoal
		
		currentPlan?.let { currentPlan ->
			
			for(i in 0..currentPlan.size) {
				val actionState = currentPlan[i]
				val previousAction = if (i > 0) currentPlan[i - 1].action else null
				val nextAction = if (i + 1 < currentPlan.size) currentPlan[i + 1].action else null
				
				// Should it really be the new goal state here?
				actionState.action.planExit(previousAction, nextAction, actionState.settings, newGoal.getGoalState())
			}
		}
		
		currentPlan = ArrayList(newGoal.getPlan())
		planValues.clear()

		currentPlan!!.let { currentPlan ->
			for (i in 0..currentPlan.size) {
				val actionState = currentPlan[i]
				val previousAction = if (i > 0) currentPlan[i - 1].action else null
				val nextAction = if (i + 1 < currentPlan.size) currentPlan[i + 1].action else null

				// Should it really be the new goal state here?
				actionState.action.planEnter(previousAction, nextAction, actionState.settings, newGoal.getGoalState())
			}
		}
		
		nextAction()
	}
	
	fun ShipOrdersComponent<ShipWorldState>.nextAction() {
		if (abortOnNextActionTransition) {
			requestPlanning()
			return
		}
		
		val plan: Queue<ReGoapActionState<ShipWorldState, Any?>> = currentGoal!!.getPlan()
		
		if (plan.isEmpty()) {
			
			val currentActionState = currentActionState
			
			if (currentActionState != null) {
				currentActionState.action.exit(currentActionState.action);
				this.currentActionState = null;
			}
			
			requestPlanning()
			
		} else {
			
			val previousActionState = currentActionState
			val newActionState = plan.remove()
			currentActionState = newActionState
			var nextActionState = if (plan.isNotEmpty()) plan.peek() else null
			
			if (previousActionState != null) previousActionState.action.exit(newActionState.action)
			newActionState.action.run(previousActionState?.action, nextActionState?.action, newActionState.settings, currentGoal!!.getGoalState(),
				done@{action: IReGoapAction<ShipWorldState, Any?> ->
					if (action == currentActionState!!.action) nextAction()
				},
				failed@{action: IReGoapAction<ShipWorldState, Any?> ->
					if (currentActionState?.action != action) {
						log.warn("Action ${currentActionState!!.action} failed but is not current action")
						return@failed
					}
					
//					if (blackListGoalOnFailure) goalBlacklist[currentGoal] = galaxy.time + currentGoal.getErrorDelay();
//					calculateNewGoal(true);
				})
		}
	}
	
	//TODO more from ReGoapAgent
}





















