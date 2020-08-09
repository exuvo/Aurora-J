package se.exuvo.aurora.starsystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.EntitySubscription
import com.artemis.annotations.Wire
import com.artemis.systems.IteratingSystem
import com.artemis.utils.IntBag
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.Viewport
import org.apache.commons.math3.util.FastMath
import org.apache.logging.log4j.LogManager
import org.lwjgl.BufferUtils
import se.exuvo.aurora.Assets
import se.exuvo.aurora.AuroraGame
import se.exuvo.aurora.Resizable
import se.exuvo.aurora.empires.components.ActiveTargetingComputersComponent
import se.exuvo.aurora.empires.components.IdleTargetingComputersComponent
import se.exuvo.aurora.galactic.AdvancedMunitionHull
import se.exuvo.aurora.galactic.BeamWeapon
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.MissileLauncher
import se.exuvo.aurora.galactic.PartRef
import se.exuvo.aurora.galactic.Railgun
import se.exuvo.aurora.galactic.SimpleMunitionHull
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.starsystems.ShadowStarSystem
import se.exuvo.aurora.starsystems.StarSystem
import se.exuvo.aurora.starsystems.components.AmmunitionPartState
import se.exuvo.aurora.starsystems.components.CircleComponent
import se.exuvo.aurora.starsystems.components.DetectionComponent
import se.exuvo.aurora.starsystems.components.LaserShotComponent
import se.exuvo.aurora.starsystems.components.MissileComponent
import se.exuvo.aurora.starsystems.components.MoveToEntityComponent
import se.exuvo.aurora.starsystems.components.MoveToPositionComponent
import se.exuvo.aurora.starsystems.components.NameComponent
import se.exuvo.aurora.starsystems.components.OrbitComponent
import se.exuvo.aurora.starsystems.components.PassiveSensorsComponent
import se.exuvo.aurora.starsystems.components.RailgunShotComponent
import se.exuvo.aurora.starsystems.components.RenderComponent
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.components.Spectrum
import se.exuvo.aurora.starsystems.components.StrategicIconComponent
import se.exuvo.aurora.starsystems.components.TargetingComputerState
import se.exuvo.aurora.starsystems.components.ThrustComponent
import se.exuvo.aurora.starsystems.components.TimedMovementComponent
import se.exuvo.aurora.starsystems.components.TintComponent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.utils.Vector2D
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.utils.sRGBtoLinearRGB
import se.exuvo.aurora.utils.scanCircleSector
import se.exuvo.aurora.utils.toLinearRGB
import se.exuvo.settings.Settings
import java.nio.IntBuffer
import kotlin.math.sign

class RenderSystem : IteratingSystem(FAMILY) {
	companion object {
		@JvmField val FAMILY = Aspect.all(TimedMovementComponent::class.java, RenderComponent::class.java, CircleComponent::class.java)
		@JvmField val LASER_SHOT_FAMILY = Aspect.all(TimedMovementComponent::class.java, RenderComponent::class.java, LaserShotComponent::class.java)
		@JvmField val RAILGUN_SHOT_FAMILY = Aspect.all(TimedMovementComponent::class.java, RenderComponent::class.java, RailgunShotComponent::class.java)
		@JvmField val MISSILE_FAMILY = Aspect.all(TimedMovementComponent::class.java, RenderComponent::class.java, MissileComponent::class.java)
		
		const val STRATEGIC_ICON_SIZE = 24f
		const val MAX_VERTICES = 128
		const val MAX_INDICES = 256
		
		@JvmField var debugPassiveSensors = Settings.getBol("Systems/Render/debugPassiveSensors", false)
		@JvmField var debugDisableStrategicView = Settings.getBol("Systems/Render/debugDisableStrategicView", false)
		@JvmField var debugDrawWeaponRangesWithoutShader = Settings.getBol("Systems/Render/debugDrawWeaponRangesWithoutShader", false)
		@JvmField val log = LogManager.getLogger(RenderSystem::class.java)
	}
	
	lateinit private var orbitMapper: ComponentMapper<OrbitComponent>
	lateinit private var circleMapper: ComponentMapper<CircleComponent>
	lateinit private var tintMapper: ComponentMapper<TintComponent>
//	lateinit private var textMapper: ComponentMapper<TextComponent>
	lateinit private var nameMapper: ComponentMapper<NameComponent>
	lateinit private var moveToEntityMapper: ComponentMapper<MoveToEntityComponent>
	lateinit private var moveToPositionMapper: ComponentMapper<MoveToPositionComponent>
	lateinit private var strategicIconMapper: ComponentMapper<StrategicIconComponent>
	lateinit private var movementMapper: ComponentMapper<TimedMovementComponent>
	lateinit private var thrustMapper: ComponentMapper<ThrustComponent>
	lateinit private var detectionMapper: ComponentMapper<DetectionComponent>
	lateinit private var sensorsMapper: ComponentMapper<PassiveSensorsComponent>
	lateinit private var shipMapper: ComponentMapper<ShipComponent>
	lateinit private var idleTargetingComputersComponentMapper: ComponentMapper<IdleTargetingComputersComponent>
	lateinit private var activeTargetingComputersComponentMapper: ComponentMapper<ActiveTargetingComputersComponent>

	private val galaxyGroupSystem by lazy (LazyThreadSafetyMode.NONE) { GameServices[GroupSystem::class] }
	private val galaxy = GameServices[Galaxy::class]
	
	@Wire
	lateinit private var starSystem: StarSystem
	
	@Wire
	lateinit private var shadowSystem: ShadowStarSystem
	
	lateinit private var familyAspect: Aspect
	lateinit private var laserShotSubscription: EntitySubscription
	lateinit private var railgunShotSubscription: EntitySubscription
	lateinit private var missileSubscription: EntitySubscription
	lateinit private var orbitSubscription: EntitySubscription
	
	private val tempL = Vector2L()
	private val tempD = Vector2D()
	private val tempF = Vector2()
	private val temp3F = Vector3()
	private val tmpIntBuffer: IntBuffer = BufferUtils.createIntBuffer(4)
	
	private var scale: Float = 1f
	lateinit private var viewport: Viewport
	private var cameraOffset: Vector2L = Vector2L.Zero
	private var shapeRenderer: ShapeRenderer  = AuroraGame.currentWindow.shapeRenderer
	private var spriteBatch: SpriteBatch = AuroraGame.currentWindow.spriteBatch
	private var uiCamera: OrthographicCamera = AuroraGame.currentWindow.screenService.uiCamera
	private var displaySize: Int = 0

	init {
		var globalData = AuroraGame.storage(RenderGlobalData::class)
		
		if (globalData == null) {
			globalData = RenderGlobalData()
			AuroraGame.storage + globalData
		}
	}
	
	override fun initialize() {
		super.initialize()

		familyAspect = FAMILY.build(world)
		
		laserShotSubscription = world.getAspectSubscriptionManager().get(LASER_SHOT_FAMILY)
		railgunShotSubscription = world.getAspectSubscriptionManager().get(RAILGUN_SHOT_FAMILY)
		missileSubscription  = world.getAspectSubscriptionManager().get(MISSILE_FAMILY)
		orbitSubscription  = world.getAspectSubscriptionManager().get(OrbitSystem.FAMILY)
	}

	override fun checkProcessing() = false

	override fun process(entityID: Int) {}
	
	class RenderGlobalData: Disposable {
		val circleShader: ShaderProgram = Assets.circleShaderProgram
		val diskShader: ShaderProgram = Assets.diskShaderProgram
		val vertices: FloatArray
		val indices: ShortArray
		val mesh: Mesh

		init {
			
			if (!circleShader.isCompiled || circleShader.getLog().length != 0) {
				log.error("Shader circleShader compile error ${circleShader.getLog()}")
				debugDrawWeaponRangesWithoutShader = true
			}
			
			

			if (!diskShader.isCompiled || diskShader.getLog().length != 0) {
				log.error("Shader diskShader compile error ${circleShader.getLog()}")
				debugDrawWeaponRangesWithoutShader = true
			}
			
			vertices = FloatArray(MAX_VERTICES);
			indices = ShortArray(MAX_INDICES)
			
			mesh = Mesh(false, MAX_VERTICES, MAX_INDICES,
					VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE)
			);
		}
		
		override fun dispose() {
			mesh.dispose()
		}
	}
	
	class RenderWindowData(): Disposable, Resizable {

		var fbo: FrameBuffer

		init {
			fbo = FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.getWidth(),Gdx.graphics.getHeight(), false)
			
		}
		
		override fun resize(width: Int, height: Int) {
			fbo.dispose()
			fbo = FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.getWidth(),Gdx.graphics.getHeight(), false)
		}

		override fun dispose() {
			fbo.dispose()
		}
	}
	
	fun wData(): RenderWindowData {
		var wData = AuroraGame.currentWindow.storage(RenderWindowData::class)
		
		if (wData == null) {
			wData = RenderWindowData()
			AuroraGame.currentWindow.storage + wData
		}
		
		return wData
	}
	
	fun gData() = AuroraGame.storage[RenderGlobalData::class]
	
	private fun drawEntities(entityIDs: IntBag) {

		shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

		entityIDs.forEachFast { entityID ->

			if (!strategicIconMapper.has(entityID) || !inStrategicView(entityID)) {

				val movement = movementMapper.get(entityID).get(galaxy.time).value
				val tintComponent = if (tintMapper.has(entityID)) tintMapper.get(entityID) else null
				val x = (movement.getXinKM() - cameraOffset.x).toFloat()
				val y = (movement.getYinKM() - cameraOffset.y).toFloat()

				shapeRenderer.color = sRGBtoLinearRGB(Color(tintComponent?.color ?: Color.WHITE))

				val circle = circleMapper.get(entityID)
				shapeRenderer.circle(x, y, circle.radius / 1000, getCircleSegments(circle.radius, scale))
			}
		}

		shapeRenderer.end()
	}

	private fun drawEntityCenters(entityIDs: IntBag) {

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
		shapeRenderer.color = sRGBtoLinearRGB(Color.PINK)

		entityIDs.forEachFast { entityID ->

			if (!strategicIconMapper.has(entityID) || !inStrategicView(entityID)) {

				val movement = movementMapper.get(entityID).get(galaxy.time).value
				val x = (movement.getXinKM() - cameraOffset.x).toFloat()
				val y = (movement.getYinKM() - cameraOffset.y).toFloat()

				val circle = circleMapper.get(entityID)
				shapeRenderer.circle(x, y, circle.radius / 1000 * 0.01f, getCircleSegments(circle.radius * 0.01f, scale))
			}
		}

		shapeRenderer.end()
	}
	
	private class WeaponRange {
		var radius: Float = 0f
		var x: Float = 0f
		var y: Float = 0f
	}
	private val railgunRanges = ArrayList<WeaponRange>()
	private val laserRanges = ArrayList<WeaponRange>()
	private val missileRanges = ArrayList<WeaponRange>()
	
	private fun drawWeaponRanges(entityIDs: IntBag, selectedEntityIDs: List<Int>) {

		if (debugDrawWeaponRangesWithoutShader) {
		
			shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
			
			entityIDs.forEachFast { entityID ->

				if (selectedEntityIDs.contains(entityID) && movementMapper.has(entityID) &&
					(idleTargetingComputersComponentMapper.has(entityID) || activeTargetingComputersComponentMapper.has(entityID))) {
	
					val tcs: List<PartRef<TargetingComputer>> = idleTargetingComputersComponentMapper.get(entityID)?.targetingComputers ?: activeTargetingComputersComponentMapper.get(entityID).targetingComputers
					
					val movement = movementMapper.get(entityID).get(galaxy.time).value
					val ship = shipMapper.get(entityID)
					
					val x = (((movement.position.x.sign * 500 + movement.position.x) / 1000L) - cameraOffset.x).toFloat()
					val y = (((movement.position.y.sign * 500 + movement.position.y) / 1000L) - cameraOffset.y).toFloat()
					val timeToImpact = 10 // s
					
					tcs.forEachFast{ tc ->
						val tcState = ship.getPartState(tc)[TargetingComputerState::class]
						
						tcState.linkedWeapons.forEachFast weaponLoop@{ weapon ->
							if (ship.isPartEnabled(weapon)) {
								val part = weapon.part
								
								val range: Long
								
								when (part) {
									is BeamWeapon -> {
										val projectileSpeed = (Units.C * 1000).toLong()
										val damage: Long = (part.efficiency * part.capacitor) / 100
										val dmg1Range = FastMath.sqrt(damage / FastMath.PI) / FastMath.tan(part.getRadialDivergence())
										val timeRange = projectileSpeed * timeToImpact
										
										range = FastMath.min(timeRange, dmg1Range.toLong())
										
										shapeRenderer.color = sRGBtoLinearRGB(Color.PURPLE)
									}
									is Railgun -> {
										val ammoState = ship.getPartState(weapon)[AmmunitionPartState::class]
										val munitionHull = ammoState.type as? SimpleMunitionHull
										
										if (munitionHull == null) {
											return@weaponLoop
										}
										
										val projectileSpeed = (part.capacitor * part.efficiency) / (100 * munitionHull.loadedMass)
										range = projectileSpeed * timeToImpact
										
										shapeRenderer.color = sRGBtoLinearRGB(Color.ORANGE)
									}
									is MissileLauncher -> {
										val ammoState = ship.getPartState(weapon)[AmmunitionPartState::class]
										val advMunitionHull = ammoState.type!! as? AdvancedMunitionHull
										
										if (advMunitionHull == null) {
											return@weaponLoop
										}
										
										val missileAcceleration = advMunitionHull.getAverageAcceleration()
										val missileLaunchSpeed = (100 * part.launchForce) / advMunitionHull.loadedMass
										val flightTime = advMunitionHull.fuelMass
										
										range = (missileLaunchSpeed * flightTime + (missileAcceleration * flightTime * flightTime) / 2) / 100
										
										shapeRenderer.color = sRGBtoLinearRGB(Color.RED)
									}
									else -> {
										return@weaponLoop
									}
								}
								
								// everything in km
								val radius = (range / 1000).toFloat()
								val cameraDistance = (tempL.set(movement.position).div(1000).dst(cameraOffset)).toFloat()
								val viewLength = scale * displaySize
								
								//TODO use on all line circles
								val cameraEdgeInsideCircle =  cameraDistance < radius - viewLength/2
								val circleOutsideCamera = cameraDistance > radius + viewLength/2
								
								if (radius / scale > 20 && !cameraEdgeInsideCircle && !circleOutsideCamera) {
									
									val segments = getCircleSegments(radius, scale)
									shapeRenderer.circle(x, y, radius, segments)
								}
							}
						}
					}
				}
			}
			
			shapeRenderer.end()
			
		} else {

			val rangePool = starSystem.pools.getPool(WeaponRange::class.java)

			missileRanges.forEachFast { it ->
				rangePool.free(it)
			}
			laserRanges.forEachFast { it ->
				rangePool.free(it)
			}
			railgunRanges.forEachFast { it ->
				rangePool.free(it)
			}
			
			missileRanges.clear();
			laserRanges.clear();
			railgunRanges.clear();

			entityIDs.forEachFast { entityID ->

				if (selectedEntityIDs.contains(entityID) && movementMapper.has(entityID)) {

					val idleTCs = idleTargetingComputersComponentMapper.get(entityID)
					val activeTCs = activeTargetingComputersComponentMapper.get(entityID)

					if (idleTCs != null || activeTCs != null) {

						val movement = movementMapper.get(entityID).get(galaxy.time).value
						val ship = shipMapper.get(entityID)

						val x = (((movement.position.x.sign * 500 + movement.position.x) / 1000L) - cameraOffset.x).toFloat()
						val y = (((movement.position.y.sign * 500 + movement.position.y) / 1000L) - cameraOffset.y).toFloat()
						val timeToImpact = 10 // s

						fun getRanges(tcs: List<PartRef<TargetingComputer>>) {
							tcs.forEachFast { tc ->
								val tcState = ship.getPartState(tc)[TargetingComputerState::class]

								tcState.linkedWeapons.forEachFast weaponLoop@{ weapon ->
									if (ship.isPartEnabled(weapon)) {
										val part = weapon.part

										val range: Long

										when (part) {
											is BeamWeapon -> {
												val projectileSpeed = (Units.C * 1000).toLong()
												val damage: Long = (part.efficiency * part.capacitor) / 100
												val dmg1Range = FastMath.sqrt(damage / FastMath.PI) / FastMath.tan(part.getRadialDivergence())
												val timeRange = projectileSpeed * timeToImpact

												//									println("damage $damage, dmg1Range $dmg1Range, timeRange $timeRange")
												range = FastMath.min(timeRange, dmg1Range.toLong())
											}
											is Railgun -> {
												val ammoState = ship.getPartState(weapon)[AmmunitionPartState::class]
												val munitionHull = ammoState.type as? SimpleMunitionHull

												if (munitionHull == null) {
													return@weaponLoop
												}

												val projectileSpeed = (part.capacitor * part.efficiency) / (100 * munitionHull.loadedMass)
												range = projectileSpeed * timeToImpact
											}
											is MissileLauncher -> {
												val ammoState = ship.getPartState(weapon)[AmmunitionPartState::class]
												val advMunitionHull = ammoState.type!! as? AdvancedMunitionHull

												if (advMunitionHull == null) {
													return@weaponLoop
												}

												val missileAcceleration = advMunitionHull.getAverageAcceleration()
												val missileLaunchSpeed = (100 * part.launchForce) / advMunitionHull.loadedMass
												val flightTime = advMunitionHull.fuelMass

												range = (missileLaunchSpeed * flightTime + (missileAcceleration * flightTime * flightTime) / 2) / 100
											}
											else -> {
												return@weaponLoop
											}
										}

										// everything in km
										val radius = (range / 1000).toFloat()
										val cameraDistance = (tempL.set(movement.position).div(1000).dst(cameraOffset)).toFloat()
										val viewLength = scale * displaySize

										val cameraEdgeInsideCircle = cameraDistance < radius - viewLength / 2
										val circleOutsideCamera = cameraDistance > radius + viewLength / 2

										if (radius / scale > 20 && !cameraEdgeInsideCircle && !circleOutsideCamera) {

											val weaponRange = rangePool.obtain()
											weaponRange.radius = radius
											weaponRange.x = x
											weaponRange.y = y
											
											when (part) {
												is BeamWeapon -> {
													laserRanges += weaponRange
												}
												is Railgun -> {
													railgunRanges += weaponRange
												}
												is MissileLauncher -> {
													missileRanges += weaponRange
												}
											}
										}
									}
								}
							}
						}
						
						if (idleTCs != null ) {
							getRanges(idleTCs.targetingComputers)
						}
						
						if (activeTCs != null) {
							getRanges(activeTCs.targetingComputers)
						}
					}
				}
			}
			
			val projectionMatrix = viewport.camera.combined

			val gData = gData()
			val wData = wData()

			val vertices = gData.vertices
			val indices = gData.indices
			val cShader = gData.circleShader
			val dShader = gData.diskShader
			val mesh = gData.mesh
			val fbo = wData.fbo

			var vertexIdx = 0
			var indiceIdx: Int
			val padding = 1 * scale
			
			fun vertex(x: Float, y: Float) {
				vertices[vertexIdx++] = x;
				vertices[vertexIdx++] = y;
			}
			
			// https://gamedev.stackexchange.com/questions/81686/how-to-make-unit-selection-circles-merge
			fun drawWeaponRanges2(weaponRanges: List<WeaponRange>, color: Color) {
				
				if (weaponRanges.isEmpty()) {
					return
				}
				
				Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, tmpIntBuffer)
				val oldFrameBufferHandle = tmpIntBuffer.get(0)
				Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, tmpIntBuffer)
				
				fbo.begin();
				Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
				Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
	
				Gdx.gl.glDisable(GL20.GL_BLEND);
				
				cShader.bind()
				cShader.setUniformMatrix("u_projTrans", projectionMatrix);
				cShader.setUniformf("u_scale", scale);
				val wepColor = sRGBtoLinearRGB(color)
				cShader.setUniformf("u_color", wepColor.r, wepColor.g, wepColor.b)
				
				//TODO draw at mouse pos if x is held
				
				// Render outer circle
				weaponRanges.forEachFast rangeLoop@{ wep ->
					val x = wep.x
					val y = wep.y
					val radius = wep.radius
					
					val minX = FastMath.max(x - radius - padding, -viewport.getScreenWidth()/2 * scale)
					val maxX = FastMath.min(x + radius + padding, viewport.getScreenWidth()/2 * scale)
					val minY = FastMath.max(y - radius - padding, -viewport.getScreenHeight()/2 * scale)
					val maxY = FastMath.min(y + radius + padding, viewport.getScreenHeight()/2 * scale)
					
					val width = maxX - minX
					val height  = maxY - minY
					
					if (height < 0 || width < 0) {
						return@rangeLoop
					}
					
					cShader.setUniformf("u_center", x, y)
					cShader.setUniformf("u_radius", radius)
					
					vertexIdx = 0
					indiceIdx = 0
					
					// Triangle 1
					indices[indiceIdx++] = 1.toShort()
					indices[indiceIdx++] = 0.toShort()
					indices[indiceIdx++] = 2.toShort()
					
					// Triangle 2
					indices[indiceIdx++] = 0.toShort()
					indices[indiceIdx++] = 3.toShort()
					indices[indiceIdx++] = 2.toShort()
					
					vertex(minX, minY);
					vertex(maxX, minY);
					vertex(maxX, maxY);
					vertex(minX, maxY);
					
					mesh.setVertices(vertices, 0, vertexIdx)
					mesh.setIndices(indices, 0, indiceIdx)
					mesh.render(cShader, GL20.GL_TRIANGLES)
				}
				
				dShader.bind()
				dShader.setUniformMatrix("u_projTrans", projectionMatrix);
	
				// Render inner circle
				weaponRanges.forEachFast rangeLoop@{ wep ->
					val x = wep.x
					val y = wep.y
					val radius = wep.radius - 1 * scale
					
					val minX = FastMath.max(x - radius - padding, -viewport.getScreenWidth()/2 * scale)
					val maxX = FastMath.min(x + radius + padding, viewport.getScreenWidth()/2 * scale)
					val minY = FastMath.max(y - radius - padding, -viewport.getScreenHeight()/2 * scale)
					val maxY = FastMath.min(y + radius + padding, viewport.getScreenHeight()/2 * scale)
					
					val width = maxX - minX
					val height  = maxY - minY
					
					if (height < 0 || width < 0) {
						return@rangeLoop
					}
					
					dShader.setUniformf("u_center", x, y)
					dShader.setUniformf("u_radius", radius)
					
					vertexIdx = 0
					indiceIdx = 0
					
					// Triangle 1
					indices[indiceIdx++] = 1.toShort()
					indices[indiceIdx++] = 0.toShort()
					indices[indiceIdx++] = 2.toShort()
					
					// Triangle 2
					indices[indiceIdx++] = 0.toShort()
					indices[indiceIdx++] = 3.toShort()
					indices[indiceIdx++] = 2.toShort()
					
					vertex(minX, minY);
					vertex(maxX, minY);
					vertex(maxX, maxY);
					vertex(minX, maxY);
					
					mesh.setVertices(vertices, 0, vertexIdx)
					mesh.setIndices(indices, 0, indiceIdx)
					mesh.render(dShader, GL20.GL_TRIANGLES)
				}
				
				// Restore previous framebuffer instead of default as fbo.end() would do
				Gdx.gl20.glBindFramebuffer(GL20.GL_FRAMEBUFFER, oldFrameBufferHandle);
				Gdx.gl20.glViewport(tmpIntBuffer.get(0), tmpIntBuffer.get(1), tmpIntBuffer.get(2), tmpIntBuffer.get(3));
				
				Gdx.gl.glEnable(GL20.GL_BLEND);
				Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
				
				val fboTex = fbo.getColorBufferTexture()
				
				spriteBatch.begin()
				spriteBatch.setColor(Color.WHITE)
				spriteBatch.draw(fboTex, 0f, 0f, viewport.getScreenWidth().toFloat(), viewport.getScreenHeight().toFloat(), 0, 0, fboTex.getWidth(), fboTex.getHeight(), false, true)
				spriteBatch.end()
				
				Gdx.gl.glDisable(GL20.GL_BLEND);
			}
			
			drawWeaponRanges2(missileRanges, Color.RED)
			drawWeaponRanges2(laserRanges, Color.PURPLE)
			drawWeaponRanges2(railgunRanges, Color.ORANGE)
		}
	}
	
	private fun drawProjectiles() {

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
		shapeRenderer.color = sRGBtoLinearRGB(Color.BLUE)
		
		laserShotSubscription.getEntities().forEachFast { entityID ->

			val movement = movementMapper.get(entityID)
			val movementNow = movement.get(galaxy.time).value
			val movementPrev = movement.previous.value
			val x = (movementNow.getXinKM() - cameraOffset.x).toFloat()
			val y = (movementNow.getYinKM() - cameraOffset.y).toFloat()

			tempF.set(10 * scale, 0f).rotateRad(MathUtils.PI + movementPrev.position.angleToRad(movement.aimTarget).toFloat()).add(x, y)
			
			val x2 = tempF.x
			val y2 = tempF.y
			
			shapeRenderer.line(x, y, x2, y2)
		}
		
		shapeRenderer.end()
		
		shapeRenderer.begin(ShapeRenderer.ShapeType.Point)
		shapeRenderer.color = sRGBtoLinearRGB(Color.GRAY)
		
		railgunShotSubscription.getEntities().forEachFast { entityID ->
			
			val movement = movementMapper.get(entityID).get(galaxy.time).value
			val x = (movement.getXinKM() - cameraOffset.x).toFloat()
			val y = (movement.getYinKM() - cameraOffset.y).toFloat()
			
			shapeRenderer.point(x, y, 0f)
		}
		
		shapeRenderer.color = sRGBtoLinearRGB(Color.WHITE)
		
		missileSubscription.getEntities().forEachFast { entityID ->
			
			val movement = movementMapper.get(entityID).get(galaxy.time).value
			val x = (movement.getXinKM() - cameraOffset.x).toFloat()
			val y = (movement.getYinKM() - cameraOffset.y).toFloat()
			
			shapeRenderer.point(x, y, 0f)
		}

		shapeRenderer.end()
		
		// Draw intercept position
		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)

		shapeRenderer.color = sRGBtoLinearRGB(Color.RED)
		laserShotSubscription.getEntities().forEachFast { entityID ->

			val movement = movementMapper.get(entityID).next
			
			if (movement != null) {
			
				val x = (((movement.value.position.x.sign * 500 + movement.value.position.x) / 1000L) - cameraOffset.x).toFloat()
				val y = (((movement.value.position.y.sign * 500 + movement.value.position.y) / 1000L) - cameraOffset.y).toFloat()
				
				val radius = scale * STRATEGIC_ICON_SIZE / 3 + 4 * scale
				val segments = getCircleSegments(radius, scale)

				shapeRenderer.circle(x, y, radius, segments)
			}
		}
		
		railgunShotSubscription.getEntities().forEachFast { entityID ->
			
			val movement = movementMapper.get(entityID).next
			
			if (movement != null) {
			
				val x = (((movement.value.position.x.sign * 500 + movement.value.position.x) / 1000L) - cameraOffset.x).toFloat()
				val y = (((movement.value.position.y.sign * 500 + movement.value.position.y) / 1000L) - cameraOffset.y).toFloat()
				
				val radius = scale * STRATEGIC_ICON_SIZE / 3 + 4 * scale
				val segments = getCircleSegments(radius, scale)

				shapeRenderer.circle(x, y, radius, segments)
			}
		}
		
		missileSubscription.getEntities().forEachFast { entityID ->
			
			val movement = movementMapper.get(entityID).next
			
			if (movement != null) {
			
				val x = (((movement.value.position.x.sign * 500 + movement.value.position.x) / 1000L) - cameraOffset.x).toFloat()
				val y = (((movement.value.position.y.sign * 500 + movement.value.position.y) / 1000L) - cameraOffset.y).toFloat()
				
				val radius = scale * STRATEGIC_ICON_SIZE / 3 + 4 * scale
				val segments = getCircleSegments(radius, scale)

				shapeRenderer.circle(x, y, radius, segments)
			}
		}
		
		shapeRenderer.end()
	}

	fun inStrategicView(entityID: Int, scale: Float = this.scale): Boolean {

		if (debugDisableStrategicView) {
			return false
		}

		val radius = circleMapper.get(entityID).radius / 1000
		return radius / scale < 5f
	}

	private fun drawStrategicEntities(entityIDs: IntBag) {

		spriteBatch.begin()

		entityIDs.forEachFast { entityID ->

			if (strategicIconMapper.has(entityID) && inStrategicView(entityID)) {

				val movement = movementMapper.get(entityID).get(galaxy.time).value
				val tintComponent = if (tintMapper.has(entityID)) tintMapper.get(entityID) else null
				var x = (movement.getXinKM() - cameraOffset.x).toFloat()
				var y = (movement.getYinKM() - cameraOffset.y).toFloat()
				val texture = strategicIconMapper.get(entityID).texture

				// https://github.com/libgdx/libgdx/wiki/Spritebatch%2C-Textureregions%2C-and-Sprites
				spriteBatch.color = sRGBtoLinearRGB(Color(tintComponent?.color ?: Color.WHITE));

				val width = scale * STRATEGIC_ICON_SIZE
				val height = scale * STRATEGIC_ICON_SIZE
				x -= width / 2
				y -= height / 2

				if (thrustMapper.has(entityID)) {

					val thrustAngle = thrustMapper.get(entityID).thrustAngle

					val originX = width / 2
					val originY = height / 2
					val scale = 1f

					spriteBatch.draw(texture, x, y, originX, originY, width, height, scale, scale, thrustAngle)

				} else {

					spriteBatch.draw(texture, x, y, width, height)
				}
			}
		}

		spriteBatch.end()
	}

	private fun drawSelections(selectedEntityIDs: List<Int>) {

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
		shapeRenderer.color = sRGBtoLinearRGB(Color.RED)

		selectedEntityIDs.forEachFast { entityID ->

			val movement = movementMapper.get(entityID).get(galaxy.time).value
			val x = (movement.getXinKM() - cameraOffset.x).toFloat()
			val y = (movement.getYinKM() - cameraOffset.y).toFloat()

			if (strategicIconMapper.has(entityID) && inStrategicView(entityID)) {

				val radius = scale * STRATEGIC_ICON_SIZE / 2 + 3 * scale
				val segments = getCircleSegments(radius, scale)
				shapeRenderer.circle(x, y, radius, segments)

			} else {

				val circle = circleMapper.get(entityID)
				val radius = circle.radius / 1000 + 3 * scale
				val segments = getCircleSegments(radius, scale)
				shapeRenderer.circle(x, y, radius, segments)
			}
		}

		shapeRenderer.end()
	}

	private fun drawTimedMovement(entityIDs: IntBag, selectedEntityIDs: List<Int>) {

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)

		entityIDs.forEachFast { entityID ->

			val movement = movementMapper.get(entityID)
			
			if (movement != null) {

				val strategic = strategicIconMapper.has(entityID) && inStrategicView(entityID)

				if (movement.previous.time != galaxy.time && movement.next != null) {

					val movementValues = movement.previous.value
					val x = (movementValues.getXinKM() - cameraOffset.x).toFloat()
					val y = (movementValues.getYinKM() - cameraOffset.y).toFloat()

					val nextMovementValues = movement.next!!.value
					val x2 = (nextMovementValues.getXinKM() - cameraOffset.x).toFloat()
					val y2 = (nextMovementValues.getYinKM() - cameraOffset.y).toFloat()
					
					if (strategic) {

						val radius = scale * STRATEGIC_ICON_SIZE / 2 + 4 * scale
						val segments = getCircleSegments(radius, scale)

						if (selectedEntityIDs.contains(entityID)) {
							shapeRenderer.color = sRGBtoLinearRGB(Color.GREEN)
							shapeRenderer.circle(x, y, radius, segments)
						}

						shapeRenderer.color = sRGBtoLinearRGB(Color.PINK)
						shapeRenderer.circle(x2, y2, radius, segments)

					} else {

						val circle = circleMapper.get(entityID)
						val radius = circle.radius / 1000 + 3 * scale
						val segments = getCircleSegments(radius, scale)

						if (selectedEntityIDs.contains(entityID)) {
							shapeRenderer.color = sRGBtoLinearRGB(Color.GREEN)
							shapeRenderer.circle(x, y, radius, segments)
						}

						shapeRenderer.color = sRGBtoLinearRGB(Color.PINK)
						shapeRenderer.circle(x2, y2, radius, segments)
					}
					
					val aimTarget = movement.aimTarget
					
					if (aimTarget != null && selectedEntityIDs.contains(entityID)) {
						
						val x3 = (((aimTarget.x.sign * 500 + aimTarget.x) / 1000L) - cameraOffset.x).toFloat()
						val y3 = (((aimTarget.y.sign * 500 + aimTarget.y) / 1000L) - cameraOffset.y).toFloat()
						
						shapeRenderer.color = sRGBtoLinearRGB(Color.ORANGE)
						
						if (strategic) {
	
							val radius = scale * STRATEGIC_ICON_SIZE / 3 + 4 * scale
							val segments = getCircleSegments(radius, scale)

							shapeRenderer.circle(x3, y3, radius, segments)
	
						} else {
	
							val circle = circleMapper.get(entityID)
							val radius = circle.radius / 1000 + 2 * scale
							val segments = getCircleSegments(radius, scale)
	
							shapeRenderer.circle(x3, y3, radius, segments)
						}
					}
				}
			}
		}

		shapeRenderer.end()
	}

	private fun drawSelectionMoveTargets(selectedEntityIDs: List<Int>) {

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
		shapeRenderer.color = Color(0.8f, 0.8f, 0.8f, 0.5f).toLinearRGB()

		for (entityID in selectedEntityIDs) {
			val movement = movementMapper.get(entityID).get(galaxy.time).value
			val x = (movement.getXinKM() - cameraOffset.x).toFloat()
			val y = (movement.getYinKM() - cameraOffset.y).toFloat()

			if (moveToEntityMapper.has(entityID)) {

				val targetEntity = moveToEntityMapper.get(entityID).targetID
				val targetMovement = movementMapper.get(targetEntity).get(galaxy.time).value
				val x2 = (targetMovement.getXinKM() - cameraOffset.x).toFloat()
				val y2 = (targetMovement.getYinKM() - cameraOffset.y).toFloat()
				shapeRenderer.line(x, y, x2, y2)

			} else if (moveToPositionMapper.has(entityID)) {

				val targetPosition = moveToPositionMapper.get(entityID).target
				val x2 = (getXinKM(targetPosition) - cameraOffset.x).toFloat()
				val y2 = (getYinKM(targetPosition) - cameraOffset.y).toFloat()
				shapeRenderer.line(x, y, x2, y2)
			}
		}

		shapeRenderer.end()
	}

	private fun drawAttackTargets(selectedEntityIDs: List<Int>) {

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
		shapeRenderer.color = Color(0.8f, 0f, 0f, 0.5f).toLinearRGB()

		val usedTargets = HashSet<Int>()

		selectedEntityIDs.forEachFast { entityID ->
			
			val tcc = activeTargetingComputersComponentMapper.get(entityID)
			
			if (tcc != null) {

				val movement = movementMapper.get(entityID).get(galaxy.time).value
				val x = (movement.getXinKM() - cameraOffset.x).toFloat()
				val y = (movement.getYinKM() - cameraOffset.y).toFloat()

				val ship = shipMapper.get(entityID)

				usedTargets.clear()

				tcc.targetingComputers.forEachFast { tc ->
					val tcState = ship.getPartState(tc)[TargetingComputerState::class]
					val target = tcState.target

					if (target != null && starSystem.isEntityReferenceValid(target) && usedTargets.add(target.entityID)) {

						val targetMovement = movementMapper.get(target.entityID).get(galaxy.time).value
						val x2 = (targetMovement.getXinKM() - cameraOffset.x).toFloat()
						val y2 = (targetMovement.getYinKM() - cameraOffset.y).toFloat()
						shapeRenderer.line(x, y, x2, y2)
					}
				}
			}
		}

		shapeRenderer.end()
	}
	
	//TODO implement
	private fun drawOrders() {

//		val empire = Player.current.empire;
//		if (empire != null) {
//
//			val shapeRenderer = AuroraGame.currentWindow.shapeRenderer
//
//			shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
//			shapeRenderer.color = Color(0.8f, 0f, 0f, 0.5f).toLinearRGB()
//
//			empire.orders.forEach {
//
//			}
//
//			shapeRenderer.end()
//		}
	}

	//TODO draw with shader
	private fun drawDetections(entityIDs: IntBag) {

		//TODO if multiple detections with the same strength overlap (ie they see the same things), only draw overlap

		fun drawDetectionsInner() {
			entityIDs.forEachFast { entityID ->
				val detection = detectionMapper.get(entityID)
				
				if (detection != null) {
					
					val movementValues = movementMapper.get(entityID).get(galaxy.time).value
					val x = (movementValues.getXinKM() - cameraOffset.x).toDouble()
					val y = (movementValues.getYinKM() - cameraOffset.y).toDouble()

					for (sensorEntry in detection.detections.entries) {

						val sensor = sensorEntry.key
						val arcWidth = 360.0 / sensor.part.arcSegments

						if (shapeRenderer.getCurrentType() == ShapeRenderer.ShapeType.Line) {

							when (sensor.part.spectrum) {

								Spectrum.Thermal -> {
									shapeRenderer.color = sRGBtoLinearRGB(Color.CORAL)
								}

								Spectrum.Electromagnetic -> {
									shapeRenderer.color = sRGBtoLinearRGB(Color.VIOLET)
								}

								else -> {
									shapeRenderer.color = sRGBtoLinearRGB(Color.WHITE)
								}
							}

						} else {

							when (sensor.part.spectrum) {

								Spectrum.Thermal -> {
									shapeRenderer.color = sRGBtoLinearRGB(Color.CORAL)
									shapeRenderer.color.a = 0.05f
								}

								Spectrum.Electromagnetic -> {
									shapeRenderer.color = sRGBtoLinearRGB(Color.VIOLET)
									shapeRenderer.color.a = 0.05f
								}

								else -> {
									shapeRenderer.color = sRGBtoLinearRGB(Color.WHITE)
									shapeRenderer.color.a = 0.05f
								}
							}
						}

						val angleSteps = sensorEntry.value

						for (angleEntry in angleSteps.entries) {

							val angleStep = angleEntry.key
							val arcAngle = sensor.part.angleOffset + angleStep * arcWidth

							for (distanceEntry in angleEntry.value) {

								val distanceStep = distanceEntry.key

								val minRadius = distanceStep * sensor.part.distanceResolution
								val maxRadius = minRadius + sensor.part.distanceResolution
								val segments = FastMath.min(100, FastMath.max(3, getCircleSegments(maxRadius.toFloat(), scale) / 4))

								shapeRenderer.scanCircleSector(x, y, maxRadius, minRadius, arcAngle, arcWidth, segments)
							}
						}
					}
				}
			}
		}

		// https://stackoverflow.com/questions/25347456/how-to-do-blending-in-libgdx
		Gdx.gl.glEnable(GL30.GL_BLEND);
		Gdx.gl.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA);

		shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
		drawDetectionsInner()
		shapeRenderer.end()

		Gdx.gl.glDisable(GL30.GL_BLEND);

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
		drawDetectionsInner()
		shapeRenderer.end()

		if (debugPassiveSensors) {
			shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
			shapeRenderer.color = sRGBtoLinearRGB(Color.PINK)

			entityIDs.forEachFast { entityID ->
				val detection = detectionMapper.get(entityID)
				
				if (detection != null) {

					for (sensorEntry in detection.detections.entries) {
						for (angleEntry in sensorEntry.value.entries) {
							for (distanceEntry in angleEntry.value) {
								for (hitPosition in distanceEntry.value.hitPositions) {

									val x = ((hitPosition.x.sign * 500 + hitPosition.x) / 1000L - cameraOffset.x).toFloat()
									val y = ((hitPosition.y.sign * 500 + hitPosition.y) / 1000L - cameraOffset.y).toFloat()

									val radius = 10 + 3 * scale
									val segments = getCircleSegments(radius, scale)
									shapeRenderer.circle(x, y, radius, segments)
								}
							}
						}
					}
				}
			}

			shapeRenderer.end()
		}
	}

	private fun drawSelectionDetectionZones(selectedEntityIDs: List<Int>) {

		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)

		selectedEntityIDs.forEachFast { entityID ->
			val sensorsComponent = sensorsMapper.get(entityID)
			
			if (sensorsComponent != null) {
				
				val movement = movementMapper.get(entityID).get(galaxy.time).value
				val x = (movement.getXinKM() - cameraOffset.x).toDouble()
				val y = (movement.getYinKM() - cameraOffset.y).toDouble()
	
				sensorsComponent.sensors.forEachFast { sensor ->
	
					when (sensor.part.spectrum) {
	
						Spectrum.Thermal -> {
							shapeRenderer.color = sRGBtoLinearRGB(Color.CORAL)
							shapeRenderer.color.a = 0.1f
						}
	
						Spectrum.Electromagnetic -> {
							shapeRenderer.color = sRGBtoLinearRGB(Color.VIOLET)
							shapeRenderer.color.a = 0.1f
						}
	
						else -> {
							shapeRenderer.color = sRGBtoLinearRGB(Color.WHITE)
							shapeRenderer.color.a = 0.1f
						}
					}
	
					val arcWidth = 360.0 / sensor.part.arcSegments
					val minRadius = sensor.part.distanceResolution
					val maxRadius = minRadius + sensor.part.distanceResolution
					val segments = FastMath.min(100, FastMath.max(3, getCircleSegments(maxRadius.toFloat(), scale) / 4))
	
					var i = 0;
					while (i < sensor.part.arcSegments) {
	
						val arcAngle = i * arcWidth + sensor.part.angleOffset
	
						shapeRenderer.scanCircleSector(x, y, maxRadius, minRadius, arcAngle, arcWidth, segments)
						shapeRenderer.flush()
						i++
					}
				}
			}
		}

		shapeRenderer.end()
	}

	private fun drawSelectionDetectionStrength(selectedEntityIDs: List<Int>) {

		val screenPosition = temp3F

		val font = Assets.fontMap
		font.color = sRGBtoLinearRGB(Color.WHITE)

		selectedEntityIDs.filter { detectionMapper.has(it) }.forEach {

			var textRow = 0

			val movementValues = movementMapper.get(it).get(galaxy.time).value
			val sensorX = (movementValues.getXinKM() - cameraOffset.x).toDouble()
			val sensorY = (movementValues.getYinKM() - cameraOffset.y).toDouble()

			val detection = detectionMapper.get(it)

			for (sensorEntry in detection.detections.entries) {

				val sensor = sensorEntry.key
				val arcWidth = 360.0 / sensor.part.arcSegments

				val angleSteps = sensorEntry.value

				for (angleEntry in angleSteps.entries) {

					val angleStep = angleEntry.key
					val angle = sensor.part.angleOffset + angleStep * arcWidth + 0.5 * arcWidth

					for (distanceEntry in angleEntry.value) {

						val distanceStep = distanceEntry.key
						val hit = distanceEntry.value

						val minRadius = distanceStep * sensor.part.distanceResolution
						val maxRadius = minRadius + sensor.part.distanceResolution
						val radius = (minRadius + maxRadius) / 2

						val text = "${sensor.part.spectrum} ${String.format("%.2e", hit.signalStrength)} - ${sensor.part.name}"

						val angleRad = FastMath.toRadians(angle)
						val x = (sensorX + radius * FastMath.cos(angleRad)).toFloat()
						val y = (sensorY + radius * FastMath.sin(angleRad)).toFloat()

						screenPosition.set(x, y, 0f)
						viewport.camera.project(screenPosition)

						font.color = sRGBtoLinearRGB(Color.GREEN)
						font.draw(spriteBatch, text, screenPosition.x - text.length * font.spaceXadvance * .5f, screenPosition.y - textRow * font.lineHeight)
					}
				}

				textRow++
			}
		}
	}

	private fun drawNames(entityIDs: IntBag) {

		val screenPosition = temp3F

		val font = Assets.fontMap
		font.color = sRGBtoLinearRGB(Color.WHITE)

		entityIDs.forEachFast { entityID ->
			if (nameMapper.has(entityID)) {
				val movement = movementMapper.get(entityID).get(galaxy.time).value
				val name = nameMapper.get(entityID).name

				var radius = 0f

				if (inStrategicView(entityID)) {

					radius = scale * STRATEGIC_ICON_SIZE / 2

				} else if (circleMapper.has(entityID)) {

					val circleComponent = circleMapper.get(entityID)
					radius = circleComponent.radius / 1000
				}

				val x = (movement.getXinKM() - cameraOffset.x).toFloat()
				val y = (movement.getYinKM() - cameraOffset.y).toFloat()

				screenPosition.set(x, y - radius * 1.2f, 0f)
				viewport.camera.project(screenPosition)

				font.draw(spriteBatch, name, screenPosition.x - name.length * font.spaceXadvance * .5f, screenPosition.y - 0.5f * font.lineHeight)
			}
		}
	}

	private fun drawMovementTimes(entityIDs: IntBag, selectedEntityIDs: List<Int>) {

		val screenPosition = temp3F

		val font = Assets.fontMap
		font.color = sRGBtoLinearRGB(Color.WHITE)

		entityIDs.forEachFast { entityID ->
			if (movementMapper.has(entityID)) {

				val movement = movementMapper.get(entityID)

				var radius = 0f

				if (inStrategicView(entityID)) {

					radius = scale * STRATEGIC_ICON_SIZE / 2

				} else if (circleMapper.has(entityID)) {

					val circleComponent = circleMapper.get(entityID)
					radius = circleComponent.radius / 1000
				}

				if (movement.next != null && movement.previous.time != galaxy.time) {

					if (selectedEntityIDs.contains(entityID)) {
						val text = "${Units.daysToDate((movement.previous.time / (24L * 60L * 60L)).toInt())} ${Units.secondsToString(movement.previous.time)}"
						val movementValues = movement.previous.value
						val x = (movementValues.getXinKM() - cameraOffset.x).toFloat()
						val y = (movementValues.getYinKM() - cameraOffset.y).toFloat()

						screenPosition.set(x, y - radius * 1.2f, 0f)
						viewport.camera.project(screenPosition)

						font.color = sRGBtoLinearRGB(Color.GREEN)
						font.draw(spriteBatch, text, screenPosition.x - text.length * font.spaceXadvance * .5f, screenPosition.y - 1.5f * font.lineHeight)
					}

					run {
						val text = "${Units.daysToDate((movement.next!!.time / (24L * 60L * 60L)).toInt())} ${Units.secondsToString(movement.next!!.time)}"
						val movementValues = movement.next!!.value
						val x = (movementValues.getXinKM() - cameraOffset.x).toFloat()
						val y = (movementValues.getYinKM() - cameraOffset.y).toFloat()

						screenPosition.set(x, y - radius * 1.2f, 0f)
						viewport.camera.project(screenPosition)

						font.color = sRGBtoLinearRGB(Color.RED)
						font.draw(spriteBatch, text, screenPosition.x - text.length * font.spaceXadvance * .5f, screenPosition.y - 1.5f * font.lineHeight)
					}
				}
			}
		}
	}
	
	fun renderOrbits(cameraOffset: Vector2L) {
		val shapeRenderer = AuroraGame.currentWindow.shapeRenderer
		val orbitsCache = starSystem.world.getSystem(OrbitSystem::class.java).orbitsCache
		
		shapeRenderer.color = Color.GRAY
		shapeRenderer.begin(ShapeRenderer.ShapeType.Point);
		orbitSubscription.getEntities().forEachFast { entityID ->
			val orbit = orbitMapper.get(entityID)
			val orbitCache: OrbitSystem.OrbitCache = orbitsCache.get(entityID)!!
			val parentEntity = orbit.parent
			val parentMovement = movementMapper.get(parentEntity).get(galaxy.time).value
			val x = (parentMovement.getXinKM() - cameraOffset.x).toDouble()
			val y = (parentMovement.getYinKM() - cameraOffset.y).toDouble()
			
			for (point in orbitCache.orbitPoints) {
				shapeRenderer.point((x + point.x).toFloat(), (y + point.y).toFloat(), 0f);
			}
		}
		shapeRenderer.end();
	}

	fun render(viewport: Viewport, cameraOffset: Vector2L) {

		this.viewport = viewport
		this.cameraOffset = cameraOffset
		shapeRenderer = AuroraGame.currentWindow.shapeRenderer
		spriteBatch = AuroraGame.currentWindow.spriteBatch
		uiCamera = AuroraGame.currentWindow.screenService.uiCamera
		
		val entityIDs = subscription.getEntities()
		val selectedEntityIDs = galaxyGroupSystem.get(GroupSystem.SELECTED).filter { it.system == starSystem && world.entityManager.isActive(it.entityID) && familyAspect.isInterested(it.entityID) }.map { it.entityID }

		viewport.apply()
		scale = (viewport.camera as OrthographicCamera).zoom
		displaySize = FastMath.hypot(viewport.getScreenWidth().toDouble(), viewport.getScreenHeight().toDouble()).toInt()
		shapeRenderer.projectionMatrix = viewport.camera.combined
		
//		gravSystem.render(viewport, cameraOffset)
		
		//TODO dont interpolate new positions if timeDiff * velocity is not noticable at current zoom level 
		
		drawDetections(entityIDs)

		if (Gdx.input.isKeyPressed(Input.Keys.C)) {
			drawSelectionDetectionZones(selectedEntityIDs)
		}
		
		if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
			drawOrders()
		}

		renderOrbits(cameraOffset)

		drawWeaponRanges(entityIDs, selectedEntityIDs)
		drawEntities(entityIDs)
		drawEntityCenters(entityIDs)
		drawProjectiles()
		drawTimedMovement(entityIDs, selectedEntityIDs)

		drawSelections(selectedEntityIDs)
		drawSelectionMoveTargets(selectedEntityIDs)
		//TODO draw selection weapon ranges
		drawAttackTargets(selectedEntityIDs)

		spriteBatch.projectionMatrix = viewport.camera.combined

		drawStrategicEntities(entityIDs)

		spriteBatch.projectionMatrix = uiCamera.combined
		spriteBatch.begin()

		drawSelectionDetectionStrength(selectedEntityIDs)
		drawNames(entityIDs)
		drawMovementTimes(entityIDs, selectedEntityIDs)

		spriteBatch.end()
	}

//	var lastBump = 0L
//	fun waterBump(mouseInGameCoordinates: Vector2L) {
//		val now = System.currentTimeMillis()
//		if (now - lastBump > 100) {
//			lastBump = now
//
//			val x = GravimetricSensorSystem.WATER_SIZE / 2 + (mouseInGameCoordinates.x / 1000L) / GravimetricSensorSystem.H_SQUARE_SIZE_KM
//			val y = GravimetricSensorSystem.WATER_SIZE / 2 + (mouseInGameCoordinates.y / 1000L) / GravimetricSensorSystem.H_SQUARE_SIZE_KM
//
//			if (x > 0 && x < GravimetricSensorSystem.WATER_SIZE-1 && y > 0 && y < GravimetricSensorSystem.WATER_SIZE-1 ){
//				//TODO don't add water, only move
//				gravSystem.waveHeight[x.toInt() * GravimetricSensorSystem.WATER_SIZE + y.toInt()] += 10f
//			}
//		}
//	}

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

fun getCircleSegments(radius: Float, scale: Float): Int {
	return FastMath.min(1000, FastMath.max(3, (10 * FastMath.cbrt((radius / scale).toDouble())).toInt()))
}

fun getXinKM(position: Vector2L): Long {
	return (500 + position.x) / 1000L
}

fun getYinKM(position: Vector2L): Long {
	return (500 + position.y) / 1000L
}
