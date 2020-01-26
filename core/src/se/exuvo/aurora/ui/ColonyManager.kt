package se.exuvo.aurora.ui

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
import se.exuvo.aurora.galactic.Resource
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.starsystems.components.AmmunitionPartState
import se.exuvo.aurora.starsystems.components.ChargedPartState
import se.exuvo.aurora.starsystems.components.FueledPartState
import se.exuvo.aurora.starsystems.components.PassiveSensorState
import se.exuvo.aurora.starsystems.components.PowerComponent
import se.exuvo.aurora.starsystems.components.PoweredPartState
import se.exuvo.aurora.starsystems.components.PoweringPartState
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.components.SolarIrradianceComponent
import se.exuvo.aurora.starsystems.components.TargetingComputerState
import se.exuvo.aurora.starsystems.systems.GroupSystem
import se.exuvo.aurora.starsystems.systems.RenderSystem
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.ui.keys.KeyActions_UIScreen
import se.exuvo.aurora.ui.keys.KeyMappings
import se.exuvo.aurora.utils.printID
import se.exuvo.aurora.utils.isNotEmpty
import se.exuvo.aurora.utils.forEachFast
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
import se.exuvo.aurora.starsystems.components.EntityUUID
import se.exuvo.aurora.empires.components.ColonyComponent
import se.exuvo.aurora.starsystems.components.NameComponent
import se.exuvo.aurora.starsystems.components.EntityReference
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
import se.exuvo.aurora.empires.components.ShipyardModificationAddSlipway
import se.exuvo.aurora.empires.components.ShipyardModificationExpandCapacity
import se.exuvo.aurora.galactic.BeamWeapon
import se.exuvo.aurora.galactic.Railgun
import se.exuvo.aurora.galactic.MissileLauncher
import se.exuvo.aurora.starsystems.systems.WeaponSystem
import org.apache.commons.math3.util.FastMath
import se.exuvo.aurora.galactic.AdvancedMunitionHull
import se.exuvo.aurora.galactic.SimpleMunitionHull
import com.artemis.annotations.Wire
import se.exuvo.aurora.starsystems.StarSystem
import imgui.DataType
import imgui.internal.classes.Rect
import imgui.ImGui.renderNavHighlight
import glm_.vec4.Vec4
import imgui.u32
import se.exuvo.aurora.galactic.DamagePattern
import se.exuvo.aurora.galactic.PartRef
import se.exuvo.aurora.galactic.Part
import se.exuvo.aurora.ui.UIScreen.UIWindow

class ColonyManager : UIWindow() {
	
	var selectedColony: EntityReference? = null
	var selectedShipyard: Shipyard? = null
	var selectedSlipway: ShipyardSlipway? = null
	var selectedRetoolHull: ShipHull? = null
	var selectedBuildHull: ShipHull? = null
	var selectedShipyardModification: ShipyardModifications? = null
	var shipyardExpandCapacity: Int = 0
	
	override fun draw() {
		
		if (visible) {
			with (ImGui) {
				with (imgui.dsl) {
					
					window("Colony manager", ::visible, WindowFlag.None.i) {
						
						val empire = Player.current.empire!!
						
						if (selectedColony == null && empire.colonies.isNotEmpty()) {
							selectedColony = empire.colonies[0]
						}
						
						// windowContentRegionWidth * 0.3f
						child("Colonies", Vec2(200, 0), true, WindowFlag.None.i) {
							
							collapsingHeader("Colonies ${empire.colonies.size()}", TreeNodeFlag.DefaultOpen.i) {
								
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
													
													val daysToCompletion = (modActivity.getCost(shipyard) - shipyard.modificationProgress) / (24 * shipyard.modificationRate)
													
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
														}
														
														if (hull != null) {
															sameLineRightAlignedColumnText(Units.massToString(hull.emptyMass))
															nextColumn()
															rightAlignedColumnText(Units.volumeToString(hull.volume))
															nextColumn()
															text("${hull}")
															nextColumn()
															text("Building")
															nextColumn()
															text("${slipway.progress()}%")
															nextColumn()
															val daysToCompletion = (slipway.totalCost() - slipway.usedResources()) / (24 * shipyard.buildRate)
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
												
												when (selectedShipyardModification) {
													ShipyardModifications.RETOOL -> {
														
														if (beginCombo("Retool Hull", if (selectedRetoolHull == null) "-" else selectedRetoolHull.toString(), 0)) {
														
															empire.shipHulls.forEach { empireHull ->
																if (empireHull != shipyard.tooledHull && empireHull.parentHull == null) {
																	val selected = empireHull == selectedRetoolHull
																	
																	if (selectable(empireHull.toString(), selected)) {
																		selectedRetoolHull = empireHull
																	}
																	
																	if (selected) {
																		setItemDefaultFocus()
																	}
																}
															}
															
															endCombo()
														}
														
														val valid = selectedRetoolHull != null && selectedRetoolHull != shipyard.tooledHull
														
														if (!valid) {
															pushItemFlag(ItemFlag.Disabled.i, true)
															pushStyleVar(StyleVar.Alpha, style.alpha * 0.5f)
														}
														
														if (button("Retool") && valid) {
															shipyard.modificationActivity = ShipyardModificationRetool(selectedRetoolHull as ShipHull)
															shipyard.modificationProgress = 0
														}
		
														if (!valid) {
															popItemFlag()
															popStyleVar()
														}
													}
													ShipyardModifications.EXPAND_CAPACITY -> {
														
														inputInt("Expand capacity by", ::shipyardExpandCapacity)
														
														val valid = shipyardExpandCapacity > 0
														
														if (!valid) {
															pushItemFlag(ItemFlag.Disabled.i, true)
															pushStyleVar(StyleVar.Alpha, style.alpha * 0.5f)
														}
														
														if (button("Expand") && valid) {
															shipyard.modificationActivity = ShipyardModificationExpandCapacity(shipyardExpandCapacity.toLong())
															shipyard.modificationProgress = 0
														}
		
														if (!valid) {
															popItemFlag()
															popStyleVar()
														}
													}
													ShipyardModifications.ADD_SLIPWAY -> {
														if (button("Build")) {
															shipyard.modificationActivity = ShipyardModificationAddSlipway()
															shipyard.modificationProgress = 0
														}
													}
												}
											}
											
											val tooledHull = shipyard.tooledHull
											
											if (tooledHull != null) {
												
												if (beginCombo("Ship Hull", if (selectedBuildHull == null) "-" else selectedBuildHull.toString(), 0)) {
													
													empire.shipHulls.forEach { empireHull ->
														if (empireHull == tooledHull || empireHull.parentHull == tooledHull) {
															val selected = empireHull == selectedBuildHull
															
															if (selectable(empireHull.toString(), selected)) {
																selectedBuildHull = empireHull
															}
															
															if (selected) {
																setItemDefaultFocus()
															}
														}
													}
													
													endCombo()
												}
												
												var freeSlipway: ShipyardSlipway? = null
												
												for (shipyardSlipway in shipyard.slipways) {
													if (shipyardSlipway.hull == null) {
														freeSlipway = shipyardSlipway
														break
													}
												}
												
												val valid = selectedBuildHull != null && freeSlipway != null
												
												if (!valid) {
													pushItemFlag(ItemFlag.Disabled.i, true)
													pushStyleVar(StyleVar.Alpha, style.alpha * 0.5f)
												}
												
												if (button("Build") && valid) {
													(freeSlipway as ShipyardSlipway).build(selectedBuildHull as ShipHull)
												}

												if (!valid) {
													popItemFlag()
													popStyleVar()
												}
											}
										}
										
										endTabItem()
									}
									
									if (beginTabItem("Industry")) {
										
										text("Munitions:")
										group {
											colony.munitions.forEach { hull, _ ->
												text(hull.name)
											}
										}
										sameLine()
										group {
											var maxWidth = 0f
											colony.munitions.forEach { _, amount ->
												maxWidth = kotlin.math.max(maxWidth, calcTextSize(amount.toString()).x)
											}
											colony.munitions.forEach { _, amount ->
												val string = amount.toString()
												ImGui.cursorPosX = ImGui.cursorPosX + maxWidth - calcTextSize(string).x - ImGui.scrollX
												text(string)
											}
										}
										
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
}