package se.exuvo.aurora.empires.components

import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.galactic.PartRef
import com.artemis.Component
import com.artemis.PooledComponent
import se.exuvo.aurora.starsystems.components.CloneableComponent

class IdleTargetingComputersComponent() : PooledComponent(), CloneableComponent<IdleTargetingComputersComponent> {
	lateinit var targetingComputers: MutableList<PartRef<TargetingComputer>>
	
	fun set(targetingComputers: MutableList<PartRef<TargetingComputer>>): IdleTargetingComputersComponent {
		this.targetingComputers = targetingComputers
		return this
	}
	
	override fun reset(): Unit {}
	override fun copy(tc: IdleTargetingComputersComponent) {
		tc.set(targetingComputers)
	}
}

class ActiveTargetingComputersComponent() : PooledComponent(), CloneableComponent<ActiveTargetingComputersComponent> {
	lateinit var targetingComputers: MutableList<PartRef<TargetingComputer>>
	
	fun set(targetingComputers: MutableList<PartRef<TargetingComputer>>): ActiveTargetingComputersComponent {
		this.targetingComputers = targetingComputers
		return this
	}
	
	override fun reset(): Unit {}
	override fun copy(tc: ActiveTargetingComputersComponent) {
		tc.set(targetingComputers)
	}
}
