package se.exuvo.aurora.ui

import com.artemis.ComponentMapper
import com.artemis.utils.Bag
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import glm_.vec2.Vec2
import glm_.vec4.Vec4
import imgui.Col
import imgui.ImGui
import imgui.TreeNodeFlag
import imgui.WindowFlag
import imgui.internal.DrawCornerFlag
import imgui.internal.classes.Rect
import imgui.or
import imgui.u32
import se.exuvo.aurora.AuroraGame
import se.exuvo.aurora.empires.components.ColonyComponent
import se.exuvo.aurora.galactic.Player
import se.exuvo.aurora.starsystems.StarSystem
import se.exuvo.aurora.starsystems.components.NameComponent
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.components.StrategicIconComponent
import se.exuvo.aurora.starsystems.systems.GroupSystem
import se.exuvo.aurora.ui.UIScreen.UIWindow
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.utils.isNotEmpty
import se.exuvo.aurora.utils.toLinearRGB
import se.exuvo.aurora.utils.toLinearRGBwithAlphaCorrection
import kotlin.concurrent.read

// like sins of a solar empire left panel
class EmpireOverview : UIWindow() {
	
	val tmp = Vec2()
	
	override fun draw() {
		if (visible) {
			with(ImGui) {
				with(imgui.dsl) {
					
					val itemSpaceX = style.itemSpacing.x
					val itemSpaceY = style.itemSpacing.y
					
					val empire = Player.current.empire
					
					if (empire != null) {
						
						tmp.put(150, Gdx.graphics.getHeight() - 120)
						setNextWindowSize(tmp)
						tmp.put(0, 30)
						setNextWindowPos(tmp)
						setNextWindowBgAlpha(0.4f)
						var flags = WindowFlag.NoSavedSettings or WindowFlag.NoMove or WindowFlag.NoResize
						window("Empire Overview", null, flags) {
							
							/*
									View modes
										relative: current system at top
										heliocentric: home system first, rest sorted by distance from home
										
									system tree
										name
										force overview dots (all factions)
										
										repeat for us, enemy, friendly, neutral
											planets (only colonised)
											stations
											large ships
											small ships
								*/
							
							//TODO only star systems in which we have a presence in
							//TODO use icons
							
							galaxy.systems.forEachFast { system ->
								
								flags = TreeNodeFlag.DefaultOpen or TreeNodeFlag.SpanAvailWidth or TreeNodeFlag.NoTreePushOnOpen
								if (treeNodeEx(system.galacticEntityID.toString(), flags, system.getName())) {
									
									val shadow = system.shadow
									
									val nameMapper = shadow.nameMapper
									val colonyMapper = shadow.colonyMapper
									val shipMapper = shadow.shipMapper
									val strategicIconMapper = shadow.strategicIconMapper
									
									fun drawIcon(entityID: Int, icon : StrategicIconComponent, selected: Boolean) {
										val window = currentWindow
										if (window.skipItems) return
										
										val size = Vec2(17, 17)
										val bb = Rect(window.dc.cursorPos, window.dc.cursorPos + size)
										
										itemSize(bb)
										if (!itemAdd(bb, getID("$entityID"))) return
										
										if (isItemHovered()) {
											window.drawList.addRect(bb.min, bb.max, Vec4(0.8f, 0.8f, 0.8f, 1.0f).toLinearRGB().u32, 0f)
											
										} else if (selected) {
											window.drawList.addRect(bb.min, bb.max, Vec4(0.5f, 0.5f, 0.5f, 1.0f).toLinearRGB().u32, 0f)
										}
										
										if (selected) {
											window.drawList.addRectFilled(bb.min + 1, bb.max - 1, Vec4(0.3f, 0.3f, 0.3f, 0.1f).toLinearRGB().u32, 0f)
										}
										
										window.drawList.addImage(icon.baseTexture.getTexture().textureObjectHandle, bb.min + 1, bb.max - 1, Vec2(icon.baseTexture.u, icon.baseTexture.v), Vec2(icon.baseTexture.u2, icon.baseTexture.v2), Vec4(1).u32)
									}
									
									val systemColonies = empire.colonies.filter { ref -> ref.system == system }
									
									systemColonies.forEachIndexed { idx, colonyRef ->
										
										val entityID = colonyRef.entityID
										
										val name = nameMapper.get(entityID)?.name
										val colony = colonyMapper.get(entityID)
										val icon = strategicIconMapper.get(entityID)
										
										drawIcon(entityID, icon, isSelected(entityID, system))
										
										if (isItemHovered()) {
											setTooltip("%s","c${system.sid}-$idx")
										}
										
//										var nodeFlags = TreeNodeFlag.Leaf or TreeNodeFlag.SpanAvailWidth or TreeNodeFlag.NoTreePushOnOpen
//
//										if (isSelected(entityID, system)) {
//											nodeFlags = nodeFlags or TreeNodeFlag.Selected
//										}
//
//										treeNodeEx("c${system.sid}-$idx", nodeFlags, "$name - ${colony.population}")
										
										if (isItemClicked()) {
											if (Player.current.selection.isNotEmpty() && !Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
												Player.current.selection.clear()
											}
											
											Player.current.selection.add(shadow.getEntityReference(entityID))
										}
										
										if (idx < systemColonies.size - 1) {
											sameLine(0f, 0f)
										}
									}
									
									shadow.empireShips[empire]?.values?.forEach { bag ->
										bag.forEachFast { index, entityID ->

											val name = nameMapper.get(entityID).name
											val ship = shipMapper.get(entityID)
											val icon = strategicIconMapper.get(entityID)
											
											drawIcon(entityID, icon, isSelected(entityID, system))
											
											//TODO shield, armor, health bars
											
											if (isItemHovered()) {
												setTooltip("%s","$name - ${ship.hull.toString()}")
												
												//TODO detailed shield, armor, total parts health
											}
											
											if (isItemClicked()) {
												if (Player.current.selection.isNotEmpty() && !Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
													Player.current.selection.clear()
												}
												
												Player.current.selection.add(shadow.getEntityReference(entityID))
											}
											
											if (index < bag.size() - 1) {
												sameLine(0f, 0f)
											}
											
//											var nodeFlags = TreeNodeFlag.Leaf or TreeNodeFlag.SpanAvailWidth or TreeNodeFlag.NoTreePushOnOpen
//
//											if (isSelected(entityID, system)) {
//												nodeFlags = nodeFlags or TreeNodeFlag.Selected
//											}
//
//											treeNodeEx("s$entityID", nodeFlags, "$name - ${ship.hull.toString()}")
										}
									}
									
									//TODO show other factions
								}
							}
						}
					}
				}
			}
		}
	}
	
	private fun isSelected(entityID: Int, system: StarSystem): Boolean {
		val selection = Player.current.selection
		
		for (i in 0 until selection.size()) {
			val ref = system.shadow.resolveEntityReference(selection[i])
			
			if (ref != null && ref.system == system && ref.entityID == entityID) {
				return true
			}
		}
		
		return false
	}
}
