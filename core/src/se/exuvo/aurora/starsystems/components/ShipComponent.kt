package se.exuvo.aurora.starsystems.components

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
import com.artemis.utils.Bag
import java.util.TreeMap
import uk.co.omegaprime.btreemap.LongObjectBTreeMap
import se.exuvo.aurora.utils.ResetableLazy
import se.exuvo.aurora.utils.resetDelegate

class ShipComponent() : Component() {
	lateinit var hull: ShipHull
	var comissionTime: Long = -1
	lateinit var armor: Array<ByteArray> // [layer][armor column] = hp
	var totalPartHP: Int = 0
	var damageablePartsMaxVolume = 0L
	val damageableParts = LongObjectBTreeMap.create<Bag<PartRef<Part>>>()
	lateinit var partHP: ByteArray
	lateinit var partEnabled: BooleanArray
	lateinit var partState: Array<PartState>
	lateinit var cargo: Map<Resource, ShipCargo>
	lateinit var munitionCargo: MutableMap<MunitionHull, Int>
	var cargoChanged = false
	var heat: Long = 0
	
	val mass by ResetableLazy (::calculateMass)
	val cargoMass by ResetableLazy (::calculateCargoMass)
	
	fun set(hull: ShipHull,
					comissionTime: Long
	): ShipComponent {
		this.hull = hull
		this.comissionTime = comissionTime

		armor = Array<ByteArray>(hull.armorLayers, { layer -> ByteArray(hull.getArmorWidth(), { hull.armorBlockHP[layer] }) }) // 1 armor block per m2
		partHP = ByteArray(hull.getParts().size, { hull[it].part.maxHealth })
		totalPartHP = partHP.size * 128 + partHP.sum()
		partEnabled = BooleanArray(hull.getParts().size, { true })
		partState = Array<PartState>(hull.getParts().size, { PartState() })
		cargo = emptyMap()
		munitionCargo = LinkedHashMap()
		
		hull.getPartRefs().forEachFast { partRef ->
			addDamageablePart(partRef)
		}
		
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

	fun calculateMass(): Long {
		var mass = hull.emptyMass
		
		mass += cargoMass
		
		return mass
	}
	
	fun calculateCargoMass(): Long {
		var mass = 0L
		
		for((resource, shipCargo) in cargo) {
			mass += shipCargo.contents[resource]!!
		}
		
		return mass
	}

	fun getPartState(partRef: PartRef<out Part>): PartState {
		return partState[partRef.index]
	}

	fun getPartHP(partRef: PartRef<out Part>): Int {
		return 128 + partHP[partRef.index]
	}

	@Suppress("NAME_SHADOWING")
	fun setPartHP(partRef: PartRef<Part>, health: Int, damageablePartsEntry: Map.Entry<Long, Bag<PartRef<Part>>>? = null) {
		if (health < 0 || health > (128 + partRef.part.maxHealth)) {
			throw IllegalArgumentException()
		}
		
		val oldHP = 128 + partHP[partRef.index]
		
		if (oldHP == 0 && health > 0) {
			
			addDamageablePart(partRef)
			
		} else if (oldHP > 0 && health == 0) {

			var damageablePartsEntry = damageablePartsEntry
			val volume = partRef.part.volume
			
			if (damageablePartsEntry == null) {
				damageablePartsEntry = getDamageablePartsEntry(volume)!!
			}
			
			damageableParts.remove(damageablePartsEntry.key)
			
			if (damageablePartsEntry.value.size() == 1) {
				
//				println("removing $volume, single")
			
				if (damageableParts.size == 0) {
					damageablePartsMaxVolume = 0
					
				} else if (volume == damageablePartsMaxVolume) {
					damageablePartsMaxVolume = damageableParts.lastKeyLong()
				}
				
			} else {
				
//				println("removing $volume, multi")
				
				damageablePartsEntry.value.remove(partRef)
				
				val newVolume = damageablePartsEntry.key - volume
				damageableParts.put(newVolume, damageablePartsEntry.value)
				
				if (damageablePartsMaxVolume >= newVolume) {
					damageablePartsMaxVolume = damageableParts.lastKeyLong()
				}
			}
		}

		totalPartHP += health - oldHP
		
		partHP[partRef.index] = (health - 128).toByte()
	}
	
	private fun getDamageablePartsEntry(volume: Long): Map.Entry<Long, Bag<PartRef<Part>>>? {
		if (damageableParts.size > 0) {
			var entry = damageableParts.ceilingEntry(volume)
			
			while(entry != null) {
				
//				println("get $volume, entry ${entry.key} = ${entry.value[0].part.volume} * ${entry.value.size()}")
				
				if (entry.value[0].part.volume == volume) {
					return entry
				}
				
				entry = damageableParts.higherEntry(entry.key)
			}
		}
		
		return null
	}
	
	private fun addDamageablePart(partRef: PartRef<Part>) {
		val part = partRef.part
		val volume = part.volume
		
		var list: Bag<PartRef<Part>>? = null
		
		if (damageableParts.size > 0) {
			val entry = getDamageablePartsEntry(volume)
			
			if (entry != null) {
				list = entry.value
				
				damageableParts.remove(entry.key)
				val newVolume = entry.key + volume
				damageableParts.put(newVolume, list)
				
				if (newVolume > damageablePartsMaxVolume) {
					damageablePartsMaxVolume = newVolume
				}
				
//				println("adding $volume, appending")
			}
		}
		
		if (list == null) {
			list = Bag<PartRef<Part>>(4)
			damageableParts.put(volume, list)
			
			if (volume > damageablePartsMaxVolume) {
				damageablePartsMaxVolume = volume
			}
			
//			println("adding $volume, new")
		}
		
		list.add(partRef)
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

			massChange()
			return true
		}

		return false
	}

	fun addCargo(munitionHull: MunitionHull, amount: Int): Boolean {

		val shipCargo = cargo[munitionHull.storageType]

		if (shipCargo != null) {

			val volumeToBeStored = amount * munitionHull.volume

			if (shipCargo.usedVolume + volumeToBeStored > shipCargo.maxVolume) {
				return false;
			}
			
			shipCargo.usedVolume += volumeToBeStored
			val storedMass = shipCargo.contents[munitionHull.storageType]!!
			shipCargo.contents[munitionHull.storageType] = storedMass + munitionHull.loadedMass * amount
			
			var stored = munitionCargo[munitionHull]

			if (stored == null) {
				stored = 0
			}
			
			munitionCargo[munitionHull] = stored + amount

			massChange()
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

			massChange()
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
		
		shipCargo.contents[munitionHull.storageType] = storedMass - retrievedAmount * munitionHull.loadedMass
		shipCargo.usedVolume -= retrievedAmount * munitionHull.volume

		massChange()
		return retrievedAmount
	}
	
	private fun massChange() {
		cargoChanged = true
		::mass.resetDelegate()
		::cargoMass.resetDelegate()
	}
	
	fun resetLazyCache() {
		::mass.resetDelegate()
		::cargoMass.resetDelegate()
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

