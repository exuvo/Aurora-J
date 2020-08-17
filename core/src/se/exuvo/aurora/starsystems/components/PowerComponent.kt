package se.exuvo.aurora.starsystems.components

import com.artemis.Component
import com.artemis.PooledComponent
import se.exuvo.aurora.galactic.Battery
import se.exuvo.aurora.galactic.Part
import se.exuvo.aurora.galactic.Reactor
import se.exuvo.aurora.galactic.SolarPanel
import kotlin.reflect.KClass
import java.security.InvalidParameterException
import se.exuvo.aurora.galactic.PartRef

class PowerComponent() : PooledComponent(), CloneableComponent<PowerComponent> {
	lateinit var powerScheme: PowerScheme
	var stateChanged = true
	var totalAvailablePower = 0L
	var totalAvailableSolarPower = 0L
	var totalRequestedPower = 0L
	var totalUsedPower = 0L
	val poweringParts = ArrayList<PartRef<Part>>()
	val poweredParts = ArrayList<PartRef<Part>>()
	val chargedParts = ArrayList<PartRef<Part>>()
	
	fun set(powerScheme: PowerScheme): PowerComponent {
		this.powerScheme = powerScheme
		stateChanged = true
		return this
	}
	
	override fun reset() {
		totalAvailablePower = 0L
		totalAvailableSolarPower = 0L
		totalRequestedPower = 0L
		totalUsedPower = 0L
		poweringParts.clear()
		poweredParts.clear()
		chargedParts.clear()
	}
	override fun copy(tc: PowerComponent) {
		tc.powerScheme = powerScheme
		tc.stateChanged = stateChanged
		tc.totalAvailablePower = totalAvailablePower
		tc.totalAvailableSolarPower = totalAvailableSolarPower
		tc.totalRequestedPower = totalRequestedPower
		tc.totalUsedPower = totalUsedPower
		
		if (poweringParts.hashCode() != tc.poweringParts.hashCode()) {
			tc.poweringParts.clear()
			tc.poweringParts.addAll(poweringParts)
		}
		
		if (poweredParts.hashCode() != tc.poweredParts.hashCode()) {
			tc.poweredParts.clear()
			tc.poweredParts.addAll(poweredParts)
		}
		
		if (chargedParts.hashCode() != tc.chargedParts.hashCode()) {
			tc.chargedParts.clear()
			tc.chargedParts.addAll(chargedParts)
		}
	}
	
	fun simpleCopy(tc: PowerComponent) {
		tc.powerScheme = powerScheme
		tc.stateChanged = stateChanged
		tc.totalAvailablePower = totalAvailablePower
		tc.totalAvailableSolarPower = totalAvailableSolarPower
		tc.totalRequestedPower = totalRequestedPower
		tc.totalUsedPower = totalUsedPower
	}
	
	fun simpleEquals(tc: PowerComponent): Boolean {
		return tc.powerScheme == powerScheme &&
		tc.stateChanged == stateChanged &&
		tc.totalAvailablePower == totalAvailablePower &&
		tc.totalAvailableSolarPower == totalAvailableSolarPower &&
		tc.totalRequestedPower == totalRequestedPower &&
		tc.totalUsedPower == totalUsedPower
	}
	
}

enum class PowerScheme(val chargeBatteryFromReactor: Boolean, private val powerTypeCompareMap: Map<KClass<out Part>, Int>) {
	SOLAR_BATTERY_REACTOR(false, mapOf(SolarPanel::class to 1, Battery::class to 2, Reactor::class to 3)),
	SOLAR_REACTOR_BATTERY(true, mapOf(SolarPanel::class to 1, Reactor::class to 2, Battery::class to 3));

	fun getPowerTypeValue(part: Part): Int {
		powerTypeCompareMap.entries.forEach { entry ->
			if (entry.key.isInstance(part)) {
				return entry.value
			}
		}
		
		throw InvalidParameterException()
	}
}