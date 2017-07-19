package se.exuvo.aurora.planetarysystems.components

import com.badlogic.ashley.core.Component

data class TextComponent(val text: MutableList<String> = ArrayList()) : Component
