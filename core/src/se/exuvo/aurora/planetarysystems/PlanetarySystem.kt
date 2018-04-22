package se.exuvo.aurora.planetarysystems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntityListener
import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Pool.Poolable
import org.apache.log4j.Logger
import se.exuvo.aurora.Assets
import se.exuvo.aurora.galactic.Empire
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.PassiveSensor
import se.exuvo.aurora.history.History
import se.exuvo.aurora.planetarysystems.components.CircleComponent
import se.exuvo.aurora.planetarysystems.components.EmissionsComponent
import se.exuvo.aurora.planetarysystems.components.EntityUUID
import se.exuvo.aurora.planetarysystems.components.GalacticPositionComponent
import se.exuvo.aurora.planetarysystems.components.MassComponent
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.planetarysystems.components.OrbitComponent
import se.exuvo.aurora.planetarysystems.components.PassiveSensorsComponent
import se.exuvo.aurora.planetarysystems.components.PlanetarySystemComponent
import se.exuvo.aurora.planetarysystems.components.RenderComponent
import se.exuvo.aurora.planetarysystems.components.SolarIrradianceComponent
import se.exuvo.aurora.planetarysystems.components.Spectrum
import se.exuvo.aurora.planetarysystems.components.StrategicIconComponent
import se.exuvo.aurora.planetarysystems.components.SunComponent
import se.exuvo.aurora.planetarysystems.components.TagComponent
import se.exuvo.aurora.planetarysystems.components.ThrustComponent
import se.exuvo.aurora.planetarysystems.components.TimedMovementComponent
import se.exuvo.aurora.planetarysystems.components.TintComponent
import se.exuvo.aurora.planetarysystems.components.UUIDComponent
import se.exuvo.aurora.planetarysystems.systems.GroupSystem
import se.exuvo.aurora.planetarysystems.systems.MovementSystem
import se.exuvo.aurora.planetarysystems.systems.OrbitSystem
import se.exuvo.aurora.planetarysystems.systems.PassiveSensorSystem
import se.exuvo.aurora.planetarysystems.systems.RenderSystem
import se.exuvo.aurora.planetarysystems.systems.ShipSystem
import se.exuvo.aurora.planetarysystems.systems.SolarIrradianceSystem
import se.exuvo.aurora.planetarysystems.systems.TagSystem
import se.exuvo.aurora.utils.DummyReentrantReadWriteLock
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Vector2L
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write
import se.exuvo.aurora.planetarysystems.systems.PowerSystem
import se.exuvo.aurora.planetarysystems.components.ShipComponent
import se.exuvo.aurora.galactic.ShipClass
import se.exuvo.aurora.galactic.SolarPanel
import se.exuvo.aurora.galactic.Resource
import se.exuvo.aurora.galactic.Reactor
import se.exuvo.aurora.galactic.NuclearContainerPart
import se.exuvo.aurora.galactic.FissionReactor
import se.exuvo.aurora.galactic.Battery
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.planetarysystems.components.PowerScheme

class PlanetarySystem(val initialName: String, val initialPosition: Vector2L) : Entity(), EntityListener {
	companion object {
		val planetarySystemIDGenerator = AtomicInteger()
		val entityIDGenerator = AtomicLong()
	}

	val id = planetarySystemIDGenerator.getAndIncrement()
	val log = Logger.getLogger(this.javaClass)
	val lock = ReentrantReadWriteLock()
	val engine = PooledEngine()

	private val solarSystemMapper = ComponentMapper.getFor(PlanetarySystemComponent::class.java)
	private val uuidMapper = ComponentMapper.getFor(UUIDComponent::class.java)
	private val galaxy by lazy {GameServices[Galaxy::class.java]}
	private val history by lazy {GameServices[History::class.java]}

	/* All missiles and other short lived entites should be Pooled using createEntityPooled() and engine.createComponent()
   * Everything else which should either be capable of moving between systems or is static should be created normally with createEntity() and Component()
   * Destroy them with destroyEntity()
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
		engine.addSystem(MovementSystem())
		engine.addSystem(SolarIrradianceSystem())
		engine.addSystem(ShipSystem())
		engine.addSystem(PassiveSensorSystem())
		engine.addSystem(PowerSystem())
		engine.addSystem(RenderSystem())

		val empire1 = galaxy.getEmpire(1)
		
		val entity1 = createEntity(Empire.GAIA)
		entity1.add(TimedMovementComponent().apply { previous.value.position.set(0, 0) })
		entity1.add(RenderComponent())
		entity1.add(CircleComponent().apply { radius = 695700f })
		entity1.add(MassComponent().apply { mass = 1.988e30 })
		entity1.add(NameComponent().apply { name = "Sun" })
		entity1.add(TagComponent().apply { tag = TagSystem.SUN })
		entity1.add(SunComponent(1361))
		entity1.add(TintComponent(Color.YELLOW))
		entity1.add(StrategicIconComponent(Assets.textures.findRegion("strategic/sun")))

		engine.addEntity(entity1)

		val entity2 = createEntity(empire1)
		entity2.add(TimedMovementComponent())
		entity2.add(RenderComponent())
		entity2.add(CircleComponent().apply { radius = 6371f })
		entity2.add(NameComponent().apply { name = "Earth" })
		entity2.add(MassComponent().apply { mass = 5.972e24 })
		entity2.add(OrbitComponent().apply { parent = entity1; a_semiMajorAxis = 1f; e_eccentricity = 0f; w_argumentOfPeriapsis = -45f })
		entity2.add(TintComponent(Color.GREEN))
		entity2.add(StrategicIconComponent(Assets.textures.findRegion("strategic/world")))
		entity2.add(EmissionsComponent(mapOf(Spectrum.Electromagnetic to 1e10, Spectrum.Thermal to 1e10)))

		engine.addEntity(entity2)

		val entity3 = createEntity(empire1)
		entity3.add(TimedMovementComponent())
		entity3.add(RenderComponent().apply { zOrder = 0.4f })
		entity3.add(CircleComponent().apply { radius = 1737f })
		entity3.add(NameComponent().apply { name = "Moon" })
		entity3.add(OrbitComponent().apply { parent = entity2; a_semiMajorAxis = (384400.0 / OrbitSystem.AU).toFloat(); e_eccentricity = 0.2f; M_meanAnomaly = 30f })
		entity3.add(TintComponent(Color.GRAY))
		entity3.add(StrategicIconComponent(Assets.textures.findRegion("strategic/moon")))
		entity3.add(EmissionsComponent(mapOf(Spectrum.Electromagnetic to 5e9, Spectrum.Thermal to 5e9)))

		engine.addEntity(entity3)

		val entity4 = createEntity(empire1)
		entity4.add(TimedMovementComponent().apply { previous.value.position.set((OrbitSystem.AU * 1000L * 1L).toLong(), 0).rotate(45f) }) //; previous.value.velocity.set(-1000000f, 0f)
		entity4.add(RenderComponent())
		entity4.add(SolarIrradianceComponent())
		entity4.add(CircleComponent().apply { radius = 10f })
		entity4.add(NameComponent().apply { name = "Ship" })
		entity4.add(MassComponent().apply { mass = 1000.0 })
		entity4.add(ThrustComponent().apply { thrust = 10f * 9.82f * 1000f })
//		entity4.add(MoveToEntityComponent(entity1, ApproachType.BRACHISTOCHRONE))
		entity4.add(TintComponent(Color.RED))
		val sensor1 = PassiveSensor(300000, Spectrum.Electromagnetic, 1e-7, 14, OrbitSystem.AU * 0.3, 20, 0.97, 1);
		sensor1.name = "EM 1e-4"
		sensor1.designDay = 1
		val sensor2 = PassiveSensor(800000, Spectrum.Thermal, 1e-8, 8, OrbitSystem.AU * 1, 0, 0.9, 5);
		sensor2.name = "TH 1e-10"
		sensor2.designDay = 1
		entity4.add(StrategicIconComponent(Assets.textures.findRegion("strategic/ship")))
		entity4.add(EmissionsComponent(mapOf(Spectrum.Electromagnetic to 1e10, Spectrum.Thermal to 1e10)))
		
		val shipClass = ShipClass()
		shipClass.name = "Elodin"
		shipClass.designDay = 1
//		shipClass.powerScheme = PowerScheme.SOLAR_REACTOR_BATTERY
		
		shipClass.parts.add(sensor1)
		shipClass.parts.add(sensor2)
		
		val solarPanel = SolarPanel()
		solarPanel.name = "Solar Panel"
		solarPanel.cost[Resource.SEMICONDUCTORS] = 250
		shipClass.parts.add(solarPanel)
		
		val reactor = FissionReactor(1 * Units.MEGAWATT)
		reactor.name = "Nuclear Reactor"
		reactor.cost[Resource.GENERIC] = 1000
		shipClass.parts.add(reactor)
//		println("Reactor fuel consumption ${reactor.fuelConsumption} kg / ${reactor.fuelTime} s")
		
		val nuclearStorage = NuclearContainerPart(10000)
		nuclearStorage.name = "Nuclear Cargo"
		nuclearStorage.cost[Resource.GENERIC] = 100
		shipClass.parts.add(nuclearStorage)
		
		val battery = Battery(200 * Units.KILOWATT, 500 * Units.KILOWATT, 0.8f, 100 * Units.GIGAWATT)
		battery.name = "Battery"
		shipClass.parts.add(battery)
		
		val shipComponent = ShipComponent(shipClass, galaxy.time)
		shipComponent.addCargo(Resource.NUCLEAR_FISSION, 10) 
		entity4.add(shipComponent)

		engine.addEntity(entity4)
	}

	fun createEntity(empire: Empire): Entity {
		val entity = Entity()
		entity.add(PlanetarySystemComponent(this))
		entity.add(UUIDComponent(EntityUUID(id, empire.id, getNewEnitityID())))
		
		history.entityCreated(entity)
		return entity
	}

	fun createEntityPooled(empire: Empire): Entity? {
		val entity = engine.createEntity()
		entity.add(engine.createComponent(PlanetarySystemComponent::class.java).apply { system = this@PlanetarySystem })
		entity.add(engine.createComponent(UUIDComponent::class.java).apply { EntityUUID(id, empire.id, getNewEnitityID()) })
		
		history.entityCreated(entity)
		return entity
	}
	
	fun destroyEntity(entity: Entity) {
		history.entityDestroyed(entity)
		engine.removeEntity(entity)
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

		lock.write {
			engine.update(deltaGameTime.toFloat())

			//TODO send changes over network
		}
	}
	
	private fun getNewEnitityID(): Long {
		return entityIDGenerator.incrementAndGet();
	}
}