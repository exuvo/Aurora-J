package se.exuvo.aurora.planetarysystems.components

import se.exuvo.aurora.galactic.CargoType
import se.exuvo.aurora.galactic.ContainerPart
import se.exuvo.aurora.galactic.Part
import se.exuvo.aurora.galactic.Resource
import java.security.InvalidParameterException
import java.util.ArrayList

import kotlin.Suppress
import se.exuvo.aurora.galactic.MunitionHull
import com.artemis.Component
import se.exuvo.aurora.galactic.PartRef
import se.exuvo.aurora.galactic.PoweringPart
import se.exuvo.aurora.galactic.PoweredPart
import se.exuvo.aurora.galactic.ChargedPart
import se.exuvo.aurora.galactic.PassiveSensor
import se.exuvo.aurora.galactic.FueledPart
import com.artemis.PooledComponent
import se.exuvo.aurora.galactic.DamagePattern
import se.exuvo.aurora.galactic.AdvancedMunitionHull

class LaserShotComponent() : PooledComponent() {
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
}

class RailgunShotComponent() : PooledComponent() {
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
}

class MissileComponent() : PooledComponent() {
	lateinit var hull: AdvancedMunitionHull
	var targetEntityID: Int = -1
	var health: Short = -1
	var damage: Int = 0
	lateinit var damagePattern: DamagePattern
	lateinit var armor: Array<ShortArray> // [layer][armor column] = hp
	lateinit var partEnabled: Array<Boolean>
	lateinit var partState: Array<PartState>
	var mass: Long = 0

	fun set(munitionHull: AdvancedMunitionHull,
					targetEntityID: Int
	) {
		this.hull = munitionHull
		this.targetEntityID = targetEntityID
		
		//TODO get from parts
		damagePattern = DamagePattern.EXPLOSIVE
		damage = 1
		
		armor = Array<ShortArray>(hull.armorLayers, { ShortArray(hull.getSurfaceArea() / 1000000, { hull.armorBlockHP }) }) // 1 armor block per m2
		partEnabled = Array<Boolean>(hull.getParts().size, { true })
		partState = Array<PartState>(hull.getParts().size, { PartState() })
		
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
			
			if (partRef.part is FueledPart) {
				state.put(FueledPartState())
			}
		}
	}
	
	override fun reset(): Unit {}
	
	fun getPartState(partRef: PartRef<out Part>): PartState {
		return partState[partRef.index]
	}
	
	fun isPartEnabled(partRef: PartRef<out Part>): Boolean {
		return partEnabled[partRef.index]
	}
	
	fun setPartEnabled(partRef: PartRef<out Part>, enabled: Boolean) {
		partEnabled[partRef.index] = enabled
	}
}
