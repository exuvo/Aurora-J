package se.exuvo.aurora.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.Color

data class TintComponent(val color: Color = Color(Color.WHITE)) : Component