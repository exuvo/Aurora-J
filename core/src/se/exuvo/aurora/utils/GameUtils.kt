package se.exuvo.aurora.utils

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import se.exuvo.aurora.planetarysystems.components.UUIDComponent
import se.exuvo.aurora.planetarysystems.components.NameComponent
import org.jasypt.util.password.BasicPasswordEncryptor
import org.jasypt.digest.StandardStringDigester


private val uuidMapper = ComponentMapper.getFor(UUIDComponent::class.java)
private val nameMapper = ComponentMapper.getFor(NameComponent::class.java)

fun Entity.printUUID(): String {

	val uuid = uuidMapper.get(this)

	if (uuid != null) {
		return uuid.uuid.toString();
	}

	return this.toString();
}


fun Entity.printName(): String {

	val nameComponent = nameMapper.get(this)

	if (nameComponent != null) {
		return nameComponent.name
	}

	return "";
}

fun Entity.printID(): String {

	return "${this.printName()} (${this.printUUID()})"
}

object EncryptionUtils {
	
	public val stringDigester: StandardStringDigester
	
	init {
		stringDigester = StandardStringDigester()
		stringDigester.initialize()
	}
}