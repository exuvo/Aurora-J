package se.exuvo.aurora.ai.goap.planner;

import java.util.Deque;
import java.util.function.Consumer;

import se.exuvo.aurora.ai.goap.interfaces.IReGoapAgent;
import se.exuvo.aurora.ai.goap.interfaces.IReGoapGoal;
import se.exuvo.aurora.ai.goap.interfaces.ReGoapActionState;

public interface IGoapPlanner<T, W> {

	IReGoapGoal<T, W> plan(IReGoapAgent<T, W> goapAgent, IReGoapGoal<T, W> blacklistGoal, Deque<ReGoapActionState<T, W>> currentPlan, Consumer<IReGoapGoal<T, W>> callback);

	IReGoapGoal<T, W> getCurrentGoal();

	IReGoapAgent<T, W> getCurrentAgent();

	boolean isPlanning();

	ReGoapPlannerSettings getSettings();
}
