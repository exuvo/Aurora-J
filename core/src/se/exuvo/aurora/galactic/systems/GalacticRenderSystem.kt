package se.exuvo.aurora.galactic.systems

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.Viewport
import se.exuvo.aurora.Assets
import se.exuvo.aurora.planetarysystems.components.GalacticPositionComponent
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.planetarysystems.components.RenderComponent
import se.exuvo.aurora.planetarysystems.components.StrategicIconComponent
import se.exuvo.aurora.planetarysystems.components.TextComponent
import se.exuvo.aurora.planetarysystems.components.TintComponent
import se.exuvo.aurora.planetarysystems.systems.GroupSystem
import se.exuvo.aurora.screens.GameScreenService
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Vector2L
import java.util.Comparator
import com.artemis.Aspect
import com.artemis.systems.IteratingSystem
import com.artemis.ComponentMapper
import com.artemis.BaseEntitySystem
import se.exuvo.aurora.AuroraGame

class GalacticRenderSystem : BaseEntitySystem(FAMILY) {

	companion object {
		val FAMILY = Aspect.all(GalacticPositionComponent::class.java, RenderComponent::class.java, StrategicIconComponent::class.java)
		val STRATEGIC_ICON_SIZE = 24f
		val RENDER_SCALE = 10
	}

	lateinit private var tintMapper: ComponentMapper<TintComponent>
	lateinit private var positionMapper: ComponentMapper<GalacticPositionComponent>
	lateinit private var textMapper: ComponentMapper<TextComponent>
	lateinit private var nameMapper: ComponentMapper<NameComponent>
	lateinit private var strategicIconMapper: ComponentMapper<StrategicIconComponent>

	lateinit private var groupSystem: GroupSystem

	override fun checkProcessing() = false
	override fun processSystem() {}

	fun drawStrategicEntities(entityIDs: IntArray, viewport: Viewport, cameraOffset: Vector2L) {

		val spriteBatch = AuroraGame.currentWindow.spriteBatch
		val zoom = (viewport.camera as OrthographicCamera).zoom

		spriteBatch.begin()

		for (entityID in entityIDs) {

				val position = positionMapper.get(entityID)
				val tintComponent = if (tintMapper.has(entityID)) tintMapper.get(entityID) else null
				var x = position.getXinRender() - cameraOffset.x
				var y = position.getYinRender() - cameraOffset.y
				val texture = strategicIconMapper.get(entityID).texture

				val color = Color(tintComponent?.color ?: Color.WHITE)

				// https://github.com/libgdx/libgdx/wiki/Spritebatch%2C-Textureregions%2C-and-Sprites
				spriteBatch.setColor(color.r, color.g, color.b, color.a);

				val width = zoom * STRATEGIC_ICON_SIZE
				val height = zoom * STRATEGIC_ICON_SIZE
				x = x - width / 2
				y = y - height / 2

				spriteBatch.draw(texture, x, y, width, height)
		}

		spriteBatch.end()
	}
	
	fun drawWormholeConnections(entityIDs: IntArray, viewport: Viewport, cameraOffset: Vector2L) {

		val shapeRenderer = AuroraGame.currentWindow.shapeRenderer
		
		shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

//		val zoom = (viewport.camera as OrthographicCamera).zoom
//
//		for (entity in entities) {
//			val position = positionMapper.get(entity)
//			val tintComponent = if (tintMapper.has(entity)) tintMapper.get(entity) else null
//			val x = (position.getXinKM() - cameraOffset.x).toFloat()
//			val y = (position.getYinKM() - cameraOffset.y).toFloat()
//
//			val color = Color(tintComponent?.color ?: Color.WHITE)
//			shapeRenderer.color = color
//
//			if (circleMapper.has(entity)) {
//
//				val circle = circleMapper.get(entity)
//				shapeRenderer.circle(x, y, circle.radius, getCircleSegments(circle.radius, zoom))
//
//			} else if (lineMapper.has(entity)) {
//
//				val line = lineMapper.get(entity)
//				shapeRenderer.line(x, y, x + line.x, y + line.y)
//			}
//		}

		shapeRenderer.end()
	}

	fun render(viewport: Viewport, cameraOffset: Vector2L) {

		val shapeRenderer = AuroraGame.currentWindow.shapeRenderer
		val spriteBatch = AuroraGame.currentWindow.spriteBatch
		val uiCamera = AuroraGame.currentWindow.screenService.uiCamera

		val entityIDs: IntArray = subscription.getEntities().getData();

		viewport.apply()
		shapeRenderer.projectionMatrix = viewport.camera.combined
		
		drawWormholeConnections(entityIDs, viewport, cameraOffset)

		spriteBatch.projectionMatrix = viewport.camera.combined

		drawStrategicEntities(entityIDs, viewport, cameraOffset)

		spriteBatch.projectionMatrix = uiCamera.combined
		spriteBatch.begin()

		val font = Assets.fontMap
		font.color = Color.GREEN
		val zoom = (viewport.camera as OrthographicCamera).zoom
		val screenPosition = Vector3()

		entityIDs.filter { nameMapper.has(it) }.forEach {
			val position = positionMapper.get(it)
			val name = nameMapper.get(it).name

			var radius = zoom * STRATEGIC_ICON_SIZE / 2

			val x = position.getXinRender() - cameraOffset.x
			val y = position.getYinRender() - cameraOffset.y

			screenPosition.set(x, y - radius * 1.1f, 0f)
			viewport.camera.project(screenPosition)

			font.draw(spriteBatch, name, screenPosition.x - name.length * font.spaceWidth * .5f, screenPosition.y - font.lineHeight / zoom)
		}

		spriteBatch.end()
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
}

