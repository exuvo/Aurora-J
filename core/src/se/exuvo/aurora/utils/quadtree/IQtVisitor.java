package se.exuvo.aurora.utils.quadtree;

public interface IQtVisitor {
	// Called when traversing a branch node.
	// (mx, my) indicate the center of the node's AABB.
	// (sx, sy) indicate the half-size of the node's AABB.
	void branch(QuadtreeAABB qt, int node, int depth, int mx, int my, int sx, int sy);
	
	// Called when traversing a leaf node.
	// (mx, my) indicate the center of the node's AABB.
	// (sx, sy) indicate the half-size of the node's AABB.
	void leaf(QuadtreeAABB qt, int node, int depth, int mx, int my, int sx, int sy);
}
