package se.exuvo.aurora.empires.components

import com.badlogic.ashley.core.Component
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.galactic.PartRef

data class WeaponsComponent(var targetingComputers: List<PartRef<TargetingComputer>>) : Component
