package se.exuvo.aurora

import com.badlogic.ashley.core.PooledEngine
import com.thedeadpixelsociety.ld34.components.BoundsComponent
import com.thedeadpixelsociety.ld34.components.PositionComponent
import com.thedeadpixelsociety.ld34.components.RenderComponent
import com.thedeadpixelsociety.ld34.components.RenderType
import com.thedeadpixelsociety.ld34.components.TextComponent
import com.thedeadpixelsociety.ld34.systems.GroupSystem
import com.thedeadpixelsociety.ld34.systems.RenderSystem
import com.thedeadpixelsociety.ld34.systems.TagSystem
import org.apache.log4j.Logger
import se.exuvo.aurora.systems.OrbitSystem
import java.util.concurrent.locks.ReentrantReadWriteLock

class SolarSystem {
	
	val log = Logger.getLogger(this.javaClass)

	val lock = ReentrantReadWriteLock()
	val engine = PooledEngine()

	fun init() {
		engine.addSystem(TagSystem())
		engine.addSystem(GroupSystem())
		engine.addSystem(OrbitSystem())
		engine.addSystem(RenderSystem())

		val entity1 = engine.createEntity()
		entity1.add(engine.createComponent(PositionComponent::class.java).apply { position.set(1f, 1f) })
		entity1.add(engine.createComponent(RenderComponent::class.java).apply { type = RenderType.CIRCLE })
		entity1.add(engine.createComponent(BoundsComponent::class.java))
		entity1.add(engine.createComponent(TextComponent::class.java).apply { text = "TESt" })
		
		engine.addEntity(entity1)
	}
	
	fun update(deltaGameTime: Int) {
		
		//TODO use readlock during update
		
		lock.writeLock().lock()
		engine.update(deltaGameTime.toFloat())
		lock.writeLock().unlock()
		
	}
	
	fun commitChanges() {
		lock.writeLock().lock()
		//TODO commit changes
		lock.writeLock().unlock()
		
		//TODO send changes over network
	}
}