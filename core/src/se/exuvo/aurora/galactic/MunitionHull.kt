package se.exuvo.aurora.galactic

import se.exuvo.aurora.utils.sumByLong
import se.exuvo.aurora.utils.forEachFast
import java.lang.IllegalArgumentException
import kotlin.reflect.KClass
import org.apache.commons.math3.util.FastMath

enum class DamagePattern {
	KINETIC,
	EXPLOSIVE,
	LASER
}

abstract class MunitionHull(val storageType: Resource) {
	companion object {
		const val lengthToDiameterRatio = 4.0
	}
	
	var name: String = ""
	var designDay: Int = 0
	var locked = false
	var obsolete = false
	var damage: Long = 0 // joules
	
	abstract var radius: Int // cm
	abstract var loadedMass: Int // kg
	abstract var volume: Long // cm³
	var hashcode = -1
		get() {
			if (field == -1) { field = calculateHashCode() }
			return field
		}
	
	open fun finalize() {
		locked = true
	}
	
	override fun toString() = name
	
	open fun calculateHashCode(): Int {
		var hash = 3;
		hash = 37 * hash + name.hashCode()
		hash = 37 * hash + designDay
		return hash
	}

	override fun hashCode(): Int = hashcode
	
	open fun resetLazyCache() {
		hashcode = -1
	}
}

class SimpleMunitionHull(storageType: Resource): MunitionHull(storageType) {
	var health: Short = -1
	var damagePattern: DamagePattern = DamagePattern.KINETIC
	override var radius = 1
	override var loadedMass: Int = 1
	override var volume = -1L
		get() {
			if (field == -1L) { field = calculateVolume() }
			return field
		}
	
	fun calculateVolume(): Long {
		// V = πr²h, http://mathhelpforum.com/geometry/170076-how-find-cylinder-dimensions-volume-aspect-ratio.html
		val length = lengthToDiameterRatio * 2 * radius
		val volume = FastMath.PI * radius * radius * length
		
		return volume.toLong()
	}
	
	override fun resetLazyCache() {
		super.resetLazyCache()
		volume = -1L
	}
}

class AdvancedMunitionHull(storageType: Resource): MunitionHull(storageType) {
	
	private val parts: MutableList<Part> = ArrayList()
	private val partRefs: MutableList<PartRef<Part>> = ArrayList()
	
	var armorLayers = 1 // Centimeters of armor
	var armorBlockHP: UByte = 100u
	
	val thrusters: MutableList<PartRef<Part>> = ArrayList()
	
	override var radius = -1
		get() {
			if (field == -1) { field = calculateRadius() }
			return field
		}
	override var loadedMass = -1
		get() {
			if (field == -1) { field = calculateLoadedMass() }
			return field
		}
	override var volume = -1L
		get() {
			if (field == -1L) { field = calculateVolume() }
			return field
		}
	var thrust = -1L
		get() {
			if (field == -1L) { field = calculateThrust() }
			return field
		}
	var thrustTime = -1
		get() {
			if (field == -1) { field = calculateThrustTime() }
			return field
		}
	var emptyMass = -1
		get() {
			if (field == -1) { field = calculateEmptyMass() }
			return field
		}
	var fuelMass = -1
		get() {
			if (field == -1) { field = calculateFuelMass() }
			return field
		}
	
	override fun finalize() {
		super.finalize()
		
		partRefs.forEachFast { partRef ->
			if (partRef.part is Warhead) {
				damage += partRef.part.damage
			}
			
			if (partRef.part is ThrustingPart) {
				thrusters += partRef
			}
		}
	}

	@Suppress("UNCHECKED_CAST")
	operator fun <T: Part> get(partClass: KClass<T>) : List<PartRef<T>> = partRefs.filter { partClass.isInstance(it.part) } as List<PartRef<T>>
	operator fun get(partIndex: Int) = partRefs[partIndex]
	
	fun getParts() = parts as List<Part>
	fun getPartRefs() = partRefs as List<PartRef<Part>>
	
	fun addPart(part: Part) {
		parts.add(part)
		partRefs.add(PartRef(part, parts.size - 1))
	}
	
	fun removePart(part: Part) {
		val index = parts.indexOf(part)
		
		if (index == -1) {
			throw IllegalArgumentException()
		}
		
		parts.removeAt(index)
		partRefs.removeAt(index)
	}
	
	fun calculateThrust(): Long {
		
		var thrust = 0L
		
		parts.forEachFast{ part ->
			if (part is ThrustingPart) {
				thrust += part.thrust
			}
		}
		
		return thrust
	}
	
	fun getMaxAcceleration(): Long = thrust / emptyMass
	
	fun getAverageAcceleration(): Long = (getMinAcceleration() + getMaxAcceleration()) / 2
	
	fun getMinAcceleration(): Long = thrust / (emptyMass + fuelMass)
	
	fun calculateThrustTime(): Int {
		val fuel = fuelMass
		val fuelConsumption = this[FueledThruster::class].sumBy { it.part.fuelConsumption }
		
		if (fuelConsumption == 0) {
			return Integer.MAX_VALUE
		}
		
		return (fuel / fuelConsumption).toInt()
	}
	
	// Kg
	fun calculateEmptyMass(): Int {
		//TODO add armor
		return parts.sumByLong { it.mass }.toInt()
	}
	
	fun calculateFuelMass(): Int {
		return parts.sumByLong {
			when (it) {
				is FuelContainerPart -> it.capacity / Resource.ROCKET_FUEL.specificVolume
				is NuclearContainerPart -> it.capacity / Resource.NUCLEAR_FISSION.specificVolume
				else -> 0
			}
		}.toInt()
	}
	
	fun calculateLoadedMass(): Int {
		return emptyMass + fuelMass
	}

	// cm³
	fun calculateVolume(): Long {
		//TODO add armor
		return parts.sumByLong { it.volume }
	}
	
	// cm
	fun calculateRadius(): Int {
		// V = πr²h, http://mathhelpforum.com/geometry/170076-how-find-cylinder-dimensions-volume-aspect-ratio.html
		val length = FastMath.pow(FastMath.pow(2.0, 2 * lengthToDiameterRatio) * volume / FastMath.PI, 1.0 / 3)
		var radius = FastMath.ceil(FastMath.sqrt(volume / FastMath.PI / length)).toInt()
		
		return radius + armorLayers
	}

	// cm^2
	fun getSurfaceArea(): Int {
		// V = πr^2h, http://mathhelpforum.com/geometry/170076-how-find-cylinder-dimensions-volume-aspect-ratio.html
		val length = FastMath.pow(FastMath.pow(2.0, 2 * lengthToDiameterRatio) * volume / FastMath.PI, 1.0 / 3)
		val radius = FastMath.sqrt(volume / FastMath.PI / length)

		val surface = 2 * FastMath.PI * radius * length + 2 * FastMath.PI * radius * radius

//		println("length $length, diameter ${2 * radius}, surface $surface, volume ${FastMath.PI * length * radius * radius}")

		//TODO add armor
		return surface.toInt()
	}
	
	override fun resetLazyCache() {
		super.resetLazyCache()
		radius = -1
		loadedMass = -1
		volume = -1L
		thrust = -1L
		thrustTime = -1
		emptyMass = -1
		fuelMass = -1
	}
}
