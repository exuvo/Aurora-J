package se.exuvo.aurora.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.utils.viewport.Viewport
import se.exuvo.aurora.Assets
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.starsystems.StarSystem
import se.exuvo.aurora.starsystems.StarSystemGeneration
import se.exuvo.aurora.starsystems.components.ApproachType
import se.exuvo.aurora.starsystems.components.CircleComponent
import se.exuvo.aurora.starsystems.components.RenderComponent
import se.exuvo.aurora.starsystems.components.TimedMovementComponent
import se.exuvo.aurora.starsystems.systems.GroupSystem
import se.exuvo.aurora.starsystems.systems.MovementSystem
import se.exuvo.aurora.starsystems.systems.RenderSystem
import se.exuvo.aurora.utils.*
import se.exuvo.aurora.ui.keys.KeyActions_StarSystemScreen
import se.exuvo.aurora.ui.keys.KeyMappings
import se.exuvo.settings.Settings
import com.artemis.Aspect
import se.exuvo.aurora.galactic.Player
import se.exuvo.aurora.AuroraGame
import com.artemis.utils.Bag
import com.artemis.utils.IntBag
import se.exuvo.aurora.starsystems.components.EntityReference
import se.exuvo.aurora.empires.components.IdleTargetingComputersComponent
import se.exuvo.aurora.empires.components.ActiveTargetingComputersComponent
import se.exuvo.aurora.galactic.EntitiesMoveToEntityCommand
import se.exuvo.aurora.galactic.EntitiesMoveToPositionCommand
import se.exuvo.aurora.galactic.EntityClearTargetCommand
import se.exuvo.aurora.galactic.EntityMoveToPositionCommand
import se.exuvo.aurora.galactic.EntityMoveToEntityCommand
import se.exuvo.aurora.galactic.EntitySetTargetCommand
import se.exuvo.aurora.starsystems.systems.SpatialPartitioningPlanetoidsSystem
import se.exuvo.aurora.starsystems.systems.SpatialPartitioningSystem
import kotlin.concurrent.withLock

class StarSystemScreen(val system: StarSystem) : GameScreenImpl(), InputProcessor {
	companion object {
		@JvmField val WEAPON_FAMILY = Aspect.one(IdleTargetingComputersComponent::class.java, ActiveTargetingComputersComponent::class.java)
		@JvmField val DIRECT_SELECTION_FAMILY = Aspect.all(TimedMovementComponent::class.java, RenderComponent::class.java, CircleComponent::class.java)
		@JvmField val INDIRECT_SELECTION_FAMILY = Aspect.all(TimedMovementComponent::class.java, RenderComponent::class.java)
	}

	private val galaxy by lazy (LazyThreadSafetyMode.NONE) { GameServices[Galaxy::class] }
	private val galaxyGroupSystem by lazy (LazyThreadSafetyMode.NONE) { GameServices[GroupSystem::class] }
	
	private val uiScreen by lazy (LazyThreadSafetyMode.NONE) { AuroraGame.currentWindow.screenService[UIScreen::class] }

	private var viewport: Viewport = ScreenViewport()
	private var camera = viewport.camera as OrthographicCamera
	private val cameraOffset = Vector2L()

	val allSubscription = system.world.getAspectSubscriptionManager().get(Aspect.all())

	var zoomLevel = 0
	var zoomSensitivity = Settings.getFloat("UI/zoomSensitivity", 1.25f).toDouble()
	val maxZoom = 1E8f

	init {
		camera.zoom = 1E6f;
		zoomLevel = (Math.log(camera.zoom.toDouble()) / Math.log(zoomSensitivity)).toInt();
	}

	override fun resize(width: Int, height: Int) {
		viewport.update(width, height)
	}

	override fun update(deltaRealTime: Float) {

		if (Gdx.input.isKeyPressed(Input.Keys.ESCAPE)) {
			Gdx.app.exit()
		}

		var hDirection = 0f
		var vDirection = 0f

		if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
			hDirection--
		}

		if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
			hDirection++
		}

		if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
			vDirection--
		}

		if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
			vDirection++
		}

		if (hDirection != 0f || vDirection != 0f) {
			cameraOffset.add((camera.zoom * hDirection).toLong(), (camera.zoom * vDirection).toLong())
		}
		
		if (commandMenuPotentialStart && (System.currentTimeMillis() - commandMenuPotentialStartTime > 200 || commandMenuPotentialStartPos.dst(Gdx.input.getX().toLong(), Gdx.input.getY().toLong()) > 50)) {
			commandMenuPotentialStart = false
			
			uiScreen.openCommandMenu()
		}
	}

	override fun draw() {
		val shapeRenderer = AuroraGame.currentWindow.shapeRenderer
		val uiCamera = AuroraGame.currentWindow.screenService.uiCamera

		galaxy.shadowLock.withLock {
			val renderSystem = system.shadow.world.getSystem(RenderSystem::class.java)
			renderSystem.render(viewport, cameraOffset)
			
//			if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) {
//				val mouseInGameCoordinates = toWorldCordinates(getMouseInScreenCordinates(Gdx.input.x, Gdx.input.y))
//				renderSystem.waterBump(mouseInGameCoordinates)
//			}
		}

		if (dragSelect) {
			shapeRenderer.projectionMatrix = uiCamera.combined
			shapeRenderer.color = Color.WHITE
			shapeRenderer.begin(ShapeRenderer.ShapeType.Line)

			val dragSelection = getDragSelection(false)
			shapeRenderer.rect(dragSelection.x.toFloat(), (dragSelection.y).toFloat(), dragSelection.width.toFloat(), dragSelection.height.toFloat())

			shapeRenderer.end()
		}

		drawUI()
	}

	var lastTickrateUpdate = System.currentTimeMillis()
	var oldGalaxyTime = galaxy.time
	var galaxyTickrate = 0L
	
	private fun drawUI() {
		val now = System.currentTimeMillis()
		
		if (now - lastTickrateUpdate > 1000) {
			lastTickrateUpdate = now
			galaxyTickrate = galaxy.time - oldGalaxyTime
			oldGalaxyTime = galaxy.time
		}
		
		val spriteBatch = AuroraGame.currentWindow.spriteBatch
		val uiCamera = AuroraGame.currentWindow.screenService.uiCamera
		
		spriteBatch.projectionMatrix = uiCamera.combined
		spriteBatch.begin()
		
		val y = 28f
		var x = 4f
		x += Assets.fontUI.draw(spriteBatch, "${Units.daysToDate(galaxy.day)} ${Units.secondsToString(galaxy.time)}, ", x, y).width
		
		if (galaxy.speed == 0L) {
			Assets.fontUI.color = Color.RED
			x += Assets.fontUI.draw(spriteBatch, "System Error", x, y).width
			Assets.fontUI.color = Color.WHITE
			
		} else if (galaxy.speed < 0L) {
			Assets.fontUI.color = Color.GRAY
			x += Assets.fontUI.draw(spriteBatch, "speed ${Units.NANO_SECOND / -galaxy.speed}", x, y).width
			Assets.fontUI.color = Color.WHITE
			
		} else if (galaxy.speedLimited) {
			Assets.fontUI.color = Color.RED
			x += Assets.fontUI.draw(spriteBatch, "speed ${Units.NANO_SECOND / galaxy.speed}", x, y).width
			Assets.fontUI.color = Color.WHITE
			
		}  else {
			x += Assets.fontUI.draw(spriteBatch, "speed ${Units.NANO_SECOND / galaxy.speed}", x, y).width
		}
		
		x += Assets.fontUI.draw(spriteBatch, " ${galaxy.tickSize}", x, y).width
		x += Assets.fontUI.draw(spriteBatch, " ${system.updateTimeAverage.toInt() / 1000}us ${galaxyTickrate}t/s", x, y).width
		x += Assets.fontUI.draw(spriteBatch, ", ${allSubscription.getEntityCount()}st", x, y).width
		
		var str = "zoom $zoomLevel"
		Assets.fontUI.cache.clear()
		val strWidth = Assets.fontUI.cache.addText(str, 0f, 0f) .width
		Assets.fontUI.cache.clear()
		Assets.fontUI.draw(spriteBatch, str, Gdx.graphics.width - strWidth - 4f, y)
		
		spriteBatch.end()
	}

	fun keyAction(action: KeyActions_StarSystemScreen): Boolean {

		if (action == KeyActions_StarSystemScreen.GENERATE_SYSTEM) {

			StarSystemGeneration(system).generateRandomSystem()

		} else if (action == KeyActions_StarSystemScreen.SPEED_UP) {
			
			Player.current.increaseSpeed()
			return true

		} else if (action == KeyActions_StarSystemScreen.SPEED_DOWN) {

			Player.current.decreaseSpeed()
			return true

		} else if (action == KeyActions_StarSystemScreen.PAUSE) {

			Player.current.pauseSpeed()
			return true

		} else if (action == KeyActions_StarSystemScreen.MAP) {

			AuroraGame.currentWindow.screenService.add(GalaxyScreen(this))

		} else if (action == KeyActions_StarSystemScreen.ATTACK) {

			if (Player.current.selection.isNotEmpty()) {
				selectedAction = KeyActions_StarSystemScreen.ATTACK
				println("Selected action " + action)
			} else {
				println("Unable to select action " + action + ", no selection")
			}
		}

		return false
	}

	override fun keyDown(keycode: Int): Boolean {

		val action = KeyMappings.getRaw(keycode, StarSystemScreen::class)

		if (action != null) {
			return keyAction(action as KeyActions_StarSystemScreen)
		}

		return false;
	}

	override fun keyUp(keycode: Int): Boolean {
		return false;
	}

	override fun keyTyped(character: Char): Boolean {

		val action = KeyMappings.getTranslated(character, StarSystemScreen::class)

		if (action != null) {
			return keyAction(action as KeyActions_StarSystemScreen)
		}

		return false;
	}

	var moveWindow = false
	var dragSelectPotentialStart = false
	var dragSelect = false
	
	var commandMenuPotentialStart = false
	var commandMenuPotentialStartTime: Long = 0
	var commandMenuPotentialStartPos = Vector2L()
	
	var selectedAction: KeyActions_StarSystemScreen? = null
	
	var dragX = 0
	var dragY = 0
	
	//TODO selection priorities, ships > planets > missiles
	
	override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {

//		imGuiScreen.closeCommandMenu()
		
		if (!moveWindow && !dragSelect) {

			when (button) {
				Input.Buttons.LEFT -> {
					
					commandMenuPotentialStart = false

					galaxy.shadowLock.withLock {
						
						val directSelectionSubscription = system.shadow.world.getAspectSubscriptionManager().get(DIRECT_SELECTION_FAMILY)
						val weaponFamilyAspect = system.shadow.world.getAspectSubscriptionManager().get(WEAPON_FAMILY).aspect
						val renderSystem = system.shadow.world.getSystem(RenderSystem::class.java)
						
						val shadow = system.shadow
						val mouseInGameCoordinates = toWorldCordinates(getMouseInScreenCordinates(screenX, screenY))
						val entitiesUnderMouse = Bag<EntityReference>()
						val testCircle = CircleL()
						val zoom = camera.zoom
//						val entityIDs = directSelectionSubscription.entities
						val entityIDs = SpatialPartitioningSystem.query(system.shadow.quadtreeShips,
								mouseInGameCoordinates.x - 1, mouseInGameCoordinates.y - 1,
								mouseInGameCoordinates.x + 1, mouseInGameCoordinates.y + 1)
						
						entityIDs.addAll(SpatialPartitioningPlanetoidsSystem.query(system.shadow.quadtreePlanetoids,
								mouseInGameCoordinates.x - 1, mouseInGameCoordinates.y - 1,
								mouseInGameCoordinates.x + 1, mouseInGameCoordinates.y + 1))
						
//						println("entityIDs $entityIDs")
						
						// Exact check first
						entityIDs.forEachFast { entityID ->
							if (directSelectionSubscription.aspect.isInterested(entityID)) {
								val position = shadow.movementMapper.get(entityID).get(galaxy.time).value.position
								val radius: Float
	
								if (renderSystem.inStrategicView(entityID, zoom)) {
	
									radius = 1000 * zoom * RenderSystem.STRATEGIC_ICON_SIZE / 2
	
								} else {
	
									radius = shadow.circleMapper.get(entityID).radius
								}
	
								testCircle.set(position, radius)
	
								if (testCircle.contains(mouseInGameCoordinates)) {
									entitiesUnderMouse.add(system.getEntityReference(entityID))
								}
							}
						}

						// Lenient check if empty
						if (entitiesUnderMouse.isEmpty()) {
							entityIDs.forEachFast { entityID ->
								if (directSelectionSubscription.aspect.isInterested(entityID)) {
									val position = shadow.movementMapper.get(entityID).get(galaxy.time).value.position
									var radius: Float = 1000 * 2 * camera.zoom
	
									if (renderSystem.inStrategicView(entityID, zoom)) {
	
										radius += 1000 * zoom * RenderSystem.STRATEGIC_ICON_SIZE / 2
	
									} else {
	
										radius += shadow.circleMapper.get(entityID).radius
									}
									testCircle.set(position, radius)
	
									if (testCircle.contains(mouseInGameCoordinates)) {
										entitiesUnderMouse.add(system.getEntityReference(entityID))
									}
								}
							}

							if (entitiesUnderMouse.isNotEmpty()) {
//								println("lenient selected ${entitiesUnderMouse.size()} entities")
							}

						} else {
//							println("strict selected ${entitiesUnderMouse.size()} entities")
						}

						if (selectedAction == null) {

							if (entitiesUnderMouse.isNotEmpty()) {

								dragSelectPotentialStart = false;

								if (Player.current.selection.isNotEmpty() && !Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
									Player.current.selection.clear()
//								println("cleared selection")
								}

								Player.current.selection.addAll(entitiesUnderMouse)
								
							} else {

								dragSelectPotentialStart = true;
								dragX = screenX;
								dragY = screenY;

//						println("drag select potential dragX $dragX, dragY $dragY")
							}

						} else if (selectedAction == KeyActions_StarSystemScreen.ATTACK) {
							selectedAction = null

							if (Player.current.selection.isNotEmpty()) {
								val selectedEntities = Player.current.selection.filter { entityRef ->
									system == entityRef.system && weaponFamilyAspect.isInterested(entityRef.entityID)
								}
								
								if (selectedEntities.isEmpty()) {
									println("No combat capable ship selected")
									
								} else {
	
									var targetRef: EntityReference? = null
									
									if (entitiesUnderMouse.isNotEmpty()) {
										targetRef = entitiesUnderMouse[0]
										println("Attacking ${printEntity(targetRef.entityID, targetRef.system.world)}")
										
									} else {
										println("Clearing attack target")
									}
									
									selectedEntities.forEachFast{ entityRef ->
										val activeTCs = shadow.activeTargetingComputersComponentMapper.get(entityRef.entityID)
										
										activeTCs?.targetingComputers?.forEachFast{ tc ->
											Player.current.empire!!.commandQueue.add(EntityClearTargetCommand(entityRef, tc))
											
											if (targetRef != null) {
												Player.current.empire!!.commandQueue.add(EntitySetTargetCommand(entityRef, tc, targetRef))
											}
										}
										
										if (targetRef != null) {
											val idleTCs = shadow.idleTargetingComputersComponentMapper.get(entityRef.entityID)
											
											idleTCs?.targetingComputers?.forEachFast{ tc ->
												Player.current.empire!!.commandQueue.add(EntitySetTargetCommand(entityRef, tc, targetRef))
											}
										}
									}
								}
							}
						}

						return true;
					}
				}
				Input.Buttons.MIDDLE -> {
					selectedAction = null
					commandMenuPotentialStart = false
					moveWindow = true
					dragX = screenX
					dragY = screenY
					return true
				}
				Input.Buttons.RIGHT -> {
					selectedAction = null
					dragSelectPotentialStart = false

					commandMenuPotentialStart = true
					commandMenuPotentialStartTime = System.currentTimeMillis()
					commandMenuPotentialStartPos.set(screenX.toLong(), screenY.toLong())
				}
			}

		} else {

			if (dragSelect && button != Input.Buttons.LEFT) {
				dragSelect = false;
				return true
			}

			if (moveWindow && button != Input.Buttons.MIDDLE) {
				moveWindow = false;
				return true
			}
		}

		return false;
	}

	override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {

		if (moveWindow && button == Input.Buttons.MIDDLE) {
			moveWindow = false;
			return true
		}

		if (button == Input.Buttons.LEFT) {
			
			if (dragSelect) {
				if (Player.current.selection.isNotEmpty() && !Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
					Player.current.selection.clear()
	//				println("cleared selection")
				}
	
				val dragSelection = getDragSelection()
	//			println("dragSelection $dragSelection")
	
				val p1GameCoordinates = toWorldCordinates(Vector3(dragSelection.x.toFloat(), (viewport.screenHeight - dragSelection.y).toFloat(), 0f))
				val p2GameCoordinates = toWorldCordinates(Vector3((dragSelection.x + dragSelection.width).toFloat(), (viewport.screenHeight - (dragSelection.y + dragSelection.height)).toFloat(), 0f))
	//			println("p1GameCoordinates $p1GameCoordinates, p2GameCoordinates $p2GameCoordinates")
	
				val entitiesInSelection = Bag<EntityReference>()
				
				galaxy.shadowLock.withLock {
					val indirectSelectionSubscription = system.shadow.world.getAspectSubscriptionManager().get(INDIRECT_SELECTION_FAMILY)
					
					val entityIDs = SpatialPartitioningSystem.query(system.shadow.quadtreeShips, p1GameCoordinates, p2GameCoordinates);
					
//					val entityIDs = indirectSelectionSubscription.entities
					val testRectangle = RectangleL(p1GameCoordinates.x, p1GameCoordinates.y, p2GameCoordinates.x - p1GameCoordinates.x, p2GameCoordinates.y - p1GameCoordinates.y)
//					println("testRectangle $testRectangle, entityIDs $entityIDs")
					
					entityIDs.forEachFast { entityID ->
						if (indirectSelectionSubscription.aspect.isInterested(entityID)) {
							val position = system.shadow.movementMapper.get(entityID).get(galaxy.time).value.position
			
							if (testRectangle.contains(position)) {
								entitiesInSelection.add(system.shadow.getEntityReference(entityID))
							}
						}
					}
				}
	
				if (entitiesInSelection.isNotEmpty()) {
					Player.current.selection.addAll(entitiesInSelection)
	//				println("drag selected ${entitiesInSelection.size} entities")
				}
	
				dragSelect = false;
				return true
			}
	
			if (dragSelectPotentialStart) {
				if (Player.current.selection.isNotEmpty() && !Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
					Player.current.selection.clear()
	//				println("cleared selection")
				}
			}
		}
		
		if (button == Input.Buttons.RIGHT && commandMenuPotentialStart) {
			
			commandMenuPotentialStart = false
			
			if (Player.current.selection.isNotEmpty()) {

				galaxy.shadowLock.withLock {
					val movementFamilyAspect = system.shadow.world.getAspectSubscriptionManager().get(MovementSystem.CAN_ACCELERATE_FAMILY).aspect
					val directSelectionSubscription = system.shadow.world.getAspectSubscriptionManager().get(DIRECT_SELECTION_FAMILY)
					val renderSystem = system.shadow.world.getSystem(RenderSystem::class.java)
					
					val selectedEntities = Player.current.selection.filter { entityRef ->
						system == entityRef.system && movementFamilyAspect.isInterested(entityRef.entityID)
					}
	
					if (selectedEntities.isNotEmpty()) {
						
						val shadow = system.shadow
						val mouseInGameCoordinates = toWorldCordinates(getMouseInScreenCordinates(screenX, screenY))
						val entitiesUnderMouse = IntBag()
						val entityIDs = directSelectionSubscription.entities
						val testCircle = CircleL()
						val zoom = camera.zoom

						entityIDs.forEachFast { entityID ->
							val position = shadow.movementMapper.get(entityID).get(galaxy.time).value.position
							val radius: Float

							if (renderSystem.inStrategicView(entityID, zoom)) {

								radius = 1000 * zoom * RenderSystem.STRATEGIC_ICON_SIZE / 2

							} else {

								radius = shadow.circleMapper.get(entityID).radius
							}

							testCircle.set(position, radius)

							if (testCircle.contains(mouseInGameCoordinates)) {
								entitiesUnderMouse.add(entityID)
							}
						}

						if (!entitiesUnderMouse.isEmpty) {

//							println("Issuing move to entity order")

							val targetEntityID = entitiesUnderMouse.get(0)
							var approachType = ApproachType.BRACHISTOCHRONE

							if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)) {
								approachType = ApproachType.BALLISTIC
							}

//								println("Queuing move to entity command for $entityRef to $targetEntityID")
							Player.current.empire!!.commandQueue.add(EntitiesMoveToEntityCommand(Bag(selectedEntities), shadow.getEntityReference(targetEntityID), approachType))

						} else {

//							println("Issuing move to position order")

							val targetPosition = mouseInGameCoordinates
							var approachType = ApproachType.BRACHISTOCHRONE

							if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)) {
								approachType = ApproachType.BALLISTIC
							}

//								println("Queuing move to position command for $entityRef to $targetPosition")
							Player.current.empire!!.commandQueue.add(EntitiesMoveToPositionCommand(Bag(selectedEntities), targetPosition, approachType))
						}

						return true;
					}
				}
			}
		}

		return false;
	}

	override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {

		if (moveWindow) {
			var mouseScreenNow = Vector3(screenX.toFloat(), screenY.toFloat(), 0f)
			var mouseWorldNow = camera.unproject(mouseScreenNow.cpy())
			var mouseWorldBefore = camera.unproject(mouseScreenNow.cpy().add(Vector3((dragX - screenX).toFloat(), (dragY - screenY).toFloat(), 0f)))

			var diff = mouseWorldNow.cpy().sub(mouseWorldBefore)

			cameraOffset.sub(diff.x.toLong(), diff.y.toLong())

			//TODO ensure camera position is always inside the solar system

			dragX = screenX;
			dragY = screenY;

			return true;
		}

		if (dragSelectPotentialStart) {

			val dx = dragX - screenX
			val dy = dragY - screenY

			if (Math.sqrt((dx * dx + dy * dy).toDouble()) > 10) {

				dragSelectPotentialStart = false
				dragSelect = true
//				println("drag select start")
			}
		}

		return false;
	}

	override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
		return false;
	}

	// Converts from input coordinates to screen coordinates
	private fun getDragSelection(alwaysPositive: Boolean = true): RectangleL {

		var x = dragX
		var y = viewport.screenHeight - dragY
		var width = Gdx.input.getX(0) - dragX
		var height = dragY - Gdx.input.getY(0)

		if (alwaysPositive) {
			if (width < 0) {
				x += width
				width = -width
			}

			if (height < 0) {
				y += height
				height = -height
			}
		}

		return RectangleL(x.toLong(), y.toLong(), width.toLong(), height.toLong())
	}

	fun getMouseInScreenCordinates(): Vector3 {
		return getMouseInScreenCordinates(Gdx.input.getX(), Gdx.input.getY())
	}

	fun getMouseInScreenCordinates(screenX: Int, screenY: Int): Vector3 {
		return Vector3(screenX.toFloat(), screenY.toFloat(), 0f)
	}

	fun toWorldCordinates(screenCoordinates: Vector3): Vector2L {
		// unproject screen coordinates to corresponding world position
		val cameraRelativeWorldPosition = camera.unproject(screenCoordinates);
		val worldPosition = Vector2L(cameraRelativeWorldPosition.x.toLong(), cameraRelativeWorldPosition.y.toLong()).add(cameraOffset)
		worldPosition.scl(1000) // km to m
		return worldPosition
	}

	// -1 for zoom-in. 1 for zoom out
	override fun scrolled(amount: Int): Boolean {

		var oldZoom = camera.zoom

		zoomLevel += amount;
		if (zoomLevel < 0) {
			zoomLevel = 0
		}

		// camera.zoom >= 1
		camera.zoom = Math.pow(zoomSensitivity, zoomLevel.toDouble()).toFloat()

//		System.out.println("zoom:" + camera.zoom + "  zoomLevel:" + zoomLevel);

		if (camera.zoom > maxZoom) {
			camera.zoom = maxZoom;
			zoomLevel = (Math.log(camera.zoom.toDouble()) / Math.log(zoomSensitivity)).toInt();
		}

		if (amount < 0) {
			// Det som var under musen innan scroll ska fortsätta vara där efter zoom
			// http://stackoverflow.com/questions/932141/zooming-an-object-based-on-mouse-position

			var diff = camera.position.cpy().sub(camera.unproject(getMouseInScreenCordinates()));
			diff = diff.sub(diff.cpy().scl(1 / oldZoom).scl(camera.zoom))
			cameraOffset.sub(diff.x.toLong(), diff.y.toLong())

			//TODO ensure cameraOffset is always inside the solar system
		}

		camera.update();
		
		//TODO allow zooming out to galaxy level, make jump not noticeable

		return true;
	}

	override fun dispose() {
	}
}
