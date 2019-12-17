package se.exuvo.aurora.planetarysystems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.EntitySubscription
import com.artemis.EntitySubscription.SubscriptionListener
import com.artemis.World
import com.artemis.WorldConfigurationBuilder
import com.artemis.annotations.EntityId
import com.artemis.utils.IntBag
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.utils.Disposable
import net.mostlyoriginal.api.event.common.EventSystem
import net.mostlyoriginal.api.event.common.SubscribeAnnotationFinder
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.Assets
import se.exuvo.aurora.galactic.AmmoContainerPart
import se.exuvo.aurora.galactic.Battery
import se.exuvo.aurora.galactic.BeamWavelength
import se.exuvo.aurora.galactic.BeamWeapon
import se.exuvo.aurora.galactic.ElectricalThruster
import se.exuvo.aurora.galactic.Empire
import se.exuvo.aurora.galactic.FissionReactor
import se.exuvo.aurora.galactic.FuelContainerPart
import se.exuvo.aurora.galactic.FueledThruster
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.MissileLauncher
import se.exuvo.aurora.galactic.MunitionHull
import se.exuvo.aurora.galactic.NuclearContainerPart
import se.exuvo.aurora.galactic.PartRef
import se.exuvo.aurora.galactic.PassiveSensor
import se.exuvo.aurora.galactic.Railgun
import se.exuvo.aurora.galactic.Resource
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
import se.exuvo.aurora.planetarysystems.components.TimedMovementComponent
import se.exuvo.aurora.planetarysystems.components.TintComponent
import se.exuvo.aurora.planetarysystems.components.UUIDComponent
import se.exuvo.aurora.planetarysystems.events.PooledFastEventDispatcher
import se.exuvo.aurora.planetarysystems.systems.CustomSystemInvocationStrategy
import se.exuvo.aurora.planetarysystems.systems.GravimetricSensorSystem
import se.exuvo.aurora.planetarysystems.systems.GroupSystem
import se.exuvo.aurora.planetarysystems.systems.MovementSystem
import se.exuvo.aurora.planetarysystems.systems.OrbitSystem
import se.exuvo.aurora.planetarysystems.systems.PassiveSensorSystem
import se.exuvo.aurora.planetarysystems.systems.PowerSystem
import se.exuvo.aurora.planetarysystems.systems.RenderSystem
import se.exuvo.aurora.planetarysystems.systems.ShipSystem
import se.exuvo.aurora.planetarysystems.systems.SolarIrradianceSystem
import se.exuvo.aurora.planetarysystems.systems.TimedLifeSystem
import se.exuvo.aurora.planetarysystems.systems.WeaponSystem
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.utils.forEach
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write
import se.exuvo.aurora.galactic.ShipHull
import se.exuvo.aurora.empires.components.ColonyComponent
import se.exuvo.aurora.empires.components.ShipyardLocation
import se.exuvo.aurora.empires.components.ShipyardType
import se.exuvo.aurora.empires.components.Shipyard
import se.exuvo.aurora.empires.components.ShipyardSlipway
import se.exuvo.aurora.planetarysystems.components.EntityReference
import se.exuvo.aurora.empires.components.ShipyardModificationExpandCapacity
import se.exuvo.aurora.planetarysystems.systems.ColonySystem
import se.exuvo.aurora.planetarysystems.components.OwnerComponent

class PlanetarySystem(val initialName: String, val initialPosition: Vector2L) : EntitySubscription.SubscriptionListener, Disposable {
	companion object {
		val planetarySystemIDGenerator = AtomicInteger()
	}

	val log = LogManager.getLogger(this.javaClass)
	var updateTime = 0L
	val entityUIDGenerator = AtomicLong()

	private val galaxy = GameServices[Galaxy::class]
	private val history = GameServices[History::class]
	val sid = planetarySystemIDGenerator.getAndIncrement()
	@EntityId
	val galacticEntityID: Int = galaxy.world.create()

	val lock = ReentrantReadWriteLock()
	val world: World
	val random = RandomXS128()

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
	lateinit private var colonyMapper: ComponentMapper<ColonyComponent>
	lateinit private var ownerMapper: ComponentMapper<OwnerComponent>

	init {
		galaxy.world.getMapper(GalacticPositionComponent::class.java).create(galacticEntityID).set(initialPosition)
		galaxy.world.getMapper(RenderComponent::class.java).create(galacticEntityID)
		galaxy.world.getMapper(NameComponent::class.java).create(galacticEntityID).set(initialName)
		galaxy.world.getMapper(StrategicIconComponent::class.java).create(galacticEntityID).set(Assets.textures.findRegion("galactic/system"))

		val worldBuilder = WorldConfigurationBuilder()
//		worldBuilder.dependsOn(ProfilerPlugin::class.java)
		worldBuilder.with(EventSystem(PooledFastEventDispatcher(), SubscribeAnnotationFinder()))
		worldBuilder.with(GroupSystem())
		worldBuilder.with(OrbitSystem())
		worldBuilder.with(ColonySystem())
		worldBuilder.with(ShipSystem())
		worldBuilder.with(MovementSystem())
		worldBuilder.with(SolarIrradianceSystem())
		worldBuilder.with(PassiveSensorSystem())
//		worldBuilder.with(GravimetricSensorSystem())
		worldBuilder.with(WeaponSystem())
		worldBuilder.with(PowerSystem())
		worldBuilder.with(TimedLifeSystem())
		worldBuilder.with(RenderSystem())
		worldBuilder.register(CustomSystemInvocationStrategy())
		//TODO add system to send changes over network

		val worldConfig = worldBuilder.build()
		worldConfig.register(this)
		world = World(worldConfig)

		world.getAspectSubscriptionManager().get(Aspect.all()).addSubscriptionListener(this)
		world.inject(this)

		world.getAspectSubscriptionManager().get(Aspect.all()).addSubscriptionListener(object : SubscriptionListener {
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
		
		val sensor1 = PassiveSensor(300000, Spectrum.Electromagnetic, 1e-7, 14, Units.AU * 0.3, 20, 0.97, 1);
		sensor1.name = "EM 1e-4"
		sensor1.designDay = 1
		
		val sensor2 = PassiveSensor(800000, Spectrum.Thermal, 1e-8, 8, Units.AU * 1, 0, 0.9, 5);
		sensor2.name = "TH 1e-10"
		sensor2.designDay = 1
		
		val shipHull = ShipHull()
		shipHull.name = "Elodin"
		shipHull.designDay = galaxy.day
//		shipClass.powerScheme = PowerScheme.SOLAR_REACTOR_BATTERY

		shipHull.addPart(sensor1)
		shipHull.addPart(sensor2)

		val solarPanel = SolarPanel()
		solarPanel.name = "Solar Panel"
		solarPanel.cost[Resource.SEMICONDUCTORS] = 300
		shipHull.addPart(solarPanel)

		val reactor = FissionReactor(5 * Units.MEGA)
		reactor.name = "Nuclear Reactor"
		reactor.cost[Resource.GENERIC] = 1000
		shipHull.addPart(reactor)
//		println("Reactor fuel consumption ${reactor.fuelConsumption} kg / ${reactor.fuelTime} s")

		val nuclearStorage = NuclearContainerPart(10000)
		nuclearStorage.name = "Nuclear Cargo"
		nuclearStorage.cost[Resource.GENERIC] = 100
		shipHull.addPart(nuclearStorage)

		val ammoStorage = AmmoContainerPart(1000000)
		ammoStorage.name = "Munitions Cargo"
		ammoStorage.cost[Resource.GENERIC] = 100
		shipHull.addPart(ammoStorage)

		val fuelStorage = FuelContainerPart(10000000)
		fuelStorage.name = "Fuel Cargo"
		fuelStorage.cost[Resource.GENERIC] = 100
		shipHull.addPart(fuelStorage)

		val battery = Battery(200 * Units.KILO, 500 * Units.KILO, 0.8f, 100 * Units.GIGA)
		battery.name = "Battery"
		shipHull.addPart(battery)

		val targetingComputer = TargetingComputer(2, 10, 0f, 10 * Units.KILO)
		targetingComputer.name = "TC 2-10"
		shipHull.addPart(targetingComputer)

		val dummyMass1 = Battery(10 * Units.KILO, 50 * Units.KILO, 0.8f, 1 * Units.GIGA)
		dummyMass1.cost[Resource.GENERIC] = 10

		val dummyMass2 = Battery(10 * Units.KILO, 50 * Units.KILO, 0.8f, 1 * Units.GIGA)
		dummyMass2.cost[Resource.GENERIC] = 100

		val sabot = MunitionHull(Resource.SABOTS)
		sabot.name = "A sabot"
		sabot.addPart(dummyMass1)

		val missile = MunitionHull(Resource.MISSILES)
		missile.name = "A missile"
		missile.addPart(dummyMass2)

		val railgun = Railgun(2 * Units.MEGA, 7, 5 * Units.MEGA, 5)
		shipHull.addPart(railgun)
		val railgunRef = shipHull[Railgun::class][0]
		shipHull.preferredPartMunitions[railgunRef] = sabot

		val missileLauncher = MissileLauncher(200 * Units.KILO, 13, 3, 10)
		shipHull.addPart(missileLauncher)
		val missileLauncherRef = shipHull[MissileLauncher::class][0]
		shipHull.preferredPartMunitions[missileLauncherRef] = missile

		val beam = BeamWeapon(1 * Units.MEGA, BeamWavelength.Microwaves, 0.0, 10 * Units.MEGA)
		shipHull.addPart(beam)

		val tcRef: PartRef<TargetingComputer> = shipHull[TargetingComputer::class][0]
		shipHull.defaultWeaponAssignments[tcRef] = shipHull.getPartRefs().filter({ it.part is WeaponPart })

		val ionThruster = ElectricalThruster(1f * 9.82f * 1000f, 1 * Units.MEGA)
		shipHull.addPart(ionThruster)

		val chemicalThruster = FueledThruster(10f * 9.82f * 1000f, 1)
		shipHull.addPart(chemicalThruster)
		
		shipHull.preferredCargo[Resource.NUCLEAR_FISSION] = 100
		shipHull.preferredCargo[Resource.ROCKET_FUEL] = 10000
		shipHull.preferredMunitions[sabot] = 100
		shipHull.preferredMunitions[missile] = 50
		
		val shipHull2 = ShipHull(shipHull)
		shipHull2.name = "Elodin"
		shipHull2.designDay = galaxy.day + 700
		
		if (empire1.shipHulls.size == 0) {
			empire1.shipHulls += shipHull
			empire1.shipHulls += shipHull2
		}

		val entity1 = createEntity(Empire.GAIA)
		timedMovementMapper.create(entity1).set(0, 0, 0f, 0f, 0)
		renderMapper.create(entity1)
		circleMapper.create(entity1).set(radius = 695700f)
		massMapper.create(entity1).set(mass = 1.988e30)
		nameMapper.create(entity1).set(name = "Sun")
		sunMapper.create(entity1).set(solarConstant = 1361)
		tintMapper.create(entity1).set(Color.YELLOW)
		strategicIconMapper.create(entity1).set(Assets.textures.findRegion("strategic/sun"))

		val entity2 = createEntity(empire1)
		ownerMapper.create(entity2).set(empire1)
		nameMapper.create(entity2).set(name = "Earth")
		timedMovementMapper.create(entity2)
		renderMapper.create(entity2)
		circleMapper.create(entity2).set(radius = 6371f)
		massMapper.create(entity2).set(mass = 5.972e24)
		orbitMapper.create(entity2).set(parent = entity1, a_semiMajorAxis = 1f, e_eccentricity = 0f, w_argumentOfPeriapsis = -45f, M_meanAnomaly = 0f)
		tintMapper.create(entity2).set(Color.GREEN)
		strategicIconMapper.create(entity2).set(Assets.textures.findRegion("strategic/world"))
		emissionsMapper.create(entity2).set(mapOf(Spectrum.Electromagnetic to 1e10, Spectrum.Thermal to 1e10))
		colonyMapper.create(entity2).set(population = random.nextLong(1000000)).apply {
			shipyards += Shipyard(ShipyardLocation.TERRESTIAL, ShipyardType.CIVILIAN).apply{
				capacity = random.nextLong(2000)
				slipways += ShipyardSlipway()
				slipways += ShipyardSlipway().apply{
					build(shipHull2)
				}
			}
			shipyards += Shipyard(ShipyardLocation.ORBITAL, ShipyardType.MILITARY).apply{
				capacity = random.nextLong(10000)
				tooledHull = shipHull
				modificationActivity = ShipyardModificationExpandCapacity(500)
				slipways += ShipyardSlipway().apply{
					build(shipHull)
				}
			}
			
			addCargo(sabot, 1000)
			addCargo(missile, 1000)
		}
		
		empire1.colonies += EntityReference(this, entity2)

		val entity3 = createEntity(empire1)
		timedMovementMapper.create(entity3)
		renderMapper.create(entity3)
		circleMapper.create(entity3).set(radius = 1737f)
		nameMapper.create(entity3).set(name = "Moon")
		orbitMapper.create(entity3).set(parent = entity2, a_semiMajorAxis = (384400.0 / Units.AU).toFloat(), e_eccentricity = 0.2f, w_argumentOfPeriapsis = 0f, M_meanAnomaly = 30f)
		tintMapper.create(entity3).set(Color.GRAY)
		strategicIconMapper.create(entity3).set(Assets.textures.findRegion("strategic/moon"))
		emissionsMapper.create(entity3).set(mapOf(Spectrum.Electromagnetic to 5e9, Spectrum.Thermal to 5e9))

		val entity4 = createEntity(empire1)
		ownerMapper.create(entity4).set(empire1)
		timedMovementMapper.create(entity4).apply { previous.value.position.set((Units.AU * 1000L * 1L).toLong(), 0).rotate(45f) } //; previous.value.velocity.set(-1000000f, 0f)
		renderMapper.create(entity4)
		solarIrradianceMapper.create(entity4)
		circleMapper.create(entity4).set(radius = 10f)
		nameMapper.create(entity4).set(name = "Ship")
//		moveToEntityMapper.create(entity4).set(world.getEntity(entity1), ApproachType.BRACHISTOCHRONE))
		tintMapper.create(entity4).set(Color.RED)
		strategicIconMapper.create(entity4).set(Assets.textures.findRegion("strategic/ship"))
		emissionsMapper.create(entity4).set(mapOf(Spectrum.Electromagnetic to 1e10, Spectrum.Thermal to 1e10))
		

		val shipComponent = shipMapper.create(entity4).set(shipHull, galaxy.time)
		shipComponent.addCargo(Resource.NUCLEAR_FISSION, 100)
		shipComponent.addCargo(Resource.ROCKET_FUEL, 10000)

		if (!shipComponent.addCargo(sabot, 10)) {
			println("Failed to add sabots")
		}

		if (!shipComponent.addCargo(missile, 10)) {
			println("Failed to add missiles")
		}
	}
	
	fun createShip(hull: ShipHull, colonyEntity: Int?, empire: Empire): Int {
		
		val shipEntity = createEntity(empire)
		
		val shipMovement = timedMovementMapper.create(shipEntity)
		renderMapper.create(shipEntity)
		circleMapper.create(shipEntity).set(radius = 10f)
		nameMapper.create(shipEntity).set(name = "New Ship")
		tintMapper.create(shipEntity).set(Color.ORANGE)
		strategicIconMapper.create(shipEntity).set(Assets.textures.findRegion("strategic/ship"))
		emissionsMapper.create(shipEntity).set(mapOf(Spectrum.Electromagnetic to 0.0, Spectrum.Thermal to 0.0))
		ownerMapper.create(shipEntity).set(empire)
		
		if (hull[SolarPanel::class].isNotEmpty()) {
			solarIrradianceMapper.create(shipEntity)
		}

		val shipComponent = shipMapper.create(shipEntity).set(hull, galaxy.time)
		
		if (colonyEntity != null) {
			
			val colony = colonyMapper.get(colonyEntity)
			val colonyMovement = timedMovementMapper.get(colonyEntity)
			val colonyPos = colonyMovement.get(galaxy.time)
			
			shipMovement.set(colonyPos.value.position, shipMovement.previous.value.velocity, galaxy.time)
			
			hull.preferredCargo.forEach{ resource, amount ->
				shipComponent.addCargo(resource, colony.retrieveCargo(resource, amount))
			}
			
			hull.preferredMunitions.forEach{ munitionHull, amount ->
				shipComponent.addCargo(munitionHull, colony.retrieveCargo(munitionHull, amount))
			}
			
		} else {
			
			hull.preferredCargo.forEach{ resource, amount ->
				shipComponent.addCargo(resource, amount)
			}
			
			hull.preferredMunitions.forEach{ munitionHull, amount ->
				shipComponent.addCargo(munitionHull, amount)
			}
		}
		
		return shipEntity
	}
	
	fun createEntity(empire: Empire): Int {
		val entityID = world.create()
		solarSystemMapper.create(entityID).set(this)
		uuidMapper.create(entityID).set(EntityUUID(sid, empire.id, getNewEntitityID()))

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

//	var delay = (Math.random() * 30).toLong()
	fun update(deltaGameTime: Int) {

		lock.write {
			world.setDelta(deltaGameTime.toFloat())
			world.process()
		}
		
//		if (Math.random() > 0.97) {
//			delay = (Math.random() * 30).toLong()
//		}
//		
//		Thread.sleep(delay)
	}

	private fun getNewEntitityID(): Long {
		return entityUIDGenerator.incrementAndGet();
	}
	
	override fun dispose() {
		world.dispose()
	}
}