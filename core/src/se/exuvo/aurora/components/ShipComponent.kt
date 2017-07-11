package se.exuvo.aurora.components

import com.badlogic.ashley.core.Component
import se.exuvo.aurora.shipcomponents.CargoType
import se.exuvo.aurora.shipcomponents.ContainerPart
import se.exuvo.aurora.shipcomponents.Part
import se.exuvo.aurora.shipcomponents.Resource
import se.exuvo.aurora.shipcomponents.ShipClass
import java.util.ArrayList
import java.security.InvalidParameterException

data class ShipComponent(var shipClass: ShipClass, val constructionDay: Int) : Component {
	var commissionDay: Int? = null
	val armor = Array<Array<Boolean>>(shipClass.getSurfaceArea(), { Array<Boolean>(shipClass.armorLayers, { true }) })
	val partHealth = Array<Int>(shipClass.parts.size, { shipClass.parts[it].maxHealth })
	val partState = Array<Int>(shipClass.parts.size, { shipClass.parts[it].maxHealth })
	var cargo: Map<Resource, ShipCargo> = emptyMap()
	var itemCargo: MutableList<Part> = ArrayList()

	init {
		var containerParts = shipClass[ContainerPart::class.java]

		if (containerParts.isNotEmpty()) {

			val shipCargos = listOf(ShipCargo(CargoType.NORMAL), ShipCargo(CargoType.LIFE_SUPPORT), ShipCargo(CargoType.FUEL), ShipCargo(CargoType.NUCLEAR))

			for (container in containerParts) {
				for (cargo in shipCargos) {
					if (cargo.type.equals(container.cargoType)) {
						cargo.maxCapacity += container.capacity
						break
					}
				}
			}

			val mutableCargo = LinkedHashMap<Resource, ShipCargo>()

			for (shipCargo in shipCargos) {
				for (resource in shipCargo.type.resources) {
					mutableCargo[resource] = shipCargo
				}
			}

			cargo = mutableCargo
		}
	}

	fun addCargo(resource: Resource, amount: Int): Boolean {

		if (resource.density == 0) {
			throw InvalidParameterException()
		}

		val shipCargo = cargo[resource]

		if (shipCargo != null) {

			val volumeToBeStored = amount * resource.density

			if (shipCargo.usedCapacity + volumeToBeStored > shipCargo.maxCapacity) {
				return false;
			}

			shipCargo.usedCapacity += volumeToBeStored
			shipCargo.contents[resource] = shipCargo.contents[resource]!! + amount

			return true
		}

		return false
	}

	fun retrieveCargo(resource: Resource, amount: Int): Int {

		if (resource.density == 0) {
			throw InvalidParameterException()
		}

		val shipCargo = cargo[resource]

		if (shipCargo != null) {

			val available = shipCargo.contents[resource]

			if (available == null || available == 0) {
				return 0
			}

			var retrievedAmount = amount

			if (available < amount) {
				retrievedAmount = available
			}

			shipCargo.contents[resource] = available - retrievedAmount
			shipCargo.usedCapacity -= retrievedAmount * resource.density

			return retrievedAmount
		}

		return 0
	}

	fun addCargo(part: Part): Boolean {

		val shipCargo = cargo[Resource.ITEMS]

		if (shipCargo != null) {

			val volumeToBeStored = part.size

			if (shipCargo.usedCapacity + volumeToBeStored > shipCargo.maxCapacity) {
				return false;
			}

			shipCargo.usedCapacity += volumeToBeStored
			itemCargo.add(part)

			return true
		}

		return false
	}

	fun retrieveCargo(part: Part): Boolean {

		if (!itemCargo.remove(part)) {
			return false
		}

		val shipCargo = cargo[Resource.ITEMS]
		shipCargo!!.usedCapacity -= part.size

		return true
	}
}

class ShipCargo(val type: CargoType) {
	var maxCapacity = 0
	var usedCapacity = 0
	var contents: MutableMap<Resource, Int> = LinkedHashMap()

	init {
		for (resource in type.resources) {
			contents[resource] = 0
		}
	}
}
