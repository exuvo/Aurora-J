package se.exuvo.aurora.empires.components

import com.badlogic.ashley.core.Component
import se.exuvo.aurora.empires.Empire

data class ColonyComponent(var population: Integer = 0, var owner: Empire? = null) : Component
