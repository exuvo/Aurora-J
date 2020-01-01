package se.exuvo.aurora.planetarysystems.components

import com.artemis.Component
import se.exuvo.aurora.galactic.Empire
import se.exuvo.aurora.planetarysystems.PlanetarySystem
import se.exuvo.aurora.utils.GameUtils
import com.artemis.PooledComponent

class UUIDComponent() : PooledComponent() {
	lateinit var uuid: EntityUUID
	
	fun set(uuid: EntityUUID): UUIDComponent {
		this.uuid = uuid
		return this
	}
	
	override fun reset(): Unit {}
}

data class EntityUUID(val planetarySystemID: Int, val empireID: Int, val entityUID: Long) {
	
	override fun toString(): String = "$planetarySystemID:$empireID:$entityUID"

	private val hashcode: Int by lazy (LazyThreadSafetyMode.NONE) {
		var hash = 1
		hash = 37 * hash + planetarySystemID
		hash = 37 * hash + empireID
		hash = 37 * hash + (entityUID xor (entityUID shr 32)).toInt()
		hash
	}

	override fun hashCode(): Int = hashcode
	
	val dispersedHash: Int by lazy (LazyThreadSafetyMode.NONE) { GameUtils.stringDigester.digest(toString()).hashCode() }
}

data class EntityReference(var system: PlanetarySystem, var entityID: Int, var entityUUID: EntityUUID) {
	
	override fun toString(): String = "$system:$entityID"

	private val hashcode: Int by lazy (LazyThreadSafetyMode.NONE) {
		var hash = 1
		hash = 37 * hash + system.sid
		hash = 37 * hash + entityID
		hash = 37 * hash + entityUUID.hashCode()
		hash
	}

	override fun hashCode(): Int = hashcode
}

class NameComponent() : PooledComponent() {
	lateinit var name: String
	
	fun set(name: String): NameComponent {
		this.name = name
		return this
	}
	
	override fun reset(): Unit {}
}
