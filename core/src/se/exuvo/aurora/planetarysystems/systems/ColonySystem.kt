package se.exuvo.aurora.planetarysystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.EntitySubscription
import com.artemis.EntitySubscription.SubscriptionListener
import com.artemis.systems.IteratingSystem
import com.artemis.utils.IntBag
import net.mostlyoriginal.api.event.common.EventSystem
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.PartRef
import se.exuvo.aurora.galactic.PassiveSensor
import se.exuvo.aurora.planetarysystems.components.DetectionComponent
import se.exuvo.aurora.planetarysystems.components.DetectionHit
import se.exuvo.aurora.planetarysystems.components.EmissionsComponent
import se.exuvo.aurora.planetarysystems.components.OwnerComponent
import se.exuvo.aurora.planetarysystems.components.PassiveSensorState
import se.exuvo.aurora.planetarysystems.components.PassiveSensorsComponent
import se.exuvo.aurora.planetarysystems.components.PoweredPartState
import se.exuvo.aurora.planetarysystems.components.ShipComponent
import se.exuvo.aurora.planetarysystems.components.TimedMovementComponent
import se.exuvo.aurora.planetarysystems.components.UUIDComponent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.utils.forEach
import se.exuvo.aurora.empires.components.ColonyComponent
import se.exuvo.aurora.galactic.Resource
import se.exuvo.aurora.planetarysystems.PlanetarySystem
import com.artemis.annotations.Wire

class ColonySystem : GalaxyTimeIntervalIteratingSystem(FAMILY, 3) { // DailyIteratingSystem
	companion object {
		val FAMILY = Aspect.all(ColonyComponent::class.java)
	}

	val log = LogManager.getLogger(this.javaClass)
	
	lateinit private var colonyMapper: ComponentMapper<ColonyComponent>
	lateinit private var emissionsMapper: ComponentMapper<EmissionsComponent>
	lateinit private var ownerMapper: ComponentMapper<OwnerComponent>
	lateinit private var shipMapper: ComponentMapper<ShipComponent>
	lateinit private var uuidMapper: ComponentMapper<UUIDComponent>
	
	@Wire
	lateinit private var system: PlanetarySystem
	lateinit private var events: EventSystem

	override fun process(entityID: Int) {

		val colony = colonyMapper.get(entityID)
		val owner = ownerMapper.get(entityID)
		val emissions = emissionsMapper.get(entityID)

		colony.shipyards.forEach { shipyard ->
			val modification = shipyard.modificationActivity
			
			if (modification != null) {
				val remainingCost = modification.getCost(shipyard) - shipyard.modificationProgress
				shipyard.modificationProgress += colony.retrieveCargo(Resource.GENERIC, Math.min(remainingCost, shipyard.modificationRate.toLong()))
				
				if (shipyard.modificationProgress >= modification.getCost(shipyard)) {
					modification.complete(shipyard)
					shipyard.modificationProgress = 0
					shipyard.modificationActivity = null
				}
			}
			
			shipyard.slipways.forEach { slipway ->
				val hull = slipway.hull
				
				if (hull != null) {
					var buildrate = shipyard.buildRate
					
					slipway.hullCost.forEach build@{resource, amount ->
						val alreadyBuilt = slipway.usedResources[resource]!!
						val remainingAmount = amount - alreadyBuilt
						
						if (remainingAmount > 0) {
							val buildAmount = colony.retrieveCargo(resource, Math.min(buildrate, remainingAmount))
							
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
						
						val shipEntity: Int = system.createShip(hull, entityID, owner.empire)
						
						slipway.hull = null
						slipway.hullCost = emptyMap()
						slipway.usedResources.clear()
					}
				}
			}
		}
	}
}
