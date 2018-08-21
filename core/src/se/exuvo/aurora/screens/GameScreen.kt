package se.exuvo.aurora.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20

interface GameScreen : Screen {
    val overlay: Boolean

    fun update(deltaRealTime: Float)
    fun draw()
}

abstract class GameScreenImpl : GameScreen {
    override val overlay = false

    override fun show() {
    }

    override fun hide() {
    }

    override fun resize(width: Int, height: Int) {
    }

    override fun update(deltaRealTime: Float) {
    }

    override fun draw() {
    }

    override fun pause() {
    }

    override fun resume() {
    }

    override fun dispose() {
    }

    override fun render(deltaRealTime: Float) {
        throw UnsupportedOperationException()
    }
}