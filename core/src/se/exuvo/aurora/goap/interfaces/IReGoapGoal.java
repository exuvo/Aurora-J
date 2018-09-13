package se.exuvo.aurora.goap.interfaces;

import java.util.Queue;
import java.util.function.Consumer;

import se.exuvo.aurora.goap.planner.IGoapPlanner;

public interface IReGoapGoal<T, W> {

	void run(Consumer<IReGoapGoal<T, W>> callback);

	// THREAD SAFE METHODS
	Queue<ReGoapActionState<T, W>> getPlan();

	String getName();

	void precalculations(IGoapPlanner<T, W> goapPlanner);

	boolean isGoalPossible();

	ReGoapState<T, W> getGoalState();

	float getPriority();

	void setPlan(Queue<ReGoapActionState<T, W>> path);

	float getErrorDelay();
}
