@file:Suppress("unused", "NAME_SHADOWING")
package se.exuvo.aurora.starsystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.systems.IteratingSystem
import net.mostlyoriginal.api.event.common.EventSystem
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.galactic.AmmunitionPart
import se.exuvo.aurora.galactic.BeamWeapon
import se.exuvo.aurora.galactic.ChargedPart
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.MissileLauncher
import se.exuvo.aurora.galactic.PoweredPart
import se.exuvo.aurora.galactic.Railgun
import se.exuvo.aurora.starsystems.components.AmmunitionPartState
import se.exuvo.aurora.starsystems.components.ChargedPartState
import se.exuvo.aurora.starsystems.components.NameComponent
import se.exuvo.aurora.starsystems.components.PoweredPartState
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.components.TargetingComputerState
import se.exuvo.aurora.starsystems.components.UUIDComponent
import se.exuvo.aurora.starsystems.events.PowerEvent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.utils.Vector2D
import se.exuvo.aurora.starsystems.components.TimedMovementComponent
import se.exuvo.aurora.starsystems.StarSystem
import com.artemis.annotations.Wire
import se.exuvo.aurora.starsystems.components.MovementValues
import se.exuvo.aurora.utils.Units
import org.apache.commons.math3.analysis.solvers.LaguerreSolver
import org.apache.commons.math3.exception.TooManyEvaluationsException
import org.apache.commons.math3.util.FastMath
import se.exuvo.aurora.starsystems.components.EmpireComponent
import se.exuvo.aurora.starsystems.components.RenderComponent
import se.exuvo.aurora.galactic.AdvancedMunitionHull
import se.exuvo.aurora.galactic.DamagePattern
import se.exuvo.aurora.galactic.SimpleMunitionHull
import se.exuvo.aurora.starsystems.components.LaserShotComponent
import se.exuvo.aurora.starsystems.components.RailgunShotComponent
import se.exuvo.aurora.starsystems.components.TimedLifeComponent
import se.exuvo.aurora.starsystems.components.MissileComponent
import se.exuvo.aurora.starsystems.components.OnPredictedMovementComponent
import se.exuvo.aurora.galactic.PartRef
import se.exuvo.aurora.galactic.Part
import se.exuvo.aurora.empires.components.ActiveTargetingComputersComponent
import se.exuvo.aurora.empires.components.IdleTargetingComputersComponent
import se.exuvo.aurora.galactic.ElectricalThruster
import se.exuvo.aurora.starsystems.PreSystem
import se.exuvo.aurora.starsystems.components.ArmorComponent
import se.exuvo.aurora.starsystems.components.CargoComponent
import se.exuvo.aurora.starsystems.components.HPComponent
import se.exuvo.aurora.starsystems.components.PartsHPComponent
import se.exuvo.aurora.starsystems.components.PartStatesComponent
import se.exuvo.aurora.starsystems.components.ShieldComponent
import java.lang.UnsupportedOperationException

class WeaponSystem : IteratingSystem(FAMILY), PreSystem {
	companion object {
		@JvmField val FAMILY = Aspect.all(ActiveTargetingComputersComponent::class.java)
		
		// Quadric formula https://en.wikipedia.org/wiki/Quadratic_equation#Quadratic_formula_and_its_derivation
		@JvmStatic
		fun getPositiveRootOfQuadraticEquation(a: Double, b: Double, c: Double) = (-b + FastMath.sqrt(b * b - 4 * a * c)) / (2 * a)
		
		@JvmStatic
		fun getPositiveRootOfQuadraticEquationSafe(a: Double, b: Double, c: Double): Double? {
			val tmp = b * b - 4 * a * c
			
			if (tmp < 0) { // square root of a negative number, no interception is possible
				return null
			}
			
			return (-b + FastMath.sqrt(tmp)) / (2 * a)
		}
		
		@JvmField val log = LogManager.getLogger(WeaponSystem::class.java)
		
		const val POLYNOMIAL_MAX_ITERATIONS: Int = 10000
	}

	lateinit private var idleTargetingComputersComponentMapper: ComponentMapper<IdleTargetingComputersComponent>
	lateinit private var activeTargetingComputersComponentMapper: ComponentMapper<ActiveTargetingComputersComponent>
	lateinit private var shipMapper: ComponentMapper<ShipComponent>
	lateinit private var cargoMapper: ComponentMapper<CargoComponent>
	lateinit private var uuidMapper: ComponentMapper<UUIDComponent>
	lateinit private var nameMapper: ComponentMapper<NameComponent>
	lateinit private var movementMapper: ComponentMapper<TimedMovementComponent>
	lateinit private var ownerMapper: ComponentMapper<EmpireComponent>
	lateinit private var renderMapper: ComponentMapper<RenderComponent>
	lateinit private var laserShotMapper: ComponentMapper<LaserShotComponent>
	lateinit private var railgunShotMapper: ComponentMapper<RailgunShotComponent>
	lateinit private var missileMapper: ComponentMapper<MissileComponent>
	lateinit private var timedLifeMapper: ComponentMapper<TimedLifeComponent>
	lateinit private var partStatesMapper: ComponentMapper<PartStatesComponent>
	lateinit private var shieldMapper: ComponentMapper<ShieldComponent>
	lateinit private var armorMapper: ComponentMapper<ArmorComponent>
	lateinit private var partHPMapper: ComponentMapper<PartsHPComponent>
	lateinit private var hpMapper: ComponentMapper<HPComponent>
	lateinit private var predictedMovementMapper: ComponentMapper<OnPredictedMovementComponent>

	@Wire
	lateinit private var starSystem: StarSystem
	lateinit private var events: EventSystem
	lateinit private var powerSystem: PowerSystem
	lateinit private var targetingSystem: TargetingSystem
	
	private val galaxy = GameServices[Galaxy::class]
	private val galaxyGroupSystem by lazy (LazyThreadSafetyMode.NONE) { GameServices[GroupSystem::class] }
	
	val polynomialSolver = LaguerreSolver()
	
	init {
//		val relativeAccuracy = 1.0e-12
//		val absoluteAccuracy = 1.0e-8
//		val functionValueAccuracy = ?
//		polynomialSolver = LaguerreSolver(relativeAccuracy, absoluteAccuracy)
	}

	//TODO do when tc is deactivated
//	override fun inserted(entities: IntBag) {
//		entities.forEachFast { entityID ->
//			val ship = shipMapper.get(entityID)
//			val activeTCs = activeTargetingComputersComponentMapper.get(entityID)!!
//
//			activeTCs.targetingComputers.forEachFast { tc ->
//				if (ship.isPartEnabled(tc)) {
//					val poweredState = partStates[tc)[PoweredPartState::class]
//					poweredState.requestedPower = tc.part.powerConsumption
//				}
//			}
//		}
//	}
	
	//TODO shutdown all weapons when InCombatComponent was removed

	fun reloadAmmoWeapons(entityID: Int, partStates: PartStatesComponent, tcState: TargetingComputerState) {
		
		while(true) {
			val partRef: PartRef<Part>? = tcState.reloadingWeapons.peek()
			
			if (partRef != null) {
				
				val part = partRef.part as AmmunitionPart
				val cargo = cargoMapper.get(entityID)
				val ammoState = partStates[partRef][AmmunitionPartState::class]
				val ammoType = ammoState.type

				if (ammoType != null) {
					
					if (ammoState.reloadedAt == 0L) { // new

						tcState.reloadingWeapons.poll()
						starSystem.changed(entityID, partStatesMapper)

						if (ammoType.radius != part.ammunitionSize) {

							log.error("Wrong ammo size for $part: ${ammoState.type!!.radius} != ${part.ammunitionSize}")
							powerSystem.deactivatePart(entityID, partRef, partStates)

						} else {

							// Take ammo from storage now to avoid multiple launchers trying to reload with the same last ordenance
							val removedAmmo = cargo.retrieveCargo(ammoType, 1)

							if (removedAmmo > 0) {

								ammoState.reloadedAt = galaxy.time + part.reloadTime
								tcState.reloadingWeapons.add(partRef)
								
								starSystem.changed(entityID, cargoMapper)
								
							} else if (ammoState.amount == 0) {

								println("Unpowering $part due to no more ammo")
								powerSystem.deactivatePart(entityID, partRef, partStates)
							}
						}

					} else if (galaxy.time >= ammoState.reloadedAt) {

						tcState.reloadingWeapons.poll()
						starSystem.changed(entityID, partStatesMapper)

						ammoState.amount += 1
						ammoState.reloadedAt = 0
						
						if (ammoState.amount == 1) {
							
							if (part is Railgun) {
								val chargedState = partStates[partRef][ChargedPartState::class]
								
								if (chargedState.charge >= part.capacitor && !tcState.readyWeapons.contains(partRef)) { // have to do the contains check if the the capacitor was overflowed too much
									tcState.readyWeapons.add(partRef)
								}
								
							} else {
								tcState.readyWeapons.add(partRef)
							}
						}

						val freeSpace = part.ammunitionAmount - ammoState.amount

						if (freeSpace > 0) {

							// Take ammo from storage now to avoid multiple launchers trying to reload with the same last ordenance
							val removedAmmo = cargo.retrieveCargo(ammoType, 1)

							if (removedAmmo > 0) {
								ammoState.reloadedAt = galaxy.time + part.reloadTime
								tcState.reloadingWeapons.add(partRef)
								
								starSystem.changed(entityID, cargoMapper)
							}
						}

					} else {
						break;
					}
					
				} else {

					println("Unpowering $part due to no ammo selected")
					powerSystem.deactivatePart(entityID, partRef, partStates)
				}
				
			} else {
				break
			}
		}
	}
	
	fun reloadChargedWeapons(powerChanged: Boolean, entityID: Int, partStates: PartStatesComponent, tcState: TargetingComputerState): Boolean {
		
		var powerChanged = powerChanged
		
		while(true) {
			val partRef: PartRef<Part>? = tcState.chargingWeapons.peek()
			
			if (partRef != null) {
				
				val part = partRef.part
				val poweredPart = part as PoweredPart
				val chargedPart = part as ChargedPart
				val poweredState = partStates[partRef][PoweredPartState::class]
				val chargedState = partStates[partRef][ChargedPartState::class]
				
				if (chargedState.expectedFullAt == 0L) { // new
					
					tcState.chargingWeapons.poll()
					starSystem.changed(entityID, partStatesMapper)
					
					//Will overfill slightly to fix shot to shot timing when powerConsumption is not a multiple of capacitor
					val wantedPower = part.powerConsumption
					
					if (poweredState.requestedPower != wantedPower) {
						poweredState.requestedPower = wantedPower
						powerChanged = true
					}
					
					chargedState.expectedFullAt = galaxy.time + (part.capacitor - chargedState.charge + poweredState.requestedPower - 1) / poweredState.requestedPower
					tcState.chargingWeapons.add(partRef)
					
				} else if (galaxy.time >= chargedState.expectedFullAt) {
					
					tcState.chargingWeapons.poll()
					starSystem.changed(entityID, partStatesMapper)
					
					if (chargedState.charge < part.capacitor) {
						
						chargedState.expectedFullAt = galaxy.time + (part.capacitor - chargedState.charge + poweredState.requestedPower - 1) / poweredState.requestedPower
						tcState.chargingWeapons.add(partRef)
						
					} else {
						
						// Assume we will fire this tick and keep requesting full power
//						val willFireSameTickIfGivenPower = false
//						
//						if (!willFireSameTickIfGivenPower) {
//							poweredState.requestedPower = 0
//							powerChanged = true
//						}
						
						chargedState.expectedFullAt = 0
						
						if (part is Railgun) {
							val ammoState = partStates[partRef][AmmunitionPartState::class]
							
							if (ammoState.amount > 0 && !tcState.readyWeapons.contains(partRef)) { // have to do the contains check if the the ammunition reloaded at the same tick
								tcState.readyWeapons.add(partRef)
								
							} else {
								poweredState.requestedPower = 0
								powerChanged = true
							}
							
						} else {
							tcState.readyWeapons.add(partRef)
						}
					}
					
				} else {
					break;
				}
				
			} else {
				break
			}
		}
		
		return powerChanged
	}
	
	override fun preProcessSystem() {
//		val tickSize = world.getDelta().toInt()
		
		subscription.getEntities().forEachFast { entityID ->
			val partStates = partStatesMapper.get(entityID)
			val weaponsComponent = activeTargetingComputersComponentMapper.get(entityID)
			val tcs = weaponsComponent.targetingComputers

			var powerChanged = false
			
			tcs.forEachFast { tc ->
				val tcState = partStates[tc][TargetingComputerState::class]

				reloadAmmoWeapons(entityID, partStates, tcState)
				
				powerChanged = reloadChargedWeapons(powerChanged, entityID, partStates, tcState)
			}
			
			if (powerChanged) {
				events.dispatch(starSystem.getEvent(PowerEvent::class).set(entityID))
			}
		}
	}

	private var tmpPosition = Vector2L()
	override fun process(entityID: Int) {
		
		val profilerEvents = starSystem.workingShadow.profilerEvents
		profilerEvents.start("$entityID")
		
		val ship = shipMapper.get(entityID)
		val partStates = partStatesMapper.get(entityID)
		val activeTCsComponent = activeTargetingComputersComponentMapper.get(entityID)
		val shipMovement = movementMapper.get(entityID).get(galaxy.time)
		val ownerEmpire = ownerMapper.get(entityID).empire

		val tcs = activeTCsComponent.targetingComputers
		var powerChanged = false

		tcs.forEachFast{ tc ->
			profilerEvents.start("tc $tc")
			val tcState = partStates[tc][TargetingComputerState::class]

			val target = tcState.target!!
			
			// Fire
			
			if (!starSystem.isEntityReferenceValid(target)) { // or dead and !forced
				
				targetingSystem.clearTarget(entityID, tc, partStates)
				log.warn("Target ${target} is no longer valid for ${tc}")
				
			} else if (galaxy.time > tcState.lockCompletionAt) {
			
				val targetMovement = movementMapper.get(target.entityID).get(galaxy.time)

				//TODO cache intercepts per ship and weapon type/ordnance
				var i = 0
				var size = tcState.readyWeapons.size()
				
				while (i < size) {
					val weapon = tcState.readyWeapons[i++]
					profilerEvents.start("wep $weapon")
					
					when (val part = weapon.part) {
						is BeamWeapon -> {
							val chargedState = partStates[weapon][ChargedPartState::class]

							val projectileSpeed = Units.C * 1000
							
							profilerEvents.start("getInterceptionPosition1")
							val result = getInterceptionPosition1(shipMovement.value, targetMovement.value, projectileSpeed)
//										val result = getInterceptionPosition(shipMovement.value, targetMovement.value, projectileSpeed, 0.0)
							profilerEvents.end()
							
							if (result == null) {
								
								log.warn("Unable to find intercept for laser and target ${target.entityID}, projectileSpeed $projectileSpeed")

								val poweredState = partStates[weapon][PoweredPartState::class]
								
								if (poweredState.requestedPower != 0L) {
									poweredState.requestedPower = 0
									powerChanged = true
								}
								
							} else {
								
								val (timeToIntercept, aimPosition, interceptPosition, interceptVelocity, relativeInterceptVelocity) = result
								val distance = tmpPosition.set(interceptPosition).sub(shipMovement.value.position).len().toLong()
								val beamArea = part.getBeamArea(distance)
								val damage: Long = part.getDeliveredEnergyTo1MSquareAtDistance(distance)
								
								val galacticTime = timeToIntercept + galaxy.time
								val galacticDays = (galacticTime / (60 * 60 * 24)).toInt()
								val days = (timeToIntercept / (60 * 60 * 24)).toInt()
								
								if (damage >= 100 && days < 10) {

//									println("laser projectileSpeed $projectileSpeed m/s, interceptSpeed ${interceptVelocity.len() / 100} m/s, aimPosition $aimPosition, interceptPosition $interceptPosition, timeToIntercept $timeToIntercept s, interceptAt ${Units.daysToDate(galacticDays)} ${Units.secondsToString(galacticTime)}")
									
									val munitionEntityID = starSystem.createEntity(ownerEmpire)
									renderMapper.create(munitionEntityID)
									nameMapper.create(munitionEntityID).set(name = part.name + " laser")
									
									laserShotMapper.create(munitionEntityID).set(target.entityID, damage, beamArea)
									
									val munitionMovement = movementMapper.create(munitionEntityID)
									munitionMovement.set(shipMovement.value, galaxy.time)
									munitionMovement.previous.value.velocity.set(interceptVelocity)
									munitionMovement.previous.value.acceleration.set(0, 0)
									munitionMovement.setPredictionCoast(MovementValues(interceptPosition, interceptVelocity, Vector2L()), aimPosition, galacticTime)
									
									timedLifeMapper.create(munitionEntityID).endTime = galacticTime
									predictedMovementMapper.create(munitionEntityID)
									
									chargedState.charge -= part.capacitor
									ship.heat += ((100 - part.efficiency) * chargedState.charge) / 100
									
									i--
									size--
									tcState.readyWeapons.remove(weapon)
									tcState.chargingWeapons.add(weapon)
									
									starSystem.changed(entityID, shipMapper, partStatesMapper)
									
								} else {

//									log.warn("Unable to find effective intercept for laser $part and target ${target.entityID}")

									val poweredState = partStates[weapon][PoweredPartState::class]

									if (poweredState.requestedPower != 0L) {
										poweredState.requestedPower = 0
										powerChanged = true
									}
								}
							}
						}
						is Railgun -> {
							val ammoState = partStates[weapon][AmmunitionPartState::class]
							val chargedState = partStates[weapon][ChargedPartState::class]

							if (chargedState.charge >= part.capacitor && ammoState.amount > 0) {

								val munitionHull = ammoState.type!! as SimpleMunitionHull
								val projectileSpeed = (chargedState.charge * part.efficiency) / (100 * munitionHull.loadedMass)
								
								profilerEvents.start("getInterceptionPosition1")
								val result = getInterceptionPosition1(shipMovement.value, targetMovement.value, projectileSpeed.toDouble())
//										val result = getInterceptionPosition(shipMovement.value, targetMovement.value, projectileSpeed.toDouble(), 0.0)
								profilerEvents.end()
								
								if (result == null) {
									
//											log.warn("Unable to find intercept for railgun $part and target ${target.entityID}, projectileSpeed ${projectileSpeed / 100}")
									
									val poweredState = partStates[weapon][PoweredPartState::class]
									
									if (poweredState.requestedPower != 0L) {
										poweredState.requestedPower = 0
										powerChanged = true
									}
									
								} else {
									
									val (timeToIntercept, aimPosition, interceptPosition, interceptVelocity, relativeInterceptVelocity) = result
									val galacticTime = timeToIntercept + galaxy.time
									val galacticDays = (galacticTime / (60 * 60 * 24)).toInt()
									val days = (timeToIntercept / (60 * 60 * 24)).toInt()
									
									if (days < 10) {
										
//												println("railgun projectileSpeed ${projectileSpeed / 100} m/s, interceptSpeed ${interceptVelocity.len() / 100} m/s, aimPosition $aimPosition, interceptPosition $interceptPosition, timeToIntercept $timeToIntercept s, interceptAt ${Units.daysToDate(galacticDays)} ${Units.secondsToString(galacticTime)}")
									
										val munitionEntityID = starSystem.createEntity(ownerEmpire)
										renderMapper.create(munitionEntityID)
										nameMapper.create(munitionEntityID).set(name = munitionHull.name)
										
										railgunShotMapper.create(munitionEntityID).set(target.entityID, munitionHull)
										hpMapper.create(munitionEntityID).set(1)
										
										val munitionMovement = movementMapper.create(munitionEntityID)
										munitionMovement.set(shipMovement.value, galaxy.time)
										munitionMovement.previous.value.velocity.set(interceptVelocity)
										munitionMovement.previous.value.acceleration.set(0, 0)
										munitionMovement.setPredictionCoast(MovementValues(interceptPosition, interceptVelocity, Vector2L()), aimPosition, galacticTime)
										
										timedLifeMapper.create(munitionEntityID).endTime = galacticTime
										predictedMovementMapper.create(munitionEntityID)
										
//											galaxyGroupSystem.add(starSystem.getEntityReference(munitionEntityID), GroupSystem.SELECTED)
										
										ship.heat += ((100 - part.efficiency) * chargedState.charge) / 100
									
										i--
										size--
										tcState.readyWeapons.remove(weapon)
										tcState.chargingWeapons.add(weapon)
										
										chargedState.charge -= part.capacitor
										
										if (ammoState.amount == 1 && ammoState.reloadedAt == 0L) {
											println("Deactivating $part due to no more ammo")
											powerSystem.deactivatePart(entityID, weapon, partStates)
											
										} else if (ammoState.amount == part.ammunitionAmount) {
											tcState.reloadingWeapons.add(weapon)
										}
										
										ammoState.amount -= 1
										
										starSystem.changed(entityID, shipMapper, partStatesMapper)
										
									} else {
										
										val poweredState = partStates[weapon][PoweredPartState::class]
										
										if (poweredState.requestedPower != 0L) {
											poweredState.requestedPower = 0
											powerChanged = true
										}
									}
								}
							}
						}
						is MissileLauncher -> {
							val ammoState = partStates[weapon][AmmunitionPartState::class]

							if (ammoState.amount > 0) {
								
								//TODO handle multi stage missiles
								// "Arrow 3" can be launched into an area of space before it is known where the target missile is going.
								// When the target and its course are identified, the Arrow interceptor is redirected using its thrust-vectoring nozzle to close the gap and conduct a "body-to-body" interception.
								
								val advMunitionHull = ammoState.type!! as AdvancedMunitionHull
								
								val missileLaunchSpeed = (100 * part.launchForce) / advMunitionHull.loadedMass
								
								val electricalThrusters = advMunitionHull.thrusters[0].part is ElectricalThruster
								
								var result: InterceptResult?
								
								if (electricalThrusters) {
									profilerEvents.start("getInterceptionPosition2")
									result = getInterceptionPosition2(shipMovement.value, targetMovement.value, missileLaunchSpeed.toDouble() / 100, advMunitionHull.getAverageAcceleration().toDouble() / 100)
									profilerEvents.end()
									
								} else { // chemical
									profilerEvents.start("getInterceptionPosition3")
									result = getInterceptionPosition3(shipMovement.value, targetMovement.value, missileLaunchSpeed.toDouble() / 100, advMunitionHull.getMinAcceleration().toDouble() / 100, advMunitionHull.getMaxAcceleration().toDouble() / 100)
									profilerEvents.end()
								}
								
								if (result == null) {
									log.warn("Unable to find intercept for missile $advMunitionHull and target ${target.entityID} within thrust time")
									profilerEvents.end()
									continue
								}
								
								if (result.timeToIntercept > advMunitionHull.thrustTime) { // Runs out of fuel, try with coasting
									
									if (electricalThrusters) {
										profilerEvents.start("getInterceptionPosition4")
										result = getInterceptionPosition4(shipMovement.value, targetMovement.value, missileLaunchSpeed.toDouble() / 100, advMunitionHull.getAverageAcceleration().toDouble() / 100, advMunitionHull.thrustTime.toDouble())
										profilerEvents.end()
										
									} else { // chemical
										profilerEvents.start("getInterceptionPosition5")
										result = getInterceptionPosition5(shipMovement.value, targetMovement.value, missileLaunchSpeed.toDouble() / 100, advMunitionHull.getMinAcceleration().toDouble() / 100, advMunitionHull.getMaxAcceleration().toDouble() / 100, advMunitionHull.thrustTime.toDouble())
										profilerEvents.end()
									}

									if (result == null) {
										log.warn("Unable to find intercept for missile $advMunitionHull and target ${target.entityID} with coasting")
										profilerEvents.end()
										continue
									}
								}
								
								val (timeToIntercept, aimPosition, interceptPosition, interceptVelocity, relativeInterceptVelocity) = result
								
								if (timeToIntercept <= advMunitionHull.thrustTime) {
									
									//TODO calculate intercept for each stage, continuing from closest intercept previous stage could manage
									
									val missileAcceleration = advMunitionHull.getAverageAcceleration().toDouble()
									
									val galacticTime = timeToIntercept + galaxy.time
									val galacticDays = (galacticTime / (60 * 60 * 24)).toInt()
									val relativeSpeed = targetMovement.value.velocity.cpy().sub(shipMovement.value.velocity).len() * FastMath.cos(targetMovement.value.velocity.angleRad(shipMovement.value.velocity))
									val impactSpeed = relativeSpeed + missileLaunchSpeed + missileAcceleration * FastMath.min(timeToIntercept, advMunitionHull.thrustTime.toLong())
									
//										println("missile missileAcceleration $missileAcceleration m/s², impactSpeed ${impactSpeed / 100} ${interceptVelocity.len() / 100} m/s, interceptSpeed ${relativeInterceptVelocity.len() / 100} m/s, aimPosition $aimPosition, interceptPosition $interceptPosition, thrustTime ${advMunitionHull.getThrustTime()} s, timeToIntercept $timeToIntercept s, interceptAt ${Units.daysToDate(galacticDays)} ${Units.secondsToString(galacticTime)}")
									
									val angleRadToIntercept = shipMovement.value.position.angleToRad(interceptPosition)
									val angleRadToAimTarget = shipMovement.value.position.angleToRad(aimPosition)
									val initialVelocity = Vector2L(missileLaunchSpeed, 0).rotateRad(angleRadToIntercept).add(shipMovement.value.velocity)
									val initialAcceleration = Vector2L(advMunitionHull.getMinAcceleration(), 0).rotateRad(angleRadToAimTarget)
									val interceptAcceleration = Vector2L(advMunitionHull.getMaxAcceleration(), 0).rotateRad(angleRadToAimTarget)
									
									val munitionEntityID = starSystem.createEntity(ownerEmpire)
									renderMapper.create(munitionEntityID)
									nameMapper.create(munitionEntityID).set(name = advMunitionHull.name)
								
									missileMapper.create(munitionEntityID).set(advMunitionHull, target.entityID)
									partStatesMapper.create(munitionEntityID).set(advMunitionHull)
									armorMapper.create(munitionEntityID).set(advMunitionHull)
									hpMapper.create(munitionEntityID).set(advMunitionHull)
									
									val munitionMovement = movementMapper.create(munitionEntityID)
									munitionMovement.set(shipMovement.value, galaxy.time)
									munitionMovement.previous.value.velocity.set(initialVelocity)
									munitionMovement.previous.value.acceleration.set(initialAcceleration)
									munitionMovement.setPredictionBallistic(MovementValues(interceptPosition, interceptVelocity, interceptAcceleration), aimPosition, advMunitionHull.getMinAcceleration(), galacticTime)
									
									timedLifeMapper.create(munitionEntityID).endTime = FastMath.min(galaxy.time + advMunitionHull.thrustTime, galacticTime)
									predictedMovementMapper.create(munitionEntityID)
									
//												galaxyGroupSystem.add(starSystem.getEntityReference(munitionEntityID), GroupSystem.SELECTED)

//												thrustComponent.thrustAngle = angleToPredictedTarget.toFloat()
									
									if (ammoState.amount == 1) {
										i--
										size--
										tcState.readyWeapons.remove(weapon)
										
										if (ammoState.reloadedAt == 0L) {
											println("Unpowering $part due to no more ammo")
											powerSystem.deactivatePart(entityID, weapon, partStates)
										}
										
									} else if (ammoState.amount == part.ammunitionAmount) {
										tcState.reloadingWeapons.add(weapon)
									}
									
									ammoState.amount--
									
									starSystem.changed(entityID, partStatesMapper)
									
								} else {
								
//												log.warn("Unable to find intercept inside thrust time for missile $advMunitionHull and target ${target.entityID}")
								}
							}
						}
						else -> RuntimeException("Unsupported weapon")
					}
					profilerEvents.end()
				}
			}
			profilerEvents.end()
		}
		
		if (powerChanged) {
			events.dispatch(starSystem.getEvent(PowerEvent::class).set(entityID))
		}
		
		profilerEvents.end()
	}
	
	val tmpVelocity = Vector2L()
	
	fun munitionExpired(entityID: Int) {

		val movement = movementMapper.get(entityID).get(galaxy.time).value
		var targetMovement: MovementValues? = null
		val laser: LaserShotComponent?
		val railgun: RailgunShotComponent?
		val missile: MissileComponent? = missileMapper.get(entityID)
		
		var targetID: Int
		val damage: Long
		val damagePattern: DamagePattern
		
		if (missile != null) {
			
			targetID = missile.targetEntityID
			
			if (missile.hull.damage > 0) {
				damagePattern = DamagePattern.EXPLOSIVE
				damage = missile.hull.damage
				
			} else {
				damagePattern = DamagePattern.KINETIC
				targetMovement = movementMapper.get(targetID).get(galaxy.time).value
				val relativeVelocity = tmpVelocity.set(movement.velocity).sub(targetMovement.velocity).len() / 100
				damage = ((missile.hull.emptyMass * relativeVelocity * relativeVelocity) / 2).toLong()
			}
			
		} else if (run { laser = laserShotMapper.get(entityID); true } && laser != null) {
			
			targetID = laser.targetEntityID
			damage = laser.damage
			damagePattern = DamagePattern.LASER
			
		} else if (run { railgun = railgunShotMapper.get(entityID); true } && railgun != null) {
			
			targetID = railgun.targetEntityID
			
			if (railgun.hull.damage > 0) {
				damagePattern = DamagePattern.EXPLOSIVE
				damage = railgun.hull.damage
				
			} else {
				damagePattern = DamagePattern.KINETIC
				targetMovement = movementMapper.get(targetID).get(galaxy.time).value
				val relativeVelocity = tmpVelocity.set(movement.velocity).sub(targetMovement.velocity).len() / 100
				damage = ((railgun.hull.loadedMass * relativeVelocity * relativeVelocity) / 2).toLong()
			}
		
		} else {
			throw UnsupportedOperationException("not laser, railgun or missile")
		}
		
		if (!world.getEntityManager().isActive(targetID)) {
			
			if (missile != null) {
				//TODO retarget
			}
			
			return
		}
		
		//TODO if missile or railgun attempt CIWS defence
		
		if (targetMovement == null) {
			targetMovement = movementMapper.get(targetID).get(galaxy.time).value
		}
		
		val distanceFromTarget = tmpPosition.set(movement.position).sub(targetMovement.position).len()
		
		if (distanceFromTarget < 1000L) { //TODO < 1000 + target length
			return
		}
		
		applyDamage(targetID, damage, damagePattern)
	}
	
	fun applyDamage(entityID: Int, damageEnergy: Long = 0, damagePattern: DamagePattern) {
		var damage = damageEnergy
		
		//TODO laser damage & area
//		if (laser.beamArea < 1 column) {
//			single column damage
//		} else if (laser.beamArea < half ship surface area) {
//			spread damage across multiple columns
//		} else {
//			spread damage across all columns
//		}
		
		//TODO explosive damage spread & loss due to small target
		
		val ship = shipMapper.get(entityID)
		val shield = shieldMapper.get(entityID)
		
		if (shield != null) {
			val partStates = partStatesMapper.get(entityID)
			damage = applyShieldDamage(entityID, damage, damagePattern, ship, shield, partStates)
			
			if (damage == 0L) {
				return
			}
		}
		
		val armor = armorMapper.get(entityID)
		
		if (armor != null) {
			damage = applyArmorDamage(entityID, damage, damagePattern, ship, armor)
			
			if (damage == 0L) {
				return
			}
		}
		
		val partsHP = partHPMapper.get(entityID)
		
		if (partsHP != null) {
			damage = applyPartHPDamage(entityID, damageEnergy, partsHP)
			
			if (damage == 0L) {
				return
			}
		}
		
		val hp = hpMapper.get(entityID)
		
		if (hp != null) {
			damage = applyHPDamage(entityID, damageEnergy, hp)
			
			if (damage == 0L) {
				return
			}
		}
		
		if (ship != null) {
			println("Ship destroyed")
			starSystem.unregisterShip(entityID, ship)
			//TODO create derelict
			
		} else {
//			println("Entity destroyed")
		}
	}
	
	fun applyShieldDamage(entityID: Int, damageEnergy: Long = 0, damagePattern: DamagePattern,
												ship: ShipComponent,
												shield: ShieldComponent,
												partStates: PartStatesComponent): Long
	{
		var damage = damageEnergy
		val shieldHP = shield.shieldHP
		
		if (shieldHP > 0) {
			var blockedDamage: Long
			
			// Blocks all laser and explosive, lets 50% kinetic through
			if (damagePattern == DamagePattern.KINETIC) {
				blockedDamage	= FastMath.min(shieldHP, damageEnergy / 2)
			} else {
				blockedDamage	= FastMath.min(shieldHP, damageEnergy)
			}
			
			damage -= blockedDamage
			
			println("Damaging shield for $blockedDamage dmg, remaining $damage")
			
			shield.shieldHP = shieldHP - blockedDamage
			
			val shields = ship.hull.shields
			val maxShieldHPComponent = ship.hull.maxShieldHP
			
			for (i in 0 until shields.size) { //TODO spread damage evenly across all shield parts
				val partRef = shields[i]
				
				val chargedState = partStates[partRef][ChargedPartState::class]
				
				val partDamage = FastMath.min(chargedState.charge, blockedDamage)
				blockedDamage -= partDamage
				
				chargedState.charge = chargedState.charge - partDamage
				
				if (blockedDamage == 0L) {
					break
				}
			}
			
			starSystem.changed(entityID, shieldMapper)
		}
		
		return damage
	}
	
	@Suppress("NAME_SHADOWING")
	fun applyArmorDamage(entityID: Int, damageEnergy: Long = 0, damagePattern: DamagePattern,
											 ship: ShipComponent?,
											 armorC: ArmorComponent = armorMapper.get(entityID),
											 damageColumn: Int? = null
	): Long {
		var damage = damageEnergy
		var damageColumn = damageColumn
		
		val armor = armorC.armor!!
		val armorLayers = armor.size
		val armorWidth = armor[0].size
		
		if (damageColumn == null) {
			damageColumn = starSystem.random.nextInt(armorWidth)
		}
		
		fun getArmorResistance(layer: Int): Int {
			if (ship != null) {
				return ship.hull.armorEnergyPerDamage[layer].toInt()
			} else {
				return 1000 // Missiles
			}
		}
		
		var layer = armorLayers
		
		when (damagePattern) {
			DamagePattern.LASER -> { //TODO spread laser on area
				while(layer > 0) {
					var armorHP: Int = armorC[--layer][damageColumn].toInt()
					
					if (armorHP > 0) {
						val armorResistance = getArmorResistance(layer)
						val blockDamage: Int = FastMath.min(armorHP, (damage / armorResistance).toInt())
						damage -= blockDamage * armorResistance
						
						println("Damaging armor $damageColumn-$layer for $blockDamage dmg, remaining $damage")
						
						armorHP -= blockDamage
						armorC[layer][damageColumn] = armorHP.toUByte()
						
						if (armorHP > 0 && damage < armorResistance) {
							damage = 0
							break
						}
					}
				}
			}
			DamagePattern.KINETIC -> {
				while(layer > 0) {
					var armorHP: Int = armorC[--layer][damageColumn].toInt()
					
					if (armorHP > 0) {
						val armorResistance = getArmorResistance(layer)
						val blockDamage: Int = FastMath.min(armorHP, (damage / armorResistance).toInt())
						damage -= blockDamage * armorResistance
						
						println("Damaging armor $damageColumn-$layer for $blockDamage dmg, remaining $damage")
						
						armorHP -= blockDamage
						armorC[layer][damageColumn] = armorHP.toUByte()
						
						if (armorHP > 0 && damage < armorResistance) {
							damage = 0
							break
						}
					}
				}
			}
			DamagePattern.EXPLOSIVE -> { // full damage hit block, quarter damage sides
				while(layer > 0) {
					var armorHP: Int = armorC[--layer][damageColumn].toInt()
					
					if (armorHP > 0) {
						val armorResistance = getArmorResistance(layer)
						val blockDamage: Int = FastMath.min(armorHP, (damage / armorResistance).toInt())
						damage -= blockDamage * armorResistance
						
						println("Damaging armor $damageColumn-$layer for $blockDamage dmg, remaining $damage")
						
						armorHP -= blockDamage
						armorC[layer][damageColumn] = armorHP.toUByte()
						
						if (armorHP > 0 && damage < armorResistance) {
							damage = 0
							break
						}
					}
					
					//TODO spread damage
				}
			}
		}
		
		if (damage < damageEnergy) {
			starSystem.changed(entityID, armorMapper)
		}
		
		return damage
	}
	
	fun applyPartHPDamage(entityID: Int, damageEnergy: Long, partsHP: PartsHPComponent = partHPMapper.get(entityID)): Long {
		var damage = damageEnergy / 1000
		
		if (damage > 0 && partsHP.totalPartHP > 0) {
			println("Damaging parts for $damage dmg")
			
			while (partsHP.damageableParts.size > 0) {
				val entry = partsHP.damageableParts.higherEntry(starSystem.random.nextLong(partsHP.damageablePartsMaxVolume))
				val partRef: PartRef<Part>
				
				if (entry.value.size() == 1) {
					partRef = entry.value[0]
					
				} else {
					partRef = entry.value[starSystem.random.nextInt(entry.value.size())]
				}
				
				var partHP = partsHP.getPartHP(partRef)
				
				val partDamage: Int = FastMath.min(partHP, damage.toInt())
				damage -= partDamage
				
				partHP -= partDamage
				partsHP.setPartHP(partRef, partHP, entry)
				
				if (partHP == 0) {
					println("Part destroyed ${partRef.part}")
				}
				
				if (damage == 0L) {
					break
				}
			}
			
			starSystem.changed(entityID, partHPMapper)
		}
		
		return damage * 1000
	}
	
	fun applyHPDamage(entityID: Int, damageEnergy: Long, hp: HPComponent = hpMapper.get(entityID)): Long {
		var damage = damageEnergy / 1000
		
		if (damage > 0 && hp.health > 0) {
			println("Damaging hp for $damage dmg")
			
			var hullHP = hp.health.toInt()
			
			val partDamage: Int = FastMath.min(hullHP, damage.toInt())
			damage -= partDamage
			
			hullHP -= partDamage
			hp.health = hullHP.toShort()
			
			starSystem.changed(entityID, hpMapper)
		}
		
		return damage * 1000
	}
	
	data class InterceptResult(val timeToIntercept: Long,
	                           val aimPosition: Vector2L,
	                           val interceptPosition: Vector2L,
	                           val interceptVelocity: Vector2L,
	                           val relativeInterceptVelocity: Vector2L)
	
	/**
	 * Calculates intercept with position, velocity, timed varying acceleration and then coasting
	 * Assumes timeToIntercept will be longer than thrust time
	 */
	fun getInterceptionPosition5(shooterMovement: MovementValues, // in m, cm/s, cm/s²
															 targetMovement: MovementValues, // in m, cm/s, cm/s²
															 missileLaunchSpeed: Double, // in m/s
															 missileStartAcceleration: Double, // in m/s²
															 missileEndAcceleration: Double, // in m/s²
															 missileAccelTime: Double, // in s
	): InterceptResult? {
		//TODO implement
//		val targetAcceleration = Vector2D(targetMovement.acceleration.x.toDouble(), targetMovement.acceleration.y.toDouble()).div(100.0)
//		val relativeVelocity = Vector2D((targetMovement.velocity.x - shooterMovement.velocity.x).toDouble(), (targetMovement.velocity.y - shooterMovement.velocity.y).toDouble()).div(100.0)
//		val relativePosition = Vector2D((targetMovement.position.x - shooterMovement.position.x).toDouble(), (targetMovement.position.y - shooterMovement.position.y).toDouble())
//
//		// The math behind it is this:
//		// (A+B+C)^2 = (A+B+C).(A+B+C) = A.A + B.B + C.C + 2*(A.B + A.C + B.C)
//
//		// interception possible, when
//		// | (P + V*t + A/2 * t^2) | - (v*t + 1/2 * a * t^2) = 0
//
//		// (target/left side:)
//		// -> P'(t) = P.P + V.V * t^2 + 1/4 * A.A * t^4 + 2 * P.V * t + 2/2 * P.A * t^2 + 2/2 * V.A * t^3
//		// = P.P + 2 * P.V * t + (V.V + P.A) * t^2 + V.A * t^3 + 1/4 * A.A * t^4
//
//		// (interceptor/right)
//		// -> v^2 * t^2 + 1/4 * a^2 * t^4 + v*a * t^3
//
//		// final polynomial:
//		// (1/4 * A.A - 1/4 * a^2) * t^4
//		// (V.A - v*a) * t^3
//		// (V.V + P.A - v^2) * t^2
//		// (2 * P.V) * t^1
//		// (P.P) * t^0
//
//		val coefs = DoubleArray(5)
//		coefs[4] = targetAcceleration.dot(targetAcceleration) / 4.0 - (missileAcceleration * missileAcceleration) / 4.0
//		coefs[3] = relativeVelocity.dot(targetAcceleration) - missileLaunchSpeed * missileAcceleration
//		coefs[2] = relativePosition.dot(targetAcceleration) + relativeVelocity.dot(relativeVelocity) - (missileLaunchSpeed * missileLaunchSpeed)
//		coefs[1] = 2 * relativePosition.dot(relativeVelocity)
//		coefs[0] = relativePosition.dot(relativePosition)
//
//		try {
//			var initialGuess = getPositiveRootOfQuadraticEquation(missileEndAcceleration - missileStartAcceleration, missileLaunchSpeed, -relativePosition.len())
//
//			if (initialGuess.isNaN() || initialGuess <= 0) {
//				log.warn("invalid initialGuess $initialGuess")
//				initialGuess = 1.0
//			}
//
//			val complexRoots = polynomialSolver.solveAllComplex(coefs, initialGuess, POLYNOMIAL_MAX_ITERATIONS)
//
//			var solvedTime: Double? = null
//
//			if (complexRoots != null) {
//				for (root in complexRoots) {
////					println("complex root $root")
//					if (root.getImaginary() == 0.0 && root.getReal() > 0 && (solvedTime == null || root.getReal() < solvedTime)) {
//						solvedTime = root.getReal()
//					}
//				}
//			}
//
////			println("solvedTime $solvedTime")
//
//			if (solvedTime != null) {
//
//				// RPos + t * RVel + (t^2 * TAccel) / 2
//				val relativeAimPosition = relativePosition.set(relativeVelocity).scl(solvedTime).add(targetAcceleration.scl(0.5 * solvedTime * solvedTime))
//
//				val aimPosition = targetMovement.position.cpy()
//				aimPosition.add(relativeAimPosition.x.toLong(), relativeAimPosition.y.toLong())
//
//				val addedVelocity = relativePosition.set(missileLaunchSpeed + missileAcceleration * solvedTime, 0.0).rotateRad(shooterMovement.position.angleToRad(aimPosition)).scl(100.0)
//				relativeVelocity.scl(100.0).add(addedVelocity)
//				val interceptVelocity = shooterMovement.velocity.cpy().add(addedVelocity.x.toLong(), addedVelocity.y.toLong())
//
//				val interceptPosition = targetMovement.acceleration.cpy().scl(0.5 * solvedTime * solvedTime)
//				interceptPosition.mulAdd(targetMovement.velocity, FastMath.round(solvedTime))
//				interceptPosition.div(100).add(targetMovement.position)
//
//				return InterceptResult(FastMath.round(solvedTime), aimPosition, interceptPosition, interceptVelocity, Vector2L(relativeVelocity.x.toLong(), relativeVelocity.y.toLong()))
//			}
//
//		} catch (e: TooManyEvaluationsException) {
//			log.warn("Unable to solve intercept polynomial: " + e.message)
//		}

		return null
	}
	
	/**
	 * Calculates intercept with position, velocity, timed constant acceleration and then coasting
	 * Assumes timeToIntercept will be longer than thrust time
 	 */
	fun getInterceptionPosition4(shooterMovement: MovementValues, // in m, cm/s, cm/s²
															 targetMovement: MovementValues, // in m, cm/s, cm/s²
															 missileLaunchSpeed: Double, // in m/s
															 missileAcceleration: Double, // in m/s²
															 missileAccelTime: Double, // in s
	): InterceptResult? {
		//TODO implement
//		val targetAcceleration = Vector2D(targetMovement.acceleration.x.toDouble(), targetMovement.acceleration.y.toDouble()).div(100.0)
//		val relativeVelocity = Vector2D((targetMovement.velocity.x - shooterMovement.velocity.x).toDouble(), (targetMovement.velocity.y - shooterMovement.velocity.y).toDouble()).div(100.0)
//		val relativePosition = Vector2D((targetMovement.position.x - shooterMovement.position.x).toDouble(), (targetMovement.position.y - shooterMovement.position.y).toDouble())
//
//		// The math behind it is this:
//		// (A+B+C)^2 = (A+B+C).(A+B+C) = A.A + B.B + C.C + 2*(A.B + A.C + B.C)
//
//		// interception possible, when
//		// | (P + V*t + A/2 * t^2) | - (v*t + 1/2 * a * t^2) = 0
//
//		// (target/left side:)
//		// -> P'(t) = P.P + V.V * t^2 + 1/4 * A.A * t^4 + 2 * P.V * t + 2/2 * P.A * t^2 + 2/2 * V.A * t^3
//		// = P.P + 2 * P.V * t + (V.V + P.A) * t^2 + V.A * t^3 + 1/4 * A.A * t^4
//
//		// (interceptor/right)
//		// -> v^2 * t^2 + 1/4 * a^2 * t^4 + v*a * t^3
//
//		// final polynomial:
//		// (1/4 * A.A - 1/4 * a^2) * t^4
//		// (V.A - v*a) * t^3
//		// (V.V + P.A - v^2) * t^2
//		// (2 * P.V) * t^1
//		// (P.P) * t^0
//
//		val coefs = DoubleArray(5)
//		coefs[4] = targetAcceleration.dot(targetAcceleration) / 4.0 - (missileAcceleration * missileAcceleration) / 4.0
//		coefs[3] = relativeVelocity.dot(targetAcceleration) - missileLaunchSpeed * missileAcceleration
//		coefs[2] = relativePosition.dot(targetAcceleration) + relativeVelocity.dot(relativeVelocity) - (missileLaunchSpeed * missileLaunchSpeed)
//		coefs[1] = 2 * relativePosition.dot(relativeVelocity)
//		coefs[0] = relativePosition.dot(relativePosition)
//
//		try {
//			var initialGuess = getPositiveRootOfQuadraticEquation(missileAcceleration, missileLaunchSpeed, -relativePosition.len())
//
//			if (initialGuess.isNaN() || initialGuess <= 0) {
//				log.warn("invalid initialGuess $initialGuess")
//				initialGuess = 1.0
//			}
//
//			val complexRoots = polynomialSolver.solveAllComplex(coefs, initialGuess, POLYNOMIAL_MAX_ITERATIONS)
//
//			var solvedTime: Double? = null
//
//			if (complexRoots != null) {
//				for (root in complexRoots) {
////					println("complex root $root")
//					if (root.getImaginary() == 0.0 && root.getReal() > 0 && (solvedTime == null || root.getReal() < solvedTime)) {
//						solvedTime = root.getReal()
//					}
//				}
//			}
//
////			println("solvedTime $solvedTime")
//
//			if (solvedTime != null) {
//
//				// RPos + t * RVel + (t^2 * TAccel) / 2
//				val relativeAimPosition = relativePosition.set(relativeVelocity).scl(solvedTime).add(targetAcceleration.scl(0.5 * solvedTime * solvedTime))
//
//				val aimPosition = targetMovement.position.cpy()
//				aimPosition.add(relativeAimPosition.x.toLong(), relativeAimPosition.y.toLong())
//
//				val addedVelocity = relativePosition.set(missileLaunchSpeed + missileAcceleration * solvedTime, 0.0).rotateRad(shooterMovement.position.angleToRad(aimPosition)).scl(100.0)
//				relativeVelocity.scl(100.0).add(addedVelocity)
//				val interceptVelocity = shooterMovement.velocity.cpy().add(addedVelocity.x.toLong(), addedVelocity.y.toLong())
//
//				val interceptPosition = targetMovement.acceleration.cpy().scl(0.5 * solvedTime * solvedTime)
//				interceptPosition.mulAdd(targetMovement.velocity, FastMath.round(solvedTime))
//				interceptPosition.div(100).add(targetMovement.position)
//
//				return InterceptResult(FastMath.round(solvedTime), aimPosition, interceptPosition, interceptVelocity, Vector2L(relativeVelocity.x.toLong(), relativeVelocity.y.toLong()))
//			}
//
//		} catch (e: TooManyEvaluationsException) {
//			log.warn("Unable to solve intercept polynomial: " + e.message)
//		}
		
		return null
	}
	
	/**
	 * Calculates intercept with position, velocity and varying acceleration
	 */
	fun getInterceptionPosition3(shooterMovement: MovementValues, // in m, cm/s, cm/s²
															 targetMovement: MovementValues, // in m, cm/s, cm/s²
															 missileLaunchSpeed: Double, // in m/s
															 missileStartAcceleration: Double, // in m/s²
															 missileEndAcceleration: Double, // in m/s²
	): InterceptResult? {
		//TODO implement
//		val targetAcceleration = Vector2D(targetMovement.acceleration.x.toDouble(), targetMovement.acceleration.y.toDouble()).div(100.0)
//		val relativeVelocity = Vector2D((targetMovement.velocity.x - shooterMovement.velocity.x).toDouble(), (targetMovement.velocity.y - shooterMovement.velocity.y).toDouble()).div(100.0)
//		val relativePosition = Vector2D((targetMovement.position.x - shooterMovement.position.x).toDouble(), (targetMovement.position.y - shooterMovement.position.y).toDouble())
//
//		// The math behind it is this:
//		// (A+B+C)^2 = (A+B+C).(A+B+C) = A.A + B.B + C.C + 2*(A.B + A.C + B.C)
//
//		// interception possible, when
//		// | (P + V*t + A/2 * t^2) | - (v*t + 1/2 * a * t^2) = 0
//
//		// (target/left side:)
//		// -> P'(t) = P.P + V.V * t^2 + 1/4 * A.A * t^4 + 2 * P.V * t + 2/2 * P.A * t^2 + 2/2 * V.A * t^3
//		// = P.P + 2 * P.V * t + (V.V + P.A) * t^2 + V.A * t^3 + 1/4 * A.A * t^4
//
//		// (interceptor/right)
//		// -> v^2 * t^2 + 1/4 * a^2 * t^4 + v*a * t^3
//
//		// final polynomial:
//		// (1/4 * A.A - 1/4 * a^2) * t^4
//		// (V.A - v*a) * t^3
//		// (V.V + P.A - v^2) * t^2
//		// (2 * P.V) * t^1
//		// (P.P) * t^0
//
//		val coefs = DoubleArray(5)
//		coefs[4] = targetAcceleration.dot(targetAcceleration) / 4.0 - (missileAcceleration * missileAcceleration) / 4.0
//		coefs[3] = relativeVelocity.dot(targetAcceleration) - missileLaunchSpeed * missileAcceleration
//		coefs[2] = relativePosition.dot(targetAcceleration) + relativeVelocity.dot(relativeVelocity) - (missileLaunchSpeed * missileLaunchSpeed)
//		coefs[1] = 2 * relativePosition.dot(relativeVelocity)
//		coefs[0] = relativePosition.dot(relativePosition)
//
//		try {
//			var initialGuess = getPositiveRootOfQuadraticEquation(missileEndAcceleration - missileStartAcceleration, missileLaunchSpeed, -relativePosition.len())
//
//			if (initialGuess.isNaN() || initialGuess <= 0) {
//				log.warn("invalid initialGuess $initialGuess")
//				initialGuess = 1.0
//			}
//
//			val complexRoots = polynomialSolver.solveAllComplex(coefs, initialGuess, POLYNOMIAL_MAX_ITERATIONS)
//
//			var solvedTime: Double? = null
//
//			if (complexRoots != null) {
//				for (root in complexRoots) {
////					println("complex root $root")
//					if (root.getImaginary() == 0.0 && root.getReal() > 0 && (solvedTime == null || root.getReal() < solvedTime)) {
//						solvedTime = root.getReal()
//					}
//				}
//			}
//
////			println("solvedTime $solvedTime")
//
//			if (solvedTime != null) {
//
//				// RPos + t * RVel + (t^2 * TAccel) / 2
//				val relativeAimPosition = relativePosition.set(relativeVelocity).scl(solvedTime).add(targetAcceleration.scl(0.5 * solvedTime * solvedTime))
//
//				val aimPosition = targetMovement.position.cpy()
//				aimPosition.add(relativeAimPosition.x.toLong(), relativeAimPosition.y.toLong())
//
//				val addedVelocity = relativePosition.set(missileLaunchSpeed + missileAcceleration * solvedTime, 0.0).rotateRad(shooterMovement.position.angleToRad(aimPosition)).scl(100.0)
//				relativeVelocity.scl(100.0).add(addedVelocity)
//				val interceptVelocity = shooterMovement.velocity.cpy().add(addedVelocity.x.toLong(), addedVelocity.y.toLong())
//
//				val interceptPosition = targetMovement.acceleration.cpy().scl(0.5 * solvedTime * solvedTime)
//				interceptPosition.mulAdd(targetMovement.velocity, FastMath.round(solvedTime))
//				interceptPosition.div(100).add(targetMovement.position)
//
//				return InterceptResult(FastMath.round(solvedTime), aimPosition, interceptPosition, interceptVelocity, Vector2L(relativeVelocity.x.toLong(), relativeVelocity.y.toLong()))
//			}
//
//		} catch (e: TooManyEvaluationsException) {
//			log.warn("Unable to solve intercept polynomial: " + e.message)
//		}

		return null
	}
	
	/**
	 * Calculates intercept with position, velocity and constant acceleration
 	 */
	fun getInterceptionPosition2(shooterMovement: MovementValues, // in m, cm/s, cm/s²
															 targetMovement: MovementValues, // in m, cm/s, cm/s²
															 missileLaunchSpeed: Double, // in m/s
															 missileAcceleration: Double // in m/s²
	): InterceptResult? {
		
		/**
		https://www.gamedev.net/forums/topic/579481-advanced-intercept-equation
		https://www.gamedev.net/forums/?topic_id=401165&page=2
		https://www.gamedev.net/forums/topic/621460-need-help-with-interception-of-accelerated-target/
		**/
		
		val targetAcceleration = Vector2D(targetMovement.acceleration.x.toDouble(), targetMovement.acceleration.y.toDouble()).div(100.0)
		val relativeVelocity = Vector2D((targetMovement.velocity.x - shooterMovement.velocity.x).toDouble(), (targetMovement.velocity.y - shooterMovement.velocity.y).toDouble()).div(100.0)
		val relativePosition = Vector2D((targetMovement.position.x - shooterMovement.position.x).toDouble(), (targetMovement.position.y - shooterMovement.position.y).toDouble())
		
		// The math behind it is this:
		// (A+B+C)^2 = (A+B+C).(A+B+C) = A.A + B.B + C.C + 2*(A.B + A.C + B.C)
		
		// interception possible, when
		// | (P + V*t + A/2 * t^2) | - (v*t + 1/2 * a * t^2) = 0
		
		// (target/left side:)
		// -> P'(t) = P.P + V.V * t^2 + 1/4 * A.A * t^4 + 2 * P.V * t + 2/2 * P.A * t^2 + 2/2 * V.A * t^3
		// = P.P + 2 * P.V * t + (V.V + P.A) * t^2 + V.A * t^3 + 1/4 * A.A * t^4
		
		// (interceptor/right)
		// -> v^2 * t^2 + 1/4 * a^2 * t^4 + v*a * t^3
		
		// final polynomial:
		// (1/4 * A.A - 1/4 * a^2) * t^4
		// (V.A - v*a) * t^3
		// (V.V + P.A - v^2) * t^2
		// (2 * P.V) * t^1
		// (P.P) * t^0
		
		val coefs = DoubleArray(5)
		coefs[4] = targetAcceleration.dot(targetAcceleration) / 4.0 - (missileAcceleration * missileAcceleration) / 4.0
		coefs[3] = relativeVelocity.dot(targetAcceleration) - missileLaunchSpeed * missileAcceleration
		coefs[2] = relativePosition.dot(targetAcceleration) + relativeVelocity.dot(relativeVelocity) - (missileLaunchSpeed * missileLaunchSpeed)
		coefs[1] = 2 * relativePosition.dot(relativeVelocity)
		coefs[0] = relativePosition.dot(relativePosition)
		
		try {
			var initialGuess = getPositiveRootOfQuadraticEquation(missileAcceleration, missileLaunchSpeed, -relativePosition.len())
			
			if (initialGuess.isNaN() || initialGuess <= 0) {
				log.warn("invalid initialGuess $initialGuess")
				initialGuess = 1.0
			}
			
			val complexRoots = polynomialSolver.solveAllComplex(coefs, initialGuess, POLYNOMIAL_MAX_ITERATIONS)
			
			var solvedTime: Double? = null
			
			if (complexRoots != null) {
				for (root in complexRoots) {
//					println("complex root $root")
					if (root.getImaginary() == 0.0 && root.getReal() > 0 && (solvedTime == null || root.getReal() < solvedTime)) {
						solvedTime = root.getReal()
					}
				}
			}
			
//			println("solvedTime $solvedTime, initialGuess $initialGuess")
			
			if (solvedTime != null) {
				
				// RPos + t * RVel + (t^2 * TAccel) / 2
				val relativeAimPosition = relativePosition.set(relativeVelocity).scl(solvedTime).add(targetAcceleration.scl(0.5 * solvedTime * solvedTime))
				
				val aimPosition = targetMovement.position.cpy()
				aimPosition.add(relativeAimPosition.x.toLong(), relativeAimPosition.y.toLong())
				
				val addedVelocity = relativePosition.set(missileLaunchSpeed + missileAcceleration * solvedTime, 0.0).rotateRad(shooterMovement.position.angleToRad(aimPosition)).scl(100.0)
				relativeVelocity.scl(100.0).add(addedVelocity)
				val interceptVelocity = shooterMovement.velocity.cpy().add(addedVelocity.x.toLong(), addedVelocity.y.toLong())
				
				val interceptPosition = targetMovement.acceleration.cpy().scl(0.5 * solvedTime * solvedTime)
				interceptPosition.mulAdd(targetMovement.velocity, FastMath.round(solvedTime))
				interceptPosition.div(100).add(targetMovement.position)
				
				return InterceptResult(FastMath.round(solvedTime), aimPosition, interceptPosition, interceptVelocity, Vector2L(relativeVelocity.x.toLong(), relativeVelocity.y.toLong()))
			}
			
		} catch (e: TooManyEvaluationsException) {
			log.warn("Unable to solve intercept polynomial: " + e.message)
		}
		
		return null
	}
	
	/**
	 * Calculates intercept with position and velocity https://www.gamedev.net/forums/?topic_id=401165
 	 */
	fun getInterceptionPosition1(shooterMovement: MovementValues,
															 targetMovement: MovementValues,
															 projectileSpeed: Double
	): InterceptResult? {
		
		val relativeVelocity = Vector2D((targetMovement.velocity.x - shooterMovement.velocity.x).toDouble(), (targetMovement.velocity.y - shooterMovement.velocity.y).toDouble()).div(100.0)
		val relativePosition = targetMovement.position.cpy().sub(shooterMovement.position)
		
		val a: Double = projectileSpeed * projectileSpeed - relativeVelocity.dot(relativeVelocity)
		val b: Double = 2 * relativeVelocity.dot(relativePosition.x.toDouble(), relativePosition.y.toDouble())
		val c: Double = relativePosition.dot(-relativePosition.x, -relativePosition.y).toDouble()
		
		val root = getPositiveRootOfQuadraticEquationSafe(a, b, c)
		
		if (root == null || root < 0) { // Intercept is not possible
			return null
		}
		
//		println("simple root $root")
		
		relativeVelocity.scl(root)
		
		val aimPosition = relativePosition.set(targetMovement.position)
		aimPosition.add(relativeVelocity.x.toLong(), relativeVelocity.y.toLong())
		
		val interceptPosition = targetMovement.velocity.cpy().scl(FastMath.round(root))
		interceptPosition.div(100).add(targetMovement.position)
		
		val projectileVelocity = Vector2D(projectileSpeed, 0.0).rotateRad(shooterMovement.position.angleToRad(aimPosition))
		relativeVelocity.add(projectileVelocity).scl(100.0)
		
		return InterceptResult(FastMath.round(root), aimPosition, interceptPosition, Vector2L(projectileVelocity.x.toLong(), projectileVelocity.y.toLong()), Vector2L(relativeVelocity.x.toLong(), relativeVelocity.y.toLong()))
	}
	
}
