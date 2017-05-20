package se.exuvo.aurora.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IntervalIteratingSystem
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.thedeadpixelsociety.ld34.components.OrbitComponent
import com.thedeadpixelsociety.ld34.components.PositionComponent
import se.exuvo.aurora.Galaxy
import se.exuvo.aurora.utils.GameServices

//TODO sorted system by no parents first outwards
class OrbitSystem : GalaxyTimeIntervalIteratingSystem(OrbitSystem.FAMILY, 1 * 60) {
	companion object {
		val FAMILY = Family.all(OrbitComponent::class.java, PositionComponent::class.java).get()
	}

	private val gravitationalConstant = 6.67408e-11
	private val AU = 100.0 //149597870700.0 / 1000.0
	
	private val orbitMapper = ComponentMapper.getFor(OrbitComponent::class.java)
	private val positionMapper = ComponentMapper.getFor(PositionComponent::class.java)

	override fun processEntity(entity: Entity) {

		val orbit = orbitMapper.get(entity)
		val parentEntity = orbit.parent
		val parentPosition: Vector2 = positionMapper.get(parentEntity).position

//		val periapsis = orbit.a_semiMajorAxis * (1f - orbit.e_eccentricity)
//		val apoapsis = orbit.a_semiMajorAxis * (1f + orbit.e_eccentricity)
		val orbitalPeriod = 2 * Math.PI * Math.sqrt(Math.pow(orbit.a_semiMajorAxis.toDouble(), 3.0) / gravitationalConstant)
		
		val M_meanAnomaly: Double = orbit.M_meanAnomaly + 360 * ((galaxy.time % orbitalPeriod.toLong()) / orbitalPeriod)
		
//		println("M_meanAnomaly $M_meanAnomaly")
		
		// Calculating orbits https://space.stackexchange.com/questions/8911/determining-orbital-position-at-a-future-point-in-time
		var M_meanAnomalyRad = (M_meanAnomaly * MathUtils.degreesToRadians).toDouble()
		
		// Solve numerically using Newtons method
		var E_eccentricAnomaly = M_meanAnomalyRad
		while (true) {
		
			var dE = (E_eccentricAnomaly - orbit.e_eccentricity * Math.sin(E_eccentricAnomaly) - M_meanAnomalyRad) / (1f - orbit.e_eccentricity * Math.cos(E_eccentricAnomaly));
			E_eccentricAnomaly -= dE;

			if (Math.abs(dE) < 1e-6) {
				break;
			}
		}

		// Coordinates with P+ towards periapsis
		val P = AU * orbit.a_semiMajorAxis * (Math.cos(E_eccentricAnomaly) - orbit.e_eccentricity)
		val Q = AU * orbit.a_semiMajorAxis * Math.sin(E_eccentricAnomaly) * Math.sqrt(1 - Math.pow(orbit.e_eccentricity.toDouble(), 2.0))

//		println("orbitalPeriod ${orbitalPeriod / (24 * 60 * 60)} days, E_eccentricAnomaly $E_eccentricAnomaly, P $P, Q $Q")

		val position = positionMapper.get(entity)
		position.position.set(P.toFloat(), Q.toFloat())
		position.position.rotate(orbit.w_argumentOfPeriapsis)
		position.position.add(parentPosition)
	}
}