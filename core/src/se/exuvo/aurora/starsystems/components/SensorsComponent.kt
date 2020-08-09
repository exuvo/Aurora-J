package se.exuvo.aurora.starsystems.components

import com.artemis.Component
import se.exuvo.aurora.galactic.PassiveSensor
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.galactic.PartRef

enum class Spectrum(val short: String) {
	Visible_Light("L"),
	Electromagnetic("EM"),
	Thermal("TH");
	
	override fun toString() : String {
		return short
	}
}

class PassiveSensorsComponent() : Component(), CloneableComponent<PassiveSensorsComponent> {
	var sensors: List<PartRef<PassiveSensor>> = emptyList()
	
	fun set(sensors: List<PartRef<PassiveSensor>>): PassiveSensorsComponent {
		this.sensors = sensors
		return this
	}
	
	override fun copy(tc: PassiveSensorsComponent) {
		if (tc.sensors == emptyList<PartRef<PassiveSensor>>()) {
			tc.sensors = ArrayList(sensors)
			
		} else if (tc.sensors.hashCode() != sensors.hashCode()) {
			val list = tc.sensors as MutableList
			list.clear()
			list.addAll(sensors)
		}
	}
}

class EmissionsComponent() : Component(), CloneableComponent<EmissionsComponent> {
	var emissions: Map<Spectrum, Double> = emptyMap()
	
	fun set(emissions: Map<Spectrum, Double>): EmissionsComponent {
		this.emissions = emissions
		return this
	}
	
	override fun copy(tc: EmissionsComponent) {
		if (tc.emissions == emptyMap<Spectrum, Double>()) {
			tc.emissions = HashMap(emissions)
			
		} else if (tc.emissions.hashCode() != emissions.hashCode()) {
			val list = tc.emissions as MutableMap
			list.clear()
			list.putAll(emissions)
		}
	}
}

// Sensor, AngleStep, DistanceStep
class DetectionComponent() : Component(), CloneableComponent<DetectionComponent> {
	var detections: Map<PartRef<PassiveSensor>, Map<Int, Map<Int, DetectionHit>>> = emptyMap()
	
	fun set(detections: Map<PartRef<PassiveSensor>, Map<Int, Map<Int, DetectionHit>>>): DetectionComponent {
		this.detections = detections
		return this
	}
	
	override fun copy(tc: DetectionComponent) {
		if (tc.detections == emptyMap<PartRef<PassiveSensor>, Map<Int, Map<Int, DetectionHit>>>()) {
			tc.detections = HashMap(detections)
			
		} else if (tc.detections.hashCode() != detections.hashCode()) {
			val list = tc.detections as MutableMap
			list.clear()
			list.putAll(detections)
		}
	}
}

data class DetectionHit(var signalStrength: Double, val entities: MutableList<Int>, val hitPositions: MutableList<Vector2L>)

class GravimetricSensorsComponent() : Component(), CloneableComponent<GravimetricSensorsComponent> {
	var sensors: List<PartRef<PassiveSensor>> = emptyList()
	
	fun set(sensors: List<PartRef<PassiveSensor>>): GravimetricSensorsComponent {
		this.sensors = sensors
		return this
	}
	
	override fun copy(tc: GravimetricSensorsComponent) {
		if (tc.sensors == emptyList<PartRef<PassiveSensor>>()) {
			tc.sensors = ArrayList(sensors)
			
		} else if (tc.sensors.hashCode() != sensors.hashCode()) {
			val list = tc.sensors as MutableList
			list.clear()
			list.addAll(sensors)
		}
	}
}