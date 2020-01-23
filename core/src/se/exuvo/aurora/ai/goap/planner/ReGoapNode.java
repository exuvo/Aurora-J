package se.exuvo.aurora.ai.goap.planner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Queue;

import net.mostlyoriginal.api.utils.pooling.PoolsCollection;
import se.exuvo.aurora.ai.goap.interfaces.GoapActionStackData;
import se.exuvo.aurora.ai.goap.interfaces.INode;
import se.exuvo.aurora.ai.goap.interfaces.IReGoapAction;
import se.exuvo.aurora.ai.goap.interfaces.IReGoapAgent;
import se.exuvo.aurora.ai.goap.interfaces.ReGoapActionState;
import se.exuvo.aurora.ai.goap.interfaces.ReGoapState;

public class ReGoapNode<T, W> implements INode<ReGoapState<T, W>> {

	private float cost;
	private IGoapPlanner<T, W> planner;
	private ReGoapNode<T, W> parent;
	private IReGoapAction<T, W> action;
	private ReGoapState<T, W> actionSettings;
	private ReGoapState<T, W> state;
	private float g;
	private float h;
	private ReGoapState<T, W> goalMergedWithWorld;

	private float heuristicMultiplier = 1;

	private final List<INode<ReGoapState<T, W>>> expandList;

	public float priority;
	public long insertionIndex;
	public int queueIndex;

	private ReGoapState<T, W> goal;
	private ReGoapState<T, W> effects;
	private ReGoapState<T, W> preconditions;

	private ReGoapNode() {
		expandList = new ArrayList<INode<ReGoapState<T, W>>>();
	}

	private ReGoapNode<T, W> init(IGoapPlanner<T, W> planner, ReGoapState<T, W> newGoal, ReGoapNode<T, W> parent, IReGoapAction<T, W> action, ReGoapState<T, W> settings) {
		expandList.clear();

		this.planner = planner;
		this.parent = parent;
		this.action = action;
		if (settings != null) this.actionSettings = settings.clone();

		if (parent != null) {
			state = parent.getState().clone();
			// g(node)
			g = parent.getPathCost();
		} else {
			state = planner.getCurrentAgent().getMemory().getWorldState().clone();
		}

//		IReGoapAction<T, W> nextAction = parent == null ? null : parent.action;
		if (action != null) {
			// create a new instance of the goal based on the paren't goal
			goal = newGoal.clone();

			GoapActionStackData<T, W> stackData = new GoapActionStackData<>();
			stackData.currentState = state;
			stackData.goalState = goal;
			stackData.next = action;
			stackData.agent = planner.getCurrentAgent();
			stackData.settings = actionSettings;

			preconditions = action.getPreconditions(stackData);
			effects = action.getEffects(stackData);
			// addding the action's cost to the node's total cost
			g += action.getCost(stackData);

			// adding the action's effects to the current node's state
			state.addFromState(effects);

			// removes from goal all the conditions that are now fullfiled in the action's effects
			goal.replaceWithMissingDifference(effects);
			// add all preconditions of the current action to the goal
			goal.addFromState(preconditions);
		} else {
			goal = newGoal;
		}
		h = goal.getSize();
		// f(node) = g(node) + h(node)
		cost = g + h * heuristicMultiplier;

		// additionally calculate the goal without any world effect to understand if we are done
		ReGoapState<T, W> diff = ReGoapState.instantiate();
		goal.missingDifference(planner.getCurrentAgent().getMemory().getWorldState(), diff);
		goalMergedWithWorld = diff;

		return this;
	}

	private static PoolsCollection pools = new PoolsCollection();

	public void recycle() {
		state.recycle();
		state = null;
		goal.recycle();
		goal = null;
		pools.free(this);
	}

	@SuppressWarnings("unchecked")
	public static <T, W> ReGoapNode<T, W> instantiate(IGoapPlanner<T, W> planner, ReGoapState<T, W> newGoal, ReGoapNode<T, W> parent, IReGoapAction<T, W> action, ReGoapState<T, W> actionSettings) {
		return pools.obtain(ReGoapNode.class).init(planner, newGoal, parent, action, actionSettings);
	}

	public float getPathCost() {
		return g;
	}

	public float getHeuristicCost() {
		return h;
	}

	public ReGoapState<T, W> getState() {
		return state;
	}

	public List<INode<ReGoapState<T, W>>> expand() {
		expandList.clear();

		IReGoapAgent<T, W> agent = planner.getCurrentAgent();
		List<IReGoapAction<T, W>> actions = agent.getActionsSet();

		GoapActionStackData<T, W> stackData = new GoapActionStackData<>();
		stackData.currentState = state;
		stackData.goalState = goal;
		stackData.next = action;
		stackData.agent = agent;
		stackData.settings = null;
		for (int index = actions.size() - 1; index >= 0; index--) {
			IReGoapAction<T, W> possibleAction = actions.get(index);

			possibleAction.precalculations(stackData);
			List<ReGoapState<T, W>> settingsList = possibleAction.getSettings(stackData);
			for (ReGoapState<T, W> settings : settingsList) {
				stackData.settings = settings;
				ReGoapState<T, W> precond = possibleAction.getPreconditions(stackData);
				ReGoapState<T, W> effects = possibleAction.getEffects(stackData);

				if (effects.hasAny(goal) && // any effect is the current Goal
						!goal.hasAnyConflict(effects, precond) && // no precondition is conflicting with the Goal or has conflict but the effects
																											 // fulfils the Goal
						!goal.hasAnyConflict(effects) && // no effect is conflicting with the Goal
						possibleAction.checkProceduralCondition(stackData)) {
					ReGoapState<T, W> newGoal = goal;
					expandList.add(instantiate(planner, newGoal, this, possibleAction, settings));
				}
			}
		}
		return expandList;
	}

	public Queue<ReGoapActionState<T, W>> calculatePath() {
		Deque<ReGoapActionState<T, W>> result = new ArrayDeque<ReGoapActionState<T, W>>();

		calculatePath(result);
		return result;
	}

	@SuppressWarnings("unchecked")
	public void calculatePath(Deque<ReGoapActionState<T, W>> result) {
		ReGoapNode<T, W> node = this;
		while (node.getParent() != null) {
			result.add(new ReGoapActionState<T, W>(node.action, node.actionSettings));
			node = (ReGoapNode<T, W>) node.getParent();
		}
	}

	public int compareTo(INode<ReGoapState<T, W>> other) {
		return Float.compare(cost, other.getCost());
	}

	public float getCost() {
		return cost;
	}

	public INode<ReGoapState<T, W>> getParent() {
		return parent;
	}

	public boolean isGoal(ReGoapState<T, W> goal) {
		return goalMergedWithWorld.getSize() <= 0;
	}

	public String getName() {
		return action != null ? action.getName() : "NoAction";
	}

	public ReGoapState<T, W> getGoal() {
		return goal;
	}

	public ReGoapState<T, W> getEffects() {
		return goal;
	}

	public ReGoapState<T, W> getPreconditions() {
		return goal;
	}

	public float getPriority() {
		return priority;
	}

	public void setPriority(float priority) {
		this.priority = priority;
	}

	public int getQueueIndex() {
		return queueIndex;
	}

	public void setQueueIndex(int queueIndex) {
		this.queueIndex = queueIndex;
	}

}
