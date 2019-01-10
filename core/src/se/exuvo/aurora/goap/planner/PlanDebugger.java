package se.exuvo.aurora.goap.planner;

import java.util.ArrayList;
import java.util.List;

public class PlanDebugger {

	public List<String> nodeDeclarations = new ArrayList<String>();
	public List<String> nodeConnections = new ArrayList<String>();

	public void addNode(String s) {
		nodeDeclarations.add(s);
	}

	public void addConn(String s) {
		nodeConnections.add(s);
	}

	public void clear() {
		nodeDeclarations.clear();
		nodeConnections.clear();
	}
}
