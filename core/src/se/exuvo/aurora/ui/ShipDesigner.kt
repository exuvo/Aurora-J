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

class ShipDesigner : UIWindow() {

	var hull: ShipHull = ShipHull()
	
	override fun draw() {
		if (visible) {
			with(ImGui) {
				with(imgui.dsl) {
					
					val itemSpaceX = style.itemSpacing.x
					val itemSpaceY = style.itemSpacing.y
					
					window("Ship designer - $hull", ::visible, WindowFlag.None.i) {
						
						child("Tabs", Vec2(0, -80 - itemSpaceY), false, WindowFlag.None.i) {
							
							if (beginTabBar("Tabs", TabBarFlag.Reorderable or TabBarFlag.TabListPopupButton or TabBarFlag.FittingPolicyResizeDown)) {
								
								if (beginTabItem("Summary")) {
									
									text("hull selection, name, classification")
									text("summary")
									
									endTabItem()
								}
								
								if (beginTabItem("Parts")) {
									
									leftInfo()

									sameLine()

									child("middle", Vec2(-200 - itemSpaceX, 0), true, WindowFlag.None.i) {
										val height = currentWindow.contentRegionRect.height - itemSpaceY
										child("available", Vec2(0, height * 0.7f), false, WindowFlag.None.i) {
											text("parts")
										}
										child("summary", Vec2(0, height * 0.3f), false, WindowFlag.None.i) {
											text("buttons")
											text("summary")
										}
									}
									
									sameLine()
									
									child("right", Vec2(200, 0), true, WindowFlag.None.i) {
										text("errors, selected parts")
									}
									
									endTabItem()
								}
								
								if (beginTabItem("Preferred cargo")) {
									
									text("cargo, ammo, fighters")
									text("link weapons and ammo")
									
									endTabItem()
								}
								
								endTabBar()
							}
						}
						
						child("Bottom", Vec2(0, 80), true, WindowFlag.None.i) {
							text("bottom")
						}
					}
				}
			}
		}
	}

	fun leftInfo() {
		with(ImGui) {
			with(imgui.dsl) {
				
				child("Left", Vec2(200, 0), true, WindowFlag.None.i) {
					text("left")
				}
			}
		}
	}
}
