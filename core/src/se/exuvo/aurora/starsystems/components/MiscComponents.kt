package se.exuvo.aurora.starsystems.components

import com.artemis.Component
import com.artemis.PooledComponent

//TODO add modification counter to avoid expensive checks
interface CloneableComponent<T> where T: CloneableComponent<T>, T: Component {
	fun copy(tc: T)
	
	fun copy2(targetComponent: Component) {
		copy(targetComponent as T)
	}
}

class SpatialPartitioningComponent(): PooledComponent() { // , CloneableComponent<SpatialPartitioningComponent>
	var nextExpectedUpdate: Long = 0
	var elementID: Int = -1
	
	fun set(nextExpectedUpdate: Long, elementID: Int): SpatialPartitioningComponent {
		this.nextExpectedUpdate = nextExpectedUpdate
		this.elementID = elementID
		return this
	}
	
	override fun reset(): Unit {
		elementID = -1
	}
//	override fun copy(tc: SpatialPartitioningComponent) {
//		tc.set(nextExpectedUpdate, elementID)
//	}
}

class SpatialPartitioningPlanetoidsComponent(): PooledComponent() { // , CloneableComponent<SpatialPartitioningComponent>
	var nextExpectedUpdate: Long = 0
	var elementID: Int = -1
	
	fun set(nextExpectedUpdate: Long, elementID: Int): SpatialPartitioningPlanetoidsComponent {
		this.nextExpectedUpdate = nextExpectedUpdate
		this.elementID = elementID
		return this
	}
	
	override fun reset(): Unit {
		elementID = -1
	}
//	override fun copy(tc: SpatialPartitioningComponent) {
//		tc.set(nextExpectedUpdate, elementID)
//	}
}