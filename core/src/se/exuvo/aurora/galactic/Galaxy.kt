package se.exuvo.aurora.galactic

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.Entity
import com.artemis.EntitySubscription.SubscriptionListener
import com.artemis.World
import com.artemis.WorldConfigurationBuilder
import com.artemis.utils.Bag
import com.artemis.utils.IntBag
import com.artemis.utils.Sort
import com.badlogic.gdx.utils.Disposable
import net.mostlyoriginal.api.event.common.EventSystem
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.galactic.systems.GalacticRenderSystem
import se.exuvo.aurora.planetarysystems.PlanetarySystem
import se.exuvo.aurora.planetarysystems.components.ChangingWorldComponent
import se.exuvo.aurora.planetarysystems.components.MovementValues
import se.exuvo.aurora.planetarysystems.components.PlanetarySystemComponent
import se.exuvo.aurora.planetarysystems.systems.CustomSystemInvocationStrategy
import se.exuvo.aurora.planetarysystems.systems.GroupSystem
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
import kotlin.concurrent.read
import se.exuvo.aurora.utils.Storage
import se.exuvo.aurora.planetarysystems.components.EntityReference
import se.exuvo.aurora.planetarysystems.components.EntityUUID

class Galaxy(val empires: MutableList<Empire>, var time: Long = 0) : Runnable, Disposable {
	val log = LogManager.getLogger(this.javaClass)

	lateinit var systems: Bag<PlanetarySystem>
	private val groupSystem by lazy(LazyThreadSafetyMode.NONE) { GameServices[GroupSystem::class] }
	private val threadPool = ThreadPoolTaskGroupHandler<SimpleTaskGroup>("Galaxy", Settings.getInt("Galaxy/threads", Runtime.getRuntime().availableProcessors()), true) //
	private var thread: Thread? = null
	private var sleeping = false
	private var shutdown = false

	var day: Int = updateDay()
	var speed: Long = 1 * Units.NANO_SECOND
	var speedLimited = false
	var tickSize: Int = 0

	val players = ArrayList<Player>()
	val storage = Storage()

	val worldLock = ReentrantReadWriteLock()
	val world: World
	
	init {
		GameServices.put(this)
		
		empires.add(Empire.GAIA)
		players.add(Player.current)
		
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

	fun init(systems: Bag<PlanetarySystem>) {
		this.systems = systems
		
		systems.forEach {
			it.init()
		}
		
		updateSpeed()
		
		val thread = Thread(this, "Galaxy");
		thread.setDaemon(true);
		thread.start();

		this.thread = thread
	}
	
	override fun dispose() {
		shutdown = true
		
		thread?.join()
		
		systems.forEach {
			it.dispose()
		}
	}
	
	fun updateSpeed() {
		
		var lowestRequestedSpeed = Long.MAX_VALUE;

		for (player in players) {
			lowestRequestedSpeed = Math.min(lowestRequestedSpeed, player.requestedSpeed)
		}
		
		speed = lowestRequestedSpeed
		
		if (speed > 0 && sleeping) {
			thread!!.interrupt()
		}
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
	
	fun resolveEntityReference(entityReference: EntityReference): EntityReference? {
		
		entityReference.system.lock.read {
			if (entityReference.system.isEntityReferenceValid(entityReference)) {
				return entityReference
			}
		}
		
		return getEntityReferenceByUUID(entityReference.entityUUID)
	}
	
	fun getEntityReferenceByUUID(entityUUID: EntityUUID): EntityReference? {
		
		systems.forEach{ system ->
			system.lock.read {
				val entityID = system.getEntityByUUID(entityUUID)
				
				if (entityID != null) {
					return system.getEntityReference(entityID)
				}
			}
		}
		
		return null
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
			var lastSleep = System.nanoTime()
			var lastProcess = System.nanoTime()

			var tasks = systems.map { UpdateSystemTask(it, this) }
			
			while (!shutdown) {
				var now = System.nanoTime()
		
				if (speed > 0L) {
					
					if (speed != oldSpeed) {
						accumulator = 0
						oldSpeed = speed
					} else {
						accumulator += now - lastSleep;
					}
					
					if (accumulator >= speed) {

						//TODO automatically adjust based on computer speed
						tickSize = if (speed >= Units.NANO_MILLI) 1 else (Units.NANO_MILLI / speed).toInt()

						// max sensible tick size is 1 minute, unless there is combat..
						if (tickSize > 60) {
							tickSize = 60
						}
						
						val tickSpeed = speed * tickSize
						
						accumulator -= tickSpeed

//						println("tickSize $tickSize, speed $speed, diff ${now - lastProcess}, accumulator $accumulator")

						time += tickSize;
						updateDay()

						val queue = LinkedBlockingQueue<UpdateSystemTask>(tasks)

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
						speedLimited = systemUpdateDuration > speed
						
						queue.clear()
						
						if (speedLimited) {
//							log.warn("Galaxy update took ${Units.nanoToString(systemUpdateDuration)} which is more than the requested speed delay ${Units.nanoToString(speed)}")
						}

//						systems.forEach {
//							print("${it.sid} ${Units.nanoToString(it.updateTime)}, ")
//						}
//						println()
						
						//if one system took a noticable larger time to process than others, schedule it earlier
						Sort.instance().sort(systems, object : Comparator<PlanetarySystem> {
							val s = tickSpeed / 10
							override fun compare(o1: PlanetarySystem, o2: PlanetarySystem): Int {
								val diff = o1.updateTime - o2.updateTime
								
								if (diff > s) return -1
								if (diff < -s) return 1
								return 0
							}
						})
						
						lastProcess = now;
					}

					lastSleep = now;
					
					// If we are more than 10 ticks behind stop counting
					if (accumulator >= speed * 10) {
						accumulator = speed * 10L
						
					} else if (accumulator < speed) {

						var sleepTime = (speed - accumulator) / Units.NANO_MILLI

						if (sleepTime > 1) {
							sleeping = true
							ThreadUtils.sleep(sleepTime - 1)
							sleeping = false
							
						} else {
							sleeping = true
							Thread.yield()
							sleeping = false
						}
					}

				} else {
					oldSpeed = speed
					sleeping = true
					ThreadUtils.sleep(1000)
					sleeping = false
				}
			}
		} catch (t: Throwable) {
			log.error("Exception in galaxy loop", t)
		}
	}

	class UpdateSystemTask(val system: PlanetarySystem, val galaxy: Galaxy) : Runnable {

		val log by lazy(LazyThreadSafetyMode.NONE) { LogManager.getLogger(this.javaClass) }

		override fun run() {
			try {
				val systemUpdateStart = System.nanoTime()
				system.update(galaxy.tickSize)
				system.updateTime = (System.nanoTime() - systemUpdateStart)
				
			} catch (t: Throwable) {
				log.error("Exception in system update", t)
				galaxy.speed = 0
			}
		}
	}
}