package se.exuvo.aurora.ai.goap.interfaces;

import java.util.List;
import java.util.function.Consumer;

public interface IReGoapAction<T, W> {

	// this should return current's action calculated parameter, will be added to the run method
	// useful for dynamic actions, for example a GoTo action can save some informations (wanted position)
	// while being chosen from the planner, we save this information and give it back when we run the method
	// most of actions would return a single item list, but more complex could return many items
	List<ReGoapState<T, W>> getSettings(GoapActionStackData<T, W> stackData);

	void run(IReGoapAction<T, W> previousAction, IReGoapAction<T, W> nextAction, ReGoapState<T, W> settings, ReGoapState<T, W> goalState, Consumer<IReGoapAction<T, W>> done,
			Consumer<IReGoapAction<T, W>> fail);

	// Called when the action has been added inside a running Plan
	void planEnter(IReGoapAction<T, W> previousAction, IReGoapAction<T, W> nextAction, ReGoapState<T, W> settings, ReGoapState<T, W> goalState);

	// Called when the plan, which had this action, has either failed or completed
	void planExit(IReGoapAction<T, W> previousAction, IReGoapAction<T, W> nextAction, ReGoapState<T, W> settings, ReGoapState<T, W> goalState);

	void exit(IReGoapAction<T, W> nextAction);

	String getName();

	boolean isActive();

	boolean isInterruptable();

	void askForInterruption();

	// MUST BE IMPLEMENTED AS THREAD SAFE
	ReGoapState<T, W> getPreconditions(GoapActionStackData<T, W> stackData);

	ReGoapState<T, W> getEffects(GoapActionStackData<T, W> stackData);

	boolean checkProceduralCondition(GoapActionStackData<T, W> stackData);

	float getCost(GoapActionStackData<T, W> stackData);

	// DO NOT CHANGE RUNTIME ACTION VARIABLES, precalculation can be ran many times even while an action is running
	void precalculations(GoapActionStackData<T, W> stackData);

	String toString(GoapActionStackData<T, W> stackData);
}
