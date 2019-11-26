package se.exuvo.aurora.empires.components

import com.artemis.Component
import se.exuvo.aurora.galactic.ShipHull
import com.artemis.utils.Bag
import se.exuvo.aurora.galactic.Resource
import java.security.InvalidParameterException


class ColonyComponent() : Component() {
	var population: Int = 0
	val resources = LinkedHashMap<Resource, Long>()
	val shipyards = ArrayList<Shipyard>()
	
	init {
		Resource.values().forEach { r -> resources[r] = 0L }
	}
	
	fun set(population: Int): ColonyComponent {
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
	var priority = 0
	var capacity = 0L // In cm³
	var fuelCostPerMass = 0.0 //kg fuel per kg of hull to launch into space
	var buildRate = 1 // kg per day
	var assignedHull: ShipHull? = null
	val slipways = ArrayList<ShipyardSlipway>()
	
	var modificationActivity: ShipyardModification? = null
	var modificationRate = 1;
	var modificationProgress = 0L
}

class ShipyardSlipway(var hull: ShipHull? = null) {
	val remainingResources: Map<Resource, Long> = LinkedHashMap()
}

enum class ShipyardLocation {
	ORBITAL,
	TERRESTIAL // Cheaper to modify shipyard, quicker to build ships, needs fuel to launch ships into space
}

enum class ShipyardType {
	CIVILIAN,
	MILITARY
}

interface ShipyardModification {
	fun getCost(shipyard: Shipyard): Long
	fun complete(shipyard: Shipyard)
}

class ShipyardModificationExpandCapacity(val addedCapacity: Long): ShipyardModification {
	override fun getCost(shipyard: Shipyard) = addedCapacity * shipyard.slipways.size
	override fun complete(shipyard: Shipyard) {
		shipyard.capacity += addedCapacity
	}
}

class ShipyardModificationRetool(val assignedHull: ShipHull): ShipyardModification {
	override fun getCost(shipyard: Shipyard) = shipyard.capacity * shipyard.slipways.size
	override fun complete(shipyard: Shipyard) {
		shipyard.assignedHull = assignedHull
	}
}

class ShipyardModificationAddSlipway(): ShipyardModification {
	override fun getCost(shipyard: Shipyard) = shipyard.capacity
	override fun complete(shipyard: Shipyard) {
		shipyard.slipways + ShipyardSlipway(shipyard.assignedHull)
	}
}