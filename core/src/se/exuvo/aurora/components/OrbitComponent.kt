package com.thedeadpixelsociety.ld34.components

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity

data class OrbitComponent(var parent: Entity? = null, var apoapsis: Float = 1000f, var periapsis: Float = 1000f) : Component