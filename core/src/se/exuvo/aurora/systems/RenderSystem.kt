package com.thedeadpixelsociety.ld34.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.SortedIteratingSystem
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.Viewport
import com.thedeadpixelsociety.ld34.components.CircleComponent
import com.thedeadpixelsociety.ld34.components.GroupComponent
import com.thedeadpixelsociety.ld34.components.LineComponent
import com.thedeadpixelsociety.ld34.components.PositionComponent
import com.thedeadpixelsociety.ld34.components.RenderComponent
import com.thedeadpixelsociety.ld34.components.TagComponent
import com.thedeadpixelsociety.ld34.components.TextComponent
import com.thedeadpixelsociety.ld34.components.TintComponent
import se.exuvo.aurora.Assets
import se.exuvo.aurora.utils.GameServices
import java.util.Comparator

class RenderSystem : SortedIteratingSystem(RenderSystem.FAMILY, ZOrderComparator()) {
	companion object {
		val FAMILY = Family.all(PositionComponent::class.java, RenderComponent::class.java).one(CircleComponent::class.java, LineComponent::class.java).get()
	}

	private val circleMapper = ComponentMapper.getFor(CircleComponent::class.java)
	private val lineMapper = ComponentMapper.getFor(LineComponent::class.java)
	private val tintMapper = ComponentMapper.getFor(TintComponent::class.java)
	private val positionMapper = ComponentMapper.getFor(PositionComponent::class.java)
	private val tagMapper = ComponentMapper.getFor(TagComponent::class.java)
	private val groupMapper = ComponentMapper.getFor(GroupComponent::class.java)
	private val textMapper = ComponentMapper.getFor(TextComponent::class.java)
	private val shapeRenderer by lazy { GameServices[ShapeRenderer::class.java] }
	private val batch by lazy { GameServices[SpriteBatch::class.java] }

	override fun checkProcessing() = false

	override fun processEntity(entity: Entity, deltaTime: Float) {

		val position = positionMapper.get(entity)
		val tint = tintMapper.get(entity)

		val color = Color(tint?.color ?: Color.WHITE)
		color.a = .5f
		shapeRenderer.color = color

		if (circleMapper.has(entity)) {

			val circle = circleMapper.get(entity)
			shapeRenderer.circle(position.position.x, position.position.y, circle.radius * 1f, 64)

		} else if (lineMapper.has(entity)) {

			val line = lineMapper.get(entity)
			shapeRenderer.line(position.position.x, position.position.y, position.position.x + line.x, position.position.y + line.y)
		}
	}

	fun render(viewport: Viewport) {
		begin(viewport)
		super.update(0f)
		end()

		beginBatch(viewport)
		val font = Assets.fontMap
		val scale = font.getData().scaleX;
		font.getData().setScale((viewport.camera as OrthographicCamera).zoom * scale)

		shapeRenderer.color = Color.WHITE
		entities.filter { textMapper.has(it) }.forEach {
			val position = positionMapper.get(it)
			val text = textMapper.get(it)

			var x = 0f
			var y = 0f

			if (circleMapper.has(it)) {

				val circle = circleMapper.get(it)
				x = circle.radius
				y = circle.radius

			} else if (lineMapper.has(it)) {

				val line = lineMapper.get(it)
				x = line.x
				y = line.y
			}

			font.draw(batch, text.text,
							position.position.x - x * .5f,
							position.position.y - y * .5f + font.lineHeight)
		}

		font.getData().setScale(scale)
		endBatch()
	}
	
//	private fun drawDottedLine(dotDist: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
//		
//		val vec2 = Vector2(x2, y2).sub(Vector2(x1, y1))
//		val length = vec2.len();
//		shapeRenderer.begin(ShapeRenderer.ShapeType.Point);
//
//		var i = 0f
//		while (i < length) {
//			vec2.clamp(length - i, length - i);
//			shapeRenderer.point(x1 + vec2.x, y1 + vec2.y, 0f);
//			i += dotDist
//		}
//		shapeRenderer.end();
//	}

	private fun begin(viewport: Viewport) {
		viewport.apply()
		shapeRenderer.projectionMatrix = viewport.camera.combined
		shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
	}

	private fun end() {
		shapeRenderer.end()
	}

	private fun beginBatch(viewport: Viewport) {
		batch.projectionMatrix = viewport.camera.combined
		batch.begin()
	}

	private fun endBatch() {
		batch.end()
	}
}

class ZOrderComparator : Comparator<Entity> {

	private val renderMapper = ComponentMapper.getFor(RenderComponent::class.java)

	override fun compare(o1: Entity, o2: Entity): Int {
		val r1 = renderMapper.get(o1)
		val r2 = renderMapper.get(o2)

		return r1.zOrder.compareTo(r2.zOrder)
	}
}
