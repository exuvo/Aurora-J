package se.exuvo.aurora.empires.components

import com.artemis.Component
import se.exuvo.aurora.galactic.ShipHull
import com.artemis.utils.Bag
import se.exuvo.aurora.galactic.Resource
import java.security.InvalidParameterException
import java.lang.IllegalStateException
import se.exuvo.aurora.utils.Units


class ColonyComponent() : Component() {
	var population: Long = 0
	val resources = LinkedHashMap<Resource, Long>()
	val shipyards = ArrayList<Shipyard>()
	
	init {
		Resource.values().forEach { r -> resources[r] = 0L }
	}
	
	fun set(population: Long): ColonyComponent {
		this.population = population
		return this
	}
	
	fun addCargo(resource: Resource, amount: Long) {
		resources[resource] = resources[resource]!! + amount
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
}

class Shipyard (
	val location: ShipyardLocation,
	val type: ShipyardType
) {
	var capacity = 1000L // In cm³
	var fuelCostPerMass = 0.0 //kg fuel per kg of hull to launch into space
	var buildRate = 1 // kg per day
	var tooledHull: ShipHull? = null
	val slipways = ArrayList<ShipyardSlipway>()
	
	var modificationActivity: ShipyardModification? = null
	var modificationRate = 1;
	var modificationProgress = 0L
	
	init {
		if (location == ShipyardLocation.TERRESTIAL) {
			fuelCostPerMass = 1.0
		}
	}
}

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

enum class ShipyardLocation(val short: String) {
	ORBITAL("ORB"),
	TERRESTIAL("GND") // Cheaper to modify shipyard, quicker to build ships, needs fuel to launch ships into space
}

enum class ShipyardType(val short: String, val modificationMultiplier: Long) {
	CIVILIAN("CIV", 100), // Cheaper to build and expand
	MILITARY("MIL", 150)
}

interface ShipyardModification {
	fun getCost(shipyard: Shipyard): Long
	fun complete(shipyard: Shipyard)
	fun getDescription(): String
}

class ShipyardModificationExpandCapacity(val addedCapacity: Long): ShipyardModification {
	override fun getCost(shipyard: Shipyard) = (addedCapacity * shipyard.slipways.size * shipyard.type.modificationMultiplier) / 100
	override fun complete(shipyard: Shipyard) {
		shipyard.capacity += addedCapacity
	}
	override fun getDescription() = "Expanding capacity by " + Units.volumeToString(addedCapacity)
}

class ShipyardModificationRetool(val assignedHull: ShipHull): ShipyardModification {
	override fun getCost(shipyard: Shipyard): Long {
		if (assignedHull.parentHull == shipyard.tooledHull) {
			return (shipyard.capacity * shipyard.slipways.size * shipyard.type.modificationMultiplier) / 200
		}
		
		return (shipyard.capacity * shipyard.slipways.size * shipyard.type.modificationMultiplier) / 100
	}
	override fun complete(shipyard: Shipyard) {
		shipyard.tooledHull = assignedHull
	}
	override fun getDescription() = "Retooling to " + assignedHull
}

class ShipyardModificationAddSlipway(): ShipyardModification {
	override fun getCost(shipyard: Shipyard) = (shipyard.capacity * shipyard.type.modificationMultiplier) / 100
	override fun complete(shipyard: Shipyard) {
		shipyard.slipways + ShipyardSlipway()
	}
	override fun getDescription() = "Adding slipway"
}
