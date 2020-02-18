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

class UIScreen : GameScreenImpl(), InputProcessor {
	companion object {
		@JvmStatic
		val log = LogManager.getLogger(UIScreen::class.java)
	}

	private val galaxy by lazy (LazyThreadSafetyMode.NONE) { GameServices[Galaxy::class] }
	private val galaxyGroupSystem by lazy (LazyThreadSafetyMode.NONE) { GameServices[GroupSystem::class] }
	
	override val overlay = true
	private val ctx: Context
	private val lwjglGlfw: ImplGlfw
	private val gl3: GLInterface
	private val gdxGLFWKeyMap = mutableMapOf<Int, Int>()
	
	private val shipDebugger = ShipDebugger()
	private val shipDesigner = ShipDesigner()
	private val colonyManager = ColonyManager()
	private val profiler = ProfilerWindow();

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
		
		shipDebugger.set(ctx, galaxy, galaxyGroupSystem)
		shipDesigner.set(ctx, galaxy, galaxyGroupSystem)
		colonyManager.set(ctx, galaxy, galaxyGroupSystem)
		profiler.set(ctx, galaxy, galaxyGroupSystem)
	}

	override fun show() {
		profiler.visible = true
	}

	private var demoVisible = false
	private var mainDebugVisible = false

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
			
			if (mainDebugVisible) {

				if (ImGui.begin("Debug window", ::mainDebugVisible, WindowFlag.MenuBar.i)) {

					if (ImGui.beginMenuBar()) {
						if (ImGui.beginMenu("Windows")) {
							if (ImGui.menuItem("Ship debug", "", shipDebugger::visible)) {
								shipDebugger.visible = !shipDebugger.visible
							}
							if (ImGui.menuItem("Ship designer", "", shipDesigner::visible)) {
								shipDesigner.visible = !shipDesigner.visible
							}
							if (ImGui.menuItem("Colony manager", "", colonyManager::visible)) {
								colonyManager.visible = !colonyManager.visible
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

			commandMenu()
			shipDebugger.draw()
			shipDesigner.draw()
			colonyManager.draw()
			profiler.draw()

			ImGui.render()
			gl3.renderDrawData(ctx.drawData)
			
		} catch (e: Throwable) {
			log.error("Error drawing debug window", e)
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
		
		if (BeginPiePopup("CommandMenu", imgui.MouseButton.Right)) {
			
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

	fun keyAction(action: KeyActions_UIScreen): Boolean {

		if (action == KeyActions_UIScreen.DEBUG) {
			mainDebugVisible = !mainDebugVisible;
			return true;
			
		} else if (action == KeyActions_UIScreen.SHIP_DEBUG) {
			shipDebugger.visible = !shipDebugger.visible;
			return true;
		
		} else if (action == KeyActions_UIScreen.COLONY_MANAGER) {
			colonyManager.visible = !colonyManager.visible;
			return true;
		
		} else if (action == KeyActions_UIScreen.SHIP_DESIGNER) {
			shipDesigner.visible = !shipDesigner.visible;
			return true;
			
		} else if (action == KeyActions_UIScreen.PROFILER) {
			profiler.visible = !profiler.visible;
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

		val action = KeyMappings.getRaw(keycode, UIScreen::class)

		if (action != null) {
			return keyAction(action as KeyActions_UIScreen)
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

		val action = KeyMappings.getTranslated(character, UIScreen::class)

		if (action != null) {
			return keyAction(action as KeyActions_UIScreen)
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
	
	public abstract class UIWindow {
		lateinit var ctx: Context
		lateinit var galaxy: Galaxy
		lateinit var galaxyGroupSystem: GroupSystem
		
		open var visible = false
		
		fun set(ctx: Context, galaxy: Galaxy, galaxyGroupSystem: GroupSystem) {
			this.ctx = ctx
			this.galaxy = galaxy
			this.galaxyGroupSystem = galaxyGroupSystem
		}
		
		fun rightAlignedColumnText(string: String) {
			with (ImGui) {
				ImGui.cursorPosX = ImGui.cursorPosX + ImGui.getColumnWidth() - calcTextSize(string).x - ImGui.scrollX - 2 * ImGui.style.itemSpacing.x
				text(string)
			}
		}
		
		fun sameLineRightAlignedColumnText(string: String) {
			with (ImGui) {
				sameLine(ImGui.cursorPosX + ImGui.getColumnWidth() - calcTextSize(string).x - ImGui.scrollX - 2 * ImGui.style.itemSpacing.x)
				text(string)
			}
		}
		
		fun lerpColor(out: Vec4, inA: Vec4, inB: Vec4, current: Float, max: Float): Vec4 {
			
			val inv = max - current
			
			out.r = (inA.r * inv + inB.r * current) / max
			out.g = (inA.g * inv + inB.g * current) / max
			out.b = (inA.b * inv + inB.b * current) / max
			
			return out
		}
		
		abstract fun draw()
	}
}
