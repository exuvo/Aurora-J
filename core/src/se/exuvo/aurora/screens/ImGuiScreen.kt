package se.exuvo.aurora.screens

import com.artemis.ComponentMapper
import com.artemis.utils.Bag
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics
import com.badlogic.gdx.utils.Disposable
import glm_.vec2.Vec2
import glm_.vec2.Vec2d
import imgui.Col
import imgui.classes.Context
import imgui.ImGui
import imgui.TreeNodeFlag
import imgui.WindowFlag
import org.apache.logging.log4j.LogManager
import org.lwjgl.glfw.GLFW
import se.exuvo.aurora.Assets
import se.exuvo.aurora.galactic.AmmunitionPart
import se.exuvo.aurora.galactic.Battery
import se.exuvo.aurora.galactic.CargoType
import se.exuvo.aurora.galactic.ChargedPart
import se.exuvo.aurora.galactic.FueledPart
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.PassiveSensor
import se.exuvo.aurora.galactic.PoweredPart
import se.exuvo.aurora.galactic.PoweringPart
import se.exuvo.aurora.galactic.ReloadablePart
import se.exuvo.aurora.galactic.Resource
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.planetarysystems.components.AmmunitionPartState
import se.exuvo.aurora.planetarysystems.components.ChargedPartState
import se.exuvo.aurora.planetarysystems.components.FueledPartState
import se.exuvo.aurora.planetarysystems.components.PassiveSensorState
import se.exuvo.aurora.planetarysystems.components.PowerComponent
import se.exuvo.aurora.planetarysystems.components.PoweredPartState
import se.exuvo.aurora.planetarysystems.components.PoweringPartState
import se.exuvo.aurora.planetarysystems.components.ReloadablePartState
import se.exuvo.aurora.planetarysystems.components.ShipComponent
import se.exuvo.aurora.planetarysystems.components.SolarIrradianceComponent
import se.exuvo.aurora.planetarysystems.components.TargetingComputerState
import se.exuvo.aurora.planetarysystems.systems.GroupSystem
import se.exuvo.aurora.planetarysystems.systems.RenderSystem
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.utils.keys.KeyActions_ImGuiScreen
import se.exuvo.aurora.utils.keys.KeyMappings
import se.exuvo.aurora.utils.printID
import se.unlogic.standardutils.reflection.ReflectionUtils
import uno.glfw.GlfwWindow
import uno.glfw.GlfwWindowHandle
import kotlin.concurrent.read
import kotlin.concurrent.write
import se.exuvo.aurora.AuroraGame
import imgui.impl.glfw.ImplGlfw
import imgui.impl.gl.ImplGL3
import imgui.impl.gl.GLInterface
import imgui.impl.gl.ImplBestGL
import imgui.imgui.widgets.BeginPiePopup
import imgui.imgui.widgets.PieMenuItem
import imgui.imgui.widgets.BeginPieMenu
import imgui.imgui.widgets.EndPieMenu
import imgui.imgui.widgets.EndPiePopup
import imgui.ImGui.windowContentRegionWidth
import imgui.TabBarFlag
import imgui.or
import se.exuvo.aurora.planetarysystems.components.EntityUUID
import se.exuvo.aurora.empires.components.ColonyComponent
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.planetarysystems.components.EntityReference
import se.exuvo.aurora.galactic.Player
import se.exuvo.aurora.empires.components.Shipyard
import se.exuvo.aurora.empires.components.ShipyardSlipway
import imgui.SelectableFlag
import imgui.Dir
import se.exuvo.aurora.galactic.ShipHull
import se.exuvo.aurora.empires.components.ShipyardModification
import imgui.StyleVar
import imgui.internal.ItemFlag
import se.exuvo.aurora.empires.components.ShipyardModificationRetool
import se.exuvo.aurora.empires.components.ShipyardModifications

class ImGuiScreen : GameScreenImpl(), InputProcessor {

	val log = LogManager.getLogger(this.javaClass)

	private val galaxy by lazy (LazyThreadSafetyMode.NONE) { GameServices[Galaxy::class] }
	private val galaxyGroupSystem by lazy (LazyThreadSafetyMode.NONE) { GameServices[GroupSystem::class] }

	override val overlay = true
	private val ctx: Context
	private val lwjglGlfw: ImplGlfw
	private val gl3: GLInterface
	private val gdxGLFWKeyMap = mutableMapOf<Int, Int>()

	class ImGuiGlobalStorage(val ctx: Context): Disposable {
		override fun dispose() {}
	}
	
	init {
		gdxGLFWKeyMap[Input.Keys.TAB] = GLFW.GLFW_KEY_TAB

		gdxGLFWKeyMap[Input.Keys.LEFT] = GLFW.GLFW_KEY_LEFT
		gdxGLFWKeyMap[Input.Keys.RIGHT] = GLFW.GLFW_KEY_RIGHT
		gdxGLFWKeyMap[Input.Keys.UP] = GLFW.GLFW_KEY_UP
		gdxGLFWKeyMap[Input.Keys.DOWN] = GLFW.GLFW_KEY_DOWN

		gdxGLFWKeyMap[Input.Keys.PAGE_UP] = GLFW.GLFW_KEY_PAGE_UP
		gdxGLFWKeyMap[Input.Keys.PAGE_DOWN] = GLFW.GLFW_KEY_PAGE_DOWN

		gdxGLFWKeyMap[Input.Keys.HOME] = GLFW.GLFW_KEY_HOME
		gdxGLFWKeyMap[Input.Keys.END] = GLFW.GLFW_KEY_END

		gdxGLFWKeyMap[Input.Keys.BACKSPACE] = GLFW.GLFW_KEY_BACKSPACE
		gdxGLFWKeyMap[Input.Keys.FORWARD_DEL] = GLFW.GLFW_KEY_DELETE

		gdxGLFWKeyMap[Input.Keys.ENTER] = GLFW.GLFW_KEY_ENTER
		gdxGLFWKeyMap[Input.Keys.ESCAPE] = GLFW.GLFW_KEY_ESCAPE

		gdxGLFWKeyMap[Input.Keys.CONTROL_LEFT] = GLFW.GLFW_KEY_LEFT_CONTROL
		gdxGLFWKeyMap[Input.Keys.CONTROL_RIGHT] = GLFW.GLFW_KEY_RIGHT_CONTROL
		gdxGLFWKeyMap[Input.Keys.ALT_LEFT] = GLFW.GLFW_KEY_LEFT_ALT
		gdxGLFWKeyMap[Input.Keys.ALT_RIGHT] = GLFW.GLFW_KEY_RIGHT_ALT
		gdxGLFWKeyMap[Input.Keys.SHIFT_LEFT] = GLFW.GLFW_KEY_LEFT_SHIFT
		gdxGLFWKeyMap[Input.Keys.SHIFT_RIGHT] = GLFW.GLFW_KEY_RIGHT_SHIFT

		gdxGLFWKeyMap[Input.Keys.A] = GLFW.GLFW_KEY_A
		gdxGLFWKeyMap[Input.Keys.C] = GLFW.GLFW_KEY_C
		gdxGLFWKeyMap[Input.Keys.V] = GLFW.GLFW_KEY_V
		gdxGLFWKeyMap[Input.Keys.X] = GLFW.GLFW_KEY_X
		gdxGLFWKeyMap[Input.Keys.Y] = GLFW.GLFW_KEY_Y
		gdxGLFWKeyMap[Input.Keys.Z] = GLFW.GLFW_KEY_Z

		var firstContext = galaxy.storage(ImGuiGlobalStorage::class)
//		
		if (firstContext == null) {
			ctx = Context()
//			ctx.io.configFlags = ctx.io.configFlags or ConfigFlag.NavEnableKeyboard.i
			galaxy.storage + ImGuiGlobalStorage(ctx)
			
		} else {
			ctx = Context(firstContext.ctx.io.fonts)
		}
		
		ctx.setCurrent()
		
		ImGui.styleColorsDark()
		
		ImGui.style.apply {
			//@formatter:off restore classic colors for some parts
			colors[Col.FrameBg.i]               (0.43f, 0.43f, 0.43f, 0.39f)
			colors[Col.FrameBgHovered.i]        (0.47f, 0.47f, 0.69f, 0.40f)
			colors[Col.FrameBgActive.i]         (0.42f, 0.41f, 0.64f, 0.69f)
			colors[Col.TitleBg.i]               (0.04f, 0.04f, 0.04f, 0.87f)
			colors[Col.TitleBgActive.i]         (0.32f, 0.32f, 0.63f, 1.00f)
			colors[Col.TitleBgCollapsed.i]      (0.00f, 0.00f, 0.00f, 0.51f)
			colors[Col.Header.i]                (0.40f, 0.40f, 0.90f, 0.45f)
			colors[Col.HeaderHovered.i]         (0.45f, 0.45f, 0.90f, 0.80f)
			colors[Col.HeaderActive.i]          (0.53f, 0.53f, 0.87f, 0.80f)
			colors[Col.PlotLines.i]             (0.80f, 0.80f, 0.80f, 1.00f)
			colors[Col.PlotHistogram.i]         (0.90f, 0.70f, 0.00f, 1.00f)
			//@formatter:on
		}
		
		lwjglGlfw = ImplGlfw(GlfwWindow(GlfwWindowHandle((Gdx.graphics as Lwjgl3Graphics).window.windowHandle)), false)
		gl3 = ImplBestGL()
	}

	override fun show() {
		addResourceAmount = 1
	}

	private var demoVisible = false
	private var mainDebugVisible = false
	private var shipDebugVisible = false
	private var shipDesignerVisible = false
	private var colonyManagerVisible = true

	var slider = 1f
	var stringbuf = CharArray(10)
	var img = Assets.textures.findRegion("strategic/sun")
	var menuBarState = BooleanArray(1)
	var graphValues = floatArrayOf(0f, 5f, 2f, 4f)
	
	override fun draw() {

		// https://github.com/kotlin-graphics/imgui/wiki/Using-libGDX
		// https://github.com/ocornut/imgui

		try {
			ctx.setCurrent()
			
			gl3.newFrame()
			lwjglGlfw.newFrame()
			ImGui.newFrame()

			if (demoVisible) {
				ImGui.showDemoWindow(::demoVisible)
			}
			
//			if (ImGui.begin("test2")) {
//				ImGui.text("Hello, world")
//				ImGui.end()
//			}
//			
//			ImGui.openPopup("test");
//			
//			if (ImGui.beginPopup("test")) {
//				if(ImGui.button("close")) {
//				  try {
//				    ImGui.closeCurrentPopup();
//				  } catch (err: NullPointerException) {
//				    err.printStackTrace()
//				  }
//				}
//			  ImGui.endPopup();
//			}

			if (mainDebugVisible) {

				if (ImGui.begin("Debug window", ::mainDebugVisible, WindowFlag.MenuBar.i)) {

					if (ImGui.beginMenuBar()) {
						if (ImGui.beginMenu("Windows")) {
							if (ImGui.menuItem("Ship debug", "", shipDebugVisible)) {
								shipDebugVisible = !shipDebugVisible
							}
							if (ImGui.menuItem("Ship designer", "", shipDesignerVisible)) {
								shipDesignerVisible = !shipDesignerVisible
							}
							if (ImGui.menuItem("Colony manager", "", colonyManagerVisible)) {
								colonyManagerVisible = !colonyManagerVisible
							}
							if (ImGui.menuItem("ImGui Demo", "hotkey", demoVisible)) {
								demoVisible = !demoVisible
							}
							ImGui.endMenu();
						}
						if (ImGui.beginMenu("Render")) {
							if (ImGui.menuItem("Show PassiveSensor hits", "", RenderSystem.debugPassiveSensors)) {
								RenderSystem.debugPassiveSensors = !RenderSystem.debugPassiveSensors
							}
							if (ImGui.menuItem("DisableStrategicView", "", RenderSystem.debugDisableStrategicView)) {
								RenderSystem.debugDisableStrategicView = !RenderSystem.debugDisableStrategicView
							}
							ImGui.endMenu();
						}
						ImGui.endMenuBar();
					}

					ImGui.text("Hello, world %d", 4)
					ImGui.text("ctx.hoveredWindow ${ctx.hoveredWindow}")
					ImGui.text("ctx.navWindow ${ctx.navWindow}")
					ImGui.text("ctx.navWindow.dc ${ctx.navWindow?.dc}")
					ImGui.text("ctx.io.wantCaptureMouse ${ctx.io.wantCaptureMouse}")
					ImGui.text("ctx.io.wantCaptureKeyboard ${ctx.io.wantCaptureKeyboard}")
					ImGui.plotLines("plot", graphValues)

					if (ImGui.button("OK")) {
						println("click")
					}

					ImGui.inputText("string", stringbuf)
					ImGui.sliderFloat("float", ::slider, 0f, 1f)
					ImGui.image(img.getTexture().textureObjectHandle, Vec2(64, 64))
				}
				ImGui.end()
			}

			shipDebug()
			commandMenu()
			shipDesigner()
			colonyManager();

			ImGui.render()
			gl3.renderDrawData(ctx.drawData)
			
		} catch (e: Throwable) {
			log.error("Error drawing debug window", e)
		}
	}
	
	private fun shipDesigner() {
		
		if (shipDesignerVisible) {

			if (ImGui.begin("Ship designer", ::shipDesignerVisible, WindowFlag.None.i)) {
				
				
				
				
				ImGui.end()
			}
		}
	}
	
	var selectedColony: EntityReference? = null
	var selectedShipyard: Shipyard? = null
	var selectedSlipway: ShipyardSlipway? = null
	var selectedHull: ShipHull? = null
	var selectedShipyardModification: ShipyardModifications? = null
	
	private fun colonyManager() {
		
		if (colonyManagerVisible) {

			with (ImGui) {
				with (imgui.dsl) {
					window("Colony manager", ::colonyManagerVisible, WindowFlag.None.i) {
						
						val empire = Player.current.empire!!
						
						if (selectedColony == null && empire.colonies.isNotEmpty()) {
							selectedColony = empire.colonies[0]
						}
						
						// windowContentRegionWidth * 0.3f
						child("Colonies", Vec2(200, 0), true, WindowFlag.None.i) {
							
							collapsingHeader("Colonies ${empire.colonies.size}", TreeNodeFlag.DefaultOpen.i) {
								
								val systemColonyMap = empire.colonies.groupBy { ref -> ref.system }
								
								systemColonyMap.forEach { entry ->
									
									val system = entry.key
									system.lock.read {
										
										if (treeNodeExV(system.galacticEntityID.toString(), TreeNodeFlag.DefaultOpen or TreeNodeFlag.SpanAvailWidth or TreeNodeFlag.NoTreePushOnOpen, "System ${system.initialName}")) {
											
											var newSelectedColony: EntityReference? = null
											
											entry.value.forEachIndexed { idx, colonyRef ->
												
												val entityID = colonyRef.entityID
												
												val nameMapper = ComponentMapper.getFor(NameComponent::class.java, system.world)
												val colonyMapper = ComponentMapper.getFor(ColonyComponent::class.java, system.world)
												val name = nameMapper.get(entityID).name
												val colony = colonyMapper.get(entityID)
												
												var nodeFlags = TreeNodeFlag.Leaf or TreeNodeFlag.SpanAvailWidth or TreeNodeFlag.NoTreePushOnOpen
												
												if (colonyRef == selectedColony) {
													nodeFlags = nodeFlags or TreeNodeFlag.Selected
												}
												
												treeNodeExV(idx.toString(), nodeFlags, "$name - ${colony.population}")
			
												if (isItemClicked()) {
													newSelectedColony = colonyRef
												}
											}
											
											if (newSelectedColony != null) {
												selectedColony = newSelectedColony
											}
											
//											treePop()
										}
									}
								}
							}
							
							collapsingHeader("Stations 0", TreeNodeFlag.DefaultOpen.i) {
								
							}
						}
	
						sameLine()
						
						child("Tabs", Vec2(0, 0), false, WindowFlag.None.i) {
							
							val colonyRef = selectedColony
							
							if (colonyRef == null) {
								text("No colony selected")
								
							} else if (beginTabBar("Tabs", TabBarFlag.Reorderable or TabBarFlag.TabListPopupButton or TabBarFlag.FittingPolicyResizeDown)) {
								
								val system = colonyRef.system
								val entityID = colonyRef.entityID
								
								system.lock.read {
									val colonyMapper = ComponentMapper.getFor(ColonyComponent::class.java, system.world)
									val colony = colonyMapper.get(entityID)
									
									if (beginTabItem("Shipyards")) {
										
										child("List", Vec2(0, -200), true, WindowFlag.None.i) {
											
											//TODO replace with tables when that is done https://github.com/ocornut/imgui/issues/125
											columns(7)
											
											text("Type")
											nextColumn()
											text("Capacity")
											nextColumn()
											text("Tooled Hull")
											nextColumn()
											text("Activity")
											nextColumn()
											text("Progress")
											nextColumn()
											text("Remaining")
											nextColumn()
											text("Completion")
											
											colony.shipyards.forEach { shipyard ->
												nextColumn()
												
												val shipyardSelected = shipyard == selectedShipyard && selectedSlipway == null
												
												val storage = ctx.currentWindow!!.dc.stateStorage
												
												val buttonIDString = "##" + shipyard.hashCode().toString()
												val buttonID = getId(buttonIDString)
												val shipyardOpen = storage.int(buttonID, 1) != 0
												
												if (arrowButtonEx(buttonIDString, if (shipyardOpen) Dir.Down else Dir.Right, Vec2(ctx.fontSize), 0)) {
													storage[buttonID] = !shipyardOpen
												}
												
												sameLine()
												if (selectable("${shipyard.type.short} - ${shipyard.location.short}", shipyardSelected, SelectableFlag.SpanAllColumns.i)) {
													selectedShipyard = shipyard
													selectedSlipway = null
													selectedHull = null
												}
												
												nextColumn()
												rightAlignedColumnText(Units.volumeToString(shipyard.capacity))
												nextColumn()
												text("${shipyard.tooledHull}")
												nextColumn()
												
												val modActivity = shipyard.modificationActivity
												
												if (modActivity != null) {
													
													text("${modActivity.getDescription()}")
													nextColumn()
													
													if (shipyard.modificationProgress == 0L) {
														selectable("0%")
													} else {
														val progress = (100 * shipyard.modificationProgress) / modActivity.getCost(shipyard)
														selectable("$progress%")
													}
													nextColumn()
													
													val daysToCompletion = (modActivity.getCost(shipyard) - shipyard.modificationProgress) / shipyard.modificationRate
													
													rightAlignedColumnText(Units.daysToRemaining(daysToCompletion.toInt()))
													nextColumn()
													text(Units.daysToDate(galaxy.day + daysToCompletion.toInt()))
													
												} else {
													text("")
													nextColumn()
													text("")
													nextColumn()
													text("")
													nextColumn()
													text("")
												}
	
												if (shipyardOpen) {
													shipyard.slipways.forEach { slipway ->
														nextColumn()
														
														val hull = slipway.hull
														val slipwaySelected = slipway == selectedSlipway
		
														ImGui.cursorPosX = ImGui.cursorPosX + ctx.fontSize + 2 * ImGui.style.framePadding.x
														
														if (selectable("##" + slipway.hashCode().toString(), slipwaySelected, SelectableFlag.SpanAllColumns.i)) {
															selectedShipyard = shipyard
															selectedSlipway = slipway
															selectedHull = null
														}
														
														if (hull != null) {
															sameLineRightAlignedColumnText(Units.massToString(hull.getMass()))
															nextColumn()
															rightAlignedColumnText(Units.volumeToString(hull.getVolume()))
															nextColumn()
															text("${hull}")
															nextColumn()
															text("Building")
															nextColumn()
															text("${slipway.progress()}%")
															nextColumn()
															val daysToCompletion = (slipway.totalCost() - slipway.usedResources()) / shipyard.buildRate
															rightAlignedColumnText(Units.daysToRemaining(daysToCompletion.toInt()))
															nextColumn()
															text(Units.daysToDate(galaxy.day + daysToCompletion.toInt()))
														} else {
															sameLineRightAlignedColumnText("-")
															nextColumn()
															rightAlignedColumnText("-")
															nextColumn()
															text("-")
															nextColumn()
															text("None")
															nextColumn()
															text("-")
															nextColumn()
															rightAlignedColumnText("-")
															nextColumn()
															text("-")
														}
													}
												}
											}
											
											endColumns()
										}
										
										var shipyard = selectedShipyard
										val slipway = selectedSlipway
										
										if (shipyard != null && slipway != null) {
											
											val hull = slipway.hull
											val tooledHull = shipyard.tooledHull
											
											if (hull != null) {
												
												alignTextToFramePadding()
												text("Building: ${hull.name}")
												sameLine()
												
												if (button("Cancel")) {
													
													slipway.hull = null
													slipway.hullCost = emptyMap()
													
													slipway.usedResources.forEach { entry ->
														colony.addCargo(entry.key, entry.value)
														slipway.usedResources[entry.key] = 0L
													}
													
												} else {
													
													text("Used/Remaining resources:")
													group {
														slipway.hullCost.forEach { entry ->
															text(entry.key.name)
														}
													}
													sameLine()
													group {
														var maxWidth = 0f
														slipway.usedResources.forEach { entry ->
															maxWidth = kotlin.math.max(maxWidth, calcTextSize(Units.massToString(entry.value)).x)
														}
														slipway.usedResources.forEach { entry ->
															val string = Units.massToString(entry.value)
															ImGui.cursorPosX = ImGui.cursorPosX + maxWidth - calcTextSize(string).x - ImGui.scrollX
															text(string)
														}
													}
													sameLine()
													group {
														var maxWidth = 0f
														slipway.hullCost.forEach { entry ->
															maxWidth = kotlin.math.max(maxWidth, calcTextSize(Units.massToString(entry.value)).x)
														}
														slipway.hullCost.forEach { entry ->
															text("/")
															sameLine()
															val string = Units.massToString(entry.value)
															ImGui.cursorPosX = ImGui.cursorPosX + maxWidth - calcTextSize(string).x - ImGui.scrollX
															text(string)
														}
													}
												}
												
											} else if (tooledHull != null) {
												
												val selectedHull2 = selectedHull
												
												if (beginCombo("Ship Hull", if (selectedHull2 == null) "-" else selectedHull2.toString(), 0)) {
													
													empire.shipHulls.filter{ it == tooledHull || it.parentHull == tooledHull }.forEach { empireHull ->
														
														val selected = empireHull == selectedHull2
														
														if (selectable(empireHull.toString(), selected)) {
															selectedHull = empireHull
														}
														
														if (selected) {
															setItemDefaultFocus()
														}
													}
													
													endCombo()
												}
												
												 if (selectedHull2 == null) {
													 pushItemFlag(ItemFlag.Disabled.i, true)
													 pushStyleVar(StyleVar.Alpha, style.alpha * 0.5f)
												 }
												
												if (button("Build") && selectedHull2 != null) {
													slipway.build(selectedHull2)
												}

												if (selectedHull2 == null) {
													popItemFlag()
													popStyleVar()
												}
											}
											
										} else if (shipyard != null) {
											
											val modification = shipyard.modificationActivity
											
											if (modification != null) {
												
												alignTextToFramePadding()
												text("${modification.getDescription()}")
												sameLine()
												
												if (button("Cancel")) {
													
													colony.addCargo(Resource.GENERIC, shipyard.modificationProgress)
													
													shipyard.modificationActivity = null
													shipyard.modificationProgress = 0L
													
												} else {
													
													text("Used/Remaining resources:")
													text(Resource.GENERIC.name)
													sameLine()
													text(Units.massToString(shipyard.modificationProgress))
													sameLine()
													text("/")
													sameLine()
													text(Units.massToString(modification.getCost(shipyard)))
												}
												
											} else {
												
												val selectedModification = selectedShipyardModification
												
												if (beginCombo("Modification", if (selectedModification == null) "-" else selectedModification.name, 0)) {
													
													ShipyardModifications.values().forEach { possibleModification ->
														val selected = possibleModification == selectedModification
														
														if (selectable(possibleModification.name, selected)) {
															selectedShipyardModification = possibleModification
														}
														
														if (selected) {
															setItemDefaultFocus()
														}
													}
													
													endCombo()
												}
												
												if (selectedShipyardModification == ShipyardModifications.RETOOL) {
													
													val selectedHull2 = selectedHull
													
													if (beginCombo("Ship Hull", if (selectedHull2 == null) "-" else selectedHull2.toString(), 0)) {
													
														empire.shipHulls.forEach { empireHull ->
															val selected = empireHull == selectedHull2
															
															if (selectable(empireHull.toString(), selected)) {
																selectedHull = empireHull
															}
															
															if (selected) {
																setItemDefaultFocus()
															}
														}
														
														endCombo()
													}
													
													 if (selectedHull2 == null) {
														 pushItemFlag(ItemFlag.Disabled.i, true)
														 pushStyleVar(StyleVar.Alpha, style.alpha * 0.5f)
													 }
													
													if (button("Retool") && selectedHull2 != null) {
														
														shipyard.modificationActivity = ShipyardModificationRetool(selectedHull2)
														shipyard.modificationProgress = 0
													}
	
													if (selectedHull2 == null) {
														popItemFlag()
														popStyleVar()
													}
													
												}
												
											}
										}
										
										endTabItem()
									}
									
									if (beginTabItem("Industry")) {
										
										text("todo")
										endTabItem()
									}
									
									if (beginTabItem("Mining")) {
										
										text("Resources:")
										group {
											colony.resources.forEach { entry ->
												text(entry.key.name)
											}
										}
										sameLine()
										group {
											var maxWidth = 0f
											colony.resources.forEach { entry ->
												maxWidth = kotlin.math.max(maxWidth, calcTextSize(Units.massToString(entry.value)).x)
											}
											colony.resources.forEach { entry ->
												val string = Units.massToString(entry.value)
												ImGui.cursorPosX = ImGui.cursorPosX + maxWidth - calcTextSize(string).x - ImGui.scrollX
												text(string)
											}
										}
										
										endTabItem()
									}
								}
								
								endTabBar()
							}
						}
					}
				}
			}
		}
	}
	
	private fun rightAlignedColumnText(string: String) {
		with (ImGui) {
			ImGui.cursorPosX = ImGui.cursorPosX + ImGui.getColumnWidth() - calcTextSize(string).x - ImGui.scrollX - 2 * ImGui.style.itemSpacing.x
			text(string)
		}
	}
	
	private fun sameLineRightAlignedColumnText(string: String) {
		with (ImGui) {
			sameLine(ImGui.cursorPosX + ImGui.getColumnWidth() - calcTextSize(string).x - ImGui.scrollX - 2 * ImGui.style.itemSpacing.x)
			text(string)
		}
	}
	
	var lastDebugTime = 0L
	var powerAvailiableValues = FloatArray(60)
	var powerRequestedValues = FloatArray(60)
	var powerUsedValues = FloatArray(60)
	var arrayIndex = 0
	var addResource = Resource.NUCLEAR_FISSION
	var addResourceAmount = 0
	
	private fun shipDebug() {

		if (shipDebugVisible) {

			if (ImGui.begin("Ship debug", ::shipDebugVisible, WindowFlag.AlwaysAutoResize.i)) {

				val selectedEntities = galaxyGroupSystem.get(GroupSystem.SELECTED)

				if (selectedEntities.isEmpty()) {
					ImGui.text("Nothing selected")

				} else {

					val entity = selectedEntities.first()
					val system = galaxy.getPlanetarySystemByEntity(entity)

					val shipMapper = ComponentMapper.getFor(ShipComponent::class.java, entity.world)
					val powerMapper = ComponentMapper.getFor(PowerComponent::class.java, entity.world)
					val irradianceMapper = ComponentMapper.getFor(SolarIrradianceComponent::class.java, entity.world)

					system.lock.read {

						val shipComponent = shipMapper.get(entity)

						ImGui.text("Entity ${entity.printID()}")

						if (ImGui.collapsingHeader("Components", 0)) {

							val components = entity.getComponents(Bag())
							
							for (component in components) {

								if (ImGui.treeNode("${component::class.java.simpleName}")) {

									val fields = ReflectionUtils.getFields(component::class.java)
									for (field in fields) {
										ReflectionUtils.fixFieldAccess(field)
										ImGui.text("${field.name}: ${field.get(component)}")
									}

									ImGui.treePop()
								}
							}
						}

						if (shipComponent != null) {

							if (ImGui.collapsingHeader("Parts", TreeNodeFlag.DefaultOpen.i)) {

								for (partRef in shipComponent.hull.getPartRefs()) {
									if (ImGui.treeNode("${partRef.part::class.simpleName} ${partRef.part.name}")) {

										if (partRef.part is PoweringPart) {
											val state = shipComponent.getPartState(partRef)[PoweringPartState::class]
											ImGui.text("availiablePower ${Units.powerToString(state.availiablePower)}")
											ImGui.text("producedPower ${Units.powerToString(state.producedPower)}")
										}

										if (partRef.part is PoweredPart) {
											val state = shipComponent.getPartState(partRef)[PoweredPartState::class]
											ImGui.text("requestedPower ${Units.powerToString(state.requestedPower)}")
											ImGui.text("givenPower ${Units.powerToString(state.givenPower)}")
										}

										if (partRef.part is ChargedPart) {
											val state = shipComponent.getPartState(partRef)[ChargedPartState::class]
											ImGui.text("charge ${Units.powerToString(state.charge)}")
										}

										if (partRef.part is PassiveSensor) {
											val state = shipComponent.getPartState(partRef)[PassiveSensorState::class]
											ImGui.text("lastScan ${state.lastScan}")
										}

										if (partRef.part is AmmunitionPart) {
											val state = shipComponent.getPartState(partRef)[AmmunitionPartState::class]
											ImGui.text("amount ${state.amount}")
											ImGui.text("type ${state.type?.name}")
										}

										if (partRef.part is ReloadablePart) {
											val state = shipComponent.getPartState(partRef)[ReloadablePartState::class]
											ImGui.text("loaded ${state.loaded}")
											ImGui.text("reloadPowerRemaining ${Units.powerToString(state.reloadPowerRemaining)}")
										}

										if (partRef.part is FueledPart) {
											val state = shipComponent.getPartState(partRef)[FueledPartState::class]
											ImGui.text("fuelEnergyRemaining ${state.fuelEnergyRemaining}")
											ImGui.text("totalFuelEnergyRemaining ${state.totalFuelEnergyRemaining}")
										}

										if (partRef.part is TargetingComputer) {
											val state = shipComponent.getPartState(partRef)[TargetingComputerState::class]
											ImGui.text("target ${state.target?.printID()}")
											ImGui.text("lockCompletionAt ${state.lockCompletionAt}")
											
											if (ImGui.treeNode("linkedWeapons ${state.linkedWeapons.size}")) {
												for(linked in state.linkedWeapons) {
													ImGui.text("$linked")
												}
												ImGui.treePop()
											}
										}

										ImGui.treePop()
									}
								}
							}

							if (ImGui.collapsingHeader("Power", 0)) {

								val solarIrradiance = irradianceMapper.get(entity)

								if (solarIrradiance != null) {

									ImGui.text("Solar irradiance ${solarIrradiance.irradiance} W/m2")
								}

								val powerComponent = powerMapper.get(entity)

								if (powerComponent != null) {

									ImGui.separator()

									val now = System.currentTimeMillis()

									if (now - lastDebugTime > 500) {
										lastDebugTime = now

										powerAvailiableValues[arrayIndex] = powerComponent.totalAvailiablePower.toFloat()
										powerRequestedValues[arrayIndex] = powerComponent.totalRequestedPower.toFloat()
										powerUsedValues[arrayIndex] = powerComponent.totalUsedPower.toFloat() / powerComponent.totalAvailiablePower.toFloat()
										arrayIndex++

										if (arrayIndex >= 60) {
											arrayIndex = 0
										}
									}

									ImGui.plotLines("AvailiablePower", { powerAvailiableValues[(arrayIndex + it) % 60] }, 60, 0, "", 0f, Float.MAX_VALUE, Vec2(0, 50))
									ImGui.plotLines("RequestedPower", { powerRequestedValues[(arrayIndex + it) % 60] }, 60, 0, "", 0f, Float.MAX_VALUE, Vec2(0, 50))
									ImGui.plotLines("UsedPower", { powerUsedValues[(arrayIndex + it) % 60] }, 60, 0, "", 0f, 1f, Vec2(0, 50))

									if (ImGui.treeNode("Producers")) {
										powerComponent.poweringParts.forEach({
											val partRef = it
											val poweringState = shipComponent.getPartState(partRef)[PoweringPartState::class]

											val power = if (poweringState.availiablePower == 0L) 0f else poweringState.producedPower / poweringState.availiablePower.toFloat()

											ImGui.progressBar(power, Vec2(), "${Units.powerToString(poweringState.producedPower)}/${Units.powerToString(poweringState.availiablePower)}")

											ImGui.sameLine(0f, ImGui.style.itemInnerSpacing.x)
											ImGui.text("${partRef.part}")

											if (partRef is FueledPart && partRef is PoweringPart) {

												val fueledState = shipComponent.getPartState(partRef)[FueledPartState::class]
												val fuelRemaining = Units.secondsToString(fueledState.fuelEnergyRemaining / partRef.power)
												val totalFuelRemaining = Units.secondsToString(fueledState.totalFuelEnergyRemaining / partRef.power)

												ImGui.text("Fuel $fuelRemaining/$totalFuelRemaining W")
											}

											if (partRef.part is Battery) {

												val chargedState = shipComponent.getPartState(partRef)[ChargedPartState::class]
												val charge = chargedState.charge
												val maxCharge = partRef.part.capacitor
												val charged = if (maxCharge == 0L) 0f else charge / maxCharge.toFloat()

												ImGui.progressBar(charged, Vec2(), "${Units.powerToString(charge)}/${Units.powerToString(maxCharge)}s")

												if (poweringState.producedPower > 0L) {

													ImGui.sameLine(0f, ImGui.style.itemInnerSpacing.x)
													ImGui.text("${Units.secondsToString(charge / poweringState.producedPower)}")
												}
											}
										})

										ImGui.treePop()
									}

									if (ImGui.treeNode("Consumers")) {
										powerComponent.poweredParts.forEach({
											val part = it
											val poweredState = shipComponent.getPartState(part)[PoweredPartState::class]

											val power = if (poweredState.requestedPower == 0L) 0f else poweredState.givenPower / poweredState.requestedPower.toFloat()
											ImGui.progressBar(power, Vec2(), "${Units.powerToString(poweredState.givenPower)}/${Units.powerToString(poweredState.requestedPower)}")

											ImGui.sameLine(0f, ImGui.style.itemInnerSpacing.x)
											ImGui.text("${part.part}")
										})

										ImGui.treePop()
									}
								}
							}

							if (ImGui.collapsingHeader("Cargo", 0)) { //TreeNodeFlags.DefaultOpen.i

								CargoType.values().forEach {
									val cargo = it

									val usedVolume = shipComponent.getUsedCargoVolume(cargo)
									val maxVolume = shipComponent.getMaxCargoVolume(cargo)
									val usedMass = shipComponent.getUsedCargoMass(cargo)
									val usage = if (maxVolume == 0L) 0f else usedVolume / maxVolume.toFloat()
									ImGui.progressBar(usage, Vec2(), "$usedMass kg, ${usedVolume / 1000}/${maxVolume / 1000} m³")

									ImGui.sameLine(0f, ImGui.style.itemInnerSpacing.x)
									ImGui.text("${cargo.name}")

									if (cargo == CargoType.AMMUNITION) {
										for (entry in shipComponent.munitionCargo.entries) {
											ImGui.text("${entry.value} ${entry.key}")
										}
									}
								}

								ImGui.separator();

								if (ImGui.beginCombo("", addResource.name)) { // The second parameter is the label previewed before opening the combo.
									for (resource in Resource.values()) {
										val isSelected = addResource == resource

										if (ImGui.selectable(resource.name, isSelected)) {
											addResource = resource
										}

										if (isSelected) { // Set the initial focus when opening the combo (scrolling + for keyboard navigation support in the upcoming navigation branch)
											ImGui.setItemDefaultFocus()
										}
									}
									ImGui.endCombo()
								}

								ImGui.inputScalar("kg", imgui.DataType.Int, ::addResourceAmount, 10, 100, "%d", 0)

								if (ImGui.button("Add")) {
									system.lock.write {
										if (!shipComponent.addCargo(addResource, addResourceAmount.toLong())) {
											println("Cargo does not fit")
										}
									}
								}

								ImGui.sameLine(0f, ImGui.style.itemInnerSpacing.x)

								if (ImGui.button("Remove")) {
									system.lock.write {
										if (shipComponent.retrieveCargo(addResource, addResourceAmount.toLong()) != addResourceAmount.toLong()) {
											println("Does not have enough of specified cargo")
										}
									}
								}

								if (ImGui.treeNode("Each resource")) {
									Resource.values().forEach {
										val resource = it

										val usedVolume = shipComponent.getUsedCargoVolume(resource)
										val maxVolume = shipComponent.getMaxCargoVolume(resource)
										val usedMass = shipComponent.getUsedCargoMass(resource)
										val usage = if (maxVolume == 0L) 0f else usedVolume / maxVolume.toFloat()
										ImGui.progressBar(usage, Vec2(), "$usedMass kg, ${usedVolume / 1000}/${maxVolume / 1000} m³")

										ImGui.sameLine(0f, ImGui.style.itemInnerSpacing.x)
										ImGui.text("${resource.name}")
									}

									ImGui.treePop()
								}
							}
						}

					}
				}

			}
			ImGui.end()
		}
	}
	
	private var commandMenuOpen = false;
	private var commandMenuClose = false;
	
	fun openCommandMenu() {
		commandMenuOpen = true
		commandMenuClose = false
	}
	
	fun closeCommandMenu() {
		commandMenuOpen = false
		commandMenuClose = true
	}
	
	private fun commandMenu() {
		
		if (commandMenuOpen) {
			ImGui.openPopup("CommandMenu");
			commandMenuOpen = false
		}
		
		if (BeginPiePopup("CommandMenu", 1)) {
			
			if (commandMenuClose) {
				ImGui.closeCurrentPopup();
				commandMenuClose = false
			}
			
			if (PieMenuItem("Test1")) {
//					commandMenuVisible = false
				println("1")
			}
			
			if (PieMenuItem("Test2")) {println("2")}

			if (PieMenuItem("Test3", false)) {println("3")}

			if (BeginPieMenu("Sub")) {
				
				if (BeginPieMenu("Sub sub\nmenu")) {
					if (PieMenuItem("SubSub")) {println("subsub1")}
					if (PieMenuItem("SubSub2")) {println("subsub2")}
					
					if (BeginPieMenu("Sub sub\nmenu")) {
					if (PieMenuItem("SubSub")) {println("subsub1")}
					if (PieMenuItem("SubSub2")) {println("subsub2")}
					if (BeginPieMenu("Sub sub\nmenu")) {
					if (PieMenuItem("SubSub")) {println("subsub1")}
					if (PieMenuItem("SubSub2")) {println("subsub2")}
					if (BeginPieMenu("Sub sub\nmenu")) {
					if (PieMenuItem("SubSub")) {println("subsub1")}
					if (PieMenuItem("SubSub2")) {println("subsub2")}
					
					EndPieMenu();
				}
					EndPieMenu();
				}
					EndPieMenu();
				}
					
					EndPieMenu();
				}
				
				if (PieMenuItem("TestSub")) {println("sub1")}
				if (PieMenuItem("TestSub2")) {println("sub2")}
				
				EndPieMenu();
			}

			EndPiePopup();
		}
	}

	override fun resize(width: Int, height: Int) {}

	override fun update(deltaRealTime: Float) {}

	fun keyAction(action: KeyActions_ImGuiScreen): Boolean {

		if (action == KeyActions_ImGuiScreen.DEBUG) {
			mainDebugVisible = !mainDebugVisible;
			return true;
		}

		return false
	}

	override fun keyDown(keycode: Int): Boolean {
		
		if (keycode == Input.Keys.NUM_1) {
			closeCommandMenu()
			return true;
		}

		if (ctx.io.wantCaptureKeyboard) {
			gdxGLFWKeyMap[keycode]?.apply {
				ctx.setCurrent()
				ImplGlfw.keyCallback(this, 0, GLFW.GLFW_PRESS, 0)
			}
			return true
		}

		val action = KeyMappings.getRaw(keycode, ImGuiScreen::class)

		if (action != null) {
			return keyAction(action as KeyActions_ImGuiScreen)
		}

		return false
	}

	override fun keyUp(keycode: Int): Boolean {
		if (ctx.io.wantCaptureKeyboard) {
			gdxGLFWKeyMap[keycode]?.apply {
				ctx.setCurrent()
				ImplGlfw.keyCallback(this, 0, GLFW.GLFW_RELEASE, 0)
			}
			return true
		}

		return false
	}

	override fun keyTyped(character: Char): Boolean {
		if (ctx.io.wantCaptureKeyboard) {
			ctx.setCurrent()
			ImplGlfw.charCallback(character.toInt())
			return true
		}

		val action = KeyMappings.getTranslated(character, ImGuiScreen::class)

		if (action != null) {
			return keyAction(action as KeyActions_ImGuiScreen)
		}

		return false
	}

	override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
		return ctx.io.wantCaptureMouse
	}

	override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
		return ctx.io.wantCaptureMouse
	}

	override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
		return ctx.io.wantCaptureMouse
	}

	override fun scrolled(amount: Int): Boolean {
		if (ctx.io.wantCaptureMouse) {
			ctx.setCurrent()
			ImplGlfw.scrollCallback(Vec2d(0, -amount))
			return true
		}

		return false
	}

	override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
		return false
	}

	override fun dispose() {
		ctx.setCurrent()
		gl3.shutdown()
		lwjglGlfw.shutdown()
		ctx.shutdown();
		super.dispose()
	}
}
