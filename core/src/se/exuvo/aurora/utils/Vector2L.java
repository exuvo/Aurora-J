package se.exuvo.aurora.utils;

import java.io.Serializable;

import org.apache.commons.math3.util.FastMath;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class Vector2L implements Serializable {

	private static final long serialVersionUID = 913902788239530931L;

	public static final Vector2L X = new Vector2L(1, 0);
	public static final Vector2L Y = new Vector2L(0, 1);
	public static final Vector2L Zero = new Vector2L(0, 0);
	
	private static final ThreadLocal<BigInt> tmpBigIntX = new ThreadLocal<BigInt>() {
		@Override protected BigInt initialValue() {
			return new BigInt(0L);
		}
	};
	
	private static final ThreadLocal<BigInt> tmpBigIntY = new ThreadLocal<BigInt>() {
		@Override protected BigInt initialValue() {
			return new BigInt(0L);
		}
	};
	
	private static final ThreadLocal<BigInt> tmpBigIntX2 = new ThreadLocal<BigInt>() {
		@Override protected BigInt initialValue() {
			return new BigInt(0L);
		}
	};
	
	private static final ThreadLocal<BigInt> tmpBigIntY2 = new ThreadLocal<BigInt>() {
		@Override protected BigInt initialValue() {
			return new BigInt(0L);
		}
	};

	/** the x-component of this vector **/
	public long x;
	/** the y-component of this vector **/
	public long y;

	/** Constructs a new vector at (0,0) */
	public Vector2L() {}

	/**
	 * Constructs a vector with the given components
	 * 
	 * @param x The x-component
	 * @param y The y-component
	 */
	public Vector2L(long x, long y) {
		this.x = x;
		this.y = y;
	}

	/**
	 * Constructs a vector from the given vector
	 * 
	 * @param v The vector
	 */
	public Vector2L(Vector2L v) {
		set(v);
	}

	public Vector2L cpy() {
		return new Vector2L(this);
	}

	public static double len(long x, long y) {
		return FastMath.hypot(x, y);
	}

	public double len() {
		return FastMath.hypot(x, y);
	}

	public Vector2L set(Vector2L v) {
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
	public Vector2L set(long x, long y) {
		this.x = x;
		this.y = y;
		return this;
	}

	public Vector2L sub(Vector2L v) {
		x -= v.x;
		y -= v.y;
		return this;
	}
	
	public Vector2L subDiv(Vector2L v, long divisor) {
		x -= v.x / divisor;
		y -= v.y / divisor;
		return this;
	}

	/**
	 * Substracts the other vector from this vector.
	 * 
	 * @param x The x-component of the other vector
	 * @param y The y-component of the other vector
	 * @return This vector for chaining
	 */
	public Vector2L sub(long x, long y) {
		this.x -= x;
		this.y -= y;
		return this;
	}
	
	public Vector2L subDiv(long x, long y, long divisor) {
		this.x -= x / divisor;
		this.y -= y / divisor;
		return this;
	}

	public Vector2L add(Vector2L v) {
		x += v.x;
		y += v.y;
		return this;
	}
	
	public Vector2L addDiv(Vector2L v, long divisor) {
		x += v.x / divisor;
		y += v.y / divisor;
		return this;
	}

	/**
	 * Adds the given components to this vector
	 * 
	 * @param x The x-component
	 * @param y The y-component
	 * @return This vector for chaining
	 */
	public Vector2L add(long x, long y) {
		this.x += x;
		this.y += y;
		return this;
	}
	
	public Vector2L addDiv(long x, long y, long divisor) {
		this.x += x / divisor;
		this.y += y / divisor;
		return this;
	}

	public static BigInt dot(long x1, long y1, long x2, long y2) {
	// return x1 * x2 + y1 * y2;
		BigInt X = tmpBigIntX2.get();
		BigInt Y = tmpBigIntY2.get();
		X.assign(x1);
		Y.assign(y1);
		X.mul(x2);
		Y.mul(y2);
		X.add(Y);
		return X;
	}

	public BigInt dot(Vector2L v) {
		return dot(v.x, v.y);
	}

	public BigInt dot(long ox, long oy) {
		return dot(x, y, ox, oy);
	}

	public Vector2L scl(long scalar) {
		x *= scalar;
		y *= scalar;
		return this;
	}
	
	public Vector2L scl(double scalar) {
		x = (long) (x * scalar);
		y = (long) (y * scalar);
		return this;
	}
	
	public Vector2L div(long divider) {
		x = x / divider;
		y = y / divider;
		return this;
	}
	
	public Vector2L div(double divider) {
		scl(1.0 / divider);
		return this;
	}

	/**
	 * Multiplies this vector by a scalar
	 * 
	 * @return This vector for chaining
	 */
	public Vector2L scl(long x, long y) {
		this.x *= x;
		this.y *= y;
		return this;
	}

	public Vector2L scl(Vector2L v) {
		this.x *= v.x;
		this.y *= v.y;
		return this;
	}

	public Vector2L mulAdd(Vector2L vec, long scalar) {
		this.x += vec.x * scalar;
		this.y += vec.y * scalar;
		return this;
	}

	public Vector2L mulAdd(Vector2L vec, Vector2L mulVec) {
		this.x += vec.x * mulVec.x;
		this.y += vec.y * mulVec.y;
		return this;
	}

	public static double dst(long x1, long y1, long x2, long y2) {
		final long x_d = x2 - x1;
		final long y_d = y2 - y1;
		return FastMath.hypot(x_d, y_d);
	}

	public double dst(Vector2L v) {
		final long x_d = v.x - x;
		final long y_d = v.y - y;
		return FastMath.hypot(x_d, y_d);
	}

	/**
	 * @param x The x-component of the other vector
	 * @param y The y-component of the other vector
	 * @return the distance between this and the other vector
	 */
	public double dst(long x, long y) {
		final long x_d = x - this.x;
		final long y_d = y - this.y;
		return FastMath.hypot(x_d, y_d);
	}
	
	/**
	 * Errors between -1.5% (on axes) and +7.5% (on lobes) and an average error of +0.043%.
	 * From https://gamedev.stackexchange.com/a/69255/142645 and https://www.flipcode.com/archives/Fast_Approximate_Distance_Functions.shtml
	 */
	public double dstAprox(Vector2L v) {
		long dx = FastMath.abs(v.x - x);
		long dy = FastMath.abs(v.y - y);
		
		long min, max;
		
		if (dx < dy) {
			min = dx;
			max = dy;
		} else {
			min = dy;
			max = dx;
		}
		
		long approx = 1007 * max + 441 * min;
		
		if (max < 16 * min) {
			approx -= 40 * max;
		}
		
		// add 512 for proper rounding
		return (approx + 512) >> 10; // div 1024
	}

	public Vector2L limit(long limit) {
		double len = len();
		if (len > limit) {
			return scl(FastMath.sqrt(limit / len));
		}
		return this;
	}

	public Vector2L clamp(long min, long max) {
		double len = len();
		if (len == 0) return this;
		if (len > max) return scl(FastMath.sqrt(max / len));
		if (len < min) return scl(FastMath.sqrt(min / len));
		return this;
	}

	public Vector2L setLength(long len) {
		double oldLen = len();
		return (oldLen == 0 || oldLen == len) ? this : scl(FastMath.sqrt(len / oldLen));
	}

	/**
	 * Converts this {@code Vector2L} to a string in the format {@code (x,y)}.
	 * 
	 * @return a string representation of this object.
	 */
	@Override
	public String toString() {
		return "(" + x + "," + y + ")";
	}

	/**
	 * Sets this {@code Vector2L} to the value represented by the specified string according to the format of {@link #toString()}.
	 * 
	 * @param v the string.
	 * @return this vector for chaining
	 */
	public Vector2L fromString(String v) {
		int s = v.indexOf(',', 1);
		if (s != -1 && v.charAt(0) == '(' && v.charAt(v.length() - 1) == ')') {
			try {
				long x = Long.parseLong(v.substring(1, s));
				long y = Long.parseLong(v.substring(s + 1, v.length() - 1));
				return this.set(x, y);
			} catch (NumberFormatException ex) {
				// Throw a GdxRuntimeException
			}
		}
		throw new GdxRuntimeException("Malformed Vector2L: " + v);
	}

	/**
	 * Calculates the 2D cross product between this and the given vector.
	 * 
	 * @param v the other vector
	 * @return the cross product
	 */
	public BigInt crs(Vector2L v) {
		return crs(v.x, v.y);
	}

	/**
	 * Calculates the 2D cross product between this and the given vector.
	 * 
	 * @param x the x-coordinate of the other vector
	 * @param y the y-coordinate of the other vector
	 * @return the cross product
	 */
	public BigInt crs(long x, long y) {
		// return this.x * y - this.y * x;
		BigInt X = tmpBigIntX.get();
		BigInt Y = tmpBigIntY.get();
		X.assign(this.x);
		Y.assign(this.y);
		X.mul(y);
		Y.mul(x);
		X.sub(Y);
		return X;
	}
	
	public double angleTo(Vector2L other){
		return FastMath.toDegrees(angleToRad(other));
	}
	
	public double angleToRad(Vector2L other){
		long x = other.x - this.x;
		long y = other.y - this.y;
		return FastMath.atan2(y, x);
	}

	/**
	 * @return the angle in degrees of this vector (point) relative to the x-axis. Angles are towards the positive y-axis (typically
	 *         counter-clockwise) and between 0 and 360.
	 */
	public double angle() {
		double angle = FastMath.toDegrees(FastMath.atan2(y, x));
		if (angle < 0) angle += 360;
		return angle;
	}

	/**
	 * @return the angle in degrees of this vector (point) relative to the given vector. Angles are towards the positive y-axis (typically
	 *         counter-clockwise.) between -180 and +180
	 */
	public double angle(Vector2L reference) {
		return FastMath.toDegrees(angleRad(reference));
	}

	/**
	 * @return the angle in radians of this vector (point) relative to the x-axis. Angles are towards the positive y-axis. (typically
	 *         counter-clockwise)
	 */
	public double angleRad() {
		return FastMath.atan2(y, x);
	}

	/**
	 * @return the angle in radians of this vector (point) relative to the given vector. Angles are towards the positive y-axis. (typically
	 *         counter-clockwise.)
	 */
	public double angleRad(Vector2L reference) {
		return FastMath.atan2(crs(reference).doubleValue(), dot(reference).doubleValue());
	}

	/**
	 * Sets the angle of the vector in degrees relative to the x-axis, towards the positive y-axis (typically counter-clockwise).
	 * 
	 * @param degrees The angle in degrees to set.
	 */
	public Vector2L setAngle(float degrees) {
		return setAngleRad(degrees * MathUtils.degreesToRadians);
	}
	
	/**
	 * Sets the angle of the vector in degrees relative to the x-axis, towards the positive y-axis (typically counter-clockwise).
	 * 
	 * @param degrees The angle in degrees to set.
	 */
	public Vector2L setAngle(double degrees) {
		return setAngleRad(Math.toRadians(degrees));
	}

	/**
	 * Sets the angle of the vector in radians relative to the x-axis, towards the positive y-axis (typically counter-clockwise).
	 * 
	 * @param radians The angle in radians to set.
	 */
	public Vector2L setAngleRad(float radians) {
		this.rotateRad(radians - angleRad());
		return this;
	}
	
	/**
	 * Sets the angle of the vector in radians relative to the x-axis, towards the positive y-axis (typically counter-clockwise).
	 * 
	 * @param radians The angle in radians to set.
	 */
	public Vector2L setAngleRad(double radians) {
		this.rotateRad(radians - angleRad());
		return this;
	}
	
	/**
	 * Rotates the Vector2L by the given angle, counter-clockwise assuming the y-axis points up.
	 * 
	 * @param degrees the angle in degrees
	 */
	public Vector2L rotate(float degrees) {
		return rotateRad(degrees * MathUtils.degreesToRadians);
	}
	
	/**
	 * Rotates the Vector2L by the given angle, counter-clockwise assuming the y-axis points up.
	 * 
	 * @param degrees the angle in degrees
	 */
	public Vector2L rotate(double degrees) {
		return rotateRad(Math.toRadians(degrees));
	}

	/**
	 * Rotates the Vector2L by the given angle, counter-clockwise assuming the y-axis points up.
	 * 
	 * @param radians the angle in radians
	 */
	public Vector2L rotateRad(double radians) {
		double cos = FastMath.cos(radians);
		double sin = FastMath.sin(radians);

		double newX = this.x * cos - this.y * sin;
		double newY = this.x * sin + this.y * cos;

		this.x = (long) newX;
		this.y = (long) newY;

		return this;
	}

	/** Rotates the Vector2L by 90 degrees in the specified direction, where >= 0 is counter-clockwise and < 0 is clockwise. */
	public Vector2L rotate90(int dir) {
		long x = this.x;
		if (dir >= 0) {
			this.x = -y;
			y = x;
		} else {
			this.x = y;
			y = -x;
		}
		return this;
	}

	public Vector2L lerp(Vector2L target, double alpha) {
		final double invAlpha = 1.0 - alpha;
		this.x = (long) ((x * invAlpha) + (target.x * alpha));
		this.y = (long) ((y * invAlpha) + (target.y * alpha));
		return this;
	}
	
	public Vector2L lerp(Vector2L target, long current, long max) {
		final long invAlpha = max - current;
		
		BigInt X1 = tmpBigIntX.get();
		BigInt Y1 = tmpBigIntY.get();
		X1.assign(x);
		Y1.assign(y);
		X1.mul(invAlpha);
		Y1.mul(invAlpha);
		
		BigInt X2 = tmpBigIntX2.get();
		BigInt Y2 = tmpBigIntY2.get();
		X2.assign(target.x);
		Y2.assign(target.y);
		X2.mul(current);
		Y2.mul(current);
		
		X1.add(X2);
		Y1.add(Y2);
		
		X1.div(max);
		Y1.div(max);
		
		x = X1.longValue();
		y = Y1.longValue();
		
		return this;
	}
	
	public Vector2L interpolate(Vector2L target, float alpha, Interpolation interpolation) {
		return lerp(target, interpolation.apply(alpha));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		long result = 1;
		result = prime * result + x;
		result = prime * result + y;
		return Long.hashCode(result);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		Vector2L other = (Vector2L) obj;
		if (x != other.x) return false;
		if (y != other.y) return false;
		return true;
	}

	public boolean isZero() {
		return x == 0 && y == 0;
	}

	public boolean isOnLine(Vector2L other) {
		return MathUtils.isZero(x * other.y - y * other.x);
	}

	public boolean isOnLine(Vector2L other, float epsilon) {
		return MathUtils.isZero(x * other.y - y * other.x, epsilon);
	}

	public boolean isCollinear(Vector2L other, float epsilon) {
		return isOnLine(other, epsilon) && dot(other).compareTo(BigInt.ZERO) == 1;
	}

	public boolean isCollinear(Vector2L other) {
		return isOnLine(other) && dot(other).compareTo(BigInt.ZERO) == 1;
	}

	public boolean isCollinearOpposite(Vector2L other, float epsilon) {
		return isOnLine(other, epsilon) && dot(other).compareTo(BigInt.ZERO) == -1;
	}

	public boolean isCollinearOpposite(Vector2L other) {
		return isOnLine(other) && dot(other).compareTo(BigInt.ZERO) == -1;
	}

	public boolean isPerpendicular(Vector2L vector) {
		return dot(vector).isZero();
	}

//	public boolean isPerpendicular(Vector2L vector, float epsilon) {
//		return dot(vector).isZero();
//	}

	public boolean hasSameDirection(Vector2L vector) {
		return dot(vector).compareTo(BigInt.ZERO) == 1;
	}

	public boolean hasOppositeDirection(Vector2L vector) {
		return dot(vector).compareTo(BigInt.ZERO) == -1;
	}

	public Vector2L setZero() {
		this.x = 0;
		this.y = 0;
		return this;
	}
}
