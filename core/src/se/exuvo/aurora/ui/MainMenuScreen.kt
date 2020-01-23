package se.exuvo.aurora.ui

import com.artemis.WorldConfigurationBuilder
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.profiling.GLErrorListener
import com.badlogic.gdx.graphics.profiling.GLProfiler
import net.mostlyoriginal.api.event.common.EventSystem
import se.exuvo.aurora.Assets
import se.exuvo.aurora.galactic.Empire
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.starsystems.StarSystem
import se.exuvo.aurora.starsystems.systems.GroupSystem
import se.exuvo.aurora.starsystems.systems.MovementSystem
import se.exuvo.aurora.starsystems.systems.OrbitSystem
import se.exuvo.aurora.starsystems.systems.PassiveSensorSystem
import se.exuvo.aurora.starsystems.systems.PowerSystem
import se.exuvo.aurora.starsystems.systems.RenderSystem
import se.exuvo.aurora.starsystems.systems.ShipSystem
import se.exuvo.aurora.starsystems.systems.SolarIrradianceSystem
import se.exuvo.aurora.starsystems.systems.WeaponSystem
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Vector2L
import net.mostlyoriginal.api.event.common.SubscribeAnnotationFinder
import net.mostlyoriginal.api.event.dispatcher.PollingPooledEventDispatcher
import se.exuvo.aurora.starsystems.systems.CustomSystemInvocationStrategy
import com.artemis.utils.Bag
import se.exuvo.aurora.AuroraGame
import se.exuvo.aurora.galactic.Player

class MainMenuScreen() : GameScreenImpl() {

	private val assetManager = GameServices[AssetManager::class]
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
		Player.current.empire = empires[0]
		
		val galaxy = Galaxy(empires, 0)
		
		val systems = Bag(StarSystem::class.java)
		systems.add(StarSystem("s1", Vector2L(0, 0)))
		systems.add(StarSystem("s2", Vector2L(4367, 0)))
		systems.add(StarSystem("s3", Vector2L(-2000, -5000)))
		
		galaxy.init(systems)

		val systemView = StarSystemScreen(systems[0])
		AuroraGame.currentWindow.screenService.add(UIScreen())
		AuroraGame.currentWindow.screenService.add(systemView)
//    }
	}

	override fun draw() {
		val batch = AuroraGame.currentWindow.spriteBatch
		
		batch.projectionMatrix = uiCamera.combined
		batch.begin()
		Assets.fontUI.draw(batch, "Main Menu", 8f, 32f)
		batch.end()
	}
}
