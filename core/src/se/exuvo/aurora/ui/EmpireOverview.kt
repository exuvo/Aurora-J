package se.exuvo.aurora.ui

import com.badlogic.gdx.Gdx
import glm_.vec2.Vec2
import imgui.ImGui
import imgui.WindowFlag
import imgui.or
import se.exuvo.aurora.ui.UIScreen.UIWindow

class EmpireOverview : UIWindow() {

	val tmp = Vec2()
	
	override fun draw() {
		if (visible) {
			with(ImGui) {
				with(imgui.dsl) {
					
					val itemSpaceX = style.itemSpacing.x
					val itemSpaceY = style.itemSpacing.y
					
					tmp.put(150, Gdx.graphics.getHeight() - 120)
					setNextWindowSize(tmp)
					tmp.put(0, 30)
					setNextWindowPos(tmp)
					setNextWindowBgAlpha(0.4f)
					val flags = WindowFlag.NoSavedSettings or WindowFlag.NoMove or WindowFlag.NoResize
					window("Empire Overview", null, flags) {
						//TODO like sins of a solar empire left panel
						textUnformatted("ships here")
						// system tree
						//  planets
						//  ships
					}
				}
			}
		}
	}
}
