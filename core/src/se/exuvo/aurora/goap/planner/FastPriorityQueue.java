package se.exuvo.aurora.goap.planner;

import java.security.InvalidParameterException;

/// An implementation of a min-Priority Queue using a heap. Has O(1) .contains()!
/// See https://github.com/BlueRaja/High-Speed-Priority-Queue-for-C-Sharp/wiki/Getting-Started for more information
public final class FastPriorityQueue<T extends INode<U>, U> {

	private int numNodes;
	private T[] nodes;

	/// Instantiate a new Priority Queue

	/// <param name="maxNodes">The max nodes ever allowed to be enqueued (going over this will cause undefined behavior)</param>
	@SuppressWarnings("unchecked")
	public FastPriorityQueue(int maxNodes) {
		if (maxNodes <= 0) {
			throw new InvalidParameterException("New queue size cannot be smaller than 1");
		}

		numNodes = 0;
		nodes = (T[]) new Object[maxNodes + 1];
	}

	/// Returns the number of nodes in the queue.
	/// O(1)
	public int getCount() {
		return numNodes;
	}

	/// Returns the maximum number of items that can be enqueued at once in this queue. Once you hit this number (ie. once Count == MaxSize),
	/// attempting to enqueue another item will cause undefined behavior. O(1)
	public int getMaxSize() {
		return nodes.length - 1;
	}

	/// Removes every node from the queue.
	/// O(n) (So, don't do this often!)
	public void clear() {
		for (int i = 1; i < 1 + numNodes; i++) {
			nodes[i] = null;
		}

		numNodes = 0;
	}

	/// Returns (in O(1)!) whether the given node is in the queue. O(1)
	public boolean contains(T node) {
		if (node == null) {
			throw new NullPointerException("node");
		}
		if (node.getQueueIndex() < 0 || node.getQueueIndex() >= nodes.length) {
			throw new InvalidParameterException("node.getQueueIndex() has been corrupted. Did you change it manually? Or add this node to another queue?");
		}

		return (nodes[node.getQueueIndex()] == node);
	}

	/// Enqueue a node to the priority queue. Lower values are placed in front. Ties are broken by first-in-first-out.
	/// If the queue is full, the result is undefined.
	/// If the node is already enqueued, the result is undefined.
	/// O(log n)
	public void enqueue(T node, float priority) {
		if (node == null) {
			throw new NullPointerException("node");
		}
		if (numNodes >= nodes.length - 1) {
			throw new InvalidParameterException("Queue is full - node cannot be added: " + node);
		}
		if (contains(node)) {
			throw new InvalidParameterException("Node is already enqueued: " + node);
		}

		node.setPriority(priority);
		numNodes++;
		nodes[numNodes] = node;
		node.setQueueIndex(numNodes);
		cascadeUp(nodes[numNodes]);
	}

	private void swap(T node1, T node2) {
		// Swap the nodes
		nodes[node1.getQueueIndex()] = node2;
		nodes[node2.getQueueIndex()] = node1;

		// Swap their indicies
		int temp = node1.getQueueIndex();
		node1.setQueueIndex(node2.getQueueIndex());
		node2.setQueueIndex(temp);
	}

	// Performance appears to be slightly better when this is NOT inlined o_O
	private void cascadeUp(T node) {
		// aka Heapify-up
		int parent = node.getQueueIndex() / 2;
		while (parent >= 1) {
			T parentNode = nodes[parent];
			if (hasHigherPriority(parentNode, node)) break;

			// Node has lower priority value, so move it up the heap
			swap(node, parentNode); // For some reason, this is faster with Swap() rather than (less..?) individual operations, like in
															 // CascadeDown()

			parent = node.getQueueIndex() / 2;
		}
	}

	private void cascadeDown(T node) {
		// aka Heapify-down
		T newParent;
		int finalQueueIndex = node.getQueueIndex();
		while (true) {
			newParent = node;
			int childLeftIndex = 2 * finalQueueIndex;

			// Check if the left-child is higher-priority than the current node
			if (childLeftIndex > numNodes) {
				// This could be placed outside the loop, but then we'd have to check newParent != node twice
				node.setQueueIndex(finalQueueIndex);
				nodes[finalQueueIndex] = node;
				break;
			}

			T childLeft = nodes[childLeftIndex];
			if (hasHigherPriority(childLeft, newParent)) {
				newParent = childLeft;
			}

			// Check if the right-child is higher-priority than either the current node or the left child
			int childRightIndex = childLeftIndex + 1;
			if (childRightIndex <= numNodes) {
				T childRight = nodes[childRightIndex];
				if (hasHigherPriority(childRight, newParent)) {
					newParent = childRight;
				}
			}

			// If either of the children has higher (smaller) priority, swap and continue cascading
			if (newParent != node) {
				// Move new parent to its new index. node will be moved once, at the end
				// Doing it this way is one less assignment operation than calling Swap()
				nodes[finalQueueIndex] = newParent;

				int temp = newParent.getQueueIndex();
				newParent.setQueueIndex(finalQueueIndex);
				finalQueueIndex = temp;
			} else {
				// See note above
				node.setQueueIndex(finalQueueIndex);
				nodes[finalQueueIndex] = node;
				break;
			}
		}
	}

	/// Returns true if 'higher' has higher priority than 'lower', false otherwise.
	/// Note that calling HasHigherPriority(node, node) (ie. both arguments the same node) will return false
	private boolean hasHigherPriority(T higher, T lower) {
		return (higher.getPriority() < lower.getPriority());
	}

	/// Removes the head of the queue and returns it.
	/// If queue is empty, result is undefined
	/// O(log n)
	public T dequeue() {
		if (numNodes <= 0) {
			throw new InvalidParameterException("Cannot call Dequeue() on an empty queue");
		}

		if (!IsValidQueue()) {
			throw new InvalidParameterException("Queue has been corrupted (Did you update a node priority manually instead of calling UpdatePriority()?" + "Or add the same node to two different queues?)");
		}

		T returnMe = nodes[1];
		remove(returnMe);
		return returnMe;
	}

	/// Resize the queue so it can accept more nodes. All currently enqueued nodes are remain.
	/// Attempting to decrease the queue size to a size too small to hold the existing nodes results in undefined behavior
	/// O(n)
	public void resize(int maxNodes) {
		if (maxNodes <= 0) {
			throw new InvalidParameterException("Queue size cannot be smaller than 1");
		}

		if (maxNodes < numNodes) {
			throw new InvalidParameterException("Called Resize(" + maxNodes + "), but current queue contains " + numNodes + " nodes");
		}

		@SuppressWarnings("unchecked")
		T[] newArray = (T[]) new Object[maxNodes + 1];
		int highestIndexToCopy = Math.min(maxNodes, numNodes);
		for (int i = 1; i <= highestIndexToCopy; i++) {
			newArray[i] = nodes[i];
		}
		nodes = newArray;
	}

	/// Returns the head of the queue, without removing it (use Dequeue() for that).
	/// If the queue is empty, behavior is undefined.
	/// O(1)
	public T getFirst() {
		if (numNodes <= 0) {
			throw new InvalidParameterException("Cannot call .getFirst() on an empty queue");
		}

		return nodes[1];
	}

	/// This method must be called on a node every time its priority changes while it is in the queue.
	/// <b>Forgetting to call this method will result in a corrupted queue!</b>
	/// Calling this method on a node not in the queue results in undefined behavior
	/// O(log n)
	public void updatePriority(T node, float priority) {
		if (node == null) {
			throw new NullPointerException("node");
		}
		if (!contains(node)) {
			throw new InvalidParameterException("Cannot call updatePriority() on a node which is not enqueued: " + node);
		}

		node.setPriority(priority);
		onNodeUpdated(node);
	}

	private void onNodeUpdated(T node) {
		// Bubble the updated node up or down as appropriate
		int parentIndex = node.getQueueIndex() / 2;
		T parentNode = nodes[parentIndex];

		if (parentIndex > 0 && hasHigherPriority(node, parentNode)) {
			cascadeUp(node);
		} else {
			// Note that CascadeDown will be called if parentNode == node (that is, node is the root)
			cascadeDown(node);
		}
	}

	/// Removes a node from the queue. The node does not need to be the head of the queue.
	/// If the node is not in the queue, the result is undefined. If unsure, check Contains() first
	/// O(log n)
	public void remove(T node) {
		if (node == null) {
			throw new NullPointerException("node");
		}
		if (!contains(node)) {
			throw new InvalidParameterException("Cannot call Remove() on a node which is not enqueued: " + node);
		}

		// If the node is already the last node, we can remove it immediately
		if (node.getQueueIndex() == numNodes) {
			nodes[numNodes] = null;
			numNodes--;
			return;
		}

		// Swap the node with the last node
		T formerLastNode = nodes[numNodes];
		swap(node, formerLastNode);
		nodes[numNodes] = null;
		numNodes--;

		// Now bubble formerLastNode (which is no longer the last node) up or down as appropriate
		onNodeUpdated(formerLastNode);
	}

//	public IEnumerator<T> getEnumerator()
//        {
//            for (int i = 1; i <= numNodes; i++)
//                yield return nodes[i];
//        }

	/// <b>Should not be called in production code.</b>
	/// Checks to make sure the queue is still in a valid state. Used for testing/debugging the queue.
	public boolean IsValidQueue() {
		for (int i = 1; i < nodes.length; i++) {
			if (nodes[i] != null) {
				int childLeftIndex = 2 * i;
				if (childLeftIndex < nodes.length && nodes[childLeftIndex] != null && hasHigherPriority(nodes[childLeftIndex], nodes[i])) return false;

				int childRightIndex = childLeftIndex + 1;
				if (childRightIndex < nodes.length && nodes[childRightIndex] != null && hasHigherPriority(nodes[childRightIndex], nodes[i])) return false;
			}
		}
		return true;
	}
}
