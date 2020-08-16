package se.exuvo.aurora.starsystems.components

import com.artemis.Component
import se.exuvo.aurora.galactic.CargoType
import se.exuvo.aurora.galactic.ContainerPart
import se.exuvo.aurora.galactic.MunitionHull
import se.exuvo.aurora.galactic.PartRef
import se.exuvo.aurora.galactic.Resource
import se.exuvo.aurora.galactic.ShipHull
import se.exuvo.aurora.utils.forEachFast
import java.security.InvalidParameterException

class CargoComponent() : Component(), CloneableComponent<CargoComponent> {
	var hullHashcode = 0
	val cargo = LinkedHashMap<Resource, CargoContainer>()
	val cargoTypes = LinkedHashMap<CargoType, CargoContainer>()
	var munitionCargo: MutableMap<MunitionHull, Int>? = null
	var cargoChanged = false
	
	var mass = -1L
		get() {
			if (field == -1L) { field = calculateCargoMass() }
			return field
		}
	
	fun set(hull: ShipHull): CargoComponent {
		val containerPartRefs: List<PartRef<ContainerPart>> = hull[ContainerPart::class]
		
		if (containerPartRefs.isEmpty()) {
			throw InvalidParameterException("no cargo on this hull $hull")
		}
		
		hullHashcode = hull.hashCode()
		cargoChanged = true
		
		val cargoContainers = listOf(CargoContainer(CargoType.NORMAL), CargoContainer(CargoType.LIFE_SUPPORT), CargoContainer(CargoType.FUEL), CargoContainer(CargoType.AMMUNITION), CargoContainer(CargoType.NUCLEAR))
		
		containerPartRefs.forEachFast{ containerRef ->
			for (container in cargoContainers) {
				if (container.type == containerRef.part.cargoType) {
					container.maxVolume += containerRef.part.capacity
				}
			}
		}
		
		cargoContainers.forEachFast{ container ->
			if (container.maxVolume > 0) {
				cargoTypes[container.type] = container
				container.type.resources.forEachFast{ resource ->
					cargo[resource] = container
				}
				if (container.type == CargoType.AMMUNITION) {
					munitionCargo = LinkedHashMap<MunitionHull, Int>()
				}
			}
		}
		
		return this
	}
	
	override fun copy(tc: CargoComponent) {
		val munitionCargo = munitionCargo
		
		if (tc.hullHashcode != hullHashcode) {
			tc.hullHashcode = hullHashcode
			tc.cargo.clear()
			
			val cargoContainers = listOf(CargoContainer(CargoType.NORMAL), CargoContainer(CargoType.LIFE_SUPPORT), CargoContainer(CargoType.FUEL), CargoContainer(CargoType.AMMUNITION), CargoContainer(CargoType.NUCLEAR))
			
			cargoContainers.forEachFast{ tcContainer ->
				tcContainer.type.resources.forEachFast{ resource ->
					val cargo = cargo[resource]
					if (cargo != null) {
						tcContainer.maxVolume = cargo.maxVolume
						tcContainer.usedVolume = cargo.usedVolume
						tcContainer.contents[resource] = cargo.contents[resource]!!
						tc.cargo[resource] = tcContainer
					}
				}
				if (cargoTypes[tcContainer.type] != null) {
					tc.cargoTypes[tcContainer.type] = tcContainer
				}
			}
			
			if (munitionCargo == null) {
				tc.munitionCargo = null
				
			} else {
				val mCargo = LinkedHashMap<MunitionHull, Int>()
				mCargo.putAll(munitionCargo)
				
				tc.munitionCargo = mCargo
			}
			
		} else {
			tc.mass = mass
			
			cargo.forEach { (resource, shipCargo) ->
				val tcShipCargo = tc.cargo[resource]!!
				tcShipCargo.maxVolume = shipCargo.maxVolume
				tcShipCargo.usedVolume = shipCargo.usedVolume
				tcShipCargo.contents[resource] = shipCargo.contents[resource]!!
			}
			
			if (munitionCargo != null) {
				val tcMunitionCargo = tc.munitionCargo!!
				tcMunitionCargo.clear()
				tcMunitionCargo.putAll(munitionCargo)
			}
		}
	}
	
	fun calculateCargoMass(): Long {
		var mass = 0L
		
		for((resource, shipCargo) in cargo) {
			mass += shipCargo.contents[resource]!!
		}
		
		return mass
	}
	
	fun getCargoAmount(resource: Resource): Long {
		
		val shipCargo = cargo[resource]
		
		if (shipCargo != null) {
			
			val available = shipCargo.contents[resource]
			
			if (available != null) {
				return available
			}
		}
		
		return 0
	}
	
	fun getCargoAmount(munitionHull: MunitionHull): Int {
		
		val shipCargo = cargo[munitionHull.storageType]
		
		if (shipCargo != null) {
			
			val available = munitionCargo!![munitionHull]
			
			if (available != null) {
				return available
			}
		}
		
		return 0
	}
	
	fun getUsedCargoVolume(resource: Resource): Long {
		
		val shipCargo = cargo[resource]
		
		if (shipCargo != null) {
			
			return shipCargo.usedVolume
		}
		
		return 0
	}
	
	fun getMaxCargoVolume(resource: Resource): Long {
		
		val shipCargo = cargo[resource]
		
		if (shipCargo != null) {
			
			return shipCargo.maxVolume
		}
		
		return 0
	}
	
	fun getUsedCargoVolume(type: CargoType): Long {
		
		val shipCargo = cargo[type.resources[0]]
		
		if (shipCargo != null) {
			
			return shipCargo.usedVolume
		}
		
		return 0
	}
	
	fun getMaxCargoVolume(type: CargoType): Long {
		
		val shipCargo = cargo[type.resources[0]]
		
		if (shipCargo != null) {
			
			return shipCargo.maxVolume
		}
		
		return 0
	}
	
	fun getUsedCargoMass(resource: Resource): Long {
		
		val shipCargo = cargo[resource]
		
		if (shipCargo != null) {
			
			val amount = shipCargo.contents[resource]
			
			if (amount != null) {
				return amount
			}
		}
		
		return 0
	}
	
	fun getUsedCargoMass(type: CargoType): Long {
		
		val shipCargo = cargo[type.resources[0]]
		
		if (shipCargo != null) {
			
			return shipCargo.contents.values.sum()
		}
		
		return 0
	}
	
	fun addCargo(resource: Resource, amount: Long): Boolean {
		
		if (resource.specificVolume == 0) {
			throw InvalidParameterException()
		}
		
		val shipCargo = cargo[resource]
		
		if (shipCargo != null) {
			
			val volumeToBeStored = amount * resource.specificVolume
			
			if (shipCargo.usedVolume + volumeToBeStored > shipCargo.maxVolume) {
				return false;
			}
			
			shipCargo.usedVolume += volumeToBeStored
			shipCargo.contents[resource] = shipCargo.contents[resource]!! + amount
			
			massChange()
			return true
		}
		
		return false
	}
	
	fun addCargo(munitionHull: MunitionHull, amount: Int): Boolean {
		
		val shipCargo = cargo[munitionHull.storageType]
		
		if (shipCargo != null) {
			
			val munitionCargo = munitionCargo!!
			
			val volumeToBeStored = amount * munitionHull.volume
			
			if (shipCargo.usedVolume + volumeToBeStored > shipCargo.maxVolume) {
				return false;
			}
			
			shipCargo.usedVolume += volumeToBeStored
			val storedMass = shipCargo.contents[munitionHull.storageType]!!
			shipCargo.contents[munitionHull.storageType] = storedMass + munitionHull.loadedMass * amount
			
			var stored = munitionCargo[munitionHull]
			
			if (stored == null) {
				stored = 0
			}
			
			munitionCargo[munitionHull] = stored + amount
			
			massChange()
			return true
		}
		
		return false
	}
	
	fun retrieveCargo(resource: Resource, amount: Long): Long {
		
		if (resource.specificVolume == 0) {
			throw InvalidParameterException()
		}
		
		val shipCargo = cargo[resource]
		
		if (shipCargo != null) {
			
			val available = shipCargo.contents[resource]
			
			if (available == null || available == 0L) {
				return 0
			}
			
			var retrievedAmount = amount
			
			if (available < amount) {
				retrievedAmount = available
			}
			
			shipCargo.contents[resource] = available - retrievedAmount
			shipCargo.usedVolume -= retrievedAmount * resource.specificVolume
			
			massChange()
			return retrievedAmount
		}
		
		return 0
	}
	
	fun retrieveCargo(munitionHull: MunitionHull, amount: Int): Int {
		
		val munitionCargo = munitionCargo
		
		if (munitionCargo == null) {
			return 0
		}
		
		var available = munitionCargo[munitionHull]
		
		if (available == null || available == 0) {
			return 0
		}
		
		var retrievedAmount = amount
		
		if (available < amount) {
			retrievedAmount = available
		}
		
		munitionCargo[munitionHull] = available - retrievedAmount
		
		val shipCargo = cargo[munitionHull.storageType]!!
		val storedMass = shipCargo.contents[munitionHull.storageType]!!
		
		shipCargo.contents[munitionHull.storageType] = storedMass - retrievedAmount * munitionHull.loadedMass
		shipCargo.usedVolume -= retrievedAmount * munitionHull.volume
		
		massChange()
		return retrievedAmount
	}
	
	private fun massChange() {
		cargoChanged = true
		mass = -1
	}
	
	fun resetLazyCache() {
		mass = -1
	}
}

data class CargoContainer(val type: CargoType) {
	var maxVolume = 0L
	var usedVolume = 0L
	var contents: MutableMap<Resource, Long> = LinkedHashMap()
	
	init {
		type.resources.forEachFast{ resource ->
			contents[resource] = 0
		}
	}
}

