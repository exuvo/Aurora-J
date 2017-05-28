package se.exuvo.aurora.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntityListener
import com.badlogic.ashley.core.Family
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.Viewport
import org.apache.log4j.Logger
import se.exuvo.aurora.components.MassComponent
import se.exuvo.aurora.components.OrbitComponent
import se.exuvo.aurora.components.PositionComponent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Vector2D
import se.exuvo.aurora.utils.Vector2Long
import se.exuvo.settings.Settings

//TODO sorted system by no parents first outwards
class OrbitSystem : GalaxyTimeIntervalIteratingSystem(OrbitSystem.FAMILY, 1 * 60), EntityListener {
	companion object {
		val FAMILY = Family.all(OrbitComponent::class.java, PositionComponent::class.java).get()
		val gravitationalConstant = 6.67408e-11
		val AU = 149597870.7 // In km
	}

	val log = Logger.getLogger(this.javaClass)

	private val orbitMapper = ComponentMapper.getFor(OrbitComponent::class.java)
	private val massMapper = ComponentMapper.getFor(MassComponent::class.java)
	private val positionMapper = ComponentMapper.getFor(PositionComponent::class.java)
	private val orbitsCache = HashMap<Entity, OrbitCache>()

	override fun addedToEngine(engine: Engine) {
		super.addedToEngine(engine)

		engine.addEntityListener(FAMILY, this)
	}

	override fun removedFromEngine(engine: Engine) {
		super.removedFromEngine(engine)

		engine.removeEntityListener(this)
	}

	override fun entityAdded(entity: Entity) {

		val orbit = orbitMapper.get(entity)
		val parentMass = massMapper.get(orbit.parent).mass

		if (orbit.e_eccentricity > 0.95) {
			log.warn("Orbital eccentricity over 0.95 won't work: ${orbit.e_eccentricity}")
		}

		// In km
		val a_semiMajorAxis = AU * orbit.a_semiMajorAxis
		val periapsis = a_semiMajorAxis * (1.0 - orbit.e_eccentricity)
		val apoapsis = a_semiMajorAxis * (1.0 + orbit.e_eccentricity)
		
		// In seconds
		val orbitalPeriod = 2 * Math.PI * Math.sqrt(Math.pow(1000.0 * a_semiMajorAxis, 3.0) / (parentMass * gravitationalConstant))

		if (Double.NaN.equals(orbitalPeriod) || orbitalPeriod < 1 * 60 * 60) {
			throw RuntimeException("orbitalPeriod $orbitalPeriod is invalid for entity $entity")
		}

		// 1 point each day
		val points = Math.min(Math.max((orbitalPeriod / (24 * 60 * 60)).toInt(), 5), 1000)
		log.debug("Calculating orbit for new entity $entity using $points points, orbitalPeriod ${orbitalPeriod / (24 * 60 * 60)} days")
		val orbitPoints = Array<Vector2D>(points, { Vector2D() })

		// If set more dots represent higher speed, else the time between dots is constant
		val invert = if (Settings.getBol("Orbits.DotsRepresentSpeed")) MathUtils.PI else 0f

		for (i in 0..points - 1) {
			val M_meanAnomaly = orbit.M_meanAnomaly + (360.0 * i) / points
			val E_eccentricAnomaly = calculateEccentricAnomalyFromMeanAnomaly(orbit, M_meanAnomaly) + invert
			calculateOrbitalPositionFromEccentricAnomaly(orbit, E_eccentricAnomaly, orbitPoints[i])

//			println("Calculated $i with M_meanAnomaly $M_meanAnomaly")
		}

		orbitsCache.put(entity, OrbitCache(orbitalPeriod, apoapsis, periapsis, orbitPoints))
	}

	override fun entityRemoved(entity: Entity) {
		orbitsCache.remove(entity)
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
		val P: Double = AU * orbit.a_semiMajorAxis * (Math.cos(E_eccentricAnomaly) - orbit.e_eccentricity)
		val Q: Double = AU * orbit.a_semiMajorAxis * Math.sin(E_eccentricAnomaly) * Math.sqrt(1 - Math.pow(orbit.e_eccentricity.toDouble(), 2.0))

//		println("orbitalPeriod ${orbitalPeriod / (24 * 60 * 60)} days, E_eccentricAnomaly $E_eccentricAnomaly, P $P, Q $Q")

		position.set(P, Q)
		position.rotate(orbit.w_argumentOfPeriapsis.toDouble())
	}

	var tempPosition = Vector2D()
	override fun processEntity(entity: Entity) {

		val orbit = orbitMapper.get(entity)
		val orbitCache: OrbitCache = orbitsCache.get(entity)!!
		val orbitalPeriod = orbitCache.orbitalPeriod

		val M_meanAnomaly = orbit.M_meanAnomaly + 360 * ((galaxy.time % orbitalPeriod.toLong()) / orbitalPeriod)

//		println("M_meanAnomaly $M_meanAnomaly")

		val E_eccentricAnomaly = calculateEccentricAnomalyFromMeanAnomaly(orbit, M_meanAnomaly)
		calculateOrbitalPositionFromEccentricAnomaly(orbit, E_eccentricAnomaly, tempPosition)

		val parentEntity = orbit.parent
		val parentPosition = positionMapper.get(parentEntity).position
		val position = positionMapper.get(entity).position
		position.set(parentPosition.x + tempPosition.x.toLong(), parentPosition.y + tempPosition.y.toLong())
	}

	private val shapeRenderer by lazy { GameServices[ShapeRenderer::class.java] }

	fun render(viewport: Viewport, cameraOffset: Vector2Long) {
		viewport.apply()
		shapeRenderer.projectionMatrix = viewport.camera.combined

		shapeRenderer.color = Color.GRAY
		shapeRenderer.begin(ShapeRenderer.ShapeType.Point);
		entities.forEach {
			val orbit = orbitMapper.get(it)
			val orbitCache: OrbitCache = orbitsCache.get(it)!!
			val parentEntity = orbit.parent
			val parentPosition = positionMapper.get(parentEntity).position
			val x = (parentPosition.x - cameraOffset.x).toFloat()
			val y = (parentPosition.y - cameraOffset.y).toFloat()

			for (point in orbitCache.orbitPoints) {
				shapeRenderer.point((x + point.x).toFloat(), (y + point.y).toFloat(), 0f);
			}
		}
		shapeRenderer.end();
	}

	data class OrbitCache(val orbitalPeriod: Double, val apoapsis: Double, val periapsis: Double, val orbitPoints: Array<Vector2D>)
}