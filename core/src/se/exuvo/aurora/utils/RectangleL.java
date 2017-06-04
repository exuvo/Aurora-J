package se.exuvo.aurora.utils;

import java.io.Serializable;

import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Scaling;

/** Encapsulates a 2D rectangle defined by its corner point in the bottom left and its extents in x (width) and y (height).
 * @author badlogicgames@gmail.com */
public class RectangleL implements Serializable {
	/** Static temporary rectangle. Use with care! Use only when sure other code will not also use this. */
	static public final RectangleL tmp = new RectangleL();

	/** Static temporary rectangle. Use with care! Use only when sure other code will not also use this. */
	static public final RectangleL tmp2 = new RectangleL();

	private static final long serialVersionUID = 5733252015138115702L;
	public long x, y;
	public long width, height;

	/** Constructs a new rectangle with all values set to zero */
	public RectangleL () {

	}

	/** Constructs a new rectangle with the given corner point in the bottom left and dimensions.
	 * @param x The corner point x-coordinate
	 * @param y The corner point y-coordinate
	 * @param width The width
	 * @param height The height */
	public RectangleL (long x, long y, long width, long height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	/** Constructs a rectangle based on the given rectangle
	 * @param rect The rectangle */
	public RectangleL (RectangleL rect) {
		x = rect.x;
		y = rect.y;
		width = rect.width;
		height = rect.height;
	}

	/** @param x bottom-left x coordinate
	 * @param y bottom-left y coordinate
	 * @param width width
	 * @param height height
	 * @return this rectangle for chaining */
	public RectangleL set (long x, long y, long width, long height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;

		return this;
	}

	/** @return the x-coordinate of the bottom left corner */
	public long getX () {
		return x;
	}

	/** Sets the x-coordinate of the bottom left corner
	 * @param x The x-coordinate
	 * @return this rectangle for chaining */
	public RectangleL setX (long x) {
		this.x = x;

		return this;
	}

	/** @return the y-coordinate of the bottom left corner */
	public long getY () {
		return y;
	}

	/** Sets the y-coordinate of the bottom left corner
	 * @param y The y-coordinate
	 * @return this rectangle for chaining */
	public RectangleL setY (long y) {
		this.y = y;

		return this;
	}

	/** @return the width */
	public long getWidth () {
		return width;
	}

	/** Sets the width of this rectangle
	 * @param width The width
	 * @return this rectangle for chaining */
	public RectangleL setWidth (long width) {
		this.width = width;

		return this;
	}

	/** @return the height */
	public long getHeight () {
		return height;
	}

	/** Sets the height of this rectangle
	 * @param height The height
	 * @return this rectangle for chaining */
	public RectangleL setHeight (long height) {
		this.height = height;

		return this;
	}

	/** return the Vector2 with coordinates of this rectangle
	 * @param position The Vector2 */
	public Vector2L getPosition (Vector2L position) {
		return position.set(x, y);
	}

	/** Sets the x and y-coordinates of the bottom left corner from vector
	 * @param position The position vector
	 * @return this rectangle for chaining */
	public RectangleL setPosition (Vector2L position) {
		this.x = position.x;
		this.y = position.y;

		return this;
	}

	/** Sets the x and y-coordinates of the bottom left corner
	 * @param x The x-coordinate
	 * @param y The y-coordinate
	 * @return this rectangle for chaining */
	public RectangleL setPosition (long x, long y) {
		this.x = x;
		this.y = y;

		return this;
	}

	/** Sets the width and height of this rectangle
	 * @param width The width
	 * @param height The height
	 * @return this rectangle for chaining */
	public RectangleL setSize (long width, long height) {
		this.width = width;
		this.height = height;

		return this;
	}

	/** Sets the squared size of this rectangle
	 * @param sizeXY The size
	 * @return this rectangle for chaining */
	public RectangleL setSize (long sizeXY) {
		this.width = sizeXY;
		this.height = sizeXY;

		return this;
	}

	/** @return the Vector2 with size of this rectangle
	 * @param size The Vector2 */
	public Vector2L getSize (Vector2L size) {
		return size.set(width, height);
	}

	/** @param x point x coordinate
	 * @param y point y coordinate
	 * @return whether the point is contained in the rectangle */
	public boolean contains (long x, long y) {
		return this.x <= x && this.x + this.width >= x && this.y <= y && this.y + this.height >= y;
	}

	/** @param point The coordinates vector
	 * @return whether the point is contained in the rectangle */
	public boolean contains (Vector2L point) {
		return contains(point.x, point.y);
	}

	/** @param circle the circle
	 * @return whether the circle is contained in the rectangle */
	public boolean contains (CircleL circle) {
		return (circle.x - circle.radius >= x) && (circle.x + circle.radius <= x + width)
			&& (circle.y - circle.radius >= y) && (circle.y + circle.radius <= y + height);
	}

	/** @param rectangle the other {@link RectangleL}.
	 * @return whether the other rectangle is contained in this rectangle. */
	public boolean contains (RectangleL rectangle) {
		long xmin = rectangle.x;
		long xmax = xmin + rectangle.width;

		long ymin = rectangle.y;
		long ymax = ymin + rectangle.height;

		return ((xmin > x && xmin < x + width) && (xmax > x && xmax < x + width))
			&& ((ymin > y && ymin < y + height) && (ymax > y && ymax < y + height));
	}

	/** @param r the other {@link RectangleL}
	 * @return whether this rectangle overlaps the other rectangle. */
	public boolean overlaps (RectangleL r) {
		return x < r.x + r.width && x + width > r.x && y < r.y + r.height && y + height > r.y;
	}

	/** Sets the values of the given rectangle to this rectangle.
	 * @param rect the other rectangle
	 * @return this rectangle for chaining */
	public RectangleL set (RectangleL rect) {
		this.x = rect.x;
		this.y = rect.y;
		this.width = rect.width;
		this.height = rect.height;

		return this;
	}

	/** Merges this rectangle with the other rectangle. The rectangle should not have negative width or negative height.
	 * @param rect the other rectangle
	 * @return this rectangle for chaining */
	public RectangleL merge (RectangleL rect) {
		long minX = Math.min(x, rect.x);
		long maxX = Math.max(x + width, rect.x + rect.width);
		x = minX;
		width = maxX - minX;

		long minY = Math.min(y, rect.y);
		long maxY = Math.max(y + height, rect.y + rect.height);
		y = minY;
		height = maxY - minY;

		return this;
	}

	/** Merges this rectangle with a point. The rectangle should not have negative width or negative height.
	 * @param x the x coordinate of the point
	 * @param y the y coordinate of the point
	 * @return this rectangle for chaining */
	public RectangleL merge (long x, long y) {
		long minX = Math.min(this.x, x);
		long maxX = Math.max(this.x + width, x);
		this.x = minX;
		this.width = maxX - minX;

		long minY = Math.min(this.y, y);
		long maxY = Math.max(this.y + height, y);
		this.y = minY;
		this.height = maxY - minY;

		return this;
	}

	/** Merges this rectangle with a point. The rectangle should not have negative width or negative height.
	 * @param vec the vector describing the point
	 * @return this rectangle for chaining */
	public RectangleL merge (Vector2L vec) {
		return merge(vec.x, vec.y);
	}

	/** Merges this rectangle with a list of points. The rectangle should not have negative width or negative height.
	 * @param vecs the vectors describing the points
	 * @return this rectangle for chaining */
	public RectangleL merge (Vector2L[] vecs) {
		long minX = x;
		long maxX = x + width;
		long minY = y;
		long maxY = y + height;
		for (int i = 0; i < vecs.length; ++i) {
			Vector2L v = vecs[i];
			minX = Math.min(minX, v.x);
			maxX = Math.max(maxX, v.x);
			minY = Math.min(minY, v.y);
			maxY = Math.max(maxY, v.y);
		}
		x = minX;
		width = maxX - minX;
		y = minY;
		height = maxY - minY;
		return this;
	}

	/** Calculates the aspect ratio ( width / height ) of this rectangle
	 * @return the aspect ratio of this rectangle. Returns Float.NaN if height is 0 to avoid ArithmeticException */
	public float getAspectRatio () {
		return (height == 0) ? Float.NaN : (float) width / (float) height;
	}

	/** Calculates the center of the rectangle. Results are located in the given Vector2
	 * @param vector the Vector2 to use
	 * @return the given vector with results stored inside */
	public Vector2L getCenter (Vector2L vector) {
		vector.x = x + width / 2;
		vector.y = y + height / 2;
		return vector;
	}

	/** Moves this rectangle so that its center point is located at a given position
	 * @param x the position's x
	 * @param y the position's y
	 * @return this for chaining */
	public RectangleL setCenter (long x, long y) {
		setPosition(x - width / 2, y - height / 2);
		return this;
	}

	/** Moves this rectangle so that its center point is located at a given position
	 * @param position the position
	 * @return this for chaining */
	public RectangleL setCenter (Vector2L position) {
		setPosition(position.x - width / 2, position.y - height / 2);
		return this;
	}

	/** Fits this rectangle around another rectangle while maintaining aspect ratio. This scales and centers the rectangle to the
	 * other rectangle (e.g. Having a camera translate and scale to show a given area)
	 * @param rect the other rectangle to fit this rectangle around
	 * @return this rectangle for chaining
	 * @see Scaling */
	public RectangleL fitOutside (RectangleL rect) {
		double ratio = getAspectRatio();

		if (ratio > rect.getAspectRatio()) {
			// Wider than tall
			setSize((long) (rect.height * ratio), rect.height);
		} else {
			// Taller than wide
			setSize(rect.width, (long) (rect.width / ratio));
		}

		setPosition((rect.x + rect.width / 2) - width / 2, (rect.y + rect.height / 2) - height / 2);
		return this;
	}

	/** Fits this rectangle into another rectangle while maintaining aspect ratio. This scales and centers the rectangle to the
	 * other rectangle (e.g. Scaling a texture within a arbitrary cell without squeezing)
	 * @param rect the other rectangle to fit this rectangle inside
	 * @return this rectangle for chaining
	 * @see Scaling */
	public RectangleL fitInside (RectangleL rect) {
		double ratio = getAspectRatio();

		if (ratio < rect.getAspectRatio()) {
			// Taller than wide
			setSize((long) (rect.height * ratio), rect.height);
		} else {
			// Wider than tall
			setSize(rect.width, (long) (rect.width / ratio));
		}

		setPosition((rect.x + rect.width / 2) - width / 2, (rect.y + rect.height / 2) - height / 2);
		return this;
	}

	/** Converts this {@code RectangleL} to a string in the format {@code [x,y,width,height]}.
	 * @return a string representation of this object. */
	public String toString () {
		return "[" + x + "," + y + "," + width + "," + height + "]";
	}

	/** Sets this {@code RectangleL} to the value represented by the specified string according to the format of {@link #toString()}
	 * .
	 * @param v the string.
	 * @return this rectangle for chaining */
	public RectangleL fromString (String v) {
		int s0 = v.indexOf(',', 1);
		int s1 = v.indexOf(',', s0 + 1);
		int s2 = v.indexOf(',', s1 + 1);
		if (s0 != -1 && s1 != -1 && s2 != -1 && v.charAt(0) == '[' && v.charAt(v.length() - 1) == ']') {
			try {
				long x = Long.parseLong(v.substring(1, s0));
				long y =Long.parseLong(v.substring(s0 + 1, s1));
				long width = Long.parseLong(v.substring(s1 + 1, s2));
				long height = Long.parseLong(v.substring(s2 + 1, v.length() - 1));
				return this.set(x, y, width, height);
			} catch (NumberFormatException ex) {
				// Throw a GdxRuntimeException
			}
		}
		throw new GdxRuntimeException("Malformed RectangleL: " + v);
	}

	public long area () {
		return this.width * this.height;
	}

	public long perimeter () {
		return 2 * (this.width + this.height);
	}

	public int hashCode () {
		final int prime = 31;
		long result = 1;
		result = prime * result + height;
		result = prime * result + width;
		result = prime * result + x;
		result = prime * result + y;
		return Long.hashCode(result);
	}

	public boolean equals (Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		RectangleL other = (RectangleL)obj;
		if (height != other.height) return false;
		if (width != other.width) return false;
		if (x != other.x) return false;
		if (y != other.y) return false;
		return true;
	}

}
