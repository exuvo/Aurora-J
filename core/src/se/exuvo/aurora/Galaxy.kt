package se.exuvo.aurora

import com.badlogic.gdx.Preferences
import org.apache.log4j.Logger
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.NanoTimeUnits
import se.unlogic.standardutils.threads.SimpleTaskGroup
import se.unlogic.standardutils.threads.ThreadPoolTaskGroupHandler
import se.unlogic.standardutils.threads.ThreadUtils
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.locks.ReentrantReadWriteLock

class Galaxy(val systems: List<SolarSystem>, var time: Long = 0) : Runnable {

	val log = Logger.getLogger(this.javaClass)
	val preferences = GameServices[Preferences::class.java]

	private val threadPool = ThreadPoolTaskGroupHandler<SimpleTaskGroup>(preferences.getInteger("Galaxy.threads", Runtime.getRuntime().availableProcessors()), true)
	private val queue = LinkedBlockingQueue<UpdateSystemTask>()
	var thread: Thread? = null
	var sleeping = false

	var day: Int = 0
	var speed: Long = 1 * NanoTimeUnits.SECOND

	fun init() {
		GameServices.put(this)
		systems.forEach { it.init() }

		val thread = Thread(this, "Galaxy");
		thread.setDaemon(true);
		thread.start();

		this.thread = thread
	}

	private fun updateDay() {
		day = (time / (60L * 60L * 24L)).toInt()
	}

	override fun run() {

		try {
			var accumulator = 0L
			var oldSpeed = speed
			var lastSleep = System.nanoTime()

			while (true) {
				var now = System.nanoTime()

				if (oldSpeed != speed) {
					accumulator = 0
				} else {
					accumulator += now - lastSleep;
				}

				if (accumulator >= speed) {

					accumulator -= speed;

					var tickSize: Int = if (speed > 1 * NanoTimeUnits.MILLI) 1 else (NanoTimeUnits.MILLI / speed).toInt()

					// max sensible tick size is 1 hour or 3600s
					if (tickSize > 3600) {
						tickSize = 3600
					}

//					println("tickSize $tickSize, speed $speed, accumulator $accumulator, diff ${now - lastSleep}")

					time += tickSize;
					updateDay()

					if (queue.isNotEmpty()) {
						throw RuntimeException("Queue should be empty here!")
					}

					systems.forEach { queue.add(UpdateSystemTask(it, tickSize)) }

					val taskGroup = SimpleTaskGroup(queue)

					val executionController = threadPool.execute(taskGroup)

					val systemUpdateStart = System.nanoTime()

//					println("start")
					executionController.start()
//					println("awaitExecution")
					executionController.awaitExecution()
//					println("end")

					val systemUpdateDuration = (System.nanoTime() - systemUpdateStart)
//					log.debug("Galaxy update took " + NanoTimeUnits.nanoToString(systemUpdateDuration))

					//TODO handle tick abortion and tickSize lowering

//				val systemCommitStart = System.currentTimeMillis()
//				systems.forEach { it.commitChanges() }
//				val systemCommitDuration = System.currentTimeMillis() - systemCommitStart

//				log.debug("Galaxy commit took " + TimeUtils.millisecondsToString(systemCommitDuration))
				}

				// If we are more than 10 ticks behind stop counting
				if (accumulator >= speed * 10) {
					accumulator = speed * 10L
				}

				lastSleep = now;
				oldSpeed = speed

				if (accumulator < speed) {

					var sleepTime = (speed - accumulator) / NanoTimeUnits.MILLI

					if (sleepTime > 0) {
						sleeping = true
						ThreadUtils.sleep(sleepTime)
						sleeping = false
					}
				}
			}
		} catch (t: Throwable) {
			log.error("Exception in galaxy loop", t)
		}
	}

	class UpdateSystemTask(val system: SolarSystem, val deltaGameTime: Int) : Runnable {

		val log by lazy { Logger.getLogger(this.javaClass) }

		override fun run() {
			try {
				system.update(deltaGameTime)
			} catch (t: Throwable) {
				log.error("Exception in system update", t)
			}
		}
	}
}