package se.exuvo.aurora.components

import com.badlogic.ashley.core.Component
import se.exuvo.aurora.shipcomponents.ContainerPart
import se.exuvo.aurora.shipcomponents.Part
import se.exuvo.aurora.shipcomponents.ResourceContainerPart
import se.exuvo.aurora.shipcomponents.Resources
import se.exuvo.aurora.shipcomponents.ShipClass

data class ShipComponent(var shipClass: ShipClass, val constructionDay: Int) : Component {
	var commissionDay: Int? = null
	val armor = Array<Array<Boolean>>(shipClass.getSurfaceArea(), { Array<Boolean>(shipClass.armorLayers, { true }) })
	val partHealth = Array<Int>(shipClass.parts.size, { shipClass.parts[it].maxHealth })
	var containers: List<ShipContainer> = emptyList()

	init {
		var containerParts = shipClass[ContainerPart::class.java]

		if (containerParts.isNotEmpty()) {
			containers = List<ShipContainer>(containerParts.size, {
				val container = containerParts[it]
				when (container) {
					is ResourceContainerPart -> ShipResourceContainer(container)
					else -> ShipItemContainer(container)
				}
			})
		}
	}

}

abstract class ShipContainer(val containerPart: ContainerPart) {
	var usedCapacity = 0
	fun getMaxCapacity() = containerPart.capacity
}

class ShipItemContainer(containerPart: ContainerPart) : ShipContainer(containerPart) {
	var contents: MutableMap<Part, Int> = LinkedHashMap()
}

class ShipResourceContainer(val resourceContainerPart: ResourceContainerPart) : ShipContainer(resourceContainerPart) {
	var contents: MutableMap<Resources, Int> = LinkedHashMap()
	// If null this is a item container
	val allowedResources: List<Resources>? = resourceContainerPart.acceptedResources
}