package se.exuvo.aurora.goap.planner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import se.exuvo.aurora.goap.interfaces.INode;

public class AStar<T> {

	private static final Logger log = Logger.getLogger(AStar.class);

	private final FastPriorityQueue<INode<T>, T> frontier;
	private final Map<T, INode<T>> stateToNode;
	private final Map<T, INode<T>> explored;
	private final List<INode<T>> createdNodes;
	// Debug
	private boolean debugPlan = false;
	private PlanDebugger debugger;

	public AStar() {
		this(1000);
	}

	public AStar(int maxNodesToExpand) {
		frontier = new FastPriorityQueue<INode<T>, T>(maxNodesToExpand);
		stateToNode = new HashMap<T, INode<T>>();
		explored = new HashMap<T, INode<T>>(); // State -> node
		createdNodes = new ArrayList<INode<T>>(maxNodesToExpand);
	}

	void clearNodes() {
		for (INode<T> node : createdNodes) {
			node.recycle();
		}
		createdNodes.clear();
	}

	private void debugPlan(INode<T> node, INode<T> parent) {
		if (!debugPlan) {
			return;
		}

		if (debugger == null) {
			debugger = new PlanDebugger();
		}

		debugger.clear();

		String nodeStr = node.hashCode() + ": pCost " + node.getPathCost() + ", hCost " + node.getHeuristicCost() + ", cost " + node.getCost() + ", name " + node.getName() + ", precon "
				+ node.getPreconditions() + ", effects " + node.getEffects() + ", goal " + node.getGoal();
		debugger.addNode(nodeStr);

		if (parent != null) {
			String connStr = String.format("{0} -> {1}", parent.hashCode(), node.hashCode());
			debugger.addConn(connStr);
		}
	}

	private void endDebugPlan(INode<T> node) {
		if (debugger != null) {
			while (node != null) {
				// mark success path
				String nodeStr = String.format("{0} [style=\"bold\" color=\"darkgreen\"]", node.hashCode());
				debugger.addNode(nodeStr);
				node = node.getParent();
			}

		}
	}

	public INode<T> run(INode<T> start, T goal) {
		return run(start, goal, 100);
	}

	public INode<T> run(INode<T> start, T goal, int maxIterations) {
		return run(start, goal, maxIterations, true);
	}

	public INode<T> run(INode<T> start, T goal, int maxIterations, boolean earlyExit) {
		return run(start, goal, maxIterations, earlyExit, true);
	}

	public INode<T> run(INode<T> start, T goal, int maxIterations, boolean earlyExit, boolean clearNodes) {
		return run(start, goal, maxIterations, earlyExit, clearNodes, false);
	}

	public INode<T> run(INode<T> start, T goal, int maxIterations, boolean earlyExit, boolean clearNodes, boolean debugPlan) {
		this.debugPlan = debugPlan;

		frontier.clear();
		stateToNode.clear();
		explored.clear();
		if (clearNodes) {
			clearNodes();
			createdNodes.add(start);
		}

		frontier.enqueue(start, start.getCost());

		debugPlan(start, null);

		int iterations = 0;
		while ((frontier.getCount() > 0) && (iterations < maxIterations) && (frontier.getCount() + 1 < frontier.getMaxSize())) {
			INode<T> node = frontier.dequeue();
			if (node.isGoal(goal)) {
				log.debug("[Astar] Success iterations: " + iterations);
				endDebugPlan(node);
				return node;
			}
			explored.put(node.getState(), node);

			for (INode<T> child : node.expand()) {
				iterations++;
				if (clearNodes) {
					createdNodes.add(child);
				}
				if (earlyExit && child.isGoal(goal)) {
					log.debug("[Astar] (early exit) Success iterations: " + iterations);
					endDebugPlan(child);
					return child;
				}
				float childCost = child.getCost();
				T state = child.getState();
				if (explored.containsKey(state)) continue;
				INode<T> similiarNode = stateToNode.get(state);
				if (similiarNode != null) {
					if (similiarNode.getCost() > childCost)
						frontier.remove(similiarNode);
					else break;
				}

				debugPlan(child, node);

				// Utilities.ReGoapLogger.Log(String.Format(" Enqueue frontier: {0}, cost: {1}", child.Name, childCost));
				frontier.enqueue(child, childCost);
				stateToNode.put(state, child);
			}
		}
		log.warn("[Astar] failed.");
		endDebugPlan(null);
		return null;
	}
}

class NodeComparer<T> implements Comparator<INode<T>> {

	public int compare(INode<T> x, INode<T> y) {
		int result = x.compareTo(y);
		if (result == 0) return 1;
		return result;
	}
}
