package se.exuvo.aurora.planetarysystems.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntityListener
import com.badlogic.ashley.core.Family
import org.apache.log4j.Logger
import se.exuvo.aurora.galactic.ChargedPart
import se.exuvo.aurora.galactic.PoweredPart
import se.exuvo.aurora.galactic.PoweringPart
import se.exuvo.aurora.planetarysystems.components.PowerComponent
import se.exuvo.aurora.planetarysystems.components.SolarIrradianceComponent
import se.exuvo.aurora.planetarysystems.components.SunComponent
import se.exuvo.aurora.planetarysystems.components.TimedMovementComponent
import se.exuvo.aurora.utils.Vector2L

class SolarIrradianceSystem : GalaxyTimeIntervalIteratingSystem(FAMILY, 1 * 60), EntityListener {
	companion object {
		val FAMILY = Family.all(SolarIrradianceComponent::class.java, TimedMovementComponent::class.java).get()
		val SUNS_FAMILY = Family.all(SunComponent::class.java, TimedMovementComponent::class.java).get()
	}

	val log = Logger.getLogger(this.javaClass)

	private val movementMapper = ComponentMapper.getFor(TimedMovementComponent::class.java)
	private val irradianceMapper = ComponentMapper.getFor(SolarIrradianceComponent::class.java)
	private val sunIrradianceMapper = ComponentMapper.getFor(SunComponent::class.java)

	
	override fun addedToEngine(engine: Engine) {
		super.addedToEngine(engine)

		engine.addEntityListener(SUNS_FAMILY, this)
	}

	override fun removedFromEngine(engine: Engine) {
		super.removedFromEngine(engine)

		engine.removeEntityListener(this)
	}
	
	override fun entityAdded(entity: Entity) {
		cacheSuns()
		runOnNextUpdate()
	}

	override fun entityRemoved(entity: Entity) {
		cacheSuns()
		runOnNextUpdate()
	}

	var suns: List<Sun> = emptyList()

	fun cacheSuns() {
		val sunEntites = engine.getEntitiesFor(SUNS_FAMILY)

		if (sunEntites.size() == 0) {

			if (suns.isNotEmpty()) {
				suns = emptyList()
			}

		} else if (sunEntites.size() != suns.size) {

			val mutableSuns = ArrayList<Sun>(sunEntites.size())

			for (sun in sunEntites) {

				var solarConstant = sunIrradianceMapper.get(sun).solarConstant
				var position = movementMapper.get(sun).get(galaxy.time).value.position

				mutableSuns.add(Sun(position, solarConstant))
			}

			suns = mutableSuns
			log.info("Cached solar irradiance for ${suns.size} suns")
		}
	}
	
	override fun update(deltaTime: Float) {
		super.update(deltaTime)
	}
	
	override fun processEntity(entity: Entity) {

		val position = movementMapper.get(entity).get(galaxy.time).value.position

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