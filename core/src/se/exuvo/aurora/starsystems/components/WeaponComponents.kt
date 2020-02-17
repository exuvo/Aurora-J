package se.exuvo.aurora.empires.components

import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.galactic.PartRef
import com.artemis.Component
import com.artemis.PooledComponent

class IdleTargetingComputersComponent() : PooledComponent() {
	lateinit var targetingComputers: MutableList<PartRef<TargetingComputer>>
	
	fun set(targetingComputers: MutableList<PartRef<TargetingComputer>>): IdleTargetingComputersComponent {
		this.targetingComputers = targetingComputers
		return this
	}
	
	override fun reset(): Unit {}
}

class ActiveTargetingComputersComponent() : PooledComponent() {
	lateinit var targetingComputers: MutableList<PartRef<TargetingComputer>>
	
	fun set(targetingComputers: MutableList<PartRef<TargetingComputer>>): ActiveTargetingComputersComponent {
		this.targetingComputers = targetingComputers
		return this
	}
	
	override fun reset(): Unit {}
}

// Manual attacking, Hostiles in system or enemy projectiles flying
class InCombatComponent(): PooledComponent() {
	
	override fun reset(): Unit {}
}
