package se.exuvo.aurora.ai.goap.interfaces;

import java.util.List;

public interface INode<T> {

	T getState();

	List<INode<T>> expand();

	int compareTo(INode<T> other);

	float getCost();

	float getHeuristicCost();

	float getPathCost();

	INode<T> getParent();

	boolean isGoal(T goal);

	String getName();

	T getGoal();

	T getEffects();

	T getPreconditions();

	int getQueueIndex();

	void setQueueIndex(int queueIndex);

	float getPriority();

	void setPriority(float priority);

	void recycle();
}
