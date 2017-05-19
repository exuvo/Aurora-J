package com.thedeadpixelsociety.ld34.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.SortedIteratingSystem
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.Viewport
import com.thedeadpixelsociety.ld34.components.BoundsComponent
import com.thedeadpixelsociety.ld34.components.GroupComponent
import com.thedeadpixelsociety.ld34.components.RenderComponent
import com.thedeadpixelsociety.ld34.components.RenderType
import com.thedeadpixelsociety.ld34.components.TagComponent
import com.thedeadpixelsociety.ld34.components.TextComponent
import com.thedeadpixelsociety.ld34.components.TintComponent
import com.thedeadpixelsociety.ld34.components.PositionComponent
import se.exuvo.aurora.Assets
import se.exuvo.aurora.utils.GameServices
import java.util.Comparator

class RenderSystem : SortedIteratingSystem(RenderSystem.FAMILY, ZOrderComparator()) {
	companion object {
		val FAMILY = Family.all(BoundsComponent::class.java, PositionComponent::class.java, RenderComponent::class.java).get()
	}

	private val boundsMapper = ComponentMapper.getFor(BoundsComponent::class.java)
	private val renderMapper = ComponentMapper.getFor(RenderComponent::class.java)
	private val tintMapper = ComponentMapper.getFor(TintComponent::class.java)
	private val positionMapper = ComponentMapper.getFor(PositionComponent::class.java)
	private val tagMapper = ComponentMapper.getFor(TagComponent::class.java)
	private val groupMapper = ComponentMapper.getFor(GroupComponent::class.java)
	private val textMapper = ComponentMapper.getFor(TextComponent::class.java)
	private val renderer by lazy { GameServices[ShapeRenderer::class.java] }
	private val batch by lazy { GameServices[SpriteBatch::class.java] }
	private var shadows = false
	var rotation = 0f
	var angle = 0f
		get() = field
		set(value) {
			field = value
			shadowOffset.rotate(value)
		}
	var shadowOffset = Vector2(2.5f, -2.5f)

	override fun checkProcessing() = false

	override fun processEntity(entity: Entity?, deltaTime: Float) {
		val bounds = boundsMapper.get(entity)
		val render = renderMapper.get(entity)
		val position = positionMapper.get(entity)
		val tint = tintMapper.get(entity)
		val tag = tagMapper.get(entity)
		val group = groupMapper.get(entity)
		val text = textMapper.get(entity)

		val color = Color(tint?.color ?: Color.WHITE)
		color.a = .5f
		renderer.color = color
		val isText = group != null && group.group == "text" && text != null

		if (!isText) {
			val shadowExclude = (tag != null && tag.tag == "player") || (group != null && group.group == "coin")

			if (!shadows || (shadows && !shadowExclude)) {
				when (render.type) {
					RenderType.CIRCLE -> {
//                        if (tag != null && tag.tag == "player" && box2d != null && box2d.body != null) {
//                            val velocity = box2d.body!!.linearVelocity
//
//                            val r2 = radius * 2f
//                            val t = velocity.len() / 175f
//                            val tmp = Vector2(velocity).nor()
//                            var rx = (r2 + ((r2 * .5f * t) * Math.abs(tmp.x)))
//                            var ry = (r2 + ((r2 * .5f * t) * Math.abs(tmp.y)))
//
//                            renderer.ellipse(transform.position.x - radius, transform.position.y - radius, rx, ry, 64)
//                        } else {
						renderer.circle(position.position.x, position.position.y, bounds.radius * 1f, 64)
//                        }
					}
					RenderType.LINE -> {
						renderer.line(position.position.x + (if (shadows) shadowOffset.x else 0f), position.position.y + (if (shadows) shadowOffset.y else 0f), bounds.radius, bounds.radius)
					}
					else -> {
					}
				}
			}

			renderer.color = Color.WHITE
		}
	}

	fun render(viewport: Viewport) {
		Gdx.gl20.glEnable(GL20.GL_BLEND)
		Gdx.gl20.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
		begin(viewport)
		shadows = true
		super.update(0f)
		end()
		Gdx.gl20.glDisable(GL20.GL_BLEND)
		begin(viewport)
		shadows = false
		super.update(0f)
		end()

		beginBatch(viewport)
		val font = Assets.fontMap
		val scale = font.getData().scaleX;
		font.getData().setScale((viewport.camera as OrthographicCamera).zoom * scale)

		entities.filter { textMapper.has(it) }.forEach {
			val bounds = boundsMapper.get(it)
			val transform = positionMapper.get(it)
			val text = textMapper.get(it)

			font.draw(batch, text.text,
							transform.position.x - bounds.radius * .5f,
							transform.position.y - bounds.radius * .5f + font.lineHeight)
		}

		font.getData().setScale(scale)
		endBatch()
	}

	private fun begin(viewport: Viewport) {
		viewport.apply()
		renderer.projectionMatrix = viewport.camera.combined
		renderer.begin(ShapeRenderer.ShapeType.Filled)
	}

	private fun end() {
		renderer.end()
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
	override fun compare(o1: Entity?, o2: Entity?): Int {
		val r1 = o1?.getComponent(RenderComponent::class.java)
		val r2 = o2?.getComponent(RenderComponent::class.java)

		return if (r1 != null && r2 != null) r1.zOrder.compareTo(r2.zOrder) else 0
	}
}
