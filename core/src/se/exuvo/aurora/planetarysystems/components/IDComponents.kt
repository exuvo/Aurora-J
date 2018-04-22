package se.exuvo.aurora.planetarysystems.components

import com.badlogic.ashley.core.Component
import se.exuvo.aurora.galactic.Empire
import se.exuvo.aurora.planetarysystems.PlanetarySystem
import se.exuvo.aurora.utils.EncryptionUtils

data class UUIDComponent(val uuid: EntityUUID) : Component

data class EntityUUID(val planetarySystemID: Int, val empireID: Int, val shipID: Long) {

	override fun toString(): String = "$planetarySystemID:$empireID:$shipID"

	private val hashcode: Int by lazy {
		var hash = 1
		hash = 37 * hash + planetarySystemID
		hash = 37 * hash + empireID
		hash = 37 * hash + (shipID xor (shipID shr 32)).toInt()
		hash
	}

	override fun hashCode(): Int = hashcode
	
	val dispersedHash: Int by lazy { EncryptionUtils.stringDigester.digest(toString()).hashCode() }
}

data class NameComponent(var name: String = "") : Component
