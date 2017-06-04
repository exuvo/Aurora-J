package se.exuvo.aurora.screens

import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import se.exuvo.aurora.Assets
import se.exuvo.aurora.utils.GameServices

class UIScreen: GameScreenImpl, InputProcessor {

	private val spriteBatch by lazy { GameServices[SpriteBatch::class.java] }
	private val uiCamera = OrthographicCamera()
	private val stage = Stage()
	private val table = Table()
	private val skin = Assets.skinUI
	
	constructor() {
		stage.addActor(table);
		table.setDebug(true)
		
		table.setFillParent(true);
//		table.setSize(100f, 100f)
//		table.setPosition(190f, 142f);
		
//		val selectionLabel = Label("Name:", skin)
//		table.add(selectionLabel);
		
//		val button = TextButton("Button 1", skin)
//		button.addListener(object: InputListener() {
//			override fun touchDown (event: InputEvent, x: Float, y: Float, pointer: Int, button: Int) : Boolean {
//				System.out.println("touchDown 1");
//				return false;
//			}
//		});
//		table.add(button);
		
	}

	override fun show() {
	}

	override fun resize(width: Int, height: Int) {
		uiCamera.setToOrtho(false, width.toFloat(), height.toFloat())
		stage.getViewport().update(width, height, true);
	}

	override fun update(deltaRealTime: Float) {
		stage.act(deltaRealTime)
	}

	override fun draw() {
		stage.draw()
		
//		spriteBatch.projectionMatrix = uiCamera.combined
//		spriteBatch.begin()
//		Assets.fontUI.draw(spriteBatch, "UI", 2f, uiCamera.viewportHeight - 3f - Assets.fontUI.lineHeight)
//		spriteBatch.end()
	}
	
	override fun keyDown(keycode: Int): Boolean {
		return stage.keyDown(keycode)
	}

	override fun keyUp(keycode: Int): Boolean {
		return stage.keyUp(keycode)
	}

	override fun keyTyped(character: Char): Boolean {
		return stage.keyTyped(character)
	}

	override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
		return stage.touchDown(screenX, screenY, pointer, button)
	}

	override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
		return stage.touchUp(screenX, screenY, pointer, button)
	}

	override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
		return stage.touchDragged(screenX, screenY, pointer)
	}

	override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
		return stage.mouseMoved(screenX, screenY)
	}
	
	override fun scrolled(amount: Int): Boolean {
		return stage.scrolled(amount)
	}
	
	override val overlay = true
	
	override fun dispose() {
		stage.dispose()
		super.dispose()
	}
}
