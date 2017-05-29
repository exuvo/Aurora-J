package se.exuvo.aurora.utils;

public class Vector2L {

	public long x;
	public long y;

	/** Constructs a new 2D grid polong. */
	public Vector2L() {}

	/**
	 * Constructs a new 2D grid polong.
	 * 
	 * @param x X coordinate
	 * @param y Y coordinate
	 */
	public Vector2L(long x, long y) {
		this.x = x;
		this.y = y;
	}

	/**
	 * Copy constructor
	 * 
	 * @param polong The 2D grid polong to make a copy of.
	 */
	public Vector2L(Vector2L polong) {
		this.x = polong.x;
		this.y = polong.y;
	}

	/**
	 * Sets the coordinates of this 2D grid polong to that of another.
	 * 
	 * @param polong The 2D grid polong to copy the coordinates of.
	 * @return this 2D grid polong for chaining.
	 */
	public Vector2L set(Vector2L polong) {
		this.x = polong.x;
		this.y = polong.y;
		return this;
	}

	/**
	 * Sets the coordinates of this 2D grid polong.
	 * 
	 * @param x X coordinate
	 * @param y Y coordinate
	 * @return this 2D grid polong for chaining.
	 */
	public Vector2L set(long x, long y) {
		this.x = x;
		this.y = y;
		return this;
	}

	/**
	 * @param other The other polong
	 * @return the squared distance between this polong and the other polong.
	 */
	public float dst2(Vector2L other) {
		long xd = other.x - x;
		long yd = other.y - y;

		return xd * xd + yd * yd;
	}

	/**
	 * @param x The x-coordinate of the other polong
	 * @param y The y-coordinate of the other polong
	 * @return the squared distance between this polong and the other polong.
	 */
	public float dst2(long x, long y) {
		long xd = x - this.x;
		long yd = y - this.y;

		return xd * xd + yd * yd;
	}

	/**
	 * @param other The other polong
	 * @return the distance between this polong and the other vector.
	 */
	public float dst(Vector2L other) {
		long xd = other.x - x;
		long yd = other.y - y;

		return (float) Math.sqrt(xd * xd + yd * yd);
	}

	/**
	 * @param x The x-coordinate of the other polong
	 * @param y The y-coordinate of the other polong
	 * @return the distance between this polong and the other polong.
	 */
	public float dst(long x, long y) {
		long xd = x - this.x;
		long yd = y - this.y;

		return (float) Math.sqrt(xd * xd + yd * yd);
	}

	/**
	 * Adds another 2D grid polong to this polong.
	 *
	 * @param other The other polong
	 * @return this 2d grid polong for chaining.
	 */
	public Vector2L add(Vector2L other) {
		x += other.x;
		y += other.y;
		return this;
	}

	/**
	 * Adds another 2D grid polong to this polong.
	 *
	 * @param x The x-coordinate of the other polong
	 * @param y The y-coordinate of the other polong
	 * @return this 2d grid polong for chaining.
	 */
	public Vector2L add(long x, long y) {
		this.x += x;
		this.y += y;
		return this;
	}

	/**
	 * Subtracts another 2D grid polong from this polong.
	 *
	 * @param other The other polong
	 * @return this 2d grid polong for chaining.
	 */
	public Vector2L sub(Vector2L other) {
		x -= other.x;
		y -= other.y;
		return this;
	}

	/**
	 * Subtracts another 2D grid polong from this polong.
	 *
	 * @param x The x-coordinate of the other polong
	 * @param y The y-coordinate of the other polong
	 * @return this 2d grid polong for chaining.
	 */
	public Vector2L sub(long x, long y) {
		this.x -= x;
		this.y -= y;
		return this;
	}

	/**
	 * @return a copy of this grid polong
	 */
	public Vector2L cpy() {
		return new Vector2L(this);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || o.getClass() != this.getClass()) return false;
		Vector2L g = (Vector2L) o;
		return this.x == g.x && this.y == g.y;
	}

	@Override
	public int hashCode() {
		final int prime = 53;
		long result = 1;
		result = prime * result + this.x;
		result = prime * result + this.y;
		return Long.hashCode(result);
	}

	@Override
	public String toString() {
		return "(" + x + ", " + y + ")";
	}

}
