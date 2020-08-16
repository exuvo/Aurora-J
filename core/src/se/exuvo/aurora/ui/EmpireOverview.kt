package se.exuvo.aurora.ui

import com.artemis.ComponentMapper
import com.artemis.utils.Bag
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import glm_.vec2.Vec2
import imgui.ImGui
import imgui.TreeNodeFlag
import imgui.WindowFlag
import imgui.or
import se.exuvo.aurora.AuroraGame
import se.exuvo.aurora.empires.components.ColonyComponent
import se.exuvo.aurora.galactic.Player
import se.exuvo.aurora.starsystems.StarSystem
import se.exuvo.aurora.starsystems.components.NameComponent
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.systems.GroupSystem
import se.exuvo.aurora.ui.UIScreen.UIWindow
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.utils.isNotEmpty
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
									
									val systemColonies = empire.colonies.filter { ref -> ref.system == system }
									
									systemColonies.forEachIndexed { idx, colonyRef ->
										
										val entityID = colonyRef.entityID
										
										val name = nameMapper.get(entityID)?.name
										val colony = colonyMapper.get(entityID)
										
										var nodeFlags = TreeNodeFlag.Leaf or TreeNodeFlag.SpanAvailWidth or TreeNodeFlag.NoTreePushOnOpen
										
										if (isSelected(entityID, system)) {
											nodeFlags = nodeFlags or TreeNodeFlag.Selected
										}
										
										treeNodeEx("c${system.sid}-$idx", nodeFlags, "$name - ${colony.population}")
										
										if (isItemClicked()) {
											if (galaxyGroupSystem.get(GroupSystem.SELECTED).isNotEmpty() && !Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
												galaxyGroupSystem.clear(GroupSystem.SELECTED)
											}
											
											galaxyGroupSystem.add(shadow.getEntityReference(entityID), GroupSystem.SELECTED)
										}
									}
									
									shadow.empireShips[empire]?.values?.forEach { bag ->
										bag.forEachFast { entityID ->

											val name = nameMapper.get(entityID).name
											val ship = shipMapper.get(entityID)

											var nodeFlags = TreeNodeFlag.Leaf or TreeNodeFlag.SpanAvailWidth or TreeNodeFlag.NoTreePushOnOpen

											if (isSelected(entityID, system)) {
												nodeFlags = nodeFlags or TreeNodeFlag.Selected
											}

											treeNodeEx("s$entityID", nodeFlags, "$name - ${ship.hull.toString()}")

											if (isItemClicked()) {
												if (galaxyGroupSystem.get(GroupSystem.SELECTED).isNotEmpty() && !Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
													galaxyGroupSystem.clear(GroupSystem.SELECTED)
												}

												galaxyGroupSystem.add(shadow.getEntityReference(entityID), GroupSystem.SELECTED)
											}
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
		val selection = galaxyGroupSystem.get(GroupSystem.SELECTED)
		
		for (i in 0 until selection.size()) {
			val ref = system.shadow.resolveEntityReference(selection[i])
			
			if (ref != null && ref.system == system && ref.entityID == entityID) {
				return true
			}
		}
		
		return false
	}
}