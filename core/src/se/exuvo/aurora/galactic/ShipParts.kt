package se.exuvo.aurora.galactic

import se.exuvo.aurora.planetarysystems.components.Spectrum

abstract class Part {
	var name: String = ""
	var designDay: Int? = null
	val cost: MutableMap<Resource, Int> = LinkedHashMap() 
	var maxHealth = 1
	var crewRequirement = 1
	
	// In cm3
	fun getVolume() : Int {
		var volume = 0
		
		cost.forEach({resource, amount ->
			volume += amount * resource.specificVolume
		})
		
		return volume
	}
}

abstract class ContainerPart(val capacity: Int, val cargoType: CargoType) : Part();

class CargoContainerPart(capacity: Int) : ContainerPart(capacity, CargoType.NORMAL);
class FuelContainerPart(capacity: Int) : ContainerPart(capacity, CargoType.FUEL);
class LifeSupportContainerPart(capacity: Int) : ContainerPart(capacity, CargoType.LIFE_SUPPORT);
class NuclearContainerPart(capacity: Int) : ContainerPart(capacity, CargoType.NUCLEAR);

interface FueledPart {
	val fuel: Resource
	val fuelConsumption: Int // kg per second usage
	val fuelTime: Int // seconds of usage for each kg
}

interface FuelWastePart {
	val waste: Resource
}

interface PoweringPart {
	val power: Int // W/s
}

interface PoweredPart {
	val powerConsumption: Int // W/s
}

interface ChargedPart {
	val capacitor: Int // Ws
}

interface AmmunitionPart {
	val ammunitionAmount: Int
	val ammunitionType: Resource
}

interface ReloadablePart {
	val reloadTime: Int
}

// thrust in N
interface ThrustingPart {
	val thrust: Float
}

class FueledPartImpl(override val fuel: Resource, override val fuelConsumption: Int, override val fuelTime: Int) : FueledPart
class FuelWastePartImpl(override val waste: Resource) : FuelWastePart
class PoweringPartImpl(override val power: Int) : PoweringPart
class PoweredPartImpl(override val powerConsumption: Int) : PoweredPart
class ChargedPartImpl(override val capacitor: Int) : ChargedPart
class AmmunitionPartImpl(override val ammunitionAmount: Int, override val ammunitionType: Resource) : AmmunitionPart
class ReloadablePartImpl(override val reloadTime: Int) : ReloadablePart
class ThrustingPartImpl(override val thrust: Float) : ThrustingPart


class Battery(powerConsumption: Int = 0,
							power: Int = 0,
							val efficiency: Float = 1f,
							capacity: Int // Ws
) : Part(),
		PoweredPart by PoweredPartImpl(powerConsumption),
		PoweringPart by PoweringPartImpl(power),
		ChargedPart by ChargedPartImpl(capacity)

class SolarPanel(val efficiency: Float = 0.46f
) : Part(),
		PoweringPart by PoweringPartImpl(0)

// power in W/s
abstract class Reactor(power: Int = 1000000,
							fuel: Resource,
							fuelTime: Int 
) : Part(),
		PoweringPart by PoweringPartImpl(power),
		FueledPart by FueledPartImpl(fuel, 1, fuelTime)

class FissionReactor(power: Int = 1000000, // Min 1MW
							val efficiency: Float = 0.33f, // Heat to energy 33%
							fuelTime: Int = ((86400000000000L / power) * efficiency.toDouble()).toInt()
							// 1 gram of fissile material yields about 1 megawatt-day (MWd) of heat energy.  https://www.nuclear-power.net/nuclear-power-plant/nuclear-fuel/fuel-consumption-of-conventional-reactor/
							// 1 MWd = 1 second of 86,400,000,000W
) : Reactor(power, Resource.NUCLEAR_FISSION, fuelTime),
		FuelWastePart by FuelWastePartImpl(Resource.NUCLEAR_WASTE)

class FusionReactor(power: Int = 1000000, // Min 1MW
										val efficiency: Float = 0.33f, // Heat to energy 33%
										fuelTime: Int = ((86400000000000L / power) * efficiency.toDouble()).toInt()
) : Reactor(power, Resource.NUCLEAR_FUSION, fuelTime)


// Electrical https://en.wikipedia.org/wiki/Electrically_powered_spacecraft_propulsion#Types
class ElectricalThruster(thrust: Float,
												 powerConsumption: Int
) : Part(),
		ThrustingPart by ThrustingPartImpl(thrust),
		PoweredPart by PoweredPartImpl(powerConsumption)

// Chemical: Hybrid, Bipropellant, Tripropellant. https://en.wikipedia.org/wiki/Rocket_engine#Chemically_powered
// Nuclear https://en.wikipedia.org/wiki/Nuclear_pulse_propulsion
class FueledThruster(thrust: Float,
										 fuel: Resource,
										 fuelConsumption: Int
) : Part(),
		ThrustingPart by ThrustingPartImpl(thrust),
		FueledPart by FueledPartImpl(fuel, fuelConsumption, 1)

//TODO refresh rate, accuracy (results in fixed offset for each entity id, scaled by distance)
class PassiveSensor(powerConsumption: Int = 0,
										val spectrum: Spectrum,
										val sensitivity: Double,
										val arcSegments: Int,
										val distanceResolution: Double, // in km
										val angleOffset: Int,
										val accuracy: Double, // 1 = 100% error
										val refreshDelay: Int // cooldown in seconds
) : Part(),
		PoweredPart by PoweredPartImpl(powerConsumption)

enum class BeamWaveLength(val short: String) {
	Visible_Light("L"),
	Infrared("IR"),
	Ultraviolet("UV"),
	Microwaves("MW"),
	Xrays("X");
	
	override fun toString() : String {
		return short
	}
}

class BeamWeapon(powerConsumption: Int = 0,
								 val waveLength: BeamWaveLength,
								 val divergence: Double,
								 capacitor: Int
) : Part(),
		PoweredPart by PoweredPartImpl(powerConsumption),
		ChargedPart by ChargedPartImpl(capacitor)

class Railgun(powerConsumption: Int = 0,
								 val barrelSize: Int,
								 capacitor: Int,
								 ammunitionAmount: Int
) : Part(),
		PoweredPart by PoweredPartImpl(powerConsumption),
		ChargedPart by ChargedPartImpl(capacitor),
		AmmunitionPart by AmmunitionPartImpl(ammunitionAmount, Resource.SABOTS)

class MissileLauncher(powerConsumption: Int = 0,
								 			val launchTubeSize: Int,
								 			ammunitionAmount: Int,
											reloadTime: Int
) : Part(),
		PoweredPart by PoweredPartImpl(powerConsumption),
		AmmunitionPart by AmmunitionPartImpl(ammunitionAmount, Resource.MISSILES),
		ReloadablePart by ReloadablePartImpl(reloadTime)

