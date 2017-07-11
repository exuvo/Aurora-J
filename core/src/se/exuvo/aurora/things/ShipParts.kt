package se.exuvo.aurora.shipcomponents

class ShipClass {
	var name: String = ""
	var designDay: Int? = null
	val parts: MutableList<Part> = ArrayList()
	var armorLayers = 1
	var preferredCargo: Map<Resource, Int> = LinkedHashMap()
	var preferredItemCargo: MutableList<Part> = ArrayList()
	//TODO default weapon assignments

	operator fun <T : Any> get(partClass: Class<T>) = parts.filterIsInstance(partClass)

	fun put(part: Part) {
		parts.add(part)
	}

	fun getCrewRequirement(): Int {
		return parts.sumBy { it.crewRequirement }
	}

	fun getVolume(): Int {
		return parts.sumBy { it.cost.values.sum() }
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

// Amounts should be stored in grams of mass
// Density in kg per m3
enum class Resource(val density: Int) {
	// No storage requirements
	GENERIC(1), // Steel, Concrete, Carbonfiber, Glass, Ceramics
	METAL_LIGHT(1), // Aluminium, Titanium
	METAL_CONDUCTIVE(1), // Copper, Gold
	SEMICONDUCTORS(1), // Silicon, germanium
	RARE_EARTH(1), // Neodymium. https://en.wikipedia.org/wiki/Rare-earth_element
	MAINTENANCE_SUPPLIES(1),
	// Requires radiation shielding
	NUCLEAR_FISSION(1), // Uranium, Thorium
	NUCLEAR_FUSION(1), // Deuterium-Tritium, Helium3. See 'Fusion Reactor Fuel Modes' at http://forum.kerbalspaceprogram.com/index.php?/topic/155255-12213-kspi-extended-11414-05-7-2017-support-release-thread/
	// Requires temperature control
	ROCKET_FUEL(1), // LOX + kerosene, LOX + H, nitrogen tetroxide + hydrazine. https://en.wikipedia.org/wiki/Rocket_propellant#Liquid_propellants
	// Requires temperature control and atmosphere
	LIFE_SUPPORT(1), // Food, Water, Air
	ITEMS(0);
}

enum class CargoType(val resources: List<Resource>) {
	NORMAL(listOf(Resource.MAINTENANCE_SUPPLIES, Resource.GENERIC, Resource.METAL_LIGHT, Resource.METAL_CONDUCTIVE, Resource.SEMICONDUCTORS, Resource.RARE_EARTH, Resource.ITEMS)),
	FUEL(listOf(Resource.ROCKET_FUEL)),
	LIFE_SUPPORT(listOf(Resource.LIFE_SUPPORT)),
	NUCLEAR(listOf(Resource.NUCLEAR_FISSION, Resource.NUCLEAR_FUSION))
}

abstract class Part {
	var name: String = ""
	var designDay: Int? = null
	val cost: MutableMap<Resource, Int> = LinkedHashMap()
	var size = 1 // In m3
	var maxHealth = 1
	var crewRequirement = 1
}

abstract class ContainerPart(val capacity: Int, val cargoType: CargoType) : Part();

class CargoContainerPart(capacity: Int) : ContainerPart(capacity, CargoType.NORMAL);
class FuelContainerPart(capacity: Int) : ContainerPart(capacity, CargoType.FUEL);
class LifeSupportContainerPart(capacity: Int) : ContainerPart(capacity, CargoType.LIFE_SUPPORT);
class NuclearContainerPart(capacity: Int) : ContainerPart(capacity, CargoType.NUCLEAR);

interface FueledPart {
	val fuel: Resource
	val fuelConsumption: Int // gram per second usage
}

class FueledPartImpl(override val fuel: Resource, override val fuelConsumption: Int) : FueledPart

interface PoweringPart {
	val power: Int // kW/s
}

interface PoweredPart {
	val powerConsumption: Int // kW/s
}

class PoweringPartImpl(override val power: Int) : PoweringPart
class PoweredPartImpl(override val powerConsumption: Int) : PoweredPart

// retracts during combat
class SolarPanel(power: Int = 0) : Part(), PoweringPart by PoweringPartImpl(power)

// power in kW/s
class Reactor(power: Int = 0, fuel: Resource, fuelConsumption: Int) : Part(), PoweringPart by PoweringPartImpl(power), FueledPart by FueledPartImpl(fuel, fuelConsumption) {
}

// thrust in N
abstract class Thruster(val thrust: Float) : Part()

// Electrical https://en.wikipedia.org/wiki/Electrically_powered_spacecraft_propulsion#Types
class ElectricalThruster(thrust: Float, powerConsumption: Int) : Thruster(thrust), PoweredPart by PoweredPartImpl(powerConsumption)

// Chemical: Hybrid, Bipropellant, Tripropellant. https://en.wikipedia.org/wiki/Rocket_engine#Chemically_powered
// Nuclear https://en.wikipedia.org/wiki/Nuclear_pulse_propulsion
class FueledThruster(thrust: Float, fuel: Resource, fuelConsumption: Int) : Thruster(thrust), FueledPart by FueledPartImpl(fuel, fuelConsumption)
