package se.exuvo.aurora.starsystems.events

import net.mostlyoriginal.api.event.common.Event
import net.mostlyoriginal.api.utils.pooling.Poolable
import net.mostlyoriginal.api.utils.pooling.PoolsCollection
import kotlin.reflect.KClass

class PowerEvent(var entityID: Int = -1): Event {
	fun set(entityID: Int): PowerEvent {
		this.entityID = entityID
		return this
	}
}

class NonLinearMovementEvent(var entityID: Int = -1): Event {
	fun set(entityID: Int): NonLinearMovementEvent {
		this.entityID = entityID
		return this
	}
}