package se.exuvo.aurora.galactic

import com.artemis.ComponentMapper
import com.artemis.Entity
import com.artemis.EntitySubscription
import com.artemis.World
import com.artemis.WorldConfigurationBuilder
import org.apache.log4j.Logger
import se.exuvo.aurora.galactic.systems.GalacticRenderSystem
import se.exuvo.aurora.planetarysystems.PlanetarySystem
import se.exuvo.aurora.planetarysystems.components.ChangingWorldComponent
import se.exuvo.aurora.planetarysystems.components.MovementValues
import se.exuvo.aurora.planetarysystems.components.PlanetarySystemComponent
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
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.utils.forEach
import se.exuvo.settings.Settings
import se.unlogic.standardutils.threads.SimpleTaskGroup
import se.unlogic.standardutils.threads.ThreadPoolTaskGroupHandler
import se.unlogic.standardutils.threads.ThreadUtils
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write
import com.artemis.Aspect
import com.artemis.EntitySubscription.SubscriptionListener
import com.artemis.utils.IntBag
import net.mostlyoriginal.api.event.common.EventSystem
import net.mostlyoriginal.plugin.ProfilerPlugin
import se.exuvo.aurora.planetarysystems.systems.CustomSystemInvocationStrategy

class Galaxy(val empires: MutableList<Empire>, var time: Long = 0) : Runnable {
	val log = Logger.getLogger(this.javaClass)

	lateinit var systems: MutableList<PlanetarySystem>
	private val groupSystem by lazy { GameServices[GroupSystem::class] }
	private val threadPool = ThreadPoolTaskGroupHandler<SimpleTaskGroup>("Galaxy", Settings.getInt("Galaxy/threads", Runtime.getRuntime().availableProcessors()), true) //
	var thread: Thread? = null
	var sleeping = false

	var day: Int = updateDay()
	var speed: Long = 1 * Units.NANO_SECOND
	var paused = false

	val worldLock = ReentrantReadWriteLock()
	val world: World
	
	init {
		GameServices.put(this)
		
		empires.add(Empire.GAIA)
		
		val worldBuilder = WorldConfigurationBuilder()
//		worldBuilder.dependsOn(ProfilerPlugin::class.java)
		worldBuilder.with(EventSystem())
		worldBuilder.with(GroupSystem())
		worldBuilder.with(GalacticRenderSystem())
		worldBuilder.register(CustomSystemInvocationStrategy())
		
		world = World(worldBuilder.build())
		world.inject(this)
		
		world.getAspectSubscriptionManager().get(Aspect.all()).addSubscriptionListener(object: SubscriptionListener {
			override fun inserted(entityIDs: IntBag) {
				entityIDs.forEach { entityID ->
					entityAdded(world, entityID)
				}
			}

			override fun removed(entityIDs: IntBag) {
				entityIDs.forEach { entityID ->
					entityRemoved(world, entityID)
				}
			}
		})
	}

	fun init(systems: MutableList<PlanetarySystem>) {
		this.systems = systems
		
		systems.forEach {
			it.init()
		}
		
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
	
	fun getPlanetarySystemBySID(sid: Int): PlanetarySystem {
		for (system in systems) {
			if (system.sid == sid) {
				return system;
			}
		}
		throw IllegalArgumentException("$sid")
	}
	
	fun getPlanetarySystemByGalacticEntityID(id: Int): PlanetarySystem {
		for (system in systems) {
			if (system.galacticEntityID == id) {
				return system;
			}
		}
		throw IllegalArgumentException("$id")
	}
	
	fun getPlanetarySystemByEntity(entity: Entity): PlanetarySystem {
		return ComponentMapper.getFor(PlanetarySystemComponent::class.java, entity.world).get(entity).system
	}
	
	fun entityAdded(world: World, entityID: Int) {
		groupSystem.inserted(world.getEntity(entityID))
	}

	fun entityRemoved(world: World, entityID: Int) {
		if (!ComponentMapper.getFor(ChangingWorldComponent::class.java, world).has(entityID)) {
			groupSystem.removed(world.getEntity(entityID))
		}
	}
	
	fun moveEntity(targetSystem: PlanetarySystem, entity: Entity, targetPosition: MovementValues) {
		val sourceWorld = entity.world
		
		ComponentMapper.getFor(ChangingWorldComponent::class.java, sourceWorld).create(entity)
		
		//TODO serialize entity, add to target system, remove from old system
	}

	private fun updateDay(): Int {
		day = (time / (24L * 60L * 60L)).toInt()
		return day
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

						var tickSize: Int = if (speed >= 1 * Units.NANO_MILLI) 1 else (Units.NANO_MILLI / speed).toInt()

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

						worldLock.write {
							world.setDelta(tickSize.toFloat())
							world.process()
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

						var sleepTime = (speed - accumulator) / Units.NANO_MILLI

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