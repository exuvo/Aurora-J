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
import se.exuvo.aurora.galactic.PassiveSensor
import com.badlogic.ashley.core.Entity
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.galactic.PartRef
import kotlin.Suppress

class ShipComponent(var shipClass: ShipClass, val constructionTime: Long) : Component {
	var commissionDay: Int? = null
	val armor = Array<Int>(shipClass.getSurfaceArea(), { shipClass.armorLayers })
	val partHealth = Array<Int>(shipClass.getParts().size, { shipClass[it].part.maxHealth })
	val partEnabled = Array<Boolean>(shipClass.getParts().size, { true })
	val partState = Array<PartState>(shipClass.getParts().size, { PartState() })
	var cargo: Map<Resource, ShipCargo> = emptyMap()
	var partCargo: MutableList<Part> = ArrayList()
	var mass: Long = 0

	init {
		var containerPartRefs = shipClass[ContainerPart::class.java]

		if (containerPartRefs.isNotEmpty()) {

			val shipCargos = listOf(ShipCargo(CargoType.NORMAL), ShipCargo(CargoType.LIFE_SUPPORT), ShipCargo(CargoType.FUEL), ShipCargo(CargoType.NUCLEAR))

			for (containerRef in containerPartRefs) {
				for (cargo in shipCargos) {
					if (cargo.type.equals(containerRef.part.cargoType)) {
						cargo.maxVolume += containerRef.part.capacity
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
			val partRef = shipClass[partIndex]
			
			if (partRef.part is PoweringPart) {
				state.put(PoweringPartState())
			}
			
			if (partRef.part is PoweredPart) {
				state.put(PoweredPartState())
			}
			
			if (partRef.part is ChargedPart) {
				state.put(ChargedPartState())
			}
			
			if (partRef.part is PassiveSensor) {
				state.put(PassiveSensorState())
			}
			
			if (partRef.part is AmmunitionPart) {
				state.put(AmmunitionPartState(Queue<Part>(partRef.part.ammunitionAmount)))
			}
			
			if (partRef.part is ReloadablePart) {
				state.put(ReloadablePartState())
			}
			
			if (partRef.part is FueledPart) {
				state.put(FueledPartState())
			}
			
			if (partRef.part is TargetingComputer) {
				
				var tcs = TargetingComputerState()
				
				@Suppress("UNCHECKED_CAST")
				var defaultAssignments: List<PartRef<Part>>? = shipClass.defaultWeaponAssignments[partRef as PartRef<TargetingComputer>]
				
				if (defaultAssignments != null) {
					tcs.linkedWeapons = defaultAssignments.toMutableList()
				}
				
				state.put(tcs)
			}
		}
	}
	
	fun getPartState(partRef: PartRef<out Part>): PartState {
		return partState[partRef.index]
	}
	
	fun getPartHealth(partRef: PartRef<out Part>): Int {
		return partHealth[partRef.index]
	}
	
	fun setPartHealth(partRef: PartRef<out Part>, health: Int) {
		if (health < 0 || health > partRef.part.maxHealth){
			throw IllegalArgumentException()
		}

		partHealth[partRef.index] = health
	}

	fun isPartEnabled(partRef: PartRef<out Part>): Boolean {
		return partEnabled[partRef.index]
	}
	
	fun setPartEnabled(partRef: PartRef<out Part>, enabled: Boolean) {
		partEnabled[partRef.index] = enabled
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

data class PoweringPartState(var availiablePower: Long = 0,
														 var producedPower: Long = 0
)

data class PoweredPartState(var requestedPower: Long = 0,
														var givenPower: Long = 0
)

data class PassiveSensorState(var lastScan: Long = 0
)


data class ChargedPartState(var charge: Long = 0)

data class AmmunitionPartState(var ammunition: Queue<Part>)

data class ReloadablePartState(var loaded: Boolean = false,
															 var reloadCompletionAt: Int = 0
)

data class TargetingComputerState(var target: Entity? = null,
															 		var lockCompletionAt: Int = 0,
																	var linkedWeapons: MutableList<PartRef<Part>> = ArrayList()
)

