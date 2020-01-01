package se.exuvo.aurora.planetarysystems.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.World
import com.artemis.WorldConfigurationBuilder.Priority
import com.artemis.systems.IteratingSystem
import com.badlogic.gdx.math.Vector2
import org.apache.logging.log4j.LogManager
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
import se.exuvo.aurora.utils.forEachFast
import org.apache.commons.math3.util.FastMath
import se.exuvo.aurora.utils.Vector2D

class MovementSystem : IteratingSystem(FAMILY), PreSystem {
	companion object {
		val FAMILY = Aspect.all(TimedMovementComponent::class.java).exclude(OrbitComponent::class.java)
		val CAN_ACCELERATE_FAMILY = Aspect.all(ThrustComponent::class.java, MassComponent::class.java)
		val DESTINATION_FAMILY = Aspect.one(MoveToPositionComponent::class.java, MoveToEntityComponent::class.java)
		
		@JvmStatic
		val log = LogManager.getLogger(MovementSystem::class.java)
	}

	lateinit private var massMapper: ComponentMapper<MassComponent>
	lateinit private var thrustMapper: ComponentMapper<ThrustComponent>
	lateinit private var moveToEntityMapper: ComponentMapper<MoveToEntityComponent>
	lateinit private var moveToPositionMapper: ComponentMapper<MoveToPositionComponent>
	lateinit private var nameMapper: ComponentMapper<NameComponent>
	lateinit private var movementMapper: ComponentMapper<TimedMovementComponent>

	lateinit private var weaponSystem: WeaponSystem
	private val galaxy = GameServices[Galaxy::class]
	
	lateinit var CAN_ACCELERATE_ASPECT: Aspect
	lateinit var DESTINATION_ASPECT: Aspect

	override fun setWorld(world: World) {
		super.setWorld(world)

		CAN_ACCELERATE_ASPECT = CAN_ACCELERATE_FAMILY.build(world)
		DESTINATION_ASPECT = DESTINATION_FAMILY.build(world)
	}

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
		subscription.getEntities().forEachFast { entityID ->

			if (CAN_ACCELERATE_ASPECT.isInterested(world.getEntity(entityID))) {
				val movement = movementMapper.get(entityID)
				val velocity = movement.previous.value.velocity
				val thrustComponent = thrustMapper.get(entityID)

				if (!DESTINATION_ASPECT.isInterested(world.getEntity(entityID))) {
					thrustComponent.thrusting = !velocity.isZero()

				} else {
					thrustComponent.thrusting = true
				}
			}
		}
	}

	private val tempPosition = Vector2L()
	private val tempVelocity = Vector2L()

	override fun process(entityID: Int) {
		val deltaGameTime = world.getDelta().toLong()

		val movement = movementMapper.get(entityID)
		val shipMovementValue = movement.previous.value
		
		val velocity = shipMovementValue.velocity
		val position = shipMovementValue.position
		val acceleration = shipMovementValue.acceleration

		if (!CAN_ACCELERATE_ASPECT.isInterested(world.getEntity(entityID))) {

			position.addDiv(velocity, 100)
			movement.previous.time = galaxy.time
			return
		}

		val mass = massMapper.get(entityID).mass
		val massL = mass.toLong()
		val thrustComponent = thrustMapper.get(entityID)
		
		val currentAcceleration = (100 * thrustComponent.thrust) / massL
		val maxAcceleration = (100 * thrustComponent.maxThrust) / massL
		val tickAcceleration = currentAcceleration * deltaGameTime
		val maxTickAcceleration = maxAcceleration * deltaGameTime

		if (!DESTINATION_ASPECT.isInterested(world.getEntity(entityID))) {

			if (!velocity.isZero()) {
				val velocityMagnitute = velocity.len()

				if (velocityMagnitute < tickAcceleration) {

					velocity.setZero()
					acceleration.setZero()
					
				} else {

					// Apply breaking in same direction as travel
					tempVelocity.set(velocity).scl(-tickAcceleration).div(velocityMagnitute)
					
					if (tickAcceleration == currentAcceleration) {
						acceleration.set(tempVelocity)
						
					} else {
						acceleration.set(velocity).scl(-currentAcceleration).div(velocityMagnitute)
					}
					
					velocity.add(tempVelocity)
					
					thrustComponent.thrustAngle = tempVelocity.angle().toFloat()
					
					tempVelocity.set(velocity).scl(deltaGameTime)
					position.addDiv(tempVelocity, 100)
					movement.previous.time = galaxy.time
				}

				nameMapper.get(entityID).name = "s " + velocity.len().toLong()
			}

			return
		}

		if (movement.next != null) { // On timed movement

			val next = movement.next!!

			if (galaxy.time >= next.time) {

				println("Movement: target reached predicted time")
				movement.previous = next
				movement.next = null
				movement.aimTarget = null
				movement.startAcceleration = null
				movement.finalAcceleration = null

				moveToEntityMapper.remove(entityID)
				moveToPositionMapper.remove(entityID)

			} else {

				val current = movement.get(galaxy.time)

				when (movement.approach) {
					ApproachType.BRACHISTOCHRONE -> {

					}
					ApproachType.BALLISTIC -> {
						nameMapper.get(entityID).name = "p " + current.value.velocity.len().toLong()
						thrustComponent.thrustAngle = current.value.acceleration.angle().toFloat()
					}
					else -> {
						throw RuntimeException("Unknown approach type: " + movement.approach)
					}
				}
			}

			return
		}

		val moveToPositionComponent = moveToPositionMapper.get(entityID)
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
		// Base formula = t = sqrt(2d / a), Inital speed 0 and reaches target at speed
		val timeToTraverseFrom0Velocity = 2 * FastMath.sqrt((100 * distance) / maxTickAcceleration) 

//		println("timeToTraverseFrom0Velocity ${TimeUnits.secondsToString(timeToTraverseFrom0Velocity.toLong())}")

		if (timeToTraverseFrom0Velocity <= 1) {
			position.set(targetPosition)
			moveToEntityMapper.remove(entityID)
			moveToPositionMapper.remove(entityID)
			println("Movement: target reached distance")
			return
		}

		val targetVelocity = if (targetMovement != null) targetMovement.velocity else Vector2L.Zero
		val targetVelocityMagnitude = targetVelocity.len()
		val angleToTarget = position.angleTo(targetPosition)
		val velocityAngle = velocity.angle();
		val targetVelocityAngle = targetVelocity.angle()
		val velocityAngleScale = (targetVelocityAngle - velocityAngle) / 180
		val velocityMagnitute = velocity.len()
		val timeToTargetWithCurrentSpeed = (100 * distance) / (velocityMagnitute + maxAcceleration)
		val positionDiffMagnitude = tempPosition.len()
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

				//TODO fix cant go south?
				
				if (timeToTargetWithCurrentSpeed < timeToStop && velocityMagnitute > 0) {

					if (timeToStop <= 1) {
						position.set(targetPosition)
						velocity.setZero()
						acceleration.setZero()
						
						moveToEntityMapper.remove(entityID)
						moveToPositionMapper.remove(entityID)
						
						println("Movement: Brachistochrone target reached time")
						nameMapper.get(entityID).name = "b " + velocity.len().toLong()

						return
					}

					// Apply breaking in same direction as travel
					tempVelocity.set(velocity).scl(tickAcceleration).div(velocityMagnitute)
					
					if (tickAcceleration == currentAcceleration) {
						acceleration.set(tempVelocity)
						
					} else {
						acceleration.set(velocity).scl(currentAcceleration).div(velocityMagnitute)
					}
					
					velocity.sub(tempVelocity)
					
					val thrustReverseAngle = (tempVelocity.angle() + 180) % 360
					thrustComponent.thrustAngle = thrustReverseAngle.toFloat()
					
					nameMapper.get(entityID).name = "b " + velocity.len().toLong()

				} else {

					tempVelocity.set(tempPosition).scl(tickAcceleration).div(positionDiffMagnitude)
					
					if (tickAcceleration == currentAcceleration) {
						acceleration.set(tempVelocity)
						
					} else {
						acceleration.set(tempPosition).scl(currentAcceleration).div(positionDiffMagnitude)
					}

					if (velocityMagnitute > 10 * currentAcceleration) {
						if (FastMath.abs(angleToTarget - velocityAngle) <= 90) { // Thrust slightly sideways if velocity is only somewhat in the wrong direction
							tempVelocity.rotate(angleToTarget - velocityAngle)

						} else if (FastMath.abs(angleToTarget - velocityAngle) > 90) { // Stop sideways velocity completly if is is too far off target
							tempVelocity.rotate(180 - (angleToTarget - velocityAngle))
						}
					}

					velocity.add(tempVelocity)
					
					thrustComponent.thrustAngle = tempVelocity.angle().toFloat()
					
					nameMapper.get(entityID).name = "a " + velocity.len().toLong()
				}

				tempVelocity.set(velocity).scl(deltaGameTime)
				position.addDiv(tempVelocity, 100)

//				println("tickAcceleration $tickAcceleration m/s, tempVelocity $tempVelocity, velocity $velocity, distance $distance")
			}
			ApproachType.BALLISTIC -> {

				nameMapper.get(entityID).name = "a " + velocityMagnitute

				if (movement.next == null && targetEntityID == null && velocityMagnitute == 0.0) {

					println("Movement: ballistic prediction to target")

					val timeToTarget = FastMath.sqrt((2 * 100 * distance) / maxAcceleration)

					val finalVelocity = maxAcceleration * timeToTarget
					
					println("timeToTarget $timeToTarget, final velocity ${finalVelocity / 100}, distance $distance, acceleration ${maxAcceleration / 100}")
					
					tempVelocity.set(finalVelocity.toLong(), 0).rotate(angleToTarget)
					
//					movement.previous.time = galaxy.time
					val targetPositionCpy = targetPosition.cpy()
					movement.setPredictionBallistic(MovementValues(targetPositionCpy, tempVelocity.cpy(), Vector2L(maxAcceleration, 0).rotate(angleToTarget)), targetPositionCpy, maxAcceleration, galaxy.time + FastMath.ceil(timeToTarget).toLong())
					thrustComponent.thrustAngle = angleToTarget.toFloat()

					return
				}
				
				if (movement.next == null) { // && targetEntityID != null
					
					val targetMovementValue: MovementValues
					
					if (targetMovement != null) {
						targetMovementValue = targetMovement
						
					} else {
						targetMovementValue = MovementValues(targetPosition, targetVelocity, Vector2L.Zero)
					}
					
					val result = weaponSystem.getInterceptionPosition(shipMovementValue, targetMovementValue, 0.0, maxAcceleration.toDouble())
										
					if (result == null) {
						
						println("Unable to find ballistic intercept to target")
						
					} else {
						
						println("Movement: ballistic prediction to target")
						
						val (timeToIntercept, aimPosition, interceptPosition, interceptVelocity) = result
						
						println("timeToIntercept $timeToIntercept, final velocity ${interceptVelocity.len()}, distance1 $distance, distance2 ${position.cpy().sub(interceptPosition).len()}, acceleration $maxAcceleration")
						
						val angleToAimTarget = position.angleTo(aimPosition)
						val angleRadToAimTarget = position.angleRad(aimPosition)
						
//						movement.previous.time = galaxy.time
						movement.setPredictionBallistic(MovementValues(interceptPosition, interceptVelocity, Vector2L(maxAcceleration, 0).rotate(angleRadToAimTarget)), aimPosition, maxAcceleration, galaxy.time + timeToIntercept)
						thrustComponent.thrustAngle = angleToAimTarget.toFloat()
					}
				}
				
				if (timeToTargetWithCurrentSpeed <= 1) {

					position.set(targetPosition)
					moveToEntityMapper.remove(entityID)
					moveToPositionMapper.remove(entityID)
					println("Movement: Ballistic target reached time")

					return
				}

				tempVelocity.set(tempPosition).scl(tickAcceleration).div(positionDiffMagnitude)

				if (velocityMagnitute > 10 * currentAcceleration) {
					if (FastMath.abs(angleToTarget - velocityAngle) <= 90) {
						tempVelocity.rotate(angleToTarget - velocityAngle)

					} else if (FastMath.abs(angleToTarget - velocityAngle) > 90) {
						tempVelocity.rotate(180 - (angleToTarget - velocityAngle))
					}
				}

				velocity.add(tempVelocity)
				thrustComponent.thrustAngle = tempVelocity.angle().toFloat()

				tempVelocity.set(velocity).scl(deltaGameTime)
				position.addDiv(tempVelocity, 100)

			}
//			else -> {
//				throw RuntimeException("Unknown approach type: " + approach)
//			}
		}

	}
}