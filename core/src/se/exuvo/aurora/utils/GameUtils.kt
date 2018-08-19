package se.exuvo.aurora.utils

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import org.apache.log4j.Logger
import org.jasypt.digest.StandardStringDigester
import se.exuvo.aurora.galactic.FuelWastePart
import se.exuvo.aurora.galactic.FueledPart
import se.exuvo.aurora.galactic.Part
import se.exuvo.aurora.galactic.PartRef
import se.exuvo.aurora.planetarysystems.components.FueledPartState
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.planetarysystems.components.ShipComponent
import se.exuvo.aurora.planetarysystems.components.UUIDComponent


private val uuidMapper = ComponentMapper.getFor(UUIDComponent::class.java)
private val nameMapper = ComponentMapper.getFor(NameComponent::class.java)
private val log = Logger.getLogger("se.exuvo.aurora.utils")

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

fun consumeFuel(deltaGameTime: Int, entity: Entity, ship: ShipComponent, partRef: PartRef<Part>, energyConsumed: Long, fuelEnergy: Long) {
	val part = partRef.part
	if (part is FueledPart) {
		val fueledState = ship.getPartState(partRef)[FueledPartState::class]

		var fuelEnergyConsumed = deltaGameTime * energyConsumed
//					println("fuelEnergyConsumed $fuelEnergyConsumed, fuelTime ${TimeUnits.secondsToString(part.fuelTime.toLong())}, power ${poweringState.producedPower.toDouble() / part.power}%")

		if (fuelEnergyConsumed > fueledState.fuelEnergyRemaining) {

			fuelEnergyConsumed -= fueledState.fuelEnergyRemaining

			var fuelRequired = part.fuelConsumption

			if (fuelEnergyConsumed > fuelEnergy * part.fuelTime) {

				fuelRequired *= (1 + fuelEnergyConsumed / (fuelEnergy * part.fuelTime)).toInt()
			}

			val remainingFuel = ship.getCargoAmount(part.fuel)
			fuelRequired = Math.min(fuelRequired, remainingFuel)

			if (part is FuelWastePart) {

				var fuelConsumed = Math.ceil((part.fuelConsumption * fueledState.fuelEnergyRemaining).toDouble() / (fuelEnergy * part.fuelTime)).toInt()

				if (fuelEnergyConsumed > fuelEnergy * part.fuelTime) {

					fuelConsumed += fuelRequired / part.fuelConsumption - 1
				}

//							println("fuelConsumed $fuelConsumed")

				ship.addCargo(part.waste, fuelConsumed)
			}

			fueledState.fuelEnergyRemaining = Math.max(fuelEnergy * part.fuelTime * fuelRequired - fuelEnergyConsumed, 0)

			val removedFuel = ship.retrieveCargo(part.fuel, fuelRequired)

			if (removedFuel != fuelRequired) {
				log.warn("Entity ${entity.printID()} was expected to consume $fuelRequired but only had $removedFuel left")
			}

		} else {

			if (part is FuelWastePart) {

				val fuelRemainingPre = Math.ceil((part.fuelConsumption * fueledState.fuelEnergyRemaining).toDouble() / (fuelEnergy * part.fuelTime)).toInt()
				val fuelRemainingPost = Math.ceil((part.fuelConsumption * (fueledState.fuelEnergyRemaining - fuelEnergyConsumed)).toDouble() / (fuelEnergy * part.fuelTime)).toInt()
				val fuelConsumed = fuelRemainingPre - fuelRemainingPost;

//							println("fuelRemainingPre $fuelRemainingPre, fuelRemainingPost $fuelRemainingPost, fuelConsumed $fuelConsumed")

				if (fuelConsumed > 0) {
					ship.addCargo(part.waste, fuelConsumed)
				}
			}

			fueledState.fuelEnergyRemaining -= fuelEnergyConsumed
		}
	}
}

object EncryptionUtils {

	public val stringDigester: StandardStringDigester

	init {
		stringDigester = StandardStringDigester()
		stringDigester.initialize()
	}
}