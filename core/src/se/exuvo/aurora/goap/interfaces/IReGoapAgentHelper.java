package se.exuvo.aurora.goap.interfaces;

// interface needed only in Unity to use GetComponent and such features for generic agents
public interface IReGoapAgentHelper {

	Class<?>[] GetGenericArguments();
}
