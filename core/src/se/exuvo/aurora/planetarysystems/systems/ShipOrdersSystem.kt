package se.exuvo.aurora.planetarysystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.EntitySubscription.SubscriptionListener
import com.artemis.systems.IteratingSystem
import com.artemis.utils.IntBag
import org.apache.log4j.Logger
import se.exuvo.aurora.galactic.Battery
import se.exuvo.aurora.galactic.ChargedPart
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.PoweredPart
import se.exuvo.aurora.galactic.PoweringPart
import se.exuvo.aurora.galactic.Reactor
import se.exuvo.aurora.galactic.SolarPanel
import se.exuvo.aurora.planetarysystems.components.ChargedPartState
import se.exuvo.aurora.planetarysystems.components.FueledPartState
import se.exuvo.aurora.planetarysystems.components.PowerComponent
import se.exuvo.aurora.planetarysystems.components.PoweredPartState
import se.exuvo.aurora.planetarysystems.components.PoweringPartState
import se.exuvo.aurora.planetarysystems.components.ShipComponent
import se.exuvo.aurora.planetarysystems.components.SolarIrradianceComponent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.consumeFuel
import se.exuvo.aurora.utils.forEach
import org.intellij.lang.annotations.JdkConstants.ListSelectionMode
import se.exuvo.aurora.planetarysystems.events.PowerEvent
import net.mostlyoriginal.api.event.common.Subscribe
import se.exuvo.aurora.planetarysystems.components.ShipOrdersComponent
import se.exuvo.aurora.goap.planner.ReGoapPlannerSettings

class ShipOrdersSystem : IteratingSystem(FAMILY) {
	companion object {
		val FAMILY = Aspect.all(ShipComponent::class.java, ShipOrdersComponent::class.java)
		val SHIP_FAMILY = Aspect.all(ShipComponent::class.java)
	}

	val log = Logger.getLogger(this.javaClass)

	lateinit private var shipMapper: ComponentMapper<ShipComponent>
	lateinit private var ordersMapper: ComponentMapper<ShipOrdersComponent>

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