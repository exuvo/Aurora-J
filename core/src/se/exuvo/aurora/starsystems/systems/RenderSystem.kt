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
import se.exuvo.aurora.galactic.Player
import se.exuvo.aurora.galactic.Railgun
import se.exuvo.aurora.galactic.SimpleMunitionHull
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.starsystems.ShadowStarSystem
import se.exuvo.aurora.starsystems.StarSystem
import se.exuvo.aurora.starsystems.components.AmmunitionPartState
import se.exuvo.aurora.starsystems.components.ArmorComponent
import se.exuvo.aurora.starsystems.components.CargoComponent
import se.exuvo.aurora.starsystems.components.CircleComponent
import se.exuvo.aurora.starsystems.components.DetectionComponent
import se.exuvo.aurora.starsystems.components.EmpireComponent
import se.exuvo.aurora.starsystems.components.HPComponent
import se.exuvo.aurora.starsystems.components.LaserShotComponent
import se.exuvo.aurora.starsystems.components.MissileComponent
import se.exuvo.aurora.starsystems.components.MoveToEntityComponent
import se.exuvo.aurora.starsystems.components.MoveToPositionComponent
import se.exuvo.aurora.starsystems.components.NameComponent
import se.exuvo.aurora.starsystems.components.OrbitComponent
import se.exuvo.aurora.starsystems.components.PartStatesComponent
import se.exuvo.aurora.starsystems.components.PartsHPComponent
import se.exuvo.aurora.starsystems.components.PassiveSensorsComponent
import se.exuvo.aurora.starsystems.components.RailgunShotComponent
import se.exuvo.aurora.starsystems.components.RenderComponent
import se.exuvo.aurora.starsystems.components.ShieldComponent
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.components.Spectrum
import se.exuvo.aurora.starsystems.components.StrategicIconComponent
import se.exuvo.aurora.starsystems.components.TargetingComputerState
import se.exuvo.aurora.starsystems.components.ThrustComponent
import se.exuvo.aurora.starsystems.components.TimedMovementComponent
import se.exuvo.aurora.starsystems.components.TintComponent
import se.exuvo.aurora.ui.ProfilerWindow
import se.exuvo.aurora.ui.UIScreen
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.utils.Vector2D
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.utils.quadtree.QuadtreeAABB
import se.exuvo.aurora.utils.quadtree.QuadtreePoint
import se.exuvo.aurora.utils.quadtree.QuadtreeVisitor
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
		
		@JvmField var debugPassiveSensors = Settings.getBol("Systems/Render/debugPassiveSensors", false)
		@JvmField var debugDisableStrategicView = Settings.getBol("Systems/Render/debugDisableStrategicView", false)
		@JvmField var debugDrawWeaponRangesWithoutShader = Settings.getBol("Systems/Render/debugDrawWeaponRangesWithoutShader", false)
		@JvmField var debugSpatialPartitioning = Settings.getBol("Systems/Render/debugSpatialPartitioning", false)
		@JvmField var debugSpatialPartitioningPlanetoids = Settings.getBol("Systems/Render/debugSpatialPartitioningPlanetoids", false)
		@JvmField val log = LogManager.getLogger(RenderSystem::class.java)
		
		@JvmField val dummyProfilerEvents = ProfilerWindow.ProfilerBag()
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
	lateinit private var partStatesMapper: ComponentMapper<PartStatesComponent>
	lateinit private var shieldMapper: ComponentMapper<ShieldComponent>
	lateinit private var armorMapper: ComponentMapper<ArmorComponent>
	lateinit private var partsHPMapper: ComponentMapper<PartsHPComponent>
	lateinit private var hpMapper: ComponentMapper<HPComponent>
	lateinit private var cargoMapper: ComponentMapper<CargoComponent>
	lateinit private var empireMapper: ComponentMapper<EmpireComponent>

	private val galaxyGroupSystem = GameServices[GroupSystem::class]
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
	
	var profilerEvents = dummyProfilerEvents
	
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
		val diskShader: ShaderProgram = Assets.shaders["disk"]!!
		val circleShader: ShaderProgram = Assets.shaders["circle"]!!
		val circleIndices: ShortArray
		val circleVertices: FloatArray
		val circleMesh: Mesh
		val strategicIconIndices: ShortArray
		val strategicIconVertices: FloatArray
		val strategicIconMesh: Mesh
		val strategicIconShader: ShaderProgram = Assets.shaders["strategic"]!!
		val strategicIconTexture = Assets.textures.findRegion("strategic/colony").texture

		init {
			
			if (!circleShader.isCompiled || circleShader.getLog().length != 0) {
				log.error("Shader circleShader compile error ${circleShader.getLog()}")
				debugDrawWeaponRangesWithoutShader = true
			}
			
			if (!diskShader.isCompiled || diskShader.getLog().length != 0) {
				log.error("Shader diskShader compile error ${circleShader.getLog()}")
				debugDrawWeaponRangesWithoutShader = true
			}
			
			if (!strategicIconShader.isCompiled || strategicIconShader.getLog().length != 0) {
				log.error("Shader strategicIconShader compile error ${strategicIconShader.getLog()}")
			}
			
			val circleMax = 64
			val circleIndicesMax = 6 * circleMax
			val circleVerticesMax = 4 * circleMax
			
			circleVertices = FloatArray(circleVerticesMax)
			circleIndices = ShortArray(circleIndicesMax)
			
			circleMesh = Mesh(false, circleVerticesMax, circleIndicesMax,
					VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE)
			);
			
			val strategicIconMax = 64
			val strategicIconIndicesMax = 6 * strategicIconMax
			val strategicIconVerticesMax = 7 * strategicIconMax
			
			strategicIconVertices = FloatArray(strategicIconVerticesMax)
			strategicIconIndices = ShortArray(strategicIconIndicesMax)
			
			strategicIconMesh = Mesh(false, strategicIconVerticesMax, strategicIconIndicesMax,
					VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE),
					VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, GL20.GL_UNSIGNED_BYTE, true, ShaderProgram.COLOR_ATTRIBUTE),
					VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "Base"),
					VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "Center"),
			);
		}
		
		override fun dispose() {
			circleMesh.dispose()
			strategicIconMesh.dispose()
		}
	}
	
	class RenderWindowData(): Disposable, Resizable {

		var fbo: FrameBuffer

		init {
			fbo = FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), false)
			
		}
		
		override fun resize(width: Int, height: Int) {
			fbo.dispose()
			fbo = FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), false)
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
				val radius = maxOf(1f, circle.radius / 1000)
				if (radius < 1f) {
					shapeRenderer.point(x, y, 0f)
				} else {
					shapeRenderer.circle(x, y, radius, getCircleSegments(radius, scale))
				}
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
				val radius = circle.radius / 1000 * 0.01f
				if (radius < 1f) {
					shapeRenderer.point(x, y, 0f)
				} else {
					shapeRenderer.circle(x, y, radius, getCircleSegments(radius, scale))
				}
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
					val partStates = partStatesMapper.get(entityID)
					
					val x = (((movement.position.x.sign * 500 + movement.position.x) / 1000L) - cameraOffset.x).toFloat()
					val y = (((movement.position.y.sign * 500 + movement.position.y) / 1000L) - cameraOffset.y).toFloat()
					
					tcs.forEachFast{ tc ->
						val tcState = partStates[tc][TargetingComputerState::class]
						val tcRange = tc.part.maxRange * 1000
						
						tcState.linkedWeapons.forEachFast weaponLoop@{ weapon ->
							if (partStates.isPartEnabled(weapon)) {
								val part = weapon.part
								
								val timeToImpact = 60 // s
								var range: Long
								
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
										val ammoState = partStates[weapon][AmmunitionPartState::class]
										val munitionHull = ammoState.type as? SimpleMunitionHull
										
										if (munitionHull == null) {
											return@weaponLoop
										}
										
										val projectileSpeed = (part.capacitor * part.efficiency) / (100 * munitionHull.loadedMass)
										range = projectileSpeed * timeToImpact
										
										shapeRenderer.color = sRGBtoLinearRGB(Color.ORANGE)
									}
									is MissileLauncher -> {
										val ammoState = partStates[weapon][AmmunitionPartState::class]
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
								
								range = minOf(range, tcRange)
								
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
						val partStates = partStatesMapper.get(entityID)

						val x = (((movement.position.x.sign * 500 + movement.position.x) / 1000L) - cameraOffset.x).toFloat()
						val y = (((movement.position.y.sign * 500 + movement.position.y) / 1000L) - cameraOffset.y).toFloat()

						fun getRanges(tcs: List<PartRef<TargetingComputer>>) {
							tcs.forEachFast { tc ->
								val tcState = partStates[tc][TargetingComputerState::class]
								val tcRange = tc.part.maxRange * 1000

								tcState.linkedWeapons.forEachFast weaponLoop@{ weapon ->
									if (partStates.isPartEnabled(weapon)) {
										val part = weapon.part

										val timeToImpact = 60 // s
										var range: Long

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
												val ammoState = partStates[weapon][AmmunitionPartState::class]
												val munitionHull = ammoState.type as? SimpleMunitionHull
												
												if (munitionHull == null) {
													return@weaponLoop
												}
												
												val projectileSpeed = (part.capacitor * part.efficiency) / (100 * munitionHull.loadedMass)
												range = projectileSpeed * timeToImpact
											}
											is MissileLauncher -> {
												val ammoState = partStates[weapon][AmmunitionPartState::class]
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

										range = minOf(range, tcRange)
										
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

			val vertices = gData.circleVertices
			val indices = gData.circleIndices
			val cShader = gData.circleShader
			val dShader = gData.diskShader
			val mesh = gData.circleMesh
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
					
					val minX = FastMath.max(x - radius - padding, -viewport.getScreenWidth() / 2 * scale)
					val maxX = FastMath.min(x + radius + padding, viewport.getScreenWidth() / 2 * scale)
					val minY = FastMath.max(y - radius - padding, -viewport.getScreenHeight() / 2 * scale)
					val maxY = FastMath.min(y + radius + padding, viewport.getScreenHeight() / 2 * scale)
					
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
					
					//TODO do multiple circles at the same instead of individual calls
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
					
					val minX = FastMath.max(x - radius - padding, -viewport.getScreenWidth() / 2 * scale)
					val maxX = FastMath.min(x + radius + padding, viewport.getScreenWidth() / 2 * scale)
					val minY = FastMath.max(y - radius - padding, -viewport.getScreenHeight() / 2 * scale)
					val maxY = FastMath.min(y + radius + padding, viewport.getScreenHeight() / 2 * scale)
					
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

		if (debugDisableStrategicView || scale == 1f) {
			return false
		}

		val radius = circleMapper.get(entityID).radius / 1000
		return radius / scale < 5f
	}

	private fun drawStrategicEntities(entityIDs: IntBag) {
		
		val gData = gData()
		val texture = gData.strategicIconTexture
		
		spriteBatch.begin()

		entityIDs.forEachFast { entityID ->

			if (strategicIconMapper.has(entityID) && inStrategicView(entityID)) {

				val baseTexture = strategicIconMapper.get(entityID).baseTexture
				
				if (baseTexture.texture !== texture) {
				
					val movement = movementMapper.get(entityID).get(galaxy.time).value
					val tintComponent = if (tintMapper.has(entityID)) tintMapper.get(entityID) else null
					var x = (movement.getXinKM() - cameraOffset.x).toFloat()
					var y = (movement.getYinKM() - cameraOffset.y).toFloat()
	
					// https://github.com/libgdx/libgdx/wiki/Spritebatch%2C-Textureregions%2C-and-Sprites
					spriteBatch.color = sRGBtoLinearRGB(tintComponent?.color ?: Color.WHITE);
	
					val width = scale * STRATEGIC_ICON_SIZE
					val height = scale * STRATEGIC_ICON_SIZE
					x -= width / 2
					y -= height / 2
					
					if (thrustMapper.has(entityID)) {
	
						val thrustAngle = thrustMapper.get(entityID).thrustAngle
	
						val originX = width / 2
						val originY = height / 2
						val scale = 1f
	
						spriteBatch.draw(baseTexture, x, y, originX, originY, width, height, scale, scale, thrustAngle)
	
					} else {
	
						spriteBatch.draw(baseTexture, x, y, width, height)
					}
				}
			}
		}

		spriteBatch.end()
		
		val projectionMatrix = viewport.camera.combined

		val vertices = gData.strategicIconVertices
		val indices = gData.strategicIconIndices
		val iconShader = gData.strategicIconShader
		val mesh = gData.strategicIconMesh
		
		iconShader.bind()
		iconShader.setUniformMatrix("u_projTrans", projectionMatrix);
		iconShader.setUniformi("u_texture", 14);
		
		Gdx.gl.glActiveTexture(GL20.GL_TEXTURE14)
		texture.bind()
		Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
		
		Gdx.gl.glEnable(GL30.GL_BLEND);
		Gdx.gl.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA);
		
		var vertexIdx = 0
		var indiceIdx = 0
		var stride = 0
		
		// 7 15 3+1+3 7+1+7 7-3=4
		// Offset center texCoords by diff in texture sizes
		val centerXOffset = 4f / texture.width
		val centerYOffset = 4f / texture.height
		val halfWidth = 7.5f * scale
		
		fun vertex(x: Float, y: Float, colorBits: Float, baseU: Float, baseV: Float, centerU: Float, centerV: Float) {
			vertices[vertexIdx++] = x;
			vertices[vertexIdx++] = y;
			vertices[vertexIdx++] = colorBits
			vertices[vertexIdx++] = baseU;
			vertices[vertexIdx++] = baseV;
			vertices[vertexIdx++] = centerU;
			vertices[vertexIdx++] = centerV;
		}
		
		entityIDs.forEachFast loop@{ entityID ->
			
			val strategicIconC = strategicIconMapper.get(entityID)
			
			if (strategicIconC != null && inStrategicView(entityID)) {
				
				val baseTex = strategicIconC.baseTexture
				val centerTex = strategicIconC.centerTexture
				
				if (baseTex.texture === texture) {
				
					val movement = movementMapper.get(entityID).get(galaxy.time).value
					val x = (movement.getXinKM() - cameraOffset.x).toFloat()
					val y = (movement.getYinKM() - cameraOffset.y).toFloat()
					
					//TODO fix rounding errors
					// centerpoint correct with 7.5 but when ship is stopped size sometimes it becomes too large and fucks it
					// centerpoint rounds wrong with 8/7 but size is always correct
//					val minX = x - halfWidth
//					val maxX = x + halfWidth
//					val minY = y - halfWidth
//					val maxY = y + halfWidth
					val minX = x - 8f * scale
					val maxX = x + 7f * scale
					val minY = y - 8f * scale
					val maxY = y + 7f * scale
					
//					if (((maxX - minX) / scale).toInt() != 15 || (maxX / scale).toInt() - (minX / scale).toInt() != 15) {
//						println("entityID $entityID w ${((maxX - minX) / scale).toInt()} w2 ${(maxX / scale).toInt() - (minX / scale).toInt()}")
//					}
					
					val viewWidth = viewport.getScreenWidth() / 2 * scale
					val viewHeight = viewport.getScreenHeight() / 2 * scale
					
					if (maxX < -viewWidth || minX > viewWidth || maxY < -viewHeight || minY > viewHeight) {
						return@loop
					}
					
					val empireC = empireMapper.get(entityID)
					val color = sRGBtoLinearRGB(if (empireC != null) empireC.empire.color else tintMapper.get(entityID)?.color ?: Color.WHITE)
					val colorBits = color.toFloatBits()
					
					// Triangle 1
					indices[indiceIdx++] = (stride + 1).toShort()
					indices[indiceIdx++] = (stride + 0).toShort()
					indices[indiceIdx++] = (stride + 2).toShort()
					
					// Triangle 2
					indices[indiceIdx++] = (stride + 0).toShort()
					indices[indiceIdx++] = (stride + 3).toShort()
					indices[indiceIdx++] = (stride + 2).toShort()
					
					stride += 4
					
					if (centerTex != null) {
						vertex(minX, minY, colorBits, baseTex.u, baseTex.v2, centerTex.u - centerXOffset, centerTex.v2 + centerYOffset);
						vertex(maxX, minY, colorBits, baseTex.u2, baseTex.v2, centerTex.u2 + centerXOffset, centerTex.v2 + centerYOffset);
						vertex(maxX, maxY, colorBits, baseTex.u2, baseTex.v, centerTex.u2 + centerXOffset, centerTex.v - centerYOffset);
						vertex(minX, maxY, colorBits, baseTex.u, baseTex.v, centerTex.u - centerXOffset, centerTex.v - centerYOffset);
					} else {
						vertex(minX, minY, colorBits, baseTex.u, baseTex.v2, 1f, 1f);
						vertex(maxX, minY, colorBits, baseTex.u2, baseTex.v2, 1f, 1f);
						vertex(maxX, maxY, colorBits, baseTex.u2, baseTex.v, 1f, 1f);
						vertex(minX, maxY, colorBits, baseTex.u, baseTex.v, 1f, 1f);
					}
					
					if (indiceIdx >= mesh.maxIndices) {
						mesh.setVertices(vertices, 0, vertexIdx)
						mesh.setIndices(indices, 0, indiceIdx)
						mesh.render(iconShader, GL20.GL_TRIANGLES)

						vertexIdx = 0
						indiceIdx = 0
						stride = 0
					}
				}
			}
		}
		
		if (indiceIdx > 0) {
			mesh.setVertices(vertices, 0, vertexIdx)
			mesh.setIndices(indices, 0, indiceIdx)
			mesh.render(iconShader, GL20.GL_TRIANGLES)
		}
		
		Gdx.gl.glDisable(GL30.GL_BLEND);
		
		// debug center pixel
//		shapeRenderer.begin(ShapeRenderer.ShapeType.Point)
//		shapeRenderer.color = sRGBtoLinearRGB(Color.WHITE)
//		entityIDs.forEachFast { entityID ->
//
//			if (strategicIconMapper.has(entityID) && inStrategicView(entityID)) {
//
//				val baseTexture = strategicIconMapper.get(entityID).baseTexture
//
//				if (baseTexture.texture == texture) {
//
//					val movement = movementMapper.get(entityID).get(galaxy.time).value
//					var x = (movement.getXinKM() - cameraOffset.x).toFloat()
//					var y = (movement.getYinKM() - cameraOffset.y).toFloat()
//
//					shapeRenderer.point(x, y, 0f)
//				}
//			}
//		}
//		shapeRenderer.end()
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

				if (movement.previous.time != galaxy.time && movement.next != null && !orbitSubscription.aspect.isInterested(entityID)) {

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

				val partStates = partStatesMapper.get(entityID)

				usedTargets.clear()

				tcc.targetingComputers.forEachFast { tc ->
					val tcState = partStates[tc][TargetingComputerState::class]
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

				if (movement.next != null && movement.previous.time != galaxy.time && !orbitSubscription.aspect.isInterested(entityID)) {
					
					var radius = 0f
	
					if (inStrategicView(entityID)) {
	
						radius = scale * STRATEGIC_ICON_SIZE / 2
	
					} else if (circleMapper.has(entityID)) {
	
						val circleComponent = circleMapper.get(entityID)
						radius = circleComponent.radius / 1000
					}

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
			val orbitCache: OrbitSystem.OrbitCache = orbitsCache[entityID]!!
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
	
	private fun drawSpatialPartitioning() {
		
		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
		
		val tree: QuadtreePoint
		
		if (AuroraGame.currentWindow.screenService[UIScreen::class].shipDebugger.useShadow) {
			tree = shadowSystem.quadtreeShips
		} else {
			tree = shadowSystem.system.world.getSystem(SpatialPartitioningSystem::class.java).tree
		}
		
		val max2 = SpatialPartitioningSystem.MAX / 2
		val treeScale = SpatialPartitioningSystem.SCALE / 1000L
		
//		println()
		tree.traverse(object : QuadtreeVisitor {
			override fun leaf(node: Int, depth: Int, mx: Int, my: Int, sx: Int, sy: Int) {
//				println("leaf node $node, depth $depth, $mx $my $sx $sy")
				shapeRenderer.color = sRGBtoLinearRGB(Color.YELLOW)
				shapeRenderer.rect((treeScale * (mx - sx - max2) - cameraOffset.x).toFloat(),
				                   (treeScale * (my - sy - max2) - cameraOffset.y).toFloat(),
				                   (2 * treeScale * sx).toFloat(),
				                   (2 * treeScale * sy).toFloat())
				
				shapeRenderer.color = sRGBtoLinearRGB(Color.TEAL)
				val enodes = tree.enodes
				val elts = tree.elts
				
				var enodeIdx = tree.nodes.get(node, QuadtreePoint.node_idx_fc)
				
				while (enodeIdx != -1) {
					val elementIdx: Int = enodes.get(enodeIdx, QuadtreeAABB.enode_idx_elementIdx)
					enodeIdx = enodes.get(enodeIdx, QuadtreePoint.enode_idx_next)
					
					val entityID = elts.get(elementIdx, QuadtreePoint.elt_idx_id)
					val x = elts.get(elementIdx, QuadtreePoint.elt_idx_mx)
					val y = elts.get(elementIdx, QuadtreePoint.elt_idx_my)

//					println("entity $entityID $l $t $r $b")
					
					shapeRenderer.rect((treeScale * (x - max2) - cameraOffset.x - 1).toFloat(), (treeScale * (y - max2) - cameraOffset.y - 1).toFloat(),
					               2f, 2f)
				}
			}
			
			override fun branch(node: Int, depth: Int, mx: Int, my: Int, sx: Int, sy: Int) {
//				println("branch node $node, depth $depth, $mx $my $sx $sy")
//				shapeRenderer.color = sRGBtoLinearRGB(Color.TEAL)
//				shapeRenderer.rect((scale * (mx - sx - max2) - cameraOffset.x).toFloat(),
//				                   (scale * (my - sy - max2) - cameraOffset.y).toFloat(),
//				                   (2 * scale * sx).toFloat(),
//				                   (2 * scale * sy).toFloat())
			}
		})
		
		shapeRenderer.end()
	}
	
	private fun drawSpatialPartitioningPlanetoids() {
		
		shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
		
		val tree: QuadtreeAABB
		
		if (AuroraGame.currentWindow.screenService[UIScreen::class].shipDebugger.useShadow) {
			tree = shadowSystem.quadtreePlanetoids
		} else {
			tree = shadowSystem.system.world.getSystem(SpatialPartitioningPlanetoidsSystem::class.java).tree
		}
		
		val max2 = SpatialPartitioningPlanetoidsSystem.MAX / 2
		val treeScale = SpatialPartitioningPlanetoidsSystem.SCALE / 1000L

//		println()
		tree.traverse(object : QuadtreeVisitor {
			override fun leaf(node: Int, depth: Int, mx: Int, my: Int, sx: Int, sy: Int) {
//				println("leaf node $node, depth $depth, $mx $my $sx $sy")
				shapeRenderer.color = sRGBtoLinearRGB(Color.YELLOW)
				shapeRenderer.rect((treeScale * (mx - sx - max2) - cameraOffset.x).toFloat(),
				                   (treeScale * (my - sy - max2) - cameraOffset.y).toFloat(),
				                   (2 * treeScale * sx).toFloat(),
				                   (2 * treeScale * sy).toFloat())
				
				shapeRenderer.color = sRGBtoLinearRGB(Color.TEAL)
				val enodes = tree.enodes
				val elts = tree.elts
				
				var enodeIdx = tree.nodes.get(node, QuadtreeAABB.node_idx_fc)
				
				while (enodeIdx != -1) {
					val elementIdx: Int = enodes.get(enodeIdx, QuadtreeAABB.enode_idx_elementIdx)
					enodeIdx = enodes.get(enodeIdx, QuadtreeAABB.enode_idx_next)
					
					val entityID = elts.get(elementIdx, QuadtreeAABB.elt_idx_id)
					val l = elts.get(elementIdx, QuadtreeAABB.elt_idx_lft)
					val t = elts.get(elementIdx, QuadtreeAABB.elt_idx_top)
					val r = elts.get(elementIdx, QuadtreeAABB.elt_idx_rgt)
					val b = elts.get(elementIdx, QuadtreeAABB.elt_idx_btm)
					
//					println("entity $entityID $l $t $r $b")
					
					shapeRenderer.rect((treeScale * (l - max2) - cameraOffset.x).toFloat(),
														 (treeScale * (t - max2) - cameraOffset.y).toFloat(),
														 (treeScale * (r - l)).toFloat(),
														 (treeScale * (b - t)).toFloat())
				}
			}
			
			override fun branch(node: Int, depth: Int, mx: Int, my: Int, sx: Int, sy: Int) {
//				println("branch node $node, depth $depth, $mx $my $sx $sy")
//				shapeRenderer.color = sRGBtoLinearRGB(Color.TEAL)
//				shapeRenderer.rect((scale * (mx - sx - max2) - cameraOffset.x).toFloat(),
//						(scale * (my - sy - max2) - cameraOffset.y).toFloat(),
//						(2 * scale * sx).toFloat(),
//						(2 * scale * sy).toFloat())
			}
		})
		
		shapeRenderer.end()
	}
	
	fun render(viewport: Viewport, cameraOffset: Vector2L) {
		
		profilerEvents = if (galaxy.speed > 0) galaxy.renderProfilerEvents else dummyProfilerEvents
		profilerEvents.clear()
		profilerEvents.start("render")
		
		profilerEvents.start("setup")
		this.viewport = viewport
		this.cameraOffset = cameraOffset
		shapeRenderer = AuroraGame.currentWindow.shapeRenderer
		spriteBatch = AuroraGame.currentWindow.spriteBatch
		uiCamera = AuroraGame.currentWindow.screenService.uiCamera
		
		val entityIDs = subscription.getEntities()
		val selectedEntityIDs = Player.current.selection.filter { it.system == starSystem && world.entityManager.isActive(it.entityID) && familyAspect.isInterested(it.entityID) }.map { it.entityID }

		viewport.apply()
		scale = (viewport.camera as OrthographicCamera).zoom
		displaySize = FastMath.hypot(viewport.getScreenWidth().toDouble(), viewport.getScreenHeight().toDouble()).toInt()
		shapeRenderer.projectionMatrix = viewport.camera.combined
		profilerEvents.end()
		
//		gravSystem.render(viewport, cameraOffset)
		
		//TODO dont interpolate new positions if timeDiff * velocity is not noticable at current zoom level 
		
		profilerEvents.start("drawDetections")
		drawDetections(entityIDs)
		profilerEvents.end()

		if (Gdx.input.isKeyPressed(Input.Keys.C)) {
			profilerEvents.start("drawSelectionDetectionZones")
			drawSelectionDetectionZones(selectedEntityIDs)
			profilerEvents.end()
		}
		
		if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
			profilerEvents.start("drawOrders")
			drawOrders()
			profilerEvents.end()
		}
		
		profilerEvents.start("renderOrbits")
		renderOrbits(cameraOffset)
		profilerEvents.end()
		
		profilerEvents.start("drawWeaponRanges")
		drawWeaponRanges(entityIDs, selectedEntityIDs)
		profilerEvents.end()
		
		profilerEvents.start("drawEntities")
		drawEntities(entityIDs)
		profilerEvents.end()
		
		profilerEvents.start("drawEntityCenters")
		drawEntityCenters(entityIDs)
		profilerEvents.end()
		
		profilerEvents.start("drawProjectiles")
		drawProjectiles()
		profilerEvents.end()
		
		profilerEvents.start("drawTimedMovement")
		drawTimedMovement(entityIDs, selectedEntityIDs)
		profilerEvents.end()
		
		profilerEvents.start("drawSelections")
		drawSelections(selectedEntityIDs)
		profilerEvents.end()
		
		profilerEvents.start("drawSelectionMoveTargets")
		drawSelectionMoveTargets(selectedEntityIDs)
		profilerEvents.end()
		
		//TODO draw selection weapon ranges
		profilerEvents.start("drawAttackTargets")
		drawAttackTargets(selectedEntityIDs)
		profilerEvents.end()

		spriteBatch.projectionMatrix = viewport.camera.combined
		
		profilerEvents.start("drawStrategicEntities")
		drawStrategicEntities(entityIDs)
		profilerEvents.end()
		
		if (debugSpatialPartitioning) {
			profilerEvents.start("drawSpatialPartitioning")
			drawSpatialPartitioning()
			profilerEvents.end()
		}
		
		if (debugSpatialPartitioningPlanetoids) {
			profilerEvents.start("drawSpatialPartitioningPlanetoids")
			drawSpatialPartitioningPlanetoids()
			profilerEvents.end()
		}
		
		profilerEvents.start("spriteBatch")
		spriteBatch.projectionMatrix = uiCamera.combined
		spriteBatch.begin()
		
		profilerEvents.start("drawSelectionDetectionStrength")
		drawSelectionDetectionStrength(selectedEntityIDs)
		profilerEvents.end()
		
		profilerEvents.start("drawNames")
		drawNames(entityIDs)
		profilerEvents.end()
		
		profilerEvents.start("drawMovementTimes")
		drawMovementTimes(entityIDs, selectedEntityIDs)
		profilerEvents.end()

		spriteBatch.end()
		profilerEvents.end()
		
		profilerEvents.end()
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
