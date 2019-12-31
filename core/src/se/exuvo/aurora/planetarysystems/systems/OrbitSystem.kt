package se.exuvo.aurora.planetarysystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.AuroraGame
import se.exuvo.aurora.planetarysystems.components.MassComponent
import se.exuvo.aurora.planetarysystems.components.OrbitComponent
import se.exuvo.aurora.planetarysystems.components.TimedMovementComponent
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.utils.Vector2D
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.utils.forEach
import se.exuvo.aurora.utils.getUUID
import se.exuvo.settings.Settings
import java.util.Collections
import org.apache.commons.math3.util.FastMath

//TODO sorted system by no parents first then outwards
class OrbitSystem : GalaxyTimeIntervalIteratingSystem(FAMILY, 1 * 60) {
	companion object {
		val FAMILY = Aspect.all(OrbitComponent::class.java, TimedMovementComponent::class.java)
		const val gravitationalConstant = 6.67408e-11
	}

	val log = LogManager.getLogger(this.javaClass)

	lateinit private var orbitMapper: ComponentMapper<OrbitComponent>
	lateinit private var massMapper: ComponentMapper<MassComponent>
	lateinit private var movementMapper: ComponentMapper<TimedMovementComponent>
	private val orbitsCache = HashMap<Int, OrbitCache>()
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
		log.debug("Calculating orbit for new entityID ${world.getEntity(entityID).getUUID()} using $points points, orbitalPeriod ${orbitalPeriod / (24 * 60 * 60)} days")
		val orbitPoints = Array<Vector2D>(points, { Vector2D() })

		// If set more dots represent higher speed, else the time between dots is constant
		val invert = if (Settings.getBol("Systems/Orbit/dotsRepresentSpeed", true)) MathUtils.PI else 0f

		for (i in 0..points - 1) {
			val M_meanAnomaly = orbit.M_meanAnomaly + (360.0 * i) / points
			val E_eccentricAnomaly = calculateEccentricAnomalyFromMeanAnomaly(orbit, M_meanAnomaly) + invert
			calculateOrbitalPositionFromEccentricAnomaly(orbit, E_eccentricAnomaly, orbitPoints[i])

//			println("Calculated $i with M_meanAnomaly $M_meanAnomaly")
		}

		orbitsCache.put(entityID, OrbitCache(orbitalPeriod, apoapsis, periapsis, orbitPoints))

		var moonsSet = moonsCache[orbit.parent]

		if (moonsSet == null) {
			moonsSet = HashSet<Int>()
			moonsCache[orbit.parent] = moonsSet
		}

		moonsSet.add(entityID)
	}

	override fun removed(entityID: Int) {
		orbitsCache.remove(entityID)

		val orbit = orbitMapper.get(entityID)

		var moonsSet = moonsCache[orbit.parent]

		if (moonsSet != null) {
			moonsSet.remove(entityID)
		}
	}

	fun getMoons(entityID: Int): Set<Int> {
		return moonsCache[entityID] ?: Collections.emptySet()
	}

	private fun calculateEccentricAnomalyFromMeanAnomaly(orbit: OrbitComponent, M_meanAnomaly: Double): Double {

		// Calculating orbits https://space.stackexchange.com/questions/8911/determining-orbital-position-at-a-future-point-in-time
		var M_meanAnomalyRad = M_meanAnomaly * MathUtils.degreesToRadians

		// Solve numerically using Newtons method
		var E_eccentricAnomaly = M_meanAnomalyRad
		var attempts = 0
		while (true) {

			var dE = (E_eccentricAnomaly - orbit.e_eccentricity * FastMath.sin(E_eccentricAnomaly) - M_meanAnomalyRad) / (1f - orbit.e_eccentricity * FastMath.cos(E_eccentricAnomaly));
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

	var tempPosition = Vector2D()
	var oldPosition = Vector2L()
	
	//TODO set timemovement next to next days position to use linear interpolation during the day
	override fun process(entityID: Int) {

		val orbit = orbitMapper.get(entityID)
		val orbitCache: OrbitCache = orbitsCache.get(entityID)!!
		val orbitalPeriod = orbitCache.orbitalPeriod

		val M_meanAnomaly = orbit.M_meanAnomaly + 360 * ((galaxy.time % orbitalPeriod.toLong()) / orbitalPeriod)

//		println("M_meanAnomaly $M_meanAnomaly")

		val E_eccentricAnomaly = calculateEccentricAnomalyFromMeanAnomaly(orbit, M_meanAnomaly)
		calculateOrbitalPositionFromEccentricAnomaly(orbit, E_eccentricAnomaly, tempPosition)
		tempPosition.scl(1000.0) // km to m

		val parentEntity = orbit.parent
		val parentMovement = movementMapper.get(parentEntity).get(galaxy.time)
		val parentPosition = parentMovement.value.position
		
		val movement = movementMapper.get(entityID).previous
		val position = movement.value.position
		
		oldPosition.set(position)
		position.set(parentPosition.x + tempPosition.x.toLong(), parentPosition.y + tempPosition.y.toLong())
		
		oldPosition.sub(position)
		oldPosition.scl(1.0 / interval)
		
		movement.value.velocity.set(oldPosition)
		movement.time = galaxy.time
	}

	fun render(cameraOffset: Vector2L) {
		val shapeRenderer = AuroraGame.currentWindow.shapeRenderer
		
		shapeRenderer.color = Color.GRAY
		shapeRenderer.begin(ShapeRenderer.ShapeType.Point);
		subscription.getEntities().forEach { entityID ->
			val orbit = orbitMapper.get(entityID)
			val orbitCache: OrbitCache = orbitsCache.get(entityID)!!
			val parentEntity = orbit.parent
			val parentMovement = movementMapper.get(parentEntity).get(galaxy.time).value
			val x = (parentMovement.getXinKM() - cameraOffset.x).toDouble()
			val y = (parentMovement.getYinKM() - cameraOffset.y).toDouble()

			for (point in orbitCache.orbitPoints) {
				shapeRenderer.point((x + point.x).toFloat(), (y + point.y).toFloat(), 0f);
			}
		}
		shapeRenderer.end();
	}

	data class OrbitCache(val orbitalPeriod: Double, val apoapsis: Double, val periapsis: Double, val orbitPoints: Array<Vector2D>)
}