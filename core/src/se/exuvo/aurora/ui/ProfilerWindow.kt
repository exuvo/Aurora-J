package se.exuvo.aurora.ui

import com.artemis.utils.Bag
import glm_.vec2.Vec2
import imgui.Col
import imgui.ImGui
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.AuroraGame
import se.exuvo.aurora.starsystems.StarSystem
import imgui.internal.classes.Rect
import glm_.vec4.Vec4
import imgui.DataType
import imgui.MouseButton
import imgui.WindowFlag
import imgui.u32
import se.exuvo.aurora.ui.UIScreen.UIWindow
import net.mostlyoriginal.api.utils.pooling.ObjectPool
import org.apache.commons.math3.util.FastMath
import se.exuvo.aurora.starsystems.ProfilingSystemInvocationStrategy
import se.exuvo.aurora.starsystems.CustomSystemInvocationStrategy
import se.exuvo.aurora.utils.clamp
import se.exuvo.aurora.utils.toLinearRGB

// Inspiration https://bitbucket.org/wolfpld/tracy/src/master/
class ProfilerWindow : UIWindow() {
	companion object {
		const val BAG_SIZE = 1024
		const val ZOOM_MIN = 0.001f
		const val ZOOM_MAX = 0.05f
	}
	
	var system: StarSystem? = null
	var zoom = 0.01f
	var systemsScroll = 0L
	var oldSystemsScroll = systemsScroll
	
	var renderScroll = 0L
	var oldRenderScroll = renderScroll
	
	override var visible: Boolean
		get() = super.visible
		set(value) {
			
			val systemScreen = AuroraGame.currentWindow.screenService(StarSystemScreen::class)
				
			if (systemScreen != null) {
				
				val oldSystem = system
				
				if (oldSystem != null) {
					oldSystem.nextInvocationStrategy = CustomSystemInvocationStrategy(oldSystem)
				}
				
				val system = systemScreen.system
				val world = system.world
				
				if (value) {
					system.nextInvocationStrategy = ProfilingSystemInvocationStrategy(system)
					
				} else {
					system.nextInvocationStrategy = CustomSystemInvocationStrategy(system)
				}
				
				this.system = system
			}
			
			super.visible = value
		}
	
	override fun draw() {
		if (visible) {
			with(ImGui) {
				with(imgui.dsl) {
					
					window("Profiler", ::visible, 0) {
						
						sliderScalar("Zoom", DataType.Float, ::zoom, ZOOM_MIN, ZOOM_MAX, zoom.toString(), 1.0f)
						
						textUnformatted("TODO time")
						
						val backupPaddingY = style.framePadding.y
						
						var x = 0f
						var y = 0f
						var maxY = 0f
						var timeOffset = 0L
						var scroll = 0L
						
						fun eventBar(start: Long, end: Long, name: String): Boolean {
							val x1 = x + (start.toFloat() - scroll) * zoom
							val width = (end - start).toFloat() * zoom
							
							if (x1 < currentWindow.size.x || x1 + width >= 0) {
								
								val id = currentWindow.getID("event $start")
								val pos = Vec2(x1, y)
								val size = Vec2(width, ctx.fontSize + 1)
								val bb = Rect(pos, pos + size)
								val labelSize = calcTextSize(name, false)
								
//								itemSize(size)
								if (!itemAdd(bb, id)) return false
								
								val (pressed, hovered, held) = buttonBehavior(bb, id, 0)
								
								//Render
								val col = if (hovered) Col.ButtonHovered.u32 else Vec4(0.3f, 0.5f, 0.3f, 1f).toLinearRGB().u32
								renderNavHighlight(bb, id)
								renderFrame(bb.min, bb.max, col, true, 1f)
								//TODO maybe change
								renderTextClipped(bb.min.plus(1f, -1f), bb.max - 1, name, labelSize, Vec2(0f, 0.5f), bb)
								
								if (y + size.y > maxY) {
									maxY = y + size.y
								}
								
								return hovered
							}
							
							return false
						}
						
						fun drawEvents(i: Int, events: ProfilerBag): Int {
							var j = i
							val startEvent = events.data[j++]
							
							var endEvent = events.data[j]
							
							while (endEvent.name != null) {
								y += 15
								j = drawEvents(j, events)
								y -= 15
								endEvent = events.data[j]
							}
							
							val name = startEvent.name ?: "null"
							
							if (eventBar(startEvent.time - timeOffset, endEvent.time - timeOffset, name)) {
								setTooltip("$name ${Units.nanoToMicroString(endEvent.time - startEvent.time)}")
							}
							
							return j + 1
						}
						
						fun drawEvents(events: ProfilerBag) {
							var size = events.size()
							
							if (size > 0) {
								if (size % 2 == 0) {
									style.framePadding.y = 0f
									x = currentWindow.dc.cursorPos.x
									y = currentWindow.dc.cursorPos.y
									maxY = y
									
									var i = 0;
									
									while (i < size - 1) {
										i = drawEvents(i, events)
									}
									
									style.framePadding.y = backupPaddingY
									
									val pos = Vec2(x, y)
									val size = Vec2(currentWindow.size.x, maxOf(maxY - y, 32f))
									itemSize(size)
									
									val bb = Rect(pos, pos + size)
									renderFrame(bb.min, bb.max, Vec4(0.5f, 0.5f, 0.5f, 0.1f).toLinearRGB().u32, true, 1f)
									
									val id = currentWindow.getID("events ${System.identityHashCode(events)}")
									if (itemAdd(bb, id)) {
										val (pressed, hovered, held) = buttonBehavior(bb, id, 0)
									}
								
								} else {
									textColored(Vec4(1f, 0f, 0f, 1f), "Event size is not even!")
								}
							}
						}
						
						fun mouseScroll(scroll: Float) { // negative scroll == zoom out
							val oldZoom = zoom
							val sensitivity = 50f // higher less sensitive
							var zoomRatio = sensitivity * (zoom - ZOOM_MIN) / ZOOM_MAX
							
//							println("a $zoomRatio")
//
							// linear
							zoomRatio = clamp((zoomRatio + scroll) / sensitivity, 0f, 1f)
							zoom = clamp(ZOOM_MIN + (ZOOM_MAX - ZOOM_MIN) * zoomRatio, ZOOM_MIN, ZOOM_MAX)
							
							// power
//							zoomRatio = clamp((zoomRatio + scroll) / sensitivity, 0f, 1f)
//							zoomRatio = FastMath.pow(zoomRatio.toDouble(), 1.1).toFloat()
//							zoom = clamp(ZOOM_MAX * zoomRatio, ZOOM_MIN, ZOOM_MAX)

//							println("b $zoomRatio")
							
							// zoom to/from center = currentWindow.size.x / zoom / 2
							systemsScroll += (systemsScroll + ctx.io.mousePos.x / oldZoom).toLong() - (systemsScroll + ctx.io.mousePos.x / zoom).toLong()
							renderScroll += (renderScroll + ctx.io.mousePos.x / oldZoom).toLong() - (renderScroll + ctx.io.mousePos.x / zoom).toLong()
						}
						
						// or WindowFlag.NoScrollWithMouse.i
						child("render", Vec2(0, 100), false, WindowFlag.NoMove.i) {
							currentWindow.scrollMax.y = 1f
							
							if (isWindowFocused()) {
								if (isMouseDragging(MouseButton.Left)) {
									renderScroll = oldRenderScroll - (getMouseDragDelta(MouseButton.Left).x / zoom).toLong()
								} else {
									oldRenderScroll = renderScroll
								}
							}
							
							if (isWindowHovered() || isWindowFocused()) {
								val scroll = ctx.io.mouseWheel
								
								if (scroll != 0f) {
									mouseScroll(scroll)
								}
							}
							
							val events = galaxy.renderProfilerEvents
							
							if (events.size() > 0) {
								timeOffset = events.data[0].time
								scroll = renderScroll
								
								textUnformatted("Renderer ${events.size()} events")
								drawEvents(events)
							}
						}
						
						child("systems", Vec2(0, 200 * galaxy.systems.size()), false, WindowFlag.NoMove.i) {
							currentWindow.scrollMax.y = 1f
							
							if (isWindowFocused()) {
								if (isMouseDragging(MouseButton.Left)) {
									systemsScroll = oldSystemsScroll - (getMouseDragDelta(MouseButton.Left).x / zoom).toLong()
								} else {
									oldSystemsScroll = systemsScroll
								}
							}
							
							if (isWindowHovered() || isWindowFocused()) {
								val scroll = ctx.io.mouseWheel
								
								if (scroll != 0f) {
									mouseScroll(scroll)
								}
							}
							
							timeOffset = galaxy.shadow.profilerEvents.data[0].time
							scroll = systemsScroll
							
							run {
								val events = galaxy.shadow.profilerEvents
								
								textUnformatted("Galaxy ${events.size()} events")
								drawEvents(events)
							}
							
							galaxy.systems.forEachFast { system ->
								
								val events = system.shadow.profilerEvents
								
								textUnformatted("Star System ${system.sid} - ${events.size()} events")
								drawEvents(events)
							}
						}
						
					}
				}
			}
		}
	}
	
	class ProfilerBag : Bag<ProfilerEvent>(ProfilerEvent::class.java, BAG_SIZE) {
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
