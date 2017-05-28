package se.exuvo.aurora.utils;

public class Vector2Long {

	public long x;
	public long y;

	/** Constructs a new 2D grid polong. */
	public Vector2Long() {}

	/**
	 * Constructs a new 2D grid polong.
	 * 
	 * @param x X coordinate
	 * @param y Y coordinate
	 */
	public Vector2Long(long x, long y) {
		this.x = x;
		this.y = y;
	}

	/**
	 * Copy constructor
	 * 
	 * @param polong The 2D grid polong to make a copy of.
	 */
	public Vector2Long(Vector2Long polong) {
		this.x = polong.x;
		this.y = polong.y;
	}

	/**
	 * Sets the coordinates of this 2D grid polong to that of another.
	 * 
	 * @param polong The 2D grid polong to copy the coordinates of.
	 * @return this 2D grid polong for chaining.
	 */
	public Vector2Long set(Vector2Long polong) {
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
	public Vector2Long set(long x, long y) {
		this.x = x;
		this.y = y;
		return this;
	}

	/**
	 * @param other The other polong
	 * @return the squared distance between this polong and the other polong.
	 */
	public float dst2(Vector2Long other) {
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
	public float dst(Vector2Long other) {
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
	public Vector2Long add(Vector2Long other) {
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
	public Vector2Long add(long x, long y) {
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
	public Vector2Long sub(Vector2Long other) {
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
	public Vector2Long sub(long x, long y) {
		this.x -= x;
		this.y -= y;
		return this;
	}

	/**
	 * @return a copy of this grid polong
	 */
	public Vector2Long cpy() {
		return new Vector2Long(this);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || o.getClass() != this.getClass()) return false;
		Vector2Long g = (Vector2Long) o;
		return this.x == g.x && this.y == g.y;
	}

	@Override
	public int hashCode() {
		final int prime = 53;
		long result = 1;
		result = prime * result + this.x;
		result = prime * result + this.y;
		return (int) result;
	}

	@Override
	public String toString() {
		return "(" + x + ", " + y + ")";
	}

}
