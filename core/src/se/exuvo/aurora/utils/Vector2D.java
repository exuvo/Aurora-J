package se.exuvo.aurora.utils;

import java.io.Serializable;

import org.apache.commons.math3.util.FastMath;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.NumberUtils;

/**
 * Encapsulates a 2D vector. Allows chaining methods by returning a reference to itself
 * 
 * @author badlogicgames@gmail.com
 */
public class Vector2D implements Serializable {

	private static final long serialVersionUID = 913902788239530931L;
	static public final float DOUBLE_ROUNDING_ERROR = 0.000000000001f; // 64 bits

	public final static Vector2D X = new Vector2D(1, 0);
	public final static Vector2D Y = new Vector2D(0, 1);
	public final static Vector2D Zero = new Vector2D(0, 0);

	/** the x-component of this vector **/
	public double x;
	/** the y-component of this vector **/
	public double y;

	/** Constructs a new vector at (0,0) */
	public Vector2D() {}

	/**
	 * Constructs a vector with the given components
	 * 
	 * @param x The x-component
	 * @param y The y-component
	 */
	public Vector2D(double x, double y) {
		this.x = x;
		this.y = y;
	}

	/**
	 * Constructs a vector from the given vector
	 * 
	 * @param v The vector
	 */
	public Vector2D(Vector2D v) {
		set(v);
	}
	
	public Vector2D(Vector2 v) {
		set(v.x, v.y);
	}

	public Vector2D cpy() {
		return new Vector2D(this);
	}

	public static double len(double x, double y) {
		return (double) FastMath.sqrt(x * x + y * y);
	}

	public double len() {
		return (double) FastMath.sqrt(x * x + y * y);
	}

	public static double len2(double x, double y) {
		return x * x + y * y;
	}

	public double len2() {
		return x * x + y * y;
	}

	public Vector2D set(Vector2D v) {
		x = v.x;
		y = v.y;
		return this;
	}

	/**
	 * Sets the components of this vector
	 * 
	 * @param x The x-component
	 * @param y The y-component
	 * @return This vector for chaining
	 */
	public Vector2D set(double x, double y) {
		this.x = x;
		this.y = y;
		return this;
	}

	public Vector2D sub(Vector2D v) {
		x -= v.x;
		y -= v.y;
		return this;
	}

	/**
	 * Substracts the other vector from this vector.
	 * 
	 * @param x The x-component of the other vector
	 * @param y The y-component of the other vector
	 * @return This vector for chaining
	 */
	public Vector2D sub(double x, double y) {
		this.x -= x;
		this.y -= y;
		return this;
	}

	public Vector2D nor() {
		double len = len();
		if (len != 0) {
			x /= len;
			y /= len;
		}
		return this;
	}

	public Vector2D add(Vector2D v) {
		x += v.x;
		y += v.y;
		return this;
	}

	/**
	 * Adds the given components to this vector
	 * 
	 * @param x The x-component
	 * @param y The y-component
	 * @return This vector for chaining
	 */
	public Vector2D add(double x, double y) {
		this.x += x;
		this.y += y;
		return this;
	}

	public static double dot(double x1, double y1, double x2, double y2) {
		return x1 * x2 + y1 * y2;
	}

	public double dot(Vector2D v) {
		return x * v.x + y * v.y;
	}

	public double dot(double ox, double oy) {
		return x * ox + y * oy;
	}

	public Vector2D scl(double scalar) {
		x *= scalar;
		y *= scalar;
		return this;
	}

	/**
	 * Multiplies this vector by a scalar
	 * 
	 * @return This vector for chaining
	 */
	public Vector2D scl(double x, double y) {
		this.x *= x;
		this.y *= y;
		return this;
	}

	public Vector2D scl(Vector2D v) {
		this.x *= v.x;
		this.y *= v.y;
		return this;
	}
	
	public Vector2D div(double divider) {
		scl(1.0 / divider);
		return this;
	}

	public Vector2D mulAdd(Vector2D vec, double scalar) {
		this.x += vec.x * scalar;
		this.y += vec.y * scalar;
		return this;
	}

	public Vector2D mulAdd(Vector2D vec, Vector2D mulVec) {
		this.x += vec.x * mulVec.x;
		this.y += vec.y * mulVec.y;
		return this;
	}

	public static double dst(double x1, double y1, double x2, double y2) {
		final double x_d = x2 - x1;
		final double y_d = y2 - y1;
		return (double) FastMath.sqrt(x_d * x_d + y_d * y_d);
	}

	public double dst(Vector2D v) {
		final double x_d = v.x - x;
		final double y_d = v.y - y;
		return (double) FastMath.sqrt(x_d * x_d + y_d * y_d);
	}

	/**
	 * @param x The x-component of the other vector
	 * @param y The y-component of the other vector
	 * @return the distance between this and the other vector
	 */
	public double dst(double x, double y) {
		final double x_d = x - this.x;
		final double y_d = y - this.y;
		return (double) FastMath.sqrt(x_d * x_d + y_d * y_d);
	}

	public static double dst2(double x1, double y1, double x2, double y2) {
		final double x_d = x2 - x1;
		final double y_d = y2 - y1;
		return x_d * x_d + y_d * y_d;
	}

	public double dst2(Vector2D v) {
		final double x_d = v.x - x;
		final double y_d = v.y - y;
		return x_d * x_d + y_d * y_d;
	}

	/**
	 * @param x The x-component of the other vector
	 * @param y The y-component of the other vector
	 * @return the squared distance between this and the other vector
	 */
	public double dst2(double x, double y) {
		final double x_d = x - this.x;
		final double y_d = y - this.y;
		return x_d * x_d + y_d * y_d;
	}
	
	/**
	 * Errors between -5% (on axes) and +3% (on lobes) and an average error of +0.043%.
	 * From https://gamedev.stackexchange.com/a/69255/142645
	 */
	public double dstAprox(Vector2D v) {
		double dx = FastMath.abs(v.x - x);
		double dy = FastMath.abs(v.y - y);
		return 0.394 * (dx + dy) + 0.554 * FastMath.max(dx, dy);
	}

	public Vector2D limit(double limit) {
		return limit2(limit * limit);
	}

	public Vector2D limit2(double limit2) {
		double len2 = len2();
		if (len2 > limit2) {
			return scl((double) FastMath.sqrt(limit2 / len2));
		}
		return this;
	}

	public Vector2D clamp(double min, double max) {
		final double len2 = len2();
		if (len2 == 0f) return this;
		double max2 = max * max;
		if (len2 > max2) return scl((double) FastMath.sqrt(max2 / len2));
		double min2 = min * min;
		if (len2 < min2) return scl((double) FastMath.sqrt(min2 / len2));
		return this;
	}

	public Vector2D setLength(double len) {
		return setLength2(len * len);
	}

	public Vector2D setLength2(double len2) {
		double oldLen2 = len2();
		return (oldLen2 == 0 || oldLen2 == len2) ? this : scl((double) FastMath.sqrt(len2 / oldLen2));
	}

	/**
	 * Converts this {@code Vector2D} to a string in the format {@code (x,y)}.
	 * 
	 * @return a string representation of this object.
	 */

	public String toString() {
		return "(" + x + "," + y + ")";
	}

	/**
	 * Sets this {@code Vector2D} to the value represented by the specified string according to the format of {@link #toString()}.
	 * 
	 * @param v the string.
	 * @return this vector for chaining
	 */
	public Vector2D fromString(String v) {
		int s = v.indexOf(',', 1);
		if (s != -1 && v.charAt(0) == '(' && v.charAt(v.length() - 1) == ')') {
			try {
				double x = Float.parseFloat(v.substring(1, s));
				double y = Float.parseFloat(v.substring(s + 1, v.length() - 1));
				return this.set(x, y);
			} catch (NumberFormatException ex) {
				// Throw a GdxRuntimeException
			}
		}
		throw new GdxRuntimeException("Malformed Vector2D: " + v);
	}

	/**
	 * Calculates the 2D cross product between this and the given vector.
	 * 
	 * @param v the other vector
	 * @return the cross product
	 */
	public double crs(Vector2D v) {
		return this.x * v.y - this.y * v.x;
	}

	/**
	 * Calculates the 2D cross product between this and the given vector.
	 * 
	 * @param x the x-coordinate of the other vector
	 * @param y the y-coordinate of the other vector
	 * @return the cross product
	 */
	public double crs(double x, double y) {
		return this.x * y - this.y * x;
	}

	/**
	 * @return the angle in degrees of this vector (point) relative to the x-axis. Angles are towards the positive y-axis (typically
	 *         counter-clockwise) and between 0 and 360.
	 */
	public double angle() {
		double angle = (double) FastMath.atan2(y, x) * 180.0 / FastMath.PI;
		if (angle < 0) angle += 360;
		return angle;
	}

	/**
	 * @return the angle in degrees of this vector (point) relative to the given vector. Angles are towards the positive y-axis (typically
	 *         counter-clockwise.) between -180 and +180
	 */
	public double angle(Vector2D reference) {
		return (double) FastMath.atan2(crs(reference), dot(reference)) * 180.0 / FastMath.PI;
	}

	/**
	 * @return the angle in radians of this vector (point) relative to the x-axis. Angles are towards the positive y-axis. (typically
	 *         counter-clockwise)
	 */
	public double angleRad() {
		return (double) FastMath.atan2(y, x);
	}

	/**
	 * @return the angle in radians of this vector (point) relative to the given vector. Angles are towards the positive y-axis. (typically
	 *         counter-clockwise.)
	 */
	public double angleRad(Vector2D reference) {
		return (double) FastMath.atan2(crs(reference), dot(reference));
	}

	/**
	 * Sets the angle of the vector in degrees relative to the x-axis, towards the positive y-axis (typically counter-clockwise).
	 * 
	 * @param degrees The angle in degrees to set.
	 */
	public Vector2D setAngle(double degrees) {
		return setAngleRad(degrees * FastMath.PI / 180.0);
	}

	/**
	 * Sets the angle of the vector in radians relative to the x-axis, towards the positive y-axis (typically counter-clockwise).
	 * 
	 * @param radians The angle in radians to set.
	 */
	public Vector2D setAngleRad(double radians) {
		this.set(len(), 0f);
		this.rotateRad(radians);

		return this;
	}

	/**
	 * Rotates the Vector2D by the given angle, counter-clockwise assuming the y-axis points up.
	 * 
	 * @param degrees the angle in degrees
	 */
	public Vector2D rotate(double degrees) {
		return rotateRad(degrees * FastMath.PI / 180.0);
	}

	/**
	 * Rotates the Vector2D by the given angle, counter-clockwise assuming the y-axis points up.
	 * 
	 * @param radians the angle in radians
	 */
	public Vector2D rotateRad(double radians) {
		double cos = (double) FastMath.cos(radians);
		double sin = (double) FastMath.sin(radians);

		double newX = this.x * cos - this.y * sin;
		double newY = this.x * sin + this.y * cos;

		this.x = newX;
		this.y = newY;

		return this;
	}

	/** Rotates the Vector2D by 90 degrees in the specified direction, where >= 0 is counter-clockwise and < 0 is clockwise. */
	public Vector2D rotate90(int dir) {
		double x = this.x;
		if (dir >= 0) {
			this.x = -y;
			y = x;
		} else {
			this.x = y;
			y = -x;
		}
		return this;
	}

	public Vector2D lerp(Vector2D target, double alpha) {
		final double invAlpha = 1.0f - alpha;
		this.x = (x * invAlpha) + (target.x * alpha);
		this.y = (y * invAlpha) + (target.y * alpha);
		return this;
	}

	public Vector2D interpolate(Vector2D target, float alpha, Interpolation interpolation) {
		return lerp(target, interpolation.apply(alpha));
	}

	public Vector2D setToRandomDirection() {
		double theta = FastMath.random() * 2 * FastMath.PI;
		return this.set(FastMath.cos(theta), FastMath.sin(theta));
	}

	public int hashCode() {
		final int prime = 31;
		long result = 1;
		result = prime * result + NumberUtils.doubleToLongBits(x);
		result = prime * result + NumberUtils.doubleToLongBits(y);
		return (int) result;
	}

	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		Vector2D other = (Vector2D) obj;
		if (NumberUtils.doubleToLongBits(x) != NumberUtils.doubleToLongBits(other.x)) return false;
		if (NumberUtils.doubleToLongBits(y) != NumberUtils.doubleToLongBits(other.y)) return false;
		return true;
	}

	public boolean epsilonEquals(Vector2D other, double epsilon) {
		if (other == null) return false;
		if (FastMath.abs(other.x - x) > epsilon) return false;
		if (FastMath.abs(other.y - y) > epsilon) return false;
		return true;
	}

	/**
	 * Compares this vector with the other vector, using the supplied epsilon for fuzzy equality testing.
	 * 
	 * @return whether the vectors are the same.
	 */
	public boolean epsilonEquals(double x, double y, double epsilon) {
		if (FastMath.abs(x - this.x) > epsilon) return false;
		if (FastMath.abs(y - this.y) > epsilon) return false;
		return true;
	}

	public boolean isUnit() {
		return isUnit(0.000000001f);
	}

	public boolean isUnit(final double margin) {
		return FastMath.abs(len2() - 1f) < margin;
	}

	public boolean isZero() {
		return x == 0 && y == 0;
	}

	public boolean isZero(final double margin) {
		return len2() < margin;
	}

	public boolean isOnLine(Vector2D other) {
		return isDoubleAlmostZero(x * other.y - y * other.x);
	}

	public boolean isOnLine(Vector2D other, double epsilon) {
		return isDoubleAlmostZero(x * other.y - y * other.x, epsilon);
	}

	public boolean isCollinear(Vector2D other, double epsilon) {
		return isOnLine(other, epsilon) && dot(other) > 0f;
	}

	public boolean isCollinear(Vector2D other) {
		return isOnLine(other) && dot(other) > 0f;
	}

	public boolean isCollinearOpposite(Vector2D other, double epsilon) {
		return isOnLine(other, epsilon) && dot(other) < 0f;
	}

	public boolean isCollinearOpposite(Vector2D other) {
		return isOnLine(other) && dot(other) < 0f;
	}

	public boolean isPerpendicular(Vector2D vector) {
		return isDoubleAlmostZero(dot(vector));
	}

	public boolean isPerpendicular(Vector2D vector, double epsilon) {
		return isDoubleAlmostZero(dot(vector), epsilon);
	}

	public boolean hasSameDirection(Vector2D vector) {
		return dot(vector) > 0;
	}

	public boolean hasOppositeDirection(Vector2D vector) {
		return dot(vector) < 0;
	}

	public Vector2D setZero() {
		this.x = 0;
		this.y = 0;
		return this;
	}

	static public boolean isDoubleAlmostZero(double value) {
		return FastMath.abs(value) <= DOUBLE_ROUNDING_ERROR;
	}

	/**
	 * Returns true if the value is zero.
	 * 
	 * @param tolerance represent an upper bound below which the value is considered zero.
	 */
	static public boolean isDoubleAlmostZero(double value, double tolerance) {
		return FastMath.abs(value) <= tolerance;
	}
}
