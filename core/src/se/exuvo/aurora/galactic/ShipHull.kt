package se.exuvo.aurora.galactic

import se.exuvo.aurora.starsystems.components.PowerScheme
import java.lang.IllegalArgumentException
import kotlin.reflect.KClass
import se.exuvo.aurora.utils.sumByLong
import se.exuvo.aurora.empires.components.ShipyardType
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.utils.ResetableLazy
import org.apache.commons.math3.util.FastMath

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
	var armorBlockHP = ByteArray(1, { 100 - 128 })
	var armorEnergyPerDamage = ShortArray(1, { 1000 }) // In J
	val preferredCargo: MutableMap<Resource, Long> = LinkedHashMap()
	val preferredMunitions: MutableMap<MunitionHull, Int> = LinkedHashMap()
	val preferredPartMunitions: MutableMap<PartRef<out Part>, MunitionHull> = LinkedHashMap()
	var powerScheme: PowerScheme = PowerScheme.SOLAR_BATTERY_REACTOR
	val defaultWeaponAssignments: MutableMap<PartRef<TargetingComputer>, List<PartRef<Part>>> = LinkedHashMap()
	
	var parentHull: ShipHull? = null
	val derivatives: MutableList<ShipHull> = ArrayList()
	
	var comment: String = ""
	
	val emptyMass by ResetableLazy (::calculateEmptyMass)
	val loadedMass by ResetableLazy (::calculateLoadedMass)
	val preferredCargoMass by ResetableLazy (::calculatePreferredCargoMass)
	val preferredMunitionMass by ResetableLazy (::calculatePreferredMunitionMass)
	val volume by ResetableLazy (::calculateVolume)
	val surfaceArea by ResetableLazy (::calculateSurfaceArea)
	val cost by ResetableLazy (::calculateCost)
	private val hashcode by ResetableLazy (::calculateHashCode)
	
	constructor(parentHull: ShipHull): this() {
		this.parentHull = parentHull
		
		name = parentHull.name
		hullClass = parentHull.hullClass
		requiredShipYardType = parentHull.requiredShipYardType
		armorLayers = parentHull.armorLayers
		armorBlockHP = parentHull.armorBlockHP
		powerScheme = parentHull.powerScheme
		
		parentHull.getParts().forEachFast{ part ->
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
	fun calculateEmptyMass(): Long {
		//TODO add armor
		return parts.sumByLong { it.mass }
	}
	
	fun calculateLoadedMass(): Long {
		var mass = emptyMass + preferredCargoMass + preferredMunitionMass
		
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
	
	fun calculatePreferredCargoMass(): Long {
		var mass = 0L
		
		for(amount in preferredCargo.values) {
			mass += amount
		}
		
		return mass
	}
	
	fun calculatePreferredMunitionMass(): Long {
		var mass = 0L
		
		for((munitonHull, amount) in preferredMunitions) {
			mass += amount * munitonHull.loadedMass
		}
		
		return mass
	}
	
	
	// cm³
	fun calculateVolume(): Long {
		//TODO add armor
		return parts.sumByLong { it.volume }
	}

	// cm²
	fun calculateSurfaceArea(): Long {
		// V = πr^2h, http://mathhelpforum.com/geometry/170076-how-find-cylinder-dimensions-volume-aspect-ratio.html
		val length = Math.pow(Math.pow(2.0, 2 * lengthToDiameterRatio) * volume / Math.PI, 1.0 / 3)
		val radius = Math.sqrt(volume / Math.PI / length)

		val surface = 2 * Math.PI * radius * length + 2 * Math.PI * radius * radius

//		println("length $length cm, diameter ${2 * radius} cm, surface $surface cm², volume ${Math.PI * length * radius * radius} cm³")

		//TODO add armor
		
		return surface.toLong()
	}
	
	fun getArmorWidth(): Int = 10 //FastMath.max(1, (surfaceArea / 1000000).toInt())
	
	fun calculateCost(): Map<Resource, Long> {
		val cost = HashMap<Resource, Long>()
		
		parts.forEachFast { part ->
			part.cost.forEach{ resource, amount ->
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
	
	fun calculateHashCode() : Int {
		var hash = 1;
		hash = 37 * hash + name.hashCode()
		hash = 37 * hash + designDay
		hash = 37 * hash + armorLayers
		hash = 37 * hash + powerScheme.ordinal
		return hash
	}

	override fun hashCode(): Int = hashcode

 fun resetLazyCache() {
		(this::emptyMass.getDelegate() as ResetableLazy).reset()
		(this::loadedMass.getDelegate() as ResetableLazy).reset()
		(this::preferredCargoMass.getDelegate() as ResetableLazy).reset()
		(this::preferredMunitionMass.getDelegate() as ResetableLazy).reset()
		(this::volume.getDelegate() as ResetableLazy).reset()
		(this::surfaceArea.getDelegate() as ResetableLazy).reset()
		(this::cost.getDelegate() as ResetableLazy).reset()
		(this::hashcode.getDelegate() as ResetableLazy).reset()
	}
}

data class PartRef<T: Part>(val part: T, val index: Int)

data class ShipHullClass(var name: String, var code: String) {
	companion object {
		val NONE = ShipHullClass("", "")
	}
}

