package se.exuvo.aurora.galactic

import se.exuvo.aurora.starsystems.components.Spectrum
import java.util.Objects
import java.security.InvalidParameterException
import org.apache.commons.math3.util.FastMath

abstract class Part {
	var name: String = ""
	var designDay: Int = -1
	val cost: MutableMap<Resource, Long> = LinkedHashMap() 
	var maxHealth: UByte = 10U
	var crewRequirement = 1

	var mass = -1L
		get() {
			if (field == -1L) { field = calculateMass() }
			return field
		}
	var volume = -1L
		get() {
			if (field == -1L) { field = calculateVolume() }
			return field
		}
	private var hashcode = -1
		get() {
			if (field == -1) { field = calculateHashCode() }
			return field
		}
	
	// In kg
	fun calculateMass(): Long = FastMath.max(1, cost.values.sum())
	
	// In cm3
	fun calculateVolume(): Long {
		var volume = 0L
		
		cost.forEach{ resource, amount ->
			volume += amount * resource.specificVolume
		}
		
		return FastMath.max(1, volume)
	}
	
	// https://stackoverflow.com/questions/113511/best-implementation-for-hashcode-method
	open fun calculateHashCode(): Int {
		var hash = 1;
		hash = 37 * hash + name.hashCode()
		hash = 37 * hash + designDay
		hash = 37 * hash + (volume xor (volume shr 32)).toInt()
		hash = 37 * hash + maxHealth.toInt()
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
	
	fun resetLazyCache() {
		hashcode = -1
		mass = -1L
		volume = -1L
	}
}

abstract class ContainerPart(val capacity: Long, val cargoType: CargoType) : Part();

class CargoContainerPart(capacity: Long) : ContainerPart(capacity, CargoType.NORMAL);
class FuelContainerPart(capacity: Long) : ContainerPart(capacity, CargoType.FUEL);
class LifeSupportContainerPart(capacity: Long) : ContainerPart(capacity, CargoType.LIFE_SUPPORT);
class AmmoContainerPart(capacity: Long) : ContainerPart(capacity, CargoType.AMMUNITION);
class NuclearContainerPart(capacity: Long) : ContainerPart(capacity, CargoType.NUCLEAR);

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

interface HeatingPart

interface AmmunitionPart {
	val ammunitionAmount: Int
	val ammunitionType: Resource
	val ammunitionSize: Int // In cm radius
	val reloadTime: Int
}

// thrust in N
interface ThrustingPart {
	val thrust: Long
}

interface WeaponPart

class FueledPartImpl(override val fuel: Resource, override val fuelConsumption: Int, override val fuelTime: Int) : FueledPart
class FuelWastePartImpl(override val waste: Resource) : FuelWastePart
class PoweringPartImpl(override val power: Long) : PoweringPart
class PoweredPartImpl(override val powerConsumption: Long) : PoweredPart
class ChargedPartImpl(override val capacitor: Long) : ChargedPart
class AmmunitionPartImpl(override val ammunitionAmount: Int, override val ammunitionType: Resource, override val ammunitionSize: Int, override val reloadTime: Int) : AmmunitionPart
class ThrustingPartImpl(override val thrust: Long) : ThrustingPart


class Battery(powerConsumption: Long = 0,
							power: Long = 0,
							val efficiency: Int = 100,
							capacity: Long // Ws
) : Part(),
		PoweredPart by PoweredPartImpl(powerConsumption),
		PoweringPart by PoweringPartImpl(power),
		ChargedPart by ChargedPartImpl(capacity)

class SolarPanel(val efficiency: Int = 46
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
class ElectricalThruster(thrust: Long,
												 powerConsumption: Long
) : Part(),
		ThrustingPart by ThrustingPartImpl(thrust),
		PoweredPart by PoweredPartImpl(powerConsumption)

// Chemical: Hybrid, Bipropellant, Tripropellant. https://en.wikipedia.org/wiki/Rocket_engine#Chemically_powered
// Nuclear https://en.wikipedia.org/wiki/Nuclear_pulse_propulsion
class FueledThruster(thrust: Long,
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

// Blocks all laser and explosive, lets 50% kinetic through
class Shield(capacitor: Long,
             powerConsumption: Long,
             val efficiency: Int = 50 // Percent Energy to shield HP
) : Part(),
		ChargedPart by ChargedPartImpl(capacitor),
		PoweredPart by PoweredPartImpl(powerConsumption)

enum class BeamWavelength(val short: String, val length: Int) { // Length in nm
	Microwaves("MW", 500000), // 100000 nm - 25000 nm
	Infrared("IR", 13750),    //  25000 nm -  2500 nm
	VisibleLight("L", 575),   //    750 nm -   400 nm
	Ultraviolet("UV", 200),   //    400 nm -     1 nm
	Xrays("X", 1);            //      1 nm -     0.001 nm
	// http://labman.phys.utk.edu/phys222core/modules/m6/The%20EM%20spectrum.html
	
	override fun toString() : String {
		return short
	}
}

class BeamWeapon(powerConsumption: Long = 0,
								 val aperature: Double = 1.0, // in mm diameter
								 val waveLength: BeamWavelength,
								 capacitor: Long,
								 val efficiency: Int = 50 // Percent Energy to damage
) : Part(),
		WeaponPart,
		PoweredPart by PoweredPartImpl(powerConsumption),
		ChargedPart by ChargedPartImpl(capacitor),
		HeatingPart
 {
	
	// Diffraction limited radial beam divergence in radians
	// https://www.quora.com/Is-the-light-from-lasers-reduced-by-the-inverse-square-law-as-distance-grows-similar-to-other-light-sources
	// https://en.wikipedia.org/wiki/Beam_divergence
	fun getRadialDivergence(): Double = (waveLength.length.toDouble() / 1000000000.0) / (FastMath.PI * 1000 * aperature / 2)
	
	// We are always outside rayleight range so beam width is linear to distance
	// in m of beam radius
	fun getBeamRadiusAtDistance(distance: Long): Double = distance.toDouble() * FastMath.tan(getRadialDivergence())
	
	 // in m²
	fun getBeamArea(distance: Long): Double = FastMath.PI * FastMath.pow(getBeamRadiusAtDistance(distance), 2.0)
	
	fun getDeliveredEnergyTo1MSquareAtDistance(distance: Long): Long { // in watts of delivered energy
		val beamArea = getBeamArea(distance)
		
		return (efficiency * capacitor) / (100 * FastMath.max(1, beamArea.toLong()))
	}
}

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
		AmmunitionPart by AmmunitionPartImpl(ammunitionAmount, Resource.SABOTS, ammunitionSize, reloadTime),
		HeatingPart

class MissileLauncher(ammunitionSize: Int,
											ammunitionAmount: Int,
											reloadTime: Int,
											val launchForce: Long = 1 // Newtons
) : Part(),
		WeaponPart,
		AmmunitionPart by AmmunitionPartImpl(ammunitionAmount, Resource.MISSILES, ammunitionSize, reloadTime) {
	init {
		if (launchForce <= 0L) throw InvalidParameterException("launchForce must be greater than 0")
	}
}

class TargetingComputer(val maxWeapons: Int,
												val lockingTime: Int, // s
												val prediction: Float,
												val maxRange: Long, // km
												powerConsumption: Long
) : Part(),
		PoweredPart by PoweredPartImpl(powerConsumption)

class Warhead(val damage: Long): Part()