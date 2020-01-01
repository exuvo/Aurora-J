package se.exuvo.aurora.galactic

import se.exuvo.aurora.planetarysystems.components.PowerScheme
import java.lang.IllegalArgumentException
import kotlin.reflect.KClass
import se.exuvo.aurora.utils.sumByLong
import se.exuvo.aurora.empires.components.ShipyardType
import se.exuvo.aurora.utils.Units

class ShipHull() {
	companion object {
		const val lengthToDiameterRatio = 2.0
	}
	
	var name: String = ""
	var hullClass: ShipHullClass = ShipHullClass.NONE
	var designDay: Int = 0
	var locked = false
	var obsolete = false
	var requiredShipYardType = ShipyardType.CIVILIAN
	private val parts: MutableList<Part> = ArrayList()
	private val partRefs: MutableList<PartRef<Part>> = ArrayList()
	var armorLayers = 1 // Centimeters of armor
	var armorBlockHP:Short = 100
	val preferredCargo: MutableMap<Resource, Long> = LinkedHashMap()
	val preferredMunitions: MutableMap<MunitionHull, Int> = LinkedHashMap()
	val preferredPartMunitions: MutableMap<PartRef<out Part>, MunitionHull> = LinkedHashMap()
	var powerScheme: PowerScheme = PowerScheme.SOLAR_BATTERY_REACTOR
	val defaultWeaponAssignments: MutableMap<PartRef<TargetingComputer>, List<PartRef<Part>>> = LinkedHashMap()
	
	var parentHull: ShipHull? = null
	val derivatives: MutableList<ShipHull> = ArrayList()
	
	var comment: String = ""
	
	constructor(parentHull: ShipHull): this() {
		this.parentHull = parentHull
		
		name = parentHull.name
		hullClass = parentHull.hullClass
		requiredShipYardType = parentHull.requiredShipYardType
		armorLayers = parentHull.armorLayers
		armorBlockHP = parentHull.armorBlockHP
		powerScheme = parentHull.powerScheme
		
		parentHull.getParts().forEach{ part ->
			addPart(part)
		}
		
		parentHull.preferredCargo.forEach{ resource, amount ->
			preferredCargo[resource] = amount
		}
		
		parentHull.preferredMunitions.forEach{ munitionHull, amount ->
			preferredMunitions[munitionHull] = amount
		}
		
		parentHull.preferredPartMunitions.forEach{ partRef, munitionHull ->
			preferredPartMunitions[partRef] = munitionHull
		}
		
		parentHull.defaultWeaponAssignments.forEach{ partRef, partRefs ->
			defaultWeaponAssignments[partRef] = ArrayList(partRefs)
		}
		
		parentHull.derivatives += this
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

	fun getCrewRequirement(): Int {
		return parts.sumBy { it.crewRequirement }
	}

	// Kg
	fun getEmptyMass(): Long {
		//TODO add armor
		return parts.sumByLong { it.getMass() }
	}
	
	fun getLoadedMass(): Long {
		var mass = getEmptyMass() + getPreferredCargoMass() + getPreferredMunitionMass()
		
		mass += parts.sumByLong {
			if (it is FuelContainerPart) {
				it.capacity / Resource.ROCKET_FUEL.specificVolume
				
			} else if (it is LifeSupportContainerPart) {
				it.capacity / Resource.LIFE_SUPPORT.specificVolume
				
			} else {
				0L
			}
		}
		
		return mass
	}
	
	fun getPreferredCargoMass(): Long {
		var mass = 0L
		
		for(amount in preferredCargo.values) {
			mass += amount
		}
		
		return mass
	}
	
	fun getPreferredMunitionMass(): Long {
		var mass = 0L
		
		for((munitonHull, amount) in preferredMunitions) {
			mass += amount * munitonHull.getLoadedMass()
		}
		
		return mass
	}
	
	
	// cm³
	fun getVolume(): Long {
		//TODO add armor
		return parts.sumByLong { it.getVolume() }
	}

	// cm^2
	fun getSurfaceArea(): Int {
		val volume = getVolume()

		// V = πr^2h, http://mathhelpforum.com/geometry/170076-how-find-cylinder-dimensions-volume-aspect-ratio.html
		val length = Math.pow(Math.pow(2.0, 2 * lengthToDiameterRatio) * volume / Math.PI, 1.0 / 3)
		val radius = Math.sqrt(volume / Math.PI / length)

		val surface = 2 * Math.PI * radius * length + 2 * Math.PI * radius * radius

//		println("length $length, diameter ${2 * radius}, surface $surface, volume ${Math.PI * length * radius * radius}")

		//TODO add armor
		
		return surface.toInt()
	}
	
	fun getCost(): Map<Resource, Long> {
		val cost = HashMap<Resource, Long>()
		
		parts.forEach { part ->
			part.cost.forEach{resource, amount ->
				var prevCost = cost[resource]
				
				if (prevCost == null) {
					cost[resource] = amount
				} else {
					cost[resource] = prevCost + amount
				}
			}
		}
		return cost
	}
	
	override fun toString(): String {
		val parentHull = parentHull
		
		if (parentHull == null) {
			return "$name ${Units.daysToYear(designDay)}"
		}
		
		return "$name ${Units.daysToYear(parentHull.designDay)}-${Units.daysToSubYear(designDay)}"
	} 
	
	private val hashcode: Int by lazy (LazyThreadSafetyMode.NONE) {
		var hash = 1;
		hash = 37 * hash + name.hashCode()
		hash = 37 * hash + designDay
		hash = 37 * hash + armorLayers
		hash = 37 * hash + powerScheme.ordinal
		hash
	}

	override fun hashCode(): Int = hashcode
}

data class PartRef<T: Part>(val part: T, val index: Int)

data class ShipHullClass(var name: String, var code: String) {
	companion object {
		val NONE = ShipHullClass("", "")
	}
}

