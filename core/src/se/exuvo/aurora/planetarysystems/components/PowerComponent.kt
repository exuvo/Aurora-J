package se.exuvo.aurora.planetarysystems.components

import com.badlogic.ashley.core.Component
import se.exuvo.aurora.galactic.Part

class PowerComponent : Component {
	var stateChanged = true
	var totalAvailiablePower = 0
	var totalRequestedPower = 0
	var totalUsedPower = 0
	val poweringParts = ArrayList<Part>()
	val poweredParts = ArrayList<Part>()
	val chargedParts = ArrayList<Part>()
}