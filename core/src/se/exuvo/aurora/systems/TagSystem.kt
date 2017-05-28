package se.exuvo.aurora.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntityListener
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import se.exuvo.aurora.components.TagComponent
import java.util.HashMap

class TagSystem() : EntitySystem() {
    private val tm = ComponentMapper.getFor(TagComponent::class.java)
    private val tagMap = HashMap<String, Entity>()
    private val listener = object : EntityListener {
        override fun entityAdded(entity: Entity?) {
            val tag = tm.get(entity)
            if (tag != null && entity != null) tagMap.put(tag.tag, entity)
        }

        override fun entityRemoved(entity: Entity?) {
            val tag = tm.get(entity)
            if (tag != null) tagMap.remove(tag.tag)
        }
    }

    operator fun get(tag: String): Entity? {
        return tagMap[tag]
    }

    override fun addedToEngine(engine: Engine?) {
        engine?.addEntityListener(Family.one(TagComponent::class.java).get(), listener)
    }

    override fun removedFromEngine(engine: Engine?) {
        tagMap.clear()
        engine?.removeEntityListener(listener)
    }
}
