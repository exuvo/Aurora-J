package se.exuvo.aurora.starsystems.systems

import java.util.Collections
import java.util.HashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import se.exuvo.aurora.utils.DummyReentrantReadWriteLock
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.starsystems.components.EntityReference
import com.artemis.utils.Bag

class GroupSystem(val lock: ReentrantReadWriteLock = DummyReentrantReadWriteLock.INSTANCE) {
	companion object {
//		const val SELECTED = "selected"
		
		@JvmField val EmptyBag = Bag<Any>(0)
		
		@Suppress("UNCHECKED_CAST")
		@JvmField val EmptyEntityBag = EmptyBag as Bag<EntityReference>
		
		@Suppress("UNCHECKED_CAST")
		@JvmField val EmptyGroupBag = EmptyBag as Bag<String>
	}

	private val groupMembershipMap = HashMap<String, HashMap<EntityReference, Int>?>()
	private val groupEntitiesMap = HashMap<String, Bag<EntityReference>?>()
	private val entityGroupsMap = HashMap<EntityReference, HashSet<String>?>()

	operator fun get(group: String): Bag<EntityReference> {
		lock.read {
			return groupEntitiesMap[group] ?: EmptyEntityBag
		}
	}

	operator fun get(entity: EntityReference): Set<String> {
		lock.read {
			return entityGroupsMap[entity] ?: Collections.emptySet()
		}
	}

	fun isMemberOf(entity: EntityReference, group: String): Boolean {
		val membership = groupMembershipMap[group]
		
		if (membership == null) {
			return false
		}
		
		return membership.contains(entity)
	}

	fun add(entity: EntityReference, group: String) {
		lock.read {
			val membership = groupMembershipMap[group]
			
			if (membership == null || !membership.contains(entity)) {
				
				lock.write {
					var membership2 = membership
					var entitiesBag: Bag<EntityReference>
							
					if (membership2 == null) {
						
						membership2 = HashMap<EntityReference, Int>()
						groupMembershipMap[group] = membership2
						
						entitiesBag = Bag<EntityReference>()
						groupEntitiesMap[group] = entitiesBag
						
					} else {
						
						entitiesBag = groupEntitiesMap[group]!!
					}
					
					val index = entitiesBag.size()
					
					membership2.put(entity, index)
					entitiesBag.add(entity)
		
					var groupsBag = entityGroupsMap[entity]
					
					if (groupsBag == null) {
						groupsBag = HashSet<String>()
						entityGroupsMap[entity] = groupsBag
					}
		
					groupsBag.add(group)
				}
			}
		}
	}
	
	fun add(entities: Bag<EntityReference>, group: String) {
		lock.write {
			var membership = groupMembershipMap[group]
			var entitiesBag: Bag<EntityReference>
						
			if (membership == null) {
				
				membership = HashMap<EntityReference, Int>()
				groupMembershipMap[group] = membership
				
				entitiesBag = Bag<EntityReference>()
				groupEntitiesMap[group] = entitiesBag
				
			} else {
				
				entitiesBag = groupEntitiesMap[group]!!
			}
			
			entities.forEachFast{ entity ->

				if (!membership.contains(entity)) {

					val index = entitiesBag.size()
					
					membership.put(entity, index)
					entitiesBag.add(entity)

					var groupsBag = entityGroupsMap[entity]

					if (groupsBag == null) {
						groupsBag = HashSet<String>()
						entityGroupsMap[entity] = groupsBag
					}

					groupsBag.add(group)
				}
			}
		}
	}

	fun add(entities: List<EntityReference>, group: String) {
		lock.write {
			var membership = groupMembershipMap[group]
			var entitiesBag: Bag<EntityReference>
						
			if (membership == null) {
				
				membership = HashMap<EntityReference, Int>()
				groupMembershipMap[group] = membership
				
				entitiesBag = Bag<EntityReference>()
				groupEntitiesMap[group] = entitiesBag
				
			} else {
				
				entitiesBag = groupEntitiesMap[group]!!
			}
			
			entities.forEachFast{ entity ->

				if (!membership.contains(entity)) {

					val index = entitiesBag.size()
					
					membership.put(entity, index)
					entitiesBag.add(entity)

					var groupsBag = entityGroupsMap[entity]

					if (groupsBag == null) {
						groupsBag = HashSet<String>()
						entityGroupsMap[entity] = groupsBag
					}

					groupsBag.add(group)
				}
			}
		}
	}

	fun remove(entity: EntityReference, group: String) {
		lock.read {
			val membership = groupMembershipMap[group]
			
			val index = membership?.get(entity)
			
			if (index != null) {
				
				lock.write {
					
					membership.remove(entity)
					
					val entitiesBag = groupEntitiesMap[group]!!
					
					if (entitiesBag.size() > 1) {
						
						val movedEntity = entitiesBag[entitiesBag.size() - 1]
						entitiesBag.remove(index)
						membership[movedEntity] = index
						
					} else {
						entitiesBag.removeLast()
					}
					
					entityGroupsMap[entity]!!.remove(group)
				}
			}
		}
	}

	fun remove(entities: List<EntityReference>, group: String) {
		lock.read {
			var membership = groupMembershipMap[group]
					
			if (membership == null || membership.isEmpty()) {
				return
			}
			
			lock.write {
				var entitiesBag = groupEntitiesMap[group]!!
				
				entities.forEachFast{ entity ->
					
					val index = membership.get(entity)
			
					if (index != null) {

						membership.remove(entity)
						
						if (entitiesBag.size() > 1) {
							
							val movedEntity = entitiesBag[entitiesBag.size() - 1]
							entitiesBag.remove(index)
							membership[movedEntity] = index
							
						} else {
							entitiesBag.removeLast()
						}
						
						entityGroupsMap[entity]!!.remove(group)
					}
				}
			}
		}
	}

	fun clear(group: String) {
		lock.read {
			var membership = groupMembershipMap[group]
					
			if (membership == null || membership.isEmpty()) {
				return
			}
			
			lock.write {
				membership.clear()
				
				var entitiesBag = groupEntitiesMap[group]!!
				
				entitiesBag.forEachFast{ entity ->
					var groupSet = entityGroupsMap[entity]!!

					groupSet.remove(group)
				}

				entitiesBag.clear()
			}
		}
	}
}
