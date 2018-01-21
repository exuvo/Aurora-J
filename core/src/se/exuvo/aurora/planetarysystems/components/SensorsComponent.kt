package se.exuvo.aurora.planetarysystems.components

import com.badlogic.ashley.core.Component
import se.exuvo.aurora.galactic.PassiveSensor
import com.badlogic.ashley.core.Entity
import se.exuvo.aurora.utils.Vector2L

enum class Spectrum(val short: String) {
	Visible_Light("L"),
	Electromagnetic("EM"),
	Thermal("TH");
	
	override fun toString() : String {
		return short
	}
}

data class PassiveSensorsComponent(var sensors: List<PassiveSensor>) : Component

data class EmissionsComponent(var emissions: Map<Spectrum, Double>) : Component

// Sensor, AngleStep, DistanceStep
data class DetectionComponent(var detections: Map<PassiveSensor, Map<Int, Map<Int, DetectionHit>>>) : Component

data class DetectionHit(var signalStrength: Double, val entities: MutableList<Entity>, val hitPositions: MutableList<Vector2L>)
