package se.exuvo.aurora.galactic

import se.exuvo.aurora.planetarysystems.components.PowerScheme

class ShipClass {
	var name: String = ""
	var designDay: Int? = null
	val parts: MutableList<Part> = ArrayList()
	var armorLayers = 1
	var preferredCargo: Map<Resource, Int> = LinkedHashMap()
	var preferredItemCargo: MutableList<Part> = ArrayList()
	var powerScheme: PowerScheme = PowerScheme.SOLAR_BATTERY_REACTOR
	//TODO default weapon assignments

	operator fun <T : Any> get(partClass: Class<T>) = parts.filterIsInstance(partClass)

	fun put(part: Part) {
		parts.add(part)
	}

	fun getCrewRequirement(): Int {
		return parts.sumBy { it.crewRequirement }
	}

	fun getVolume(): Int {
		return parts.sumBy { it.getVolume() }
	}

	fun getSurfaceArea(): Int {
		val volume = getVolume()

		// V = πr^2h, http://mathhelpforum.com/geometry/170076-how-find-cylinder-dimensions-volume-aspect-ratio.html
		val lengthToDiameterRatio = 2.0
		val length = Math.pow(Math.pow(2.0, 2 * lengthToDiameterRatio) * volume / Math.PI, 1.0 / 3)
		val radius = Math.sqrt(volume / Math.PI / length)

		val surface = 2 * Math.PI * radius * length + 2 * Math.PI * radius * radius

//		println("length $length, diameter ${2 * radius}, surface $surface, volume ${Math.PI * length * radius * radius}")

		return surface.toInt()
	}
}

