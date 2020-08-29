package se.exuvo.aurora.utils.imgui

import glm_.vec2.Vec2
import glm_.vec4.Vec4
import imgui.Col
import imgui.ImGui
import imgui.internal.classes.Rect
import imgui.max
import imgui.u32
import se.exuvo.aurora.starsystems.components.ArmorComponent
import se.exuvo.aurora.starsystems.components.ShieldComponent
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.utils.toLinearRGB

fun rightAlignedColumnText(string: String) {
	with (ImGui) {
		ImGui.cursorPosX = ImGui.cursorPosX + ImGui.getColumnWidth() - calcTextSize(string).x - ImGui.scrollX - 2 * ImGui.style.itemSpacing.x
		text(string)
	}
}

fun sameLineRightAlignedColumnText(string: String) {
	with (ImGui) {
		sameLine(ImGui.cursorPosX + ImGui.getColumnWidth() - calcTextSize(string).x - ImGui.scrollX - 2 * ImGui.style.itemSpacing.x)
		text(string)
	}
}

fun lerpColorSRGB(out: Vec4, inA: Vec4, inB: Vec4, current: Float, max: Float): Vec4 {
	return lerpColorLinear(out, inA.toLinearRGB(), inB.toLinearRGB(), current, max)
}

fun lerpColorLinear(out: Vec4, inA: Vec4, inB: Vec4, current: Float, max: Float): Vec4 {
	
	val inv = max - current
	
	out.r = (inA.r * inv + inB.r * current) / max
	out.g = (inA.g * inv + inB.g * current) / max
	out.b = (inA.b * inv + inB.b * current) / max
	
	return out
}

object ShipUI {
	
	//@formatter:off
	val fullArmorColor =  Vec4(0f, 1f, 0f, 1f).toLinearRGB()
	val emptyArmorColor = Vec4(1f, 0f, 0f, 1f).toLinearRGB()
	val tmpColor =        Vec4(0f, 0f, 0f, 1f).toLinearRGB()
	
	val filledShieldColor =      Vec4(0.3f, 0.3f, 1.0f, 1f).toLinearRGB()
	val depletedShieldColor =    Vec4(0.3f, 0.3f, 0.6f, 1f).toLinearRGB()
	val selDepletedShieldColor = Vec4(0.3f, 0.3f, 0.8f, 1f).toLinearRGB()
	//@formatter:on
	
	private fun armorBlock(hp: Float, maxHP: Float): Boolean {
		with (ImGui) {
			val window = currentWindow
			val id = window.getID("armor")
			val pos = Vec2(window.dc.cursorPos)
			
			val size = Vec2(5f, 5f)
			val bb = Rect(pos, pos + size)
			itemSize(size)
			if (!itemAdd(bb, id)) return false
			
			val flags = 0
			val (pressed, hovered, held) = buttonBehavior(bb, id, flags)
			
			//Render
			val col = if (hovered) Col.ButtonHovered.u32 else lerpColorLinear(tmpColor, emptyArmorColor, fullArmorColor, hp, maxHP).u32
			renderNavHighlight(bb, id)
			renderFrame(bb.min, bb.max, col, true, style.frameRounding)
			
			return hovered
		}
	}
	
	fun armor(ship: ShipComponent, armor: ArmorComponent, tooltipCallback: ((x: Int, y: Int, armorHP: Int, maxArmorHP: Int) -> Unit) ?) {
		with (ImGui) {
			for (y in ship.hull.armorLayers-1 downTo 0) { // layer
				for (x in 0 until ship.hull.getArmorWidth()) {
					with (currentWindow) {
						if (x == 0) {
							dc.cursorPos.y = dc.cursorPos.y + 1 - style.itemSpacing.y
						} else {
							sameLine(0f, 1f)
						}
					}
					
					pushID(y * 31 + x)
					
					val armorHP = armor[y][x].toInt()
					val maxArmorHP = ship.hull.armorBlockHP[y].toInt()
					
					if (armorBlock(armorHP.toFloat(), maxArmorHP.toFloat()) && tooltipCallback != null) {
						tooltipCallback(x, y, armorHP, maxArmorHP)
					}
					
					popID()
				}
			}
		}
	}
	
	fun shieldBar(ship: ShipComponent, shield: ShieldComponent): Boolean {
		return shieldBar(ship, shield.shieldHP, ship.hull.maxShieldHP)
	}
	
	fun shieldBar(ship: ShipComponent, hp: Long, maxHP: Long): Boolean {
		with (ImGui) {
			val window = currentWindow
			
			val id = window.getID("shield")
			val pos = Vec2(window.dc.cursorPos)
			
			val size = Vec2(5 * ship.hull.getArmorWidth() + 1 * (ship.hull.getArmorWidth() - 1), 5f)
			val filledSize = Vec2(size.x * (hp / maxHP.toDouble()), 5f)
			val bb = Rect(pos, pos + size)
			val bbFilled = Rect(pos, pos + filledSize)
			itemSize(size)
			if (!itemAdd(bb, id)) return false
			
			val flags = 0
			val (pressed, hovered, held) = buttonBehavior(bb, id, flags)
			
			//Render
			renderNavHighlight(bb, id)
			
			var col = if (hovered) selDepletedShieldColor.u32 else depletedShieldColor.u32
			renderFrame(bb.min, bb.max, col, true, style.frameRounding)
			
			col = if (hovered) Col.ButtonHovered.u32 else filledShieldColor.u32
			window.drawList.addRectFilled(bbFilled.min, bbFilled.max, col, style.frameRounding)
			
			return hovered
		}
	}
}