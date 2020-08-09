package se.exuvo.aurora.starsystems.components

import se.exuvo.aurora.galactic.Part

import se.exuvo.aurora.galactic.PartRef
import se.exuvo.aurora.galactic.PoweringPart
import se.exuvo.aurora.galactic.PoweredPart
import se.exuvo.aurora.galactic.ChargedPart
import se.exuvo.aurora.galactic.PassiveSensor
import se.exuvo.aurora.galactic.FueledPart
import com.artemis.PooledComponent
import se.exuvo.aurora.galactic.DamagePattern
import se.exuvo.aurora.galactic.AdvancedMunitionHull

class LaserShotComponent() : PooledComponent(), CloneableComponent<LaserShotComponent> {
	var targetEntityID: Int = -1
	var damage: Long = 0 // J
	var beamArea: Long = 0 // cm²
	
	fun set(targetEntityID: Int,
					damage: Long,
					beamArea: Double // m²
	) {
		this.targetEntityID = targetEntityID
		this.damage = damage
		this.beamArea = (1000000 * beamArea).toLong()
	}
	
	override fun reset(): Unit {}
	override fun copy(tc: LaserShotComponent) {
		tc.targetEntityID = targetEntityID
		tc.damage = damage
		tc.beamArea = beamArea
	}
}

class RailgunShotComponent() : PooledComponent(), CloneableComponent<RailgunShotComponent> {
	var targetEntityID: Int = -1
	var health: Short = -1
	var damage: Long = 0 // J
	lateinit var damagePattern: DamagePattern
	
	fun set(targetEntityID: Int,
					damage: Long,
					damagePattern: DamagePattern,
					health: Short = -1
	) {
		this.targetEntityID = targetEntityID
		this.damage = damage
		this.damagePattern = damagePattern
		this.health = health
	}
	
	override fun reset(): Unit {}
	override fun copy(tc: RailgunShotComponent) {
		tc.set(targetEntityID, damage, damagePattern, health)
	}
}

class MissileComponent() : PooledComponent(), CloneableComponent<MissileComponent> {
	lateinit var hull: AdvancedMunitionHull
	var targetEntityID: Int = -1
	var health: Short = -1
	var damage: Int = 0
	lateinit var damagePattern: DamagePattern
	lateinit var armor: Array<ByteArray> // [layer][armor column] = hp
	lateinit var partEnabled: BooleanArray
	lateinit var partStates: Array<PartStates>
	var mass: Long = 0

	fun set(munitionHull: AdvancedMunitionHull,
					targetEntityID: Int
	) {
		this.hull = munitionHull
		this.targetEntityID = targetEntityID
		
		//TODO get from parts
		damagePattern = DamagePattern.EXPLOSIVE
		damage = 1
		
		//TODO change to ByteArray like ships
		armor = Array<ByteArray>(hull.armorLayers, { ByteArray(hull.getSurfaceArea() / 1000000, { hull.armorBlockHP }) }) // 1 armor block per m2
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
	}
	
	override fun reset(): Unit {}
	override fun copy(tc: MissileComponent) {
		val newComponent = tc.targetEntityID == -1
		val sameHull = !newComponent && tc.hull == hull
		
		if (!sameHull) {
			tc.hull = hull
		}
		
		tc.targetEntityID = targetEntityID
		tc.health = health
		tc.damage = damage
		tc.mass = mass
		
		
		if (!sameHull) {
			tc.damagePattern = damagePattern
			tc.armor = Array<ByteArray>(armor.size, {layer -> ByteArray(armor[0].size, { column -> armor[layer][column] }) })
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
				
				if (partRef.part is FueledPart) {
					tcStates.put(FueledPartState(states[FueledPartState::class]))
				}
				
				tcStates
			})
		} else {
			armor.forEachIndexed { layerIndex, layer ->
				layer.forEachIndexed { columnIndex, hp ->
					tc.armor[layerIndex][columnIndex] = hp
				}
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
					}
				}
			}
		}
	}
	
	fun getPartState(partRef: PartRef<out Part>): PartStates {
		return partStates[partRef.index]
	}
	
	fun isPartEnabled(partRef: PartRef<out Part>): Boolean {
		return partEnabled[partRef.index]
	}
	
	fun setPartEnabled(partRef: PartRef<out Part>, enabled: Boolean) {
		partEnabled[partRef.index] = enabled
	}
}
