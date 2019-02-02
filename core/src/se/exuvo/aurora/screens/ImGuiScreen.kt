package se.exuvo.aurora.screens

import com.artemis.ComponentMapper
import com.artemis.utils.Bag
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics
import glm_.vec2.Vec2
import glm_.vec2.Vec2d
import imgui.Context
import imgui.ImGui
import imgui.TreeNodeFlag
import imgui.WindowFlag
import imgui.impl.ImplGL3
import imgui.impl.LwjglGlfw
import org.apache.log4j.Logger
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
import kotlin.concurrent.read
import kotlin.concurrent.write
import imgui.DataType
import uno.glfw.GlfwWindowHandle
import imgui.Col
import imgui.Style
import glm_.vec4.Vec4

class ImGuiScreen : GameScreenImpl(), InputProcessor {

	val log = Logger.getLogger(this.javaClass)

	private val galaxy by lazy { GameServices[Galaxy::class] }
	private val galaxyGroupSystem by lazy { GameServices[GroupSystem::class] }

	private val uiCamera = GameServices[GameScreenService::class].uiCamera

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
		
		ImGui.styleColorsDark()
//		ImGui.styleColorsClassic()
		
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
		
		LwjglGlfw.init(GlfwWindow(GlfwWindowHandle((Gdx.graphics as Lwjgl3Graphics).window.windowHandle)), false)
	}

	override fun show() {
		addResourceAmount[0] = 1
	}

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
		// https://github.com/kotlin-graphics/imgui/blob/4b052ea00bae762a4ac5f62b5bf7939f33b7895a/src/test/kotlin/imgui/gl/test%20lwjgl.kt

		try {
			LwjglGlfw.newFrame()
			ImGui.newFrame()

			if (demoVisible) {
				ImGui.showDemoWindow(::demoVisible)
			}

			if (mainDebugVisible) {

				if (ImGui.begin_("Debug window", ::mainDebugVisible, WindowFlag.MenuBar.i)) {

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
					ImGui.text("ctx.io.wantCaptureMouse ${ctx.io.wantCaptureMouse}")
					ImGui.text("ctx.io.wantCaptureKeyboard ${ctx.io.wantCaptureKeyboard}")
					ImGui.plotLines("plot", graphValues)

					if (ImGui.button("OK")) {
						println("click")
					}

					ImGui.inputText("string", stringbuf)
					ImGui.sliderFloat("float", slider, 0f, 1f)
					ImGui.image(img.getTexture().textureObjectHandle, Vec2(64, 64))
				}
				ImGui.end()
			}

			shipDebug()

			ImGui.render()

			if (ImGui.drawData != null) {
				ImplGL3.renderDrawData(ImGui.drawData!!)
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
	var addResource = Resource.NUCLEAR_FISSION
	var addResourceAmount = IntArray(1)
	private fun shipDebug() {

		if (shipDebugVisible) {

			if (ImGui.begin_("Ship debug", ::shipDebugVisible, WindowFlag.AlwaysAutoResize.i)) {

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

								for (partRef in shipComponent.shipClass.getPartRefs()) {
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
									val usage = if (maxVolume == 0) 0f else usedVolume / maxVolume.toFloat()
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

								ImGui.inputScalar("kg", imgui.DataType.Int, addResourceAmount, 10, 100, "%d", 0)

								if (ImGui.button("Add")) {
									system.lock.write {
										if (!shipComponent.addCargo(addResource, addResourceAmount[0])) {
											println("Cargo does not fit")
										}
									}
								}

								ImGui.sameLine(0f, ImGui.style.itemInnerSpacing.x)

								if (ImGui.button("Remove")) {
									system.lock.write {
										if (shipComponent.retrieveCargo(addResource, addResourceAmount[0]) != addResourceAmount[0]) {
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
										val usage = if (maxVolume == 0) 0f else usedVolume / maxVolume.toFloat()
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

		if (ctx.io.wantCaptureKeyboard) {
			gdxGLFWKeyMap[keycode]?.apply {
				LwjglGlfw.keyCallback(this, 0, GLFW.GLFW_PRESS, 0)
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
				LwjglGlfw.keyCallback(this, 0, GLFW.GLFW_RELEASE, 0)
			}
			return true
		}

		return false
	}

	override fun keyTyped(character: Char): Boolean {
		if (ctx.io.wantCaptureKeyboard) {
			LwjglGlfw.charCallback(character.toInt())
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
			LwjglGlfw.scrollCallback(Vec2d(0, -amount))
			return true
		}

		return false
	}

	override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
		return false
	}

	override fun dispose() {
		LwjglGlfw.shutdown()
		ctx.shutdown();
		super.dispose()
	}
}
