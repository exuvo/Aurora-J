package se.exuvo.aurora.planetarysystems.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import org.apache.log4j.Logger
import se.exuvo.aurora.planetarysystems.components.PositionComponent
import se.exuvo.aurora.planetarysystems.components.SolarIrradianceComponent
import se.exuvo.aurora.planetarysystems.components.SunComponent
import se.exuvo.aurora.utils.Vector2L

class SolarIrradianceSystem : GalaxyTimeIntervalIteratingSystem(FAMILY, 10 * 60) {
	companion object {
		val FAMILY = Family.all(SolarIrradianceComponent::class.java, PositionComponent::class.java).get()
		val SUNS_FAMILY = Family.all(SunComponent::class.java, PositionComponent::class.java).get()
	}

	val log = Logger.getLogger(this.javaClass)

	private val positionMapper = ComponentMapper.getFor(PositionComponent::class.java)
	private val irradianceMapper = ComponentMapper.getFor(SolarIrradianceComponent::class.java)
	private val sunIrradianceMapper = ComponentMapper.getFor(SunComponent::class.java)

	var suns: List<Sun> = emptyList()

	override fun update(deltaTime: Float) {

		val sunEntites = engine.getEntitiesFor(SUNS_FAMILY)

		if (sunEntites.size() == 0) {

			if (suns.isNotEmpty()) {
				suns = emptyList()
			}

		} else if (sunEntites.size() != suns.size) {

			val mutableSuns = ArrayList<Sun>(sunEntites.size())

			for (sun in sunEntites) {

				var solarConstant = sunIrradianceMapper.get(sun).solarConstant
				var position = positionMapper.get(sun).position

				mutableSuns.add(Sun(position, solarConstant))
			}

			suns = mutableSuns
		}

		super.update(deltaTime)
	}

	override fun processEntity(entity: Entity) {

		val position = positionMapper.get(entity).position

		var totalIrradiance = 0.0

		for (sun in suns) {
			val distance = position.dst(sun.position) / 1000

			// https://en.wikipedia.org/wiki/Inverse-square_law
			val irradiance = sun.solarConstant * Math.pow(OrbitSystem.AU, 2.0) / Math.pow(distance, 2.0)
			totalIrradiance += irradiance
//			println("distance ${distance / OrbitSystem.AU} AU, irradiance $irradiance ${(100 * irradiance / sun.solarConstant).toInt()}%")
			
			//TODO calculate correct irradiance if we are inside the sun 
		}

		irradianceMapper.get(entity).irradiance = totalIrradiance.toInt()
	}

	data class Sun(val position: Vector2L, val solarConstant: Int)
}