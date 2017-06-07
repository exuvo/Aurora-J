package se.exuvo.aurora.utils;

import java.io.Serializable;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.NumberUtils;

/** A convenient 2D circle class.
 * @author mzechner */
public class CircleL implements Serializable {
	private static final long serialVersionUID = -2015935330063547868L;
	
	public long x, y;
	public float radius;

	/** Constructs a new circle with all values set to zero */
	public CircleL () {

	}

	/** Constructs a new circle with the given X and Y coordinates and the given radius.
	 * 
	 * @param x X coordinate
	 * @param y Y coordinate
	 * @param radius The radius of the circle */
	public CircleL (long x, long y, float radius) {
		this.x = x;
		this.y = y;
		this.radius = radius;
	}

	/** Constructs a new circle using a given {@link Vector2} that contains the desired X and Y coordinates, and a given radius.
	 * 
	 * @param position The position {@link Vector2}.
	 * @param radius The radius */
	public CircleL (Vector2L position, float radius) {
		this.x = position.x;
		this.y = position.y;
		this.radius = radius;
	}

	/** Copy constructor
	 * 
	 * @param circle The circle to construct a copy of. */
	public CircleL (CircleL circle) {
		this.x = circle.x;
		this.y = circle.y;
		this.radius = circle.radius;
	}

	/** Creates a new {@link CircleL} in terms of its center and a point on its edge.
	 * 
	 * @param center The center of the new circle
	 * @param edge Any point on the edge of the given circle */
	public CircleL (Vector2L center, Vector2 edge) {
		this.x = center.x;
		this.y = center.y;
		this.radius = Vector2.len(center.x - edge.x, center.y - edge.y);
	}

	/** Sets a new location and radius for this circle.
	 * 
	 * @param x X coordinate
	 * @param y Y coordinate
	 * @param radius CircleL radius */
	public void set (long x, long y, float radius) {
		this.x = x;
		this.y = y;
		this.radius = radius;
	}

	/** Sets a new location and radius for this circle.
	 * 
	 * @param position Position {@link Vector2} for this circle.
	 * @param radius CircleL radius */
	public void set (Vector2L position, float radius) {
		this.x = position.x;
		this.y = position.y;
		this.radius = radius;
	}

	/** Sets a new location and radius for this circle, based upon another circle.
	 * 
	 * @param circle The circle to copy the position and radius of. */
	public void set (CircleL circle) {
		this.x = circle.x;
		this.y = circle.y;
		this.radius = circle.radius;
	}

	/** Sets this {@link CircleL}'s values in terms of its center and a point on its edge.
	 * 
	 * @param center The new center of the circle
	 * @param edge Any point on the edge of the given circle */
	public void set (Vector2L center, Vector2L edge) {
		this.x = center.x;
		this.y = center.y;
		this.radius = Vector2.len(center.x - edge.x, center.y - edge.y);
	}

	/** Sets the x and y-coordinates of circle center from vector
	 * @param position The position vector */
	public void setPosition (Vector2L position) {
		this.x = position.x;
		this.y = position.y;
	}

	/** Sets the x and y-coordinates of circle center
	 * @param x The x-coordinate
	 * @param y The y-coordinate */
	public void setPosition (long x, long y) {
		this.x = x;
		this.y = y;
	}

	/** Sets the x-coordinate of circle center
	 * @param x The x-coordinate */
	public void setX (long x) {
		this.x = x;
	}

	/** Sets the y-coordinate of circle center
	 * @param y The y-coordinate */
	public void setY (long y) {
		this.y = y;
	}

	/** Sets the radius of circle
	 * @param radius The radius */
	public void setRadius (float radius) {
		this.radius = radius;
	}

	/**
	 * Checks whether or not this circle contains a given point.
	 * 
	 * @param x X coordinate
	 * @param y Y coordinate
	 * @return true if this circle contains the given point.
	 */
	public boolean contains(long x, long y) {
		double xd = this.x - x;
		double yd = this.y - y;
		return xd * xd + yd * yd <= radius * radius;
		
//		BigInt X = new BigInt(x);
//		X.mul(x);
//		BigInt Y = new BigInt(y);
//		Y.mul(y);
//		X.add(Y);
//		return X.compareTo(new BigInt((long) (radius * radius))) <= 0;
	}

	/** Checks whether or not this circle contains a given point.
	 * 
	 * @param point The {@link Vector2} that contains the point coordinates.
	 * 
	 * @return true if this circle contains this point; false otherwise. */
	public boolean contains (Vector2L point) {
		return contains(point.x, point.y);
	}

	/**
	 * @param c the other {@link CircleL}
	 * @return whether this circle contains the other circle.
	 */
	public boolean contains(CircleL c) {
		final float radiusDiff = radius - c.radius;
		if (radiusDiff < 0f) return false; // Can't contain bigger circle
		final double dx = x - c.x;
		final double dy = y - c.y;
		final double dst = dx * dx + dy * dy;
		final float radiusSum = radius + c.radius;
		return (!(radiusDiff * radiusDiff < dst) && (dst < radiusSum * radiusSum));
	}

	/** @param c the other {@link CircleL}
	 * @return whether this circle overlaps the other circle. */
	public boolean overlaps (CircleL c) {
		double dx = x - c.x;
		double dy = y - c.y;
		double distance = dx * dx + dy * dy;
		float radiusSum = radius + c.radius;
		return distance < radiusSum * radiusSum;
	}

	/** Returns a {@link String} representation of this {@link CircleL} of the form {@code x,y,radius}. */
	@Override
	public String toString () {
		return x + "," + y + "," + radius;
	}

	/** @return The circumference of this circle (as 2 * {@link MathUtils#PI2}) * {@code radius} */
	public float circumference () {
		return this.radius * MathUtils.PI2;
	}

	/** @return The area of this circle (as {@link MathUtils#PI} * radius * radius). */
	public float area () {
		return this.radius * this.radius * MathUtils.PI;
	}

	@Override
	public boolean equals (Object o) {
		if (o == this) return true;
		if (o == null || o.getClass() != this.getClass()) return false;
		CircleL c = (CircleL)o;
		return this.x == c.x && this.y == c.y && this.radius == c.radius;
	}

	@Override
	public int hashCode () {
		final int prime = 41;
		long result = 1;
		result = prime * result + NumberUtils.floatToRawIntBits(radius);
		result = prime * result + x;
		result = prime * result + y;
		return Long.hashCode(result);
	}
}
