package se.exuvo.aurora.galactic

import se.exuvo.aurora.planetarysystems.components.PowerScheme
import java.lang.IllegalArgumentException

class MunitionClass(val storageType: Resource) {
	var name: String = ""
	var designDay: Int = 0
	private val parts: MutableList<Part> = ArrayList()
	private val partRefs: MutableList<PartRef<Part>> = ArrayList()
	var armorLayers = 1 // Centimeters of armor
	var armorBlockHP = 100

	@Suppress("UNCHECKED_CAST")
	operator fun <T: Part> get(partClass: Class<T>) : List<PartRef<T>> = partRefs.filter { partClass.isInstance(it.part) } as List<PartRef<T>>
	operator fun get(partIndex: Int) = partRefs[partIndex]
	
	fun getParts() = parts as List<Part>
	fun getPartRefs() = partRefs as List<PartRef<Part>>
	
	fun addPart(part: Part) {
		parts.add(part)
		partRefs.add(PartRef(part, parts.size - 1))
	}
	
	fun removePart(part: Part) {
		val index = parts.indexOf(part)
		
		if (index == -1) {
			throw IllegalArgumentException()
		}
		
		parts.removeAt(index)
		partRefs.removeAt(index)
	}
	
	// Kg
	fun getMass(): Int {
		//TODO add armor
		return parts.sumBy { it.getMass() }
	}

	// cm³
	fun getVolume(): Int {
		//TODO add armor
		return parts.sumBy { it.getVolume() }
	}
	
	fun getRadius(): Int {
		val volume = getVolume()

		// V = πr^2h, http://mathhelpforum.com/geometry/170076-how-find-cylinder-dimensions-volume-aspect-ratio.html
		val lengthToDiameterRatio = 2.0
		val length = Math.pow(Math.pow(2.0, 2 * lengthToDiameterRatio) * volume / Math.PI, 1.0 / 3)
		var radius = Math.ceil(Math.sqrt(volume / Math.PI / length)).toInt()
		
		//TODO add armor
		return radius + armorLayers
	}

	// cm^2
	fun getSurfaceArea(): Int {
		val volume = getVolume()

		// V = πr^2h, http://mathhelpforum.com/geometry/170076-how-find-cylinder-dimensions-volume-aspect-ratio.html
		val lengthToDiameterRatio = 2.0
		val length = Math.pow(Math.pow(2.0, 2 * lengthToDiameterRatio) * volume / Math.PI, 1.0 / 3)
		val radius = Math.sqrt(volume / Math.PI / length)

		val surface = 2 * Math.PI * radius * length + 2 * Math.PI * radius * radius

//		println("length $length, diameter ${2 * radius}, surface $surface, volume ${Math.PI * length * radius * radius}")

		//TODO add armor
		return surface.toInt()
	}
	
	override fun toString() = name
	
	private val hashcode: Int by lazy (LazyThreadSafetyMode.NONE) {
		var hash = 1;
		hash = 37 * hash + name.hashCode()
		hash = 37 * hash + designDay
		hash = 37 * hash + armorLayers
		hash
	}

	override fun hashCode(): Int = hashcode
}
