package se.exuvo.aurora.starsystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import net.mostlyoriginal.api.event.common.EventSystem
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.starsystems.components.EmissionsComponent
import se.exuvo.aurora.starsystems.components.EmpireComponent
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.components.UUIDComponent
import se.exuvo.aurora.empires.components.ColonyComponent
import se.exuvo.aurora.galactic.Resource
import se.exuvo.aurora.starsystems.StarSystem
import com.artemis.annotations.Wire
import se.exuvo.aurora.starsystems.components.CargoComponent

class ColonySystem : GalaxyTimeIntervalIteratingSystem(FAMILY, 60 * 60) { // hourly? DailyIteratingSystem
	companion object {
		@JvmField val FAMILY = Aspect.all(ColonyComponent::class.java)
	}

	val log = LogManager.getLogger(this.javaClass)
	
	lateinit private var colonyMapper: ComponentMapper<ColonyComponent>
	lateinit private var cargoMapper: ComponentMapper<CargoComponent>
	lateinit private var emissionsMapper: ComponentMapper<EmissionsComponent>
	lateinit private var ownerMapper: ComponentMapper<EmpireComponent>
	lateinit private var shipMapper: ComponentMapper<ShipComponent>
	lateinit private var uuidMapper: ComponentMapper<UUIDComponent>
	
	@Wire
	lateinit private var system: StarSystem
	lateinit private var events: EventSystem
	
	override fun inserted(entityID: Int) {
		super.inserted(entityID)
		
		val colony = colonyMapper.get(entityID)
		val cargo = cargoMapper.get(entityID)
		
		cargo.set(colony)
		
		val excludedResources = listOf(Resource.MISSILES, Resource.SABOTS)
		for (r in Resource.values()) {
			if (!excludedResources.contains(r)) {
				cargo.addCargo(r, 10000000L)
			}
		}
	}
	
	override fun process(entityID: Int) {

		val colony = colonyMapper.get(entityID)
		val cargo = cargoMapper.get(entityID)
//		val emissions = emissionsMapper.get(entityID)

		colony.shipyards.forEach { shipyard ->
			val modification = shipyard.modificationActivity
			
			if (modification != null) {
				val remainingCost = modification.getCost(shipyard) - shipyard.modificationProgress
				shipyard.modificationProgress += cargo.retrieveCargo(Resource.GENERIC, Math.min(remainingCost, shipyard.modificationRate.toLong()))
				
				if (shipyard.modificationProgress >= modification.getCost(shipyard)) {
					modification.complete(shipyard)
					shipyard.modificationProgress = 0
					shipyard.modificationActivity = null
				}
				
				system.changed(entityID, colonyMapper)
			}
			
			shipyard.slipways.forEach { slipway ->
				val hull = slipway.hull
				
				if (hull != null) {
					var buildrate = shipyard.buildRate
					
					slipway.hullCost.forEach build@{resource, amount ->
						val alreadyBuilt = slipway.usedResources[resource]!!
						val remainingAmount = amount - alreadyBuilt
						
						if (remainingAmount > 0) {
							val buildAmount = cargo.retrieveCargo(resource, Math.min(buildrate, remainingAmount))
							
							if (buildAmount > 0) {
								slipway.usedResources[resource] = buildAmount + alreadyBuilt
								buildrate -= buildAmount
								
								if (buildrate <= 0) {
									return@build
								}
							}
						}
					}
					
					if (buildrate < shipyard.buildRate && slipway.usedResources() >= slipway.totalCost()) {
						
						val owner = ownerMapper.get(entityID)
						val shipEntity: Int = system.createShip(hull, entityID, owner.empire)
						
						slipway.hull = null
						slipway.hullCost = emptyMap()
						slipway.usedResources.clear()
					}
					
					if (buildrate != shipyard.buildRate) {
						system.changed(entityID, colonyMapper)
					}
				}
			}
		}
	}
}
