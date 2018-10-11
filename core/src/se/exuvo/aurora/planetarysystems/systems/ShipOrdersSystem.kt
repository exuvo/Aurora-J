package se.exuvo.aurora.planetarysystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.EntitySubscription.SubscriptionListener
import com.artemis.systems.IteratingSystem
import com.artemis.utils.IntBag
import org.apache.log4j.Logger
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.goap.planner.ReGoapPlannerSettings
import se.exuvo.aurora.planetarysystems.components.ShipComponent
import se.exuvo.aurora.planetarysystems.components.ShipOrdersComponent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.forEach
import se.exuvo.aurora.planetarysystems.components.ShipWorldState

class ShipOrdersSystem : IteratingSystem(FAMILY) {
	companion object {
		val FAMILY = Aspect.all(ShipComponent::class.java, ShipOrdersComponent::class.java)
		val SHIP_FAMILY = Aspect.all(ShipComponent::class.java)
	}

	val log = Logger.getLogger(this.javaClass)

	lateinit private var shipMapper: ComponentMapper<ShipComponent>
	lateinit private var ordersMapper: ComponentMapper<ShipOrdersComponent<ShipWorldState, Boolean?>>

	private val galaxy = GameServices[Galaxy::class]
	val plannerSettings = ReGoapPlannerSettings()

	override fun initialize() {
		super.initialize()

		world.getAspectSubscriptionManager().get(SHIP_FAMILY).addSubscriptionListener(object : SubscriptionListener {
			override fun inserted(entityIDs: IntBag) {
				entityIDs.forEach { entityID ->

					if (!ordersMapper.has(entityID)) {
						ordersMapper.create(entityID)
					}
				}
			}

			override fun removed(entities: IntBag) {}
		})
	}

	override fun process(entityID: Int) {
		val ship = shipMapper.get(entityID)
		var ordersComponent = ordersMapper.get(entityID)
		
		
	}
}