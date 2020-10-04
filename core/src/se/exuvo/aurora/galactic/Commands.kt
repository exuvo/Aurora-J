package se.exuvo.aurora.galactic

import com.artemis.utils.Bag
import se.exuvo.aurora.starsystems.StarSystem
import se.exuvo.aurora.starsystems.components.ApproachType
import se.exuvo.aurora.starsystems.components.EntityReference
import se.exuvo.aurora.starsystems.systems.MovementSystem
import se.exuvo.aurora.starsystems.systems.TargetingSystem
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.utils.forEachFast

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

abstract class EntitiesCommand(var entitiesRef: Bag<EntityReference>): Command() {
	override fun isValid(): Boolean {
		var size = entitiesRef.size
		var i = 0
		
		while (i < size) {
			val entityRef2 = entitiesRef[i].system.galaxy.resolveEntityReference(entitiesRef[i])
			
			if (entityRef2 != null) {
				entitiesRef[i] = entityRef2
				i++
			} else {
				entitiesRef.remove(i)
				size--
			}
		}
		
		return size > 0
	}
	
	override fun getSystem() = entitiesRef[0].system
}

abstract class EntityTargetEntityCommand(entityRef: EntityReference, var targetRef: EntityReference): EntityCommand(entityRef) {
	override fun isValid(): Boolean {
		if (!super.isValid()) {
			return false
		}
		
		val entityRef2 = targetRef.system.galaxy.resolveEntityReference(targetRef)
		
		if (entityRef2 != null && getSystem() == entityRef2.system) {
			targetRef = entityRef2
			return true
		}
		
		return false
	}
}

abstract class EntitiesTargetEntityCommand(entitiesRef: Bag<EntityReference>, var targetRef: EntityReference): EntitiesCommand(entitiesRef) {
	override fun isValid(): Boolean {
		if (!super.isValid()) {
			return false
		}

		val entityRef2 = targetRef.system.galaxy.resolveEntityReference(targetRef)

		if (entityRef2 != null && getSystem() == entityRef2.system) {
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

class EntityClearTargetCommand(shipRef: EntityReference, val tc: PartRef<TargetingComputer>): EntityCommand(shipRef) {
	override fun apply() {
		entityRef.system.world.getSystem(TargetingSystem::class.java).clearTarget(entityRef.entityID, tc)
	}
}

class EntitySetTargetCommand(shipRef: EntityReference, val tc: PartRef<TargetingComputer>, targetRef: EntityReference): EntityTargetEntityCommand(shipRef, targetRef) {
	override fun apply() {
		entityRef.system.world.getSystem(TargetingSystem::class.java).setTarget(entityRef.entityID, tc, targetRef)
	}
}

class EntityMoveToPositionCommand(shipRef: EntityReference, val targetPosition: Vector2L, val approachType: ApproachType): EntityCommand(shipRef) {
	override fun apply() {
		entityRef.system.world.getSystem(MovementSystem::class.java).moveToPosition(entityRef.entityID, targetPosition, approachType)
	}
}

class EntityMoveToEntityCommand(shipRef: EntityReference, targetRef: EntityReference, val approachType: ApproachType): EntityTargetEntityCommand(shipRef, targetRef) {
	override fun apply() {
		entityRef.system.world.getSystem(MovementSystem::class.java).moveToEntity(entityRef.entityID, targetRef.entityID, approachType)
	}
}

class EntitiesMoveToPositionCommand(shipRefs: Bag<EntityReference>, val targetPosition: Vector2L, val approachType: ApproachType): EntitiesCommand(shipRefs) {
	override fun apply() {
		entitiesRef.forEachFast { entityRef ->
			entityRef.system.world.getSystem(MovementSystem::class.java).moveToPosition(entityRef.entityID, targetPosition, approachType)
		}
	}
}

class EntitiesMoveToEntityCommand(shipRefs: Bag<EntityReference>, targetRef: EntityReference, val approachType: ApproachType): EntitiesTargetEntityCommand(shipRefs, targetRef) {
	override fun apply() {
		entitiesRef.forEachFast { entityRef ->
			entityRef.system.world.getSystem(MovementSystem::class.java).moveToEntity(entityRef.entityID, targetRef.entityID, approachType)
		}
	}
}