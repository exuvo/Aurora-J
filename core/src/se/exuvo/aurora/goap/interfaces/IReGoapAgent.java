package se.exuvo.aurora.goap.interfaces;

import java.util.List;

public interface IReGoapAgent<T, W> {

	IReGoapMemory<T, W> getMemory();

	IReGoapGoal<T, W> getCurrentGoal();

	// called from a goal when the goal is available
	void warnPossibleGoal(IReGoapGoal<T, W> goal);

	boolean isActive();

	List<ReGoapActionState<T, W>> getStartingPlan();

	W getPlanValue(T key);

	void setPlanValue(T key, W value);

	boolean hasPlanValue(T target);

	// THREAD SAFE
	List<IReGoapGoal<T, W>> getGoalsSet();

	List<IReGoapAction<T, W>> getActionsSet();

	ReGoapState<T, W> instantiateNewState();

}
