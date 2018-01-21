package se.exuvo.aurora.planetarysystems.components

import com.badlogic.ashley.core.Component
import se.exuvo.aurora.galactic.Empire

data class OwnerComponent(var empire: Empire) : Component
