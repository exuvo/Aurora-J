package se.exuvo.aurora.starsystems.components

import com.artemis.Component
import com.artemis.PooledComponent
import se.exuvo.aurora.galactic.CargoType
import se.exuvo.aurora.galactic.Resource
import se.exuvo.aurora.galactic.ShipHull
import se.exuvo.aurora.galactic.PoweredPart
import se.exuvo.aurora.galactic.ChargedPart
import se.exuvo.aurora.galactic.AmmunitionPart
import se.exuvo.aurora.galactic.FueledPart
import se.exuvo.aurora.galactic.PoweringPart
import se.exuvo.aurora.galactic.PassiveSensor
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.galactic.BeamWeapon
import se.exuvo.aurora.galactic.MissileLauncher
import se.exuvo.aurora.galactic.Railgun

class ShipComponent() : PooledComponent(), CloneableComponent<ShipComponent> {
	lateinit var hull: ShipHull
	var commissionTime: Long = -1
	var heat: Long = 0
	
	fun set(hull: ShipHull,
					comissionTime: Long
	): ShipComponent {
		this.hull = hull
		this.commissionTime = comissionTime

		return this
	}
	
	override fun copy(tc: ShipComponent) {
		val newComponent = tc.commissionTime == -1L
		val sameHull = !newComponent && tc.hull == hull
		
		if (!sameHull) {
			tc.hull = hull
		}
		
		tc.commissionTime = commissionTime
		tc.heat = heat
		
		if (!sameHull) {
		
		} else {
		
		}
	}
	
	override fun reset() {}
}



