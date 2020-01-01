package se.exuvo.aurora.empires.components

import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.galactic.PartRef
import com.artemis.Component
import com.artemis.PooledComponent

class WeaponsComponent() : PooledComponent() {
	lateinit var targetingComputers: List<PartRef<TargetingComputer>>
	
	fun set(targetingComputers: List<PartRef<TargetingComputer>>): WeaponsComponent {
		this.targetingComputers = targetingComputers
		return this
	}
	
	override fun reset(): Unit {}
}
