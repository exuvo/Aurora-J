package se.exuvo.aurora.planetarysystems.events

import net.mostlyoriginal.api.event.common.Event
import net.mostlyoriginal.api.utils.pooling.Poolable
import com.artemis.annotations.PooledWeaver
import net.mostlyoriginal.api.utils.pooling.PoolsCollection
import kotlin.reflect.KClass

private val pools = PoolsCollection()

fun <T: Event> getEvent(eventClass: KClass<T>) = pools.obtain(eventClass.java)

class PowerEvent(var entityID: Int = -1): Event {
	fun set(entityID: Int): PowerEvent {
		this.entityID = entityID
		return this
	}
}