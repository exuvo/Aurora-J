package se.exuvo.aurora.galactic

import se.exuvo.aurora.planetarysystems.components.Spectrum
import java.util.Objects

abstract class Part {
	var name: String = ""
	var designDay: Int = -1
	val cost: MutableMap<Resource, Long> = LinkedHashMap() 
	var maxHealth = 1
	var crewRequirement = 1
	
	// In kg
	fun getMass() = cost.values.sum()
	
	// In cm3
	fun getVolume() : Long {
		var volume = 0L
		
		cost.forEach({resource, amount ->
			volume += amount * resource.specificVolume
		})
		
		return volume
	}
	
	private val hashcode: Int by lazy (LazyThreadSafetyMode.NONE) {calculateHashCode()}
	
	// https://stackoverflow.com/questions/113511/best-implementation-for-hashcode-method
	open fun calculateHashCode() : Int {
		var hash = 1;
		hash = 37 * hash + name.hashCode()
		hash = 37 * hash + designDay
		val volume = getVolume()
		hash = 37 * hash + (volume xor (volume shr 32)).toInt()
		hash = 37 * hash + maxHealth
		hash = 37 * hash + crewRequirement
		return hash
	}
	
	override fun hashCode(): Int = hashcode
	override fun toString(): String {
		
		if (name.length > 0) {
			return "${this::class.simpleName} $name"
		}
		
		return "${this::class.simpleName}"
	} 
}

abstract class ContainerPart(val capacity: Int, val cargoType: CargoType) : Part();

class CargoContainerPart(capacity: Int) : ContainerPart(capacity, CargoType.NORMAL);
class FuelContainerPart(capacity: Int) : ContainerPart(capacity, CargoType.FUEL);
class LifeSupportContainerPart(capacity: Int) : ContainerPart(capacity, CargoType.LIFE_SUPPORT);
class AmmoContainerPart(capacity: Int) : ContainerPart(capacity, CargoType.AMMUNITION);
class NuclearContainerPart(capacity: Int) : ContainerPart(capacity, CargoType.NUCLEAR);

interface FueledPart {
	val fuel: Resource
	val fuelConsumption: Int // kg per fuelTime
	val fuelTime: Int // seconds of full usage for each kg
}

interface FuelWastePart {
	val waste: Resource
}

interface PoweringPart {
	val power: Long // W/s
}

interface PoweredPart {
	val powerConsumption: Long // W/s
}

interface ChargedPart {
	val capacitor: Long // Ws
}

interface AmmunitionPart {
	val ammunitionAmount: Int
	val ammunitionType: Resource
	val ammunitionSize: Int // In cm radius
	val reloadTime: Int
}

// thrust in N
interface ThrustingPart {
	val thrust: Float
}

interface WeaponPart

class FueledPartImpl(override val fuel: Resource, override val fuelConsumption: Int, override val fuelTime: Int) : FueledPart
class FuelWastePartImpl(override val waste: Resource) : FuelWastePart
class PoweringPartImpl(override val power: Long) : PoweringPart
class PoweredPartImpl(override val powerConsumption: Long) : PoweredPart
class ChargedPartImpl(override val capacitor: Long) : ChargedPart
class AmmunitionPartImpl(override val ammunitionAmount: Int, override val ammunitionType: Resource, override val ammunitionSize: Int, override val reloadTime: Int) : AmmunitionPart
class ThrustingPartImpl(override val thrust: Float) : ThrustingPart


class Battery(powerConsumption: Long = 0,
							power: Long = 0,
							val efficiency: Float = 1f,
							capacity: Long // Ws
) : Part(),
		PoweredPart by PoweredPartImpl(powerConsumption),
		PoweringPart by PoweringPartImpl(power),
		ChargedPart by ChargedPartImpl(capacity)

class SolarPanel(val efficiency: Float = 0.46f
) : Part(),
		PoweringPart by PoweringPartImpl(0)

// power in W/s
abstract class Reactor(power: Long = 1000000,
							fuel: Resource,
							fuelTime: Int 
) : Part(),
		PoweringPart by PoweringPartImpl(power),
		FueledPart by FueledPartImpl(fuel, 1, fuelTime)

class FissionReactor(power: Long = 1000000, // Min 1MW
							val efficiency: Float = 0.33f, // Heat to energy 33%
							fuelTime: Int = ((86400000000000L / power) * efficiency.toDouble()).toInt()
							// 1 gram of fissile material yields about 1 megawatt-day (MWd) of heat energy.  https://www.nuclear-power.net/nuclear-power-plant/nuclear-fuel/fuel-consumption-of-conventional-reactor/
							// 1 MWd = 1 second of 86,400,000,000W
) : Reactor(power, Resource.NUCLEAR_FISSION, fuelTime),
		FuelWastePart by FuelWastePartImpl(Resource.NUCLEAR_WASTE)

class FusionReactor(power: Long = 1000000, // Min 1MW
										val efficiency: Float = 0.33f, // Heat to energy 33%
										fuelTime: Int = ((86400000000000L / power) * efficiency.toDouble()).toInt()
) : Reactor(power, Resource.NUCLEAR_FUSION, fuelTime)


// Electrical https://en.wikipedia.org/wiki/Electrically_powered_spacecraft_propulsion#Types
class ElectricalThruster(thrust: Float,
												 powerConsumption: Long
) : Part(),
		ThrustingPart by ThrustingPartImpl(thrust),
		PoweredPart by PoweredPartImpl(powerConsumption)

// Chemical: Hybrid, Bipropellant, Tripropellant. https://en.wikipedia.org/wiki/Rocket_engine#Chemically_powered
// Nuclear https://en.wikipedia.org/wiki/Nuclear_pulse_propulsion
class FueledThruster(thrust: Float,
										 fuelConsumption: Int,
										 fuel: Resource = Resource.ROCKET_FUEL
) : Part(),
		ThrustingPart by ThrustingPartImpl(thrust),
		FueledPart by FueledPartImpl(fuel, fuelConsumption, 1)

class PassiveSensor(powerConsumption: Long = 0,
										val spectrum: Spectrum,
										val sensitivity: Double,
										val arcSegments: Int,
										val distanceResolution: Double, // in km
										val angleOffset: Int,
										val accuracy: Double, // 1 = 100% error
										val refreshDelay: Int // cooldown in seconds
) : Part(),
		PoweredPart by PoweredPartImpl(powerConsumption) {

	override fun calculateHashCode() : Int {
		var hash = super.calculateHashCode()
		hash = 37 * hash + spectrum.ordinal
		hash = 37 * hash + (sensitivity.toBits() xor (sensitivity.toBits() shr 32)).toInt()
		hash = 37 * hash + arcSegments
		hash = 37 * hash + (distanceResolution.toBits() xor (distanceResolution.toBits() shr 32)).toInt()
		hash = 37 * hash + angleOffset
		hash = 37 * hash + (accuracy.toBits() xor (accuracy.toBits() shr 32)).toInt()
		hash = 37 * hash + refreshDelay
		return hash
	}
}

enum class BeamWavelength(val short: String) {
	VisibleLight("L"),
	Infrared("IR"),
	Ultraviolet("UV"),
	Microwaves("MW"),
	Xrays("X");
	
	override fun toString() : String {
		return short
	}
}

class BeamWeapon(powerConsumption: Long = 0,
								 val waveLength: BeamWavelength,
								 val divergence: Double,
								 capacitor: Long
) : Part(),
		WeaponPart,
		PoweredPart by PoweredPartImpl(powerConsumption),
		ChargedPart by ChargedPartImpl(capacitor)

class Railgun(powerConsumption: Long = 0,
							ammunitionSize: Int,
							capacitor: Long,
							ammunitionAmount: Int,
							reloadTime: Int,
							val efficiency: Int = 20 // Percent Energy to velocity
) : Part(),
		WeaponPart,
		PoweredPart by PoweredPartImpl(powerConsumption),
		ChargedPart by ChargedPartImpl(capacitor),
		AmmunitionPart by AmmunitionPartImpl(ammunitionAmount, Resource.SABOTS, ammunitionSize, reloadTime)

class MissileLauncher(powerConsumption: Long = 0,
								 			ammunitionSize: Int,
								 			ammunitionAmount: Int,
											reloadTime: Int,
											val launchSpeed: Float = 0f
) : Part(),
		WeaponPart,
		PoweredPart by PoweredPartImpl(powerConsumption),
		AmmunitionPart by AmmunitionPartImpl(ammunitionAmount, Resource.MISSILES, ammunitionSize, reloadTime)

class TargetingComputer(val maxWeapons: Int,
												val lockingTime: Int,
												val prediction: Float,
												powerConsumption: Long
) : Part(),
		PoweredPart by PoweredPartImpl(powerConsumption)