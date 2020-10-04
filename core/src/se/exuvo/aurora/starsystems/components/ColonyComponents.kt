package se.exuvo.aurora.empires.components

import com.artemis.Component
import se.exuvo.aurora.galactic.MunitionHull
import se.exuvo.aurora.galactic.Resource
import se.exuvo.aurora.galactic.ShipHull
import se.exuvo.aurora.starsystems.components.CloneableComponent
import se.exuvo.aurora.utils.Units

class PlanetComponent() : Component(), CloneableComponent<PlanetComponent> {
	var cleanWater = 0L
	var pollutedWater = 0L 
	var usableLandArea = 0L // km²
	var arableLandArea = 0L // km² (subtracts from usable when used)
	var blockedLandArea = 0L
	var gravity = 100 // percentage of earth
	var atmosphericDensity = 1225 // g/m³ at 1013.25 hPa (abs) and 15°C
	var atmospheBreathability = 100 // percentage
	var temperature = 20 // celcius
	val minableResources = LinkedHashMap<Resource, Long>()
	val resourceAccessibility = LinkedHashMap<Resource, Int>()
	
	override fun copy(c: PlanetComponent) {
		c.cleanWater = cleanWater
		c.pollutedWater = pollutedWater
		c.usableLandArea = usableLandArea
		c.arableLandArea = arableLandArea
		c.blockedLandArea = blockedLandArea
		c.gravity = gravity
		c.atmosphericDensity = atmosphericDensity
		c.atmospheBreathability = atmospheBreathability
		c.temperature = temperature
		c.minableResources.clear()
		c.minableResources.putAll(minableResources)
		c.resourceAccessibility.clear()
		c.resourceAccessibility.putAll(resourceAccessibility)
	}
}

class ColonyComponent() : Component(), CloneableComponent<ColonyComponent> {
	var population = 0L
	var housingLandArea = 0L
	var farmingLandArea = 0L
	var industrialLandArea = 0L // pollutes water
	var miningLandArea = 0L // pollutes water
	val buildings = ArrayList<Building>()
	val shipyards = ArrayList<Shipyard>()
	
	fun set(population: Long,
					housingLandArea: Long,
					farmingLandArea: Long,
					industrialLandArea: Long
	): ColonyComponent {
		this.population = population
		this.housingLandArea = housingLandArea
		this.farmingLandArea = farmingLandArea
		this.industrialLandArea = industrialLandArea
		return this
	}
	
	override fun copy(c: ColonyComponent) {
		c.set(population, housingLandArea, farmingLandArea, industrialLandArea)
		c.buildings.clear()
		c.buildings.addAll(buildings)
		c.shipyards.clear()
		c.shipyards.addAll(shipyards) //TODO deep copy
	}
}

abstract class Building {
	var name: String = "";
	val cost = LinkedHashMap<Resource, Long>()
}

class EmptyBuildingSlot : Building() {
	
}

class Shipyard (
	val location: ShipyardLocation,
	val type: ShipyardType
) {
	var capacity = 1000L // In cm³
	var fuelCostPerMass = 0.0 //kg fuel per kg of hull to launch into space
	var buildRate = location.baseBuildrate // kg per hour
	var tooledHull: ShipHull? = null
	val slipways = ArrayList<ShipyardSlipway>()
	
	var modificationActivity: ShipyardModification? = null
	var modificationRate = 1000; // kg per hour
	var modificationProgress = 0L
	
	init {
		if (location == ShipyardLocation.TERRESTIAL) {
			fuelCostPerMass = 1.0
		}
	}
}

//TODO build hull and armor with resources, handle parts separetely for cancelling, reworks and storage
class ShipyardSlipway() {
	var hull: ShipHull? = null // If set, being built
	var hullCost: Map<Resource, Long> = emptyMap()
	val usedResources = HashMap<Resource, Long>()
	
	fun build(newHull: ShipHull) {
		if (hull != null) {
			throw IllegalStateException("Already building a ship")
		}
		
		hull = newHull
		hullCost = newHull.cost
		usedResources.clear()
		hullCost.forEach { entry ->
			usedResources[entry.key] = 0L
		}
	}
	
	fun usedResources() = usedResources.values.sum()
	fun totalCost() = hullCost.values.sum()
	
	fun progress(): Int {
		val usedResources = usedResources()
		
		if (usedResources == 0L) {
			return 0;
		}
		
		return ((100L * usedResources) / totalCost()).toInt()
	}
}

enum class ShipyardLocation(val short: String, val baseBuildrate: Long, val modificationMultiplier: Long) {
	ORBITAL(   "ORB", 100, 120),
	TERRESTIAL("GND", 150, 100) // Cheaper to modify shipyard, quicker to build ships, needs fuel to launch ships into space
}

enum class ShipyardType(val short: String, val modificationMultiplier: Long) {
	CIVILIAN("CIV", 100),
	MILITARY("MIL", 150) // More expensive to build and expand due to need to handle explosives
}

enum class ShipyardModifications(name: String) {
	RETOOL("Retool"),
	EXPAND_CAPACITY("Expand capacity"),
	ADD_SLIPWAY("Add slipway")
}

interface ShipyardModification {
	fun getCost(shipyard: Shipyard): Long
	fun complete(shipyard: Shipyard)
	fun getDescription(): String
}

class ShipyardModificationExpandCapacity(val addedCapacity: Long): ShipyardModification {
	override fun getCost(shipyard: Shipyard) = (addedCapacity * shipyard.slipways.size * shipyard.type.modificationMultiplier * shipyard.location.modificationMultiplier) / 10L
	override fun complete(shipyard: Shipyard) {
		shipyard.capacity += addedCapacity
	}
	override fun getDescription() = "Expanding capacity by " + Units.volumeToString(addedCapacity)
}

class ShipyardModificationRetool(val assignedHull: ShipHull): ShipyardModification {
	override fun getCost(shipyard: Shipyard): Long {
		return (shipyard.capacity * shipyard.slipways.size * shipyard.type.modificationMultiplier * shipyard.location.modificationMultiplier) / 100L
	}
	override fun complete(shipyard: Shipyard) {
		shipyard.tooledHull = assignedHull
	}
	override fun getDescription() = "Retooling to " + assignedHull
}

class ShipyardModificationAddSlipway(): ShipyardModification {
	override fun getCost(shipyard: Shipyard) = (shipyard.capacity * shipyard.type.modificationMultiplier * shipyard.location.modificationMultiplier) / 10L
	override fun complete(shipyard: Shipyard) {
		shipyard.slipways += ShipyardSlipway()
	}
	override fun getDescription() = "Adding slipway"
}
