package se.exuvo.aurora.systems

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntityListener
import com.badlogic.ashley.core.EntitySystem
import java.util.Collections
import java.util.HashMap

class GroupSystem() : EntitySystem(), EntityListener {
	companion object {
		val SELECTED = "selected"
	}

	private val groupToEntityMap = HashMap<String, MutableSet<Entity>?>()
	private val entityToGroupMap = HashMap<Entity, MutableSet<String>?>()

	override fun checkProcessing() = false

	override fun addedToEngine(engine: Engine?) {
		engine?.addEntityListener(this)
	}

	override fun removedFromEngine(engine: Engine) {
		groupToEntityMap.clear()
		entityToGroupMap.clear()
		engine.removeEntityListener(this)
	}

	operator fun get(group: String): Set<Entity> {
		return groupToEntityMap[group] ?: Collections.emptySet()
	}

	operator fun get(entity: Entity): Set<String> {
		return entityToGroupMap[entity] ?: Collections.emptySet()
	}

	fun isMemberOf(entity: Entity, group: String): Boolean {
		return get(group).contains(entity)
	}

	fun add(entity: Entity, group: String) {

		var entitiesSet = groupToEntityMap[group]

		if (entitiesSet == null) {
			entitiesSet = HashSet<Entity>()
			groupToEntityMap[group] = entitiesSet
		}

		entitiesSet.add(entity)

		var groupSet = entityToGroupMap[entity]

		if (groupSet == null) {
			groupSet = HashSet<String>()
			entityToGroupMap[entity] = groupSet
		}

		groupSet.add(group)
	}

	fun add(entities: List<Entity>, group: String) {

		var entitiesSet = groupToEntityMap[group]

		if (entitiesSet == null) {
			entitiesSet = HashSet<Entity>()
			groupToEntityMap[group] = entitiesSet
		}

		entitiesSet.addAll(entities)

		for (entity in entities) {
			var groupSet = entityToGroupMap[entity]

			if (groupSet == null) {
				groupSet = HashSet<String>()
				entityToGroupMap[entity] = groupSet
			}

			groupSet.add(group)
		}
	}

	fun remove(entity: Entity, group: String) {

		val entitiesSet = groupToEntityMap[group]

		if (entitiesSet != null) {
			entitiesSet.remove(entity)
		}

		var groupSet = entityToGroupMap[entity]

		if (groupSet != null) {
			groupSet.remove(group)
		}
	}

	fun remove(entities: List<Entity>, group: String) {

		val entitiesSet = groupToEntityMap[group]

		if (entitiesSet != null) {
			entitiesSet.removeAll(entities)
		}

		for (entity in entities) {
			var groupSet = entityToGroupMap[entity]

			if (groupSet != null) {
				groupSet.remove(group)
			}
		}
	}

	fun clear(group: String) {

		val entitiesSet = groupToEntityMap[group]

		if (entitiesSet != null) {

			for (entity in entitiesSet) {
				var groupSet = entityToGroupMap[entity]

				if (groupSet != null) {
					groupSet.remove(group)
				}
			}

			entitiesSet.clear()
		}
	}

	override fun entityAdded(entity: Entity) {
	}

	override fun entityRemoved(entity: Entity) {

		var groupSet = entityToGroupMap[entity]

		if (groupSet != null) {

			for (group in groupSet) {
				remove(entity, group)
			}

			entityToGroupMap[entity] = null
		}
	}
}
