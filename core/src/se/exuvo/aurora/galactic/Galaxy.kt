package se.exuvo.aurora.galactic

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.EntitySubscription
import com.artemis.World
import com.artemis.WorldConfigurationBuilder
import com.artemis.utils.Bag
import com.artemis.utils.IntBag
import com.badlogic.gdx.utils.Disposable
import net.mostlyoriginal.api.event.common.EventSystem
import org.apache.commons.math3.util.FastMath
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.galactic.systems.GalacticRenderSystem
import se.exuvo.aurora.starsystems.StarSystem
import se.exuvo.aurora.starsystems.components.ChangingWorldComponent
import se.exuvo.aurora.starsystems.components.EntityReference
import se.exuvo.aurora.starsystems.components.EntityUUID
import se.exuvo.aurora.starsystems.components.MovementValues
import se.exuvo.aurora.starsystems.systems.GroupSystem
import se.exuvo.aurora.ui.ProfilerWindow
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.utils.exponentialAverage
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.settings.Settings
import se.unlogic.standardutils.threads.ThreadUtils
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class Galaxy(val empires: MutableList<Empire>, var time: Long = 0) : Runnable, EntitySubscription.SubscriptionListener, Disposable {
	companion object {
		@JvmField val log = LogManager.getLogger(Galaxy::class.java)
	}

	lateinit var systems: Bag<StarSystem>
	private var thread: Thread? = null
	private val threads = Bag<Thread>(Settings.getInt("Galaxy/threads", Runtime.getRuntime().availableProcessors()))
	val threadsCondition = Object()
	val takenWorkCounter = AtomicInteger()
	val completedWorkCounter = AtomicInteger()
	private var sleeping = false
	var shutdown = false

	var day: Int = updateDay()
	var speed: Long = 1 * Units.NANO_SECOND
	var speedLimited = false
	var tickSize: Int = 1

	private val groupSystem by lazy(LazyThreadSafetyMode.NONE) { GameServices[GroupSystem::class] }
	val players = ArrayList<Player>()

	val world: World
	
	val allSubscription: EntitySubscription
	
	var workingShadow: ShadowGalaxy
	var shadow: ShadowGalaxy // Always safe to use from other StarSystems, requires shadow lock to use from UI
	val shadowLock = ReentrantLock()
	
	val renderProfilerEvents = ProfilerWindow.ProfilerBag()
	
	init {
		GameServices.put(this)
		
		empires.add(Empire.GAIA)
		players.add(Player.current)
		
		val worldBuilder = WorldConfigurationBuilder()
//		worldBuilder.dependsOn(ProfilerPlugin::class.java)
		worldBuilder.with(EventSystem())
		worldBuilder.with(GalacticRenderSystem())
		
		world = World(worldBuilder.build())
		world.inject(this)
		
		allSubscription = world.getAspectSubscriptionManager().get(Aspect.all())
		allSubscription.addSubscriptionListener(this)
		
		workingShadow = ShadowGalaxy(this)
		shadow  = ShadowGalaxy(this)
	}
	
	fun init(systems: Bag<StarSystem>) {
		this.systems = systems
		
		systems.forEachFast { system ->
			system.init()
		}
		
		updateSpeed()
		
		GalaxyWorker.galaxy = this
		takenWorkCounter.set(systems.size())
		completedWorkCounter.set(systems.size())
		
		for(i in 0 until threads.getCapacity()) {
			val thread = Thread(GalaxyWorker, "Galaxy worker ${1 + i}")
			thread.setDaemon(true);
			thread.priority = Thread.NORM_PRIORITY + 1
			threads.add(thread)
			thread.start();
		}
		
		val thread = Thread(this, "Galaxy");
		thread.setDaemon(true);
		thread.start();

		this.thread = thread
	}
	
	override fun run() {

		try {
			var accumulator = speed
			var oldSpeed = speed
			var lastSleep = System.nanoTime()
			var lastProcess = System.nanoTime()

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
						tickSize = 1
//						tickSize = if (speed >= Units.NANO_MILLI) 1 else (Units.NANO_MILLI / speed).toInt()
//
//						// max sensible tick size is 1 minute, unless there is combat..
//						if (tickSize > 60) {
//							tickSize = 60
//						}
						
						val tickSpeed = speed * tickSize
						
						accumulator -= tickSpeed

//						println("tickSize $tickSize, speed $speed, diff ${now - lastProcess}, accumulator $accumulator")

						time += tickSize;
						updateDay()
						
						val profilerEvents = workingShadow.profilerEvents
						profilerEvents.clear()
						
						val systemUpdateStart = System.nanoTime()
						
						profilerEvents.start("commands")
						empires.forEachFast { empire ->
							while(true) {
								val command = empire.commandQueue.poll() ?: break
								
								try {
									if (command.isValid()) {
										command.getSystem().commandQueue.add(command)
									} else {
										log.warn("Invalid command $command")
									}
								} catch (e: Exception) {
									log.error("Exception validating command $command", e)
								}
							}
						}
						profilerEvents.end()
						
						completedWorkCounter.set(0)
						takenWorkCounter.set(0)
						
						profilerEvents.start("run threads")
						synchronized(threadsCondition) {
							threadsCondition.notifyAll()
						}
//						threads.forEachFast { thread ->
//							thread.interrupt()
//						}
						
						workingShadow.added.clear()
						workingShadow.changed.clear()
						workingShadow.deleted.clear()
						
						profilerEvents.start("process")
						world.setDelta(tickSize.toFloat())
						world.process()
						profilerEvents.end()
						
						profilerEvents.start("shadow update")
						workingShadow.update()
						profilerEvents.end()
						
						this.thread!!.priority = Thread.NORM_PRIORITY - 1
						while (completedWorkCounter.getOpaque() < systems.size() && !shutdown) {
							Thread.yield()
//							try {
//								Thread.sleep(10)
//							} catch (e: InterruptedException) {
//								if (completedWorkCounter.get() == systems.size() || shutdown) {
//									break
//								}
//							}
						}
						profilerEvents.end()
						this.thread!!.priority = Thread.NORM_PRIORITY
						
						profilerEvents.start("shadows lock")
						shadowLock.withLock {
							profilerEvents.start("promote shadows")
							systems.forEachFast { system ->
								val oldShadowWorld = system.shadow
								
								system.shadow = system.workingShadow
								system.workingShadow = oldShadowWorld
							}
							
							val oldShadowWorld = shadow
							
							shadow = workingShadow
							workingShadow = oldShadowWorld
							profilerEvents.end()
						}
						profilerEvents.end()
						
						val systemUpdateDuration = (System.nanoTime() - systemUpdateStart)
						speedLimited = systemUpdateDuration > speed
						
						if (speedLimited) {
//							log.warn("Galaxy update took ${Units.nanoToString(systemUpdateDuration)} which is more than the requested speed delay ${Units.nanoToString(speed)}")
//							println("Galaxy update took ${Units.nanoToString(systemUpdateDuration)} which is more than the requested speed delay ${Units.nanoToString(speed)}")
						}

//						for (system in systems) {
//							print("${system.sid} ${Units.nanoToString(system.updateTime)}, ")
//						}
//						println()
						
						// If one system took a noticeable larger time to process than others, schedule it earlier
						profilerEvents.start("system sort")
						systems.sort(object : Comparator<StarSystem> {
							val s = tickSpeed / 10
							override fun compare(o1: StarSystem, o2: StarSystem): Int {
								val diff = o1.updateTime - o2.updateTime
								
								if (diff > s) return -1
								if (diff < -s) return 1
								return 0
							}
						})
						profilerEvents.end()
						
						lastProcess = now;
					}

					lastSleep = now;
					
					// If we are more than 10 ticks behind limit counting
					if (accumulator >= speed * 10) {
						accumulator = speed * 10L
						
					} else if (accumulator < speed) {

						var sleepTime = (speed - accumulator) / Units.NANO_MILLI

						if (sleepTime > 1) {
							sleeping = true
							ThreadUtils.sleep(sleepTime - 1)
							sleeping = false
							
						} else if ((speed - accumulator) / Units.NANO_MICRO > 10) {
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
	
	object GalaxyWorker : Runnable {
		@JvmStatic
		val log = LogManager.getLogger(GalaxyWorker::class.java)
		
		@JvmStatic
		lateinit var galaxy: Galaxy
 
		override fun run() {
			try {
				while(!galaxy.shutdown) {
					synchronized(galaxy.threadsCondition) {
						if (galaxy.takenWorkCounter.get() >= galaxy.systems.size()) {
//							println("sleep ${Thread.currentThread().name}")
							galaxy.threadsCondition.wait()
//							Thread.yield()
//							try {
//								Thread.sleep(Long.MAX_VALUE)
//							} catch(e: InterruptedException) {}
//							println("wake ${Thread.currentThread().name}")
						}
					}
					
					if (galaxy.shutdown) {
						return
					}
					
					var systemIndex = galaxy.takenWorkCounter.getAndIncrement()
					
//					println("index ${Thread.currentThread().name} = $systemIndex")
					
					while (systemIndex < galaxy.systems.size()) {
						
						val system = galaxy.systems[systemIndex]
						
						try {
							val systemUpdateStart = System.nanoTime()
							system.update(galaxy.tickSize)
							system.updateTime = (System.nanoTime() - systemUpdateStart)
			 
							system.updateTimeAverage = exponentialAverage(system.updateTime.toDouble(), system.updateTimeAverage, FastMath.min(100.0, (Units.NANO_SECOND / FastMath.abs(galaxy.speed)).toDouble()))
							
						} catch (t: Throwable) {
							log.error("Exception in system update for $system tick ${galaxy.time}", t)
							galaxy.speed = 0
							
						} finally {
							if (galaxy.completedWorkCounter.incrementAndGet() == galaxy.systems.size()) {
//								galaxy.thread!!.interrupt()
								break
							}
						}
						
						systemIndex = galaxy.takenWorkCounter.getAndIncrement()
					}
				}
			} catch(t: Throwable) {
				log.error("", t);
			}
		}
	}
	
	override fun inserted(entityIDs: IntBag) {
		entityIDs.forEachFast { entityID ->
			workingShadow.added.unsafeSet(entityID)
		}
	}
	
	override fun removed(entityIDs: IntBag) {
		entityIDs.forEachFast { entityID ->
			workingShadow.deleted.unsafeSet(entityID)
		}
	}
 
	fun updateSpeed() {
		
		var lowestRequestedSpeed = Long.MAX_VALUE;

		players.forEachFast{ player ->
			lowestRequestedSpeed = Math.min(lowestRequestedSpeed, player.requestedSpeed)
		}
		
		speed = lowestRequestedSpeed
		
		if (speed > 0 && sleeping) {
			thread!!.interrupt()
		}
	}

	fun getEmpire(id: Int): Empire {
		empires.forEachFast{ empire ->
			if (empire.id == id) {
				return empire;
			}
		}
		throw IllegalArgumentException("$id")
	}
	
	fun getStarSystemBySID(sid: Int): StarSystem {
		systems.forEachFast{ system ->
			if (system.sid == sid) {
				return system;
			}
		}
		throw IllegalArgumentException("$sid")
	}
	
	fun getStarSystemByGalacticEntityID(id: Int): StarSystem {
		systems.forEachFast{ system ->
			if (system.galacticEntityID == id) {
				return system;
			}
		}
		throw IllegalArgumentException("$id")
	}
	
	fun resolveEntityReference(entityReference: EntityReference): EntityReference? {
		
		if (entityReference.system.isEntityReferenceValid(entityReference)) {
			return entityReference
		}
		
		return getEntityReferenceByUUID(entityReference.entityUUID, entityReference)
	}
	
	fun getEntityReferenceByUUID(entityUUID: EntityUUID, oldEntityReference: EntityReference? = null): EntityReference? {
		
		systems.forEachFast{ system ->
			val entityID = system.getEntityByUUID(entityUUID)
			
			if (entityID != null) {
				if (oldEntityReference != null) {
					return system.updateEntityReference(entityID, oldEntityReference)
				} else {
					return system.getEntityReference(entityID)
				}
			}
		}
		
		return null
	}
	
	// If traveling non-instant move entity when at midpoint between systems
	fun moveEntity(entityID: Int, sourceSystem: StarSystem, targetSystem: StarSystem,  targetPosition: MovementValues) {
		val sourceWorld = sourceSystem.world
		
		sourceSystem.solarSystemMapper.remove(entityID)
		sourceSystem.changingWorldMapper.create(entityID)
		
		//TODO could we just move it? or does that cause problems
		//TODO serialize entity, add to target system, remove from old system
	}

	private fun updateDay(): Int {
		day = (time / (24L * 60L * 60L)).toInt()
		return day
	}
	
	override fun dispose() {
		shutdown = true
		synchronized(threadsCondition) {
			threadsCondition.notifyAll()
		}
		
		thread?.join()
		
		systems.forEachFast { system ->
			system.dispose()
		}
		
		world.dispose()
		shadow.dispose()
		workingShadow.dispose()
	}
}