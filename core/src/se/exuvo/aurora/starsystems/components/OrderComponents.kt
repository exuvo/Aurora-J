package se.exuvo.aurora.starsystems.components

import com.artemis.Component
import se.exuvo.aurora.ai.goap.interfaces.IReGoapAction
import se.exuvo.aurora.ai.goap.interfaces.IReGoapAgent
import se.exuvo.aurora.ai.goap.interfaces.IReGoapGoal
import se.exuvo.aurora.ai.goap.interfaces.IReGoapMemory
import se.exuvo.aurora.ai.goap.interfaces.ReGoapActionState
import se.exuvo.aurora.ai.goap.interfaces.ReGoapState
import com.artemis.utils.Bag
import com.artemis.utils.ImmutableBag
import se.exuvo.aurora.utils.Vector2L
import com.artemis.utils.IntBag

// can be shared between multiple ships
abstract class ShipOrder {
	val ships = IntBag()
	var nextShipOrder: ShipOrder? = null
}

abstract class DestinationOrder: ShipOrder() {
	var targetPosition: Vector2L? = null
	var targetEntity: EntityReference? = null
}

enum class ShipWorldState {
	FUEL_LOW,
	IN_WEAPONS_RANGE,
	SHIELDS_UP,
	POSITION,
	PLANETARY_SYSTEM
}

class ShipMemory<T>: IReGoapMemory<T, Any?> {
	val goapState: ReGoapState<T, Any?> = ReGoapState.instantiate()
	
	override fun getWorldState() = goapState
}

class ShipOrdersComponent<T: ShipWorldState> : Component(), IReGoapAgent<T, Any?> {
	var currentShipOrder: ShipOrder? = null
	
//	val automaticGoals = Bag<IReGoapGoal<T, Any?>>()
//	val blacklistedGoalsMap = HashMap<IReGoapGoal<T, Any?>, Long>()
//	val allowedGoals = Bag<IReGoapGoal<T, Any?>>()
//	var possibleGoals: ImmutableBag<IReGoapGoal<T, Any?>> = automaticGoals
//	var allowedGoalsDirty = true
	
	val possibleActions = Bag<IReGoapAction<T, Any?>>()
	val memory = ShipMemory<T>()
	
	@JvmField
	var currentGoal: IReGoapGoal<T, Any?>? = null
	var currentPlan: MutableList<ReGoapActionState<T, Any?>>? = null
	val planValues = HashMap<T, Any?>()
	var currentActionState: ReGoapActionState<T, Any?>? = null
	var abortOnNextActionTransition = false
	var needsPlanning = false
	var lastPlanning = -1000L
	
	override fun getCurrentGoal(): IReGoapGoal<T, Any?>? = currentGoal

	override fun isActive() = true

	override fun getActionsSet(): MutableList<IReGoapAction<T, Any?>>? {
		TODO()
	}

	override fun getMemory(): IReGoapMemory<T, Any?>? = memory

	override fun hasPlanValue(target: T) = planValues.containsKey(target)

	override fun getPlanValue(key: T) = planValues.get(key)
	
	override fun instantiateNewState(): ReGoapState<T, Any?>? {
		TODO()
	}

	override fun getGoalsSet(): MutableList<IReGoapGoal<T, Any?>>? {
		TODO()
	}

	override fun setPlanValue(key: T, value: Any?) {
		planValues[key] = value
	}

	override fun warnPossibleGoal(goal: IReGoapGoal<T, Any?>?) {
		TODO()
	}

	override fun getStartingPlan(): MutableList<ReGoapActionState<T, Any?>>? = currentPlan
	
}
