package se.exuvo.aurora.planetarysystems.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntityListener
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import org.apache.log4j.Logger
import se.exuvo.aurora.empires.components.WeaponsComponent
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.PassiveSensor
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.planetarysystems.components.PassiveSensorsComponent
import se.exuvo.aurora.planetarysystems.components.PoweredPartState
import se.exuvo.aurora.planetarysystems.components.ShipComponent
import se.exuvo.aurora.planetarysystems.components.UUIDComponent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.galactic.TargetingComputer

class WeaponSystem : IteratingSystem(FAMILY), EntityListener {
	companion object {
		val FAMILY = Family.all(WeaponsComponent::class.java).get()
	}
	
	val log = Logger.getLogger(this.javaClass)

	private val weaponsComponentMapper = ComponentMapper.getFor(WeaponsComponent::class.java)
	private val shipMapper = ComponentMapper.getFor(ShipComponent::class.java)
	private val uuidMapper = ComponentMapper.getFor(UUIDComponent::class.java)
	private val nameMapper = ComponentMapper.getFor(NameComponent::class.java)

	private val galaxy = GameServices[Galaxy::class.java]
	
	override fun addedToEngine(engine: Engine) {
		super.addedToEngine(engine)

		engine.addEntityListener(FAMILY, this)
	}

	override fun removedFromEngine(engine: Engine) {
		super.removedFromEngine(engine)

		engine.removeEntityListener(this)
	}
	
	override fun entityAdded(entity: Entity) {

		var weaponsComponent = weaponsComponentMapper.get(entity)

		if (weaponsComponent == null) {

			val ship = shipMapper.get(entity)
			val targetingComputers = ship.shipClass[TargetingComputer::class.java]

			if (targetingComputers.isNotEmpty()) {
				weaponsComponent = WeaponsComponent(targetingComputers)
				entity.add(weaponsComponent)

				targetingComputers.forEach({
					val tc = it
					val poweredState = ship.getPartState(tc)[PoweredPartState::class]
					poweredState.requestedPower = tc.part.powerConsumption
				})
			}
		}
	}

	override fun entityRemoved(entity: Entity) {
		
	}
	
	override fun processEntity(entity: Entity, deltaGameTime: Float) {
		
		val weaponsComponent = weaponsComponentMapper.get(entity)
		
		/* Determine closest approach
 		 * https://math.stackexchange.com/questions/1256660/shortest-distance-between-two-moving-points
 		 * https://stackoverflow.com/questions/32218356/how-to-calculate-shortest-distance-between-two-moving-objects
  	 */
		
	}
}