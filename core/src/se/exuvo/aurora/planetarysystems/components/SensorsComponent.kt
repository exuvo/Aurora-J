package se.exuvo.aurora.planetarysystems.components

import com.artemis.Component
import se.exuvo.aurora.galactic.PassiveSensor
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.galactic.PartRef
import com.artemis.Entity

enum class Spectrum(val short: String) {
	Visible_Light("L"),
	Electromagnetic("EM"),
	Thermal("TH");
	
	override fun toString() : String {
		return short
	}
}

class PassiveSensorsComponent() : Component() {
	lateinit var sensors: List<PartRef<PassiveSensor>>
	
	fun set(sensors: List<PartRef<PassiveSensor>>): PassiveSensorsComponent {
		this.sensors = sensors
		return this
	}
}

class EmissionsComponent() : Component() {
	lateinit var emissions: Map<Spectrum, Double>
	
	fun set(emissions: Map<Spectrum, Double>): EmissionsComponent {
		this.emissions = emissions
		return this
	}
}

// Sensor, AngleStep, DistanceStep
class DetectionComponent() : Component() {
	lateinit var detections: Map<PartRef<PassiveSensor>, Map<Int, Map<Int, DetectionHit>>>
	
	fun set(detections: Map<PartRef<PassiveSensor>, Map<Int, Map<Int, DetectionHit>>>): DetectionComponent {
		this.detections = detections
		return this
	}
}

data class DetectionHit(var signalStrength: Double, val entities: MutableList<Int>, val hitPositions: MutableList<Vector2L>)
