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
		
		@JvmStatic
		val log = LogManager.getLogger(WeaponSystem::class.java)
	}

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
											
												val (timeToIntercept, aimPosition, interceptPosition, interceptVelocity) = result
												val galacticTime = timeToIntercept + galaxy.time
												val galacticDays = (galacticTime / (60 * 60 * 24)).toInt()
												println("laser projectileSpeed $projectileSpeed m/s, interceptSpeed ${interceptVelocity.len() / 100} m/s, aimPosition $aimPosition, interceptPosition $interceptPosition, timeToIntercept $timeToIntercept s, interceptAt ${Units.daysToDate(galacticDays)} ${Units.secondsToString(galacticTime)}")
												
												//TODO fire
												//TODO heat ship
											
//											} else {
												
//												log.error("Unable to find effective intercept for laser $part and target ${target.entityID}")
//											}
										}
										

									}
								}
								is Railgun -> {
									val ammoState = ship.getPartState(weapon)[AmmunitionPartState::class]
									val chargedState = ship.getPartState(weapon)[ChargedPartState::class]
	
									if (chargedState.charge >= part.capacitor && ammoState.amount > 0) {
	
										val munitionClass = ammoState.type!!
										
										val projectileSpeed = (chargedState.charge * part.efficiency) / munitionClass.getLoadedMass()
										val result = getInterceptionPosition(shipMovement.value, projectileSpeed.toDouble() / 100, targetMovement.value)
										
										if (result == null) {
											
											log.warn("Unable to find intercept for railgun $part and target ${target.entityID}, projectileSpeed ${projectileSpeed / 100}")
											
										} else {
										
											val (timeToIntercept, aimPosition, interceptPosition, interceptVelocity) = result
											val galacticTime = timeToIntercept + galaxy.time
											val galacticDays = (galacticTime / (60 * 60 * 24)).toInt()
											
											println("railgun projectileSpeed ${projectileSpeed / 100} m/s, interceptSpeed ${interceptVelocity.len() / 100} m/s, aimPosition $aimPosition, interceptPosition $interceptPosition, timeToIntercept $timeToIntercept s, interceptAt ${Units.daysToDate(galacticDays)} ${Units.secondsToString(galacticTime)}")
											
											//TODO fire with PooledComponents
										//TODO heat ship
										
										chargedState.charge = 0
										ammoState.amount -= 1
										}
									}
								}
								is MissileLauncher -> {
									val ammoState = ship.getPartState(weapon)[AmmunitionPartState::class]
	
									if (ammoState.amount > 0) {
										
										//TODO handle multi stage missiles
										// Arrow 3 can be launched into an area of space before it is known where the target missile is going.
										// When the target and its course are identified, the Arrow interceptor is redirected using its thrust-vectoring nozzle to close the gap and conduct a "body-to-body" interception.
										
										val munitionClass = ammoState.type!!
										
										val missileAcceleration = munitionClass.getAverageAcceleration().toDouble()
										val missileLaunchSpeed = (100 * part.launchForce) / munitionClass.getLoadedMass()
										
										val result = getInterceptionPosition(shipMovement.value, targetMovement.value, missileLaunchSpeed.toDouble() / 100, missileAcceleration / 100)
										
										if (result == null) {
											
											log.warn("Unable to find intercept for missile $munitionClass and target ${target.entityID}")
											
										} else {
											
											val (timeToIntercept, aimPosition, interceptPosition, interceptVelocity) = result
											
											val galacticTime = timeToIntercept + galaxy.time
											val galacticDays = (galacticTime / (60 * 60 * 24)).toInt()
											val relativeSpeed = targetMovement.value.velocity.cpy().sub(shipMovement.value.velocity).len() * FastMath.cos(targetMovement.value.velocity.angleToRad(shipMovement.value.velocity))
											val impactSpeed = relativeSpeed + missileLaunchSpeed + missileAcceleration * FastMath.min(timeToIntercept, munitionClass.getThrustTime().toLong())
											
											println("missile missileAcceleration $missileAcceleration m/s², impactSpeed ${impactSpeed / 100} m/s, interceptSpeed ${interceptVelocity.len() / 100} m/s, aimPosition $aimPosition, interceptPosition $interceptPosition, thrustTime ${munitionClass.getThrustTime()} s, timeToIntercept $timeToIntercept s, interceptAt ${Units.daysToDate(galacticDays)} ${Units.secondsToString(galacticTime)}")
											
											if (timeToIntercept <= munitionClass.getThrustTime()) {
												
												val angleRadToIntercept = shipMovement.value.position.angleToRad(interceptPosition)
												val initialVelocity = Vector2L(missileLaunchSpeed, 0).rotateRad(angleRadToIntercept).add(shipMovement.value.velocity)
												
												//TODO fire
												
//												val angleToPredictedTarget = position.angleTo(interceptPosition)
//						
//												movement.previous.time = galaxy.time
//												movement.setPredictionBallistic(MovementValues(interceptPosition, interceptVelocity, Vector2L.Zero), maxAcceleration, galaxy.time + timeToIntercept)
//												thrustComponent.thrustAngle = angleToPredictedTarget.toFloat()
												
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
	
	data class InterceptResult(val timeToIntercept: Long, val aimPosition: Vector2L, val interceptPosition: Vector2L, val interceptVelocity: Vector2L)
	
	//TODO support increasing acceleration as fuel depletes
	fun getInterceptionPosition(shooterMovement: MovementValues,
	                            targetMovement: MovementValues,
	                            missileLaunchSpeed: Double,
	                            missileAcceleration: Double
	): InterceptResult? { // timeToIntercept, interceptPosition, interceptVelocity
		
		/**
		https://www.gamedev.net/forums/topic/579481-advanced-intercept-equation
		https://www.gamedev.net/forums/?topic_id=401165&page=2
		https://www.gamedev.net/forums/topic/621460-need-help-with-interception-of-accelerated-target/
		**/
		
		val targetAcceleration = Vector2D(targetMovement.acceleration.x.toDouble(), targetMovement.acceleration.y.toDouble())
		val relativeVelocity = Vector2D((targetMovement.velocity.x - shooterMovement.velocity.x).toDouble(), (targetMovement.velocity.y - shooterMovement.velocity.y).toDouble())
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
				
				// RPos + t * RVel + (t^2 * TAccel) / 2
				relativePosition.set(relativeVelocity).scl(solvedTime).add(targetAcceleration.scl(0.5 * solvedTime * solvedTime))
				
				val aimPosition = targetMovement.position.cpy().sub(shooterMovement.position)
				aimPosition.add(relativePosition.x.toLong(), relativePosition.y.toLong())
				
				relativePosition.set(missileLaunchSpeed + missileAcceleration * solvedTime, 0.0).rotateRad(shooterMovement.position.angleToRad(aimPosition))
				relativeVelocity.add(relativePosition).scl(100.0)
				
				val interceptPosition = targetMovement.acceleration.cpy().scl(0.5 * solvedTime * solvedTime)
				interceptPosition.mulAdd(targetMovement.velocity, FastMath.round(solvedTime))
				interceptPosition.div(100).add(targetMovement.position)
				
				return InterceptResult(FastMath.ceil(solvedTime).toLong(), aimPosition, interceptPosition, Vector2L(relativeVelocity.x.toLong(), relativeVelocity.y.toLong()))
			}
			
		} catch (e: TooManyEvaluationsException) {
			log.error("Unable to solve intercept polynomial", e)
		}
		
		return null
	}
	
	// https://www.gamedev.net/forums/?topic_id=401165
	fun getInterceptionPosition(shooterMovement: MovementValues,
	                            projectileSpeed: Double,
	                            targetMovement: MovementValues
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
		
		val aimPosition = relativePosition.set(shooterMovement.position)
		aimPosition.add(relativeVelocity.x.toLong(), relativeVelocity.y.toLong())
		
		val interceptPosition = targetMovement.velocity.cpy().scl(FastMath.round(root))
		interceptPosition.div(100).add(targetMovement.position)
		
		relativeVelocity.scl(100.0)
		
		return InterceptResult(FastMath.ceil(root).toLong(), aimPosition, interceptPosition, Vector2L(relativeVelocity.x.toLong(), relativeVelocity.y.toLong()))
	}
	
	
}
