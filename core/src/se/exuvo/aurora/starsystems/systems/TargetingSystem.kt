package se.exuvo.aurora.starsystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.EntitySubscription
import com.artemis.EntitySubscription.SubscriptionListener
import com.artemis.annotations.Wire
import com.artemis.systems.IteratingSystem
import com.artemis.utils.IntBag
import net.mostlyoriginal.api.event.common.EventSystem
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.empires.components.ActiveTargetingComputersComponent
import se.exuvo.aurora.empires.components.IdleTargetingComputersComponent
import se.exuvo.aurora.galactic.ChargedPart
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.PartRef
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.starsystems.PreSystem
import se.exuvo.aurora.starsystems.StarSystem
import se.exuvo.aurora.starsystems.components.ChargedPartState
import se.exuvo.aurora.starsystems.components.EntityReference
import se.exuvo.aurora.starsystems.components.LaserShotComponent
import se.exuvo.aurora.starsystems.components.MissileComponent
import se.exuvo.aurora.starsystems.components.NameComponent
import se.exuvo.aurora.starsystems.components.OnPredictedMovementComponent
import se.exuvo.aurora.starsystems.components.EmpireComponent
import se.exuvo.aurora.starsystems.components.PartStatesComponent
import se.exuvo.aurora.starsystems.components.PoweredPartState
import se.exuvo.aurora.starsystems.components.RailgunShotComponent
import se.exuvo.aurora.starsystems.components.RenderComponent
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.components.TargetingComputerState
import se.exuvo.aurora.starsystems.components.TimedLifeComponent
import se.exuvo.aurora.starsystems.components.TimedMovementComponent
import se.exuvo.aurora.starsystems.components.UUIDComponent
import se.exuvo.aurora.starsystems.events.PowerEvent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.utils.printEntity
import java.lang.IllegalStateException

class TargetingSystem : IteratingSystem(FAMILY), PreSystem {
	companion object {
		@JvmField val FAMILY = Aspect.all(IdleTargetingComputersComponent::class.java) //TODO , InCombatComponent::class.java
		@JvmField val RELOAD_FAMILY = Aspect.all(IdleTargetingComputersComponent::class.java)
		@JvmField val SHIP_FAMILY = Aspect.all(ShipComponent::class.java)
		
		@JvmField val log = LogManager.getLogger(TargetingSystem::class.java)
	}

	lateinit private var idleTargetingComputersComponentMapper: ComponentMapper<IdleTargetingComputersComponent>
	lateinit private var activeTargetingComputersComponentMapper: ComponentMapper<ActiveTargetingComputersComponent>
	lateinit private var shipMapper: ComponentMapper<ShipComponent>
	lateinit private var uuidMapper: ComponentMapper<UUIDComponent>
	lateinit private var nameMapper: ComponentMapper<NameComponent>
	lateinit private var movementMapper: ComponentMapper<TimedMovementComponent>
	lateinit private var ownerMapper: ComponentMapper<EmpireComponent>
	lateinit private var renderMapper: ComponentMapper<RenderComponent>
	lateinit private var laserShotMapper: ComponentMapper<LaserShotComponent>
	lateinit private var railgunShotMapper: ComponentMapper<RailgunShotComponent>
	lateinit private var missileMapper: ComponentMapper<MissileComponent>
	lateinit private var timedLifeMapper: ComponentMapper<TimedLifeComponent>
	lateinit private var predictedMovementMapper: ComponentMapper<OnPredictedMovementComponent>
	lateinit private var partStatesMapper: ComponentMapper<PartStatesComponent>

	@Wire
	lateinit private var starSystem: StarSystem
	lateinit private var events: EventSystem
	lateinit private var powerSystem: PowerSystem
	lateinit private var weaponSystem: WeaponSystem
	
	private val galaxy = GameServices[Galaxy::class]
	private val galaxyGroupSystem by lazy (LazyThreadSafetyMode.NONE) { GameServices[GroupSystem::class] }
	lateinit private var reloadFamily: EntitySubscription
	
	override fun initialize() {
		super.initialize()

		reloadFamily = world.getAspectSubscriptionManager().get(RELOAD_FAMILY)
		
		world.getAspectSubscriptionManager().get(SHIP_FAMILY).addSubscriptionListener(object : SubscriptionListener {
			override fun inserted(entities: IntBag) {
				entities.forEachFast { entityID ->
					var idleTCs = idleTargetingComputersComponentMapper.get(entityID)
					var activeTCs = activeTargetingComputersComponentMapper.get(entityID)

					if (idleTCs == null && activeTCs == null) {

						val ship = shipMapper.get(entityID)
						val partStates: PartStatesComponent = partStatesMapper.get(entityID)
						val targetingComputers = ship.hull[TargetingComputer::class]

						if (targetingComputers.isNotEmpty()) {
							idleTCs = idleTargetingComputersComponentMapper.create(entityID)
							idleTCs.targetingComputers = ArrayList<PartRef<TargetingComputer>>(targetingComputers)

							targetingComputers.forEachFast { tc ->
								val poweredState = partStates[tc][PoweredPartState::class]
								poweredState.requestedPower = 0
							}
						}
					}
				}
			}

			override fun removed(entities: IntBag) {}
		})
	}
	
	//TODO do when tc is activated
	override fun inserted(entities: IntBag) {
		entities.forEachFast { entityID ->
			val partStates: PartStatesComponent = partStatesMapper.get(entityID)
			val idleTCs = idleTargetingComputersComponentMapper.get(entityID)!!

			idleTCs.targetingComputers.forEachFast { tc ->
				if (partStates.isPartEnabled(tc)) {
					val poweredState = partStates[tc][PoweredPartState::class]
					poweredState.requestedPower = 0
				}
			}
		}
	}
	
	fun setTarget(entityID: Int, tc: PartRef<TargetingComputer>, targetRef: EntityReference, partStates: PartStatesComponent = partStatesMapper.get(entityID)) {
		
		println("Setting target for ${printEntity(entityID, world)}.$tc to ${printEntity(targetRef.entityID, world)}")
		
		val tcState = partStates[tc][TargetingComputerState::class]
		tcState.lockCompletionAt = galaxy.time + tc.part.lockingTime
		tcState.target = targetRef
		
		val idleTCs = idleTargetingComputersComponentMapper.get(entityID)
		var activeTCs = activeTargetingComputersComponentMapper.get(entityID)
		
		if (!idleTCs.targetingComputers.remove(tc)) {
			throw IllegalStateException("targeting computer $tc is not idle on $entityID")
		}
		
		if (idleTCs.targetingComputers.isEmpty()) {
			idleTargetingComputersComponentMapper.remove(entityID)
		}
		
		if (activeTCs == null) {
			activeTCs = activeTargetingComputersComponentMapper.create(entityID).set(ArrayList<PartRef<TargetingComputer>>(4))
		}
		
		activeTCs.targetingComputers.add(tc)
		starSystem.changed(entityID, partStatesMapper, idleTargetingComputersComponentMapper, activeTargetingComputersComponentMapper)
	}
	
	fun clearTarget(entityID: Int, tc: PartRef<TargetingComputer>, partStates: PartStatesComponent = partStatesMapper.get(entityID)) {
		
		println("Clearing target for ${printEntity(entityID, world)}.$tc")
		
		val tcState = partStates[tc][TargetingComputerState::class]
		tcState.lockCompletionAt = 0
		tcState.target = null
		
		starSystem.changed(entityID, partStatesMapper, idleTargetingComputersComponentMapper, activeTargetingComputersComponentMapper)
		
		var idleTCs = idleTargetingComputersComponentMapper.get(entityID)
		val activeTCs = activeTargetingComputersComponentMapper.get(entityID)
		
		activeTCs.targetingComputers.remove(tc)
		
		if (activeTCs.targetingComputers.isEmpty()) {
			activeTargetingComputersComponentMapper.remove(entityID)
		}
		
		if (idleTCs == null) {
			idleTCs = idleTargetingComputersComponentMapper.create(entityID).set(ArrayList<PartRef<TargetingComputer>>(4))
		}
		
		idleTCs.targetingComputers.add(tc)
		
		var powerChanged = false
		
		tcState.linkedWeapons.forEachFast{ weapon ->
			val part = weapon.part
			
			if (part is ChargedPart) {
				val poweredState = partStates[weapon][PoweredPartState::class]
				val chargedState = partStates[weapon][ChargedPartState::class]
				
				if (poweredState.requestedPower != 0L) {
					poweredState.requestedPower = 0
					powerChanged = true
				}
				
				chargedState.charge = 0
				chargedState.expectedFullAt = 0
			}
		}
		
		if (powerChanged) {
			events.dispatch(starSystem.getEvent(PowerEvent::class).set(entityID))
		}
	}
	
	//TODO shutdown all weapons when InCombatComponent was removed

	override fun preProcessSystem() {
		val tickSize = world.getDelta().toInt()
		
		reloadFamily.getEntities().forEachFast { entityID ->
//			val ship = shipMapper.get(entityID)
			val partStates: PartStatesComponent = partStatesMapper.get(entityID)
			val idleTCsComponent = idleTargetingComputersComponentMapper.get(entityID)
			val tcs = idleTCsComponent.targetingComputers

			var powerChanged = false
			
			tcs.forEachFast{ tc ->
				val tcState = partStates[tc][TargetingComputerState::class]

				weaponSystem.reloadAmmoWeapons(entityID, partStates, tcState)
				
				//TODO keep reloading charged weapons while InCombat
//				powerChanged = weaponSystem.reloadChargedWeapons(powerChanged, entityID, ship, tcState)
			}
			
			if (powerChanged) {
				events.dispatch(starSystem.getEvent(PowerEvent::class).set(entityID))
			}
		}
		
//		tcState.chargingWeapons.forEachFast{ weapon ->
//					val part = weapon.part
//					val poweredPart = part as PoweredPart
//					val chargedPart = part as ChargedPart
//					
//					val poweredState = partStates[weapon)[PoweredPartState::class]
//					val chargedState = partStates[weapon)[ChargedPartState::class]
//
//					if (chargedState.charge < part.capacitor) {
//						val wantedPower = FastMath.min(part.powerConsumption, part.capacitor - chargedState.charge)
//						
//						if (poweredState.requestedPower != wantedPower) {
//							poweredState.requestedPower = wantedPower
//							powerChanged = true
//						}
//						
//					} else {
//						val idlePower = (0.1 * part.powerConsumption).toLong()
//						
//						poweredState.requestedPower = idlePower
//						powerChanged = true
//						
//						tcState.readyWeapons.add(weapon)
//						tcState.chargingWeapons.remove(weapon)
//						i--
//						size--
//					}
//				}
	}

	private var tmpPosition = Vector2L()
	override fun process(entityID: Int) {

//		val ship = shipMapper.get(entityID)
		val partStates: PartStatesComponent = partStatesMapper.get(entityID)
		val weaponsComponent = idleTargetingComputersComponentMapper.get(entityID)
		val shipMovement = movementMapper.get(entityID).get(galaxy.time)
		val ownerEmpire = ownerMapper.get(entityID).empire

		val tcs = weaponsComponent.targetingComputers

		tcs.forEachFast{ tc ->
			val tcState = partStates[tc][TargetingComputerState::class]

			//TODO automatically target hostiles in range
			
			// below is copy from WeaponSystem as starting point
//			if (!starSystem.isEntityReferenceValid(target)) {
//				
//				tcState.target = null
//				log.warn("Target ${target} is no longer valid for ${tc}")
//				
//			} else if (galaxy.time > tcState.lockCompletionAt) {
//			
//				val targetMovement = movementMapper.get(target.entityID).get(galaxy.time)
//
//				//TODO cache intercepts per firecontrol and weapon type/ordenance
//				tcState.linkedWeapons.forEachFast{ weapon ->
//					if (ship.isPartEnabled(weapon)) {
//						val part = weapon.part
//
//						when (part) {
//							is BeamWeapon -> {
//								val chargedState = partStates[weapon)[ChargedPartState::class]
//
//								if (chargedState.charge >= part.capacitor) {
//									chargedState.charge = 0
//
//									val projectileSpeed = Units.C * 1000
//									val result = getInterceptionPosition(shipMovement.value, targetMovement.value, projectileSpeed)
////										val result = getInterceptionPosition(shipMovement.value, targetMovement.value, projectileSpeed, 0.0)
//									
//									if (result == null) {
//										
//										log.warn("Unable to find intercept for laser and target ${target.entityID}, projectileSpeed $projectileSpeed")
//										
//									} else {
//										
//										val distance = tmpPosition.set(targetMovement.value.position).sub(shipMovement.value.position).len().toLong()
//										val beamArea = part.getBeamArea(distance)
//										var damage: Long = part.getDeliveredEnergyTo1MSquareAtDistance(distance)
//											
//										val (timeToIntercept, aimPosition, interceptPosition, interceptVelocity, relativeInterceptVelocity) = result
//										val galacticTime = timeToIntercept + galaxy.time
//										val galacticDays = (galacticTime / (60 * 60 * 24)).toInt()
//										val days = (timeToIntercept / (60 * 60 * 24)).toInt()
//										
////											if ((beamArea <= 1 || damage > 1000) && days < 30) {
//											
////												println("laser projectileSpeed $projectileSpeed m/s, interceptSpeed ${interceptVelocity.len() / 100} m/s, aimPosition $aimPosition, interceptPosition $interceptPosition, timeToIntercept $timeToIntercept s, interceptAt ${Units.daysToDate(galacticDays)} ${Units.secondsToString(galacticTime)}")
//											
//											val munitionEntityID = starSystem.createEntity(ownerEmpire)
//											renderMapper.create(munitionEntityID)
//											nameMapper.create(munitionEntityID).set(name = part.name + " laser")
//											
//											laserShotMapper.create(munitionEntityID).set(target.entityID, damage, beamArea)
//											
//											val munitionMovement = movementMapper.create(munitionEntityID)
//											munitionMovement.set(shipMovement.value, galaxy.time)
//											munitionMovement.previous.value.velocity.set(interceptVelocity)
//											munitionMovement.previous.value.acceleration.set(0, 0)
//											munitionMovement.setPredictionCoast(MovementValues(interceptPosition, interceptVelocity, Vector2L()), aimPosition, galacticTime)
//											
//											timedLifeMapper.create(munitionEntityID).endTime = galacticTime
//											predictedMovementMapper.create(munitionEntityID)
//											
//											ship.heat += ((100 - part.efficiency) * chargedState.charge) / 100
//											
////											} else {
////												
////												log.error("Unable to find effective intercept for laser $part and target ${target.entityID}")
////											}
//									}
//								}
//							}
//							is Railgun -> {
//								val ammoState = partStates[weapon)[AmmunitionPartState::class]
//								val chargedState = partStates[weapon)[ChargedPartState::class]
//
//								if (chargedState.charge >= part.capacitor && ammoState.amount > 0) {
//
//									val munitionHull = ammoState.type!! as SimpleMunitionHull
//									
//									val projectileSpeed = (chargedState.charge * part.efficiency) / (100 * munitionHull.loadedMass)
//									
//									val result = getInterceptionPosition(shipMovement.value, targetMovement.value, projectileSpeed.toDouble())
////										val result = getInterceptionPosition(shipMovement.value, targetMovement.value, projectileSpeed.toDouble(), 0.0)
//									
//									if (result == null) {
//										
////											log.warn("Unable to find intercept for railgun $part and target ${target.entityID}, projectileSpeed ${projectileSpeed / 100}")
//										
//									} else {
//										
//										val (timeToIntercept, aimPosition, interceptPosition, interceptVelocity, relativeInterceptVelocity) = result
//										val galacticTime = timeToIntercept + galaxy.time
//										val galacticDays = (galacticTime / (60 * 60 * 24)).toInt()
//										val days = (timeToIntercept / (60 * 60 * 24)).toInt()
//										
//										if (days < 30) {
//											
////												println("railgun projectileSpeed ${projectileSpeed / 100} m/s, interceptSpeed ${interceptVelocity.len() / 100} m/s, aimPosition $aimPosition, interceptPosition $interceptPosition, timeToIntercept $timeToIntercept s, interceptAt ${Units.daysToDate(galacticDays)} ${Units.secondsToString(galacticTime)}")
//										
//											val munitionEntityID = starSystem.createEntity(ownerEmpire)
//											renderMapper.create(munitionEntityID)
//											nameMapper.create(munitionEntityID).set(name = munitionHull.name)
//											
//											val damage: Long
//											
//											if (munitionHull.damagePattern == DamagePattern.EXPLOSIVE) {
//												
//												damage = munitionHull.damage
//												
//											} else {
//												
//												damage = ((munitionHull.loadedMass * projectileSpeed * projectileSpeed) / 2).toLong()
//											}
//											
//											railgunShotMapper.create(munitionEntityID).set(target.entityID, damage, munitionHull.damagePattern, munitionHull.health)
//											
//											val munitionMovement = movementMapper.create(munitionEntityID)
//											munitionMovement.set(shipMovement.value, galaxy.time)
//											munitionMovement.previous.value.velocity.set(interceptVelocity)
//											munitionMovement.previous.value.acceleration.set(0, 0)
//											munitionMovement.setPredictionCoast(MovementValues(interceptPosition, interceptVelocity, Vector2L()), aimPosition, galacticTime)
//											
//											timedLifeMapper.create(munitionEntityID).endTime = galacticTime
//											predictedMovementMapper.create(munitionEntityID)
//											
////											galaxyGroupSystem.add(starSystem.getEntityReference(munitionEntityID), GroupSystem.SELECTED)
//											
//											ship.heat += ((100 - part.efficiency) * chargedState.charge) / 100
//										
//											chargedState.charge = 0
////											ammoState.amount -= 1
//										}
//									}
//								}
//							}
//							is MissileLauncher -> {
//								val ammoState = partStates[weapon)[AmmunitionPartState::class]
//
//								if (ammoState.amount > 0) {
//									
//									//TODO handle multi stage missiles
//									// Arrow 3 can be launched into an area of space before it is known where the target missile is going.
//									// When the target and its course are identified, the Arrow interceptor is redirected using its thrust-vectoring nozzle to close the gap and conduct a "body-to-body" interception.
//									
//									val advMunitionHull = ammoState.type!! as AdvancedMunitionHull
//									
//									val missileAcceleration = advMunitionHull.getAverageAcceleration().toDouble()
//									val missileLaunchSpeed = (100 * part.launchForce) / advMunitionHull.loadedMass
//									
//									val result = getInterceptionPosition(shipMovement.value, targetMovement.value, missileLaunchSpeed.toDouble() / 100, missileAcceleration / 100)
//									
//									if (result == null) {
//										
////											log.warn("Unable to find intercept for missile $advMunitionHull and target ${target.entityID}")
//										
//									} else {
//										
//										val (timeToIntercept, aimPosition, interceptPosition, interceptVelocity, relativeInterceptVelocity) = result
//										
//										val galacticTime = timeToIntercept + galaxy.time
//										val galacticDays = (galacticTime / (60 * 60 * 24)).toInt()
//										val relativeSpeed = targetMovement.value.velocity.cpy().sub(shipMovement.value.velocity).len() * FastMath.cos(targetMovement.value.velocity.angleRad(shipMovement.value.velocity))
//										val impactSpeed = relativeSpeed + missileLaunchSpeed + missileAcceleration * FastMath.min(timeToIntercept, advMunitionHull.thrustTime.toLong())
//										
//										if (timeToIntercept <= advMunitionHull.thrustTime) {
//											
////												println("missile missileAcceleration $missileAcceleration m/s², impactSpeed ${impactSpeed / 100} ${interceptVelocity.len() / 100} m/s, interceptSpeed ${relativeInterceptVelocity.len() / 100} m/s, aimPosition $aimPosition, interceptPosition $interceptPosition, thrustTime ${advMunitionHull.getThrustTime()} s, timeToIntercept $timeToIntercept s, interceptAt ${Units.daysToDate(galacticDays)} ${Units.secondsToString(galacticTime)}")
//											
//											val angleRadToIntercept = shipMovement.value.position.angleToRad(interceptPosition)
//											val angleRadToAimTarget = shipMovement.value.position.angleToRad(aimPosition)
//											val initialVelocity = Vector2L(missileLaunchSpeed, 0).rotateRad(angleRadToIntercept).add(shipMovement.value.velocity)
//											val initialAcceleration = Vector2L(advMunitionHull.getMinAcceleration(), 0).rotateRad(angleRadToAimTarget)
//											val interceptAcceleration = Vector2L(advMunitionHull.getMaxAcceleration(), 0).rotateRad(angleRadToAimTarget)
//											
//											val munitionEntityID = starSystem.createEntity(ownerEmpire)
//											renderMapper.create(munitionEntityID)
//											nameMapper.create(munitionEntityID).set(name = advMunitionHull.name)
//										
//											missileMapper.create(munitionEntityID).set(advMunitionHull, target.entityID)
//											
//											val munitionMovement = movementMapper.create(munitionEntityID)
//											munitionMovement.set(shipMovement.value, galaxy.time)
//											munitionMovement.previous.value.velocity.set(initialVelocity)
//											munitionMovement.previous.value.acceleration.set(initialAcceleration)
//											munitionMovement.setPredictionBallistic(MovementValues(interceptPosition, interceptVelocity, interceptAcceleration), aimPosition, advMunitionHull.getMinAcceleration(), galacticTime)
//											
//											timedLifeMapper.create(munitionEntityID).endTime = FastMath.min(galaxy.time + advMunitionHull.thrustTime, galacticTime)
//											predictedMovementMapper.create(munitionEntityID)
//											
////												galaxyGroupSystem.add(starSystem.getEntityReference(munitionEntityID), GroupSystem.SELECTED)
//										
////												thrustComponent.thrustAngle = angleToPredictedTarget.toFloat()
//											
////												ammoState.amount -= 1
//											
//										} else {
//											
////												log.warn("Unable to find intercept inside thrust time for missile $advMunitionHull and target ${target.entityID}")
//										}
//									}
//								}
//							}
//							else -> RuntimeException("Unsupported weapon")
//						}
//					}
//				}
//			}
		}
	}
	
	
}
