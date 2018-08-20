package se.exuvo.aurora.screens

import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Window
import com.badlogic.gdx.utils.viewport.ScreenViewport
import se.exuvo.aurora.Assets
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.utils.GameServices

class ResearchScreen : GameScreenImpl(), InputProcessor {

	private val spriteBatch by lazy { GameServices[SpriteBatch::class] }
	private val galaxy by lazy { GameServices[Galaxy::class] }

	private val stage = Stage(ScreenViewport())
	private val selectionWindow: Window
	private val skin = Assets.skinUI

	init {

		selectionWindow = Window("Selection", skin)
		stage.addActor(selectionWindow);
//		selectionWindow.setDebug(true)

		selectionWindow.setPosition(0f, 142f);
		selectionWindow.pack()
	}

	override fun show() {
	}

	override fun resize(width: Int, height: Int) {
		stage.getViewport().update(width, height, false);
	}

	override fun update(deltaRealTime: Float) {
		stage.act(deltaRealTime)

//		val selectionModificationCount = galaxyGroupSystem.getModificationCount(GroupSystem.SELECTED)
//
//		if (previousSelectionModificationCount != selectionModificationCount) {
//
//			selectionWindow.clearChildren()
//
//			val currentSelection = galaxyGroupSystem[GroupSystem.SELECTED]
//
//			if (currentSelection.isNotEmpty()) {
//
//				selectionWindow.add(Label("Name", skin))
//				selectionWindow.add(Label("Type", skin))
//
//				for (entity in currentSelection) {
//					if (nameMapper.has(entity)) {
//
//						val name = nameMapper.get(entity).name
//						selectionWindow.row()
//						selectionWindow.add(Label(name, skin))
//
//						val type: String
//
//						if (orbitMapper.has(entity)) {
//
//							val orbitComponent = orbitMapper.get(entity)
//							val planetarySystem: PlanetarySystem = galaxy.getPlanetarySystem(entity)
//							val tagSystem = planetarySystem.engine.getSystem(TagSystem::class.java)
//							val sun = tagSystem[TagSystem.SUN]
//
//							if (orbitComponent.parent == sun) {
//								type = "Planet"
//							} else {
//								type = "Moon"
//							}
//
//						} else if (thrustMapper.has(entity)) {
//							type = "Ship"
//
//						} else {
//							type = "Station"
//						}
//
//						selectionWindow.add(Label(type, skin))
//					}
//				}
//			}
//
//			selectionWindow.pack()
//			previousSelectionModificationCount = selectionModificationCount
//		}
	}

	override fun draw() {
		stage.draw()
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
		try {
			stage.dispose()
		} catch (ignore: IllegalArgumentException) {
		}
		super.dispose()
	}
}
