package se.exuvo.aurora.planetarysystems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntityListener
import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Pool.Poolable
import org.apache.log4j.Logger
import se.exuvo.aurora.Assets
import se.exuvo.aurora.planetarysystems.components.ApproachType
import se.exuvo.aurora.planetarysystems.components.CircleComponent
import se.exuvo.aurora.planetarysystems.components.GalacticPositionComponent
import se.exuvo.aurora.planetarysystems.components.MassComponent
import se.exuvo.aurora.planetarysystems.components.MoveToEntityComponent
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.planetarysystems.components.OrbitComponent
import se.exuvo.aurora.planetarysystems.components.PlanetarySystemComponent
import se.exuvo.aurora.planetarysystems.components.PositionComponent
import se.exuvo.aurora.planetarysystems.components.RenderComponent
import se.exuvo.aurora.planetarysystems.components.SolarIrradianceComponent
import se.exuvo.aurora.planetarysystems.components.StrategicIconComponent
import se.exuvo.aurora.planetarysystems.components.SunComponent
import se.exuvo.aurora.planetarysystems.components.TagComponent
import se.exuvo.aurora.planetarysystems.components.ThrustComponent
import se.exuvo.aurora.planetarysystems.components.TimedMovementComponent
import se.exuvo.aurora.planetarysystems.components.TintComponent
import se.exuvo.aurora.planetarysystems.components.VelocityComponent
import se.exuvo.aurora.planetarysystems.systems.GroupSystem
import se.exuvo.aurora.planetarysystems.systems.MovementSystem
import se.exuvo.aurora.planetarysystems.systems.OrbitSystem
import se.exuvo.aurora.planetarysystems.systems.RenderSystem
import se.exuvo.aurora.planetarysystems.systems.ShipSystem
import se.exuvo.aurora.planetarysystems.systems.SolarIrradianceSystem
import se.exuvo.aurora.planetarysystems.systems.TagSystem
import se.exuvo.aurora.utils.DummyReentrantReadWriteLock
import se.exuvo.aurora.utils.Vector2L
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

class PlanetarySystem(val initialName: String, val initialPosition: Vector2L) : Entity(), EntityListener {

	val log = Logger.getLogger(this.javaClass)
	val lock = ReentrantReadWriteLock()
	val engine = PooledEngine()

	private val solarSystemMapper = ComponentMapper.getFor(PlanetarySystemComponent::class.java)

	/* All missiles and other short lived entites should be Pooled using engine.createEntity() and engine.createComponent()
   * Everything else which should either be capable of moving between systems or is static should be created normally with Entity() and Component() 
	 */
	fun init() {
		add(GalacticPositionComponent(initialPosition))
		add(RenderComponent())
		add(NameComponent(initialName))
		add(StrategicIconComponent(Assets.textures.findRegion("galactic/system")))
		
		engine.addEntityListener(this)

		engine.addSystem(TagSystem())
		engine.addSystem(GroupSystem(DummyReentrantReadWriteLock.INSTANCE))
		engine.addSystem(OrbitSystem())
		engine.addSystem(SolarIrradianceSystem())
		engine.addSystem(ShipSystem())
		engine.addSystem(MovementSystem())
		engine.addSystem(RenderSystem())

		val entity1 = Entity()
		entity1.add(PositionComponent().apply { position.set(0, 0) })
		entity1.add(RenderComponent())
		entity1.add(CircleComponent().apply { radius = 695700f })
		entity1.add(MassComponent().apply { mass = 1.988e30 })
		entity1.add(NameComponent().apply { name = "Sun" })
		entity1.add(TagComponent().apply { tag = TagSystem.SUN })
		entity1.add(SunComponent(1361))
		entity1.add(TintComponent(Color.YELLOW))
		entity1.add(StrategicIconComponent(Assets.textures.findRegion("strategic/sun")))

		engine.addEntity(entity1)

		val entity2 = Entity()
		entity2.add(PositionComponent())
		entity2.add(RenderComponent())
		entity2.add(CircleComponent().apply { radius = 6371f })
		entity2.add(NameComponent().apply { name = "Earth" })
		entity2.add(MassComponent().apply { mass = 5.972e24 })
		entity2.add(OrbitComponent().apply { parent = entity1; a_semiMajorAxis = 1f; e_eccentricity = 0f; w_argumentOfPeriapsis = -45f })
		entity2.add(TintComponent(Color.GREEN))
		entity2.add(StrategicIconComponent(Assets.textures.findRegion("strategic/world")))

		engine.addEntity(entity2)

		val entity3 = Entity()
		entity3.add(PositionComponent())
		entity3.add(RenderComponent().apply { zOrder = 0.4f })
		entity3.add(CircleComponent().apply { radius = 1737f })
		entity3.add(NameComponent().apply { name = "Moon" })
		entity3.add(OrbitComponent().apply { parent = entity2; a_semiMajorAxis = (384400.0 / OrbitSystem.AU).toFloat(); e_eccentricity = 0.2f; M_meanAnomaly = 30f })
		entity3.add(TintComponent(Color.GRAY))
		entity3.add(StrategicIconComponent(Assets.textures.findRegion("strategic/moon")))

		engine.addEntity(entity3)

		val entity4 = Entity()
		entity4.add(PositionComponent().apply { position.set((OrbitSystem.AU * 1000L * 1L).toLong(), 0).rotate(45f) })
		entity4.add(RenderComponent())
		entity4.add(SolarIrradianceComponent())
		entity4.add(CircleComponent().apply { radius = 10f })
		entity4.add(NameComponent().apply { name = "Ship" })
		entity4.add(MassComponent().apply { mass = 1000.0 })
		entity4.add(ThrustComponent().apply { thrust = 10f * 9.82f * 1000f })
		entity4.add(VelocityComponent().apply { velocity.set(-1000000f, 0f) })
		entity4.add(MoveToEntityComponent(entity1, ApproachType.BRACHISTOCHRONE))
		entity4.add(TintComponent(Color.RED))
		entity4.add(StrategicIconComponent(Assets.textures.findRegion("strategic/ship")))
		entity4.add(TimedMovementComponent(Vector2L(entity4.getComponent(PositionComponent::class.java).position), 0))

		engine.addEntity(entity4)
	}

	override fun entityAdded(entity: Entity) {
		if (!solarSystemMapper.has(entity)) {
			if (entity is Poolable) {
				entity.add(engine.createComponent(PlanetarySystemComponent::class.java).apply { system = this@PlanetarySystem })
			} else {
				entity.add(PlanetarySystemComponent(this))
			}
		} else {
			val solarSystemComponent = solarSystemMapper.get(entity)
			solarSystemComponent.system = this
		}
	}

	override fun entityRemoved(entity: Entity) {
		val solarSystemComponent = solarSystemMapper.get(entity)
		solarSystemComponent.system = null
	}

	fun update(deltaGameTime: Int) {

		//TODO use readlock during update

		lock.write {
			engine.update(deltaGameTime.toFloat())

			//TODO send changes over network
		}
	}
}