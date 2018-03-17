package se.exuvo.aurora.utils

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import se.exuvo.aurora.planetarysystems.components.UUIDComponent


private val uuidMapper = ComponentMapper.getFor(UUIDComponent::class.java)

fun Entity.printUUID(): String {

	val uuid = uuidMapper.get(this)

	if (uuid != null) {
		return uuid.uuid.toString();
	}

	return this.toString();
}

