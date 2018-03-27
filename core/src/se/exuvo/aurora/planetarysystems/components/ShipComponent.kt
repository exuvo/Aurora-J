package se.exuvo.aurora.planetarysystems.components

import com.badlogic.ashley.core.Component
import se.exuvo.aurora.galactic.CargoType
import se.exuvo.aurora.galactic.ContainerPart
import se.exuvo.aurora.galactic.Part
import se.exuvo.aurora.galactic.Resource
import se.exuvo.aurora.galactic.ShipClass
import java.security.InvalidParameterException
import java.util.ArrayList
import java.lang.IllegalArgumentException
import se.exuvo.aurora.galactic.PoweredPart
import se.exuvo.aurora.galactic.ChargedPart
import se.exuvo.aurora.galactic.AmmunitionPart
import com.badlogic.gdx.utils.Queue
import se.exuvo.aurora.galactic.ReloadablePart
import se.exuvo.aurora.galactic.FueledPart
import se.exuvo.aurora.galactic.PoweringPart
import kotlin.reflect.KClass

class ShipComponent(var shipClass: ShipClass, val constructionTime: Long) : Component {
	var commissionDay: Int? = null
	val armor = Array<Int>(shipClass.getSurfaceArea(), { shipClass.armorLayers })
	val partHealth = Array<Int>(shipClass.parts.size, { shipClass.parts[it].maxHealth })
	val partEnabled = Array<Boolean>(shipClass.parts.size, { true })
	val partState = Array<PartState>(shipClass.parts.size, { PartState() })
	var cargo: Map<Resource, ShipCargo> = emptyMap()
	var partCargo: MutableList<Part> = ArrayList()
	var mass: Long = 0

	init {
		var containerParts = shipClass[ContainerPart::class.java]

		if (containerParts.isNotEmpty()) {

			val shipCargos = listOf(ShipCargo(CargoType.NORMAL), ShipCargo(CargoType.LIFE_SUPPORT), ShipCargo(CargoType.FUEL), ShipCargo(CargoType.NUCLEAR))

			for (container in containerParts) {
				for (cargo in shipCargos) {
					if (cargo.type.equals(container.cargoType)) {
						cargo.maxVolume += container.capacity
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
		
		partState.forEachIndexed { partIndex, state ->
			val part = shipClass.parts[partIndex]
			
			if (part is PoweringPart) {
				state.put(PoweringPartState())
			}
			
			if (part is PoweredPart) {
				state.put(PoweredPartState())
			}
			
			if (part is ChargedPart) {
				state.put(ChargedPartState())
			}
			
			if (part is AmmunitionPart) {
				state.put(AmmunitionPartState(Queue<Part>(part.ammunitionAmount)))
			}
			
			if (part is ReloadablePart) {
				state.put(ReloadablePartState())
			}
			
			if (part is FueledPart) {
				state.put(FueledPartState())
			}
		}
	}
	
	//TODO replace shipClass.parts.indexOf(part) with map
	
	fun getPartState(part: Part): PartState {
		val index = shipClass.parts.indexOf(part)

		if (index == -1) {
			throw IllegalArgumentException()
		}

		return partState[index]
	}
	
	fun getPartHealth(part: Part): Int {
		val index = shipClass.parts.indexOf(part)

		if (index == -1) {
			throw IllegalArgumentException()
		}

		return partHealth[index]
	}
	
	fun setPartHealth(part: Part, health: Int) {
		val index = shipClass.parts.indexOf(part)

		if (index == -1) {
			throw IllegalArgumentException()
		}
		
		if (health < 0 || health > shipClass.parts[index].maxHealth){
			throw IllegalArgumentException()
		}

		partHealth[index] = health
	}

	fun isPartEnabled(part: Part): Boolean {
		val index = shipClass.parts.indexOf(part)

		if (index == -1) {
			throw IllegalArgumentException()
		}

		return partEnabled[index]
	}
	
	fun setPartEnabled(part: Part, enabled: Boolean) {
		val index = shipClass.parts.indexOf(part)

		if (index == -1) {
			throw IllegalArgumentException()
		}

		partEnabled[index] = enabled
	}
	
	fun getCargoAmount(resource: Resource): Int {

		val shipCargo = cargo[resource]

		if (shipCargo != null) {

			val available = shipCargo.contents[resource]

			if (available != null) {
				return available
			}
		}

		return 0
	}
	
	fun getUsedCargoVolume(resource: Resource): Int {

		val shipCargo = cargo[resource]

		if (shipCargo != null) {

			return shipCargo.usedVolume
		}

		return 0
	}
	
	fun getMaxCargoVolume(resource: Resource): Int {

		val shipCargo = cargo[resource]

		if (shipCargo != null) {

			return shipCargo.maxVolume
		}

		return 0
	}
	
	fun getUsedCargoVolume(type: CargoType): Int {

		val shipCargo = cargo[type.resources[0]]

		if (shipCargo != null) {

			return shipCargo.usedVolume
		}

		return 0
	}
	
	fun getMaxCargoVolume(type: CargoType): Int {

		val shipCargo = cargo[type.resources[0]]

		if (shipCargo != null) {

			return shipCargo.maxVolume
		}

		return 0
	}
	
	fun getUsedCargoMass(resource: Resource): Int {

		val shipCargo = cargo[resource]

		if (shipCargo != null) {

			val amount = shipCargo.contents[resource]
			
			if (amount != null) {
				return amount
			}
		}

		return 0
	}
	
	fun getUsedCargoMass(type: CargoType): Int {

		val shipCargo = cargo[type.resources[0]]

		if (shipCargo != null) {

			return shipCargo.contents.values.sum()
		}

		return 0
	}

	fun addCargo(resource: Resource, amount: Int): Boolean {

		if (resource.specificVolume == 0) {
			throw InvalidParameterException()
		}

		val shipCargo = cargo[resource]

		if (shipCargo != null) {

			val volumeToBeStored = amount * resource.specificVolume

			if (shipCargo.usedVolume + volumeToBeStored > shipCargo.maxVolume) {
				return false;
			}

			shipCargo.usedVolume += volumeToBeStored
			shipCargo.contents[resource] = shipCargo.contents[resource]!! + amount

			return true
		}

		return false
	}

	fun retrieveCargo(resource: Resource, amount: Int): Int {

		if (resource.specificVolume == 0) {
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
			shipCargo.usedVolume -= retrievedAmount * resource.specificVolume

			return retrievedAmount
		}

		return 0
	}

	fun addCargo(part: Part): Boolean {

		val shipCargo = cargo[Resource.ITEMS]

		if (shipCargo != null) {

			val volumeToBeStored = part.getVolume()

			if (shipCargo.usedVolume + volumeToBeStored > shipCargo.maxVolume) {
				return false;
			}

			shipCargo.usedVolume += volumeToBeStored
			partCargo.add(part)

			return true
		}

		return false
	}

	fun retrieveCargo(part: Part): Boolean {

		if (!partCargo.remove(part)) {
			return false
		}

		val shipCargo = cargo[Resource.ITEMS]
		shipCargo!!.usedVolume -= part.getVolume()

		return true
	}
}

data class ShipCargo(val type: CargoType) {
	var maxVolume = 0
	var usedVolume = 0
	var contents: MutableMap<Resource, Int> = LinkedHashMap()

	init {
		for (resource in type.resources) {
			contents[resource] = 0
		}
	}
}

@Suppress("UNCHECKED_CAST")
class PartState {
	private val states = HashMap<KClass<*>, Any>()

	operator fun <T : Any> get(serviceClass: KClass<T>) = states[serviceClass] as T
	fun <T : Any> tryGet(serviceClass: KClass<T>) = states[serviceClass] as? T

	fun put(state: Any) {
		states[state::class] = state
	}

	fun put(state: Any, savedClass: KClass<*>) {
		states[savedClass] = state
	}
}

data class FueledPartState(var fuelEnergyRemaining: Long = 0,
													 var totalFuelEnergyRemaining: Long = 0
)

data class PoweringPartState(var availiablePower: Int = 0,
														 var producedPower: Int = 0
)

data class PoweredPartState(var requestedPower: Int = 0,
														var givenPower: Int = 0
)

data class ChargedPartState(var charge: Int = 0)

data class AmmunitionPartState(var ammunition: Queue<Part>)

data class WeaponPartState(var requestedPower: Int = 0,
													 var givenPower: Int = 0
)

data class ReloadablePartState(var loaded: Boolean = true,
															 var reloadedAt: Int = 0
)

