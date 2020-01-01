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
import se.exuvo.aurora.galactic.FueledPart
import se.exuvo.aurora.galactic.PoweringPart
import kotlin.reflect.KClass
import se.exuvo.aurora.galactic.PassiveSensor
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.galactic.PartRef
import kotlin.Suppress
import se.exuvo.aurora.galactic.MunitionHull
import se.exuvo.aurora.utils.forEachFast
import com.artemis.Entity

class ShipComponent() : Component() {
	lateinit var hull: ShipHull
	var constructionTime: Long = -1
	var commissionDay: Int? = null
	lateinit var armor: Array<ShortArray> // [layer][armor column] = hp
	lateinit var partHealth: ByteArray
	lateinit var partEnabled: BooleanArray
	lateinit var partState: Array<PartState>
	lateinit var cargo: Map<Resource, ShipCargo>
	lateinit var munitionCargo: MutableMap<MunitionHull, Int>
	var cargoChanged = true
	var heat: Long = 0

	fun set(hull: ShipHull,
					constructionTime: Long
	): ShipComponent {
		this.hull = hull
		this.constructionTime = constructionTime

		armor = Array<ShortArray>(hull.armorLayers, { ShortArray(hull.getSurfaceArea() / 1000000, { hull.armorBlockHP }) }) // 1 armor block per m2
		partHealth = ByteArray(hull.getParts().size, { hull[it].part.maxHealth })
		partEnabled = BooleanArray(hull.getParts().size, { true })
		partState = Array<PartState>(hull.getParts().size, { PartState() })
		cargo = emptyMap()
		munitionCargo = LinkedHashMap()
		
		var containerPartRefs: List<PartRef<ContainerPart>> = hull[ContainerPart::class]

		if (containerPartRefs.isNotEmpty()) {

			val shipCargos = listOf(ShipCargo(CargoType.NORMAL), ShipCargo(CargoType.LIFE_SUPPORT), ShipCargo(CargoType.FUEL), ShipCargo(CargoType.AMMUNITION), ShipCargo(CargoType.NUCLEAR))

			containerPartRefs.forEachFast{ containerRef ->
				for (cargo in shipCargos) {
					if (cargo.type.equals(containerRef.part.cargoType)) {
						cargo.maxVolume += containerRef.part.capacity
						break
					}
				}
			}

			val mutableCargo = LinkedHashMap<Resource, ShipCargo>()

			shipCargos.forEachFast{ cargo ->
				cargo.type.resources.forEachFast{ resource ->
					mutableCargo[resource] = cargo
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
				ammoState.type = hull.preferredPartMunitions[partRef]
				state.put(ammoState)
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
		var mass = hull.getEmptyMass()
		
		mass += getCargoMass()
		
		return mass
	}
	
	fun getCargoMass(): Long {
		var mass = 0L
		
		for((resource, shipCargo) in cargo) {
			mass += shipCargo.contents[resource]!!
		}
		
		return mass
	}

	fun getPartState(partRef: PartRef<out Part>): PartState {
		return partState[partRef.index]
	}

	fun getPartHealth(partRef: PartRef<out Part>): Byte {
		return partHealth[partRef.index]
	}

	fun setPartHealth(partRef: PartRef<out Part>, health: Byte) {
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
	
	fun getCargoAmount(munitionHull: MunitionHull): Int {

		val shipCargo = cargo[munitionHull.storageType]

		if (shipCargo != null) {

			var available = munitionCargo[munitionHull]

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

	fun addCargo(munitionHull: MunitionHull, amount: Int): Boolean {

		val shipCargo = cargo[munitionHull.storageType]

		if (shipCargo != null) {

			val volumeToBeStored = amount * munitionHull.getVolume()

			if (shipCargo.usedVolume + volumeToBeStored > shipCargo.maxVolume) {
				return false;
			}
			
			shipCargo.usedVolume += volumeToBeStored
			val storedMass = shipCargo.contents[munitionHull.storageType]!!
			shipCargo.contents[munitionHull.storageType] = storedMass + munitionHull.getLoadedMass() * amount
			
			var stored = munitionCargo[munitionHull]

			if (stored == null) {
				stored = 0
			}
			
			munitionCargo[munitionHull] = stored + amount

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

	fun retrieveCargo(munitionHull: MunitionHull, amount: Int): Int {

		var available = munitionCargo[munitionHull]

		if (available == null || available == 0) {
			return 0
		}

		var retrievedAmount = amount

		if (available < amount) {
			retrievedAmount = available
		}

		munitionCargo[munitionHull] = available - retrievedAmount
		
		val shipCargo = cargo[munitionHull.storageType]!!
		val storedMass = shipCargo.contents[munitionHull.storageType]!!
		
		shipCargo.contents[munitionHull.storageType] = storedMass - retrievedAmount * munitionHull.getLoadedMass()
		shipCargo.usedVolume -= retrievedAmount * munitionHull.getVolume()

		return retrievedAmount
	}
}

data class ShipCargo(val type: CargoType) {
	var maxVolume = 0L
	var usedVolume = 0L
	var contents: MutableMap<Resource, Long> = LinkedHashMap()

	init {
		type.resources.forEachFast{ resource ->
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
															 var amount: Int = 0,
															 var reloadPowerRemaining: Long = 0
)

data class TargetingComputerState(var target: EntityReference? = null,
																	var lockCompletionAt: Long = 0,
																	var linkedWeapons: MutableList<PartRef<Part>> = ArrayList()
)

