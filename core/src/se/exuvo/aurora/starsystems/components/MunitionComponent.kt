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
import se.exuvo.aurora.galactic.SimpleMunitionHull

class LaserShotComponent() : PooledComponent(), CloneableComponent<LaserShotComponent> {
	var targetEntityID: Int = -1
	var damage: Long = 0 // J per 1m² at hit distance
	var beamArea: Long = 0 // cm² total beam area at hit distance
	
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
	lateinit var hull: SimpleMunitionHull
	var targetEntityID: Int = -1
	
	fun set(targetEntityID: Int,
					hull: SimpleMunitionHull
	) {
		this.targetEntityID = targetEntityID
		this.hull = hull
	}
	
	override fun reset(): Unit {}
	override fun copy(tc: RailgunShotComponent) {
		val newComponent = tc.targetEntityID == -1
		val sameHull = !newComponent && tc.hull == hull
		
		if (!sameHull) {
			tc.hull = hull
		}
		
		tc.targetEntityID = targetEntityID
	}
}

class MissileComponent() : PooledComponent(), CloneableComponent<MissileComponent> {
	lateinit var hull: AdvancedMunitionHull
	var targetEntityID: Int = -1

	fun set(munitionHull: AdvancedMunitionHull,
					targetEntityID: Int
	) {
		this.hull = munitionHull
		this.targetEntityID = targetEntityID
	}
	
	override fun reset(): Unit {}
	override fun copy(tc: MissileComponent) {
		val newComponent = tc.targetEntityID == -1
		val sameHull = !newComponent && tc.hull == hull
		
		if (!sameHull) {
			tc.hull = hull
		}
		
		tc.targetEntityID = targetEntityID
	}
}
