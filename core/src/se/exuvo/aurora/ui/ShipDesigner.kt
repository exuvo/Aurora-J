package se.exuvo.aurora.ui

import glm_.vec2.Vec2
import imgui.ImGui
import imgui.TabBarFlag
import imgui.WindowFlag
import imgui.or
import se.exuvo.aurora.galactic.ShipHull
import se.exuvo.aurora.ui.UIScreen.UIWindow

class ShipDesigner : UIWindow() {

	var hull: ShipHull = ShipHull()
	
	override fun draw() {
		if (visible) {
			with(ImGui) {
				with(imgui.dsl) {
					
					val itemSpaceX = style.itemSpacing.x
					val itemSpaceY = style.itemSpacing.y
					
					window("Ship designer - $hull", ::visible, WindowFlag.None.i) {
						
						child("Tabs", Vec2(0, -80 - itemSpaceY), false, WindowFlag.None.i) {
							
							if (beginTabBar("Tabs", TabBarFlag.Reorderable or TabBarFlag.TabListPopupButton or TabBarFlag.FittingPolicyResizeDown)) {
								
								if (beginTabItem("Summary")) {
									
									text("hull selection, name, classification")
									text("summary")
									
									endTabItem()
								}
								
								if (beginTabItem("Parts")) {
									
									leftInfo()

									sameLine()

									child("middle", Vec2(-200 - itemSpaceX, 0), true, WindowFlag.None.i) {
										val height = currentWindow.contentRegionRect.height - itemSpaceY
										child("available", Vec2(0, height * 0.7f), false, WindowFlag.None.i) {
											text("parts")
										}
										child("summary", Vec2(0, height * 0.3f), false, WindowFlag.None.i) {
											text("buttons")
											text("summary")
										}
									}
									
									sameLine()
									
									child("right", Vec2(200, 0), true, WindowFlag.None.i) {
										text("errors, selected parts")
									}
									
									endTabItem()
								}
								
								if (beginTabItem("Preferred cargo")) {
									
									text("cargo, ammo, fighters")
									text("link weapons and ammo")
									
									endTabItem()
								}
								
								endTabBar()
							}
						}
						
						child("Bottom", Vec2(0, 80), true, WindowFlag.None.i) {
							text("bottom")
						}
					}
				}
			}
		}
	}

	fun leftInfo() {
		with(ImGui) {
			with(imgui.dsl) {
				
				child("Left", Vec2(200, 0), true, WindowFlag.None.i) {
					text("left")
				}
			}
		}
	}
}
