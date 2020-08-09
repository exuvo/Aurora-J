package se.exuvo.aurora.starsystems.components

import com.artemis.Component

//TODO add modification counter to avoid expensive checks
interface CloneableComponent<T> where T: CloneableComponent<T>, T: Component {
	fun copy(tc: T)
	
	fun copy2(targetComponent: Component) {
		copy(targetComponent as T)
	}
}
