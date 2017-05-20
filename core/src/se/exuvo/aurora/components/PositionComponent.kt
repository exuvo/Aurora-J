package com.thedeadpixelsociety.ld34.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector2

// In km
data class PositionComponent(val position: Vector2 = Vector2()) : Component