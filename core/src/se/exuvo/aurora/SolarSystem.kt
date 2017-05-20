package se.exuvo.aurora

import com.badlogic.ashley.core.PooledEngine
import com.thedeadpixelsociety.ld34.components.OrbitComponent
import com.thedeadpixelsociety.ld34.components.PositionComponent
import com.thedeadpixelsociety.ld34.components.RenderComponent
import com.thedeadpixelsociety.ld34.components.LineComponent
import com.thedeadpixelsociety.ld34.components.CircleComponent
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
		entity1.add(engine.createComponent(PositionComponent::class.java).apply { position.set(0f, 0f) })
		entity1.add(engine.createComponent(RenderComponent::class.java))
		entity1.add(engine.createComponent(CircleComponent::class.java).apply { radius = 5f })
//		entity1.add(engine.createComponent(LineComponent::class.java).apply { x = 100f; y = 100f })
		entity1.add(engine.createComponent(TextComponent::class.java).apply { text = "Sun" })

		engine.addEntity(entity1)

		val entity2 = engine.createEntity()
		entity2.add(engine.createComponent(PositionComponent::class.java))
		entity2.add(engine.createComponent(RenderComponent::class.java))
		entity2.add(engine.createComponent(CircleComponent::class.java).apply { radius = 2f })
		entity2.add(engine.createComponent(TextComponent::class.java).apply { text = "1" })
		entity2.add(engine.createComponent(OrbitComponent::class.java).apply { parent = entity1; a_semiMajorAxis = 2f; e_eccentricity = 0.5f; w_argumentOfPeriapsis = -45f })

		engine.addEntity(entity2)

		val entity3 = engine.createEntity()
		entity3.add(engine.createComponent(PositionComponent::class.java))
		entity3.add(engine.createComponent(RenderComponent::class.java))
		entity3.add(engine.createComponent(CircleComponent::class.java))
		entity3.add(engine.createComponent(TextComponent::class.java).apply { text = "2" })
		entity3.add(engine.createComponent(OrbitComponent::class.java).apply { parent = entity2; a_semiMajorAxis = 0.5f; e_eccentricity = 0.2f })

		engine.addEntity(entity3)

		val entity4 = engine.createEntity()
		entity4.add(engine.createComponent(PositionComponent::class.java))
		entity4.add(engine.createComponent(RenderComponent::class.java))
		entity4.add(engine.createComponent(CircleComponent::class.java))
		entity4.add(engine.createComponent(OrbitComponent::class.java).apply { parent = entity3; a_semiMajorAxis = 0.2f })

		engine.addEntity(entity4)
	}

	fun update(deltaGameTime: Int) {

		//TODO use readlock during update

		lock.writeLock().lock()
		try {
			engine.update(deltaGameTime.toFloat())
		} finally {
			lock.writeLock().unlock()
		}
	}

	fun commitChanges() {
		lock.writeLock().lock()
		//TODO commit changes
		lock.writeLock().unlock()

		//TODO send changes over network
	}
}