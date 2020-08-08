package se.exuvo.aurora.ui

import com.artemis.ComponentMapper
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
import se.exuvo.aurora.starsystems.components.StarSystemComponent
import se.exuvo.aurora.starsystems.components.RenderComponent
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.components.TargetingComputerState
import se.exuvo.aurora.starsystems.components.TimedMovementComponent
import se.exuvo.aurora.starsystems.systems.GroupSystem
import se.exuvo.aurora.starsystems.systems.MovementSystem
import se.exuvo.aurora.starsystems.systems.RenderSystem
import se.exuvo.aurora.starsystems.systems.WeaponSystem
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
import se.exuvo.aurora.starsystems.systems.TargetingSystem
import kotlin.concurrent.withLock

class StarSystemScreen(val system: StarSystem) : GameScreenImpl(), InputProcessor {
	companion object {
		val WEAPON_FAMILY = Aspect.one(IdleTargetingComputersComponent::class.java, ActiveTargetingComputersComponent::class.java)
	}

	private val galaxy by lazy (LazyThreadSafetyMode.NONE) { GameServices[Galaxy::class] }
	private val galaxyGroupSystem by lazy (LazyThreadSafetyMode.NONE) { GameServices[GroupSystem::class] }
	private val renderSystem by lazy (LazyThreadSafetyMode.NONE) { system.world.getSystem(RenderSystem::class.java) }
	private val movementSystem by lazy (LazyThreadSafetyMode.NONE) { system.world.getSystem(MovementSystem::class.java) }
	private val weaponSystem by lazy (LazyThreadSafetyMode.NONE) { system.world.getSystem(WeaponSystem::class.java) }
	private val targetingSystem by lazy (LazyThreadSafetyMode.NONE) { system.world.getSystem(TargetingSystem::class.java) }
	
	private val uiScreen by lazy (LazyThreadSafetyMode.NONE) { AuroraGame.currentWindow.screenService[UIScreen::class] }

	private var viewport: Viewport = ScreenViewport()
	private var camera = viewport.camera as OrthographicCamera
	private val cameraOffset = Vector2L()

	private val circleMapper = ComponentMapper.getFor(CircleComponent::class.java, system.world)
	private val movementMapper = ComponentMapper.getFor(TimedMovementComponent::class.java, system.world)
	private val starSystemMapper = ComponentMapper.getFor(StarSystemComponent::class.java, system.world)
	private val idleTargetingComputersComponentMapper = ComponentMapper.getFor(IdleTargetingComputersComponent::class.java, system.world)
	private val activeTargetingComputersComponentMapper = ComponentMapper.getFor(ActiveTargetingComputersComponent::class.java, system.world)
	private val shipMapper = ComponentMapper.getFor(ShipComponent::class.java, system.world)
	
	val allSubscription = system.world.getAspectSubscriptionManager().get(Aspect.all())

	var zoomLevel = 0
	var zoomSensitivity = Settings.getFloat("UI/zoomSensitivity", 1.25f).toDouble()
	val maxZoom = 1E8f

	init {
		viewport.update(Gdx.graphics.width, Gdx.graphics.height)
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

		galaxy.uiLock.withLock {
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

	private fun drawUI() {
		val spriteBatch = AuroraGame.currentWindow.spriteBatch
		val uiCamera = AuroraGame.currentWindow.screenService.uiCamera
		
		spriteBatch.projectionMatrix = uiCamera.combined
		spriteBatch.begin()
		
		var x = 8f
		x += Assets.fontUI.draw(spriteBatch, "System view, zoomLevel $zoomLevel, ${Units.daysToDate(galaxy.day)} ${Units.secondsToString(galaxy.time)}, ", x, 32f).width
		
		if (galaxy.speed == 0L) {
			Assets.fontUI.color = Color.RED
			x += Assets.fontUI.draw(spriteBatch, "System Error", x, 32f).width
			Assets.fontUI.color = Color.WHITE
			
		} else if (galaxy.speed < 0L) {
			Assets.fontUI.color = Color.GRAY
			x += Assets.fontUI.draw(spriteBatch, "speed ${Units.NANO_SECOND / -galaxy.speed}", x, 32f).width
			Assets.fontUI.color = Color.WHITE
			
		} else if (galaxy.speedLimited) {
			Assets.fontUI.color = Color.RED
			x += Assets.fontUI.draw(spriteBatch, "speed ${Units.NANO_SECOND / galaxy.speed}", x, 32f).width
			Assets.fontUI.color = Color.WHITE
			
		}  else {
			x += Assets.fontUI.draw(spriteBatch, "speed ${Units.NANO_SECOND / galaxy.speed}", x, 32f).width
		}
		
		x += Assets.fontUI.draw(spriteBatch, " ${system.updateTimeAverage.toInt() / 1000}us", x, 32f).width
		x += Assets.fontUI.draw(spriteBatch, ", ${allSubscription.getEntityCount()}st", x, 32f).width
		
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

			galaxy.uiLock.withLock {
				if (galaxyGroupSystem.get(GroupSystem.SELECTED).isNotEmpty()) {
					selectedAction = KeyActions_StarSystemScreen.ATTACK
					println("Selected action " + action)
				} else {
					println("Unable to select action " + action + ", no selection")
				}
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
	
	//TODO selection priorities, ships > planets > missiles
	val directSelectionFamily = system.world.getAspectSubscriptionManager().get(Aspect.all(TimedMovementComponent::class.java, RenderComponent::class.java, CircleComponent::class.java))
	val indirectSelectionFamily = system.world.getAspectSubscriptionManager().get(Aspect.all(TimedMovementComponent::class.java, RenderComponent::class.java))
	val weaponFamily = WEAPON_FAMILY.build(system.world)
	val movementFamily = MovementSystem.CAN_ACCELERATE_FAMILY.build(system.world)

	var dragX = 0
	var dragY = 0

	override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {

//		imGuiScreen.closeCommandMenu()
		
		if (!moveWindow && !dragSelect) {

			when (button) {
				Input.Buttons.LEFT -> {
					
					commandMenuPotentialStart = false

					galaxy.uiLock.withLock {

						val mouseInGameCoordinates = toWorldCordinates(getMouseInScreenCordinates(screenX, screenY))
						val entitiesUnderMouse = Bag<EntityReference>()
						val entityIDs = directSelectionFamily.entities
						val testCircle = CircleL()
						val zoom = camera.zoom

						// Exact check first
						entityIDs.forEachFast { entityID ->
							val position = movementMapper.get(entityID).get(galaxy.time).value.position
							val radius: Float

							if (renderSystem.inStrategicView(entityID, zoom)) {

								radius = zoom * RenderSystem.STRATEGIC_ICON_SIZE / 2

							} else {

								radius = circleMapper.get(entityID).radius
							}

							testCircle.set(position, radius * 1000)

							if (testCircle.contains(mouseInGameCoordinates)) {
								entitiesUnderMouse.add(system.getEntityReference(entityID))
							}
						}

						// Lenient check if empty
						if (entitiesUnderMouse.isEmpty()) {
							entityIDs.forEachFast { entityID ->
								val position = movementMapper.get(entityID).get(galaxy.time).value.position
								val radius: Float

								if (renderSystem.inStrategicView(entityID, zoom)) {

									radius = zoom * RenderSystem.STRATEGIC_ICON_SIZE / 2

								} else {

									radius = circleMapper.get(entityID).radius * 1.1f + 2 * camera.zoom
								}

								testCircle.set(position, radius * 1000)

								if (testCircle.contains(mouseInGameCoordinates)) {
									entitiesUnderMouse.add(system.getEntityReference(entityID))
								}
							}

							if (entitiesUnderMouse.isNotEmpty()) {
//								println("lenient selected ${entitiesUnderMouse.size} entities")
							}

						} else {
//							println("strict selected ${entitiesUnderMouse.size} entities")
						}

						if (selectedAction == null) {

							if (entitiesUnderMouse.isNotEmpty()) {

								dragSelectPotentialStart = false;

								if (galaxyGroupSystem.get(GroupSystem.SELECTED).isNotEmpty() && !Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
									galaxyGroupSystem.clear(GroupSystem.SELECTED)
//								println("cleared selection")
								}

								galaxyGroupSystem.add(entitiesUnderMouse, GroupSystem.SELECTED)

							} else {

								dragSelectPotentialStart = true;
								dragX = screenX;
								dragY = screenY;

//						println("drag select potential dragX $dragX, dragY $dragY")
							}

						} else if (selectedAction == KeyActions_StarSystemScreen.ATTACK) {
							selectedAction = null

							if (galaxyGroupSystem.get(GroupSystem.SELECTED).isNotEmpty()) {
								val selectedEntities = galaxyGroupSystem.get(GroupSystem.SELECTED).filter { entityRef ->
									system == entityRef.system && weaponFamily.isInterested(entityRef.entityID)
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
										val ship = shipMapper.get(entityRef.entityID)
										val activeTCs = activeTargetingComputersComponentMapper.get(entityRef.entityID)
										
										if (activeTCs != null) {
											activeTCs.targetingComputers.forEachFast{ tc ->
												val tcState = ship.getPartState(tc)[TargetingComputerState::class]
	
												if (targetRef != null) {
													tcState.target = targetRef
													
												} else {
													targetingSystem.clearTarget(entityRef.entityID, ship, tc)
												}
											}
										}
										
										if (targetRef != null) {
											val idleTCs = idleTargetingComputersComponentMapper.get(entityRef.entityID)
											
											idleTCs.targetingComputers.forEachFast{ tc ->
												targetingSystem.setTarget(entityRef.entityID, ship, tc, targetRef)
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
				if (galaxyGroupSystem.get(GroupSystem.SELECTED).isNotEmpty() && !Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
					galaxyGroupSystem.clear(GroupSystem.SELECTED)
	//				println("cleared selection")
				}
	
				val dragSelection = getDragSelection()
	//			println("dragSelection $dragSelection")
	
				val p1GameCoordinates = toWorldCordinates(Vector3(dragSelection.x.toFloat(), (viewport.screenHeight - dragSelection.y).toFloat(), 0f))
				val p2GameCoordinates = toWorldCordinates(Vector3((dragSelection.x + dragSelection.width).toFloat(), (viewport.screenHeight - (dragSelection.y + dragSelection.height)).toFloat(), 0f))
	//			println("p1GameCoordinates $p1GameCoordinates, p2GameCoordinates $p2GameCoordinates")
	
				val entitiesInSelection = Bag<EntityReference>()
				
				galaxy.uiLock.withLock {
					val entityIDs = indirectSelectionFamily.entities
					val testRectangle = RectangleL(p1GameCoordinates.x, p1GameCoordinates.y, p2GameCoordinates.x - p1GameCoordinates.x, p2GameCoordinates.y - p1GameCoordinates.y)
		//			println("testRectangle $testRectangle")
		
					entityIDs.forEachFast { entityID ->
						val position = movementMapper.get(entityID).get(galaxy.time).value.position
		
						if (testRectangle.contains(position)) {
							entitiesInSelection.add(system.getEntityReference(entityID))
						}
					}
				}
	
				if (entitiesInSelection.isNotEmpty()) {
					galaxyGroupSystem.add(entitiesInSelection, GroupSystem.SELECTED)
	//				println("drag selected ${entitiesInSelection.size} entities")
				}
	
				dragSelect = false;
				return true
			}
	
			if (dragSelectPotentialStart) {
				if (galaxyGroupSystem.get(GroupSystem.SELECTED).isNotEmpty() && !Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
					galaxyGroupSystem.clear(GroupSystem.SELECTED)
	//				println("cleared selection")
				}
			}
		}
		
		if (button == Input.Buttons.RIGHT && commandMenuPotentialStart) {
			
			commandMenuPotentialStart = false
			
			if (galaxyGroupSystem.get(GroupSystem.SELECTED).isNotEmpty()) {

				val selectedEntities = galaxyGroupSystem.get(GroupSystem.SELECTED).filter { entityRef ->

					system == entityRef.system && movementFamily.isInterested(entityRef.entityID)
				}

				if (selectedEntities.isNotEmpty()) {

					galaxy.uiLock.withLock {

						val mouseInGameCoordinates = toWorldCordinates(getMouseInScreenCordinates(screenX, screenY))
						val entitiesUnderMouse = IntBag()
						val entityIDs = directSelectionFamily.entities
						val testCircle = CircleL()
						val zoom = camera.zoom

						// Exact check first
						entityIDs.forEachFast { entityID ->
							val position = movementMapper.get(entityID).get(galaxy.time).value.position
							val radius: Float

							if (renderSystem.inStrategicView(entityID, zoom)) {

								radius = zoom * RenderSystem.STRATEGIC_ICON_SIZE / 2

							} else {

								radius = circleMapper.get(entityID).radius
							}

							testCircle.set(position, radius * 1000)

							if (testCircle.contains(mouseInGameCoordinates)) {
								entitiesUnderMouse.add(entityID)
							}
						}

						// Lenient check if empty
						if (entitiesUnderMouse.isEmpty()) {
							entityIDs.forEachFast { entityID ->
								val position = movementMapper.get(entityID).get(galaxy.time).value.position
								val radius: Float

								if (renderSystem.inStrategicView(entityID, zoom)) {

									radius = zoom * RenderSystem.STRATEGIC_ICON_SIZE / 2

								} else {

									radius = circleMapper.get(entityID).radius * 1.1f + 2 * camera.zoom
								}

								testCircle.set(position, radius * 1000)

								if (testCircle.contains(mouseInGameCoordinates)) {
									entitiesUnderMouse.add(entityID)
								}
							}
						}

						if (!entitiesUnderMouse.isEmpty) {

//							println("Issuing move to entity order")

							val targetEntityID = entitiesUnderMouse.get(0)
							var approachType = ApproachType.BRACHISTOCHRONE

							if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
								approachType = ApproachType.BALLISTIC
							}

							selectedEntities.forEachFast{ entityRef ->
								movementSystem.moveToEntity(entityRef.entityID, targetEntityID, approachType)
							}

						} else {

//							println("Issuing move to position order")

							val targetPosition = mouseInGameCoordinates
							var approachType = ApproachType.BRACHISTOCHRONE

							if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
								approachType = ApproachType.BALLISTIC
							}

							selectedEntities.forEachFast{ entityRef ->

								movementSystem.moveToPosition(entityRef.entityID, targetPosition, approachType)
							}
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
//			Det som var under musen innan scroll ska fortsätta vara där efter zoom
//			http://stackoverflow.com/questions/932141/zooming-an-object-based-on-mouse-position

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
