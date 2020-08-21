package se.exuvo.aurora.starsystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.badlogic.gdx.math.MathUtils
import org.apache.commons.math3.util.FastMath
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.starsystems.StarSystem
import se.exuvo.aurora.starsystems.components.MassComponent
import se.exuvo.aurora.starsystems.components.MovementValues
import se.exuvo.aurora.starsystems.components.OrbitComponent
import se.exuvo.aurora.starsystems.components.TimedMovementComponent
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.utils.Vector2D
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.utils.getUUID
import se.exuvo.settings.Settings
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.set

//TODO sorted system by no parents first then outwards. Now requires entityID order to match parent order
class OrbitSystem : GalaxyTimeIntervalIteratingSystem(FAMILY, 24 * 60 * 60) {
	companion object {
		@JvmField val FAMILY = Aspect.all(OrbitComponent::class.java, TimedMovementComponent::class.java)
		const val gravitationalConstant = 6.67408e-11
		@JvmField val log = LogManager.getLogger(OrbitSystem::class.java)
	}

	lateinit private var orbitMapper: ComponentMapper<OrbitComponent>
	lateinit private var massMapper: ComponentMapper<MassComponent>
	lateinit private var movementMapper: ComponentMapper<TimedMovementComponent>
	
	@Wire
	lateinit private var system: StarSystem
	
	val orbitsCache = ConcurrentHashMap<Int, OrbitCache>()
	private val moonsCache = HashMap<Int, MutableSet<Int>?>()

	override fun inserted(entityID: Int) {
		val orbit = orbitMapper.get(entityID)
		val parentMass = massMapper.get(orbit.parent).mass

		if (orbit.e_eccentricity > 0.95) {
			log.warn("Orbital eccentricity over 0.95 won't work: ${orbit.e_eccentricity}")
		}

		// In km
		val a_semiMajorAxis = Units.AU * orbit.a_semiMajorAxis
		val periapsis = a_semiMajorAxis * (1.0 - orbit.e_eccentricity)
		val apoapsis = a_semiMajorAxis * (1.0 + orbit.e_eccentricity)

		// In seconds
		val orbitalPeriod = 2 * FastMath.PI * FastMath.sqrt(FastMath.pow(1000.0 * a_semiMajorAxis, 3.0) / (parentMass * gravitationalConstant))

		if (Double.NaN.equals(orbitalPeriod) || orbitalPeriod < 1 * 60 * 60) {
			throw RuntimeException("orbitalPeriod ${orbitalPeriod}s is invalid for entityID $entityID")
		}

		// 1 point each day
		val points = FastMath.min(FastMath.max((orbitalPeriod / (24 * 60 * 60)).toInt(), 5), 1000)
		log.debug("Calculating orbit for new entityID ${getUUID(entityID, world)} using $points points, orbitalPeriod ${orbitalPeriod / (24 * 60 * 60)} days")
		val orbitPoints = Array<Vector2D>(points, { Vector2D() })

		// If set more dots represent higher speed, else the time between dots is constant
		val invert = if (Settings.getBol("Systems/Orbit/dotsRepresentSpeed", true)) MathUtils.PI else 0f

		for (i in 0 until points) {
			val M_meanAnomaly = orbit.M_meanAnomaly + (360.0 * i) / points
			val E_eccentricAnomaly = calculateEccentricAnomalyFromMeanAnomaly(orbit, M_meanAnomaly) + invert
			calculateOrbitalPositionFromEccentricAnomaly(orbit, E_eccentricAnomaly, orbitPoints[i])

//			println("Calculated $i with M_meanAnomaly $M_meanAnomaly")
		}
		
		orbitsCache[entityID] = OrbitCache(orbitalPeriod, apoapsis, periapsis, orbitPoints)

		var moonsSet = moonsCache[orbit.parent]

		if (moonsSet == null) {
			moonsSet = HashSet<Int>()
			moonsCache[orbit.parent] = moonsSet
		}

		moonsSet.add(entityID)
		
		process(entityID)
	}

	override fun removed(entityID: Int) {
		orbitsCache.remove(entityID)

		val orbit = orbitMapper.get(entityID)

		val moonsSet = moonsCache[orbit.parent]
		moonsSet?.remove(entityID)
	}

	fun getMoons(entityID: Int): Set<Int> {
		return moonsCache[entityID] ?: Collections.emptySet()
	}

	private fun calculateEccentricAnomalyFromMeanAnomaly(orbit: OrbitComponent, M_meanAnomaly: Double): Double {
		// Calculating orbits https://space.stackexchange.com/questions/8911/determining-orbital-position-at-a-future-point-in-time
		val M_meanAnomalyRad = M_meanAnomaly * MathUtils.degreesToRadians

		// Solve numerically using Newtons method
		var E_eccentricAnomaly = M_meanAnomalyRad
		var attempts = 0
		while (true) {

			val dE = (E_eccentricAnomaly - orbit.e_eccentricity * FastMath.sin(E_eccentricAnomaly) - M_meanAnomalyRad) / (1f - orbit.e_eccentricity * FastMath.cos(E_eccentricAnomaly));
			E_eccentricAnomaly -= dE;

//			println("dE $dE")

			attempts++;
			if (FastMath.abs(dE) < 1e-5) {
				break
			} else if (attempts >= 10) {
				log.warn("Calculating orbital position took more than $attempts attempts")
				break
			}
		}

		return E_eccentricAnomaly
	}

	private fun calculateOrbitalPositionFromEccentricAnomaly(orbit: OrbitComponent, E_eccentricAnomaly: Double, position: Vector2D) {
		// Coordinates with P+ towards periapsis
		val P: Double = Units.AU * orbit.a_semiMajorAxis * (FastMath.cos(E_eccentricAnomaly) - orbit.e_eccentricity)
		val Q: Double = Units.AU * orbit.a_semiMajorAxis * FastMath.sin(E_eccentricAnomaly) * FastMath.sqrt(1 - FastMath.pow(orbit.e_eccentricity.toDouble(), 2.0))

//		println("orbitalPeriod ${orbitalPeriod / (24 * 60 * 60)} days, E_eccentricAnomaly $E_eccentricAnomaly, P $P, Q $Q")

		position.set(P, Q)
		position.rotate(orbit.w_argumentOfPeriapsis.toDouble())
	}

	val relativePosition = Vector2D()
	val newVelocity = Vector2L()
	val tmpPosition = Vector2L()
	
	override fun process(entityID: Int) {
		val today = galaxy.time
		val dayLength = 24 * 60 * 60
		val tomorrow = today + dayLength
		
		val orbit = orbitMapper.get(entityID)
		val orbitCache: OrbitCache = orbitsCache[entityID]!!
		val orbitalPeriod = orbitCache.orbitalPeriod

		val M_meanAnomalyToday =    orbit.M_meanAnomaly + 360 * ((today % orbitalPeriod.toLong()) / orbitalPeriod)
		val M_meanAnomalyTomorrow = orbit.M_meanAnomaly + 360 * ((tomorrow % orbitalPeriod.toLong()) / orbitalPeriod)

//		println("M_meanAnomaly $M_meanAnomaly")

		val E_eccentricAnomalyToday =    calculateEccentricAnomalyFromMeanAnomaly(orbit, M_meanAnomalyToday)
		val E_eccentricAnomalyTomorrow = calculateEccentricAnomalyFromMeanAnomaly(orbit, M_meanAnomalyTomorrow)
		
		// Today
		calculateOrbitalPositionFromEccentricAnomaly(orbit, E_eccentricAnomalyToday, relativePosition)
		relativePosition.scl(1000.0) // km to m

		val parentEntity = orbit.parent
		var parentMovement = movementMapper.get(parentEntity).get(today)
		var parentPosition = parentMovement.value.position
		
		val movement = movementMapper.get(entityID)
		val position = movement.previous.value.position
		
		position.set(parentPosition.x + relativePosition.x.toLong(), parentPosition.y + relativePosition.y.toLong())
		newVelocity.set(position)
		
		movement.previous.time = today
		
		// Tomorrow
		calculateOrbitalPositionFromEccentricAnomaly(orbit, E_eccentricAnomalyTomorrow, relativePosition)
		relativePosition.scl(1000.0) // km to m
		
		parentMovement = movementMapper.get(parentEntity).get(tomorrow)
		parentPosition = parentMovement.value.position
		
		tmpPosition.set(parentPosition.x + relativePosition.x.toLong(), parentPosition.y + relativePosition.y.toLong())
		
		newVelocity.sub(tmpPosition)
		newVelocity.scl(100.0 / interval)
		movement.previous.value.velocity.set(newVelocity)
		
		movement.setPrediction(MovementValues(tmpPosition.cpy(), newVelocity.cpy(), Vector2L()), tomorrow)
		
		system.changed(entityID, movementMapper)
	}

	data class OrbitCache(val orbitalPeriod: Double, val apoapsis: Double, val periapsis: Double, val orbitPoints: Array<Vector2D>)
}
