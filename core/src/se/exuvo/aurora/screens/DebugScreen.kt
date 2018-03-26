package se.exuvo.aurora.screens

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics
import glm_.vec2.Vec2
import glm_.vec2.Vec2d
import imgui.Context
import imgui.ImGui
import imgui.WindowFlags
import imgui.impl.LwjglGL3
import org.apache.log4j.Logger
import org.lwjgl.glfw.GLFW
import se.exuvo.aurora.Assets
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.planetarysystems.components.OrbitComponent
import se.exuvo.aurora.planetarysystems.components.ThrustComponent
import se.exuvo.aurora.planetarysystems.systems.GroupSystem
import se.exuvo.aurora.utils.GameServices
import uno.glfw.GlfwWindow
import se.exuvo.aurora.planetarysystems.components.ShipComponent
import se.exuvo.aurora.planetarysystems.components.PowerComponent
import se.exuvo.aurora.utils.printID
import se.exuvo.aurora.planetarysystems.systems.RenderSystem
import se.exuvo.aurora.planetarysystems.components.SolarIrradianceComponent

class DebugScreen : GameScreenImpl(), InputProcessor {

	val log = Logger.getLogger(this.javaClass)

	private val galaxy by lazy { GameServices[Galaxy::class.java] }
	private val galaxyGroupSystem by lazy { GameServices[GroupSystem::class.java] }

	private val nameMapper = ComponentMapper.getFor(NameComponent::class.java)
	private val shipMapper = ComponentMapper.getFor(ShipComponent::class.java)
	private val powerMapper = ComponentMapper.getFor(PowerComponent::class.java)
	private val orbitMapper = ComponentMapper.getFor(OrbitComponent::class.java)
	private val thrustMapper = ComponentMapper.getFor(ThrustComponent::class.java)
	private val irradianceMapper = ComponentMapper.getFor(SolarIrradianceComponent::class.java)

	private val uiCamera = GameServices[GameScreenService::class.java].uiCamera

	override val overlay = true
	private val ctx: Context
	private val gdxGLFWKeyMap = mutableMapOf<Int, Int>()

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

		ctx = Context()
		LwjglGL3.init(GlfwWindow((Gdx.graphics as Lwjgl3Graphics).window.windowHandle), false)
	}

	override fun show() {}

	private var demoVisible = false
	private var mainDebugVisible = false
	private var shipDebugVisible = true
	
	var slider = FloatArray(1)
	var stringbuf = CharArray(10)
	var img = Assets.textures.findRegion("strategic/sun")
	var menuBarState = BooleanArray(1)
	var graphValues = floatArrayOf(0f, 5f, 2f, 4f)

	override fun draw() {

		// https://github.com/kotlin-graphics/imgui/wiki/Using-libGDX
		// https://github.com/ocornut/imgui

		try {
			LwjglGL3.newFrame()

			if (demoVisible) {
				var windowClose = booleanArrayOf(demoVisible)
				ImGui.showDemoWindow(windowClose)
				demoVisible = windowClose[0]
			}
			
			if (mainDebugVisible) {
				var windowClose = booleanArrayOf(mainDebugVisible)

				if (ImGui.begin("Debug window", windowClose, WindowFlags.MenuBar.i)) {

					if (ImGui.beginMenuBar()) {
						if (ImGui.beginMenu("Windows")) {
							if (ImGui.menuItem("Ship debug", "", shipDebugVisible)) {
								shipDebugVisible = !shipDebugVisible
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
					ImGui.plotLines("plot", graphValues)

					if (ImGui.button("OK")) {
						println("click")
					}

					ImGui.inputText("string", stringbuf)
					ImGui.sliderFloat("float", slider, 0f, 1f)
					ImGui.image(img.getTexture().textureObjectHandle, Vec2(64, 64))
				}
				ImGui.end()

				mainDebugVisible = windowClose[0]
			}
			
			shipDebug()

			ImGui.render()

			if (ImGui.drawData != null) {
				LwjglGL3.renderDrawData(ImGui.drawData!!)
			}
		} catch (e: Throwable) {
			log.error("Error drawing debug window", e)
		}
	}

	var lastDebugTime = 0L
	var powerAvailiableValues = FloatArray(60)
	var powerRequestedValues = FloatArray(60)
	var powerUsedValues = FloatArray(60)
	var arrayIndex = 0
	private fun shipDebug() {
		
		if (shipDebugVisible) {
			var windowClose = booleanArrayOf(shipDebugVisible)

			if (ImGui.begin("Ship debug", windowClose, WindowFlags.AlwaysAutoResize.i)) {

				val selectedEntities = galaxyGroupSystem.get(GroupSystem.SELECTED)

				if (selectedEntities.isEmpty()) {
					ImGui.text("Nothing selected")
					
				} else {

					val entity = selectedEntities.iterator().next()
					val shipComponent = shipMapper.get(entity)
					
					ImGui.text("Entity ${entity.printID()}")

					if (shipComponent != null) {

						val powerComponent = powerMapper.get(entity)

						if (powerComponent != null) {

							ImGui.separator()
							ImGui.text("Power")
							
							val now = System.currentTimeMillis()
							
							if (now - lastDebugTime > 500) {
								lastDebugTime = now
								
								powerAvailiableValues[arrayIndex] = powerComponent.totalAvailiablePower.toFloat()
								powerRequestedValues[arrayIndex] = powerComponent.totalRequestedPower.toFloat()
								powerUsedValues[arrayIndex] = powerComponent.totalUsedPower.toFloat()
								arrayIndex++
								
								if (arrayIndex >= 60) {
									arrayIndex = 0
								}
							}
							
							ImGui.plotLines("AvailiablePower", {powerAvailiableValues[(arrayIndex + it) % 60]}, 60, 0, "", 0f, Float.MAX_VALUE, Vec2(0, 50))
							ImGui.plotLines("RequestedPower", {powerRequestedValues[(arrayIndex + it) % 60]}, 60, 0, "", 0f, Float.MAX_VALUE, Vec2(0, 50))
							ImGui.plotLines("UsedPower", {powerUsedValues[(arrayIndex + it) % 60]}, 60, 0, "", 0f, Float.MAX_VALUE, Vec2(0, 50))
						}
						
						val solarIrradiance = irradianceMapper.get(entity)
						
						if (solarIrradiance != null) {
							
							ImGui.separator()
							ImGui.text("Solar irradiance ${solarIrradiance.irradiance} W/m2")
						}
					}
				}


			}
			ImGui.end()

			shipDebugVisible = windowClose[0]
		}
	}

	override fun resize(width: Int, height: Int) {}

	override fun update(deltaRealTime: Float) {}

	override fun keyDown(keycode: Int): Boolean {

		if (keycode == Input.Keys.GRAVE) {
			mainDebugVisible = !mainDebugVisible;
			return true;
		}

		if (mainDebugVisible) {
			gdxGLFWKeyMap[keycode]?.apply {
				LwjglGL3.keyCallback(this, 0, GLFW.GLFW_PRESS, 0)
			}
		}

		return mainDebugVisible && ctx.navWindow != null
	}

	override fun keyUp(keycode: Int): Boolean {
		if (mainDebugVisible) {
			gdxGLFWKeyMap[keycode]?.apply {
				LwjglGL3.keyCallback(this, 0, GLFW.GLFW_RELEASE, 0)
			}
		}

		return mainDebugVisible && ctx.navWindow != null
	}

	override fun keyTyped(character: Char): Boolean {
		if (mainDebugVisible) {
			LwjglGL3.charCallback(character.toInt())
		}

		return mainDebugVisible && ctx.navWindow != null
	}

	// Seems to read mouse state every frame
	override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
		return mainDebugVisible && ctx.navWindow != null
	}

	// Seems to read mouse state every frame
	override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
		if (mainDebugVisible) {
//			LwjglGL3.mouseButtonCallback(button, GLFW.GLFW_PRESS, 0)
		}

		return mainDebugVisible && ctx.navWindow != null
	}

	override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
		return mainDebugVisible && ctx.navWindow != null
	}

	override fun scrolled(amount: Int): Boolean {
		if (mainDebugVisible) {
			LwjglGL3.scrollCallback(Vec2d(0, -amount))
		}

		return mainDebugVisible && ctx.navWindow != null
	}

	// Seems to read mouse pos every frame
	override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
		return false
	}

	override fun dispose() {
		LwjglGL3.shutdown()
		ctx.shutdown();
		super.dispose()
	}
}
