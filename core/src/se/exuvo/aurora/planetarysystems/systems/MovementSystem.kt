package se.exuvo.aurora.planetarysystems.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.Vector2
import org.apache.log4j.Logger
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.planetarysystems.components.ApproachType
import se.exuvo.aurora.planetarysystems.components.MassComponent
import se.exuvo.aurora.planetarysystems.components.MoveToEntityComponent
import se.exuvo.aurora.planetarysystems.components.MoveToPositionComponent
import se.exuvo.aurora.planetarysystems.components.MovementValues
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.planetarysystems.components.OrbitComponent
import se.exuvo.aurora.planetarysystems.components.ThrustComponent
import se.exuvo.aurora.planetarysystems.components.TimedMovementComponent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Vector2L

class MovementSystem : IteratingSystem(FAMILY) {
	companion object {
		val FAMILY = Family.all(TimedMovementComponent::class.java).exclude(OrbitComponent::class.java).get()
		val CAN_ACCELERATE_FAMILY = Family.all(ThrustComponent::class.java, MassComponent::class.java).get()
		val DESTINATION_FAMILY = Family.one(MoveToPositionComponent::class.java, MoveToEntityComponent::class.java).get()
	}

	val log = Logger.getLogger(this.javaClass)

	private val massMapper = ComponentMapper.getFor(MassComponent::class.java)
	private val thrustMapper = ComponentMapper.getFor(ThrustComponent::class.java)
	private val moveToEntityMapper = ComponentMapper.getFor(MoveToEntityComponent::class.java)
	private val moveToPositionMapper = ComponentMapper.getFor(MoveToPositionComponent::class.java)
	private val nameMapper = ComponentMapper.getFor(NameComponent::class.java)
	private val movementMapper = ComponentMapper.getFor(TimedMovementComponent::class.java)

	private val galaxy = GameServices[Galaxy::class.java]

	private val tempPosition = Vector2L()
	private val tempVelocity = Vector2()

	override fun processEntity(entity: Entity, deltaGameTime: Float) {

		val movement = movementMapper.get(entity)

		val velocity = movement.previous.value.velocity
		val position = movement.previous.value.position

		if (!CAN_ACCELERATE_FAMILY.matches(entity)) {

			position.add(velocity.x.toLong(), velocity.y.toLong())
			movement.previous.time = galaxy.time
			return
		}

		val mass = massMapper.get(entity).mass
		val thrustComponent = thrustMapper.get(entity)
		val acceleration = thrustComponent.thrust / mass
		val tickAcceleration = acceleration * deltaGameTime

		if (!DESTINATION_FAMILY.matches(entity)) {

			if (movement.next != null) {

				println("Movement: Prediction aborted due to order removed")
				val current = movement.get(galaxy.time)
				movement.set(current.value, galaxy.time)
				movement.next = null
			}

			if (velocity.isZero()) {
				return
			}

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

			nameMapper.get(entity).name = "s " + velocityMagnitute
			return
		}

		val moveToPositionComponent = moveToPositionMapper.get(entity)

		if (movement.next != null) {

			if (moveToPositionComponent != null && !movement.next!!.value.position.equals(moveToPositionComponent.target)) {

				println("Movement: Prediction aborted due to changed target position")
				val current = movement.get(galaxy.time)
				movement.set(current.value, galaxy.time)
				movement.next = null

			} else {

				val next = movement.next!!

				if (galaxy.time >= next.time) {

					println("Movement: target reached predicted time")
					movement.previous = next
					movement.next = null

					entity.remove(MoveToEntityComponent::class.java)
					entity.remove(MoveToPositionComponent::class.java)

				} else {

					val current = movement.get(galaxy.time)

					when (movement.approach) {
						ApproachType.BRACHISTOCHRONE -> {

						}
						ApproachType.BALLISTIC -> {
							nameMapper.get(entity).name = "a " + current.value.velocity.len()
						}
						else -> {
							throw RuntimeException("Unknown approach type: " + movement.approach)
						}
					}
				}

				return
			}
		}

		val moveToEntityComponent = moveToEntityMapper.get(entity)

		val targetEntity = moveToEntityComponent?.target
		var targetMovement: MovementValues? = null
		val targetPosition: Vector2L

		if (targetEntity != null) {

			targetMovement = movementMapper.get(targetEntity).get(galaxy.time).value
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
		val timeToTraverseFrom0Velocity = 2 * Math.sqrt(distance / tickAcceleration) // Base formula = t = sqrt(2d / a), Inital speed 0 and reaches target at speed 

//		println("timeToTraverseFrom0Velocity ${TimeUnits.secondsToString(timeToTraverseFrom0Velocity.toLong())}")

		if (timeToTraverseFrom0Velocity <= 1) {
			position.set(targetPosition)
			entity.remove(MoveToEntityComponent::class.java)
			entity.remove(MoveToPositionComponent::class.java)
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
		val timeToTargetWithCurrentSpeed = distance / (velocityMagnitute + acceleration)
//		println("angleToTarget, $angleToTarget , velocityAngle $velocityAngle")

		when (approach) {
			ApproachType.BRACHISTOCHRONE -> {

				val timeToStop = (velocityMagnitute - velocityAngleScale * targetVelocityMagnitude) / acceleration

//			println("timeToTargetWithCurrentSpeed ${timeToTargetWithCurrentSpeed}, timeToStop ${timeToStop}")
//				println("angleToTarget, $angleToTarget, velocityAngle $velocityAngle, tempVelocityAngle ${tempVelocity.angle()}")

//				if (timedMovement != null && (timedMovement.next == null || !timedMovement.next!!.value.position.equals(targetPosition))) {
//
//					timedMovement.setPredictionBallistic()
//				}

				if (timeToTargetWithCurrentSpeed <= timeToStop && velocityMagnitute > 0) {

					nameMapper.get(entity).name = "b " + velocityMagnitute

					if (timeToStop <= 1) {
						position.set(targetPosition)
						entity.remove(MoveToEntityComponent::class.java)
						entity.remove(MoveToPositionComponent::class.java)
						println("Movement: Brachistochrone target reached time")

						return
					}

					tempVelocity.set(velocity).nor().scl(tickAcceleration.toFloat())
					velocity.sub(tempVelocity)

					thrustComponent.thrustAngle = tempVelocity.angle() - 180
					if (thrustComponent.thrustAngle < 0) thrustComponent.thrustAngle += 360;

				} else {

					nameMapper.get(entity).name = "a " + velocityMagnitute

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

				nameMapper.get(entity).name = "a " + velocityMagnitute

				if (movement.next == null && targetEntity == null && Math.abs(angleToTarget - velocityAngle) < 5) {

					println("Movement: ballistic prediction to target")
					val finalVelocity = Math.sqrt(velocityMagnitute * velocityMagnitute + 2 * acceleration * distance)

					val timeToTarget: Double

					if (velocityMagnitute == 0f) {

						timeToTarget = Math.sqrt(2 * distance / acceleration)

					} else {

						// Quadric formula https://en.wikipedia.org/wiki/Quadratic_equation#Quadratic_formula_and_its_derivation
						val a = acceleration / 2
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
					movement.setPredictionBallistic(MovementValues(targetPosition.cpy(), tempVelocity.cpy()), acceleration, acceleration, galaxy.time + timeToTarget.toLong())
					thrustComponent.thrustAngle = angleToTarget

					return
				}

				if (timeToTargetWithCurrentSpeed <= 1) {

					position.set(targetPosition)
					entity.remove(MoveToEntityComponent::class.java)
					entity.remove(MoveToPositionComponent::class.java)
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