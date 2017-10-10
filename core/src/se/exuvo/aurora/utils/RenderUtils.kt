package se.exuvo.aurora.utils

import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.math.MathUtils

fun ShapeRenderer.scanCircleSector(x: Double, y: Double, radiusOuter: Double, radiusInner: Double, start: Double, degrees: Double, segments: Int) {

	if (segments <= 0) {
		throw IllegalArgumentException("segments must be > 0.")
	}

	val colorBits = color.toFloatBits();
	val theta: Double = (2 * Math.PI * (degrees / 360.0)) / segments;
	val cos: Double = Math.cos(theta);
	val sin: Double = Math.sin(theta);
	var cx: Double = radiusOuter * Math.cos(Math.toRadians(start));
	var cy: Double = radiusOuter * Math.sin(Math.toRadians(start));
	var cx2: Double = radiusInner * Math.cos(Math.toRadians(start));
	var cy2: Double = radiusInner * Math.sin(Math.toRadians(start));

	this.scanCircleSectorInner(x, y, cos, sin, cx, cy, cx2, cy2, segments)

	val theta2: Double = 2 * MathUtils.PI * (degrees / 360.0f)
	val cos2: Double = Math.cos(theta2)
	val sin2: Double = Math.sin(theta2)

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