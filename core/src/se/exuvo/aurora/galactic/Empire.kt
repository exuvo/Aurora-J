package se.exuvo.aurora.galactic

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.PooledEngine
import org.apache.log4j.Logger
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.concurrent.atomic.AtomicInteger


class Empire(var name: String) {
companion object {
		val empireIDGenerator = AtomicInteger()
	}

	val id = empireIDGenerator.getAndIncrement()
	val log = Logger.getLogger(this.javaClass)
	val lock = ReentrantReadWriteLock()

	var funds: Long = 0
	val colonies = ArrayList<Entity>()

}