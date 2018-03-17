package se.exuvo.aurora.screens

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics
import com.badlogic.gdx.graphics.GL20
import glm_.vec2.Vec2d
import imgui.Context
import imgui.ImGui
import imgui.impl.LwjglGL3
import org.lwjgl.glfw.GLFW
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.planetarysystems.components.OrbitComponent
import se.exuvo.aurora.planetarysystems.components.ThrustComponent
import se.exuvo.aurora.planetarysystems.systems.GroupSystem
import se.exuvo.aurora.utils.GameServices
import uno.glfw.GlfwWindow

class DebugScreen : GameScreenImpl(), InputProcessor {

	private val galaxy by lazy { GameServices[Galaxy::class.java] }
	private val galaxyGroupSystem by lazy { GameServices[GroupSystem::class.java] }

	private val nameMapper = ComponentMapper.getFor(NameComponent::class.java)
	private val orbitMapper = ComponentMapper.getFor(OrbitComponent::class.java)
	private val thrustMapper = ComponentMapper.getFor(ThrustComponent::class.java)

	private val uiCamera = GameServices[GameScreenService::class.java].uiCamera

	override val overlay = true
	private var debugVisible = false
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
	
	var f = 0f

	override fun draw() {

		// https://github.com/kotlin-graphics/imgui/wiki/Using-libGDX

		LwjglGL3.newFrame()

		ImGui.text("Hello, world %d", 123)
		ImGui.text("ctx.hoveredWindow ${ctx.hoveredWindow}")
		ImGui.text("ctx.navWindow ${ctx.navWindow}")
		
//		ImGui.button("OK") {
//			// react
//		}
//		ImGui.inputText("string", buf)
//		ImGui.sliderFloat("float", ::f, 0f, 1f)
//		ImGui.image(texture.textureObjectHandle, Vec2(imageWidth, imageHeight))

		ImGui.render()

		if (ImGui.drawData != null) {

			LwjglGL3.renderDrawData(ImGui.drawData!!)
		}
	}

	override fun resize(width: Int, height: Int) {}

	override fun update(deltaRealTime: Float) {}

	override fun keyDown(keycode: Int): Boolean {
		gdxGLFWKeyMap[keycode]?.apply {
			LwjglGL3.keyCallback(this, 0, GLFW.GLFW_PRESS, 0)
		}
		return ctx.navWindow != null
	}
	
	override fun keyUp(keycode: Int): Boolean {
		gdxGLFWKeyMap[keycode]?.apply {
			LwjglGL3.keyCallback(this, 0, GLFW.GLFW_RELEASE, 0)
		}
		return ctx.navWindow != null
	}
	
	override fun keyTyped(character: Char): Boolean {
		LwjglGL3.charCallback(character.toInt())
		return ctx.navWindow != null
	}

	override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
		return ctx.navWindow != null
	}

	override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
		LwjglGL3.mouseButtonCallback(button, GLFW.GLFW_PRESS, 0)
		return ctx.navWindow != null
	}
	
	override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
		return ctx.navWindow != null
	}
	
	override fun scrolled(amount: Int): Boolean {
		LwjglGL3.scrollCallback(Vec2d(0, -amount))
		return ctx.navWindow != null
	}

	override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
		return false
	}

	override fun dispose() {
		LwjglGL3.shutdown()
		ctx.shutdown();
		super.dispose()
	}
}
