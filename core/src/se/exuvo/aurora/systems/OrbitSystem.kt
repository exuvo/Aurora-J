package se.exuvo.aurora.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntityListener
import com.badlogic.ashley.core.Family
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.Viewport
import com.thedeadpixelsociety.ld34.components.OrbitComponent
import com.thedeadpixelsociety.ld34.components.PositionComponent
import org.apache.log4j.Logger
import se.exuvo.aurora.utils.GameServices
import se.exuvo.settings.Settings

//TODO sorted system by no parents first outwards
class OrbitSystem : GalaxyTimeIntervalIteratingSystem(OrbitSystem.FAMILY, 1 * 60), EntityListener {
	companion object {
		val FAMILY = Family.all(OrbitComponent::class.java, PositionComponent::class.java).get()
	}

	val log = Logger.getLogger(this.javaClass)
	private val gravitationalConstant = 6.67408e-11
	private val AU = 100f //149597870700.0 / 1000.0

	private val orbitMapper = ComponentMapper.getFor(OrbitComponent::class.java)
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

		if (orbit.e_eccentricity > 1) {
			log.warn("Orbital eccentricity over 1 won't work: ${orbit.e_eccentricity}")
		}

		val periapsis = orbit.a_semiMajorAxis * (1f - orbit.e_eccentricity)
		val apoapsis = orbit.a_semiMajorAxis * (1f + orbit.e_eccentricity)
		val orbitalPeriod = (2 * Math.PI * Math.sqrt(Math.pow(orbit.a_semiMajorAxis.toDouble(), 3.0) / gravitationalConstant)).toFloat()

		// 1 point each day
		val points = Math.max((orbit.a_semiMajorAxis * AU).toInt(), 3)
//		val points = Math.max(((Math.PI * orbitalPeriod) / (24 * 60 * 60)).toInt(), 9)
		val orbitPoints = Array<Vector2>(points, { Vector2() })

		log.info("Calculating orbit for new entity $entity using $points points, orbitalPeriod ${orbitalPeriod / (24 * 60 * 60)} days")

		// If set more dots represent higher speed, else the time between dots is constant
		val invert = if (Settings.getBol("Orbits.DotsRepresentSpeed")) MathUtils.PI else 0f 

		for (i in 0..points - 1) {
			val M_meanAnomaly = orbit.M_meanAnomaly + (360f * i) / points
			val E_eccentricAnomaly = calculateEccentricAnomalyFromMeanAnomaly(orbit, M_meanAnomaly) + invert
			calculateOrbitalPositionFromEccentricAnomaly(orbit, E_eccentricAnomaly, orbitPoints[i])

//			println("Calculated $i with M_meanAnomaly $M_meanAnomaly")
		}

		orbitsCache.put(entity, OrbitCache(orbitalPeriod, apoapsis, periapsis, orbitPoints))
	}

	override fun entityRemoved(entity: Entity) {
		orbitsCache.remove(entity)
	}

	private fun calculateEccentricAnomalyFromMeanAnomaly(orbit: OrbitComponent, M_meanAnomaly: Float): Float {

		// Calculating orbits https://space.stackexchange.com/questions/8911/determining-orbital-position-at-a-future-point-in-time
		var M_meanAnomalyRad = M_meanAnomaly * MathUtils.degreesToRadians

		// Solve numerically using Newtons method
		var E_eccentricAnomaly = M_meanAnomalyRad
		var attempts = 0
		while (true) {

			var dE = (E_eccentricAnomaly - orbit.e_eccentricity * MathUtils.sin(E_eccentricAnomaly) - M_meanAnomalyRad) / (1f - orbit.e_eccentricity * MathUtils.cos(E_eccentricAnomaly));
			E_eccentricAnomaly -= dE;

//			println("dE $dE")

			attempts++;
			if (Math.abs(dE) < 1e-3) {
				break
			} else if (attempts >= 5) {
				log.warn("Calculating orbital position took more than $attempts")
				break
			}
		}

		return E_eccentricAnomaly
	}

	private fun calculateOrbitalPositionFromEccentricAnomaly(orbit: OrbitComponent, E_eccentricAnomaly: Float, position: Vector2) {

		// Coordinates with P+ towards periapsis
		val P: Float = AU * orbit.a_semiMajorAxis * (MathUtils.cos(E_eccentricAnomaly) - orbit.e_eccentricity)
		val Q: Float = AU * orbit.a_semiMajorAxis * MathUtils.sin(E_eccentricAnomaly) * Math.sqrt(1 - Math.pow(orbit.e_eccentricity.toDouble(), 2.0)).toFloat()

//		println("orbitalPeriod ${orbitalPeriod / (24 * 60 * 60)} days, E_eccentricAnomaly $E_eccentricAnomaly, P $P, Q $Q")

		position.set(P, Q)
		position.rotate(orbit.w_argumentOfPeriapsis)
	}

	override fun processEntity(entity: Entity) {

		val orbit = orbitMapper.get(entity)
		val orbitCache: OrbitCache = orbitsCache.get(entity)!!
		val orbitalPeriod = orbitCache.orbitalPeriod

		val M_meanAnomaly = orbit.M_meanAnomaly + 360 * ((galaxy.time % orbitalPeriod.toLong()) / orbitalPeriod)

//		println("M_meanAnomaly $M_meanAnomaly")

		val positionComponent = positionMapper.get(entity)
		val E_eccentricAnomaly = calculateEccentricAnomalyFromMeanAnomaly(orbit, M_meanAnomaly)
		calculateOrbitalPositionFromEccentricAnomaly(orbit, E_eccentricAnomaly, positionComponent.position)

		val parentEntity = orbit.parent
		val parentPosition: Vector2 = positionMapper.get(parentEntity).position
		positionComponent.position.add(parentPosition)
	}

	private val shapeRenderer by lazy { GameServices[ShapeRenderer::class.java] }

	fun render(viewport: Viewport) {
		viewport.apply()
		shapeRenderer.projectionMatrix = viewport.camera.combined

		shapeRenderer.color = Color.GRAY
		shapeRenderer.begin(ShapeRenderer.ShapeType.Point);
		entities.forEach {
			val orbit = orbitMapper.get(it)
			val orbitCache: OrbitCache = orbitsCache.get(it)!!
			val parentEntity = orbit.parent
			val parentPosition: Vector2 = positionMapper.get(parentEntity).position

			for (point in orbitCache.orbitPoints) {
				shapeRenderer.point(parentPosition.x + point.x, parentPosition.y + point.y, 0f);
			}
		}
		shapeRenderer.end();
	}

	data class OrbitCache(val orbitalPeriod: Float, val apoapsis: Float, val periapsis: Float, val orbitPoints: Array<Vector2>)
}