package se.exuvo.aurora.galactic

import se.exuvo.aurora.planetarysystems.components.PowerScheme
import se.exuvo.aurora.utils.sumByLong
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.galactic.FueledThruster
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
	
	abstract fun getRadius(): Int // cm
	abstract fun getLoadedMass(): Long // kg
	abstract fun getVolume(): Long // cm³
	
	override fun toString() = name
	
	private val hashcode: Int by lazy (LazyThreadSafetyMode.NONE) {
		var hash = 1;
		hash = 37 * hash + name.hashCode()
		hash = 37 * hash + designDay
		hash
	}

	override fun hashCode(): Int = hashcode
}

class SimpleMunitionHull(storageType: Resource): MunitionHull(storageType) {
	var health: Short = -1
	var damagePattern: DamagePattern = DamagePattern.KINETIC
	var damage: Long = 0 // joules
	@JvmField
	var radius: Int = 1
	var mass: Long = 1
	
	override fun getRadius(): Int = radius
	override fun getLoadedMass(): Long = mass
	override fun getVolume(): Long {
		// V = πr²h, http://mathhelpforum.com/geometry/170076-how-find-cylinder-dimensions-volume-aspect-ratio.html
		val length = lengthToDiameterRatio * 2 * radius
		val volume = FastMath.PI * radius * radius * length
		
		return volume.toLong()
	}
}

class AdvancedMunitionHull(storageType: Resource): MunitionHull(storageType) {
	
	private val parts: MutableList<Part> = ArrayList()
	private val partRefs: MutableList<PartRef<Part>> = ArrayList()
	
	var armorLayers = 1 // Centimeters of armor
	var armorBlockHP: Short = 100

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
	
	fun getThrust(): Long {
		
		var thrust = 0L
		
		parts.forEachFast{ part ->
			if (part is ThrustingPart) {
				thrust += part.thrust
			}
		}
		
		return thrust
	}
	
	fun getMaxAcceleration(): Long = getThrust() / getEmptyMass()
	
	fun getAverageAcceleration(): Long = (getMinAcceleration() + getMaxAcceleration()) / 2
	
	fun getMinAcceleration(): Long = getThrust() / (getEmptyMass() + getFuelMass())
	
	fun getThrustTime(): Int {
		val fuel = getFuelMass()
		val fuelConsumption = this[FueledThruster::class].sumBy { it.part.fuelConsumption }
		
		if (fuelConsumption == 0) {
			return Integer.MAX_VALUE
		}
		
		return (fuel / fuelConsumption).toInt()
	}
	
	// Kg
	fun getEmptyMass(): Long {
		//TODO add armor
		return parts.sumByLong { it.getMass() }
	}
	
	fun getFuelMass(): Long {
		return parts.sumByLong {
			if (it is FuelContainerPart) {
				it.capacity / Resource.ROCKET_FUEL.specificVolume
				
			} else if (it is LifeSupportContainerPart) {
				it.capacity / Resource.LIFE_SUPPORT.specificVolume
				
			} else if (it is NuclearContainerPart) {
				it.capacity / Resource.NUCLEAR_FISSION.specificVolume
				
			} else {
				0
			}
		}
	}
	
	override fun getLoadedMass(): Long {
		return getEmptyMass() + getFuelMass()
	}

	// cm³
	override fun getVolume(): Long {
		//TODO add armor
		return parts.sumByLong { it.getVolume() }
	}
	
	// cm
	override fun getRadius(): Int {
		val volume = getVolume()

		// V = πr²h, http://mathhelpforum.com/geometry/170076-how-find-cylinder-dimensions-volume-aspect-ratio.html
		val length = FastMath.pow(FastMath.pow(2.0, 2 * lengthToDiameterRatio) * volume / FastMath.PI, 1.0 / 3)
		var radius = FastMath.ceil(FastMath.sqrt(volume / FastMath.PI / length)).toInt()
		
		return radius + armorLayers
	}

	// cm^2
	fun getSurfaceArea(): Int {
		val volume = getVolume()

		// V = πr^2h, http://mathhelpforum.com/geometry/170076-how-find-cylinder-dimensions-volume-aspect-ratio.html
		val length = FastMath.pow(FastMath.pow(2.0, 2 * lengthToDiameterRatio) * volume / FastMath.PI, 1.0 / 3)
		val radius = FastMath.sqrt(volume / FastMath.PI / length)

		val surface = 2 * FastMath.PI * radius * length + 2 * FastMath.PI * radius * radius

//		println("length $length, diameter ${2 * radius}, surface $surface, volume ${FastMath.PI * length * radius * radius}")

		//TODO add armor
		return surface.toInt()
	}
}
