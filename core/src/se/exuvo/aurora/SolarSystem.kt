package se.exuvo.aurora

import com.badlogic.ashley.core.PooledEngine
import org.apache.log4j.Logger
import se.exuvo.aurora.components.CircleComponent
import se.exuvo.aurora.components.MassComponent
import se.exuvo.aurora.components.NameComponent
import se.exuvo.aurora.components.OrbitComponent
import se.exuvo.aurora.components.PositionComponent
import se.exuvo.aurora.components.RenderComponent
import se.exuvo.aurora.systems.GroupSystem
import se.exuvo.aurora.systems.OrbitSystem
import se.exuvo.aurora.systems.RenderSystem
import se.exuvo.aurora.systems.TagSystem
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
		entity1.add(engine.createComponent(PositionComponent::class.java).apply { position.set(0, 0) })
		entity1.add(engine.createComponent(RenderComponent::class.java))
		entity1.add(engine.createComponent(CircleComponent::class.java).apply { radius = 695700f })
		entity1.add(engine.createComponent(MassComponent::class.java).apply { mass = 1.988e30 })
		entity1.add(engine.createComponent(NameComponent::class.java).apply { name = "Sun" })

		engine.addEntity(entity1)

		val entity2 = engine.createEntity()
		entity2.add(engine.createComponent(PositionComponent::class.java))
		entity2.add(engine.createComponent(RenderComponent::class.java))
		entity2.add(engine.createComponent(CircleComponent::class.java).apply { radius = 6371f })
		entity2.add(engine.createComponent(NameComponent::class.java).apply { name = "Earth" })
		entity2.add(engine.createComponent(MassComponent::class.java).apply { mass = 5.972e24 })
		entity2.add(engine.createComponent(OrbitComponent::class.java).apply { parent = entity1; a_semiMajorAxis = 1f; e_eccentricity = 0.5f; w_argumentOfPeriapsis = -45f })

		engine.addEntity(entity2)

		val entity3 = engine.createEntity()
		entity3.add(engine.createComponent(PositionComponent::class.java))
		entity3.add(engine.createComponent(RenderComponent::class.java))
		entity3.add(engine.createComponent(CircleComponent::class.java).apply{ radius = 1737f})
		entity3.add(engine.createComponent(NameComponent::class.java).apply { name = "Moon" })
		entity3.add(engine.createComponent(OrbitComponent::class.java).apply { parent = entity2; a_semiMajorAxis = (384400.0 / OrbitSystem.AU).toFloat(); e_eccentricity = 0.2f; M_meanAnomaly = 30f })

		engine.addEntity(entity3)
		
		val entity4 = engine.createEntity()
		entity4.add(engine.createComponent(PositionComponent::class.java))
		entity4.add(engine.createComponent(RenderComponent::class.java))
		entity4.add(engine.createComponent(CircleComponent::class.java).apply{ radius = 10f})
		entity4.add(engine.createComponent(NameComponent::class.java).apply { name = "Ship" })

		engine.addEntity(entity4)
	}

	fun update(deltaGameTime: Int) {

		//TODO use readlock during update

		lock.writeLock().lock()
		try {
			engine.update(deltaGameTime.toFloat())
			
			//TODO send changes over network
		} finally {
			lock.writeLock().unlock()
		}
	}
}