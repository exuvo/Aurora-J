package se.exuvo.aurora.starsystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.EntitySubscription
import com.artemis.EntitySubscription.SubscriptionListener
import com.artemis.annotations.Wire
import com.artemis.utils.IntBag
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.starsystems.components.SolarIrradianceComponent
import se.exuvo.aurora.starsystems.components.SunComponent
import se.exuvo.aurora.starsystems.components.TimedMovementComponent
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.utils.isNotEmpty
import com.artemis.utils.Bag
import se.exuvo.aurora.starsystems.StarSystem

class SolarIrradianceSystem : GalaxyTimeIntervalIteratingSystem(FAMILY, 1 * 60) {
	companion object {
		@JvmField val FAMILY = Aspect.all(SolarIrradianceComponent::class.java, TimedMovementComponent::class.java)
		@JvmField val SUNS_FAMILY = Aspect.all(SunComponent::class.java, TimedMovementComponent::class.java)
		@JvmField val log = LogManager.getLogger(SolarIrradianceSystem::class.java)
	}

	lateinit private var movementMapper: ComponentMapper<TimedMovementComponent>
	lateinit private var irradianceMapper: ComponentMapper<SolarIrradianceComponent>
	lateinit private var sunIrradianceMapper: ComponentMapper<SunComponent>

	lateinit private var sunsSubscription: EntitySubscription
	
	@Wire
	lateinit private var system: StarSystem

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

	var suns = Bag<Sun>()

	fun cacheSuns() {
		val sunEntites = sunsSubscription.getEntities()

		if (sunEntites.size() == 0) {

			if (suns.isNotEmpty()) {
				suns.clear()
			}

		} else if (sunEntites.size() != suns.size()) {

			val mutableSuns = Bag<Sun>(sunEntites.size())

			sunEntites.forEachFast { entityID ->

				var solarConstant = sunIrradianceMapper.get(entityID).solarConstant
				var position = movementMapper.get(entityID).get(galaxy.time).value.position

				mutableSuns.add(Sun(position, solarConstant))
			}

			suns = mutableSuns
			log.info("Cached solar irradiance for ${suns.size()} suns")
		}
	}

	override fun process(entityID: Int) {

		val position = movementMapper.get(entityID).get(galaxy.time).value.position

		var totalIrradiance = 0.0

		suns.forEachFast{ sun ->
			val distance = position.dst(sun.position) / 1000

			// https://en.wikipedia.org/wiki/Inverse-square_law
			val irradiance = sun.solarConstant * Math.pow(Units.AU, 2.0) / Math.pow(distance, 2.0)
			totalIrradiance += irradiance
//			println("distance ${distance / OrbitSystem.AU} AU, irradiance $irradiance ${(100 * irradiance / sun.solarConstant).toInt()}%")
			
			//TODO calculate correct irradiance if we are inside the sun 
		}
		
		val irradianceComponent = irradianceMapper.get(entityID)
		val irridance = totalIrradiance.toInt()
		
		if (irradianceComponent.irradiance != irridance) {
			irradianceComponent.irradiance = irridance
			system.changed(entityID, irradianceMapper)
		}
	}

	data class Sun(val position: Vector2L, val solarConstant: Int)
}