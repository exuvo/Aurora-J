package se.exuvo.aurora.galactic

import se.exuvo.aurora.starsystems.StarSystem
import se.exuvo.aurora.starsystems.components.ApproachType
import se.exuvo.aurora.starsystems.components.EntityReference
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.systems.MovementSystem
import se.exuvo.aurora.starsystems.systems.TargetingSystem
import se.exuvo.aurora.utils.Vector2L

abstract class Command {
	abstract fun isValid(): Boolean
	abstract fun apply()
	abstract fun getSystem(): StarSystem
}

abstract class EntityCommand(var entityRef: EntityReference): Command() {
	override fun isValid(): Boolean {
		val entityRef2 = entityRef.system.galaxy.resolveEntityReference(entityRef)
		
		if (entityRef2 != null) {
			entityRef = entityRef2
			return true
		}
		
		return false
	}
	
	override fun getSystem() = entityRef.system
}

abstract class TargetEntityCommand(entityRef: EntityReference, var targetRef: EntityReference): EntityCommand(entityRef) {
	override fun isValid(): Boolean {
		if (!super.isValid()) {
			return false
		}
		
		val entityRef2 = targetRef.system.galaxy.resolveEntityReference(targetRef)
		
		if (entityRef2 != null && entityRef.system == entityRef2.system) {
			targetRef = entityRef2
			return true
		}
		
		return false
	}
}

abstract class ShipyardCommand(planetRef: EntityReference): EntityCommand(planetRef) {
	override fun isValid(): Boolean {
		if (!super.isValid()) {
			return false
		}
		
		return true
	}
}

class ClearTargetCommand(shipRef: EntityReference, val tc: PartRef<TargetingComputer>): EntityCommand(shipRef) {
	override fun apply() {
		val ship = entityRef.system.world.getMapper(ShipComponent::class.java).get(entityRef.entityID)
		entityRef.system.world.getSystem(TargetingSystem::class.java).clearTarget(entityRef.entityID, ship, tc)
	}
}

class SetTargetCommand(shipRef: EntityReference, val tc: PartRef<TargetingComputer>, targetRef: EntityReference): TargetEntityCommand(shipRef, targetRef) {
	override fun apply() {
		val ship = entityRef.system.world.getMapper(ShipComponent::class.java).get(entityRef.entityID)
		entityRef.system.world.getSystem(TargetingSystem::class.java).setTarget(entityRef.entityID, ship, tc, targetRef)
	}
}

class MoteToPositionCommand(shipRef: EntityReference, val targetPosition: Vector2L, val approachType: ApproachType): EntityCommand(shipRef) {
	override fun apply() {
		entityRef.system.world.getSystem(MovementSystem::class.java).moveToPosition(entityRef.entityID, targetPosition, approachType)
	}
}

class MoveToEntityCommand(shipRef: EntityReference, targetRef: EntityReference, val approachType: ApproachType): TargetEntityCommand(shipRef, targetRef) {
	override fun apply() {
		entityRef.system.world.getSystem(MovementSystem::class.java).moveToEntity(entityRef.entityID, targetRef.entityID, approachType)
	}
}