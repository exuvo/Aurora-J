package se.exuvo.aurora.goap.interfaces;

public class ReGoapActionState<T, W> {

	public IReGoapAction<T, W> action;
	public ReGoapState<T, W> settings;

	public ReGoapActionState(IReGoapAction<T, W> action, ReGoapState<T, W> settings) {
		this.action = action;
		this.settings = settings;
	}
}
