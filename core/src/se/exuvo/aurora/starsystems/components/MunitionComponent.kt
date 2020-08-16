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
	var damage: Long = 0 // J
	lateinit var damagePattern: DamagePattern
	
	fun set(targetEntityID: Int,
					damage: Long,
					damagePattern: DamagePattern
	) {
		this.targetEntityID = targetEntityID
		this.damage = damage
		this.damagePattern = damagePattern
	}
	
	override fun reset(): Unit {}
	override fun copy(tc: RailgunShotComponent) {
		tc.set(targetEntityID, damage, damagePattern)
	}
}

class MissileComponent() : PooledComponent(), CloneableComponent<MissileComponent> {
	lateinit var hull: AdvancedMunitionHull
	var targetEntityID: Int = -1
	var damage: Int = 0
	lateinit var damagePattern: DamagePattern

	fun set(munitionHull: AdvancedMunitionHull,
					targetEntityID: Int
	) {
		this.hull = munitionHull
		this.targetEntityID = targetEntityID
		
		//TODO get from parts
		damagePattern = DamagePattern.EXPLOSIVE
		damage = 1
	}
	
	override fun reset(): Unit {}
	override fun copy(tc: MissileComponent) {
		val newComponent = tc.targetEntityID == -1
		val sameHull = !newComponent && tc.hull == hull
		
		if (!sameHull) {
			tc.hull = hull
			tc.damagePattern = damagePattern
		}
		
		tc.targetEntityID = targetEntityID
		tc.damage = damage
	}
}
