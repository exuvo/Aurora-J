package com.thedeadpixelsociety.ld34.components

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity

// See https://en.wikipedia.org/wiki/Orbital_elements
data class OrbitComponent(var parent: Entity? = null,
													var e_eccentricity: Float = 0f, // 0 = circle, 0 < elliptic < 1
													var a_semiMajorAxis: Float = 1f, // In AU
													var w_argumentOfPeriapsis: Float = 0f, // In 360 degrees
													var M_meanAnomaly: Float = 0f) : Component // In 360 degrees