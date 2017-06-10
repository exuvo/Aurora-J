package se.exuvo.aurora

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntityListener
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.utils.Pool.Poolable
import org.apache.log4j.Logger
import se.exuvo.aurora.components.ApproachType
import se.exuvo.aurora.components.CircleComponent
import se.exuvo.aurora.components.MassComponent
import se.exuvo.aurora.components.MoveToComponent
import se.exuvo.aurora.components.NameComponent
import se.exuvo.aurora.components.OrbitComponent
import se.exuvo.aurora.components.PositionComponent
import se.exuvo.aurora.components.RenderComponent
import se.exuvo.aurora.components.SolarSystemComponent
import se.exuvo.aurora.components.TagComponent
import se.exuvo.aurora.components.ThrustComponent
import se.exuvo.aurora.components.VelocityComponent
import se.exuvo.aurora.systems.GroupSystem
import se.exuvo.aurora.systems.MovementSystem
import se.exuvo.aurora.systems.OrbitSystem
import se.exuvo.aurora.systems.RenderSystem
import se.exuvo.aurora.systems.TagSystem
import se.exuvo.aurora.utils.DummyReentrantReadWriteLock
import java.util.Random
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

class SolarSystem : EntityListener {
	companion object {
		val FAMILY = Family.exclude(ThrustComponent::class.java).get()
	}

	val log = Logger.getLogger(this.javaClass)

	val lock = ReentrantReadWriteLock()
	val engine = PooledEngine()

	private val solarSystemMapper = ComponentMapper.getFor(SolarSystemComponent::class.java)

	/* All missiles and other short lived entites should be Pooled using engine.createEntity() and engine.createComponent()
   * Everything else which should either be capable of moving between systems or is static should be created normally with Entity() and Component() 
	 */
	fun init() {
		engine.addEntityListener(this)

		engine.addSystem(TagSystem())
		engine.addSystem(GroupSystem(DummyReentrantReadWriteLock.INSTANCE))
		engine.addSystem(OrbitSystem())
		engine.addSystem(MovementSystem())
		engine.addSystem(RenderSystem())

		val entity1 = Entity()
		entity1.add(PositionComponent().apply { position.set(0, 0) })
		entity1.add(RenderComponent())
		entity1.add(CircleComponent().apply { radius = 695700f })
		entity1.add(MassComponent().apply { mass = 1.988e30 })
		entity1.add(NameComponent().apply { name = "Sun" })
		entity1.add(TagComponent().apply { tag = TagSystem.SUN })

		engine.addEntity(entity1)

		val entity2 = Entity()
		entity2.add(PositionComponent())
		entity2.add(RenderComponent())
		entity2.add(CircleComponent().apply { radius = 6371f })
		entity2.add(NameComponent().apply { name = "Earth" })
		entity2.add(MassComponent().apply { mass = 5.972e24 })
		entity2.add(OrbitComponent().apply { parent = entity1; a_semiMajorAxis = 1f; e_eccentricity = 0f; w_argumentOfPeriapsis = -45f })

		engine.addEntity(entity2)

		val entity3 = Entity()
		entity3.add(PositionComponent())
		entity3.add(RenderComponent())
		entity3.add(CircleComponent().apply { radius = 1737f })
		entity3.add(NameComponent().apply { name = "Moon" })
		entity3.add(OrbitComponent().apply { parent = entity2; a_semiMajorAxis = (384400.0 / OrbitSystem.AU).toFloat(); e_eccentricity = 0.2f; M_meanAnomaly = 30f })

		engine.addEntity(entity3)

		val entity4 = Entity()
		entity4.add(PositionComponent().apply { position.set((OrbitSystem.AU * 1000L * 1L).toLong(), 0).setAngle(45f) })
		entity4.add(RenderComponent())
		entity4.add(CircleComponent().apply { radius = 10f })
		entity4.add(NameComponent().apply { name = "Ship" })
		entity4.add(MassComponent().apply { mass = 1000.0 })
		entity4.add(ThrustComponent().apply { thrust = 10f * 9.82f * 1000f })
		entity4.add(VelocityComponent().apply { velocity.set(-1000000f, 0f) })
		entity4.add(MoveToComponent().apply { target = entity1; approach = ApproachType.BRACHISTOCHRONE })

		engine.addEntity(entity4)
	}

	override fun entityAdded(entity: Entity) {
		if (!solarSystemMapper.has(entity)) {
			if (entity is Poolable) {
				entity.add(engine.createComponent(SolarSystemComponent::class.java).apply { system = this@SolarSystem })
			} else {
				entity.add(SolarSystemComponent(this))
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

	fun generateRandomSystem() {

		for (entity in engine.getEntitiesFor(FAMILY)) {
			engine.removeEntity(entity)
		}

		// Mass range of stellar objects in kg
		// Star: 		10^29 - 10^39 
		// Planet:		10^22 - 10^28
		// Moon:		10^20 - 10^22
		// Asteroid:	10^10 - 10^20

		// mass = density * 4 * radius^3 * 10^9 density in kg/m^3
		// density = 500 - 5000 				planets, higher at lower orbits
		// density = 2000 - 3530 				moons random
		// density = 10^-6 - 10^18				stars smaller = denser


		val randomNumbersClass = Random()


		val Star = addRandomStar(randomNumbersClass)
		//addPlanet (Star,1e0,1f,"planet",6.666f,0.5f,0f,0f)
		//addPlanet (Star,1e0,1f,"planet2",4f,0.5f,90f,0f)

		var planet = addRandomPlanet(randomNumbersClass, Star)

		addPlanet(Star, 1e24, 10000f, "biggest", 150f, 0f, 0f, 0f)

		var i = 0
		while (planet != null) {
			if (i == 100) {
				break
			}
			planet = addRandomPlanet(randomNumbersClass, Star)
			i++
		}

	}

	fun addRandomStar(randomNumbersClass: Random): Entity {
		var radius: Double
		var density: Double
		var mass: Double

		//Decide the chance to appear	
		var oStar = 1  // Contains: Blue giant, Blue superstar
		var bStar = 2  // Contains: Blue giant, Blue superstar
		var aStar = 6  // A type
		var fStar = 30  // F type
		var gStar = 76  // Contains: Yellow supergiants
		var kStar = 121  // K type
		var mStar = 765  // Contains: Red dwarf, Red giant, Red superstar

		val sumStar = mStar + kStar + gStar + fStar + aStar + bStar + oStar

		val randomStar = sumStar * randomNumbersClass.nextDouble()

		//Convert the chance valus to usable values
		kStar = kStar + mStar
		gStar = gStar + kStar
		fStar = fStar + gStar
		aStar = aStar + fStar
		bStar = bStar + aStar
		oStar = oStar + bStar

		if (sumStar != oStar) {
			throw RuntimeException("sumStar != oStar")
		}

		// need to be in the same order as the convertion order 
		when {
			randomStar < mStar -> {
				radius = 100000 + (486990 - 100000) * randomNumbersClass.nextDouble()
				density = 10000 + (100000 - 10000) * randomNumbersClass.nextDouble()
			}
			randomStar < kStar -> {
				radius = (486990 + randomNumbersClass.nextInt(667872 - 486990)).toDouble()
				density = (2768 + randomNumbersClass.nextInt(11341 - 2768)).toDouble()
			}
			randomStar < gStar -> {
				radius = (667872 + randomNumbersClass.nextInt(800055 - 667872)).toDouble()
				density = (1509 + randomNumbersClass.nextInt(2768 - 1509)).toDouble()
			}
			randomStar < fStar -> {
				radius = (800055 + randomNumbersClass.nextInt(973980 - 800055)).toDouble()
				density = (753 + randomNumbersClass.nextInt(1509 - 753)).toDouble()
			}
			randomStar < aStar -> {
				radius = (973980 + randomNumbersClass.nextInt(1252260 - 973980)).toDouble()
				density = (286 + randomNumbersClass.nextInt(753 - 286)).toDouble()
			}
			randomStar < bStar -> {
				radius = (1252260 + randomNumbersClass.nextInt(4591620 - 1252260)).toDouble()
				density = (2 + randomNumbersClass.nextInt(286 - 2)).toDouble()
			}
			randomStar < oStar -> {
				radius = (4591620 + randomNumbersClass.nextInt(1000000000 - 4591620)).toDouble()
				density = (0.0003 + 2 * randomNumbersClass.nextDouble())
			}
			else -> {
				log.error("Error on sun generation, defaulting to known star")
				radius = 486990e0
				density = 2000e0
			}
		}
		mass = density * 4 * Math.pow(radius, (3).toDouble()) * 10e9

		//Fix name later (load from big name file)
		return addStar(mass, radius.toFloat(), "Sun")
	}

	fun addStar(starMass: Double, starRadius: Float, starName: String): Entity {

		val entity = Entity()
		entity.add(PositionComponent().apply { position.set(0, 0) })
		entity.add(RenderComponent())
		entity.add(CircleComponent().apply { radius = starRadius })
		entity.add(MassComponent().apply { mass = starMass })
		entity.add(NameComponent().apply { name = starName })

		engine.addEntity(entity)

		return entity
	}

	fun addRandomPlanet(randomNumbersClass: Random, parentEntity: Entity): Entity? {
		val parentMass = ComponentMapper.getFor(MassComponent::class.java).get(parentEntity).mass
		var semiMajorAxis = Math.sqrt(parentMass) * (0.8 + 29.2 * randomNumbersClass.nextDouble()) * 1 / (1e16)
		var eccentricity = 0 + (0.85 - (0.85 / (1 + semiMajorAxis / 30))) * randomNumbersClass.nextDouble()
		var argumentOfPeriapsis = 360 * randomNumbersClass.nextDouble()
		val meanAnomaly = 360 * randomNumbersClass.nextDouble()

		val orbitals = engine.getSystem(OrbitSystem::class.java).getMoons(parentEntity)
		var breakCounter = 0
		var i = 0
		while (i < orbitals.count()) {
			val otherPlanetEccentricity = ComponentMapper.getFor(OrbitComponent::class.java).get(orbitals.elementAt(i)).e_eccentricity
			val otherPlanetSemiMajorAxis = ComponentMapper.getFor(OrbitComponent::class.java).get(orbitals.elementAt(i)).a_semiMajorAxis
			val otherPlanetArgumentOfPeriapsis = ComponentMapper.getFor(OrbitComponent::class.java).get(orbitals.elementAt(i)).w_argumentOfPeriapsis
			val otherPlanetPeriapsis = otherPlanetSemiMajorAxis * (1 - otherPlanetEccentricity)
			val otherPlanetApoapsis = otherPlanetSemiMajorAxis * (1 + otherPlanetEccentricity)
			val periapsis = semiMajorAxis * (1 - eccentricity)
			val apoapsis = semiMajorAxis * (1 + eccentricity)
			val minimumDistance = 0.50

			if (otherPlanetApoapsis > apoapsis + minimumDistance && otherPlanetPeriapsis > periapsis + minimumDistance) { //Inside case
				val c1 = (otherPlanetSemiMajorAxis - minimumDistance) * (1 - Math.pow(otherPlanetEccentricity.toDouble(), 2e0)) - semiMajorAxis * (1 - Math.pow(eccentricity.toDouble(), 2e0))
				val c2 = (otherPlanetSemiMajorAxis - minimumDistance) * (1 - Math.pow(otherPlanetEccentricity.toDouble(), 2e0)) * eccentricity * Math.cos(Math.toRadians(-argumentOfPeriapsis)) - semiMajorAxis * (1 - Math.pow(eccentricity.toDouble(), 2e0)) * otherPlanetEccentricity * Math.cos(Math.toRadians(-otherPlanetArgumentOfPeriapsis.toDouble()))
				val c3 = (otherPlanetSemiMajorAxis - minimumDistance) * (1 - Math.pow(otherPlanetEccentricity.toDouble(), 2e0)) * eccentricity * Math.sin(Math.toRadians(-argumentOfPeriapsis)) - semiMajorAxis * (1 - Math.pow(eccentricity.toDouble(), 2e0)) * otherPlanetEccentricity * Math.sin(Math.toRadians(-otherPlanetArgumentOfPeriapsis.toDouble()))
				// c1 + c2*cos(x) + c3*sin(x) = 0
				val R = Math.sqrt(Math.pow(c2, 2e0) + Math.pow(c3, 2e0))
				if (1 < Math.abs(-c1 / R)) {
					i++
					continue
				}
			} else if (apoapsis > otherPlanetApoapsis + minimumDistance && periapsis > otherPlanetPeriapsis + minimumDistance) { //Outside case
				val c1 = (otherPlanetSemiMajorAxis + minimumDistance) * (1 - Math.pow(otherPlanetEccentricity.toDouble(), 2e0)) - semiMajorAxis * (1 - Math.pow(eccentricity.toDouble(), 2e0))
				val c2 = (otherPlanetSemiMajorAxis + minimumDistance) * (1 - Math.pow(otherPlanetEccentricity.toDouble(), 2e0)) * eccentricity * Math.cos(Math.toRadians(-argumentOfPeriapsis)) - semiMajorAxis * (1 - Math.pow(eccentricity.toDouble(), 2e0)) * otherPlanetEccentricity * Math.cos(Math.toRadians(-otherPlanetArgumentOfPeriapsis.toDouble()))
				val c3 = (otherPlanetSemiMajorAxis + minimumDistance) * (1 - Math.pow(otherPlanetEccentricity.toDouble(), 2e0)) * eccentricity * Math.sin(Math.toRadians(-argumentOfPeriapsis)) - semiMajorAxis * (1 - Math.pow(eccentricity.toDouble(), 2e0)) * otherPlanetEccentricity * Math.sin(Math.toRadians(-otherPlanetArgumentOfPeriapsis.toDouble()))
				// c1 + c2*cos(x) + c3*sin(x) = 0
				val R = Math.sqrt(Math.pow(c2, 2e0) + Math.pow(c3, 2e0))
				if (1 < Math.abs(-c1 / R)) {
					i++
					continue
				}
			}

			semiMajorAxis = Math.sqrt(parentMass) * (0.8 + 29.2 * randomNumbersClass.nextDouble()) * 1 / (1e16)
			eccentricity = 0 + (0.85 - (0.85 / (1 + semiMajorAxis / 30))) * randomNumbersClass.nextDouble()
			argumentOfPeriapsis = 360 * randomNumbersClass.nextDouble()

			i = 0
			breakCounter++

			if (breakCounter > 1000) {
				return null
			}
		}

		val solidPlanetRadius = 6371e0 //Fix me
		var gasPlanetRadius = 1e0 // Fix me

		if (gasPlanetRadius > solidPlanetRadius) {
			gasPlanetRadius = solidPlanetRadius
		}

		val solidPlanetDensity = 5000 + 1000 * randomNumbersClass.nextDouble()
		val gasPlanetDensity = 500 + 500 * randomNumbersClass.nextDouble()  // depends on radius, fix later

		val mass = (solidPlanetDensity - gasPlanetDensity) * 4 * Math.pow(solidPlanetRadius, (3).toDouble()) * 10e9 + gasPlanetDensity * 4 * Math.pow(gasPlanetRadius, (3).toDouble()) * 10e9

//		println (parentMass)
//		println(semiMajorAxis)

		val planet = addPlanet(parentEntity, mass, 6371f, "Dummy planet", semiMajorAxis.toFloat(), eccentricity.toFloat(), argumentOfPeriapsis.toFloat(), meanAnomaly.toFloat())

		//add moons

		return planet
	}

	fun addPlanet(planetParent: Entity, planetMass: Double, planetRadius: Float, planetName: String, semiMajorAxis: Float, eccentricity: Float, argumentOfPeriapsis: Float, meanAnomaly: Float): Entity {

		val entity = Entity()
		entity.add(PositionComponent())
		entity.add(RenderComponent())
		entity.add(CircleComponent().apply { radius = planetRadius })
		entity.add(NameComponent().apply { name = planetName })
		entity.add(MassComponent().apply { mass = planetMass })
		entity.add(OrbitComponent().apply { parent = planetParent; a_semiMajorAxis = semiMajorAxis; e_eccentricity = eccentricity; w_argumentOfPeriapsis = argumentOfPeriapsis; M_meanAnomaly = meanAnomaly })

		engine.addEntity(entity)

		return entity
	}

	fun addMoon(moonParent: Entity, moonMass: Double, moonRadius: Float, moonName: String, semiMajorAxis: Float, eccentricity: Float, argumentOfPeriapsis: Float, meanAnomaly: Float): Entity {

		val entity = Entity()
		entity.add(PositionComponent())
		entity.add(RenderComponent())
		entity.add(CircleComponent().apply { radius = moonRadius })
		entity.add(NameComponent().apply { name = moonName })
		entity.add(MassComponent().apply { mass = moonMass })
		entity.add(OrbitComponent().apply { parent = moonParent; a_semiMajorAxis = semiMajorAxis; e_eccentricity = eccentricity; w_argumentOfPeriapsis = argumentOfPeriapsis; M_meanAnomaly = meanAnomaly })

		engine.addEntity(entity)

		return entity
	}

	fun addAsteroid(asteroidParent: Entity, asteroidMass: Double, asteroidRadius: Float, asteroidName: String, semiMajorAxis: Float, eccentricity: Float, argumentOfPeriapsis: Float, meanAnomaly: Float): Entity {

		val entity = Entity()
		entity.add(PositionComponent())
		entity.add(RenderComponent())
		entity.add(CircleComponent().apply { radius = asteroidRadius })
		entity.add(NameComponent().apply { name = asteroidName })
		entity.add(MassComponent().apply { mass = asteroidMass })
		entity.add(OrbitComponent().apply { parent = asteroidParent; a_semiMajorAxis = semiMajorAxis; e_eccentricity = eccentricity; w_argumentOfPeriapsis = argumentOfPeriapsis; M_meanAnomaly = meanAnomaly })

		engine.addEntity(entity)

		return entity
	}

	fun addGravityNode(gravityNodeMass: Double) {
		//add code later
	}

	fun update(deltaGameTime: Int) {

		//TODO use readlock during update

		lock.write {
			engine.update(deltaGameTime.toFloat())

			//TODO send changes over network
		}
	}
}