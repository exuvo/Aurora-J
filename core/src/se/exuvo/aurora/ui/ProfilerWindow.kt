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
import net.mostlyoriginal.api.utils.pooling.PoolsCollection
import net.mostlyoriginal.api.utils.pooling.ObjectPool
import se.exuvo.aurora.starsystems.systems.ProfilingSystemInvocationStrategy
import se.exuvo.aurora.utils.callPrivateFunc
import se.exuvo.aurora.starsystems.systems.CustomSystemInvocationStrategy

// Inspiration https://bitbucket.org/wolfpld/tracy/src/master/
class ProfilerWindow : UIWindow() {
	companion object {
		const val BAG_SIZE = 1024
	}
	
	var system: StarSystem? = null
	var zoom = 0.001f
	var xScroll = 0L
	
	val systemEvents = ProfilerBag()
	val renderEvents = ProfilerBag()

	override var visible: Boolean
		get() = super.visible
		set(value) {
			
			val systemScreen = AuroraGame.currentWindow.screenService(StarSystemScreen::class)
				
			if (systemScreen != null) {
				
				val oldSystem = system
				
				if (oldSystem != null) {
					oldSystem.world.callPrivateFunc("setInvocationStrategy", CustomSystemInvocationStrategy())
				}
				
				val system = systemScreen.system
				val world = system.world
				
				if (value) {
					world.callPrivateFunc("setInvocationStrategy", ProfilingSystemInvocationStrategy(this))
					
				} else {
					world.callPrivateFunc("setInvocationStrategy", CustomSystemInvocationStrategy())
				}
				
				this.system = system
			}
			
			super.visible = value
		}
	
	override fun draw() {
		if (visible) {
			with(ImGui) {
				with(imgui.dsl) {
					
					val itemSpaceX = style.itemSpacing.x
					val itemSpaceY = style.itemSpacing.y
					
					window("Profiler", ::visible, WindowFlag.HorizontalScrollbar.i) {
						
//						child("Timeline", Vec2(), true, WindowFlag.HorizontalScrollbar.i) {
//							child("Time", Vec2(0, 50), false, WindowFlag.None.i) {
								text("TODO time")
//							}
							
//							child("StarSystem", Vec2(0, 200), false, WindowFlag.None.i) {
								
								val system = system
								if (system != null) {
									system.lock.read {
										
										text("Star System ${system.sid} - ${systemEvents.size()} events")
										
										val window = currentWindow
										
										var size = systemEvents.size()
										if (size > 0) {
											
//											run { // text debug
//												var x = cursorPosX
//												
//												for(item in systemEvents) {
//													cursorPosX = x
//													text("$item")
//													x += if (item.name != null) 10 else -10 
//												}
//											}
											
											val backupPaddingY = style.framePadding.y
											style.framePadding.y = 0f
											
											val timeOffset = systemEvents.data[0].time
											var x = window.dc.cursorPos.x
											var y = window.dc.cursorPos.y
											var maxY = y
											
											fun eventBar(x: Float, y: Float, start: Long, end: Long, name: String): Boolean {
												val id = window.getId("event")
												val pos = Vec2(x + start.toFloat() * zoom, y)
												val size = Vec2((end - start).toFloat() * zoom, ctx.fontSize + 1)
												val bb = Rect(pos, pos + size)
												val labelSize = calcTextSize(name, false)
												
												itemSize(size)
												if (!itemAdd(bb, id)) return false
												
												val flags = 0
												val (pressed, hovered, held) = buttonBehavior(bb, id, flags)
												
												//Render
												val col = if (hovered) Col.ButtonHovered.u32 else Vec4(0.3f, 0.5f, 0.3f, 1f).u32
												renderNavHighlight(bb, id)
												renderFrame(bb.min, bb.max, col, true, 1f)
												renderTextClipped(bb.min.plus(1f, -1f), bb.max - 1, name, -1, labelSize, Vec2(0f, 0.5f), bb)
					
												if (y + size.y > maxY) {
													maxY = y + size.y
												}
												
												return hovered
											}
											
											fun drawEvents(i: Int): Int {
												var i = i
												val startEvent = systemEvents.data[i++]
												
												var endEvent = systemEvents.data[i]
												
												while (endEvent.name != null) {
													y += 15
													i = drawEvents(i)
													y -= 15
													endEvent = systemEvents.data[i]
												}
												
												val name = startEvent.name ?: "null"
												
												if (eventBar(x, y, startEvent.time - timeOffset, endEvent.time - timeOffset, name)) {
													setTooltip("$name ${Units.nanoToMicroString(endEvent.time - startEvent.time)}")
												}
												
												return i + 1
											}
											
											var i = 0;
											
											while(i < size - 1) {
												i = drawEvents(i)
											}
											
											window.dc.cursorPos.y = maxY
											window.dc.cursorMaxPos.y = maxY
											
											style.framePadding.y = backupPaddingY
										}
									}
								}
//							}
							
//							child("Renderer", Vec2(0, 100), false, WindowFlag.None.i) {
								text("Renderer ${renderEvents.size()} events")
//							}
//						}
						
					}
				}
			}
		}
	}
	
	inner class ProfilerBag : Bag<ProfilerEvent>(ProfilerEvent::class.java, BAG_SIZE) {
		val eventPool = EventPool()
		
		fun start(time: Long, name: String) {
			add(eventPool.obtain().start(time, name))
		}
		
		fun start(name: String) {
			add(eventPool.obtain().start(System.nanoTime(), name))
		}
		
		fun end(time: Long = System.nanoTime()) {
			add(eventPool.obtain().end(time))
		}
		
		override fun clear() {
			
			this.forEachFast { event ->
				eventPool.free(event)
			}
			
			super.clear()
		}
	}

	class ProfilerEvent() {
		
		var time: Long = 0
		var name: String? = null // Set to start event, null to end previous
		
		fun start(time: Long = System.nanoTime(), name: String): ProfilerEvent {
			this.time = time
			this.name = name
			
			return this
		}
		
		fun end(time: Long = System.nanoTime()): ProfilerEvent {
			this.time = time
			this.name = null
			
			return this
		}
		
		override fun toString() = "$time $name"
	}
	
	class EventPool : ObjectPool<ProfilerEvent>(ProfilerEvent::class.java) {
		override fun instantiateObject(): ProfilerWindow.ProfilerEvent? {
			return ProfilerEvent()
		}
	}
}
