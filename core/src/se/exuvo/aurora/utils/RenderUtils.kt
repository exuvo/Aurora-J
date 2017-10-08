package se.exuvo.aurora.utils

import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.math.MathUtils

fun ShapeRenderer.scanArc(x: Float, y: Float, radiusOuter: Float, radiusInner: Float, start: Float, degrees: Float, segments: Int) {

	if (segments <= 0) {
		throw IllegalArgumentException("segments must be > 0.")
	}

	val colorBits: Float = color.toFloatBits();
	val theta: Float = (2 * MathUtils.PI * (degrees / 360.0f)) / segments;
	val cos: Float = MathUtils.cos(theta);
	val sin: Float = MathUtils.sin(theta);
	var cx: Float = radiusOuter * MathUtils.cos(start * MathUtils.degreesToRadians);
	var cy: Float = radiusOuter * MathUtils.sin(start * MathUtils.degreesToRadians);
	var cx2: Float = radiusInner * MathUtils.cos(start * MathUtils.degreesToRadians);
	var cy2: Float = radiusInner * MathUtils.sin(start * MathUtils.degreesToRadians);

	this.scanArcInner(x, y, cos, sin, cx, cy, cx2, cy2, segments)

	val theta2: Float = 2 * MathUtils.PI * (degrees / 360.0f)
	val cos2: Float = MathUtils.cos(theta2)
	val sin2: Float = MathUtils.sin(theta2)

	val x1End = cos2 * cx - sin2 * cy
	val y1End = sin2 * cx + cos2 * cy
	val x2End = cos2 * cx2 - sin2 * cy2
	val y2End = sin2 * cx2 + cos2 * cy2

	if (this.getCurrentType() == ShapeType.Line) {

		renderer.color(colorBits);
		renderer.vertex(x + cx, y + cy, 0f);
		renderer.color(colorBits);
		renderer.vertex(x + cx2, y + cy2, 0f);
		
		renderer.color(colorBits);
		renderer.vertex(x + x1End, y + y1End, 0f);
		renderer.color(colorBits);
		renderer.vertex(x + x2End, y + y2End, 0f);
 
		this.scanArcInner(x, y, cos, sin, cx2, cy2, cx2, cy2, segments)

	} else {

		this.scanArcInner(x, y, cos, sin, cx2, cy2, x1End, y1End, segments)
	}
}

fun ShapeRenderer.scanArcInner(x: Float, y: Float, cos: Float, sin: Float, cx1: Float, cy1: Float, cx2: Float, cy2: Float, segments: Int) {

	if (segments <= 0) {
		throw IllegalArgumentException("segments must be > 0.")
	}

	val colorBits: Float = color.toFloatBits();
	var cx = cx1
	var cy = cy1

	if (this.getCurrentType() == ShapeType.Line) {

//			this.check(ShapeType.Line, ShapeType.Filled, segments * 2 + 2);

		var i = 0
		while (i++ < segments) {

			renderer.color(colorBits);
			renderer.vertex(x + cx, y + cy, 0f);

			val temp = cx;
			cx = cos * cx - sin * cy;
			cy = sin * temp + cos * cy;

			renderer.color(colorBits);
			renderer.vertex(x + cx, y + cy, 0f);
		}

	} else {

//			this.check(ShapeType.Line, ShapeType.Filled, segments * 3 + 3);

		var i = 0
		while (i++ < segments) {

			renderer.color(colorBits);
			renderer.vertex(x + cx2, y + cy2, 0f);
			renderer.color(colorBits);
			renderer.vertex(x + cx, y + cy, 0f);

			val temp = cx;
			cx = cos * cx - sin * cy;
			cy = sin * temp + cos * cy;

			renderer.color(colorBits);
			renderer.vertex(x + cx, y + cy, 0f);
		}
	}
}