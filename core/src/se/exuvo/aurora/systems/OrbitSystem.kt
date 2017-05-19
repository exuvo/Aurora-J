package se.exuvo.aurora.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.gdx.math.Vector2
import com.thedeadpixelsociety.ld34.components.MassComponent
import com.thedeadpixelsociety.ld34.components.OrbitComponent
import com.thedeadpixelsociety.ld34.components.PositionComponent

//TODO sorted system by no parents first outwards
class OrbitSystem : DailyIteratingSystem(OrbitSystem.FAMILY) {
	companion object {
		val FAMILY = Family.all(OrbitComponent::class.java, PositionComponent::class.java).get()
	}

	private val orbitMapper = ComponentMapper.getFor(OrbitComponent::class.java)
	private val positionMapper = ComponentMapper.getFor(PositionComponent::class.java)
	private val massMapper = ComponentMapper.getFor(MassComponent::class.java)
	private val gravitationalConstant = 6.67408 * Math.pow(10.0, -11.0)

	override fun processEntity(entity: Entity) {

		val day = galaxy.day
//		val centuriesFrom0 = day.toFloat() / (365 * 100)

		val orbit = orbitMapper.get(entity)
		val parentEntity = orbit.parent
		var parentMass = massMapper.get(parentEntity).mass
		val parentPosition: Vector2 = positionMapper.get(parentEntity).position

		// Calculating orbits https://space.stackexchange.com/questions/8911/determining-orbital-position-at-a-future-point-in-time
		val periapsis = orbit.a_semiMajorAxis * (1f - orbit.e_eccentricity)
		val apoapsis = orbit.a_semiMajorAxis * (1f + orbit.e_eccentricity)
		val orbitalPeriod = 2 * Math.PI * Math.sqrt(Math.pow(orbit.a_semiMajorAxis.toDouble(), 3.0) / (parentMass * gravitationalConstant))

		// Solve numerically using Newtons method
		var E_eccentricAnomaly = orbit.M_meanAnomaly.toDouble();
		while (true) {
		
			var dE = (E_eccentricAnomaly - orbit.e_eccentricity * Math.sin(E_eccentricAnomaly) - orbit.M_meanAnomaly) / (1 - orbit.e_eccentricity * Math.cos(E_eccentricAnomaly));
			E_eccentricAnomaly -= dE;

			if (Math.abs(dE) < 1e-6) {
				break;
			}
		}

		// Coordinates with P+ towards periapsis
		val P = orbit.a_semiMajorAxis * (Math.cos(E_eccentricAnomaly) - orbit.e_eccentricity)
		val Q = orbit.a_semiMajorAxis * Math.sin(E_eccentricAnomaly) * Math.sqrt(1 - Math.pow(orbit.e_eccentricity.toDouble(), 2.0))

		println("periapsis $periapsis, apoapsis $apoapsis, orbitalPeriod $orbitalPeriod, E_eccentricAnomaly $E_eccentricAnomaly, P $P, Q $Q")

		val position = positionMapper.get(entity)
		position.position.set(P.toFloat(), Q.toFloat())
		position.position.rotate(orbit.w_argumentOfPeriapsis)
//		position.position.add(parentPosition)
	}
}