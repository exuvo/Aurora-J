package com.thedeadpixelsociety.ld34.screens

import com.badlogic.gdx.Screen

interface GameScreen : Screen {
    val overlay: Boolean

    fun update(deltaRealTime: Float)
    fun draw()
}
