package se.exuvo.aurora.galactic

import se.exuvo.aurora.planetarysystems.components.Spectrum

abstract class Part {
	var name: String = ""
	var designDay: Int? = null
	val cost: MutableMap<Resource, Int> = LinkedHashMap() 
	var maxHealth = 1
	var crewRequirement = 1
	
	// In m3
	fun getVolume() : Int {
		return cost.values.sum()
	}
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
	val power: Int // W/s
}

interface PoweredPart {
	val powerConsumption: Int // W/s
}

class PoweringPartImpl(override val power: Int) : PoweringPart
class PoweredPartImpl(override val powerConsumption: Int) : PoweredPart

// retracts during combat
class SolarPanel(power: Int = 0) : Part(), PoweringPart by PoweringPartImpl(power)

// power in W/s
class Reactor(power: Int = 0, fuel: Resource, fuelConsumption: Int) : Part(), PoweringPart by PoweringPartImpl(power), FueledPart by FueledPartImpl(fuel, fuelConsumption) {
}

// thrust in N
abstract class Thruster(val thrust: Float) : Part()

// Electrical https://en.wikipedia.org/wiki/Electrically_powered_spacecraft_propulsion#Types
class ElectricalThruster(thrust: Float, powerConsumption: Int) : Thruster(thrust), PoweredPart by PoweredPartImpl(powerConsumption)

// Chemical: Hybrid, Bipropellant, Tripropellant. https://en.wikipedia.org/wiki/Rocket_engine#Chemically_powered
// Nuclear https://en.wikipedia.org/wiki/Nuclear_pulse_propulsion
class FueledThruster(thrust: Float, fuel: Resource, fuelConsumption: Int) : Thruster(thrust), FueledPart by FueledPartImpl(fuel, fuelConsumption)

//TODO refresh rate, accuracy (results in fixed offset for each entity id, scaled by distance)
class PassiveSensor(powerConsumption: Int = 0,
										val spectrum: Spectrum,
										val sensitivity: Double,
										val arcSegments: Int,
										val distanceResolution: Double, // in km
										val angleOffset: Int,
										val accuracy: Double, // 1 = 100% error
										val refreshDelay: Int // cooldown in seconds
) : Part(), PoweredPart by PoweredPartImpl(powerConsumption)
