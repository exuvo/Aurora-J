package se.exuvo.aurora.goap.planner;

public class ReGoapPlannerSettings {
	
	public boolean planningEarlyExit = false;
	// increase both if your agent has a lot of actions
	public int maxIterations = 1000;
	public int maxNodesToExpand = 10000;
	// set this to true if using dynamic actions, such as GenericGoTo or GatherResourceAction
	// a dynamic action is an action that has dynamic preconditions or effects (changed in runtime/precalcultions)
	public boolean usingDynamicActions = false;

	public boolean debugPlan = false;
}
