package se.exuvo.aurora.components

import com.badlogic.ashley.core.Component
import se.exuvo.aurora.SolarSystem

data class SolarSystemComponent(var system: SolarSystem? = null) : Component