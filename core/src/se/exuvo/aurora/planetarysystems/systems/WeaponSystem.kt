package se.exuvo.aurora.planetarysystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.EntitySubscription.SubscriptionListener
import com.artemis.systems.IteratingSystem
import com.artemis.utils.IntBag
import net.mostlyoriginal.api.event.common.EventSystem
import org.apache.logging.log4j.LogManager
import se.exuvo.aurora.empires.components.WeaponsComponent
import se.exuvo.aurora.galactic.AmmunitionPart
import se.exuvo.aurora.galactic.BeamWeapon
import se.exuvo.aurora.galactic.ChargedPart
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.MissileLauncher
import se.exuvo.aurora.galactic.PoweredPart
import se.exuvo.aurora.galactic.Railgun
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.planetarysystems.components.AmmunitionPartState
import se.exuvo.aurora.planetarysystems.components.ChargedPartState
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.planetarysystems.components.PoweredPartState
import se.exuvo.aurora.planetarysystems.components.ShipComponent
import se.exuvo.aurora.planetarysystems.components.TargetingComputerState
import se.exuvo.aurora.planetarysystems.components.UUIDComponent
import se.exuvo.aurora.planetarysystems.events.PowerEvent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.forEach
import se.exuvo.aurora.utils.Vector2L
import com.badlogic.gdx.math.Vector2
import se.exuvo.aurora.utils.Vector2D
import se.exuvo.aurora.planetarysystems.components.TimedMovementComponent
import se.exuvo.aurora.planetarysystems.PlanetarySystem
import com.artemis.annotations.Wire
import se.exuvo.aurora.planetarysystems.components.MovementValues
import se.exuvo.aurora.utils.Units
import org.apache.commons.math3.analysis.solvers.PegasusSolver
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction
import org.apache.commons.math3.analysis.solvers.LaguerreSolver
import org.apache.commons.math3.exception.TooManyEvaluationsException
import org.apache.commons.math3.util.FastMath

class WeaponSystem : IteratingSystem(FAMILY), PreSystem {
	companion object {
		val FAMILY = Aspect.all(WeaponsComponent::class.java)
		val SHIP_FAMILY = Aspect.all(ShipComponent::class.java)
		
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
	}

	val log = LogManager.getLogger(this.javaClass)

	lateinit private var weaponsComponentMapper: ComponentMapper<WeaponsComponent>
	lateinit private var shipMapper: ComponentMapper<ShipComponent>
	lateinit private var uuidMapper: ComponentMapper<UUIDComponent>
	lateinit private var nameMapper: ComponentMapper<NameComponent>
	lateinit private var movementMapper: ComponentMapper<TimedMovementComponent>
	lateinit private var events: EventSystem

	@Wire
	lateinit private var planetarySystem: PlanetarySystem
	
	private val galaxy = GameServices[Galaxy::class]
	
	val polynomialSolver: LaguerreSolver

	init {
//		val relativeAccuracy = 1.0e-12
//		val absoluteAccuracy = 1.0e-8
//		val functionValueAccuracy = ?
//		polynomialSolver = LaguerreSolver(relativeAccuracy, absoluteAccuracy)
		polynomialSolver = LaguerreSolver()
	}

	override fun initialize() {
		super.initialize()

		world.getAspectSubscriptionManager().get(SHIP_FAMILY).addSubscriptionListener(object : SubscriptionListener {
			override fun inserted(entities: IntBag) {
				entities.forEach { entityID ->
					var weaponsComponent = weaponsComponentMapper.get(entityID)

					if (weaponsComponent == null) {

						val ship = shipMapper.get(entityID)
						val targetingComputers = ship.hull[TargetingComputer::class]

						if (targetingComputers.isNotEmpty()) {
							weaponsComponent = weaponsComponentMapper.create(entityID)
							weaponsComponent.targetingComputers = targetingComputers

							targetingComputers.forEach({
								val tc = it
								if (ship.isPartEnabled(tc)) {
									val poweredState = ship.getPartState(tc)[PoweredPartState::class]
									poweredState.requestedPower = tc.part.powerConsumption
								}
							})
						}
					}
				}
			}

			override fun removed(entities: IntBag) {}
		})
	}

	override fun preProcessSystem() {
		subscription.getEntities().forEach { entityID ->
			val ship = shipMapper.get(entityID)
			val weaponsComponent = weaponsComponentMapper.get(entityID)
			val tcs = weaponsComponent.targetingComputers

			var powerChanged = false
			
			for (tc in tcs) {
				val tcState = ship.getPartState(tc)[TargetingComputerState::class]

				if (tcState.target != null && tcState.lockCompletionAt == 0L) { // Start targeting
					tcState.lockCompletionAt = galaxy.time + tc.part.lockingTime

				} else if (tcState.target == null && tcState.lockCompletionAt != 0L) { // Stop targeting
					tcState.lockCompletionAt = 0
				}

				// Reload ammo weapons
				for (weapon in tcState.linkedWeapons) {
					if (ship.isPartEnabled(weapon) && weapon.part is PoweredPart) {
						val part = weapon.part
						val poweredState = ship.getPartState(weapon)[PoweredPartState::class]
						var powerWanted = tcState.target != null

						if (part is AmmunitionPart) {
							val ammoState = ship.getPartState(weapon)[AmmunitionPartState::class]

							if (ammoState.type != null) {

								if (ammoState.type!!.getRadius() != part.ammunitionSize) {
									log.error("Wrong ammo size for $part: ${ammoState.type!!.getRadius()} != ${part.ammunitionSize}")
									
								} else {
									
									var freeSpace = part.ammunitionAmount - ammoState.amount

									if (freeSpace > 0) {
										
										if (ammoState.reloadPowerRemaining >= 0L) {
											
											ammoState.reloadPowerRemaining -= FastMath.min(poweredState.givenPower, ammoState.reloadPowerRemaining)

											if (ammoState.reloadPowerRemaining == 0L) {

												ammoState.amount += 1
												freeSpace -= 1
												ammoState.reloadPowerRemaining = -1

											} else {

												powerWanted = true
											}
										}

										if (ammoState.reloadPowerRemaining == -1L && freeSpace > 0) {

											// Take ammo from storage now to avoid multiple launchers trying to reload with the same last ordenance
											val removedAmmo = ship.retrieveCargo(ammoState.type!!, 1)
											
											if (removedAmmo > 0) {

												powerWanted = true
												ammoState.reloadPowerRemaining = part.reloadTime * part.powerConsumption

											} else if (ammoState.amount == 0) {

												powerWanted = false

												if (poweredState.requestedPower != 0L) {
													println("Unpowering $part due to no more ammo")
												}
											}
										}
									}
								}

							} else {
								powerWanted = false
							}
						}

						if (powerWanted) {

							val idlePower = (0.1 * part.powerConsumption).toLong()

							if (part is ChargedPart) {
								val chargedState = ship.getPartState(weapon)[ChargedPartState::class]

								if (chargedState.charge < part.capacitor) {
									val wantedPower = FastMath.min(part.powerConsumption, part.capacitor - chargedState.charge)
									if (poweredState.requestedPower != wantedPower) {
										poweredState.requestedPower = wantedPower
										powerChanged = true
									}
								} else {
									if (poweredState.requestedPower != idlePower) {
										poweredState.requestedPower = idlePower
										powerChanged = true
									}
								}

							} else if (part is AmmunitionPart) {
								val ammoState = ship.getPartState(weapon)[AmmunitionPartState::class]

								if (ammoState.reloadPowerRemaining >= 0) {
									if (poweredState.requestedPower != part.powerConsumption) {
										poweredState.requestedPower = part.powerConsumption
										powerChanged = true
									}
								} else {
									if (poweredState.requestedPower != idlePower) {
										poweredState.requestedPower = idlePower
										powerChanged = true
									}
								}
							}

						} else {

							if (poweredState.requestedPower != 0L) {
								poweredState.requestedPower = 0
								powerChanged = true

								if (part is ChargedPart) {
									val chargedState = ship.getPartState(weapon)[ChargedPartState::class]
									chargedState.charge = 0
								}
							}
						}
					}
				}
			}
			
			if (powerChanged) {
				events.dispatch(planetarySystem.getEvent(PowerEvent::class).set(entityID))
			}
		}
	}

	override fun process(entityID: Int) {

		val ship = shipMapper.get(entityID)
		val weaponsComponent = weaponsComponentMapper.get(entityID)
		val shipMovement = movementMapper.get(entityID).get(galaxy.time)

		val tcs = weaponsComponent.targetingComputers

		for (tc in tcs) {
			val tcState = ship.getPartState(tc)[TargetingComputerState::class]

			val target = tcState.target
			
			// Fire
			if (target!= null) {
				
				if (!planetarySystem.isEntityReferenceValid(target)) {
					
					tcState.target = null
					log.warn("Target ${target} is no longer valid for ${tc}")
					
				} else if (galaxy.time > tcState.lockCompletionAt) {
				
					val targetMovement = movementMapper.get(target.entityID).get(galaxy.time)
	
					//TODO cache intercepts per firecontrol and weapon type/ordenance
					for (weapon in tcState.linkedWeapons) {
						if (ship.isPartEnabled(weapon)) {
							val part = weapon.part
	
							when (part) {
								is BeamWeapon -> {
									val chargedState = ship.getPartState(weapon)[ChargedPartState::class]
	
									if (chargedState.charge >= part.capacitor) {
										chargedState.charge = 0
	
										val projectileSpeed = Units.C * 1000
										val result = getInterceptionPosition(shipMovement.value, projectileSpeed, targetMovement.value)
										
										if (result == null) {
											
											log.warn("Unable to find intercept for laser and target ${target.entityID}, projectileSpeed $projectileSpeed")
											
										} else {
										
//											if (beamDivergance is sensible at timeToIntercept) {
											
												val (normalisedDirection, timeToIntercept, interceptPosition) = result
												val galacticTime = timeToIntercept + galaxy.time
												val galacticDays = (galacticTime / (60 * 60 * 24)).toInt()
												println("laser projectileSpeed ${projectileSpeed} m/s, interceptPosition $interceptPosition, timeToIntercept ${timeToIntercept}s, interceptAt ${Units.daysToDate(galacticDays)} ${Units.secondsToString(galacticTime)}, normalisedDirection $normalisedDirection")
												
//											} else {
												
//												log.error("Unable to find effective intercept for laser $part and target ${target.entityID}")
//											}
										}
										
										//TODO fire
										//TODO heat ship
									}
								}
								is Railgun -> {
									val ammoState = ship.getPartState(weapon)[AmmunitionPartState::class]
									val chargedState = ship.getPartState(weapon)[ChargedPartState::class]
	
									if (chargedState.charge >= part.capacitor && ammoState.amount > 0) {
	
										val munitionClass = ammoState.type!!
										
										val projectileSpeed = (chargedState.charge * part.efficiency) / (100L * munitionClass.getMass())
										val result = getInterceptionPosition(shipMovement.value, projectileSpeed.toDouble(), targetMovement.value)
										
										if (result == null) {
											
											log.warn("Unable to find intercept for railgun $part and target ${target.entityID}, projectileSpeed $projectileSpeed")
											
										} else {
										
											val (normalisedDirection, timeToIntercept, interceptPosition) = result
											val galacticTime = timeToIntercept + galaxy.time
											val galacticDays = (galacticTime / (60 * 60 * 24)).toInt()
											
											println("railgun projectileSpeed ${projectileSpeed} m/s, interceptPosition $interceptPosition, timeToIntercept ${timeToIntercept} s, interceptAt ${Units.daysToDate(galacticDays)} ${Units.secondsToString(galacticTime)}, normalisedDirection $normalisedDirection")
										}
										
										//TODO fire
										//TODO heat ship
										
										chargedState.charge = 0
										ammoState.amount -= 1
									}
								}
								is MissileLauncher -> {
									val ammoState = ship.getPartState(weapon)[AmmunitionPartState::class]
	
									if (ammoState.amount > 0) {
										
										//TODO handle multi stage missiles
										// Arrow 3 can be launched into an area of space before it is known where the target missile is going.
										// When the target and its course are identified, the Arrow interceptor is redirected using its thrust-vectoring nozzle to close the gap and conduct a "body-to-body" interception.
										
										val munitionClass = ammoState.type!!
										
										val targetAcceleration = Vector2D()
										val missileAcceleration = munitionClass.getAverageAcceleration().toDouble()
										val missileLaunchSpeed = part.launchForce / munitionClass.getLoadedMass()
										
										val result = getInterceptionPosition(shipMovement.value, targetMovement.value, targetAcceleration, missileLaunchSpeed.toDouble(), missileAcceleration)
										
										if (result == null) {
											
											log.warn("Unable to find intercept for missile $munitionClass and target ${target.entityID}")
											
										} else {
											
											val (normalisedDirection, timeToIntercept, interceptPosition) = result
											
											val galacticTime = timeToIntercept + galaxy.time
											val galacticDays = (galacticTime / (60 * 60 * 24)).toInt()
											val impactVelocity = missileLaunchSpeed + missileAcceleration * FastMath.min(timeToIntercept, munitionClass.getThrustTime().toLong())
											
											println("missile missileAcceleration ${missileAcceleration} m/s², impactVelocity ${impactVelocity} m/s, interceptPosition $interceptPosition, thrustTime ${munitionClass.getThrustTime()} s, timeToIntercept ${timeToIntercept} s, interceptAt ${Units.daysToDate(galacticDays)} ${Units.secondsToString(galacticTime)}, normalisedDirection $normalisedDirection")
											
											if (timeToIntercept <= munitionClass.getThrustTime()) {
												
												normalisedDirection.scl(missileLaunchSpeed.toFloat())
												
												//TODO fire
												
												ammoState.amount -= 1
												
											} else {
												
												log.warn("Unable to find intercept inside thrust time for missile $munitionClass and target ${target.entityID}")
											}
										}
									}
								}
								else -> RuntimeException("Unsupported weapon")
							}
						}
					}
				}
			}
		}
	}
	
	fun getInterceptionPosition(shooterMovement: MovementValues,
	                            targetMovement: MovementValues,
	                            targetAcceleration: Vector2D,
	                            missileLaunchSpeed: Double,
	                            missileAcceleration: Double
	): Triple<Vector2, Long, Vector2L>? {
		
		/**
		https://www.gamedev.net/forums/topic/579481-advanced-intercept-equation
		https://www.gamedev.net/forums/?topic_id=401165&page=2
		https://www.gamedev.net/forums/topic/621460-need-help-with-interception-of-accelerated-target/
		**/
		
		val relativeVelocity = Vector2D((targetMovement.velocity.x - shooterMovement.velocity.x).toDouble(), (targetMovement.velocity.y - shooterMovement.velocity.y).toDouble())
		val relativePosition = Vector2D((targetMovement.position.x - shooterMovement.position.x).toDouble(), (targetMovement.position.y - shooterMovement.position.y).toDouble())
		
		val coefs = DoubleArray(5)
		coefs[4] = targetAcceleration.dot(targetAcceleration) / 4.0 - FastMath.pow(missileAcceleration, 2.0) / 4.0
		coefs[3] = relativeVelocity.dot(targetAcceleration) /* / 2.0 */ - missileLaunchSpeed * missileAcceleration
		coefs[2] = relativePosition.dot(targetAcceleration) + relativeVelocity.dot(relativeVelocity) - FastMath.pow(missileLaunchSpeed, 2.0)
		coefs[1] = 2 * relativePosition.dot(relativeVelocity)
		coefs[0] = relativePosition.dot(relativePosition)
		
		try {
			val complexRoots = polynomialSolver.solveAllComplex(coefs, 1.0)
			
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
				
				relativeVelocity.scl(solvedTime)
				targetAcceleration.scl(0.5 * FastMath.pow(solvedTime, 2.0))
				
				val interceptPosition = shooterMovement.position.cpy()
				interceptPosition.add(relativeVelocity.x.toLong(), relativeVelocity.y.toLong())
				interceptPosition.add(targetAcceleration.x.toLong(), targetAcceleration.y.toLong())
				
				val normalisedDirection = Vector2(interceptPosition.x.toFloat(), interceptPosition.y.toFloat()).nor()
				
				return Triple(normalisedDirection, FastMath.ceil(solvedTime).toLong(), interceptPosition)
			}
			
		} catch (e: TooManyEvaluationsException) {
			log.error("Unable to solve intercept polynomial", e)
		}
		
		return null
	}
	
	// https://www.gamedev.net/forums/?topic_id=401165
	fun getInterceptionPosition(shooterMovement: MovementValues, projectileSpeed: Double, targetMovement: MovementValues): Triple<Vector2, Long, Vector2L>? {
		
		val relativeVelocity = Vector2D((targetMovement.velocity.x - shooterMovement.velocity.x).toDouble(), (targetMovement.velocity.y - shooterMovement.velocity.y).toDouble())
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
		
		val interceptPosition = relativePosition.set(shooterMovement.position)
		interceptPosition.add(relativeVelocity.x.toLong(), relativeVelocity.y.toLong())
		
		val normalisedDirection = Vector2(interceptPosition.x.toFloat(), interceptPosition.y.toFloat()).nor()
		
		return Triple(normalisedDirection, FastMath.ceil(root).toLong(), interceptPosition)
	}
	
	
}
