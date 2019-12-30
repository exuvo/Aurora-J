package se.exuvo.aurora.utils

import org.apache.logging.log4j.Logger
import org.jasypt.digest.StandardStringDigester
import se.exuvo.aurora.galactic.FuelWastePart
import se.exuvo.aurora.galactic.FueledPart
import se.exuvo.aurora.galactic.Part
import se.exuvo.aurora.galactic.PartRef
import se.exuvo.aurora.planetarysystems.components.FueledPartState
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.planetarysystems.components.ShipComponent
import se.exuvo.aurora.planetarysystems.components.UUIDComponent
import com.artemis.Entity
import com.artemis.ComponentMapper
import com.artemis.utils.IntBag
import com.badlogic.gdx.Gdx
import org.apache.logging.log4j.LogManager
import com.artemis.utils.Bag


private val log = LogManager.getLogger("se.exuvo.aurora.utils")

inline fun IntBag.forEach(action: (entityID: Int) -> Unit) {
	for (i in 0 .. size() - 1) {
		action(data[i])
	}
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T> Bag<T>.isNotEmpty(): Boolean = !isEmpty()

operator fun <T> Bag<T>.plusAssign(element: T): Unit  {
	add(element)
}

fun Entity.getUUID(): String {

	return world.getMapper(UUIDComponent::class.java).get(this)?.uuid.toString();
}

fun Entity.printName(): String {

	val nameComponent = world.getMapper(NameComponent::class.java).get(this)

	if (nameComponent != null) {
		return nameComponent.name
	}

	return "";
}

fun Entity.printID(): String {

	return "${this.printName()} (${this.getUUID()})"
}

inline fun <T> Iterable<T>.sumByLong(selector: (T) -> Long): Long {
    var sum = 0L
    for (element in this) {
        sum += selector(element)
    }
    return sum
}

fun exponentialAverage(newValue: Double, expAverage: Double, delay: Double) : Double = newValue + Math.pow(Math.E, -1/delay) * (expAverage - newValue)

fun consumeFuel(deltaGameTime: Int, entity: Entity, ship: ShipComponent, partRef: PartRef<Part>, energyConsumed: Long, fuelEnergy: Long) {
	val part = partRef.part
	if (part is FueledPart) {
		val fueledState = ship.getPartState(partRef)[FueledPartState::class]

		var fuelEnergyConsumed = deltaGameTime * energyConsumed
//					println("fuelEnergyConsumed $fuelEnergyConsumed, fuelTime ${TimeUnits.secondsToString(part.fuelTime.toLong())}, power ${poweringState.producedPower.toDouble() / part.power}%")

		if (fuelEnergyConsumed > fueledState.fuelEnergyRemaining) {

			fuelEnergyConsumed -= fueledState.fuelEnergyRemaining

			var fuelRequired = part.fuelConsumption.toLong()

			if (fuelEnergyConsumed > fuelEnergy * part.fuelTime) {

				fuelRequired *= (1 + fuelEnergyConsumed / (fuelEnergy * part.fuelTime)).toInt()
			}

			val remainingFuel = ship.getCargoAmount(part.fuel)
			fuelRequired = Math.min(fuelRequired, remainingFuel)

			if (part is FuelWastePart) {

				var fuelConsumed = Math.ceil((part.fuelConsumption * fueledState.fuelEnergyRemaining).toDouble() / (fuelEnergy * part.fuelTime)).toLong()

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

				val fuelRemainingPre = Math.ceil((part.fuelConsumption * fueledState.fuelEnergyRemaining).toDouble() / (fuelEnergy * part.fuelTime)).toLong()
				val fuelRemainingPost = Math.ceil((part.fuelConsumption * (fueledState.fuelEnergyRemaining - fuelEnergyConsumed)).toDouble() / (fuelEnergy * part.fuelTime)).toLong()
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

object GameUtils {

	public val stringDigester: StandardStringDigester

	init {
		stringDigester = StandardStringDigester()
		stringDigester.initialize()
	}
}