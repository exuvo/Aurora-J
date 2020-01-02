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

class EntityReference() {
	lateinit var system: PlanetarySystem
	var entityID: Int = -1
	lateinit var entityUUID: EntityUUID
	
	fun set(system: PlanetarySystem, entityID: Int, entityUUID: EntityUUID): EntityReference {
		this.system = system
		this.entityID = entityID
		this.entityUUID = entityUUID
		return this
	}
	
	override fun toString(): String = "$system:$entityID"

	override fun hashCode(): Int = entityUUID.hashCode()
}

class NameComponent() : PooledComponent() {
	lateinit var name: String
	
	fun set(name: String): NameComponent {
		this.name = name
		return this
	}
	
	override fun reset(): Unit {}
}
