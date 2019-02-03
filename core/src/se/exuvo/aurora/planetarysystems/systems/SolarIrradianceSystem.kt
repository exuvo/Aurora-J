package se.exuvo.aurora.planetarysystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.Entity
import com.artemis.EntitySubscription.SubscriptionListener
import com.artemis.utils.IntBag
import com.sun.xml.internal.ws.api.pipe.Engine
import org.apache.log4j.Logger
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.planetarysystems.components.PoweredPartState
import se.exuvo.aurora.planetarysystems.components.SolarIrradianceComponent
import se.exuvo.aurora.planetarysystems.components.SunComponent
import se.exuvo.aurora.planetarysystems.components.TimedMovementComponent
import se.exuvo.aurora.utils.*
import com.artemis.EntitySubscription

class SolarIrradianceSystem : GalaxyTimeIntervalIteratingSystem(FAMILY, 1 * 60) {
	companion object {
		val FAMILY = Aspect.all(SolarIrradianceComponent::class.java, TimedMovementComponent::class.java)
		val SUNS_FAMILY = Aspect.all(SunComponent::class.java, TimedMovementComponent::class.java)
	}

	val log = Logger.getLogger(this.javaClass)

	lateinit private var movementMapper: ComponentMapper<TimedMovementComponent>
	lateinit private var irradianceMapper: ComponentMapper<SolarIrradianceComponent>
	lateinit private var sunIrradianceMapper: ComponentMapper<SunComponent>

	lateinit private var sunsSubscription: EntitySubscription

	override fun initialize() {
		super.initialize()

		sunsSubscription = world.getAspectSubscriptionManager().get(SUNS_FAMILY)
		sunsSubscription.addSubscriptionListener(object : SubscriptionListener {
			override fun inserted(entities: IntBag) {
				cacheSuns()
				runOnNextUpdate()
			}

			override fun removed(entities: IntBag) {
				cacheSuns()
				runOnNextUpdate()
			}
		})
	}


	var suns: List<Sun> = emptyList()

	fun cacheSuns() {
		val sunEntites = sunsSubscription.getEntities()

		if (sunEntites.size() == 0) {

			if (suns.isNotEmpty()) {
				suns = emptyList()
			}

		} else if (sunEntites.size() != suns.size) {

			val mutableSuns = ArrayList<Sun>(sunEntites.size())

			sunEntites.forEach { entityID ->

				var solarConstant = sunIrradianceMapper.get(entityID).solarConstant
				var position = movementMapper.get(entityID).get(galaxy.time).value.position

				mutableSuns.add(Sun(position, solarConstant))
			}

			suns = mutableSuns
			log.info("Cached solar irradiance for ${suns.size} suns")
		}
	}

	override fun process(entityID: Int) {

		val position = movementMapper.get(entityID).get(galaxy.time).value.position

		var totalIrradiance = 0.0

		for (sun in suns) {
			val distance = position.dst(sun.position) / 1000

			// https://en.wikipedia.org/wiki/Inverse-square_law
			val irradiance = sun.solarConstant * Math.pow(Units.AU, 2.0) / Math.pow(distance, 2.0)
			totalIrradiance += irradiance
//			println("distance ${distance / OrbitSystem.AU} AU, irradiance $irradiance ${(100 * irradiance / sun.solarConstant).toInt()}%")

			//TODO calculate correct irradiance if we are inside the sun 
		}

		irradianceMapper.get(entityID).irradiance = totalIrradiance.toInt()
	}

	data class Sun(val position: Vector2L, val solarConstant: Int)
}