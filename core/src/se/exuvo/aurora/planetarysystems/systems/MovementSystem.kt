package se.exuvo.aurora.planetarysystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.systems.IteratingSystem
import com.badlogic.gdx.math.Vector2
import org.apache.log4j.Logger
import se.exuvo.aurora.galactic.ElectricalThruster
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.ThrustingPart
import se.exuvo.aurora.planetarysystems.components.ApproachType
import se.exuvo.aurora.planetarysystems.components.MassComponent
import se.exuvo.aurora.planetarysystems.components.MoveToEntityComponent
import se.exuvo.aurora.planetarysystems.components.MoveToPositionComponent
import se.exuvo.aurora.planetarysystems.components.MovementValues
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.planetarysystems.components.OrbitComponent
import se.exuvo.aurora.planetarysystems.components.PoweredPartState
import se.exuvo.aurora.planetarysystems.components.ThrustComponent
import se.exuvo.aurora.planetarysystems.components.TimedMovementComponent
import se.exuvo.aurora.planetarysystems.events.PowerEvent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Vector2L
import se.exuvo.aurora.utils.forEach
import com.artemis.WorldConfigurationBuilder.Priority

class MovementSystem : IteratingSystem(FAMILY), PreSystem {
	companion object {
		val FAMILY = Aspect.all(TimedMovementComponent::class.java).exclude(OrbitComponent::class.java)
		val CAN_ACCELERATE_FAMILY = Aspect.all(ThrustComponent::class.java, MassComponent::class.java)
		val DESTINATION_FAMILY = Aspect.one(MoveToPositionComponent::class.java, MoveToEntityComponent::class.java)
	}

	val canAccelerateFamily by lazy { CAN_ACCELERATE_FAMILY.build(world) }
	val destionationFamily by lazy { DESTINATION_FAMILY.build(world) }

	val log = Logger.getLogger(this.javaClass)

	lateinit private var massMapper: ComponentMapper<MassComponent>
	lateinit private var thrustMapper: ComponentMapper<ThrustComponent>
	lateinit private var moveToEntityMapper: ComponentMapper<MoveToEntityComponent>
	lateinit private var moveToPositionMapper: ComponentMapper<MoveToPositionComponent>
	lateinit private var nameMapper: ComponentMapper<NameComponent>
	lateinit private var movementMapper: ComponentMapper<TimedMovementComponent>

	private val galaxy = GameServices[Galaxy::class]

	private val tempPosition = Vector2L()
	private val tempVelocity = Vector2()

	fun moveToPosition(entityID: Int, target: Vector2L, approach: ApproachType = ApproachType.BRACHISTOCHRONE) {
		if (moveToEntityMapper.has(entityID)) {
			moveToEntityMapper.remove(entityID)
		}

		val moveToPositionComponent: MoveToPositionComponent;

		if (moveToPositionMapper.has(entityID)) {
			moveToPositionComponent = moveToPositionMapper.get(entityID)
			val movement = movementMapper.get(entityID)

			if (movement.next != null) {
				println("Movement: Prediction aborted due to changed target position")
				val current = movement.get(galaxy.time)
				movement.set(current.value, galaxy.time)
				movement.next = null
			}

		} else {
			moveToPositionComponent = moveToPositionMapper.create(entityID)
		}

		moveToPositionComponent.set(target, approach)
	}

	fun moveToEntity(entityID: Int, targetID: Int, approach: ApproachType = ApproachType.BRACHISTOCHRONE) {
		if (moveToPositionMapper.has(entityID)) {
			moveToPositionMapper.remove(entityID)
		}

		val moveToEntityComponent: MoveToEntityComponent;

		if (moveToEntityMapper.has(entityID)) {
			moveToEntityComponent = moveToEntityMapper.get(entityID)

		} else {
			moveToEntityComponent = moveToEntityMapper.create(entityID)
		}

		moveToEntityComponent.set(targetID, approach)
	}

	fun cancelMovement(entityID: Int) {
		if (moveToPositionMapper.has(entityID)) {
			moveToPositionMapper.remove(entityID)
		}

		if (moveToEntityMapper.has(entityID)) {
			moveToEntityMapper.remove(entityID)
		}

		val movement = movementMapper.get(entityID)

		if (movement.next != null) {

			println("Movement: Prediction aborted due to order canceled")
			val current = movement.get(galaxy.time)
			movement.set(current.value, galaxy.time)
			movement.next = null
		}
	}

	override fun getPreProcessPriority() = Priority.HIGH

	override fun preProcessSystem() {
		subscription.getEntities().forEach { entityID ->

			if (canAccelerateFamily.isInterested(world.getEntity(entityID))) {
				val movement = movementMapper.get(entityID)
				val velocity = movement.previous.value.velocity
				val thrustComponent = thrustMapper.get(entityID)

				if (!destionationFamily.isInterested(world.getEntity(entityID))) {
					thrustComponent.thrusting = !velocity.isZero()

				} else {
					thrustComponent.thrusting = true
				}
			}
		}
	}

	override fun process(entityID: Int) {
		val deltaGameTime = world.getDelta()

		val movement = movementMapper.get(entityID)

		val velocity = movement.previous.value.velocity
		val position = movement.previous.value.position

		if (!canAccelerateFamily.isInterested(world.getEntity(entityID))) {

			position.add(velocity.x.toLong(), velocity.y.toLong())
			movement.previous.time = galaxy.time
			return
		}

		val mass = massMapper.get(entityID).mass
		val thrustComponent = thrustMapper.get(entityID)
		val acceleration = thrustComponent.thrust / mass
		val maxAcceleration = thrustComponent.maxThrust / mass
		val tickAcceleration = acceleration * deltaGameTime
		val maxTickAcceleration = maxAcceleration * deltaGameTime

		if (!destionationFamily.isInterested(world.getEntity(entityID))) {

			if (!velocity.isZero()) {
				val velocityMagnitute = velocity.len()

				if (velocityMagnitute < tickAcceleration) {

					velocity.setZero()

				} else {

					tempVelocity.set(velocity).nor().scl(tickAcceleration.toFloat())
					velocity.sub(tempVelocity)
					thrustComponent.thrustAngle = tempVelocity.angle() + 180
					tempVelocity.set(velocity).scl(deltaGameTime)
					position.add(tempVelocity.x.toLong(), tempVelocity.y.toLong())
					movement.previous.time = galaxy.time
				}

				nameMapper.get(entityID).name = "s " + velocityMagnitute
			}

			return
		}

		val moveToPositionComponent = moveToPositionMapper.get(entityID)

		if (movement.next != null) {

			val next = movement.next!!

			if (galaxy.time >= next.time) {

				println("Movement: target reached predicted time")
				movement.previous = next
				movement.next = null

				moveToEntityMapper.remove(entityID)
				moveToPositionMapper.remove(entityID)

			} else {

				val current = movement.get(galaxy.time)

				when (movement.approach) {
					ApproachType.BRACHISTOCHRONE -> {

					}
					ApproachType.BALLISTIC -> {
						nameMapper.get(entityID).name = "a " + current.value.velocity.len()
					}
					else -> {
						throw RuntimeException("Unknown approach type: " + movement.approach)
					}
				}
			}

			return
		}

		val moveToEntityComponent = moveToEntityMapper.get(entityID)

		val targetEntityID = moveToEntityComponent?.targetID
		var targetMovement: MovementValues? = null
		val targetPosition: Vector2L

		if (targetEntityID != null) {

			targetMovement = movementMapper.get(targetEntityID).get(galaxy.time).value
			targetPosition = targetMovement.position

		} else {

			targetPosition = moveToPositionComponent.target
		}

		val approach = if (moveToEntityComponent != null) moveToEntityComponent.approach else moveToPositionComponent.approach

		tempPosition.set(targetPosition).sub(position)
		val distance = tempPosition.len()

		//TODO if radiuses are touching we are done

		// t = sqrt(2d / a)
		// d = ut + 1/2at^2
		val timeToTraverseFrom0Velocity = 2 * Math.sqrt(distance / maxTickAcceleration) // Base formula = t = sqrt(2d / a), Inital speed 0 and reaches target at speed 

//		println("timeToTraverseFrom0Velocity ${TimeUnits.secondsToString(timeToTraverseFrom0Velocity.toLong())}")

		if (timeToTraverseFrom0Velocity <= 1) {
			position.set(targetPosition)
			moveToEntityMapper.remove(entityID)
			moveToPositionMapper.remove(entityID)
			println("Movement: target reached distance")
			return
		}

		val targetVelocity = if (targetMovement != null) targetMovement.velocity else Vector2.Zero
		val targetVelocityMagnitude = targetVelocity.len()
		val angleToTarget = position.angleTo(targetPosition).toFloat()
		val velocityAngle = velocity.angle();
		val targetVelocityAngle = targetVelocity.angle()
		val velocityAngleScale = (targetVelocityAngle - velocityAngle) / 180
		val velocityMagnitute = velocity.len()
		val timeToTargetWithCurrentSpeed = distance / (velocityMagnitute + maxAcceleration)
//		println("angleToTarget, $angleToTarget , velocityAngle $velocityAngle")

		when (approach) {
			ApproachType.BRACHISTOCHRONE -> {

				val timeToStop = (velocityMagnitute - velocityAngleScale * targetVelocityMagnitude) / maxAcceleration

//			println("timeToTargetWithCurrentSpeed ${timeToTargetWithCurrentSpeed}, timeToStop ${timeToStop}")
//				println("angleToTarget, $angleToTarget, velocityAngle $velocityAngle, tempVelocityAngle ${tempVelocity.angle()}")

//				if (timedMovement != null && (timedMovement.next == null || !timedMovement.next!!.value.position.equals(targetPosition))) {
//
//					timedMovement.setPredictionBallistic()
//				}

				if (timeToTargetWithCurrentSpeed <= timeToStop && velocityMagnitute > 0) {

					nameMapper.get(entityID).name = "b " + velocityMagnitute

					if (timeToStop <= 1) {
						position.set(targetPosition)
						moveToEntityMapper.remove(entityID)
						moveToPositionMapper.remove(entityID)
						println("Movement: Brachistochrone target reached time")

						return
					}

					tempVelocity.set(velocity).nor().scl(tickAcceleration.toFloat())
					velocity.sub(tempVelocity)

					thrustComponent.thrustAngle = tempVelocity.angle() - 180
					if (thrustComponent.thrustAngle < 0) thrustComponent.thrustAngle += 360;

				} else {

					nameMapper.get(entityID).name = "a " + velocityMagnitute

					tempVelocity.set(tempPosition.x.toFloat(), tempPosition.y.toFloat()).nor()
					tempVelocity.scl(tickAcceleration.toFloat())

					if (velocityMagnitute > 10 * acceleration) {
						if (Math.abs(angleToTarget - velocityAngle) <= 90) {
							tempVelocity.rotate(angleToTarget - velocityAngle)

						} else if (Math.abs(angleToTarget - velocityAngle) > 90) {
							tempVelocity.rotate(180 - (angleToTarget - velocityAngle))
						}
					}

					velocity.add(tempVelocity)
					thrustComponent.thrustAngle = tempVelocity.angle()
				}

				tempVelocity.set(velocity).scl(deltaGameTime)
				position.add(tempVelocity.x.toLong(), tempVelocity.y.toLong())

//			println("acceleration $acceleration m/s, tempVelocity $tempVelocity, velocity $velocity, distance $distance")

			}
			ApproachType.BALLISTIC -> {

				nameMapper.get(entityID).name = "a " + velocityMagnitute

				if (movement.next == null && targetEntityID == null && Math.abs(angleToTarget - velocityAngle) < 5) {

					println("Movement: ballistic prediction to target")
					val finalVelocity = Math.sqrt(velocityMagnitute * velocityMagnitute + 2 * maxAcceleration * distance)

					val timeToTarget: Double

					if (velocityMagnitute == 0f) {

						timeToTarget = Math.sqrt(2 * distance / maxAcceleration)

					} else {

						// Quadric formula https://en.wikipedia.org/wiki/Quadratic_equation#Quadratic_formula_and_its_derivation
						val a = maxAcceleration / 2
						val b = velocityMagnitute
						val c = -distance
						val pos = (-b + Math.sqrt(b * b - 4 * a * c)) / (2 * a)
						val neg = (-b - Math.sqrt(b * b - 4 * a * c)) / (2 * a)

//						println("+$pos $neg")
						timeToTarget = Math.max(pos, neg)
					}

//					println("timeToTarget $timeToTarget, final velocity $finalVelocity, distance $distance, acceleration $acceleration")

					tempVelocity.set(1f, 0f).rotate(angleToTarget).scl(finalVelocity.toFloat())
					movement.previous.time = galaxy.time
					movement.setPredictionBallistic(MovementValues(targetPosition.cpy(), tempVelocity.cpy()), maxAcceleration, maxAcceleration, galaxy.time + timeToTarget.toLong())
					thrustComponent.thrustAngle = angleToTarget

					return
				}

				if (timeToTargetWithCurrentSpeed <= 1) {

					position.set(targetPosition)
					moveToEntityMapper.remove(entityID)
					moveToPositionMapper.remove(entityID)
					println("Movement: Ballistic target reached time")

					return
				}

				tempVelocity.set(tempPosition.x.toFloat(), tempPosition.y.toFloat()).nor()
				tempVelocity.scl(tickAcceleration.toFloat())

				if (velocityMagnitute > 10 * acceleration) {
					if (Math.abs(angleToTarget - velocityAngle) <= 90) {
						tempVelocity.rotate(angleToTarget - velocityAngle)

					} else if (Math.abs(angleToTarget - velocityAngle) > 90) {
						tempVelocity.rotate(180 - (angleToTarget - velocityAngle))
					}
				}

				velocity.add(tempVelocity)
				thrustComponent.thrustAngle = tempVelocity.angle()

				tempVelocity.set(velocity).scl(deltaGameTime)
				position.add(tempVelocity.x.toLong(), tempVelocity.y.toLong())

			}
			else -> {
				throw RuntimeException("Unknown approach type: " + approach)
			}
		}

	}
}