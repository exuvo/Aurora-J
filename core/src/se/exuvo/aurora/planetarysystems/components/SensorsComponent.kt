package se.exuvo.aurora.planetarysystems.components

import com.badlogic.ashley.core.Component
import se.exuvo.aurora.galactic.Sensor
import com.badlogic.ashley.core.Entity

enum class Spectrum(val short: String) {
	Visible_Light("L"),
	Electromagnetic("EM"),
	Thermal("TH");
	
	override fun toString() : String {
		return short
	}
}

data class SensorsComponent(var sensors: List<Sensor>) : Component

data class EmissionsComponent(var emissions: Map<Spectrum, Double>) : Component

data class DetectionComponent(var detections: Map<Entity, List<DetectionHit>>) : Component

data class DetectionHit(val sensor: Sensor, val signalStrength: Double)
