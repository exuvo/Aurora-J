package se.exuvo.aurora.planetarysystems.components

import com.artemis.Component
import se.exuvo.aurora.goap.interfaces.IReGoapAction
import se.exuvo.aurora.goap.interfaces.IReGoapAgent
import se.exuvo.aurora.goap.interfaces.IReGoapGoal
import se.exuvo.aurora.goap.interfaces.IReGoapMemory
import se.exuvo.aurora.goap.interfaces.ReGoapActionState
import se.exuvo.aurora.goap.interfaces.ReGoapState
import java.util.ArrayDeque
import com.artemis.utils.Bag
import com.artemis.utils.ImmutableBag

// can be shared between multiple ships
interface Order {
	
}

enum class ShipWorldState {
	FUEL_LOW,
	IN_WEAPONS_RANGE
}

class ShipMemory<T, W>: IReGoapMemory<T, W> {
	val goapState: ReGoapState<T, W> = ReGoapState.instantiate()
	
	override fun getWorldState() = goapState
}

class ShipOrdersComponent<T, W> : Component(), IReGoapAgent<T, W> where T: ShipWorldState, W: Boolean?  {
	var currentOrder: Order? = null
	val orderQueue = ArrayDeque<Order>()
	
	val allGoals = Bag<IReGoapGoal<T, W>>()
	var goalsChanged = true
	val blacklistedGoalMap = HashMap<IReGoapGoal<T, W>, Long>()
	val blacklistedGoals = Bag<IReGoapGoal<T, W>>()
	var activeGoals: ImmutableBag<IReGoapGoal<T, W>> = allGoals
	
	val actions = Bag<IReGoapAction<T, W>>()
	val memory = ShipMemory<T, W>()
	
	var currentGoal: IReGoapGoal<T, W>? = null
	val planValues = HashMap<T, W>()
	
	fun updateActiveGoals(galacticTime: Long) {
		goalsChanged = false;
		
		if (blacklistedGoalMap.isNotEmpty()) {
			blacklistedGoals.clear();

			for (goal in allGoals) {
				val blackListedGoalTime = blacklistedGoalMap.get(goal)
				
				if (blackListedGoalTime == null) {
					blacklistedGoals.add(goal);
					
				} else if (blackListedGoalTime < galacticTime) {
					blacklistedGoalMap.remove(goal);
					blacklistedGoals.add(goal);
				}
			}
			
			activeGoals = blacklistedGoals;
			
		} else {
			activeGoals = allGoals;
		}
	}
	
	override fun getCurrentGoal() = currentGoal

	override fun isActive() = true

	override fun getActionsSet(): MutableList<IReGoapAction<T, W>>? {
		TODO()
	}

	override fun getMemory() = memory

	override fun hasPlanValue(target: T) = planValues.containsKey(target)

	override fun getPlanValue(key: T) = planValues.get(key)
	
	override fun instantiateNewState(): ReGoapState<T, W>? {
		TODO()
	}

	override fun getGoalsSet(): MutableList<IReGoapGoal<T, W>>? {
		TODO()
	}

	override fun setPlanValue(key: T, value: W) {
		planValues[key] = value
	}

	override fun warnPossibleGoal(goal: IReGoapGoal<T, W>?) {
		TODO()
	}

	override fun getStartingPlan(): MutableList<ReGoapActionState<T, W>>? {
		TODO()
	}
	
}

class FleetOrdersComponent() : Component() {
	var currentOrder: Order? = null
	val orderQueue = ArrayDeque<Order>()
}
