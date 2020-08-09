package se.exuvo.aurora.starsystems.components

import com.artemis.Component

// See https://en.wikipedia.org/wiki/Orbital_elements
class OrbitComponent() : Component(), CloneableComponent<OrbitComponent> {
	var parent: Int = -1
	var e_eccentricity: Float = 0f // 0 = circle, 0 < elliptic < 1
	var a_semiMajorAxis: Float = 1f // In AU
	var w_argumentOfPeriapsis: Float = 0f // In 360 degrees
	var M_meanAnomaly: Float = 0f // In 360 degrees

	fun set(parent: Int,
					e_eccentricity: Float,
					a_semiMajorAxis: Float,
					w_argumentOfPeriapsis: Float,
					M_meanAnomaly: Float
	): OrbitComponent {
		this.parent = parent
		this.e_eccentricity = e_eccentricity
		this.a_semiMajorAxis = a_semiMajorAxis
		this.w_argumentOfPeriapsis = w_argumentOfPeriapsis
		this.M_meanAnomaly = M_meanAnomaly
		return this
	}
	
	override fun copy(tc: OrbitComponent) {
		tc.set(parent, e_eccentricity, a_semiMajorAxis, w_argumentOfPeriapsis, M_meanAnomaly)
	}
}