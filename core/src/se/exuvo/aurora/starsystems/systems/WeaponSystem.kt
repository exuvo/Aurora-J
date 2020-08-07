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
import se.exuvo.aurora.starsystems.components.OwnerComponent
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

class WeaponSystem : IteratingSystem(FAMILY), PreSystem {
	companion object {
		val FAMILY = Aspect.all(ActiveTargetingComputersComponent::class.java)
		
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
		
		@JvmStatic
		val log = LogManager.getLogger(WeaponSystem::class.java)
		
		const val POLYNOMIAL_MAX_ITERATIONS: Int = 10000
	}

	lateinit private var idleTargetingComputersComponentMapper: ComponentMapper<IdleTargetingComputersComponent>
	lateinit private var activeTargetingComputersComponentMapper: ComponentMapper<ActiveTargetingComputersComponent>
	lateinit private var shipMapper: ComponentMapper<ShipComponent>
	lateinit private var uuidMapper: ComponentMapper<UUIDComponent>
	lateinit private var nameMapper: ComponentMapper<NameComponent>
	lateinit private var movementMapper: ComponentMapper<TimedMovementComponent>
	lateinit private var ownerMapper: ComponentMapper<OwnerComponent>
	lateinit private var renderMapper: ComponentMapper<RenderComponent>
	lateinit private var laserShotMapper: ComponentMapper<LaserShotComponent>
	lateinit private var railgunShotMapper: ComponentMapper<RailgunShotComponent>
	lateinit private var missileMapper: ComponentMapper<MissileComponent>
	lateinit private var timedLifeMapper: ComponentMapper<TimedLifeComponent>
	lateinit private var predictedMovementMapper: ComponentMapper<OnPredictedMovementComponent>

	@Wire
	lateinit private var starSystem: StarSystem
	lateinit private var events: EventSystem
	lateinit private var powerSystem: PowerSystem
	lateinit private var targetingSystem: TargetingSystem
	
	private val galaxy = GameServices[Galaxy::class]
	private val galaxyGroupSystem by lazy (LazyThreadSafetyMode.NONE) { GameServices[GroupSystem::class] }
	
	val polynomialSolver: LaguerreSolver

	init {
//		val relativeAccuracy = 1.0e-12
//		val absoluteAccuracy = 1.0e-8
//		val functionValueAccuracy = ?
//		polynomialSolver = LaguerreSolver(relativeAccuracy, absoluteAccuracy)
		polynomialSolver = LaguerreSolver()
	}

	//TODO do when tc is deactivated
//	override fun inserted(entities: IntBag) {
//		entities.forEachFast { entityID ->
//			val ship = shipMapper.get(entityID)
//			val activeTCs = activeTargetingComputersComponentMapper.get(entityID)!!
//
//			activeTCs.targetingComputers.forEachFast { tc ->
//				if (ship.isPartEnabled(tc)) {
//					val poweredState = ship.getPartState(tc)[PoweredPartState::class]
//					poweredState.requestedPower = tc.part.powerConsumption
//				}
//			}
//		}
//	}
	
	//TODO shutdown all weapons when InCombatComponent was removed

	fun reloadAmmoWeapons(entityID: Int, ship: ShipComponent, tcState: TargetingComputerState) {
		
		while(true) {
			val partRef: PartRef<Part>? = tcState.reloadingWeapons.peek()
			
			if (partRef != null) {
				
				val part = partRef.part
				val ammoPart = part as AmmunitionPart
				val ammoState = ship.getPartState(partRef)[AmmunitionPartState::class]
				val ammoType = ammoState.type

				if (ammoType != null) {
					
					if (ammoState.reloadedAt == 0L) { // new

						tcState.reloadingWeapons.poll()

						if (ammoType.radius != part.ammunitionSize) {

							log.error("Wrong ammo size for $part: ${ammoState.type!!.radius} != ${part.ammunitionSize}")
							powerSystem.deactivatePart(entityID, ship, partRef)

						} else {

							// Take ammo from storage now to avoid multiple launchers trying to reload with the same last ordenance
							val removedAmmo = ship.retrieveCargo(ammoType, 1)

							if (removedAmmo > 0) {

								ammoState.reloadedAt = galaxy.time + part.reloadTime
								tcState.reloadingWeapons.add(partRef)

							} else if (ammoState.amount == 0) {

								println("Unpowering $part due to no more ammo")
								powerSystem.deactivatePart(entityID, ship, partRef)
							}
						}

					} else if (galaxy.time >= ammoState.reloadedAt) {

						tcState.reloadingWeapons.poll()

						ammoState.amount += 1
						ammoState.reloadedAt = 0

						if (ammoState.amount == 1) {
							
							if (part is Railgun) {
								val chargedState = ship.getPartState(partRef)[ChargedPartState::class]
								
								if (chargedState.charge >= part.capacitor && !tcState.readyWeapons.contains(partRef)) { // have to do the contains check if the the capacitor was overflowed too much
									tcState.readyWeapons.add(partRef)
								}
								
							} else {
								tcState.readyWeapons.add(partRef)
							}
						}

						var freeSpace = part.ammunitionAmount - ammoState.amount

						if (freeSpace > 0) {

							// Take ammo from storage now to avoid multiple launchers trying to reload with the same last ordenance
							val removedAmmo = ship.retrieveCargo(ammoType, 1)

							if (removedAmmo > 0) {
								ammoState.reloadedAt = galaxy.time + part.reloadTime
								tcState.reloadingWeapons.add(partRef)
							}
						}

					} else {
						break;
					}
					
				} else {

					println("Unpowering $part due to no ammo selected")
					powerSystem.deactivatePart(entityID, ship, partRef)
				}
				
			} else {
				break
			}
		}
	}
	
	fun reloadChargedWeapons(powerChanged: Boolean, entityID: Int, ship: ShipComponent, tcState: TargetingComputerState): Boolean {
		
		var powerChanged = powerChanged
		
		while(true) {
			val partRef: PartRef<Part>? = tcState.chargingWeapons.peek()
			
			if (partRef != null) {
				
				val part = partRef.part
				val poweredPart = part as PoweredPart
				val chargedPart = part as ChargedPart
				val poweredState = ship.getPartState(partRef)[PoweredPartState::class]
				val chargedState = ship.getPartState(partRef)[ChargedPartState::class]
				
				if (chargedState.expectedFullAt == 0L) { // new
					
					tcState.chargingWeapons.poll()
					
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
							val ammoState = ship.getPartState(partRef)[AmmunitionPartState::class]
							
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
		val tickSize = world.getDelta().toInt()
		
		subscription.getEntities().forEachFast { entityID ->
			val ship = shipMapper.get(entityID)
			val weaponsComponent = activeTargetingComputersComponentMapper.get(entityID)
			val tcs = weaponsComponent.targetingComputers

			var powerChanged = false
			
			tcs.forEachFast { tc ->
				val tcState = ship.getPartState(tc)[TargetingComputerState::class]

				reloadAmmoWeapons(entityID, ship, tcState)
				
				powerChanged = reloadChargedWeapons(powerChanged, entityID, ship, tcState)
			}
			
			if (powerChanged) {
				events.dispatch(starSystem.getEvent(PowerEvent::class).set(entityID))
			}
		}
	}

	private var tmpPosition = Vector2L()
	override fun process(entityID: Int) {

		val ship = shipMapper.get(entityID)
		val activeTCsComponent = activeTargetingComputersComponentMapper.get(entityID)
		val shipMovement = movementMapper.get(entityID).get(galaxy.time)
		val ownerEmpire = ownerMapper.get(entityID).empire

		val tcs = activeTCsComponent.targetingComputers
		var powerChanged = false

		tcs.forEachFast{ tc ->
			val tcState = ship.getPartState(tc)[TargetingComputerState::class]

			val target = tcState.target!!
			
			// Fire
			
			if (!starSystem.isEntityReferenceValid(target)) { // or dead and !forced
				
				targetingSystem.clearTarget(entityID, ship, tc)
				log.warn("Target ${target} is no longer valid for ${tc}")
				
			} else if (galaxy.time > tcState.lockCompletionAt) {
			
				val targetMovement = movementMapper.get(target.entityID).get(galaxy.time)

				//TODO cache intercepts per ship and weapon type/ordenance
				var i = 0
				var size = tcState.readyWeapons.size()
				
				while (i < size) {
					val weapon = tcState.readyWeapons[i++]
					val part = weapon.part

					when (part) {
						is BeamWeapon -> {
							val chargedState = ship.getPartState(weapon)[ChargedPartState::class]
							chargedState.charge -= part.capacitor

							val projectileSpeed = Units.C * 1000
							val result = getInterceptionPosition(shipMovement.value, targetMovement.value, projectileSpeed)
//										val result = getInterceptionPosition(shipMovement.value, targetMovement.value, projectileSpeed, 0.0)
							
							if (result == null) {
								
								log.warn("Unable to find intercept for laser and target ${target.entityID}, projectileSpeed $projectileSpeed")

								val poweredState = ship.getPartState(weapon)[PoweredPartState::class]
								
								if (poweredState.requestedPower != 0L) {
									poweredState.requestedPower = 0
									powerChanged = true
								}
								
							} else {
								
								val distance = tmpPosition.set(targetMovement.value.position).sub(shipMovement.value.position).len().toLong()
								val beamArea = part.getBeamArea(distance)
								var damage: Long = part.getDeliveredEnergyTo1MSquareAtDistance(distance)
									
								val (timeToIntercept, aimPosition, interceptPosition, interceptVelocity, relativeInterceptVelocity) = result
								val galacticTime = timeToIntercept + galaxy.time
								val galacticDays = (galacticTime / (60 * 60 * 24)).toInt()
								val days = (timeToIntercept / (60 * 60 * 24)).toInt()
								
//								if ((beamArea <= 1 || damage > 1000) && days < 30) {
//						
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
									
									ship.heat += ((100 - part.efficiency) * chargedState.charge) / 100
									
//								} else {
//									
//									log.error("Unable to find effective intercept for laser $part and target ${target.entityID}")
//								
//									val poweredState = ship.getPartState(weapon)[PoweredPartState::class]
//									
//									if (poweredState.requestedPower != 0L) {
//										poweredState.requestedPower = 0
//										powerChanged = true
//									}
//								}
								
								i--
								size--
								tcState.readyWeapons.remove(weapon)
								tcState.chargingWeapons.add(weapon)
							}
						}
						is Railgun -> {
							val ammoState = ship.getPartState(weapon)[AmmunitionPartState::class]
							val chargedState = ship.getPartState(weapon)[ChargedPartState::class]

							if (chargedState.charge >= part.capacitor && ammoState.amount > 0) {

								val munitionHull = ammoState.type!! as SimpleMunitionHull
								
								val projectileSpeed = (chargedState.charge * part.efficiency) / (100 * munitionHull.loadedMass)
								
								val result = getInterceptionPosition(shipMovement.value, targetMovement.value, projectileSpeed.toDouble())
//										val result = getInterceptionPosition(shipMovement.value, targetMovement.value, projectileSpeed.toDouble(), 0.0)
								
								if (result == null) {
									
//											log.warn("Unable to find intercept for railgun $part and target ${target.entityID}, projectileSpeed ${projectileSpeed / 100}")
									
									val poweredState = ship.getPartState(weapon)[PoweredPartState::class]
									
									if (poweredState.requestedPower != 0L) {
										poweredState.requestedPower = 0
										powerChanged = true
									}
									
								} else {
									
									val (timeToIntercept, aimPosition, interceptPosition, interceptVelocity, relativeInterceptVelocity) = result
									val galacticTime = timeToIntercept + galaxy.time
									val galacticDays = (galacticTime / (60 * 60 * 24)).toInt()
									val days = (timeToIntercept / (60 * 60 * 24)).toInt()
									
									if (days < 30) {
										
//												println("railgun projectileSpeed ${projectileSpeed / 100} m/s, interceptSpeed ${interceptVelocity.len() / 100} m/s, aimPosition $aimPosition, interceptPosition $interceptPosition, timeToIntercept $timeToIntercept s, interceptAt ${Units.daysToDate(galacticDays)} ${Units.secondsToString(galacticTime)}")
									
										val munitionEntityID = starSystem.createEntity(ownerEmpire)
										renderMapper.create(munitionEntityID)
										nameMapper.create(munitionEntityID).set(name = munitionHull.name)
										
										val damage: Long
										
										if (munitionHull.damagePattern == DamagePattern.EXPLOSIVE) {
											
											damage = munitionHull.damage
											
										} else {
											
											damage = ((munitionHull.loadedMass * projectileSpeed * projectileSpeed) / 2).toLong()
										}
										
										railgunShotMapper.create(munitionEntityID).set(target.entityID, damage, munitionHull.damagePattern, munitionHull.health)
										
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
											println("Unpowering $part due to no more ammo")
											powerSystem.deactivatePart(entityID, ship, weapon)
											
										} else if (ammoState.amount == part.ammunitionAmount) {
											tcState.reloadingWeapons.add(weapon)
										}
										
										ammoState.amount -= 1
										
									} else {
										
										val poweredState = ship.getPartState(weapon)[PoweredPartState::class]
										
										if (poweredState.requestedPower != 0L) {
											poweredState.requestedPower = 0
											powerChanged = true
										}
									}
								}
							}
						}
						is MissileLauncher -> {
							val ammoState = ship.getPartState(weapon)[AmmunitionPartState::class]

							if (ammoState.amount > 0) {
								
								//TODO handle multi stage missiles
								// Arrow 3 can be launched into an area of space before it is known where the target missile is going.
								// When the target and its course are identified, the Arrow interceptor is redirected using its thrust-vectoring nozzle to close the gap and conduct a "body-to-body" interception.
								
								val advMunitionHull = ammoState.type!! as AdvancedMunitionHull
								
								val missileAcceleration = advMunitionHull.getAverageAcceleration().toDouble()
								val missileLaunchSpeed = (100 * part.launchForce) / advMunitionHull.loadedMass
								
								val result = getInterceptionPosition(shipMovement.value, targetMovement.value, missileLaunchSpeed.toDouble() / 100, missileAcceleration / 100)
								
								if (result == null) {
									
//											log.warn("Unable to find intercept for missile $advMunitionHull and target ${target.entityID}")
									
								} else {
									
									val (timeToIntercept, aimPosition, interceptPosition, interceptVelocity, relativeInterceptVelocity) = result
									
									val galacticTime = timeToIntercept + galaxy.time
									val galacticDays = (galacticTime / (60 * 60 * 24)).toInt()
									val relativeSpeed = targetMovement.value.velocity.cpy().sub(shipMovement.value.velocity).len() * FastMath.cos(targetMovement.value.velocity.angleRad(shipMovement.value.velocity))
									val impactSpeed = relativeSpeed + missileLaunchSpeed + missileAcceleration * FastMath.min(timeToIntercept, advMunitionHull.thrustTime.toLong())
									
									if (timeToIntercept <= advMunitionHull.thrustTime) {
										
//												println("missile missileAcceleration $missileAcceleration m/s², impactSpeed ${impactSpeed / 100} ${interceptVelocity.len() / 100} m/s, interceptSpeed ${relativeInterceptVelocity.len() / 100} m/s, aimPosition $aimPosition, interceptPosition $interceptPosition, thrustTime ${advMunitionHull.getThrustTime()} s, timeToIntercept $timeToIntercept s, interceptAt ${Units.daysToDate(galacticDays)} ${Units.secondsToString(galacticTime)}")
										
										val angleRadToIntercept = shipMovement.value.position.angleToRad(interceptPosition)
										val angleRadToAimTarget = shipMovement.value.position.angleToRad(aimPosition)
										val initialVelocity = Vector2L(missileLaunchSpeed, 0).rotateRad(angleRadToIntercept).add(shipMovement.value.velocity)
										val initialAcceleration = Vector2L(advMunitionHull.getMinAcceleration(), 0).rotateRad(angleRadToAimTarget)
										val interceptAcceleration = Vector2L(advMunitionHull.getMaxAcceleration(), 0).rotateRad(angleRadToAimTarget)
										
										val munitionEntityID = starSystem.createEntity(ownerEmpire)
										renderMapper.create(munitionEntityID)
										nameMapper.create(munitionEntityID).set(name = advMunitionHull.name)
									
										missileMapper.create(munitionEntityID).set(advMunitionHull, target.entityID)
										
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
												powerSystem.deactivatePart(entityID, ship, weapon)
											}
											
										} else if (ammoState.amount == part.ammunitionAmount) {
											tcState.reloadingWeapons.add(weapon)
										}
										
										ammoState.amount--
										
									} else {
										
//												log.warn("Unable to find intercept inside thrust time for missile $advMunitionHull and target ${target.entityID}")
									}
								}
							}
						}
						else -> RuntimeException("Unsupported weapon")
					}
				}
			}
		}
		
		if (powerChanged) {
			events.dispatch(starSystem.getEvent(PowerEvent::class).set(entityID))
		}
	}
	
	fun munitionExpired(entityID: Int) {

		val movement = movementMapper.get(entityID).get(galaxy.time).value
		val laser: LaserShotComponent? = laserShotMapper.get(entityID)
		val railgun: RailgunShotComponent? = railgunShotMapper.get(entityID)
		val missile: MissileComponent? = missileMapper.get(entityID)

		if (laser != null) {

			val targetID = laser.targetEntityID

			if (!world.getEntityManager().isActive(targetID) || !shipMapper.has(targetID)) {
				return
			}

			val targetMovement = movementMapper.get(targetID).get(galaxy.time).value
			val distanceFromTarget = tmpPosition.set(movement.position).sub(targetMovement.position).len()
			
			if (distanceFromTarget < 1000L) { //TODO < target length
				return
			}
			
//			if (laser.beamArea < 1 column) {
				
				applyArmorDamage(entityID, laser.damage, DamagePattern.LASER)
				
//			} else {
				
				// spread damage
//				applyArmorDamage(entityID, laser.damage, railgun.damagePattern)
				
//			}

		} else if (railgun != null) {

			val targetID = railgun.targetEntityID

			if (!world.getEntityManager().isActive(targetID) || !shipMapper.has(targetID)) {
				return
			}

			val targetMovement = movementMapper.get(targetID).get(galaxy.time).value
			val distanceFromTarget = tmpPosition.set(movement.position).sub(targetMovement.position).len()
			
			if (distanceFromTarget < 1000L) {
				return
			}
			
			applyArmorDamage(entityID, railgun.damage, railgun.damagePattern)

		} else if (missile != null) {

			val targetID = missile.targetEntityID
		}
	}
	
	@Suppress("NAME_SHADOWING")
	fun applyArmorDamage(entityID: Int, damageEnergy: Long = 0, damagePattern: DamagePattern, damageColumn: Int? = null ) {
		val ship = shipMapper.get(entityID)
		
		var damage = damageEnergy
		var damageColumn = damageColumn
		
		if (damageColumn == null) {
			damageColumn = starSystem.random.nextInt(ship.hull.getArmorWidth())
		}
		
		var layer = ship.hull.armorLayers
		
		when (damagePattern) {
			DamagePattern.LASER -> {
				while(layer > 0) {
					var armorHP: Int = 128 + ship.armor[--layer][damageColumn]
					
					if (armorHP > 0) {
						val armorResistance = ship.hull.armorEnergyPerDamage[layer].toInt()
						val blockDamage: Int = FastMath.min(armorHP, (damage / armorResistance).toInt())
						damage -= blockDamage * armorResistance
						
						println("Damaging armor $damageColumn-$layer for $blockDamage dmg, remaining $damage")
						
						armorHP -= blockDamage
						ship.armor[layer][damageColumn] = (armorHP - 128).toByte()
						
						if (armorHP > 0 && damage < armorResistance) {
							return
						}
					}
				}
			}
			DamagePattern.KINETIC -> {
				while(layer > 0) {
					val armorHP: Int = ship.armor[--layer][damageColumn].toInt()
					
					if (armorHP > 0) {
						break
					}
				}
			}
			DamagePattern.EXPLOSIVE -> { // full damage hit block, quarter damage sides
				while(layer > 0) {
					var armorHP: Int = 128 + ship.armor[--layer][damageColumn]
					
					if (armorHP > 0) {
						val armorResistance = ship.hull.armorEnergyPerDamage[layer].toInt()
						val blockDamage: Int = FastMath.min(armorHP, (damage / armorResistance).toInt())
						damage -= blockDamage * armorResistance
						
						println("Damaging armor $damageColumn-$layer for $blockDamage dmg, remaining $damage")
						
						armorHP -= blockDamage
						ship.armor[layer][damageColumn] = (armorHP - 128).toByte()
						
						if (armorHP > 0 && damage < armorResistance) {
							return
						}
						
						break
					}
				}
				
				//TODO spread damage
			}
		}
		
		applyHullDamage(entityID, ship, damage)
	}
	
	fun applyHullDamage(entityID: Int, ship: ShipComponent, damageEnergy: Long) {
		var damage = damageEnergy / 1000
		
		if (damage > 0 && ship.totalPartHP > 0) {
			println("Damaging parts for $damage dmg")
			while (ship.damageableParts.size > 0) {
				val entry = ship.damageableParts.higherEntry(starSystem.random.nextLong(ship.damageablePartsMaxVolume))
				val partRef: PartRef<Part>
				
				if (entry.value.size() == 1) {
					partRef = entry.value[0]
					
				} else {
					partRef = entry.value[starSystem.random.nextInt(entry.value.size())]
				}
				
				var partHP = ship.getPartHP(partRef).toInt()
				
				val partDamage: Int = FastMath.min(partHP, damage.toInt())
				damage -= partDamage
				
				partHP = partHP - partDamage
				ship.setPartHP(partRef, partHP, entry)
				
				if (partHP == 0) {
					println("Part destroyed ${partRef.part}")
				}
				
				if (damage == 0L) {
					return
				}
			}
			
			if (damage > 0) {
				println("Ship destroyed")
				//TODO create derelict
				starSystem.unregisterShip(entityID, ship)
			}
		}
	}
	
	data class InterceptResult(val timeToIntercept: Long,
	                           val aimPosition: Vector2L,
	                           val interceptPosition: Vector2L,
	                           val interceptVelocity: Vector2L,
	                           val relativeInterceptVelocity: Vector2L)
	
	//TODO support increasing acceleration as fuel depletes
	// Calculates intercept with position, speed and acceleration
	fun getInterceptionPosition(shooterMovement: MovementValues, // in m, cm/s, cm/s²
	                            targetMovement: MovementValues, // in m, cm/s, cm/s²
	                            missileLaunchSpeed: Double, // in m/s
	                            missileAcceleration: Double // in m/s²
	): InterceptResult? { // timeToIntercept, interceptPosition, interceptVelocity
		
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
		coefs[3] = relativeVelocity.dot(targetAcceleration) /* / 2.0 */ - missileLaunchSpeed * missileAcceleration
		coefs[2] = relativePosition.dot(targetAcceleration) + relativeVelocity.dot(relativeVelocity) - (missileLaunchSpeed * missileLaunchSpeed)
		coefs[1] = 2 * relativePosition.dot(relativeVelocity)
		coefs[0] = relativePosition.dot(relativePosition)
		
		try {
			//TODO better start value, ex distance divided by speed^2
			val complexRoots = polynomialSolver.solveAllComplex(coefs, 1.0, POLYNOMIAL_MAX_ITERATIONS)
			
			var solvedTime: Double? = null
			
			if (complexRoots != null) {
				for (root in complexRoots) {
//					println("complex root $root")
					if (root.getImaginary() == 0.0 && root.getReal() > 0 && (solvedTime == null || root.getReal() < solvedTime)) {
						solvedTime = root.getReal()
					}
				}
			}
			
//			println("solvedTime $solvedTime")
			
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
	
	// Calculates intercept with position and speed https://www.gamedev.net/forums/?topic_id=401165
	fun getInterceptionPosition(shooterMovement: MovementValues,
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
