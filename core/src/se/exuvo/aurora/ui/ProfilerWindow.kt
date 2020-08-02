package se.exuvo.aurora.ui

import com.artemis.utils.Bag
import glm_.vec2.Vec2
import imgui.Col
import imgui.ImGui
import imgui.WindowFlag
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.utils.forEachFast
import kotlin.concurrent.read
import se.exuvo.aurora.AuroraGame
import se.exuvo.aurora.starsystems.StarSystem
import imgui.internal.classes.Rect
import glm_.vec4.Vec4
import imgui.u32
import se.exuvo.aurora.ui.UIScreen.UIWindow
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
					
//					val itemSpaceX = style.itemSpacing.x
//					val itemSpaceY = style.itemSpacing.y
					
					window("Profiler", ::visible, WindowFlag.HorizontalScrollbar.i) {
						
						text("TODO time")
						
						val system = system
						if (system != null) {
							system.lock.read {
								
								text("Star System ${system.sid} - ${systemEvents.size()} events")
								
								val window = currentWindow
								
								var size = systemEvents.size()
								if (size > 0) {
									
									val backupPaddingY = style.framePadding.y
									style.framePadding.y = 0f
									
									val timeOffset = systemEvents.data[0].time
									var x = window.dc.cursorPos.x
									var y = window.dc.cursorPos.y
									var maxY = y
									
									fun eventBar(x: Float, y: Float, start: Long, end: Long, name: String): Boolean {
										val id = window.getID("event")
										val pos = Vec2(x + start.toFloat() * zoom, y)
										val itemSize = Vec2((end - start).toFloat() * zoom, ctx.fontSize + 1)
										val bb = Rect(pos, pos + itemSize)
										val labelSize = calcTextSize(name, false)
										
										itemSize(itemSize)
										if (!itemAdd(bb, id)) return false
										
										val flags = 0
										val (pressed, hovered, held) = buttonBehavior(bb, id, flags)
										
										//Render
										val col = if (hovered) Col.ButtonHovered.u32 else Vec4(0.3f, 0.5f, 0.3f, 1f).u32
										renderNavHighlight(bb, id)
										renderFrame(bb.min, bb.max, col, true, 1f)
										//TODO maybe change
										renderTextClipped(bb.min.plus(1f, -1f), bb.max - 1, name, labelSize, Vec2(0f, 0.5f), bb)
			
										if (y + itemSize.y > maxY) {
											maxY = y + itemSize.y
										}
										
										return hovered
									}
									
									fun drawEvents(i: Int): Int {
										var j = i
										val startEvent = systemEvents.data[j++]
										
										var endEvent = systemEvents.data[j]
										
										while (endEvent.name != null) {
											y += 15
											j = drawEvents(j)
											y -= 15
											endEvent = systemEvents.data[j]
										}
										
										val name = startEvent.name ?: "null"
										
										if (eventBar(x, y, startEvent.time - timeOffset, endEvent.time - timeOffset, name)) {
											setTooltip("$name ${Units.nanoToMicroString(endEvent.time - startEvent.time)}")
										}
										
										return j + 1
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
					
						text("Renderer ${renderEvents.size()} events")
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
