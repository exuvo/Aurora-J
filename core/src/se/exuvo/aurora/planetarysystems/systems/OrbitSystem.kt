package se.exuvo.aurora.planetarysystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.Entity
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.Viewport
import com.sun.xml.internal.ws.api.pipe.Engine
import org.apache.log4j.Logger
import se.exuvo.aurora.planetarysystems.components.MassComponent
import se.exuvo.aurora.planetarysystems.components.OrbitComponent
import se.exuvo.aurora.planetarysystems.components.TimedMovementComponent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Vector2D
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.utils.getUUID
import se.exuvo.aurora.utils.forEach
import se.exuvo.settings.Settings
import java.util.Collections
import se.exuvo.aurora.utils.Units

//TODO sorted system by no parents first then outwards
class OrbitSystem : GalaxyTimeIntervalIteratingSystem(FAMILY, 1 * 60) {
	companion object {
		val FAMILY = Aspect.all(OrbitComponent::class.java, TimedMovementComponent::class.java)
		val gravitationalConstant = 6.67408e-11
	}

	val log = Logger.getLogger(this.javaClass)

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
		val orbitalPeriod = 2 * Math.PI * Math.sqrt(Math.pow(1000.0 * a_semiMajorAxis, 3.0) / (parentMass * gravitationalConstant))

		if (Double.NaN.equals(orbitalPeriod) || orbitalPeriod < 1 * 60 * 60) {
			throw RuntimeException("orbitalPeriod ${orbitalPeriod}s is invalid for entityID $entityID")
		}

		// 1 point each day
		val points = Math.min(Math.max((orbitalPeriod / (24 * 60 * 60)).toInt(), 5), 1000)
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

			var dE = (E_eccentricAnomaly - orbit.e_eccentricity * Math.sin(E_eccentricAnomaly) - M_meanAnomalyRad) / (1f - orbit.e_eccentricity * Math.cos(E_eccentricAnomaly));
			E_eccentricAnomaly -= dE;

//			println("dE $dE")

			attempts++;
			if (Math.abs(dE) < 1e-5) {
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
		val P: Double = Units.AU * orbit.a_semiMajorAxis * (Math.cos(E_eccentricAnomaly) - orbit.e_eccentricity)
		val Q: Double = Units.AU * orbit.a_semiMajorAxis * Math.sin(E_eccentricAnomaly) * Math.sqrt(1 - Math.pow(orbit.e_eccentricity.toDouble(), 2.0))

//		println("orbitalPeriod ${orbitalPeriod / (24 * 60 * 60)} days, E_eccentricAnomaly $E_eccentricAnomaly, P $P, Q $Q")

		position.set(P, Q)
		position.rotate(orbit.w_argumentOfPeriapsis.toDouble())
	}

	var tempPosition = Vector2D()
	var oldPosition = Vector2L()
	var tempVelocity = Vector2()
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
		tempVelocity.set(oldPosition.x.toFloat(), oldPosition.y.toFloat()).scl(1f / interval)
		
		movement.value.velocity.set(tempVelocity)
		movement.time = galaxy.time
	}

	private val shapeRenderer by lazy { GameServices[ShapeRenderer::class] }

	fun render(cameraOffset: Vector2L) {
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