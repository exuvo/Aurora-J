package se.exuvo.aurora.systems

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import se.exuvo.aurora.Galaxy
import se.exuvo.aurora.utils.GameServices

abstract class DailyIteratingSystem(family: Family) : IteratingSystem(family) {

	val galaxy = GameServices[Galaxy::class.java]
	var lastDay: Int = -1

	override fun checkProcessing(): Boolean {
		return galaxy.day > lastDay
	}

	override fun update(deltaTime: Float) {
		super.update(deltaTime)
		lastDay = galaxy.day
	}

	override fun processEntity(entity: Entity, deltaTime: Float) {
		processEntity(entity)
	}

	abstract fun processEntity(entity: Entity)
}

abstract class DailySystem() : EntitySystem() {

	val galaxy = GameServices[Galaxy::class.java]
	var lastDay: Int = -1

	override fun checkProcessing(): Boolean {
		return galaxy.day > lastDay
	}

	override fun update(deltaTime: Float) {
		dailyUpdate()
		lastDay = galaxy.day
	}

	abstract fun dailyUpdate()
}

abstract class GalaxyTimeIntervalIteratingSystem(family: Family, val interval: Long) : IteratingSystem(family) {

	val galaxy = GameServices[Galaxy::class.java]
	var lastTime: Long = -interval - 1

	override fun checkProcessing(): Boolean {
		return galaxy.time - lastTime > interval
	}

	override fun update(deltaTime: Float) {
		super.update(deltaTime)
		lastTime = galaxy.time
	}

	override fun processEntity(entity: Entity, deltaTime: Float) {
		processEntity(entity)
	}

	abstract fun processEntity(entity: Entity)
}

abstract class GalaxyTimeIntervalSystem(val interval: Long) : EntitySystem() {

	val galaxy = GameServices[Galaxy::class.java]
	var lastTime: Long = -interval -1

	override fun checkProcessing(): Boolean {
		return galaxy.time - lastTime > interval
	}

	override fun update(deltaTime: Float) {
		dailyUpdate()
		lastTime = galaxy.time
	}

	abstract fun dailyUpdate()
}