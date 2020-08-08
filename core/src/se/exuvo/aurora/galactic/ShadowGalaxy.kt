package se.exuvo.aurora.galactic

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.ComponentType
import com.artemis.EntitySubscription
import com.artemis.World
import com.artemis.WorldConfigurationBuilder
import com.badlogic.gdx.utils.Disposable
import se.exuvo.aurora.empires.components.ColonyComponent
import se.exuvo.aurora.starsystems.components.CircleComponent
import se.exuvo.aurora.starsystems.components.EmissionsComponent
import se.exuvo.aurora.starsystems.components.EntityReference
import se.exuvo.aurora.starsystems.components.EntityUUID
import se.exuvo.aurora.starsystems.components.GalacticPositionComponent
import se.exuvo.aurora.starsystems.components.MassComponent
import se.exuvo.aurora.starsystems.components.MoveToEntityComponent
import se.exuvo.aurora.starsystems.components.NameComponent
import se.exuvo.aurora.starsystems.components.OrbitComponent
import se.exuvo.aurora.starsystems.components.OwnerComponent
import se.exuvo.aurora.starsystems.components.StarSystemComponent
import se.exuvo.aurora.starsystems.components.RenderComponent
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.components.SolarIrradianceComponent
import se.exuvo.aurora.starsystems.components.Spectrum
import se.exuvo.aurora.starsystems.components.StrategicIconComponent
import se.exuvo.aurora.starsystems.components.SunComponent
import se.exuvo.aurora.starsystems.components.TimedMovementComponent
import se.exuvo.aurora.starsystems.components.TintComponent
import se.exuvo.aurora.starsystems.components.UUIDComponent
import se.exuvo.aurora.utils.forEachFast

// World with no systems only entities
class ShadowGalaxy(val galaxy: Galaxy) : Disposable {
	val world: World
	var time = 0L
		set(newTime) {
			field = newTime
			day = (time / (24L * 60L * 60L)).toInt()
		}
	var day = 0
	
	lateinit var positionMapper: ComponentMapper<GalacticPositionComponent>
	
	val allSubscription: EntitySubscription
	
	init {
		val worldBuilder = WorldConfigurationBuilder()
		worldBuilder.register()
		
		val worldConfig = worldBuilder.build()
		world = World(worldConfig)
		
		// copy system component order
		galaxy.world.componentManager.componentTypes.forEachFast { type: ComponentType ->
			world.getMapper(type.type)
		}
		
		world.inject(this)
		
		allSubscription = world.getAspectSubscriptionManager().get(Aspect.all())
	}
	
	fun update() {
		
		allSubscription.entities.forEachFast { entityID ->
			world.delete(entityID)
		}
		
		world.process()
		world.entityManager.resetNextID()
		
		galaxy.allSubscription.entities.forEachFast { entityID ->
			//TODO create entity with specific ID
		}
		
		world.process()
	}
	
	override fun dispose() {
	
	}
}