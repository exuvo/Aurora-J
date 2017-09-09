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
import se.exuvo.aurora.planetarysystems.components.PositionComponent
import se.exuvo.aurora.planetarysystems.components.ThrustComponent
import se.exuvo.aurora.planetarysystems.components.TimedMovementComponent
import se.exuvo.aurora.planetarysystems.components.VelocityComponent
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Vector2L

class MovementSystem : IteratingSystem(FAMILY) {
	companion object {
		val FAMILY = Family.all(PositionComponent::class.java, VelocityComponent::class.java).exclude(OrbitComponent::class.java).get()
		val CAN_ACCELERATE_FAMILY = Family.all(ThrustComponent::class.java, MassComponent::class.java).get()
		val DESTINATION_FAMILY = Family.one(MoveToPositionComponent::class.java, MoveToEntityComponent::class.java).get()
	}

	val log = Logger.getLogger(this.javaClass)

	private val massMapper = ComponentMapper.getFor(MassComponent::class.java)
	private val positionMapper = ComponentMapper.getFor(PositionComponent::class.java)
	private val thrustMapper = ComponentMapper.getFor(ThrustComponent::class.java)
	private val velocityMapper = ComponentMapper.getFor(VelocityComponent::class.java)
	private val moveToEntityMapper = ComponentMapper.getFor(MoveToEntityComponent::class.java)
	private val moveToPositionMapper = ComponentMapper.getFor(MoveToPositionComponent::class.java)
	private val nameMapper = ComponentMapper.getFor(NameComponent::class.java)
	private val timedMovementMapper = ComponentMapper.getFor(TimedMovementComponent::class.java)

	private val galaxy = GameServices[Galaxy::class.java]

	private val tempPosition = Vector2L()
	private val tempVelocity = Vector2()

	override fun processEntity(entity: Entity, deltaGameTime: Float) {

		val velocityComponent = velocityMapper.get(entity)
		val velocity = velocityComponent.velocity
		val position = positionMapper.get(entity).position

		if (!CAN_ACCELERATE_FAMILY.matches(entity)) {

			position.add(velocity.x.toLong(), velocity.y.toLong())
			return
		}

		val mass = massMapper.get(entity).mass
		val thrust = thrustMapper.get(entity).thrust
		val trueAcceleration = thrust / mass
		val acceleration = trueAcceleration * deltaGameTime

		if (!DESTINATION_FAMILY.matches(entity)) {

			if (velocity.isZero()) {
				return
			}

			val velocityMagnitute = velocity.len()

			if (velocityMagnitute < acceleration) {

				velocity.setZero()

			} else {

				tempVelocity.set(velocity).nor().scl(acceleration.toFloat())
				velocity.sub(tempVelocity)
				velocityComponent.thrustAngle = tempVelocity.angle()
				tempVelocity.set(velocity).scl(deltaGameTime)
				position.add(tempVelocity.x.toLong(), tempVelocity.y.toLong())
			}

			nameMapper.get(entity).name = "c " + velocityMagnitute

			return
		}

		val moveToEntityComponent = moveToEntityMapper.get(entity)
		val moveToPositionComponent = moveToPositionMapper.get(entity)
		val timedMovement = timedMovementMapper.get(entity)

		val targetEntity = moveToEntityComponent?.target
		val targetPosition = if (targetEntity != null) positionMapper.get(targetEntity).position else moveToPositionComponent.target
		val approach = if (moveToEntityComponent != null) moveToEntityComponent.approach else moveToPositionComponent.approach

		tempPosition.set(targetPosition).sub(position)
		val distance = tempPosition.len()

		//TODO if radiuses are touching we are done

		// t = sqrt(2d / a)
		// d = ut + 1/2at^2
		val timeToTraverseFrom0Velocity = 2 * Math.sqrt(distance / acceleration) // Base formula = t = sqrt(2d / a), Inital speed 0 and reaches target at speed 

//		println("timeToTraverseFrom0Velocity ${TimeUnits.secondsToString(timeToTraverseFrom0Velocity.toLong())}")

		if (timeToTraverseFrom0Velocity <= 1) {
			position.set(targetPosition)
			entity.remove(MoveToEntityComponent::class.java)
			entity.remove(MoveToPositionComponent::class.java)
			println("Target reached distance")
			return
		}

		val targetVelocity = if (targetEntity != null) velocityMapper.get(targetEntity)?.velocity ?: Vector2.Zero else Vector2.Zero
		val targetVelocityMagnitude = targetVelocity.len()
		val angleToTarget = position.angleTo(targetPosition).toFloat()
		val velocityAngle = velocity.angle();
		val targetVelocityAngle = targetVelocity.angle()
		val velocityAngleScale = (targetVelocityAngle - velocityAngle) / 180
		val velocityMagnitute = velocity.len()
		val timeToTargetWithCurrentSpeed = distance / (velocityMagnitute + trueAcceleration)
//		println("angleToTarget, $angleToTarget , velocityAngle $velocityAngle")

		when (approach) {
			ApproachType.BRACHISTOCHRONE -> {

				val timeToStop = (velocityMagnitute - velocityAngleScale * targetVelocityMagnitude) / trueAcceleration

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
						println("Brachistochrone target reached time")

						if (timedMovement.next != null) {
							timedMovement.previous = timedMovement.next!!
							timedMovement.next = null
						}

						return
					}

					tempVelocity.set(velocity).nor().scl(acceleration.toFloat())
					velocity.sub(tempVelocity)

					velocityComponent.thrustAngle = tempVelocity.angle() - 180
					if (velocityComponent.thrustAngle < 0) velocityComponent.thrustAngle += 360;

				} else {

					nameMapper.get(entity).name = "a " + velocityMagnitute

					tempVelocity.set(tempPosition.x.toFloat(), tempPosition.y.toFloat()).nor()
					tempVelocity.scl(acceleration.toFloat())

					if (velocityMagnitute > 0 && Math.abs(angleToTarget - velocityAngle) < 90) {
						tempVelocity.rotate(angleToTarget - velocityAngle)
					}

					velocity.add(tempVelocity)
					velocityComponent.thrustAngle = tempVelocity.angle()
				}

				tempVelocity.set(velocity).scl(deltaGameTime)
				position.add(tempVelocity.x.toLong(), tempVelocity.y.toLong())

//			println("acceleration $acceleration m/s, tempVelocity $tempVelocity, velocity $velocity, distance $distance")

			}
			ApproachType.BALLISTIC -> {

				if (timedMovement != null && (timedMovement.next == null || !timedMovement.next!!.value.position.equals(targetPosition))) {

					timedMovement.set(MovementValues(position.cpy(), velocity.cpy()), galaxy.time)

					tempVelocity.set(1f, 0f).rotate(angleToTarget).scl((acceleration * timeToTargetWithCurrentSpeed).toFloat()).add(velocity)
					timedMovement.setPredictionBallistic(MovementValues(targetPosition.cpy(), tempVelocity.cpy()), trueAcceleration, trueAcceleration, galaxy.time + timeToTargetWithCurrentSpeed.toLong())
				}

				if (timeToTargetWithCurrentSpeed <= 1) {

					position.set(targetPosition)
					entity.remove(MoveToEntityComponent::class.java)
					entity.remove(MoveToPositionComponent::class.java)
					println("Ballistic target reached time")

					if (timedMovement.next != null) {
						timedMovement.previous = timedMovement.next!!
						timedMovement.next = null
					}

					return
				}

				nameMapper.get(entity).name = "a " + velocityMagnitute

				tempVelocity.set(tempPosition.x.toFloat(), tempPosition.y.toFloat()).nor()
				tempVelocity.scl(acceleration.toFloat())

				if (velocityMagnitute > 0 && Math.abs(angleToTarget - velocityAngle) < 90) {
					tempVelocity.rotate(2f * (angleToTarget - velocityAngle))
				}

				velocity.add(tempVelocity)
				velocityComponent.thrustAngle = tempVelocity.angle()

				tempVelocity.set(velocity).scl(deltaGameTime)
				position.add(tempVelocity.x.toLong(), tempVelocity.y.toLong())

			}
			else -> {
				throw RuntimeException("Unknown approach type: " + approach)
			}
		}

	}
}