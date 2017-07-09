package se.exuvo.aurora.shipcomponents

class ShipClass {
	var name: String = ""
	var designDay: Int? = null
	val parts: MutableList<Part> = ArrayList()
	var armorLayers = 1

	operator fun <T : Any> get(partClass: Class<T>) = parts.filterIsInstance(partClass)

	fun put(part: Part) {
		parts.add(part)
	}

	fun getVolume(): Int {
		return parts.sumBy { it.cost.values.sum() }
	}

	fun getSurfaceArea(): Int {
		// V = πr^2h, http://mathhelpforum.com/geometry/170076-how-find-cylinder-dimensions-volume-aspect-ratio.html
		val volume = getVolume()

		val lengthToDiameterRatio = 2.0
		val length = Math.pow(Math.pow(2.0, 2 * lengthToDiameterRatio) * volume / Math.PI, 1.0 / 3)
		val radius = Math.sqrt(volume / Math.PI / length)

		val surface = 2 * Math.PI * radius * length + 2 * Math.PI * radius * radius

//		println("length $length, diameter ${2 * radius}, surface $surface, volume ${Math.PI * length * radius * radius}")

		return surface.toInt()
	}
}

enum class Resources(val density: Float) { // Amounts should be stored in grams of mass
	GENERIC(1f), // Steel, Concrete, Carbonfiber, Glass, Ceramics
	METAL_LIGHT(1f), // Aluminium, Titanium
	METAL_CONDUCTIVE(1f), // Copper, Gold
	SEMICONDUCTORS(1f), // Silicon, germanium
	RARE_EARTH(1f), // Neodymium. https://en.wikipedia.org/wiki/Rare-earth_element
	NUCLEAR_FISSION(1f), // Uranium, Thorium
	NUCLEAR_FUSION(1f), // Deuterium-Tritium, Helium3. See 'Fusion Reactor Fuel Modes' at http://forum.kerbalspaceprogram.com/index.php?/topic/155255-12213-kspi-extended-11414-05-7-2017-support-release-thread/
	ROCKET_FUEL(1f); // LOX + kerosene, LOX + H, nitrogen tetroxide + hydrazine. https://en.wikipedia.org/wiki/Rocket_propellant#Liquid_propellants

	companion object {
		val MINERALS = listOf(Resources.GENERIC, Resources.METAL_LIGHT, Resources.METAL_CONDUCTIVE, Resources.SEMICONDUCTORS, Resources.RARE_EARTH)
	}
}

abstract class Part {
	var name: String = ""
	var designDay: Int? = null
	var cost: MutableMap<Resources, Int> = LinkedHashMap()
	var size: Int = 1 // In m3
}

abstract class ContainerPart(val capacity: Int) : Part();
abstract class ResourceContainerPart(capacity: Int, val acceptedResources: List<Resources>) : ContainerPart(capacity);

class FuelContainerPart(capacity: Int) : ResourceContainerPart(capacity, listOf(Resources.ROCKET_FUEL));
class MineralContainerPart(capacity: Int) : ResourceContainerPart(capacity, Resources.MINERALS);
class NuclearContainerPart(capacity: Int) : ResourceContainerPart(capacity, listOf(Resources.NUCLEAR_FISSION, Resources.NUCLEAR_FUSION));

interface FueledPart {
	var fuel: Resources
	var fuelConsumption: Int // gram per second usage
}

class FueledPartImpl(override var fuel: Resources, override var fuelConsumption: Int) : FueledPart

class Reactor : Part(), FueledPart by FueledPartImpl(Resources.NUCLEAR_FISSION, 1) {
	var power: Int = 0 // In kW/s
}

// Electrical https://en.wikipedia.org/wiki/Electrically_powered_spacecraft_propulsion#Types
open class Thruster : Part() {
	var thrust: Float = 0f // In N
}

// Chemical: Hybrid, Bipropellant, Tripropellant. https://en.wikipedia.org/wiki/Rocket_engine#Chemically_powered
// Nuclear https://en.wikipedia.org/wiki/Nuclear_pulse_propulsion
class FueledThruster : Thruster(), FueledPart by FueledPartImpl(Resources.ROCKET_FUEL, 1)