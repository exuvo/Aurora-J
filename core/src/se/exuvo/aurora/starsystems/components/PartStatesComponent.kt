package se.exuvo.aurora.starsystems.components

import com.artemis.Component
import com.artemis.PooledComponent
import com.artemis.utils.Bag
import se.exuvo.aurora.galactic.AdvancedMunitionHull
import se.exuvo.aurora.galactic.AmmunitionPart
import se.exuvo.aurora.galactic.ChargedPart
import se.exuvo.aurora.galactic.FueledPart
import se.exuvo.aurora.galactic.MunitionHull
import se.exuvo.aurora.galactic.Part
import se.exuvo.aurora.galactic.PartRef
import se.exuvo.aurora.galactic.PassiveSensor
import se.exuvo.aurora.galactic.PoweredPart
import se.exuvo.aurora.galactic.PoweringPart
import se.exuvo.aurora.galactic.ShipHull
import se.exuvo.aurora.galactic.TargetingComputer
import java.util.*
import kotlin.collections.HashMap
import kotlin.reflect.KClass

class PartStatesComponent() : PooledComponent(), CloneableComponent<PartStatesComponent> {
	var hullHashcode = 0
	lateinit var partEnabled: BooleanArray
	lateinit var partStates: Array<PartStates>
	
	fun set(hull: ShipHull): PartStatesComponent {
		hullHashcode = hull.hashCode()
		partEnabled = BooleanArray(hull.getParts().size, { true })
		partStates = Array<PartStates>(hull.getParts().size, { PartStates() })
		
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
	
	fun set(hull: AdvancedMunitionHull): PartStatesComponent {
		hullHashcode = hull.hashCode()
		partEnabled = BooleanArray(hull.getParts().size, { true })
		partStates = Array<PartStates>(hull.getParts().size, { PartStates() })
		
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
			
			if (partRef.part is FueledPart) {
				state.put(FueledPartState())
			}
		}
		
		return this
	}
	
	override fun reset() {}
	
	override fun copy(tc: PartStatesComponent) {
		if (tc.hullHashcode != hullHashcode) {
			tc.hullHashcode = hullHashcode
			tc.partEnabled = BooleanArray(partEnabled.size, { partEnabled[it] })
			tc.partStates = Array<PartStates>(partStates.size, { partIndex ->
				val states = partStates[partIndex]
				val tcStates = PartStates()
				
				for (partState in states.states.values) {
					when (partState) {
						is PoweringPartState -> tcStates.put(PoweringPartState(partState))
						is PoweredPartState -> tcStates.put(PoweredPartState(partState))
						is ChargedPartState -> tcStates.put(ChargedPartState(partState))
						is PassiveSensorState -> tcStates.put(PassiveSensorState(partState))
						is AmmunitionPartState -> tcStates.put(AmmunitionPartState(partState))
						is FueledPartState -> tcStates.put(FueledPartState(partState))
						is TargetingComputerState -> tcStates.put(TargetingComputerState(this, partState))
						is WeaponPartState -> tcStates.put(WeaponPartState(partState))
					}
				}
				
				tcStates
			})
			
		} else {
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
		}
	}
	
	operator fun get(partRef: PartRef<out Part>) = getPartState(partRef)
	
	fun getPartState(partRef: PartRef<out Part>): PartStates {
		return partStates[partRef.index]
	}
	
	fun isPartEnabled(partRef: PartRef<out Part>): Boolean {
		return partEnabled!![partRef.index]
	}
	
	fun setPartEnabled(partRef: PartRef<out Part>, enabled: Boolean) {
		partEnabled!![partRef.index] = enabled
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

data class WeaponPartState(var targetingComputer: PartRef<Part>? = null)
{
	constructor(o: WeaponPartState) : this(o.targetingComputer)
}

data class TargetingComputerState(var target: EntityReference? = null,
																	var lockCompletionAt: Long = 0,
																	var linkedWeapons: Bag<PartRef<Part>> = Bag(1),
																	var readyWeapons: Bag<PartRef<Part>> = Bag(1),
																	var disabledWeapons: Bag<PartRef<Part>> = Bag(1),
																	var reloadingWeapons: PriorityQueue<PartRef<Part>>,
																	var chargingWeapons: PriorityQueue<PartRef<Part>>
) {
	constructor(partStates: PartStatesComponent) : this (
			reloadingWeapons = PriorityQueue(AmmunitionReladedAtComparator(partStates)),
			chargingWeapons = PriorityQueue(ChargedExpectedFullAtComparator(partStates))
	)
	constructor(partStates: PartStatesComponent, size: Int) : this (
			reloadingWeapons = PriorityQueue(AmmunitionReladedAtComparator(partStates)),
			chargingWeapons = PriorityQueue(ChargedExpectedFullAtComparator(partStates)),
			linkedWeapons = Bag(size),
			disabledWeapons = Bag(size),
			readyWeapons = Bag(size)
	)
	constructor(partStates: PartStatesComponent, o: TargetingComputerState) : this (
			reloadingWeapons = PriorityQueue(AmmunitionReladedAtComparator(partStates)),
			chargingWeapons = PriorityQueue(ChargedExpectedFullAtComparator(partStates)),
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

class AmmunitionReladedAtComparator(val partStates: PartStatesComponent): Comparator<PartRef<Part>> {
	override fun compare(a: PartRef<Part>, b: PartRef<Part>): Int {
		val reloadedAtA = partStates.getPartState(a)[AmmunitionPartState::class].reloadedAt
		val reloadedAtB = partStates.getPartState(b)[AmmunitionPartState::class].reloadedAt
		
		return reloadedAtA.compareTo(reloadedAtB)
	}
}

class ChargedExpectedFullAtComparator(val partStates: PartStatesComponent): Comparator<PartRef<Part>> {
	override fun compare(a: PartRef<Part>, b: PartRef<Part>): Int {
		val reloadedAtA = partStates.getPartState(a)[ChargedPartState::class].expectedFullAt
		val reloadedAtB = partStates.getPartState(b)[ChargedPartState::class].expectedFullAt
		
		return reloadedAtA.compareTo(reloadedAtB)
	}
}
