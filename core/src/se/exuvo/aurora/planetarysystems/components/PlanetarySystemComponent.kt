package se.exuvo.aurora.planetarysystems.components

import com.badlogic.ashley.core.Component
import se.exuvo.aurora.planetarysystems.PlanetarySystem

data class PlanetarySystemComponent(var system: PlanetarySystem? = null) : Component