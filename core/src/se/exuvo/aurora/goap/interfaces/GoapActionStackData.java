package se.exuvo.aurora.goap.interfaces;

public class GoapActionStackData<T, W> {

	public ReGoapState<T, W> currentState;
	public ReGoapState<T, W> goalState;
	public IReGoapAgent<T, W> agent;
	public IReGoapAction<T, W> next;
	public ReGoapState<T, W> settings;
}
