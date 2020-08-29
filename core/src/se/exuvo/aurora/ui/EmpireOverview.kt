package se.exuvo.aurora.ui

import com.artemis.utils.Bag
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.OrthographicCamera
import glm_.vec2.Vec2
import glm_.vec4.Vec4
import imgui.Col
import imgui.ImGui
import imgui.TreeNodeFlag
import imgui.WindowFlag
import imgui.classes.Context
import imgui.internal.classes.Rect
import imgui.or
import imgui.u32
import net.mostlyoriginal.api.utils.pooling.ObjectPool
import org.lwjgl.BufferUtils
import se.exuvo.aurora.AuroraGame
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.galactic.Part
import se.exuvo.aurora.galactic.PartRef
import se.exuvo.aurora.galactic.Player
import se.exuvo.aurora.galactic.Shield
import se.exuvo.aurora.starsystems.StarSystem
import se.exuvo.aurora.starsystems.components.CargoComponent
import se.exuvo.aurora.starsystems.components.ChargedPartState
import se.exuvo.aurora.starsystems.components.PowerComponent
import se.exuvo.aurora.starsystems.components.StrategicIconComponent
import se.exuvo.aurora.starsystems.systems.GroupSystem
import se.exuvo.aurora.starsystems.systems.RenderSystem
import se.exuvo.aurora.ui.UIScreen.UIWindow
import se.exuvo.aurora.utils.GameServices
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.utils.imgui.ShipUI
import se.exuvo.aurora.utils.isNotEmpty
import se.exuvo.aurora.utils.sRGBtoLinearRGB
import se.exuvo.aurora.utils.toLinearRGB
import java.nio.IntBuffer

// like sins of a solar empire left panel
class EmpireOverview : UIWindow() {
	
	val tmp = Vec2()
	val pool = DeferredIconPool()
	val deferredIcons = Bag<DeferredIcon>()
	
	lateinit var renderSystemGlobalData: RenderSystem.RenderGlobalData
	
	override fun set(ctx: Context, galaxy: Galaxy, galaxyGroupSystem: GroupSystem, imguiCamera: OrthographicCamera) {
		super.set(ctx, galaxy, galaxyGroupSystem, imguiCamera)
		
		renderSystemGlobalData = galaxy.systems[0].shadow.world.getSystem(RenderSystem::class.java).gData()
	}
	
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
							
							var windowCoveredByOtherWindow = false
							val thisWindowBB = currentWindow.outerRectClipped
							
							for (i in 1 until ctx.windows.size) { // 0 is fallback window
								val win = ctx.windows[i]
								if (win != currentWindow && win.isActiveAndVisible) {
									val bb = win.outerRectClipped
									
									if (bb.overlaps(thisWindowBB)) {
										windowCoveredByOtherWindow = true
										break
									}
								}
							}
							
							/*
									View modes
										relative: current system at top, rest sorted by distance from current
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
							
							val strategicIconsTexture = renderSystemGlobalData.strategicIconTexture
							
							Player.current.visibleSystems.forEachFast { system ->
								
								flags = TreeNodeFlag.DefaultOpen or TreeNodeFlag.SpanAvailWidth or TreeNodeFlag.NoTreePushOnOpen
								if (treeNodeEx(system.galacticEntityID.toString(), flags, system.getName())) {
									
									val shadow = system.shadow
									
									val nameMapper = shadow.nameMapper
									val colonyMapper = shadow.colonyMapper
									val shipMapper = shadow.shipMapper
									val empireMapper = shadow.empireMapper
									val strategicIconMapper = shadow.strategicIconMapper
									val tintMapper = shadow.tintMapper
									
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
										
										val empireC = empireMapper.get(entityID)
										val color = sRGBtoLinearRGB(if (empireC != null) empireC.empire.color else tintMapper.get(entityID)?.color ?: Color.WHITE)
										
										if (icon.baseTexture.texture == strategicIconsTexture) {
											
											if (!windowCoveredByOtherWindow) {
												// Batched rendering of strategic icons after imgui has been drawn
												deferredIcons.add(pool.obtain().set(icon, bb, color.toFloatBits()))
											
											} else {
												// Inefficient but renders in correct order
												window.drawList.addCallback({ _, drawCmd ->
													val deferredIcon = drawCmd.userCallbackData as DeferredIcon
													
													inlineDraw(deferredIcon)
													
													pool.free(deferredIcon)
												}, pool.obtain().set(icon, bb, color.toFloatBits()))
											}
											
										} else {
											window.drawList.addImage(icon.baseTexture.getTexture().textureObjectHandle, bb.min + 1, bb.max - 1, Vec2(icon.baseTexture.u, icon.baseTexture.v), Vec2(icon.baseTexture.u2, icon.baseTexture.v2), Vec4(color.r, color.g, color.g, color.a).u32)
										}
									}
									
									val systemColonies = empire.colonies.filter { ref -> ref.system == system }
									
									systemColonies.forEachIndexed { idx, colonyRef ->
										
										val entityID = colonyRef.entityID
										
										val name = nameMapper.get(entityID)?.name
										val colony = colonyMapper.get(entityID)
										val icon = strategicIconMapper.get(entityID)
										
										drawIcon(entityID, icon, isSelected(entityID, system))
										
										if (isItemHovered()) {
											tooltip {
												textUnformatted("c${system.sid}-$idx")
												textUnformatted("Population: ${colony.population}")
											}
										}
										
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
												tooltip {
													textUnformatted("$name - ${ship.hull}")
													
													val powerMapper = shadow.powerMapper
													val shield = shadow.shieldMapper.get(entityID)
													val armor = shadow.armorMapper.get(entityID)
													val partsHP = shadow.partsHPMapper.get(entityID)
													val cargoC = shadow.cargoMapper.get(entityID)
													
													group {
														if (shield != null) {
															ShipUI.shieldBar(ship, shield)
															currentWindow.dc.cursorPos.y += 2
														}
														ShipUI.armor(ship, armor, null)
													}
													sameLine()
													group {
														if (shield != null) {
															text("%s / %s", Units.capacityToString(shield.shieldHP), Units.capacityToString(ship.hull.maxShieldHP))
														}
														textUnformatted("ArmorHP ${armor.getTotalHP()} / ${ship.hull.maxArmorHP}")
														textUnformatted("PartHP ${partsHP.totalPartHP} / ${ship.hull.maxPartHP}")
													}
													
													val sortedParts = Bag<PartRef<Part>>(ship.hull.getPartRefs().size)
													for(partRef in ship.hull.getPartRefs()) {
														val hp = partsHP.getPartHP(partRef)
														val maxHP = partRef.part.maxHealth.toInt()
														if (hp < maxHP) {
															sortedParts.add(partRef)
														}
													}
													
													sortedParts.sort{ p1, p2 ->
														var diff = p2.part.maxHealth.toInt() - p1.part.maxHealth.toInt()
														
														if (diff == 0) {
															diff = partsHP.getPartHP(p2) - partsHP.getPartHP(p1)
														}
														
														diff
													}
													
													sortedParts.forEachFast{ partRef ->
														val hp = partsHP.getPartHP(partRef)
														val maxHP = partRef.part.maxHealth.toInt()
														
														if (hp == 0) {
															pushStyleColor(Col.Text, Vec4(1, 0, 0, 1))
														} else {
															pushStyleColor(Col.Text, Vec4(0.5, 0.5, 0, 1))
														}
														
														textUnformatted("$hp / $maxHP ${partRef.part}")
														popStyleColor()
													}
												}
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
	
	val glParam: IntBuffer = BufferUtils.createIntBuffer(1)
	
	fun inlineDraw(deferredIcon: DeferredIcon) {
		val projectionMatrix = imguiCamera.combined
		
		val texture = renderSystemGlobalData.strategicIconTexture
		val vertices = renderSystemGlobalData.strategicIconVertices
		val indices = renderSystemGlobalData.strategicIconIndices
		val iconShader = renderSystemGlobalData.strategicIconShader
		val mesh = renderSystemGlobalData.strategicIconMesh
		
		val lastProgram = Gdx.gl.glGetIntegerv(GL20.GL_CURRENT_PROGRAM, glParam).let{ glParam[0] }
		val lastArrayBuffer = Gdx.gl.glGetIntegerv(GL20.GL_ARRAY_BUFFER_BINDING, glParam).let { glParam[0] }
		val lastVertexArray = Gdx.gl.glGetIntegerv(GL30.GL_VERTEX_ARRAY_BINDING, glParam).let{ glParam[0] }
		val lastElementBuffer = Gdx.gl.glGetIntegerv(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING, glParam).let{ glParam[0] }
		
		iconShader.bind()
		iconShader.setUniformMatrix("u_projTrans", projectionMatrix);
		iconShader.setUniformi("u_texture", 14);
		
		texture.bind()
		
		var vertexIdx = 0
		var indiceIdx = 0

		// Offset center texCoords by diff in texture sizes. 7 15 3+1+3 7+1+7 7-3=4
		val centerXOffset = 4f / texture.width
		val centerYOffset = 4f / texture.height

		fun vertex(x: Float, y: Float, colorBits: Float, baseU: Float, baseV: Float, centerU: Float, centerV: Float) {
			vertices[vertexIdx++] = x;
			vertices[vertexIdx++] = y;
			vertices[vertexIdx++] = colorBits
			vertices[vertexIdx++] = baseU;
			vertices[vertexIdx++] = baseV;
			vertices[vertexIdx++] = centerU;
			vertices[vertexIdx++] = centerV;
		}

		val baseTex = deferredIcon.icon!!.baseTexture
		val centerTex = deferredIcon.icon!!.centerTexture
		val colorBits = deferredIcon.colorBits

		val minX = deferredIcon.bb.min.x + 1
		val maxX = deferredIcon.bb.max.x - 1
		val minY = deferredIcon.bb.min.y + 1
		val maxY = deferredIcon.bb.max.y - 1

		// Triangle 1
		indices[indiceIdx++] = 1.toShort()
		indices[indiceIdx++] = 0.toShort()
		indices[indiceIdx++] = 2.toShort()

		// Triangle 2
		indices[indiceIdx++] = 0.toShort()
		indices[indiceIdx++] = 3.toShort()
		indices[indiceIdx++] = 2.toShort()

		if (centerTex != null) {
			vertex(minX, minY, colorBits, baseTex.u, baseTex.v2, centerTex.u - centerXOffset, centerTex.v2 + centerYOffset);
			vertex(maxX, minY, colorBits, baseTex.u2, baseTex.v2, centerTex.u2 + centerXOffset, centerTex.v2 + centerYOffset);
			vertex(maxX, maxY, colorBits, baseTex.u2, baseTex.v, centerTex.u2 + centerXOffset, centerTex.v - centerYOffset);
			vertex(minX, maxY, colorBits, baseTex.u, baseTex.v, centerTex.u - centerXOffset, centerTex.v - centerYOffset);
		} else {
			vertex(minX, minY, colorBits, baseTex.u, baseTex.v2, 1f, 1f);
			vertex(maxX, minY, colorBits, baseTex.u2, baseTex.v2, 1f, 1f);
			vertex(maxX, maxY, colorBits, baseTex.u2, baseTex.v, 1f, 1f);
			vertex(minX, maxY, colorBits, baseTex.u, baseTex.v, 1f, 1f);
		}

		mesh.setVertices(vertices, 0, vertexIdx)
		mesh.setIndices(indices, 0, indiceIdx)
		mesh.render(iconShader, GL20.GL_TRIANGLES)
		
		Gdx.gl.glUseProgram(lastProgram);
		Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, lastArrayBuffer)
		Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, lastElementBuffer)
		Gdx.gl30.glBindVertexArray(lastVertexArray)
	}
	
	override fun postDraw() {
		if (deferredIcons.isEmpty) {
			return
		}
		
		val projectionMatrix = imguiCamera.combined
		
		val texture = renderSystemGlobalData.strategicIconTexture
		val vertices = renderSystemGlobalData.strategicIconVertices
		val indices = renderSystemGlobalData.strategicIconIndices
		val iconShader = renderSystemGlobalData.strategicIconShader
		val mesh = renderSystemGlobalData.strategicIconMesh
		
		iconShader.bind()
		iconShader.setUniformMatrix("u_projTrans", projectionMatrix);
		iconShader.setUniformi("u_texture", 14);
		
		Gdx.gl.glActiveTexture(GL20.GL_TEXTURE14)
		texture.bind()
		Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
		
		Gdx.gl.glEnable(GL30.GL_BLEND);
		Gdx.gl.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA);
		
		var vertexIdx = 0
		var indiceIdx = 0
		var stride = 0
		
		// Offset center texCoords by diff in texture sizes. 7 15 3+1+3 7+1+7 7-3=4
		val centerXOffset = 4f / texture.width
		val centerYOffset = 4f / texture.height
		
		fun vertex(x: Float, y: Float, colorBits: Float, baseU: Float, baseV: Float, centerU: Float, centerV: Float) {
			vertices[vertexIdx++] = x;
			vertices[vertexIdx++] = y;
			vertices[vertexIdx++] = colorBits
			vertices[vertexIdx++] = baseU;
			vertices[vertexIdx++] = baseV;
			vertices[vertexIdx++] = centerU;
			vertices[vertexIdx++] = centerV;
		}
		
		deferredIcons.forEachFast { deferredIcon ->
			
			val baseTex = deferredIcon.icon!!.baseTexture
			val centerTex = deferredIcon.icon!!.centerTexture
			val colorBits = deferredIcon.colorBits
			
			val minX = deferredIcon.bb.min.x + 1
			val maxX = deferredIcon.bb.max.x - 1
			val minY = deferredIcon.bb.min.y + 1
			val maxY = deferredIcon.bb.max.y - 1
			
			// Triangle 1
			indices[indiceIdx++] = (stride + 1).toShort()
			indices[indiceIdx++] = (stride + 0).toShort()
			indices[indiceIdx++] = (stride + 2).toShort()
			
			// Triangle 2
			indices[indiceIdx++] = (stride + 0).toShort()
			indices[indiceIdx++] = (stride + 3).toShort()
			indices[indiceIdx++] = (stride + 2).toShort()
			
			stride += 4
			
			if (centerTex != null) {
				vertex(minX, minY, colorBits, baseTex.u, baseTex.v2, centerTex.u - centerXOffset, centerTex.v2 + centerYOffset);
				vertex(maxX, minY, colorBits, baseTex.u2, baseTex.v2, centerTex.u2 + centerXOffset, centerTex.v2 + centerYOffset);
				vertex(maxX, maxY, colorBits, baseTex.u2, baseTex.v, centerTex.u2 + centerXOffset, centerTex.v - centerYOffset);
				vertex(minX, maxY, colorBits, baseTex.u, baseTex.v, centerTex.u - centerXOffset, centerTex.v - centerYOffset);
			} else {
				vertex(minX, minY, colorBits, baseTex.u, baseTex.v2, 1f, 1f);
				vertex(maxX, minY, colorBits, baseTex.u2, baseTex.v2, 1f, 1f);
				vertex(maxX, maxY, colorBits, baseTex.u2, baseTex.v, 1f, 1f);
				vertex(minX, maxY, colorBits, baseTex.u, baseTex.v, 1f, 1f);
			}
			
			if (indiceIdx >= mesh.maxIndices) {
				mesh.setVertices(vertices, 0, vertexIdx)
				mesh.setIndices(indices, 0, indiceIdx)
				mesh.render(iconShader, GL20.GL_TRIANGLES)
				
				vertexIdx = 0
				indiceIdx = 0
				stride = 0
			}
			
			pool.free(deferredIcon)
		}
		
		deferredIcons.clear()
		
		if (indiceIdx > 0) {
			mesh.setVertices(vertices, 0, vertexIdx)
			mesh.setIndices(indices, 0, indiceIdx)
			mesh.render(iconShader, GL20.GL_TRIANGLES)
		}
		
		Gdx.gl.glDisable(GL30.GL_BLEND);
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
	
	data class DeferredIcon(var icon: StrategicIconComponent? = null, var bb : Rect = Rect(), var colorBits: Float = 0f) {
		fun set(icon: StrategicIconComponent, bb: Rect, colorBits: Float): DeferredIcon {
			this.icon = icon;
			this.bb.put(bb)
			this.colorBits = colorBits
			return this
		}
	}
	
	class DeferredIconPool : ObjectPool<DeferredIcon>(DeferredIcon::class.java) {
		override fun instantiateObject(): DeferredIcon? {
			return DeferredIcon()
		}
	}
}
