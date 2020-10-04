package se.exuvo.aurora.starsystems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.CustomComponentManager
import com.artemis.EntitySubscription
import com.artemis.SystemInvocationStrategy
import com.artemis.World
import com.artemis.WorldConfigurationBuilder
import com.artemis.utils.Bag
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
import se.exuvo.aurora.galactic.NuclearContainerPart
import se.exuvo.aurora.galactic.PartRef
import se.exuvo.aurora.galactic.PassiveSensor
import se.exuvo.aurora.galactic.Railgun
import se.exuvo.aurora.galactic.Resource
import se.exuvo.aurora.galactic.SolarPanel
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.history.History
import se.exuvo.aurora.starsystems.components.CircleComponent
import se.exuvo.aurora.starsystems.components.EmissionsComponent
import se.exuvo.aurora.starsystems.components.EntityUUID
import se.exuvo.aurora.starsystems.components.GalacticPositionComponent
import se.exuvo.aurora.starsystems.components.MassComponent
import se.exuvo.aurora.starsystems.components.MoveToEntityComponent
import se.exuvo.aurora.starsystems.components.NameComponent
import se.exuvo.aurora.starsystems.components.OrbitComponent
import se.exuvo.aurora.starsystems.components.StarSystemComponent
import se.exuvo.aurora.starsystems.components.RenderComponent
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.components.SolarIrradianceComponent
import se.exuvo.aurora.starsystems.components.Spectrum
import se.exuvo.aurora.starsystems.components.StrategicIconComponent
import se.exuvo.aurora.starsystems.components.SunComponent
import se.exuvo.aurora.starsystems.components.TimedMovementComponent
import se.exuvo.aurora.starsystems.components.TintComponent
import se.exuvo.aurora.starsystems.components.UUIDComponent
import se.exuvo.aurora.starsystems.events.PooledFastEventDispatcher
import se.exuvo.aurora.starsystems.systems.MovementSystem
import se.exuvo.aurora.starsystems.systems.OrbitSystem
import se.exuvo.aurora.starsystems.systems.PassiveSensorSystem
import se.exuvo.aurora.starsystems.systems.PowerSystem
import se.exuvo.aurora.starsystems.systems.ShipSystem
import se.exuvo.aurora.starsystems.systems.SolarIrradianceSystem
import se.exuvo.aurora.starsystems.systems.TimedLifeSystem
import se.exuvo.aurora.starsystems.systems.WeaponSystem
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.utils.plusAssign
import java.util.concurrent.atomic.AtomicInteger
import se.exuvo.aurora.galactic.ShipHull
import se.exuvo.aurora.empires.components.ColonyComponent
import se.exuvo.aurora.empires.components.ShipyardLocation
import se.exuvo.aurora.empires.components.ShipyardType
import se.exuvo.aurora.empires.components.Shipyard
import se.exuvo.aurora.empires.components.ShipyardSlipway
import se.exuvo.aurora.starsystems.components.EntityReference
import se.exuvo.aurora.empires.components.ShipyardModificationExpandCapacity
import se.exuvo.aurora.starsystems.systems.ColonySystem
import se.exuvo.aurora.starsystems.components.EmpireComponent
import net.mostlyoriginal.api.utils.pooling.PoolsCollection
import kotlin.reflect.KClass
import net.mostlyoriginal.api.event.common.Event
import se.exuvo.aurora.empires.components.ActiveTargetingComputersComponent
import se.exuvo.aurora.galactic.SimpleMunitionHull
import se.exuvo.aurora.galactic.AdvancedMunitionHull
import se.exuvo.aurora.galactic.DamagePattern
import se.exuvo.aurora.galactic.Shield
import se.exuvo.aurora.starsystems.systems.MovementPredictedSystem
import se.exuvo.aurora.galactic.Command
import se.exuvo.aurora.galactic.ContainerPart
import se.exuvo.aurora.galactic.Warhead
import se.exuvo.aurora.starsystems.components.ArmorComponent
import se.exuvo.aurora.starsystems.components.AsteroidComponent
import se.exuvo.aurora.starsystems.components.CargoComponent
import se.exuvo.aurora.starsystems.components.ChangingWorldComponent
import se.exuvo.aurora.starsystems.components.HPComponent
import se.exuvo.aurora.starsystems.components.LaserShotComponent
import se.exuvo.aurora.starsystems.components.MissileComponent
import se.exuvo.aurora.starsystems.components.PartStatesComponent
import se.exuvo.aurora.starsystems.components.PartsHPComponent
import se.exuvo.aurora.starsystems.components.RailgunShotComponent
import se.exuvo.aurora.starsystems.components.ShieldComponent
import se.exuvo.aurora.starsystems.components.ShipOrder
import se.exuvo.aurora.starsystems.components.StrategicIcon
import se.exuvo.aurora.starsystems.components.StrategicIconBase
import se.exuvo.aurora.starsystems.components.StrategicIconCenter
import se.exuvo.aurora.starsystems.systems.SpatialPartitioningSystem
import se.exuvo.aurora.starsystems.systems.SpatialPartitioningPlanetoidsSystem
import se.exuvo.aurora.starsystems.systems.TargetingSystem
import uk.co.omegaprime.btreemap.LongObjectBTreeMap
import java.util.concurrent.ArrayBlockingQueue

class StarSystem(val initialName: String, val initialPosition: Vector2L) : EntitySubscription.SubscriptionListener, Disposable {
	companion object {
		@JvmStatic
		val starSystemIDGenerator = AtomicInteger()
		
		@JvmStatic
		val UUID_ASPECT = Aspect.all(UUIDComponent::class.java)
		
		@JvmStatic
		val COMBAT_ASPECT = Aspect.one(ActiveTargetingComputersComponent::class.java, RailgunShotComponent::class.java, LaserShotComponent::class.java, MissileComponent::class.java)
		
		@JvmStatic
		val log = LogManager.getLogger(StarSystem::class.java)
	}
	
	var updateTime = 0L
	var updateTimeAverage = 0.0

	private var entityUIDGenerator = 1L
	val galaxy = GameServices[Galaxy::class]
	private val history = GameServices[History::class]
	val sid = starSystemIDGenerator.getAndIncrement()
	val galacticEntityID: Int = galaxy.world.create()

	val world: World
	var nextInvocationStrategy: SystemInvocationStrategy? = null
	val random = RandomXS128()
	val pools = PoolsCollection()
	
	val empireShips = LinkedHashMap<Empire, LongObjectBTreeMap<IntBag>>()
	val empireOrders = LinkedHashMap<Empire, Bag<ShipOrder>>()
	
	val allSubscription: EntitySubscription
	val uuidSubscription: EntitySubscription
	val combatSubscription: EntitySubscription
	
	var commandQueue = ArrayBlockingQueue<Command>(128)
	var workingShadow: ShadowStarSystem
	var shadow: ShadowStarSystem // Always safe to use from other StarSystems, requires shadow lock to use from UI
	var skipClearShadowChanged = false

	lateinit var solarSystemMapper: ComponentMapper<StarSystemComponent>
	lateinit var uuidMapper: ComponentMapper<UUIDComponent>
	lateinit var timedMovementMapper: ComponentMapper<TimedMovementComponent>
	lateinit var renderMapper: ComponentMapper<RenderComponent>
	lateinit var circleMapper: ComponentMapper<CircleComponent>
	lateinit var massMapper: ComponentMapper<MassComponent>
	lateinit var nameMapper: ComponentMapper<NameComponent>
	lateinit var orbitMapper: ComponentMapper<OrbitComponent>
	lateinit var sunMapper: ComponentMapper<SunComponent>
	lateinit var solarIrradianceMapper: ComponentMapper<SolarIrradianceComponent>
	lateinit var tintMapper: ComponentMapper<TintComponent>
	lateinit var strategicIconMapper: ComponentMapper<StrategicIconComponent>
	lateinit var emissionsMapper: ComponentMapper<EmissionsComponent>
	lateinit var moveToEntityMapper: ComponentMapper<MoveToEntityComponent>
	lateinit var shipMapper: ComponentMapper<ShipComponent>
	lateinit var colonyMapper: ComponentMapper<ColonyComponent>
	lateinit var ownerMapper: ComponentMapper<EmpireComponent>
	lateinit var partStatesMapper: ComponentMapper<PartStatesComponent>
	lateinit var shieldMapper: ComponentMapper<ShieldComponent>
	lateinit var armorMapper: ComponentMapper<ArmorComponent>
	lateinit var partsHPMapper: ComponentMapper<PartsHPComponent>
	lateinit var hpMapper: ComponentMapper<HPComponent>
	lateinit var cargoMapper: ComponentMapper<CargoComponent>
	lateinit var changingWorldMapper: ComponentMapper<ChangingWorldComponent>
	lateinit var asteroidMapper: ComponentMapper<AsteroidComponent>
	
	lateinit var spatialPartitioningSystem: SpatialPartitioningSystem
	lateinit var spatialPartitioningPlanetoidsSystem: SpatialPartitioningPlanetoidsSystem

	init {
		galaxy.world.getMapper(GalacticPositionComponent::class.java).create(galacticEntityID).set(initialPosition)
		galaxy.world.getMapper(RenderComponent::class.java).create(galacticEntityID)
		galaxy.world.getMapper(NameComponent::class.java).create(galacticEntityID).set(initialName)
		galaxy.world.getMapper(StrategicIconComponent::class.java).create(galacticEntityID).set(Assets.textures.findRegion("galactic/system"), null)

		val worldBuilder = WorldConfigurationBuilder()
//		worldBuilder.dependsOn(ProfilerPlugin::class.java)
//		worldBuilder.with(DebugPlugin.thatLogsErrorsIn("net.mostlyoriginal"))
//		worldBuilder.with(DebugPlugin.thatLogsEverythingIn("net.mostlyoriginal"))
		worldBuilder.with(EventSystem(PooledFastEventDispatcher(pools), SubscribeAnnotationFinder()))
		worldBuilder.with(OrbitSystem())
		worldBuilder.with(ColonySystem())
		worldBuilder.with(ShipSystem())
		worldBuilder.with(MovementPredictedSystem())
		worldBuilder.with(MovementSystem())
		worldBuilder.with(SolarIrradianceSystem())
		worldBuilder.with(PassiveSensorSystem())
//		worldBuilder.with(GravimetricSensorSystem())
		worldBuilder.with(TargetingSystem())
		worldBuilder.with(WeaponSystem())
		worldBuilder.with(PowerSystem())
		worldBuilder.with(TimedLifeSystem())
		worldBuilder.with(SpatialPartitioningSystem())
		worldBuilder.with(SpatialPartitioningPlanetoidsSystem())
		worldBuilder.register(CustomSystemInvocationStrategy(this))
		
		val worldConfig = worldBuilder.build()
		worldConfig.setComponentManager(CustomComponentManager(worldConfig.expectedEntityCount() , this))
		worldConfig.register(this)
		
		world = World(worldConfig)
		world.inject(this)
		
		allSubscription = world.getAspectSubscriptionManager().get(Aspect.all())
		allSubscription.addSubscriptionListener(this)
		
		uuidSubscription = world.getAspectSubscriptionManager().get(UUID_ASPECT)
		combatSubscription = world.getAspectSubscriptionManager().get(COMBAT_ASPECT)
		
		workingShadow = ShadowStarSystem(this)
		shadow  = ShadowStarSystem(this)
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
		shipHull.icon = StrategicIcon(StrategicIconBase.LARGE, StrategicIconCenter.INTEL)
		shipHull.designDay = galaxy.day
		shipHull.armorLayers = 5
		shipHull.armorBlockHP = UByteArray(5, { 100u })
		shipHull.armorEnergyPerDamage = ShortArray(5, { 1000 })
		shipHull.armorBlockHP[2] = 255u
		shipHull.armorEnergyPerDamage[2] = 800.toShort()
		shipHull.armorBlockHP[3] = 50u
		shipHull.armorEnergyPerDamage[3] = 3000.toShort()
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
		reactor.maxHealth = 50u
		shipHull.addPart(reactor)
//		println("Reactor fuel consumption ${reactor.fuelConsumption} kg / ${reactor.fuelTime} s")

		val nuclearStorage = NuclearContainerPart(10000)
		nuclearStorage.name = "Nuclear Cargo"
		nuclearStorage.cost[Resource.GENERIC] = 100
		shipHull.addPart(nuclearStorage)

		val ammoStorage = AmmoContainerPart(200000000)
		ammoStorage.name = "Munitions Cargo"
		ammoStorage.cost[Resource.GENERIC] = 100
		shipHull.addPart(ammoStorage)

		val fuelStorage = FuelContainerPart(400000000)
		fuelStorage.name = "Fuel Cargo"
		fuelStorage.cost[Resource.GENERIC] = 100
		fuelStorage.maxHealth = 30u
		shipHull.addPart(fuelStorage)

		val battery = Battery(200 * Units.KILO, 500 * Units.KILO, 80, 100 * Units.GIGA)
		battery.name = "Battery"
		shipHull.addPart(battery)

		val sabot = SimpleMunitionHull(Resource.SABOTS)
		sabot.name = "A sabot"
		sabot.loadedMass = 10
		sabot.radius = 5
		sabot.health = 2
		sabot.damagePattern = DamagePattern.KINETIC

		val missileBattery = Battery(10 * Units.KILO, 50 * Units.KILO, 80, 1 * Units.GIGA)
		missileBattery.cost[Resource.GENERIC] = 50
		
		val missileIonThruster = ElectricalThruster(290 * 1000, 0) // 1 * Units.KILO
//		val missileChemicalThruster = FueledThruster(29000 * 1000, 0)
		
		val missileFuelPart = FuelContainerPart(5000L * Resource.ROCKET_FUEL.specificVolume)
		val missileWarhead = Warhead(100_000)
		
		val missile = AdvancedMunitionHull(Resource.MISSILES)
		missile.name = "Sprint missile"
		missile.addPart(missileBattery)
		missile.addPart(missileIonThruster)
		missile.addPart(missileFuelPart)
		missile.addPart(missileWarhead)
		missile.finalize()

		val railgun = Railgun(2 * Units.MEGA, 5, 5 * Units.MEGA, 5, 3, 20)
		shipHull.addPart(railgun)
		val railgunRef = shipHull[Railgun::class][0]
		shipHull.preferredPartMunitions[railgunRef] = sabot

		val missileLauncher = MissileLauncher(7, 3, 10, 1000 * 5500)
		missileLauncher.maxHealth = 15u
		shipHull.addPart(missileLauncher)
		val missileLauncherRef = shipHull[MissileLauncher::class][0]
		shipHull.preferredPartMunitions[missileLauncherRef] = missile

		val beam = BeamWeapon(1 * Units.MEGA, 1.0, BeamWavelength.Infrared, 10 * Units.MEGA)
		shipHull.addPart(beam)

		val targetingComputer1 = TargetingComputer(2, 1, 1f, (0.5 * Units.AU).toLong(),10 * Units.KILO)
		targetingComputer1.name = "TC 05-1-2"
		shipHull.addPart(targetingComputer1)
		
		val tcRef1: PartRef<TargetingComputer> = shipHull[TargetingComputer::class][0]
		shipHull.defaultWeaponAssignments[tcRef1] = shipHull.getPartRefs().filter({ it.part is Railgun })
		
		val targetingComputer2 = TargetingComputer(2, 5, 1f, (2 * Units.AU).toLong(),10 * Units.KILO)
		targetingComputer2.name = "TC 20-5-2"
		shipHull.addPart(targetingComputer2)
		
		val tcRef2: PartRef<TargetingComputer> = shipHull[TargetingComputer::class][1]
		shipHull.defaultWeaponAssignments[tcRef2] = shipHull.getPartRefs().filter({ it.part is BeamWeapon || it.part is MissileLauncher })

		val ionThruster = ElectricalThruster(2000 * 982, 1 * Units.MEGA)
		ionThruster.maxHealth = 30u
		shipHull.addPart(ionThruster)

		val chemicalThruster = FueledThruster(10000 * 982, 1)
		chemicalThruster.maxHealth = 30u
		shipHull.addPart(chemicalThruster)
		
		val shield = Shield(1 * Units.MEGA, 10 * Units.KILO, 50)
		shield.name = "X-Booster"
		shipHull.addPart(shield)
		
		val shield2 = Shield(10 * Units.KILO, 1 * Units.KILO, 80)
		shield2.name = "S-Booster"
		shipHull.addPart(shield2)
		
		shipHull.preferredCargo[Resource.NUCLEAR_FISSION] = 100
		shipHull.preferredCargo[Resource.ROCKET_FUEL] = 10000
		shipHull.preferredMunitions[sabot] = 100
		shipHull.preferredMunitions[missile] = 50
		
		shipHull.finalize()
		
		val shipHull2 = ShipHull(shipHull)
		shipHull2.name = "Elodin"
		shipHull2.designDay = galaxy.day + 700
		
		shipHull2.finalize()
		
		if (empire1.shipHulls.size == 0) {
			empire1.shipHulls += shipHull
			empire1.shipHulls += shipHull2
		}

		val entity1 = createEntity(Empire.GAIA)
		timedMovementMapper.create(entity1).set(0, 0, 0, 0, 0, 0, 0)
		renderMapper.create(entity1)
		circleMapper.create(entity1).set(radius = 696340000f)
		massMapper.create(entity1).set(mass = 1.988e30)
		nameMapper.create(entity1).set(name = "Sun")
		sunMapper.create(entity1).set(solarConstant = 1361)
		tintMapper.create(entity1).set(Color.YELLOW)
		strategicIconMapper.create(entity1).set(Assets.textures.findRegion("strategic/sun"), null)

		val entity2 = createEntity(empire1)
		ownerMapper.create(entity2).set(empire1)
		nameMapper.create(entity2).set(name = "Earth")
		timedMovementMapper.create(entity2)
		renderMapper.create(entity2)
		circleMapper.create(entity2).set(radius = 6371000f)
		massMapper.create(entity2).set(mass = 5.972e24)
		orbitMapper.create(entity2).set(parent = entity1, a_semiMajorAxis = 1f, e_eccentricity = 0f, w_argumentOfPeriapsis = -45f, M_meanAnomaly = 0f)
		tintMapper.create(entity2).set(Color.GREEN)
//		strategicIconMapper.create(entity2).set(Assets.textures.findRegion("strategic/world"), null)
		strategicIconMapper.create(entity2).set(StrategicIcon(StrategicIconBase.COLONY, StrategicIconCenter.THREE))
		emissionsMapper.create(entity2).set(mapOf(Spectrum.Electromagnetic to 1e10, Spectrum.Thermal to 1e10))
		colonyMapper.create(entity2).set(random.nextLong(1000000), 1L, 1L, 1L).apply {
			shipyards += Shipyard(ShipyardLocation.TERRESTIAL, ShipyardType.CIVILIAN).apply{
				capacity = random.nextLong(2000)
				slipways += ShipyardSlipway()
				slipways += ShipyardSlipway().apply{
					build(shipHull2)
					hullCost.forEach({ (resource, amount) ->
						usedResources[resource] = maxOf(0, amount - 1)
					})
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
		}
		cargoMapper.create(entity2).apply {
			addCargo(sabot, 1000)
			addCargo(missile, 1000)
		}
		
		empire1.colonies += getEntityReference(entity2)

		val entity3 = createEntity(empire1)
		timedMovementMapper.create(entity3)
		renderMapper.create(entity3)
		circleMapper.create(entity3).set(radius = 1737100f)
		nameMapper.create(entity3).set(name = "Moon")
		orbitMapper.create(entity3).set(parent = entity2, a_semiMajorAxis = (384400.0 / Units.AU).toFloat(), e_eccentricity = 0.2f, w_argumentOfPeriapsis = 0f, M_meanAnomaly = 30f)
		tintMapper.create(entity3).set(Color.GRAY)
		strategicIconMapper.create(entity3).set(Assets.textures.findRegion("strategic/moon"), null)
		emissionsMapper.create(entity3).set(mapOf(Spectrum.Electromagnetic to 5e9, Spectrum.Thermal to 5e9))

		val entity4 = createEntity(empire1)
		ownerMapper.create(entity4).set(empire1)
		timedMovementMapper.create(entity4).apply { previous.value.position.set((Units.AU * 1000 * 0.9).toLong(), 0).rotate(45f) } //; previous.value.velocity.set(-1000000f, 0f)
		renderMapper.create(entity4)
		solarIrradianceMapper.create(entity4)
		circleMapper.create(entity4).set(radius = 10f)
		nameMapper.create(entity4).set(name = "Ship")
//		moveToEntityMapper.create(entity4).set(world.getEntity(entity1), ApproachType.BRACHISTOCHRONE))
		tintMapper.create(entity4).set(Color.RED)
		strategicIconMapper.create(entity4).set(StrategicIcon(StrategicIconBase.HUGE, StrategicIconCenter.HEALING_CIRCLE))
		emissionsMapper.create(entity4).set(mapOf(Spectrum.Electromagnetic to 1e10, Spectrum.Thermal to 1e10))
		
		val shipComponent = shipMapper.create(entity4).set(shipHull, galaxy.time)
		val partStates = partStatesMapper.create(entity4).set(shipHull)
		partsHPMapper.create(entity4).set(shipHull)
		armorMapper.create(entity4).set(shipHull)
		shieldMapper.create(entity4).set(shipHull, partStates)
		
		val cargo = cargoMapper.create(entity4).set(shipHull)
		
		cargo.addCargo(Resource.NUCLEAR_FISSION, nuclearStorage.capacity / Resource.NUCLEAR_FISSION.specificVolume)
		cargo.addCargo(Resource.ROCKET_FUEL, fuelStorage.capacity / Resource.ROCKET_FUEL.specificVolume)

		if (!cargo.addCargo(sabot, 50)) {
			println("Failed to add sabots")
		}

		if (!cargo.addCargo(missile, 20)) {
			println("Failed to add missiles")
		}
		
		registerShip(entity4, empire1, shipHull.emptyMass)
		
//		createShip(shipHull, entity2, empire1)
//		createShip(shipHull, entity2, empire1)
//		createShip(shipHull, entity2, empire1)
//		createShip(shipHull, entity2, empire1)
//		createShip(shipHull, entity2, empire1)
//		createShip(shipHull, entity2, empire1)
//		createShip(shipHull, entity2, empire1)
//		createShip(shipHull, entity2, empire1)
//		createShip(shipHull, entity2, empire1)
		
		val entity5 = createEntity(Empire.GAIA)
		timedMovementMapper.create(entity5).apply {previous.value.velocity.set(0L, 1000_00L).rotate(45f); previous.value.position.set(- (Units.AU * 1000 * 0.5).toLong(), (Units.AU * 1000 * 0.1).toLong()) }
		renderMapper.create(entity5)
		circleMapper.create(entity5).set(radius = 100f)
		nameMapper.create(entity5).set(name = "Asteroid")
		tintMapper.create(entity5).set(Color.GRAY)
		asteroidMapper.create(entity5)
		strategicIconMapper.create(entity5).set(StrategicIcon(StrategicIconBase.ASTEROID1, StrategicIconCenter.NONE))
		
		val entity6 = createEntity(Empire.GAIA)
		timedMovementMapper.create(entity6).apply {previous.value.velocity.set(1_000_000L, 0).rotate(45f); previous.value.position.set((Units.AU * 1000 * 2).toLong(), 0).rotate(135f) }
		renderMapper.create(entity6)
		circleMapper.create(entity6).set(radius = 100f)
		nameMapper.create(entity6).set(name = "Asteroid 2")
		tintMapper.create(entity6).set(Color.GRAY)
		asteroidMapper.create(entity6)
		strategicIconMapper.create(entity6).set(StrategicIcon(StrategicIconBase.ASTEROID1, StrategicIconCenter.NONE))
		
		val entity7 = createEntity(Empire.GAIA)
		timedMovementMapper.create(entity7).apply {previous.value.velocity.set(1_000_000L, 0).rotate(45f); previous.value.position.set((Units.AU * 1000 * 2).toLong(), 0).rotate(120f) }
		renderMapper.create(entity7)
		circleMapper.create(entity7).set(radius = 100f)
		nameMapper.create(entity7).set(name = "Asteroid 3")
		tintMapper.create(entity7).set(Color.GRAY)
		asteroidMapper.create(entity7)
		strategicIconMapper.create(entity7).set(StrategicIcon(StrategicIconBase.ASTEROID1, StrategicIconCenter.NONE))
		
		shadow.update()
	}
	
	fun createShip(hull: ShipHull, colonyEntity: Int?, empire: Empire): Int {
		
		val shipEntity = createEntity(empire)
		
		val shipMovement = timedMovementMapper.create(shipEntity)
		renderMapper.create(shipEntity)
		circleMapper.create(shipEntity).set(radius = 10f)
		nameMapper.create(shipEntity).set(name = "New Ship")
		tintMapper.create(shipEntity).set(Color.ORANGE)
		strategicIconMapper.create(shipEntity).set(hull.icon)
		emissionsMapper.create(shipEntity).set(mapOf(Spectrum.Electromagnetic to 0.0, Spectrum.Thermal to 0.0))
		ownerMapper.create(shipEntity).set(empire)
		
		if (hull[SolarPanel::class].isNotEmpty()) {
			solarIrradianceMapper.create(shipEntity)
		}

		val shipComponent = shipMapper.create(shipEntity).set(hull, galaxy.time)
		val partStates = partStatesMapper.create(shipEntity).set(hull)
		partsHPMapper.create(shipEntity).set(hull)
		armorMapper.create(shipEntity).set(hull)
		
		if (hull.shields.isNotEmpty()) {
			shieldMapper.create(shipEntity).set(hull, partStates)
		}
		
		val cargo = if (hull[ContainerPart::class].isNotEmpty()) cargoMapper.create(shipEntity).set(hull) else null
		
		if (colonyEntity != null) {
			
			val colony = colonyMapper.get(colonyEntity)!!
			val colonyCargo = cargoMapper.get(colonyEntity)
			val colonyMovement = timedMovementMapper.get(colonyEntity)
			val colonyPos = colonyMovement.get(galaxy.time)
			
			shipMovement.set(colonyPos.value.position, shipMovement.previous.value.velocity, Vector2L.Zero, galaxy.time)
			
			if (cargo != null) {
				hull.preferredCargo.forEach{ (resource, amount) ->
					cargo.addCargo(resource, colonyCargo.retrieveCargo(resource, amount))
				}
				
				hull.preferredMunitions.forEach{ (munitionHull, amount) ->
					cargo.addCargo(munitionHull, colonyCargo.retrieveCargo(munitionHull, amount))
				}
			}
			
		} else {
			
			if (cargo != null) {
				hull.preferredCargo.forEach{ (resource, amount) ->
					cargo.addCargo(resource, amount)
				}
				
				hull.preferredMunitions.forEach{ (munitionHull, amount) ->
					cargo.addCargo(munitionHull, amount)
				}
			}
		}
		
		registerShip(shipEntity, empire, hull.emptyMass)
		
		return shipEntity
	}
	
	fun registerShip(entityID: Int,
									 empire: Empire = ownerMapper.get(entityID).empire,
									 emptyMass: Long = shipMapper.get(entityID).hull.emptyMass)
	{
		var map = empireShips[empire]
		
		if (map == null) {
			map = LongObjectBTreeMap.create<IntBag>()!!
			empireShips[empire] = map
		}
		
		var ships = map[emptyMass]
		
		if (ships == null) {
			ships = IntBag()
			map[emptyMass] = ships
		}
		
		ships.add(entityID)
	}
	
	fun unregisterShip(entityID: Int, ship: ShipComponent = shipMapper.get(entityID)) {
		val empire = ownerMapper.get(entityID)!!.empire
		var map = empireShips[empire]
		
		if (map == null) {
			log.error("Attempt to remove ship $entityID but empire $empire has no registered ships in this system $this")
			return
		}
		
		val mass = ship.hull.emptyMass
		
		var ships = map[mass]
		
		if (ships == null) {
			log.error("Attempt to remove ship $entityID but empire $empire has no registered ships of mass $mass in this system $this")
			return
		}
		
		if (!ships.removeValue(entityID)) {
			log.error("Attempt to remove ship $entityID from empire $empire but it is not registered in this system $this")
		}
	}
	
	fun createEntity(empire: Empire): Int {
		val entityID = world.create()
		solarSystemMapper.create(entityID).set(this)
		uuidMapper.create(entityID).set(EntityUUID(sid, empire.id, getNewEntityID()))

		history.entityCreated(entityID, world)
		
		return entityID
	}
	
	fun destroyEntity(entityID: Int) {
		history.entityDestroyed(entityID, world)
		world.delete(entityID)
	}
	
	fun getEntityReference(entityID: Int): EntityReference {
		
		return EntityReference().set(this, entityID, uuidMapper.get(entityID).uuid)
	}
	
	fun updateEntityReference(entityID: Int, entityReference: EntityReference): EntityReference {
		
		return entityReference.set(this, entityID, uuidMapper.get(entityID).uuid)
	}
	
	fun isEntityReferenceValid(entityReference: EntityReference): Boolean {
		
		if (entityReference.system != this || !world.getEntityManager().isActive(entityReference.entityID)) {
			return false
		}
		
		val uuid = uuidMapper.get(entityReference.entityID).uuid
		
		return entityReference.entityUUID.hashCode() == uuid.hashCode()
	}

	fun getEntityByUUID(entityUUID: EntityUUID): Int? {
		
		uuidSubscription.getEntities().forEachFast { entityID ->
			val uuid = uuidMapper.get(entityID).uuid
			
			if (uuid.hashCode() == entityUUID.hashCode()) {
				return entityID
			}
		}
		
		return null
	}

	override fun inserted(entityIDs: IntBag) {
		entityIDs.forEachFast { entityID ->
			workingShadow.added.unsafeSet(entityID)
			solarSystemMapper.create(entityID).set(this)
		}
	}
	
	/**
	 * Mark that component contents have changed
	 */
	fun changed(entityID: Int, componentIndex: Int) {
		workingShadow.changed.unsafeSet(entityID)
		workingShadow.changedComponents[componentIndex].unsafeSet(entityID)
	}
	
	fun changed(entityID: Int, vararg componentIndexes: Int) {
		workingShadow.changed.unsafeSet(entityID)
		
		for (index in componentIndexes) {
			workingShadow.changedComponents[index].unsafeSet(entityID)
		}
	}
	
	inline fun changed(entityID: Int, componentMapper: ComponentMapper<*>) {
		changed(entityID, componentMapper.type.index)
	}
	
	fun changed(entityID: Int, vararg componentMappers: ComponentMapper<*>) {
		changed(entityID, *IntArray(componentMappers.size, { index -> componentMappers[index].type.index } ))
	}
	
	override fun removed(entityIDs: IntBag) {
		entityIDs.forEachFast { entityID ->
			workingShadow.deleted.unsafeSet(entityID)
		}
	}
	
	fun update(deltaGameTime: Int) {
		
		val profilerEvents = workingShadow.profilerEvents
		profilerEvents.clear()
		
		profilerEvents.start("shadow clear")
		workingShadow.added.clear()
		workingShadow.deleted.clear()
		
		if (skipClearShadowChanged) {
			skipClearShadowChanged = false
			
		} else {
			workingShadow.changed.clear()
			workingShadow.changedComponents.forEachFast { bitVector ->
				bitVector.clear()
			}
		}
		
		workingShadow.quadtreeShipsChanged = false;
		workingShadow.quadtreePlanetoidsChanged = false;
		profilerEvents.end()
		
		profilerEvents.start("commands")
		while(true) {
			val command = commandQueue.poll() ?: break
			
			try {
				command.apply()
			} catch (e: Exception) {
				log.error("Exception running command $command", e)
			}
		}
		profilerEvents.end()
		
		profilerEvents.start("processing")
		
		val invocationStrategy = nextInvocationStrategy
		if (invocationStrategy != null) {
			world.setInvocationStrategy(invocationStrategy)
			nextInvocationStrategy = null
		}
		
		if (deltaGameTime <= 100 || combatSubscription.entityCount > 0) {
			
			world.setDelta(1f)
			
			for (i in 0 until deltaGameTime) {
				profilerEvents.start("process 1")
				world.process()
				profilerEvents.end()
			}
			
		} else {
			
			val delta = 1 + deltaGameTime / 100
			world.setDelta(delta.toFloat())
			
			for (i in 0 until deltaGameTime / delta) {
				profilerEvents.start("process $delta")
				world.process()
				profilerEvents.end()
			}
			
			world.setDelta(1f)
			
			for (i in 0 until deltaGameTime - delta * (deltaGameTime / delta)) {
				profilerEvents.start("process 1")
				world.process()
				profilerEvents.end()
			}
			
//			println("processing deltaGameTime $deltaGameTime = $delta x ${deltaGameTime / delta} + ${deltaGameTime - delta * (deltaGameTime / delta)}")
		}
		profilerEvents.end()
		
		profilerEvents.start("shadow update")
		workingShadow.update()
		profilerEvents.end()
	}

	private fun getNewEntityID(): Long {
		return entityUIDGenerator++
	}
	
	fun <T: Event> getEvent(eventClass: KClass<T>): T = pools.obtain(eventClass.java)
	
	fun getName() = galaxy.world.getMapper(NameComponent::class.java).get(galacticEntityID).name
	
	override fun toString(): String  = "$galacticEntityID.${getName()}"
	
	override fun dispose() {
		world.dispose()
		workingShadow.dispose()
		shadow.dispose()
	}
}