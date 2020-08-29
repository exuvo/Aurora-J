package se.exuvo.aurora.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.math.MathUtils
import glm_.vec3.Vec3
import glm_.vec4.Vec4
import org.apache.commons.math3.util.FastMath

fun sRGBtoLinearRGB(color: Float): Float {
	if (color <= 0.04045) {
		return color / 12.92f
	}
	return FastMath.pow((color + 0.055) / 1.055, 2.4).toFloat()
}

fun linearRGBtoSRGB(color: Float): Float {
	if (color <= 0.0031308) {
		return color * 12.92f
	}
	return (1.055 * FastMath.pow(color.toDouble(), 1 / 2.4) - 0.055).toFloat()
}

fun sRGBtoLinearRGB(color: Color): Color {
	return Color(sRGBtoLinearRGB(color.r), sRGBtoLinearRGB(color.g), sRGBtoLinearRGB(color.b), color.a)
}

fun sRGBtoLinearRGB(color: Vec3): Vec3 {
	return Vec3(sRGBtoLinearRGB(color.r), sRGBtoLinearRGB(color.g), sRGBtoLinearRGB(color.b))
}

fun sRGBtoLinearRGB(color: Vec4): Vec4 {
	return Vec4(sRGBtoLinearRGB(color.r), sRGBtoLinearRGB(color.g), sRGBtoLinearRGB(color.b), color.a)
}

fun linearRGBtoSRGB(color: Color): Color {
	return Color(linearRGBtoSRGB(color.r), linearRGBtoSRGB(color.g), linearRGBtoSRGB(color.b), color.a)
}

fun linearRGBtoSRGB(color: Vec3): Vec3 {
	return Vec3(linearRGBtoSRGB(color.r), linearRGBtoSRGB(color.g), linearRGBtoSRGB(color.b))
}

fun linearRGBtoSRGB(color: Vec4): Vec4 {
	return Vec4(linearRGBtoSRGB(color.r), linearRGBtoSRGB(color.g), linearRGBtoSRGB(color.b), color.a)
}

fun Color.toLinearRGB(): Color {
	r = sRGBtoLinearRGB(r)
	g = sRGBtoLinearRGB(g)
	b = sRGBtoLinearRGB(b)
	return this
}

fun Vec3.toLinearRGB(): Vec3 {
	r = sRGBtoLinearRGB(r)
	g = sRGBtoLinearRGB(g)
	b = sRGBtoLinearRGB(b)
	return this
}

fun Vec4.toLinearRGB(): Vec4 {
	r = sRGBtoLinearRGB(r)
	g = sRGBtoLinearRGB(g)
	b = sRGBtoLinearRGB(b)
	return this
}

// Fixes sRGB defined colors with alpha < 1
fun Vec4.toLinearRGBwithAlphaCorrection(): Vec4 {
	r = sRGBtoLinearRGB(r * a)
	g = sRGBtoLinearRGB(g * a)
	b = sRGBtoLinearRGB(b * a)
	a = 1f
	return this
}

fun Vec3.toSRGB(): Vec3 {
	r = linearRGBtoSRGB(r)
	g = linearRGBtoSRGB(g)
	b = linearRGBtoSRGB(b)
	return this
}

fun Vec4.toSRGB(): Vec4 {
	r = linearRGBtoSRGB(r)
	g = linearRGBtoSRGB(g)
	b = linearRGBtoSRGB(b)
	return this
}

fun lerpColors(lerp: Float, low: Color, mid: Color, high: Color, result: Color) {
	result.set(mid)

	var l = MathUtils.clamp(lerp, -1f, 1f)

	if (l > 0f) {
		result.lerp(high, l)

	} else {
		result.lerp(low, -l)
	}
}

fun ShapeRenderer.scanCircleSector(x: Double, y: Double, radiusOuter: Double, radiusInner: Double, start: Double, degrees: Double, segments: Int) {

	if (segments <= 0) {
		throw IllegalArgumentException("segments must be > 0.")
	}
	
	val newVertices: Int
	
	if (getCurrentType() == ShapeType.Line) {
		newVertices = segments * 8 * 2 + 16
	} else {
		newVertices = segments * 12 * 2
	}
	
	if (renderer.maxVertices - renderer.numVertices < newVertices) {
		// Not enough space.
		val type = getCurrentType()
		end()
		begin(type)
	}

	val colorBits = color.toFloatBits();
	val theta: Double = (2 * Math.PI * (degrees / 360.0)) / segments;
	val cos: Double = FastMath.cos(theta);
	val sin: Double = FastMath.sin(theta);
	var cx: Double = radiusOuter * FastMath.cos(Math.toRadians(start));
	var cy: Double = radiusOuter * FastMath.sin(Math.toRadians(start));
	var cx2: Double = radiusInner * FastMath.cos(Math.toRadians(start));
	var cy2: Double = radiusInner * FastMath.sin(Math.toRadians(start));

	this.scanCircleSectorInner(x, y, cos, sin, cx, cy, cx2, cy2, segments)

	val theta2: Double = 2 * MathUtils.PI * (degrees / 360.0f)
	val cos2: Double = FastMath.cos(theta2)
	val sin2: Double = FastMath.sin(theta2)

	val x1End = cos2 * cx - sin2 * cy
	val y1End = sin2 * cx + cos2 * cy
	val x2End = cos2 * cx2 - sin2 * cy2
	val y2End = sin2 * cx2 + cos2 * cy2

	if (this.getCurrentType() == ShapeType.Line) {

		renderer.color(colorBits);
		renderer.vertex((x + cx).toFloat(), (y + cy).toFloat(), 0f);
		renderer.color(colorBits);
		renderer.vertex((x + cx2).toFloat(), (y + cy2).toFloat(), 0f);

		renderer.color(colorBits);
		renderer.vertex((x + x1End).toFloat(), (y + y1End).toFloat(), 0f);
		renderer.color(colorBits);
		renderer.vertex((x + x2End).toFloat(), (y + y2End).toFloat(), 0f);

		this.scanCircleSectorInner(x, y, cos, sin, cx2, cy2, cx2, cy2, segments)

	} else {

		this.scanCircleSectorInner(x, y, cos, sin, cx2, cy2, x1End, y1End, segments)
	}
}

fun ShapeRenderer.scanCircleSectorInner(x: Double, y: Double, cos: Double, sin: Double, cx1: Double, cy1: Double, cx2: Double, cy2: Double, segments: Int) {

	if (segments <= 0) {
		throw IllegalArgumentException("segments must be > 0.")
	}

	val colorBits = color.toFloatBits();
	var cx = cx1
	var cy = cy1

	if (this.getCurrentType() == ShapeType.Line) {

//			this.check(ShapeType.Line, ShapeType.Filled, segments * 2 + 2);

		var i = 0
		while (i++ < segments) {

			renderer.color(colorBits);
			renderer.vertex((x + cx).toFloat(), (y + cy).toFloat(), 0f);

			val temp = cx;
			cx = cos * cx - sin * cy;
			cy = sin * temp + cos * cy;

			renderer.color(colorBits);
			renderer.vertex((x + cx).toFloat(), (y + cy).toFloat(), 0f);
		}

	} else {

//			this.check(ShapeType.Line, ShapeType.Filled, segments * 3 + 3);

		var i = 0
		while (i++ < segments) {

			renderer.color(colorBits);
			renderer.vertex((x + cx2).toFloat(), (y + cy2).toFloat(), 0f);
			renderer.color(colorBits);
			renderer.vertex((x + cx).toFloat(), (y + cy).toFloat(), 0f);

			val temp = cx;
			cx = cos * cx - sin * cy;
			cy = sin * temp + cos * cy;

			renderer.color(colorBits);
			renderer.vertex((x + cx).toFloat(), (y + cy).toFloat(), 0f);
		}
	}
}