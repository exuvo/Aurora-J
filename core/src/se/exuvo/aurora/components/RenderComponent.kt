package com.thedeadpixelsociety.ld34.components

import com.badlogic.ashley.core.Component

enum class RenderType {
    CIRCLE,
    LINE
}

data class RenderComponent(var type: RenderType = RenderType.CIRCLE, var zOrder: Float = .5f) : Component