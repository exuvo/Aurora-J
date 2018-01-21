package se.exuvo.aurora.planetarysystems.components

import com.badlogic.ashley.core.Component
import se.exuvo.aurora.galactic.Empire
import se.exuvo.aurora.planetarysystems.PlanetarySystem

data class UUIDComponent(val uuid: EntityUUID) : Component

data class EntityUUID(val planetarySystemID: Int, val empireID: Int, val shipID: Long) {
}