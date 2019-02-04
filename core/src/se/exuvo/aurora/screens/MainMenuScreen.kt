package se.exuvo.aurora.screens

import com.artemis.WorldConfigurationBuilder
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.profiling.GLErrorListener
import com.badlogic.gdx.graphics.profiling.GLProfiler
import net.mostlyoriginal.api.event.common.EventSystem
import net.mostlyoriginal.plugin.ProfilerPlugin
import se.exuvo.aurora.Assets
import se.exuvo.aurora.galactic.Empire
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.planetarysystems.PlanetarySystem
import se.exuvo.aurora.planetarysystems.systems.GroupSystem
import se.exuvo.aurora.planetarysystems.systems.MovementSystem
import se.exuvo.aurora.planetarysystems.systems.OrbitSystem
import se.exuvo.aurora.planetarysystems.systems.PassiveSensorSystem
import se.exuvo.aurora.planetarysystems.systems.PowerSystem
import se.exuvo.aurora.planetarysystems.systems.RenderSystem
import se.exuvo.aurora.planetarysystems.systems.ShipSystem
import se.exuvo.aurora.planetarysystems.systems.SolarIrradianceSystem
import se.exuvo.aurora.planetarysystems.systems.WeaponSystem
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Vector2L
import net.mostlyoriginal.api.event.common.SubscribeAnnotationFinder
import net.mostlyoriginal.api.event.dispatcher.PollingPooledEventDispatcher
import se.exuvo.aurora.planetarysystems.systems.CustomSystemInvocationStrategy
import com.artemis.utils.Bag

class MainMenuScreen() : GameScreenImpl() {

	private val assetManager by lazy (LazyThreadSafetyMode.NONE) { GameServices[AssetManager::class] }
	private val batch by lazy (LazyThreadSafetyMode.NONE) { GameServices[SpriteBatch::class] }
	private val uiCamera = OrthographicCamera()
	
	override fun show() {
		val profiler = GameServices[GLProfiler::class]
		profiler.setListener(GLErrorListener.THROWING_LISTENER)
	}

	override fun resize(width: Int, height: Int) {
		uiCamera.setToOrtho(false, width.toFloat(), height.toFloat())
	}

	override fun update(deltaRealTime: Float) {
		if (Gdx.input.isKeyPressed(Input.Keys.ESCAPE)) {
			Gdx.app.exit()
		}

//		if (Gdx.input.isKeyPressed(Input.Keys.ENTER)) {

		val empires = mutableListOf(Empire("player1"), Empire("player2"))
		val galaxy = Galaxy(empires, 0) //, system2, system3
		
		val systems = Bag(PlanetarySystem::class.java)
		systems.add(PlanetarySystem("s1", Vector2L(0, 0)))
//		systems.add(PlanetarySystem("s2", Vector2L(4367, 0)))
//		systems.add(PlanetarySystem("s3", Vector2L(-2000, -5000)))
		
		galaxy.init(systems)

		val systemView = PlanetarySystemScreen(systems[0])
		GameServices.put(GalaxyScreen(systemView))
		GameServices[GameScreenService::class].add(ImGuiScreen())
//		GameServices[GameScreenService::class.java].add(Scene2DScreen())
		GameServices[GameScreenService::class].add(systemView)
//    }
	}

	override fun draw() {
		batch.projectionMatrix = uiCamera.combined
		batch.begin()
		Assets.fontUI.draw(batch, "Main Menu", 8f, 32f)
		batch.end()
	}
}
