package se.exuvo.aurora.ai.goap.planner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import se.exuvo.aurora.ai.goap.interfaces.GoapActionStackData;
import se.exuvo.aurora.ai.goap.interfaces.IReGoapAction;
import se.exuvo.aurora.ai.goap.interfaces.IReGoapAgent;
import se.exuvo.aurora.ai.goap.interfaces.IReGoapGoal;
import se.exuvo.aurora.ai.goap.interfaces.ReGoapActionState;
import se.exuvo.aurora.ai.goap.interfaces.ReGoapState;

public class ReGoapPlanner<T, W> implements IGoapPlanner<T, W> {

	protected static final Logger log = LogManager.getLogger(ReGoapPlanner.class);
	private IReGoapAgent<T, W> goapAgent;
	private IReGoapGoal<T, W> currentGoal;
	public boolean calculated;
	private final AStar<ReGoapState<T, W>> astar;
	private final ReGoapPlannerSettings settings;

	public ReGoapPlanner() {
		this.settings = new ReGoapPlannerSettings();
		astar = new AStar<ReGoapState<T, W>>(this.settings.maxNodesToExpand);
	}

	public ReGoapPlanner(ReGoapPlannerSettings settings) {
		if (settings == null) {
			throw new NullPointerException();
		}

		this.settings = settings;
		astar = new AStar<ReGoapState<T, W>>(this.settings.maxNodesToExpand);
	}

	public IReGoapGoal<T, W> plan(IReGoapAgent<T, W> agent) {
		return plan(agent, null);
	}

	public IReGoapGoal<T, W> plan(IReGoapAgent<T, W> agent, IReGoapGoal<T, W> blacklistGoal) {
		return plan(agent, blacklistGoal, null);
	}

	public IReGoapGoal<T, W> plan(IReGoapAgent<T, W> agent, IReGoapGoal<T, W> blacklistGoal, Deque<ReGoapActionState<T, W>> currentPlan) {
		return plan(agent, blacklistGoal, currentPlan, null);
	}

	@SuppressWarnings("unchecked")
	public IReGoapGoal<T, W> plan(IReGoapAgent<T, W> agent, IReGoapGoal<T, W> blacklistGoal, Deque<ReGoapActionState<T, W>> currentPlan, Consumer<IReGoapGoal<T, W>> callback) {
		log.debug("[ReGoalPlanner] Starting planning calculation for agent: " + agent);
		goapAgent = agent;
		calculated = false;
		currentGoal = null;
		List<IReGoapGoal<T, W>> possibleGoals = new ArrayList<IReGoapGoal<T, W>>();

		for (IReGoapGoal<T, W> goal : goapAgent.getGoalsSet()) {
			if (goal == blacklistGoal) continue;
			goal.precalculations(this);
			if (goal.isGoalPossible()) possibleGoals.add(goal);
		}

		possibleGoals.sort(new Comparator<IReGoapGoal<T, W>>() {

			@Override
			public int compare(IReGoapGoal<T, W> x, IReGoapGoal<T, W> y) {
				return Float.compare(x.getPriority(), y.getPriority());
			}
		});

		ReGoapState<T, W> currentState = agent.getMemory().getWorldState();

		while (possibleGoals.size() > 0) {
			currentGoal = possibleGoals.get(possibleGoals.size() - 1);
			possibleGoals.remove(possibleGoals.size() - 1);
			ReGoapState<T, W> goalState = currentGoal.getGoalState();

			// can't work with dynamic actions, of course
			if (!settings.usingDynamicActions) {
				ReGoapState<T, W> wantedGoalCheck = currentGoal.getGoalState();
				GoapActionStackData<T, W> stackData = new GoapActionStackData<>();
				stackData.agent = goapAgent;
				stackData.currentState = currentState;
				stackData.goalState = goalState;
				stackData.next = null;
				stackData.settings = null;

				// we check if the goal can be archived through actions first, so we don't brute force it with A* if we can't
				for (IReGoapAction<T, W> action : goapAgent.getActionsSet()) {
					action.precalculations(stackData);
					if (!action.checkProceduralCondition(stackData)) {
						continue;
					}
					// check if the effects of all actions can archieve currentGoal
					ReGoapState<T, W> previous = wantedGoalCheck;
					wantedGoalCheck = ReGoapState.instantiate();
					previous.missingDifference(action.getEffects(stackData), wantedGoalCheck);
				}

				// finally push the current world state
				ReGoapState<T, W> current = wantedGoalCheck;
				wantedGoalCheck = ReGoapState.instantiate();
				current.missingDifference(getCurrentAgent().getMemory().getWorldState(), wantedGoalCheck);
				// can't validate goal
				if (wantedGoalCheck.getSize() > 0) {
					currentGoal = null;
					continue;
				}
			}

			goalState = goalState.clone();

			ReGoapNode<T, W> leaf = (ReGoapNode<T, W>) astar.run(ReGoapNode.instantiate(this, goalState, null, null, null), goalState, settings.maxIterations, settings.planningEarlyExit,
					settings.debugPlan);
			if (leaf == null) {
				currentGoal = null;
				continue;
			}

			Queue<ReGoapActionState<T, W>> result = leaf.calculatePath();
			if (currentPlan != null && currentPlan == result) {
				currentGoal = null;
				break;
			}
			if (result.size() == 0) {
				currentGoal = null;
				continue;
			}
			currentGoal.setPlan(result);
			break;
		}
		calculated = true;

		if (callback != null) {
			callback.accept(currentGoal);
		}

		if (currentGoal != null) {
			log.debug(String.format("[ReGoapPlanner] Calculated plan for goal '{0}', plan length: {1}", currentGoal, currentGoal.getPlan().size()));

			if (log.getLevel() == Level.DEBUG) {
				int i = 0;
				GoapActionStackData<T, W> stackData = new GoapActionStackData<>();
				stackData.agent = agent;
				stackData.currentState = currentState;
				stackData.goalState = currentGoal.getGoalState();
				stackData.next = null;

				for (ReGoapActionState<T, W> action : currentGoal.getPlan()) {
					stackData.settings = action.settings;
					log.debug(String.format("[ReGoapPlanner] {0}) {1}", i++, action.action.toString(stackData)));
				}
			}

		} else {
			log.warn("[ReGoapPlanner] Error while calculating plan.");
		}

		return currentGoal;
	}

	public IReGoapGoal<T, W> getCurrentGoal() {
		return currentGoal;
	}

	public IReGoapAgent<T, W> getCurrentAgent() {
		return goapAgent;
	}

	public boolean isPlanning() {
		return !calculated;
	}

	public ReGoapPlannerSettings getSettings() {
		return settings;
	}
}
