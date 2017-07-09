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
	
	fun getWidth() : Int {
		return 1 + parts.sumBy { it.cost.values.sum() } / 1000
	}
}

enum class Resource { // Amounts should be stored in grams of mass
	GENERIC, // Steel, Concrete, Carbonfiber, Glass, Ceramics
	METAL_LIGHT, // Aluminium, Titanium
	METAL_CONDUCTIVE, // Copper, Gold
	SEMICONDUCTORS, // Silicon, germanium
	RARE_EARTH, // Neodymium. https://en.wikipedia.org/wiki/Rare-earth_element
	NUCLEAR_FISSION, // Uranium, Thorium
	NUCLEAR_FUSION, // Deuterium-Tritium, Helium3. See 'Fusion Reactor Fuel Modes' at http://forum.kerbalspaceprogram.com/index.php?/topic/155255-12213-kspi-extended-11414-05-7-2017-support-release-thread/
	ROCKET_FUEL; // LOX + kerosene, LOX + H, nitrogen tetroxide + hydrazine. https://en.wikipedia.org/wiki/Rocket_propellant#Liquid_propellants
	companion object {
		val MINERALS = listOf(Resource.GENERIC, Resource.METAL_LIGHT, Resource.METAL_CONDUCTIVE, Resource.SEMICONDUCTORS, Resource.RARE_EARTH)
	}
}

abstract class Part {
	var name: String = ""
	var designDay: Int? = null
	var cost: MutableMap<Resource, Int> = LinkedHashMap()
}

abstract class ResourceContainerPart(val size: Int, val acceptedResources: List<Resource>) : Part() {
	var resources: MutableMap<Resource, Int> = LinkedHashMap()
}

class FuelContainerPart(size: Int) : ResourceContainerPart(size, listOf(Resource.ROCKET_FUEL));
class MineralContainerPart(size: Int) : ResourceContainerPart(size, Resource.MINERALS);
class NuclearContainerPart(size: Int) : ResourceContainerPart(size, listOf(Resource.NUCLEAR_FISSION, Resource.NUCLEAR_FUSION));

interface FueledPart {
	var fuel: Resource
	var fuelConsumption: Int // gram per second usage
}

class FueledPartImpl(override var fuel: Resource, override var fuelConsumption: Int) : FueledPart

class Reactor : Part(), FueledPart by FueledPartImpl(Resource.NUCLEAR_FISSION, 1) {
	var power: Int = 0 // In kW/s
}

// Electrical https://en.wikipedia.org/wiki/Electrically_powered_spacecraft_propulsion#Types
open class Thruster : Part() {
	var thrust: Float = 0f // In N
}

// Chemical: Hybrid, Bipropellant, Tripropellant. https://en.wikipedia.org/wiki/Rocket_engine#Chemically_powered
// Nuclear https://en.wikipedia.org/wiki/Nuclear_pulse_propulsion
class FueledThruster : Thruster(), FueledPart by FueledPartImpl(Resource.ROCKET_FUEL, 1)