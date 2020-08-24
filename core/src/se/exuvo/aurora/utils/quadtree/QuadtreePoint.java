package se.exuvo.aurora.utils.quadtree;

import com.artemis.utils.IntBag;
import org.jetbrains.annotations.NotNull;

import java.security.InvalidParameterException;

/**
 * https://stackoverflow.com/questions/41946007/efficient-and-well-explained-implementation-of-a-quadtree-for-2d-collision-det
 * @author Dragon Energy
 * @author exuvo
 */
public class QuadtreePoint {
	// ----------------------------------------------------------------------------------------
	// Element fields:
	// ----------------------------------------------------------------------------------------
	// Stores the point the element is at.
	public static final int elt_idx_mx = 0, elt_idx_my = 1;

	// Stores the ID of the element.
	public static final int elt_idx_id = 2;

	// Stores all the elements in the quadtree.
	public final IntList elts = new IntList(3, 128);

	// ----------------------------------------------------------------------------------------
	// Element node fields:
	// ----------------------------------------------------------------------------------------
	// Points to the next element in the leaf node. A value of -1 indicates the end of the list.
	public static final int enode_idx_next = 0;

	// Stores the element index.
	static final int enode_idx_elementIdx = 1;

	// Stores all the element nodes in the quadtree.
	public final IntList enodes = new IntList(2, 128);

	// ----------------------------------------------------------------------------------------
	// Node fields:
	// ----------------------------------------------------------------------------------------
	// Points to the first child if this node is a branch or the first element node if this node is a leaf.
	// branch: node_idx_fc is node index. Consecutive child order: TL TR BL BR
	// leaf: node_idx_fc is element node index.
	public static final int node_idx_fc = 0;

	// Stores the number of elements in the node or -1 if it is not a leaf.
	static final int node_idx_size = 1;

	// Stores all the nodes in the quadtree. The first node in this sequence is always the root.
	public final IntList nodes = new IntList(2, 128);

	// ----------------------------------------------------------------------------------------
	// Node traversal data fields:
	// ----------------------------------------------------------------------------------------
	static final int nd_num = 6;

	// Stores the extents of the node using a centered rectangle and half-size.
	static final int nd_idx_mx = 0, nd_idx_my = 1, nd_idx_sx = 2, nd_idx_sy = 3;

	// Stores the index of the node.
	static final int nd_idx_index = 4;

	// Stores the depth of the node.
	static final int nd_idx_depth = 5;

	// ----------------------------------------------------------------------------------------
	// Data Members
	// ----------------------------------------------------------------------------------------
	// Stores the quadtree extents.
	private final int root_mx, root_my, root_sx, root_sy;

	// Maximum allowed elements in a leaf before the leaf is subdivided/split unless
	// the leaf is at the maximum allowed tree depth.
	private int max_elements;

	// Stores the maximum depth allowed for the quadtree.
	private final int max_depth;

	/**
	 * Creates a quadtree with the requested extents, maximum elements per leaf, and maximum tree depth.
 	 */
	public QuadtreePoint(int width, int height, int start_max_elements, int start_max_depth) {
		max_elements = start_max_elements;
		max_depth = start_max_depth;

		// Insert the root node to the qt.
		nodes.insert();
		nodes.set(0, node_idx_fc, -1);
		nodes.set(0, node_idx_size, 0);

		// Set the extents of the root node.
		root_mx = width / 2;
		root_my = height / 2;
		root_sx = root_mx;
		root_sy = root_my;
	}

	/**
	 * Inserts an element with the specified id and size
	 * @return element index
 	 */
	public int insert(int id, int x, int y) {
		// Insert a new element.
		final int new_element = elts.insert();

		// Set the fields of the new element.
		elts.set(new_element, elt_idx_mx, x);
		elts.set(new_element, elt_idx_my, y);
		elts.set(new_element, elt_idx_id, id);

		// Insert the element to the appropriate leaf node(s).
		node_insert(0, 0, root_mx, root_my, root_sx, root_sy, new_element);
		return new_element;
	}

	/**
	 * Removes the specified element from the tree.
	 */
	public void remove(int elementIdx) {
		final int mx = elts.get(elementIdx, elt_idx_mx);
		final int my = elts.get(elementIdx, elt_idx_my);
		IntList leaf = find_leaf(0, 0, root_mx, root_my, root_sx, root_sy, mx, my);
		
		final int nd_index = leaf.get(0, nd_idx_index);

		// Walk the list until we find the element node.
		int node_index = nodes.get(nd_index, node_idx_fc);
		int prev_index = -1;
		while (node_index != -1 && enodes.get(node_index, enode_idx_elementIdx) != elementIdx) {
			prev_index = node_index;
			node_index = enodes.get(node_index, enode_idx_next);
		}

		if (node_index != -1) {
			// Remove the element node.
			final int next_index = enodes.get(node_index, enode_idx_next);
			if (prev_index == -1) {
				nodes.set(nd_index, node_idx_fc, next_index);
			} else {
				enodes.set(prev_index, enode_idx_next, next_index);
			}
			enodes.erase(node_index);

			// Decrement the leaf element count.
			nodes.set(nd_index, node_idx_size, nodes.get(nd_index, node_idx_size) - 1);
		}

		// Remove the element.
		elts.erase(elementIdx);
	}

	/**
	 * Cleans up the tree, removing empty leaves.
 	 */
	public boolean cleanup() {
		boolean changed = false;
		
		// Only process the root if it's not a leaf.
		if (nodes.get(0, node_idx_size) == -1) {
			// Push the root index to the stack.
			IntList to_process = new IntList(1, 4 * max_depth);
			to_process.set(to_process.pushBack(), 0, 0);

			while (to_process.size() > 0) {
				// Pop a node from the stack.
				final int node = to_process.get(to_process.size() - 1, 0);
				final int fc = nodes.get(node, node_idx_fc);
				int num_empty_leaves = 0;
				to_process.popBack();

				// Loop through the children.
				for (int j = 0; j < 4; ++j) {
					final int child = fc + j;

					// Increment empty leaf count if the child is an empty
					// leaf. Otherwise if the child is a branch, add it to
					// the stack to be processed in the next iteration.
					final int size = nodes.get(child, node_idx_size);
					if (size == 0) {
						++num_empty_leaves;
					} else if (size == -1) {
						// Push the child index to the stack.
						to_process.set(to_process.pushBack(), 0, child);
					}
				}

				// If all the children were empty leaves, remove them and
				// make this node the new empty leaf.
				if (num_empty_leaves == 4) {
					// Remove all 4 children in reverse order so that they
					// can be reclaimed on subsequent insertions in proper
					// order.
					nodes.erase(fc + 3);
					nodes.erase(fc + 2);
					nodes.erase(fc + 1);
					nodes.erase(fc + 0);

					// Make this node the new empty leaf.
					nodes.set(node, node_idx_fc, -1);
					nodes.set(node, node_idx_size, 0);
					
					changed = true;
				}
			}
		}
		
		return changed;
	}
	
	/**
	 * Cleans up the tree, removing empty leaves and consolidating mostly empty child nodes.
	 */
	public boolean cleanupFull() {
		boolean changed = false;
		
		// Only process the root if it's not a leaf.
		if (nodes.get(0, node_idx_size) == -1) {
			// Push the root index to the stack.
			IntList elements = new IntList(1, max_elements);;
			IntList to_process = new IntList(1, 4 * max_depth);
			to_process.set(to_process.pushBack(), 0, 0);
			
			while (to_process.size() > 0) {
				// Pop a node from the stack.
				final int node = to_process.get(to_process.size() - 1, 0);
				final int fc = nodes.get(node, node_idx_fc);
				int num_empty_leaves = 0;
				int num_elements = 0;
				to_process.popBack();
				
				// Loop through the children.
				for (int j = 0; j < 4; ++j) {
					final int child = fc + j;
					
					// Increment empty leaf count if the child is an empty
					// leaf. Otherwise if the child is a branch, add it to
					// the stack to be processed in the next iteration.
					final int size = nodes.get(child, node_idx_size);
					if (size == 0) {
						++num_empty_leaves;
					} else if (size == -1) {
						// Push the child index to the stack.
						to_process.set(to_process.pushBack(), 0, child);
						num_elements = -1;
					} else if (num_elements != -1) {
						num_elements += size;
					}
				}
				
				// If all the children were empty leaves, remove them and
				// make this node the new empty leaf.
				if (num_empty_leaves == 4) {
					// Remove all 4 children in reverse order so that they
					// can be reclaimed on subsequent insertions in proper order.
					nodes.erase(fc + 3);
					nodes.erase(fc + 2);
					nodes.erase(fc + 1);
					nodes.erase(fc + 0);
					
					// Make this node the new empty leaf.
					nodes.set(node, node_idx_fc, -1);
					nodes.set(node, node_idx_size, 0);
					
					changed = true;
					
				} else if (num_elements != -1 && num_elements <= max_elements / 2) {
					// Consolidate children
					for (int j = 0; j < 4; ++j) {
						final int child = fc + j;
						
						while (nodes.get(child, node_idx_fc) != -1) {
							final int index = nodes.get(child, node_idx_fc);
							final int next_index = enodes.get(index, enode_idx_next);
							final int elt = enodes.get(index, enode_idx_elementIdx);
							
							// Pop off the element node from the leaf and remove it from the qt.
							nodes.set(child, node_idx_fc, next_index);
							enodes.erase(index);
							
							// Insert element to the list.
							elements.set(elements.pushBack(), 0, elt);
						}
					}
					
					assert(num_elements == elements.size());
					
					// Remove all 4 children in reverse order so that they
					// can be reclaimed on subsequent insertions in proper order.
					nodes.erase(fc + 3);
					nodes.erase(fc + 2);
					nodes.erase(fc + 1);
					nodes.erase(fc + 0);
					
					nodes.set(node, node_idx_fc, -1);
					nodes.set(node, node_idx_size, num_elements);
					
					// Transfer the elements
					for (int i = 0; i < num_elements; ++i) {
						final int nodeOldFirstChild = nodes.get(node, node_idx_fc);
						final int newElementNodeIdx = enodes.insert();
						nodes.set(node, node_idx_fc, newElementNodeIdx);
						enodes.set(newElementNodeIdx, enode_idx_next, nodeOldFirstChild);
						enodes.set(newElementNodeIdx, enode_idx_elementIdx, elements.get(i, 0));
					}
					
					elements.clear();
					changed = true;
				}
			}
		}
		
		return changed;
	}

	/**
	 * Returns a list of elements found in the specified rectangle.
 	 */
	public IntBag query(int x1, int y1, int x2, int y2) {
		return query(x1, y1, x2, y2, -1);
	}

	/**
	 * Returns a list of elements found in the specified rectangle excluding the specified element to omit.
 	 */
	public IntBag query(int x1, int y1, int x2, int y2, int omit_element) {
		IntBag out = new IntBag(64);

		// Find the leaves that intersect the specified query rectangle.
		final int qlft = x1;
		final int qtop = y1;
		final int qrgt = x2;
		final int qbtm = y2;
		IntList leaves = find_leaves(0, 0, root_mx, root_my, root_sx, root_sy, qlft, qtop, qrgt, qbtm);

		// For each leaf node, look for elements that intersect.
		for (int j = 0; j < leaves.size(); ++j) {
			final int nd_index = leaves.get(j, nd_idx_index);

			// Walk the list and add elements that intersect.
			int elt_node_index = nodes.get(nd_index, node_idx_fc);
			while (elt_node_index != -1) {
				final int elementIdx = enodes.get(elt_node_index, enode_idx_elementIdx);
				final int mx = elts.get(elementIdx, elt_idx_mx);
				final int my = elts.get(elementIdx, elt_idx_my);
				if (elementIdx != omit_element) { // Don't do intersection test here as tree data is not always up to date.  && intersect(mx, my, qlft, qtop, qrgt, qbtm)
					out.add(elts.get(elementIdx, elt_idx_id));
				}
				elt_node_index = enodes.get(elt_node_index, enode_idx_next);
			}
		}

		return out;
	}
	
//	private static boolean intersect(int x, int y, int l, int t, int r, int b) {
//		return l <= x && x <= r && t <= y && y <= b;
//	}
	
	/**
	 * Traverses all the nodes in the tree, calling 'branch' for branch nodes and 'leaf' for leaf nodes.
 	 */
	public void traverse(QuadtreeVisitor visitor) {
		IntList to_process = new IntList(nd_num, 4 * max_depth);
		pushNode(to_process, 0, 0, root_mx, root_my, root_sx, root_sy);

		while (to_process.size() > 0) {
			final int back_idx = to_process.size() - 1;
			final int nd_mx = to_process.get(back_idx, nd_idx_mx);
			final int nd_my = to_process.get(back_idx, nd_idx_my);
			final int nd_sx = to_process.get(back_idx, nd_idx_sx);
			final int nd_sy = to_process.get(back_idx, nd_idx_sy);
			final int nd_index = to_process.get(back_idx, nd_idx_index);
			final int nd_depth = to_process.get(back_idx, nd_idx_depth);
			to_process.popBack();

			if (nodes.get(nd_index, node_idx_size) == -1) {
				// Push the children of the branch to the stack.
				final int fc = nodes.get(nd_index, node_idx_fc);
				final int hx = nd_sx >> 1, hy = nd_sy >> 1;
				final int l = nd_mx - hx, t = nd_my - hy, r = nd_mx + hx, b = nd_my + hy;
				pushNode(to_process, fc + 0, nd_depth + 1, l, t, hx, hy);
				pushNode(to_process, fc + 1, nd_depth + 1, r, t, hx, hy);
				pushNode(to_process, fc + 2, nd_depth + 1, l, b, hx, hy);
				pushNode(to_process, fc + 3, nd_depth + 1, r, b, hx, hy);
				visitor.branch(nd_index, nd_depth, nd_mx, nd_my, nd_sx, nd_sy);
			} else
				visitor.leaf(nd_index, nd_depth, nd_mx, nd_my, nd_sx, nd_sy);
		}
	}

	private static void pushNode(IntList nodes, int nd_index, int nd_depth, int nd_mx, int nd_my, int nd_sx, int nd_sy) {
		final int back_idx = nodes.pushBack();
		nodes.set(back_idx, nd_idx_mx, nd_mx);
		nodes.set(back_idx, nd_idx_my, nd_my);
		nodes.set(back_idx, nd_idx_sx, nd_sx);
		nodes.set(back_idx, nd_idx_sy, nd_sy);
		nodes.set(back_idx, nd_idx_index, nd_index);
		nodes.set(back_idx, nd_idx_depth, nd_depth);
	}
	
	private IntList find_leaves(int node, int depth,
															int mx, int my, int sx, int sy,
															int lft, int top, int rgt, int btm) {
		IntList leaves = new IntList(nd_num, 32);
		IntList to_process = new IntList(nd_num, 4 * max_depth);
		pushNode(to_process, node, depth, mx, my, sx, sy);
		
		while (to_process.size() > 0) {
			final int back_idx = to_process.size() - 1;
			final int nd_mx = to_process.get(back_idx, nd_idx_mx);
			final int nd_my = to_process.get(back_idx, nd_idx_my);
			final int nd_sx = to_process.get(back_idx, nd_idx_sx);
			final int nd_sy = to_process.get(back_idx, nd_idx_sy);
			final int nd_index = to_process.get(back_idx, nd_idx_index);
			final int nd_depth = to_process.get(back_idx, nd_idx_depth);
			to_process.popBack();
			
			// If this node is a leaf, insert it to the list.
			if (nodes.get(nd_index, node_idx_size) != -1)
				pushNode(leaves, nd_index, nd_depth, nd_mx, nd_my, nd_sx, nd_sy);
			else {
				// Otherwise push the children that intersect the rectangle.
				final int fc = nodes.get(nd_index, node_idx_fc);
				final int hx = nd_sx / 2, hy = nd_sy / 2;
				final int l = nd_mx - hx, t = nd_my - hy, r = nd_mx + hx, b = nd_my + hy;
				
				if (top <= nd_my) {
					if (lft <= nd_mx)
						pushNode(to_process, fc + 0, nd_depth + 1, l, t, hx, hy);
					if (rgt > nd_mx)
						pushNode(to_process, fc + 1, nd_depth + 1, r, t, hx, hy);
				}
				if (btm > nd_my) {
					if (lft <= nd_mx)
						pushNode(to_process, fc + 2, nd_depth + 1, l, b, hx, hy);
					if (rgt > nd_mx)
						pushNode(to_process, fc + 3, nd_depth + 1, r, b, hx, hy);
				}
			}
		}
		return leaves;
	}

	private IntList find_leaf(int node, int depth,
															int mx, int my, int sx, int sy,
															int x, int y) {
		IntList to_process = new IntList(nd_num, 1);
		pushNode(to_process, node, depth, mx, my, sx, sy);

		while (to_process.size() > 0) {
			final int back_idx = to_process.size() - 1;
			final int nd_mx = to_process.get(back_idx, nd_idx_mx);
			final int nd_my = to_process.get(back_idx, nd_idx_my);
			final int nd_sx = to_process.get(back_idx, nd_idx_sx);
			final int nd_sy = to_process.get(back_idx, nd_idx_sy);
			final int nd_index = to_process.get(back_idx, nd_idx_index);
			final int nd_depth = to_process.get(back_idx, nd_idx_depth);
			to_process.popBack();

			// If this node is a leaf, insert it to the list.
			if (nodes.get(nd_index, node_idx_size) != -1) {
				IntList leaf = new IntList(nd_num, 1);
				pushNode(leaf, nd_index, nd_depth, nd_mx, nd_my, nd_sx, nd_sy);
				return leaf;
			} else {
				// Otherwise push the child that contains the point.
				final int fc = nodes.get(nd_index, node_idx_fc);
				final int hx = nd_sx / 2, hy = nd_sy / 2;
				final int l = nd_mx - hx, t = nd_my - hy, r = nd_mx + hx, b = nd_my + hy;

				// With bounds checks
//				final int nd_l = nd_mx - nd_sx, nd_t = nd_my - nd_sy, nd_r = nd_mx + nd_sx, nd_b = nd_my + nd_sy;
//				if (nd_t <= y && y < nd_my) {
//					if (nd_l <= x && x < nd_mx)
//						pushNode(to_process, fc + 0, nd_depth + 1, l, t, hx, hy);
//					if (nd_mx <= x && x < nd_r)
//						pushNode(to_process, fc + 1, nd_depth + 1, r, t, hx, hy);
//				} else if (nd_my <= y && y < nd_b) {
//					if (nd_l <= x && x < nd_mx)
//						pushNode(to_process, fc + 2, nd_depth + 1, l, b, hx, hy);
//					if (nd_mx <= x && x < nd_r)
//						pushNode(to_process, fc + 3, nd_depth + 1, r, b, hx, hy);
//				}
				
				// We assume the point is within bounds already, otherwise the caller fucked up
				if (y <= nd_my) {
					if (x <= nd_mx) {
						pushNode(to_process, fc + 0, nd_depth + 1, l, t, hx, hy);
					} else
						pushNode(to_process, fc + 1, nd_depth + 1, r, t, hx, hy);
				} else {
					if (x <= nd_mx) {
						pushNode(to_process, fc + 2, nd_depth + 1, l, b, hx, hy);
					} else
						pushNode(to_process, fc + 3, nd_depth + 1, r, b, hx, hy);
				}
				
				// inside AABB check
//				if (top <= nd_my) {
//					if (lft <= nd_mx)
//						pushNode(to_process, fc + 0, nd_depth + 1, l, t, hx, hy);
//					if (rgt > nd_mx)
//						pushNode(to_process, fc + 1, nd_depth + 1, r, t, hx, hy);
//				}
//				if (btm > nd_my) {
//					if (lft <= nd_mx)
//						pushNode(to_process, fc + 2, nd_depth + 1, l, b, hx, hy);
//					if (rgt > nd_mx)
//						pushNode(to_process, fc + 3, nd_depth + 1, r, b, hx, hy);
//				}
			}
		}
		return null;
	}

	private void node_insert(int index, int depth, int mx, int my, int sx, int sy, int elementIdx) {
		// Find the leaves and insert the element to all the leaves found.
		final int emx = elts.get(elementIdx, elt_idx_mx);
		final int emy = elts.get(elementIdx, elt_idx_my);
		IntList leaf = find_leaf(index, depth, mx, my, sx, sy, emx, emy);

		if (leaf != null) {
			final int nd_mx = leaf.get(0, nd_idx_mx);
			final int nd_my = leaf.get(0, nd_idx_my);
			final int nd_sx = leaf.get(0, nd_idx_sx);
			final int nd_sy = leaf.get(0, nd_idx_sy);
			final int nd_index = leaf.get(0, nd_idx_index);
			final int nd_depth = leaf.get(0, nd_idx_depth);
			leaf_insert(nd_index, nd_depth, nd_mx, nd_my, nd_sx, nd_sy, elementIdx);
		}
	}

	private void leaf_insert(int node, int depth, int mx, int my, int sx, int sy, int elementIdx) {
		// Insert the element node to the leaf.
		final int nodeOldFirstChild = nodes.get(node, node_idx_fc);
		final int newElementNodeIdx = enodes.insert();
		nodes.set(node, node_idx_fc, newElementNodeIdx);
		enodes.set(newElementNodeIdx, enode_idx_next, nodeOldFirstChild);
		enodes.set(newElementNodeIdx, enode_idx_elementIdx, elementIdx);

		final int nodeSize = nodes.get(node, node_idx_size);

		// If the leaf is full, split it.
		if (nodeSize == max_elements && depth < max_depth) {
			// Transfer elements from the leaf node to a list of elements.
			IntList elements = new IntList(1, 1 + max_elements);;
			while (nodes.get(node, node_idx_fc) != -1) {
				final int index = nodes.get(node, node_idx_fc);
				final int next_index = enodes.get(index, enode_idx_next);
				final int elt = enodes.get(index, enode_idx_elementIdx);

				// Pop off the element node from the leaf and remove it from the qt.
				nodes.set(node, node_idx_fc, next_index);
				enodes.erase(index);

				// Insert element to the list.
				elements.set(elements.pushBack(), 0, elt);
			}

			// Start by allocating 4 child nodes.
			final int fc = nodes.insert();
			nodes.insert();
			nodes.insert();
			nodes.insert();
			nodes.set(node, node_idx_fc, fc);

			// Initialize the new child nodes.
			for (int j = 0; j < 4; ++j) {
				nodes.set(fc + j, node_idx_fc, -1);
				nodes.set(fc + j, node_idx_size, 0);
			}

			// Transfer the elements in the former leaf node to its new children.
			nodes.set(node, node_idx_size, -1);
			for (int j = 0; j < elements.size(); ++j) {
				node_insert(node, depth, mx, my, sx, sy, elements.get(j, 0));
			}
		} else {
			// Increment the leaf element count.
			nodes.set(node, node_idx_size, nodeSize + 1);
		}
	}
	
	public void copy(@NotNull QuadtreePoint tree) {
		elts.copy(tree.elts);
		enodes.copy(tree.enodes);
		nodes.copy(tree.nodes);
	}
}