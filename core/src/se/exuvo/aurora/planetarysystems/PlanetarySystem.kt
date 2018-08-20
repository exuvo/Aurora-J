package se.exuvo.aurora.planetarysystems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.EntitySubscription
import com.artemis.EntitySubscription.SubscriptionListener
import com.artemis.World
import com.artemis.WorldConfiguration
import com.artemis.annotations.EntityId
import com.artemis.utils.IntBag
import com.badlogic.gdx.graphics.Color
import org.apache.log4j.Logger
import se.exuvo.aurora.Assets
import se.exuvo.aurora.galactic.AmmoContainerPart
import se.exuvo.aurora.galactic.Battery
import se.exuvo.aurora.galactic.BeamWavelength
import se.exuvo.aurora.galactic.BeamWeapon
import se.exuvo.aurora.galactic.Empire
import se.exuvo.aurora.galactic.FissionReactor
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.MissileLauncher
import se.exuvo.aurora.galactic.MunitionClass
import se.exuvo.aurora.galactic.NuclearContainerPart
import se.exuvo.aurora.galactic.PartRef
import se.exuvo.aurora.galactic.PassiveSensor
import se.exuvo.aurora.galactic.Railgun
import se.exuvo.aurora.galactic.Resource
import se.exuvo.aurora.galactic.ShipClass
import se.exuvo.aurora.galactic.SolarPanel
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.galactic.WeaponPart
import se.exuvo.aurora.history.History
import se.exuvo.aurora.planetarysystems.components.CircleComponent
import se.exuvo.aurora.planetarysystems.components.EmissionsComponent
import se.exuvo.aurora.planetarysystems.components.EntityUUID
import se.exuvo.aurora.planetarysystems.components.GalacticPositionComponent
import se.exuvo.aurora.planetarysystems.components.MassComponent
import se.exuvo.aurora.planetarysystems.components.MoveToEntityComponent
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.planetarysystems.components.OrbitComponent
import se.exuvo.aurora.planetarysystems.components.PlanetarySystemComponent
import se.exuvo.aurora.planetarysystems.components.RenderComponent
import se.exuvo.aurora.planetarysystems.components.ShipComponent
import se.exuvo.aurora.planetarysystems.components.SolarIrradianceComponent
import se.exuvo.aurora.planetarysystems.components.Spectrum
import se.exuvo.aurora.planetarysystems.components.StrategicIconComponent
import se.exuvo.aurora.planetarysystems.components.SunComponent
import se.exuvo.aurora.planetarysystems.components.ThrustComponent
import se.exuvo.aurora.planetarysystems.components.TimedMovementComponent
import se.exuvo.aurora.planetarysystems.components.TintComponent
import se.exuvo.aurora.planetarysystems.components.UUIDComponent
import se.exuvo.aurora.planetarysystems.systems.OrbitSystem
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.utils.forEach
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write
import se.exuvo.aurora.galactic.ElectricalThruster
import se.exuvo.aurora.galactic.FueledThruster
import se.exuvo.aurora.galactic.FuelContainerPart

class PlanetarySystem(val initialName: String, worldConfig: WorldConfiguration, val initialPosition: Vector2L) : EntitySubscription.SubscriptionListener {
	companion object {
		val planetarySystemIDGenerator = AtomicInteger()
	}

	val log = Logger.getLogger(this.javaClass)
	val entityUIDGenerator = AtomicLong()

	private val galaxy = GameServices[Galaxy::class]
	private val history = GameServices[History::class]
	val sid = planetarySystemIDGenerator.getAndIncrement()
	@EntityId
	val galacticEntityID: Int = galaxy.world.create()

	val lock = ReentrantReadWriteLock()
	val world: World = World(worldConfig)

	lateinit private var solarSystemMapper: ComponentMapper<PlanetarySystemComponent>
	lateinit private var uuidMapper: ComponentMapper<UUIDComponent>
	lateinit private var timedMovementMapper: ComponentMapper<TimedMovementComponent>
	lateinit private var renderMapper: ComponentMapper<RenderComponent>
	lateinit private var circleMapper: ComponentMapper<CircleComponent>
	lateinit private var massMapper: ComponentMapper<MassComponent>
	lateinit private var nameMapper: ComponentMapper<NameComponent>
	lateinit private var orbitMapper: ComponentMapper<OrbitComponent>
	lateinit private var sunMapper: ComponentMapper<SunComponent>
	lateinit private var solarIrradianceMapper: ComponentMapper<SolarIrradianceComponent>
	lateinit private var tintMapper: ComponentMapper<TintComponent>
	lateinit private var strategicIconMapper: ComponentMapper<StrategicIconComponent>
	lateinit private var emissionsMapper: ComponentMapper<EmissionsComponent>
	lateinit private var moveToEntityMapper: ComponentMapper<MoveToEntityComponent>
	lateinit private var shipMapper: ComponentMapper<ShipComponent>

	init {
		galaxy.world.getMapper(GalacticPositionComponent::class.java).create(galacticEntityID).set(initialPosition)
		galaxy.world.getMapper(RenderComponent::class.java).create(galacticEntityID)
		galaxy.world.getMapper(NameComponent::class.java).create(galacticEntityID).set(initialName)
		galaxy.world.getMapper(StrategicIconComponent::class.java).create(galacticEntityID).set(Assets.textures.findRegion("galactic/system"))

		world.getAspectSubscriptionManager().get(Aspect.all()).addSubscriptionListener(this)
		world.inject(this)
		
		world.getAspectSubscriptionManager().get(Aspect.all()).addSubscriptionListener(object: SubscriptionListener {
			override fun inserted(entityIDs: IntBag) {
				entityIDs.forEach { entityID ->
					galaxy.entityAdded(world, entityID)
				}
			}

			override fun removed(entityIDs: IntBag) {
				entityIDs.forEach { entityID ->
					galaxy.entityRemoved(world, entityID)
				}
			}
		})
	}

	fun init() {
		val empire1 = galaxy.getEmpire(1)

		val entity1 = createEntity(Empire.GAIA)
		timedMovementMapper.create(entity1).set(0, 0, 0f, 0f, 0)
		renderMapper.create(entity1)
		circleMapper.create(entity1).set(radius = 695700f)
		massMapper.create(entity1).set( mass = 1.988e30)
		nameMapper.create(entity1).set( name = "Sun")
		sunMapper.create(entity1).set(solarConstant = 1361)
		tintMapper.create(entity1).set(Color.YELLOW)
		strategicIconMapper.create(entity1).set(Assets.textures.findRegion("strategic/sun"))

		val entity2 = createEntity(empire1)
		timedMovementMapper.create(entity2)
		renderMapper.create(entity2)
		circleMapper.create(entity2).set( radius = 6371f)
		nameMapper.create(entity2).set( name = "Earth")
		massMapper.create(entity2).set( mass = 5.972e24)
		orbitMapper.create(entity2).set(parent = entity1, a_semiMajorAxis = 1f, e_eccentricity = 0f, w_argumentOfPeriapsis = -45f, M_meanAnomaly = 0f)
		tintMapper.create(entity2).set(Color.GREEN)
		strategicIconMapper.create(entity2).set(Assets.textures.findRegion("strategic/world"))
		emissionsMapper.create(entity2).set(mapOf(Spectrum.Electromagnetic to 1e10, Spectrum.Thermal to 1e10))

		val entity3 = createEntity(empire1)
		timedMovementMapper.create(entity3)
		renderMapper.create(entity3)
		circleMapper.create(entity3).set( radius = 1737f)
		nameMapper.create(entity3).set( name = "Moon")
		orbitMapper.create(entity3).set(parent = entity2, a_semiMajorAxis = (384400.0 / OrbitSystem.AU).toFloat(), e_eccentricity = 0.2f, w_argumentOfPeriapsis = 0f, M_meanAnomaly = 30f)
		tintMapper.create(entity3).set(Color.GRAY)
		strategicIconMapper.create(entity3).set(Assets.textures.findRegion("strategic/moon"))
		emissionsMapper.create(entity3).set(mapOf(Spectrum.Electromagnetic to 5e9, Spectrum.Thermal to 5e9))

		val entity4 = createEntity(empire1)
		timedMovementMapper.create(entity4).apply{previous.value.position.set((OrbitSystem.AU * 1000L * 1L).toLong(), 0).rotate(45f)} //; previous.value.velocity.set(-1000000f, 0f)
		renderMapper.create(entity4)
		solarIrradianceMapper.create(entity4)
		circleMapper.create(entity4).set( radius = 10f)
		nameMapper.create(entity4).set( name = "Ship")
//		moveToEntityMapper.create(entity4).set(world.getEntity(entity1), ApproachType.BRACHISTOCHRONE))
		tintMapper.create(entity4).set(Color.RED)
		val sensor1 = PassiveSensor(300000, Spectrum.Electromagnetic, 1e-7, 14, OrbitSystem.AU * 0.3, 20, 0.97, 1);
		sensor1.name = "EM 1e-4"
		sensor1.designDay = 1
		val sensor2 = PassiveSensor(800000, Spectrum.Thermal, 1e-8, 8, OrbitSystem.AU * 1, 0, 0.9, 5);
		sensor2.name = "TH 1e-10"
		sensor2.designDay = 1
		strategicIconMapper.create(entity4).set(Assets.textures.findRegion("strategic/ship"))
		emissionsMapper.create(entity4).set(mapOf(Spectrum.Electromagnetic to 1e10, Spectrum.Thermal to 1e10))

		val shipClass = ShipClass()
		shipClass.name = "Elodin"
		shipClass.designDay = 1
//		shipClass.powerScheme = PowerScheme.SOLAR_REACTOR_BATTERY

		shipClass.addPart(sensor1)
		shipClass.addPart(sensor2)

		val solarPanel = SolarPanel()
		solarPanel.name = "Solar Panel"
		solarPanel.cost[Resource.SEMICONDUCTORS] = 300
		shipClass.addPart(solarPanel)

		val reactor = FissionReactor(5 * Units.MEGAWATT)
		reactor.name = "Nuclear Reactor"
		reactor.cost[Resource.GENERIC] = 1000
		shipClass.addPart(reactor)
//		println("Reactor fuel consumption ${reactor.fuelConsumption} kg / ${reactor.fuelTime} s")

		val nuclearStorage = NuclearContainerPart(10000)
		nuclearStorage.name = "Nuclear Cargo"
		nuclearStorage.cost[Resource.GENERIC] = 100
		shipClass.addPart(nuclearStorage)

		val ammoStorage = AmmoContainerPart(1000000)
		ammoStorage.name = "Munitions Cargo"
		ammoStorage.cost[Resource.GENERIC] = 100
		shipClass.addPart(ammoStorage)
		
		val fuelStorage = FuelContainerPart(10000000)
		fuelStorage.name = "Fuel Cargo"
		fuelStorage.cost[Resource.GENERIC] = 100
		shipClass.addPart(fuelStorage)

		val battery = Battery(200 * Units.KILOWATT, 500 * Units.KILOWATT, 0.8f, 100 * Units.GIGAWATT)
		battery.name = "Battery"
		shipClass.addPart(battery)

		val targetingComputer = TargetingComputer(2, 10, 0f, 10 * Units.KILOWATT)
		targetingComputer.name = "TC 2-10"
		shipClass.addPart(targetingComputer)

		val dummyMass1 = Battery(10 * Units.KILOWATT, 50 * Units.KILOWATT, 0.8f, 1 * Units.GIGAWATT)
		dummyMass1.cost[Resource.GENERIC] = 10

		val dummyMass2 = Battery(10 * Units.KILOWATT, 50 * Units.KILOWATT, 0.8f, 1 * Units.GIGAWATT)
		dummyMass2.cost[Resource.GENERIC] = 100

		val sabot = MunitionClass(Resource.SABOTS)
		sabot.name = "A sabot"
		sabot.addPart(dummyMass1)

		val missile = MunitionClass(Resource.MISSILES)
		missile.name = "A missile"
		missile.addPart(dummyMass2)

		val railgun = Railgun(2 * Units.MEGAWATT, 7, 5 * Units.MEGAWATT, 5)
		shipClass.addPart(railgun)
		val railgunRef = shipClass[Railgun::class][0]
		shipClass.preferredMunitions[railgunRef] = sabot

		val missileLauncher = MissileLauncher(200 * Units.KILOWATT, 13, 3, 10)
		shipClass.addPart(missileLauncher)
		val missileLauncherRef = shipClass[MissileLauncher::class][0]
		shipClass.preferredMunitions[missileLauncherRef] = missile

		val beam = BeamWeapon(1 * Units.MEGAWATT, BeamWavelength.Microwaves, 0.0, 10 * Units.MEGAWATT)
		shipClass.addPart(beam)

		val tcRef: PartRef<TargetingComputer> = shipClass[TargetingComputer::class][0]
		shipClass.defaultWeaponAssignments[tcRef] = shipClass.getPartRefs().filter({ it.part is WeaponPart })
		
		val ionThruster = ElectricalThruster(1f * 9.82f * 1000f, 1 * Units.MEGAWATT)
		shipClass.addPart(ionThruster)
		
		val chemicalThruster = FueledThruster(10f * 9.82f * 1000f, 1)
		shipClass.addPart(chemicalThruster)

		val shipComponent = shipMapper.create(entity4).set(shipClass, galaxy.time)
		shipComponent.addCargo(Resource.NUCLEAR_FISSION, 100)
		shipComponent.addCargo(Resource.ROCKET_FUEL, 10000)

		if (!shipComponent.addCargo(sabot, 10)) {
			println("Failed to add sabots")
		}

		if (!shipComponent.addCargo(missile, 10)) {
			println("Failed to add missiles")
		}
	}

	fun createEntity(empire: Empire): Int {
		val entityID = world.create()
		solarSystemMapper.create(entityID).set(this)
		uuidMapper.create(entityID).set(EntityUUID(sid, empire.id, getNewEnitityID()))

		history.entityCreated(entityID, world)
		return entityID
	}

	fun destroyEntity(entityID: Int) {
		history.entityDestroyed(entityID, world)
		world.delete(entityID)
	}

	override fun inserted(entityIDs: IntBag) {
		entityIDs.forEach { entityID ->
			solarSystemMapper.create(entityID).set(this)
		}
	}

	override fun removed(entityIDs: IntBag) {
		entityIDs.forEach { entityID ->
			solarSystemMapper.remove(entityID)
		}
	}

	fun update(deltaGameTime: Int) {

		lock.write {
			world.setDelta(deltaGameTime.toFloat())
			world.process()
		}
	}

	private fun getNewEnitityID(): Long {
		return entityUIDGenerator.incrementAndGet();
	}
}