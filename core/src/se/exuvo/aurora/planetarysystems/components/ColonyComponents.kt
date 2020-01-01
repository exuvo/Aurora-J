package se.exuvo.aurora.empires.components

import com.artemis.Component
import se.exuvo.aurora.galactic.ShipHull
import com.artemis.utils.Bag
import se.exuvo.aurora.galactic.Resource
import java.security.InvalidParameterException
import java.lang.IllegalStateException
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.galactic.MunitionHull

//TODO part storage
class ColonyComponent() : Component() {
	var population: Long = 0
	val resources = LinkedHashMap<Resource, Long>()
	val munitions = LinkedHashMap<MunitionHull, Int>()
	val shipyards = ArrayList<Shipyard>()
	
	init {
		val exludedResources = listOf(Resource.MISSILES, Resource.SABOTS)
		for (r in Resource.values()) {
			if (!exludedResources.contains(r)) {
				resources[r] = 10000000L
			}
		}
	}
	
	fun set(population: Long): ColonyComponent {
		this.population = population
		return this
	}
	
	fun getCargoAmount(resource: Resource): Long = resources[resource]!!
	
	fun getCargoAmount(munitionHull: MunitionHull): Int {

		var available = munitions[munitionHull]

		if (available != null) {
			return available
		}

		return 0
	}
	
	fun addCargo(resource: Resource, amount: Long) {
		resources[resource] = resources[resource]!! + amount
	}
	
	fun addCargo(munitionHull: MunitionHull, amount: Int) {
		
		var stored = munitions[munitionHull]

		if (stored == null) {
			stored = 0
		}
		
		munitions[munitionHull] = stored + amount
	}
	
	fun retrieveCargo(resource: Resource, amount: Long): Long {

		val available = resources[resource]

		if (available!! == 0L) {
			return 0L
		}

		var retrievedAmount = amount

		if (available < amount) {
			retrievedAmount = available
		}

		resources[resource] = available - retrievedAmount

		return retrievedAmount
	}
	
	fun retrieveCargo(munitionHull: MunitionHull, amount: Int): Int {

		val available = munitions[munitionHull]

		if (available == null || available == 0) {
			return 0
		}

		var retrievedAmount = amount

		if (available < amount) {
			retrievedAmount = available
		}

		munitions[munitionHull] = available - retrievedAmount

		return retrievedAmount
	}
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
		hullCost = newHull.getCost()
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
	TERRESTIAL("GND", 100, 100) // Cheaper to modify shipyard, quicker to build ships, needs fuel to launch ships into space
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
