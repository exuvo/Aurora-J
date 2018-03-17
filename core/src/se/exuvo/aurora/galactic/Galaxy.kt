package se.exuvo.aurora.galactic

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntityListener
import org.apache.log4j.Logger
import se.exuvo.aurora.galactic.systems.GalacticRenderSystem
import se.exuvo.aurora.planetarysystems.PlanetarySystem
import se.exuvo.aurora.planetarysystems.components.PlanetarySystemComponent
import se.exuvo.aurora.planetarysystems.systems.GroupSystem
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.TimeUnits
import se.exuvo.settings.Settings
import se.unlogic.standardutils.threads.SimpleTaskGroup
import se.unlogic.standardutils.threads.ThreadPoolTaskGroupHandler
import se.unlogic.standardutils.threads.ThreadUtils
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write
import java.lang.IllegalArgumentException

class Galaxy(val empires: MutableList<Empire>, val systems: MutableList<PlanetarySystem>, var time: Long = 0) : Runnable, EntityListener {

	val log = Logger.getLogger(this.javaClass)

	private val planetarySystemMapper = ComponentMapper.getFor(PlanetarySystemComponent::class.java)
	private val groupSystem by lazy { GameServices[GroupSystem::class.java] }
	private val threadPool = ThreadPoolTaskGroupHandler<SimpleTaskGroup>("Galaxy", Settings.getInt("Galaxy.Threads"), true) //
	var thread: Thread? = null
	var sleeping = false

	var day: Int = 0
	var speed: Long = 1 * TimeUnits.NANO_SECOND
	var paused = false

	val engineLock = ReentrantReadWriteLock()
	val engine = Engine()

	fun init() {
		empires.add(Empire.GAIA)
		
		GameServices.put(this)
		systems.forEach {
			engine.addEntity(it)
			it.init()
			it.engine.addEntityListener(this)
		}

		engine.addSystem(GalacticRenderSystem())
		
		val thread = Thread(this, "Galaxy");
		thread.setDaemon(true);
		thread.start();

		this.thread = thread
	}

	fun getEmpire(id: Int): Empire {
		for (empire in empires) {
			if (empire.id == id) {
				return empire;
			}
		}
		throw IllegalArgumentException("$id")
	}
	
	fun getPlanetarySystem(id: Int): PlanetarySystem {
		for (system in systems) {
			if (system.id == id) {
				return system;
			}
		}
		throw IllegalArgumentException("$id")
	}

	override fun entityAdded(entity: Entity) {
		groupSystem.entityAdded(entity)
	}

	override fun entityRemoved(entity: Entity) {
		groupSystem.entityRemoved(entity)
	}

	fun getPlanetarySystem(entity: Entity): PlanetarySystem {
		return planetarySystemMapper.get(entity).system!!
	}

	private fun updateDay() {
		day = (time / (24L * 60L * 60L)).toInt()
	}

	override fun run() {

		try {
			var accumulator = speed
			var oldSpeed = speed
			var oldPaused = paused
			var lastSleep = System.nanoTime()

			while (true) {
				var now = System.nanoTime()

				if (paused != oldPaused) {
					accumulator = 0
					lastSleep = System.nanoTime()
					oldPaused = paused
				}

				if (!paused) {

					if (oldSpeed != speed) {
						accumulator = 0
						oldSpeed = speed
					} else {
						accumulator += now - lastSleep;
					}

					if (accumulator >= speed) {

						accumulator -= speed;

						var tickSize: Int = if (speed >= 1 * TimeUnits.NANO_MILLI) 1 else (TimeUnits.NANO_MILLI / speed).toInt()

						// max sensible tick size is 1 minute
						if (tickSize > 60) {
							tickSize = 60
						}

//						println("tickSize $tickSize, speed $speed, accumulator $accumulator, diff ${now - lastSleep}")

						time += tickSize;
						updateDay()

						val queue = LinkedBlockingQueue<UpdateSystemTask>(systems.size)

						systems.forEach { queue.add(UpdateSystemTask(it, tickSize, this)) }

						val executionController = threadPool.execute(SimpleTaskGroup(queue))

						val systemUpdateStart = System.nanoTime()
						executionController.start()

						while (!executionController.isFinished() && !executionController.isAborted) {
							try {
								executionController.awaitExecution()
							} catch (ignore: InterruptedException) {
							}
						}

						engineLock.write {
							engine.update(tickSize.toFloat())
						}

						val systemUpdateDuration = (System.nanoTime() - systemUpdateStart)
//					log.debug("Galaxy update took " + NanoTimeUnits.nanoToString(systemUpdateDuration))

						//TODO handle tick abortion and tickSize lowering
					}

					// If we are more than 10 ticks behind stop counting
					if (accumulator >= speed * 10) {
						accumulator = speed * 10L
					}

					lastSleep = now;

					if (accumulator < speed) {

						var sleepTime = (speed - accumulator) / TimeUnits.NANO_MILLI

						if (sleepTime > 0) {
							sleeping = true
							ThreadUtils.sleep(sleepTime)
							sleeping = false
						}
					}

				} else {
					sleeping = true
					ThreadUtils.sleep(1000)
					sleeping = false
				}
			}
		} catch (t: Throwable) {
			log.error("Exception in galaxy loop", t)
		}
	}

	class UpdateSystemTask(val system: PlanetarySystem, val deltaGameTime: Int, val galaxy: Galaxy) : Runnable {

		val log by lazy { Logger.getLogger(this.javaClass) }

		override fun run() {
			try {
				system.update(deltaGameTime)
			} catch (t: Throwable) {
				log.error("Exception in system update", t)
				galaxy.paused = true
			}
		}
	}
}