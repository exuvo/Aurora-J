package se.exuvo.aurora.starsystems.components

import com.artemis.Component
import se.exuvo.aurora.galactic.CargoType
import se.exuvo.aurora.galactic.ContainerPart
import se.exuvo.aurora.galactic.Part
import se.exuvo.aurora.galactic.Resource
import se.exuvo.aurora.galactic.ShipHull
import java.security.InvalidParameterException
import java.lang.IllegalArgumentException
import se.exuvo.aurora.galactic.PoweredPart
import se.exuvo.aurora.galactic.ChargedPart
import se.exuvo.aurora.galactic.AmmunitionPart
import se.exuvo.aurora.galactic.FueledPart
import se.exuvo.aurora.galactic.PoweringPart
import kotlin.reflect.KClass
import se.exuvo.aurora.galactic.PassiveSensor
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.galactic.PartRef
import kotlin.Suppress
import se.exuvo.aurora.galactic.MunitionHull
import se.exuvo.aurora.utils.forEachFast
import com.artemis.utils.Bag
import uk.co.omegaprime.btreemap.LongObjectBTreeMap
import java.util.PriorityQueue

class ShipComponent() : Component(), CloneableComponent<ShipComponent> {
	lateinit var hull: ShipHull
	var commissionTime: Long = -1
	lateinit var armor: Array<ByteArray> // [layer][armor column] = hp
	var totalPartHP: Int = 0
	var damageablePartsMaxVolume = 0L
	val damageableParts = LongObjectBTreeMap.create<Bag<PartRef<Part>>>()
	lateinit var partHP: ByteArray
	lateinit var partEnabled: BooleanArray
	lateinit var partStates: Array<PartStates>
	lateinit var cargo: Map<Resource, ShipCargo>
	lateinit var munitionCargo: MutableMap<MunitionHull, Int>
	var cargoChanged = false
	var heat: Long = 0
	
	var mass = -1L
		get() {
			if (field == -1L) { field = calculateMass() }
			return field
		}
	var cargoMass = -1L
		get() {
			if (field == -1L) { field = calculateCargoMass() }
			return field
		}
	
	fun set(hull: ShipHull,
					comissionTime: Long
	): ShipComponent {
		this.hull = hull
		this.commissionTime = comissionTime

		armor = Array<ByteArray>(hull.armorLayers, { layer -> ByteArray(hull.getArmorWidth(), { hull.armorBlockHP[layer] }) }) // 1 armor block per m2
		partHP = ByteArray(hull.getParts().size, { hull[it].part.maxHealth })
		totalPartHP = partHP.size * 128 + partHP.sum()
		partEnabled = BooleanArray(hull.getParts().size, { true })
		partStates = Array<PartStates>(hull.getParts().size, { PartStates() })
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

		partStates.forEachIndexed { partIndex, state ->
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

				@Suppress("UNCHECKED_CAST")
				var defaultAssignments: List<PartRef<Part>>? = hull.defaultWeaponAssignments[partRef as PartRef<TargetingComputer>]
				
				var tcs: TargetingComputerState

				if (defaultAssignments != null) {
					
					tcs = TargetingComputerState(this, defaultAssignments.size)
					
					for (weaponRef in defaultAssignments) {
						tcs.linkedWeapons.add(weaponRef)
						
						if (weaponRef.part is AmmunitionPart) {
							tcs.reloadingWeapons.add(weaponRef)
						}
						
						if (weaponRef.part is ChargedPart) {
							tcs.chargingWeapons.add(weaponRef)
						}
						
						val weaponState = WeaponPartState()
						weaponState.targetingComputer = partRef
						
						val weaponPartState = getPartState(weaponRef)
						weaponPartState.put(weaponState)
					}
					
				} else {
					tcs = TargetingComputerState(this)
				}

				state.put(tcs)
			}
		}

		return this
	}
	
	override fun copy(tc: ShipComponent) {
		val newComponent = tc.commissionTime == -1L
		val sameHull = !newComponent && tc.hull == hull
		
		if (!sameHull) {
			tc.hull = hull
		}
		
		tc.commissionTime = commissionTime
		tc.totalPartHP = totalPartHP
		tc.damageablePartsMaxVolume = damageablePartsMaxVolume
		tc.cargoChanged = cargoChanged
		tc.heat = heat
		
		if (!sameHull) {
			tc.armor = Array<ByteArray>(armor.size, {layer -> ByteArray(hull.getArmorWidth(), { column -> armor[layer][column] }) })
			tc.partHP = ByteArray(partHP.size, { partHP[it] })
			tc.partEnabled = BooleanArray(partEnabled.size, { partEnabled[it] })
			tc.partStates = Array<PartStates>(partStates.size, { partIndex ->
				val partRef = hull[partIndex]
				val states = partStates[partIndex]
				val tcStates = PartStates()
				
				if (partRef.part is PoweringPart) {
					tcStates.put(PoweringPartState(states[PoweringPartState::class]))
				}
				
				if (partRef.part is PoweredPart) {
					tcStates.put(PoweredPartState(states[PoweredPartState::class]))
				}
				
				if (partRef.part is ChargedPart) {
					tcStates.put(ChargedPartState(states[ChargedPartState::class]))
				}
				
				if (partRef.part is PassiveSensor) {
					tcStates.put(PassiveSensorState(states[PassiveSensorState::class]))
				}
				
				if (partRef.part is AmmunitionPart) {
					tcStates.put(AmmunitionPartState(states[AmmunitionPartState::class]))
				}
				
				if (partRef.part is FueledPart) {
					tcStates.put(FueledPartState(states[FueledPartState::class]))
				}
				
				if (partRef.part is TargetingComputer) {
					tcStates.put(TargetingComputerState(this, states[TargetingComputerState::class]))
				}
				
				tcStates
			})
			
			if (cargo == emptyMap<Resource, ShipCargo>()) {
				tc.cargo = emptyMap()
			} else {
				val shipCargos = listOf(ShipCargo(CargoType.NORMAL), ShipCargo(CargoType.LIFE_SUPPORT), ShipCargo(CargoType.FUEL), ShipCargo(CargoType.AMMUNITION), ShipCargo(CargoType.NUCLEAR))
				val mutableCargo = LinkedHashMap<Resource, ShipCargo>()
				
				shipCargos.forEachFast{ tcShipCargo ->
					tcShipCargo.type.resources.forEachFast{ resource ->
						val cargo = cargo[resource]!!
						tcShipCargo.maxVolume = cargo.maxVolume
						tcShipCargo.usedVolume = cargo.usedVolume
						tcShipCargo.contents[resource] = cargo.contents[resource]!!
						mutableCargo[resource] = tcShipCargo
					}
				}
				
				tc.cargo = mutableCargo
			}
			
			tc.munitionCargo = LinkedHashMap()
			tc.munitionCargo.putAll(munitionCargo)
		} else {
			armor.forEachIndexed { layerIndex, layer ->
				layer.forEachIndexed { columnIndex, hp ->
					tc.armor[layerIndex][columnIndex] = hp
				}
			}
			partHP.forEachIndexed { index, hp ->
				tc.partHP[index] = hp
			}
			partEnabled.forEachIndexed { index, enabled ->
				tc.partEnabled[index] = enabled
			}
			partStates.forEachIndexed { index, partStates ->
				val tcPartState = tc.partStates[index]
				partStates.states.forEach { (type, state) ->
					when(type) {
						FueledPartState::class -> {
							state as FueledPartState
							val tcState = tcPartState[type] as FueledPartState
							tcState.fuelEnergyRemaining = state.fuelEnergyRemaining
							tcState.totalFuelEnergyRemaining = state.totalFuelEnergyRemaining
						}
						PoweringPartState::class -> {
							state as PoweringPartState
							val tcState = tcPartState[type] as PoweringPartState
							tcState.availiablePower = state.availiablePower
							tcState.producedPower = state.producedPower
						}
						PoweredPartState::class -> {
							state as PoweredPartState
							val tcState = tcPartState[type] as PoweredPartState
							tcState.requestedPower = state.requestedPower
							tcState.givenPower = state.givenPower
						}
						PassiveSensorState::class -> {
							state as PassiveSensorState
							val tcState = tcPartState[type] as PassiveSensorState
							tcState.lastScan = state.lastScan
						}
						ChargedPartState::class -> {
							state as ChargedPartState
							val tcState = tcPartState[type] as ChargedPartState
							tcState.charge = state.charge
							tcState.expectedFullAt = state.expectedFullAt
						}
						AmmunitionPartState::class -> {
							state as AmmunitionPartState
							val tcState = tcPartState[type] as AmmunitionPartState
							tcState.type = state.type
							tcState.amount = state.amount
							tcState.reloadedAt = state.reloadedAt
						}
						TargetingComputerState::class -> {
							state as TargetingComputerState
							val tcState = tcPartState[type] as TargetingComputerState
							tcState.target = state.target
							tcState.lockCompletionAt = state.lockCompletionAt
							
							if (tcState.linkedWeapons.hashCode() != state.linkedWeapons.hashCode()) {
								tcState.linkedWeapons.clear()
								tcState.linkedWeapons.addAll(state.linkedWeapons)
							}
							
							if (tcState.disabledWeapons.hashCode() != state.disabledWeapons.hashCode()) {
								tcState.disabledWeapons.clear()
								tcState.disabledWeapons.addAll(state.disabledWeapons)
							}
							
							if (tcState.reloadingWeapons.hashCode() != state.reloadingWeapons.hashCode()) {
								tcState.reloadingWeapons.clear()
								tcState.reloadingWeapons.addAll(state.reloadingWeapons)
							}
							
							if (tcState.chargingWeapons.hashCode() != state.chargingWeapons.hashCode()) {
								tcState.chargingWeapons.clear()
								tcState.chargingWeapons.addAll(state.chargingWeapons)
							}
							
							if (tcState.readyWeapons.hashCode() != state.readyWeapons.hashCode()) {
								tcState.readyWeapons.clear()
								tcState.readyWeapons.addAll(state.readyWeapons)
							}
						}
						WeaponPartState::class -> {
							state as WeaponPartState
							val tcState = tcPartState[type] as WeaponPartState
							tcState.targetingComputer = state.targetingComputer
						}
					}
				}
			}
			
			if (cargo != emptyMap<Resource, ShipCargo>() && cargo.hashCode() != tc.cargo.hashCode()) {
				cargo.forEach { resource, shipCargo ->
					val tcShipCargo = tc.cargo[resource]!!
					tcShipCargo.maxVolume = shipCargo.maxVolume
					tcShipCargo.usedVolume = shipCargo.usedVolume
					tcShipCargo.contents[resource] = shipCargo.contents[resource]!!
				}
			}
			
			if (munitionCargo.hashCode() != tc.munitionCargo.hashCode()) {
				tc.munitionCargo.clear()
				tc.munitionCargo.putAll(munitionCargo)
			}
		}
	}

	fun calculateMass(): Long = hull.emptyMass + cargoMass
	
	fun calculateCargoMass(): Long {
		var mass = 0L
		
		for((resource, shipCargo) in cargo) {
			mass += shipCargo.contents[resource]!!
		}
		
		return mass
	}
	
	fun shieldHP(): Long {
		
		var shieldHP = 0L
		
		hull.shields.forEachFast { partRef ->
			if (isPartEnabled(partRef)) {
				shieldHP += partStates[partRef.index][ChargedPartState::class].charge
			}
		}
		
		return shieldHP
	}

	fun getPartState(partRef: PartRef<out Part>): PartStates {
		return partStates[partRef.index]
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
		mass = -1
		cargoMass = -1
	}
	
	fun resetLazyCache() {
		mass = -1
		cargoMass = -1
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
class PartStates {
	val states = HashMap<KClass<*>, Any>() //TODO to fixed array with static index

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
) {
	constructor(o: FueledPartState): this(o.fuelEnergyRemaining, o.totalFuelEnergyRemaining)
}

data class PoweringPartState(var availiablePower: Long = 0,
														 var producedPower: Long = 0
) {
	constructor(o: PoweringPartState) : this(o.availiablePower, o.producedPower)
}

data class PoweredPartState(var requestedPower: Long = 0,
														var givenPower: Long = 0
) {
	constructor(o: PoweredPartState) : this(o.requestedPower, o.givenPower)
}

data class PassiveSensorState(var lastScan: Long = 0
) {
	constructor(o: PassiveSensorState) : this(o.lastScan)
}

data class ChargedPartState(var charge: Long = 0,
														var expectedFullAt: Long = 0
) {
	constructor(o: ChargedPartState) : this(o.charge, o.expectedFullAt)
}

data class AmmunitionPartState(var type: MunitionHull? = null,
															 var amount: Int = 0,
															 var reloadedAt: Long = 0 // time remaining when part is disabled
) {
	constructor(o: AmmunitionPartState) : this(o.type, o.amount, o.reloadedAt)
}

data class TargetingComputerState(var target: EntityReference? = null,
																	var lockCompletionAt: Long = 0,
																	var linkedWeapons: Bag<PartRef<Part>> = Bag(1),
																	var readyWeapons: Bag<PartRef<Part>> = Bag(1),
																	var disabledWeapons: Bag<PartRef<Part>> = Bag(1),
																	var reloadingWeapons: PriorityQueue<PartRef<Part>>,
																	var chargingWeapons: PriorityQueue<PartRef<Part>>
) {
	constructor(ship: ShipComponent) : this (
		reloadingWeapons = PriorityQueue(AmmunitionReladedAtComparator(ship)),
		chargingWeapons = PriorityQueue(ChargedExpectedFullAtComparator(ship))
	)
	constructor(ship: ShipComponent, size: Int) : this (
		reloadingWeapons = PriorityQueue(AmmunitionReladedAtComparator(ship)),
		chargingWeapons = PriorityQueue(ChargedExpectedFullAtComparator(ship)),
		linkedWeapons = Bag(size),
		disabledWeapons = Bag(size),
		readyWeapons = Bag(size)
	)
	constructor(ship: ShipComponent, o: TargetingComputerState) : this (
			reloadingWeapons = PriorityQueue(AmmunitionReladedAtComparator(ship)),
			chargingWeapons = PriorityQueue(ChargedExpectedFullAtComparator(ship)),
			linkedWeapons = Bag(o.linkedWeapons.size()),
			disabledWeapons = Bag(o.disabledWeapons.size()),
			readyWeapons = Bag(o.readyWeapons.size())
	) {
		reloadingWeapons.addAll(o.reloadingWeapons)
		chargingWeapons.addAll(o.chargingWeapons)
		linkedWeapons.addAll(o.linkedWeapons)
		disabledWeapons.addAll(o.disabledWeapons)
		readyWeapons.addAll(o.readyWeapons)
	}
}

class AmmunitionReladedAtComparator(val ship: ShipComponent): Comparator<PartRef<Part>> {
	override fun compare(a: PartRef<Part>, b: PartRef<Part>): Int {
		val reloadedAtA = ship.getPartState(a)[AmmunitionPartState::class].reloadedAt
		val reloadedAtB = ship.getPartState(b)[AmmunitionPartState::class].reloadedAt
		
		return reloadedAtA.compareTo(reloadedAtB)
	}
}

class ChargedExpectedFullAtComparator(val ship: ShipComponent): Comparator<PartRef<Part>> {
	override fun compare(a: PartRef<Part>, b: PartRef<Part>): Int {
		val reloadedAtA = ship.getPartState(a)[ChargedPartState::class].expectedFullAt
		val reloadedAtB = ship.getPartState(b)[ChargedPartState::class].expectedFullAt
		
		return reloadedAtA.compareTo(reloadedAtB)
	}
}

data class WeaponPartState(var targetingComputer: PartRef<Part>? = null)
{
	constructor(o: WeaponPartState) : this(o.targetingComputer)
}