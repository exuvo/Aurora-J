package se.exuvo.aurora.components

import com.badlogic.ashley.core.Component
import se.exuvo.aurora.SolarSystem

// W/m2 @ 1 AU. https://en.wikipedia.org/wiki/Solar_constant
data class SunComponent(var solarConstant: Int = 1361) : Component

// W/m2
data class SolarIrradianceComponent(var irradiance: Int = 0) : Component
