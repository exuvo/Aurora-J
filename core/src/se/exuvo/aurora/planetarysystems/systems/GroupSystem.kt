package se.exuvo.aurora.planetarysystems.systems

import java.util.Collections
import java.util.HashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import se.exuvo.aurora.utils.DummyReentrantReadWriteLock
import com.artemis.Entity
import com.artemis.EntitySystem
import com.artemis.Aspect

class GroupSystem(val lock: ReentrantReadWriteLock = DummyReentrantReadWriteLock.INSTANCE) : EntitySystem(Aspect.all()) {
	companion object {
		// Galaxy only
		val SELECTED = "selected"
	}

	private val groupToEntityMap = HashMap<String, MutableSet<Entity>?>()
	private val entityToGroupMap = HashMap<Entity, MutableSet<String>?>()

	private val groupToEntityModificationCountMap = HashMap<String, Int?>()
	private val entityToGroupModificationCountpMap = HashMap<Entity, Int?>()

	override fun checkProcessing() = false
	override fun processSystem() {}

	operator fun get(group: String): Set<Entity> {
		lock.read {
			return groupToEntityMap[group] ?: Collections.emptySet()
		}
	}

	operator fun get(entity: Entity): Set<String> {
		lock.read {
			return entityToGroupMap[entity] ?: Collections.emptySet()
		}
	}

	fun getModificationCount(group: String): Int {
		lock.read {
			return groupToEntityModificationCountMap[group] ?: 0
		}
	}

	fun getModificationCount(entity: Entity): Int {
		lock.read {
			return entityToGroupModificationCountpMap[entity] ?: 0
		}
	}

	private fun incrementModificationCount(group: String) {
		groupToEntityModificationCountMap[group] = 1 + (groupToEntityModificationCountMap[group] ?: 0)
	}

	private fun incrementModificationCount(entity: Entity) {
		entityToGroupModificationCountpMap[entity] = 1 + (entityToGroupModificationCountpMap[entity] ?: 0)
	}

	fun isMemberOf(entity: Entity, group: String): Boolean {
		return get(group).contains(entity)
	}

	fun add(entity: Entity, group: String) {
		lock.write {
			var entitiesSet = groupToEntityMap[group]

			if (entitiesSet == null) {
				entitiesSet = HashSet<Entity>()
				groupToEntityMap[group] = entitiesSet
			}

			entitiesSet.add(entity)
			incrementModificationCount(group)

			var groupSet = entityToGroupMap[entity]

			if (groupSet == null) {
				groupSet = HashSet<String>()
				entityToGroupMap[entity] = groupSet
			}

			groupSet.add(group)
			incrementModificationCount(entity)
		}
	}

	fun add(entities: List<Entity>, group: String) {
		lock.write {
			var entitiesSet = groupToEntityMap[group]

			if (entitiesSet == null) {
				entitiesSet = HashSet<Entity>()
				groupToEntityMap[group] = entitiesSet
			}

			entitiesSet.addAll(entities)
			incrementModificationCount(group)

			for (entity in entities) {
				var groupSet = entityToGroupMap[entity]

				if (groupSet == null) {
					groupSet = HashSet<String>()
					entityToGroupMap[entity] = groupSet
				}

				groupSet.add(group)
				incrementModificationCount(entity)
			}
		}
	}

	fun remove(entity: Entity, group: String) {
		lock.write {
			val entitiesSet = groupToEntityMap[group]

			if (entitiesSet == null) {
				return
			}

			entitiesSet.remove(entity)
			incrementModificationCount(group)

			var groupSet = entityToGroupMap[entity]

			if (groupSet != null) {
				groupSet.remove(group)
				incrementModificationCount(entity)
			}
		}
	}

	fun remove(entities: List<Entity>, group: String) {
		lock.write {
			val entitiesSet = groupToEntityMap[group]

			if (entitiesSet == null) {
				return
			}

			entitiesSet.removeAll(entities)
			incrementModificationCount(group)

			for (entity in entities) {
				var groupSet = entityToGroupMap[entity]

				if (groupSet != null) {
					groupSet.remove(group)
					incrementModificationCount(entity)
				}
			}
		}
	}

	fun clear(group: String) {
		lock.write {
			val entitiesSet = groupToEntityMap[group]

			if (entitiesSet != null) {

				for (entity in entitiesSet) {
					var groupSet = entityToGroupMap[entity]

					groupSet!!.remove(group)
					incrementModificationCount(entity)
				}

				entitiesSet.clear()
				incrementModificationCount(group)
			}
		}
	}

	override fun inserted(entity: Entity) {}

	override fun removed(entity: Entity) {
		lock.write {
			var groupSet = entityToGroupMap[entity]

			if (groupSet != null) {

				for (group in groupSet) {
					val entitiesSet = groupToEntityMap[group]

					entitiesSet!!.remove(entity)
					incrementModificationCount(group)
				}

				entityToGroupMap[entity] = null
				entityToGroupModificationCountpMap[entity] = null
			}
		}
	}
}
