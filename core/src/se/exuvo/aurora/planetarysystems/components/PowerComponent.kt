package se.exuvo.aurora.planetarysystems.components

import com.badlogic.ashley.core.Component
import se.exuvo.aurora.galactic.Battery
import se.exuvo.aurora.galactic.Part
import se.exuvo.aurora.galactic.Reactor
import se.exuvo.aurora.galactic.SolarPanel
import kotlin.reflect.KClass
import java.security.InvalidParameterException

class PowerComponent(var powerScheme: PowerScheme) : Component {
	var stateChanged = true
	var totalAvailiablePower = 0
	var totalRequestedPower = 0
	var totalUsedPower = 0
	val poweringParts = ArrayList<Part>()
	val poweredParts = ArrayList<Part>()
	val chargedParts = ArrayList<Part>()
}

enum class PowerScheme(val chargeBatteryFromReactor: Boolean, private val powerTypeCompareMap: Map<KClass<out Part>, Int>) {
	SOLAR_BATTERY_REACTOR(false, mapOf(SolarPanel::class to 1, Battery::class to 2, Reactor::class to 3)),
	SOLAR_REACTOR_BATTERY(true, mapOf(SolarPanel::class to 1, Reactor::class to 2, Battery::class to 3));

	fun getPowerTypeValue(part: Part): Int {
		powerTypeCompareMap.entries.forEach({
			val entry = it

			if (entry.key.isInstance(part)) {
				return entry.value
			}
		})
		
		throw InvalidParameterException()
	}
}