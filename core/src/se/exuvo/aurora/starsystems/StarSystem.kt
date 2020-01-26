package se.exuvo.aurora.starsystems

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
import se.exuvo.aurora.starsystems.systems.CustomSystemInvocationStrategy
import se.exuvo.aurora.starsystems.systems.GravimetricSensorSystem
import se.exuvo.aurora.starsystems.systems.GroupSystem
import se.exuvo.aurora.starsystems.systems.MovementSystem
import se.exuvo.aurora.starsystems.systems.OrbitSystem
import se.exuvo.aurora.starsystems.systems.PassiveSensorSystem
import se.exuvo.aurora.starsystems.systems.PowerSystem
import se.exuvo.aurora.starsystems.systems.RenderSystem
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
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write
import se.exuvo.aurora.galactic.ShipHull
import se.exuvo.aurora.empires.components.ColonyComponent
import se.exuvo.aurora.empires.components.ShipyardLocation
import se.exuvo.aurora.empires.components.ShipyardType
import se.exuvo.aurora.empires.components.Shipyard
import se.exuvo.aurora.empires.components.ShipyardSlipway
import se.exuvo.aurora.starsystems.components.EntityReference
import se.exuvo.aurora.empires.components.ShipyardModificationExpandCapacity
import se.exuvo.aurora.starsystems.systems.ColonySystem
import se.exuvo.aurora.starsystems.components.OwnerComponent
import net.mostlyoriginal.api.utils.pooling.PoolsCollection
import kotlin.reflect.KClass
import net.mostlyoriginal.api.event.common.Event
import se.exuvo.aurora.galactic.SimpleMunitionHull
import se.exuvo.aurora.galactic.AdvancedMunitionHull
import se.exuvo.aurora.galactic.DamagePattern
import se.exuvo.aurora.galactic.Shield
import se.exuvo.aurora.starsystems.systems.MovementPredictedSystem

class StarSystem(val initialName: String, val initialPosition: Vector2L) : EntitySubscription.SubscriptionListener, Disposable {
	companion object {
		@JvmStatic
		val starSystemIDGenerator = AtomicInteger()
		
		@JvmStatic
		val UUID_ASPECT = Aspect.all(UUIDComponent::class.java)
		
		@JvmStatic
		val log = LogManager.getLogger(StarSystem::class.java)
	}
	
	var updateTime = 0L
	var updateTimeAverage = 0.0

	private var entityUIDGenerator = 1L
	private val galaxy = GameServices[Galaxy::class]
	private val history = GameServices[History::class]
	val sid = starSystemIDGenerator.getAndIncrement()
	val galacticEntityID: Int = galaxy.world.create()

	val lock = ReentrantReadWriteLock()
	val world: World
	val random = RandomXS128()
	val pools = PoolsCollection()

	lateinit private var solarSystemMapper: ComponentMapper<StarSystemComponent>
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
		worldBuilder.with(EventSystem(PooledFastEventDispatcher(pools), SubscribeAnnotationFinder()))
		worldBuilder.with(OrbitSystem())
		worldBuilder.with(ColonySystem())
		worldBuilder.with(ShipSystem())
		worldBuilder.with(MovementPredictedSystem())
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
				entityIDs.forEachFast { entityID ->
					galaxy.entityAdded(world, entityID)
				}
			}

			override fun removed(entityIDs: IntBag) {
				entityIDs.forEachFast { entityID ->
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
		shipHull.armorLayers = 5
		shipHull.armorBlockHP = ByteArray(5, { 100 - 128 })
		shipHull.armorEnergyPerDamage = ShortArray(5, { 1000 })
		shipHull.armorBlockHP[2] = (255 - 128).toByte()
		shipHull.armorEnergyPerDamage[2] = 800.toShort()
		shipHull.armorBlockHP[3] = (50 - 128).toByte()
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
		reactor.maxHealth = 5 - 128
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
		fuelStorage.maxHealth = 3 - 128
		shipHull.addPart(fuelStorage)

		val battery = Battery(200 * Units.KILO, 500 * Units.KILO, 0.8f, 100 * Units.GIGA)
		battery.name = "Battery"
		shipHull.addPart(battery)

		val targetingComputer = TargetingComputer(2, 1, 0f, 10 * Units.KILO)
		targetingComputer.name = "TC 2-10"
		shipHull.addPart(targetingComputer)

		val sabot = SimpleMunitionHull(Resource.SABOTS)
		sabot.name = "A sabot"
		sabot.mass = 10
		sabot.radius = 5
		sabot.health = 2
		sabot.damagePattern = DamagePattern.KINETIC

		val missileBattery = Battery(10 * Units.KILO, 50 * Units.KILO, 0.8f, 1 * Units.GIGA)
		missileBattery.cost[Resource.GENERIC] = 500
		
//		val missileIonThruster = ElectricalThruster(290 * 1000, 1 * Units.KILO)
		val missileChemicalThruster = FueledThruster(29000 * 1000, 0)
		val missileFuelPart = FuelContainerPart(5000L * Resource.ROCKET_FUEL.specificVolume)
		
		val missile = AdvancedMunitionHull(Resource.MISSILES)
		missile.name = "Sprint missile"
		missile.addPart(missileBattery)
		missile.addPart(missileChemicalThruster)
		missile.addPart(missileFuelPart)

		val railgun = Railgun(2 * Units.MEGA, 5, 5 * Units.MEGA, 5, 3, 20)
		shipHull.addPart(railgun)
		val railgunRef = shipHull[Railgun::class][0]
		shipHull.preferredPartMunitions[railgunRef] = sabot

		val missileLauncher = MissileLauncher(200 * Units.KILO, 14, 3, 10, 1000 * 5500)
		missileLauncher.maxHealth = 3 - 128
		shipHull.addPart(missileLauncher)
		val missileLauncherRef = shipHull[MissileLauncher::class][0]
		shipHull.preferredPartMunitions[missileLauncherRef] = missile

		val beam = BeamWeapon(1 * Units.MEGA, 1.0, BeamWavelength.Infrared, 10 * Units.MEGA)
		shipHull.addPart(beam)

		val tcRef: PartRef<TargetingComputer> = shipHull[TargetingComputer::class][0]
		shipHull.defaultWeaponAssignments[tcRef] = shipHull.getPartRefs().filter({ it.part is WeaponPart })

		val ionThruster = ElectricalThruster(2000 * 982, 1 * Units.MEGA)
		shipHull.addPart(ionThruster)

		val chemicalThruster = FueledThruster(10000 * 982, 1)
		shipHull.addPart(chemicalThruster)
		
		val shield = Shield(1 * Units.MEGA, 200 * Units.KILO, 50)
		shipHull.addPart(shield)
		
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
		timedMovementMapper.create(entity1).set(0, 0, 0, 0, 0, 0, 0)
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
		
		empire1.colonies += getEntityReference(entity2)

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
		timedMovementMapper.create(entity4).apply { previous.value.position.set((Units.AU * 1000 * 1).toLong(), 0).rotate(45f) } //; previous.value.velocity.set(-1000000f, 0f)
		renderMapper.create(entity4)
		solarIrradianceMapper.create(entity4)
		circleMapper.create(entity4).set(radius = 10f)
		nameMapper.create(entity4).set(name = "Ship")
//		moveToEntityMapper.create(entity4).set(world.getEntity(entity1), ApproachType.BRACHISTOCHRONE))
		tintMapper.create(entity4).set(Color.RED)
		strategicIconMapper.create(entity4).set(Assets.textures.findRegion("strategic/ship"))
		emissionsMapper.create(entity4).set(mapOf(Spectrum.Electromagnetic to 1e10, Spectrum.Thermal to 1e10))
		
		val shipComponent = shipMapper.create(entity4).set(shipHull, galaxy.time)
		shipComponent.addCargo(Resource.NUCLEAR_FISSION, nuclearStorage.capacity / Resource.NUCLEAR_FISSION.specificVolume)
		shipComponent.addCargo(Resource.ROCKET_FUEL, fuelStorage.capacity / Resource.ROCKET_FUEL.specificVolume)

		if (!shipComponent.addCargo(sabot, 50)) {
			println("Failed to add sabots")
		}

		if (!shipComponent.addCargo(missile, 20)) {
			println("Failed to add missiles")
		}
		
		val entity5 = createEntity(Empire.GAIA)
		timedMovementMapper.create(entity5).apply {previous.value.velocity.set(0L, 1000 * 100L); previous.value.position.set(- (Units.AU * 1000 * 0.5).toLong(), 0) }
		renderMapper.create(entity5)
		circleMapper.create(entity5).set(radius = 100f)
		nameMapper.create(entity5).set(name = "Asteroid")
		tintMapper.create(entity5).set(Color.GRAY)
		strategicIconMapper.create(entity5).set(Assets.textures.findRegion("strategic/moon"))
	}
	
	fun createShip(hull: ShipHull, colonyEntity: Int?, empire: Empire): Int {
		
		val shipEntity = createEntity(empire)
		
		//TODO use https://github.com/junkdog/artemis-odb/wiki/Entity-Transmuter ?
		
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
			
			shipMovement.set(colonyPos.value.position, shipMovement.previous.value.velocity, Vector2L.Zero, galaxy.time)
			
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
	
	fun getEntityReference(entityID: Int): EntityReference {
		
		return pools.getPool(EntityReference::class.java).obtain().set(this, entityID, uuidMapper.get(entityID).uuid)
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
		
		world.getAspectSubscriptionManager().get(UUID_ASPECT).getEntities().forEachFast{ entityID ->
			val uuid = uuidMapper.get(entityID).uuid
			
			if (uuid.hashCode() == entityUUID.hashCode()) {
				return entityID
			}
		}
		
		return null
	}

	override fun inserted(entityIDs: IntBag) {
		entityIDs.forEachFast { entityID ->
			solarSystemMapper.create(entityID).set(this)
		}
	}

	override fun removed(entityIDs: IntBag) {
		entityIDs.forEachFast { entityID ->
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
		return entityUIDGenerator++
	}
	
	fun <T: Event> getEvent(eventClass: KClass<T>) = pools.obtain(eventClass.java)
	
	override fun dispose() {
		world.dispose()
	}
}