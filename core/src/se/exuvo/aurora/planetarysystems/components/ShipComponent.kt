package se.exuvo.aurora.planetarysystems.components

import com.artemis.Component
import se.exuvo.aurora.galactic.CargoType
import se.exuvo.aurora.galactic.ContainerPart
import se.exuvo.aurora.galactic.Part
import se.exuvo.aurora.galactic.Resource
import se.exuvo.aurora.galactic.ShipHull
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
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.galactic.PartRef
import kotlin.Suppress
import se.exuvo.aurora.galactic.MunitionHull
import com.artemis.Entity

class ShipComponent() : Component() {
	lateinit var hull: ShipHull
	var constructionTime: Long = -1
	var commissionDay: Int? = null
	lateinit var armor: Array<Int>
	lateinit var partHealth: Array<Int>
	lateinit var partEnabled: Array<Boolean>
	lateinit var partState: Array<PartState>
	lateinit var cargo: Map<Resource, ShipCargo>
	lateinit var munitionCargo: MutableMap<MunitionHull, Int>
	var cargoChanged = true

	fun set(hull: ShipHull,
					constructionTime: Long
	): ShipComponent {
		this.hull = hull
		this.constructionTime = constructionTime

		armor = Array<Int>(hull.getSurfaceArea(), { hull.armorLayers })
		partHealth = Array<Int>(hull.getParts().size, { hull[it].part.maxHealth })
		partEnabled = Array<Boolean>(hull.getParts().size, { true })
		partState = Array<PartState>(hull.getParts().size, { PartState() })
		cargo = emptyMap()
		munitionCargo = LinkedHashMap()
		
		var containerPartRefs = hull[ContainerPart::class]

		if (containerPartRefs.isNotEmpty()) {

			val shipCargos = listOf(ShipCargo(CargoType.NORMAL), ShipCargo(CargoType.LIFE_SUPPORT), ShipCargo(CargoType.FUEL), ShipCargo(CargoType.AMMUNITION), ShipCargo(CargoType.NUCLEAR))

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
			val partRef = hull[partIndex]

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
				val ammoState = AmmunitionPartState()
				ammoState.type = hull.preferredMunitions[partRef]
				state.put(ammoState)
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
				var defaultAssignments: List<PartRef<Part>>? = hull.defaultWeaponAssignments[partRef as PartRef<TargetingComputer>]

				if (defaultAssignments != null) {
					tcs.linkedWeapons = defaultAssignments.toMutableList()
				}

				state.put(tcs)
			}
		}

		return this
	}

	fun getMass(): Long {
		var mass = hull.getMass()
		
		//TODO add cargo
		
		return mass
	}

	fun getPartState(partRef: PartRef<out Part>): PartState {
		return partState[partRef.index]
	}

	fun getPartHealth(partRef: PartRef<out Part>): Int {
		return partHealth[partRef.index]
	}

	fun setPartHealth(partRef: PartRef<out Part>, health: Int) {
		if (health < 0 || health > partRef.part.maxHealth) {
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

	fun getCargoAmount(resource: Resource): Long {

		val shipCargo = cargo[resource]

		if (shipCargo != null) {

			val available = shipCargo.contents[resource]

			if (available != null) {
				return available
			}
		}

		return 0
	}
	
	fun getCargoAmount(munitionClass: MunitionHull): Int {

		val shipCargo = cargo[munitionClass.storageType]

		if (shipCargo != null) {

			var available = munitionCargo[munitionClass]

			if (available != null) {
				return available
			}
		}

		return 0
	}

	fun getUsedCargoVolume(resource: Resource): Long {

		val shipCargo = cargo[resource]

		if (shipCargo != null) {

			return shipCargo.usedVolume
		}

		return 0
	}
	
	fun getMaxCargoVolume(resource: Resource): Long {

		val shipCargo = cargo[resource]

		if (shipCargo != null) {

			return shipCargo.maxVolume
		}

		return 0
	}
	
	fun getUsedCargoVolume(type: CargoType): Long {

		val shipCargo = cargo[type.resources[0]]

		if (shipCargo != null) {

			return shipCargo.usedVolume
		}

		return 0
	}

	fun getMaxCargoVolume(type: CargoType): Long {

		val shipCargo = cargo[type.resources[0]]

		if (shipCargo != null) {

			return shipCargo.maxVolume
		}

		return 0
	}

	fun getUsedCargoMass(resource: Resource): Long {

		val shipCargo = cargo[resource]

		if (shipCargo != null) {

			val amount = shipCargo.contents[resource]

			if (amount != null) {
				return amount
			}
		}

		return 0
	}

	fun getUsedCargoMass(type: CargoType): Long {

		val shipCargo = cargo[type.resources[0]]

		if (shipCargo != null) {

			return shipCargo.contents.values.sum()
		}

		return 0
	}

	fun addCargo(resource: Resource, amount: Long): Boolean {

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

	fun addCargo(munitionClass: MunitionHull, amount: Int): Boolean {

		val shipCargo = cargo[munitionClass.storageType]

		if (shipCargo != null) {

			val volumeToBeStored = amount * munitionClass.getVolume()

			if (shipCargo.usedVolume + volumeToBeStored > shipCargo.maxVolume) {
				return false;
			}
			
			shipCargo.usedVolume += volumeToBeStored
			val storedMass = shipCargo.contents[munitionClass.storageType]!!
			shipCargo.contents[munitionClass.storageType] = storedMass + munitionClass.getMass() * amount
			
			var stored = munitionCargo[munitionClass]

			if (stored == null) {
				stored = 0
			}
			
			munitionCargo[munitionClass] = stored + amount

			return true
		}

		return false
	}

	fun retrieveCargo(resource: Resource, amount: Long): Long {

		if (resource.specificVolume == 0) {
			throw InvalidParameterException()
		}

		val shipCargo = cargo[resource]

		if (shipCargo != null) {

			val available = shipCargo.contents[resource]

			if (available == null || available == 0L) {
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

	fun retrieveCargo(munitionClass: MunitionHull, amount: Int): Int {

		var available = munitionCargo[munitionClass]

		if (available == null || available == 0) {
			return 0
		}

		var retrievedAmount = amount

		if (available < amount) {
			retrievedAmount = available
		}

		munitionCargo[munitionClass] = available - retrievedAmount
		
		val shipCargo = cargo[munitionClass.storageType]!!
		val storedMass = shipCargo.contents[munitionClass.storageType]!!
		
		shipCargo.contents[munitionClass.storageType] = storedMass - retrievedAmount * munitionClass.getMass()
		shipCargo.usedVolume -= retrievedAmount * munitionClass.getVolume()

		return retrievedAmount
	}
}

data class ShipCargo(val type: CargoType) {
	var maxVolume = 0L
	var usedVolume = 0L
	var contents: MutableMap<Resource, Long> = LinkedHashMap()

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

data class AmmunitionPartState(var type: MunitionHull? = null,
															 var amount: Int = 0
)

data class ReloadablePartState(var loaded: Boolean = false,
															 var reloadPowerRemaining: Long = 0
)

data class TargetingComputerState(var target: Entity? = null,
																	var lockCompletionAt: Long = 0,
																	var linkedWeapons: MutableList<PartRef<Part>> = ArrayList()
)

