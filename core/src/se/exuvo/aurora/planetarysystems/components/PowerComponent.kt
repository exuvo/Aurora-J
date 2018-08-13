package se.exuvo.aurora.planetarysystems.components

import com.badlogic.ashley.core.Component
import se.exuvo.aurora.galactic.Battery
import se.exuvo.aurora.galactic.Part
import se.exuvo.aurora.galactic.Reactor
import se.exuvo.aurora.galactic.SolarPanel
import kotlin.reflect.KClass
import java.security.InvalidParameterException
import se.exuvo.aurora.galactic.PoweringPart
import se.exuvo.aurora.galactic.ChargedPart
import se.exuvo.aurora.galactic.PoweredPart
import se.exuvo.aurora.galactic.PartRef

class PowerComponent(var powerScheme: PowerScheme) : Component {
	var stateChanged = true
	var totalAvailiablePower = 0L
	var totalAvailiableSolarPower = 0L
	var totalRequestedPower = 0L
	var totalUsedPower = 0L
	val poweringParts = ArrayList<PartRef<Part>>()
	val poweredParts = ArrayList<PartRef<Part>>()
	val chargedParts = ArrayList<PartRef<Part>>()
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