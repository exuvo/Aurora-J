package se.exuvo.aurora.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.utils.Disposable
import glm_.vec2.Vec2
import glm_.vec2.Vec2d
import glm_.vec4.Vec4
import imgui.Col
import imgui.ConfigFlag
import imgui.ImGui
import imgui.WindowFlag
import imgui.classes.Context
import imgui.imgui.widgets.beginPieMenu
import imgui.imgui.widgets.beginPiePopup
import imgui.imgui.widgets.endPieMenu
import imgui.imgui.widgets.endPiePopup
import imgui.imgui.widgets.pieMenuItem
import imgui.impl.gl.GLInterface
import imgui.impl.gl.ImplBestGL
import imgui.impl.glfw.ImplGlfw
import org.apache.logging.log4j.LogManager
import org.lwjgl.glfw.GLFW
import se.exuvo.aurora.Assets
import se.exuvo.aurora.AuroraGame
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.starsystems.systems.GroupSystem
import se.exuvo.aurora.starsystems.systems.RenderSystem
import se.exuvo.aurora.ui.keys.KeyActions_UIScreen
import se.exuvo.aurora.ui.keys.KeyMappings
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.toLinearRGB
import uno.glfw.GlfwWindow
import uno.glfw.GlfwWindowHandle
import kotlin.concurrent.withLock

class UIScreen : GameScreenImpl(), InputProcessor {
	companion object {
		@JvmStatic val log = LogManager.getLogger(UIScreen::class.java)
	}

	private val galaxy by lazy (LazyThreadSafetyMode.NONE) { GameServices[Galaxy::class] }
	private val galaxyGroupSystem by lazy (LazyThreadSafetyMode.NONE) { GameServices[GroupSystem::class] }
	
	override val overlay = true
	val imguiCamera = OrthographicCamera()
	private val ctx: Context
	private val lwjglGlfw: ImplGlfw
	private val gl3: GLInterface
	private val gdxGLFWKeyMap = mutableMapOf<Int, Int>()
	
	val shipDebugger = ShipDebugger()
	val shipDesigner = ShipDesigner()
	val colonyManager = ColonyManager()
	val profiler = ProfilerWindow()
	val empireOverview = EmpireOverview()

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

		var firstContext = AuroraGame.storage(ImGuiGlobalStorage::class)
//		
		if (firstContext == null) {
			ctx = Context()
			AuroraGame.storage + ImGuiGlobalStorage(ctx)
			
		} else {
			ctx = Context(firstContext.ctx.io.fonts)
		}
		
		ctx.io.configFlags = ctx.io.configFlags or ConfigFlag.IsSRGB.i
		ctx.setCurrent()
		
		ImGui.styleColorsDark()
		
		ImGui.style.apply {
			itemSpacing.y = 3f
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
		
		// convert style from sRGB to linear https://github.com/ocornut/imgui/issues/578#issuecomment-577222389
		for (i in 0 until ImGui.style.colors.size) {
			ImGui.style.colors[i].toLinearRGB()
		}
		
		lwjglGlfw = ImplGlfw(GlfwWindow(GlfwWindowHandle((Gdx.graphics as Lwjgl3Graphics).window.windowHandle)), false)
		gl3 = ImplBestGL()
		
		shipDebugger.set(ctx, galaxy, galaxyGroupSystem, imguiCamera)
		shipDesigner.set(ctx, galaxy, galaxyGroupSystem, imguiCamera)
		colonyManager.set(ctx, galaxy, galaxyGroupSystem, imguiCamera)
		profiler.set(ctx, galaxy, galaxyGroupSystem, imguiCamera)
		empireOverview.set(ctx, galaxy, galaxyGroupSystem, imguiCamera)
	}

	override fun show() {
		empireOverview.visible = true
	}

	private var demoVisible = false
	private var mainDebugVisible = false

	var slider = 1f
	var stringbuf = ByteArray(10)
	var img = Assets.textures.findRegion("strategic/sun")
	var menuBarState = BooleanArray(1)
	var graphValues = floatArrayOf(0f, 5f, 2f, 4f)
	
	override fun draw() {
		try {
			ctx.setCurrent()
			
			gl3.newFrame()
			lwjglGlfw.newFrame()
			ImGui.newFrame()

			if (demoVisible) {
				ImGui.showDemoWindow(::demoVisible)
			}
			
			galaxy.shadowLock.withLock {
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
								if (ImGui.menuItem("DrawWeaponRangesWithoutShader", "", RenderSystem.debugDrawWeaponRangesWithoutShader)) {
									RenderSystem.debugDrawWeaponRangesWithoutShader = !RenderSystem.debugDrawWeaponRangesWithoutShader
								}
								if (ImGui.menuItem("debugSpatialPartitioning", "", RenderSystem.debugSpatialPartitioning)) {
									RenderSystem.debugSpatialPartitioning = !RenderSystem.debugSpatialPartitioning
								}
								if (ImGui.menuItem("debugSpatialPartitioningPlanetoids", "", RenderSystem.debugSpatialPartitioningPlanetoids)) {
									RenderSystem.debugSpatialPartitioningPlanetoids = !RenderSystem.debugSpatialPartitioningPlanetoids
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
				empireOverview.draw()
			}
			
			ImGui.render()
			gl3.renderDrawData(ctx.drawData)
			
			empireOverview.postDraw()
			
		} catch (e: Throwable) {
			log.error("Error drawing windows", e)
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
		with (ImGui) {
			with (imgui.dsl) {
				if (commandMenuOpen) {
					openPopup("CommandMenu");
					commandMenuOpen = false
				}
				
				//TODO get selection intersection of supported actions from Player.current.selection
				//TODO add click support to beginPieMenu
				//TODO add hover support to pieMenuItem
				
				if (beginPiePopup("CommandMenu", imgui.MouseButton.Right)) {
					
					if (commandMenuClose) {
						closeCurrentPopup();
						commandMenuClose = false
					}
					
					if (beginPieMenu("Move")) {
						if (isItemClicked()) {
							println("move click")
						}
						if (pieMenuItem("Toggle Chemical Thrusters")) {
						
						}
						if (pieMenuItem("Hyper drive")) {
						}
						if (isItemHovered()) {
							println("hyper drive hovered")
							setTooltip("Open hyper drive window")
						}
						if (pieMenuItem("Jump drive")) {
						
						}
						if (pieMenuItem("Alcubierre drive")) {
						
						}
						
						endPieMenu();
					}
					
					if (beginPieMenu("Attack")) {
						if (pieMenuItem("Kinetic bombardment")) {
						
						}
						
						endPieMenu();
					}
					
					if (pieMenuItem("Test2")) {println("2")}
		
					if (pieMenuItem("Test3", false)) {println("3")}
					
		
					if (beginPieMenu("Sub")) {
						
						if (beginPieMenu("Sub sub\nmenu")) {
							if (pieMenuItem("SubSub")) {println("subsub1")}
							if (pieMenuItem("SubSub2")) {println("subsub2")}
							
							if (beginPieMenu("Sub sub\nmenu")) {
								if (pieMenuItem("SubSub")) {println("subsub1")}
								if (pieMenuItem("SubSub2")) {println("subsub2")}
									if (beginPieMenu("Sub sub\nmenu")) {
										if (pieMenuItem("SubSub")) {println("subsub1")}
										if (pieMenuItem("SubSub2")) {println("subsub2")}
										if (beginPieMenu("Sub sub\nmenu")) {
											if (pieMenuItem("SubSub")) {println("subsub1")}
											if (pieMenuItem("SubSub2")) {println("subsub2")}
												
												endPieMenu();
											}
										endPieMenu();
									}
								endPieMenu();
							}
							
							endPieMenu();
						}
						
						if (pieMenuItem("TestSub")) {println("sub1")}
						if (pieMenuItem("TestSub2")) {println("sub2")}
						
						endPieMenu();
					}
		
					endPiePopup();
				}
			}
		}
	}
	
	override fun resize(width: Int, height: Int) {
		imguiCamera.setToOrtho(true, width.toFloat(), height.toFloat())
	}

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
		lateinit var imguiCamera: OrthographicCamera
		lateinit var galaxy: Galaxy
		lateinit var galaxyGroupSystem: GroupSystem
		
		open var visible = false
		
		open fun set(ctx: Context, galaxy: Galaxy, galaxyGroupSystem: GroupSystem, imguiCamera: OrthographicCamera) {
			this.ctx = ctx
			this.galaxy = galaxy
			this.galaxyGroupSystem = galaxyGroupSystem
			this.imguiCamera = imguiCamera
		}
		
		abstract fun draw()
		open fun postDraw() {}
	}
}
