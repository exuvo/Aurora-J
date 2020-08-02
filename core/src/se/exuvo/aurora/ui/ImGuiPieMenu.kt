package imgui.imgui.widgets

import glm_.glm
import glm_.vec2.Vec2
import glm_.vec2.Vec2i
import glm_.vec4.Vec4
import imgui.COL32
import imgui.Col
import imgui.Cond
import imgui.ImGui.beginPopup
import imgui.ImGui.calcTextSize
import imgui.ImGui.closeCurrentPopup
import imgui.ImGui.end
import imgui.ImGui.endPopup
import imgui.ImGui.getColorU32
import imgui.ImGui.io
import imgui.ImGui.isMouseReleased
import imgui.ImGui.isPopupOpen
import imgui.ImGui.popStyleColor
import imgui.ImGui.popStyleVar
import imgui.ImGui.pushStyleColor
import imgui.ImGui.pushStyleVar
import imgui.ImGui.setNextWindowPos
import imgui.MouseButton
import imgui.StyleVar
import imgui.api.g
import imgui.classes.DrawList
import imgui.classes.Style
import imgui.internal.classes.Rect

// From https://github.com/ocornut/imgui/issues/434#issuecomment-351743369

const val c_iRadiusEmpty = 30
const val c_iRadiusMin = 30
const val c_iMinItemCount = 3
const val c_iMinItemCountPerLevel = 3

data class PieMenu(
	var m_iCurrentIndex: Int = 0,
	var m_fMaxItemSqrDiameter: Float = 0f,
	var m_fLastMaxItemSqrDiameter: Float = 0f,
	var m_iHoveredItem: Int = 0,
	var m_iLastHoveredItem: Int = 0,
	var m_iClickedItem: Int = 0,
	var m_oItemIsSubMenu: MutableList<Boolean> = ArrayList<Boolean>(),
	var m_oItemNames: MutableList<String> = ArrayList<String>(),
	var m_oItemSizes: MutableList<Vec2> = ArrayList<Vec2>()
)

data class PieMenuContext(
	var m_oPieMenuStack: MutableList<PieMenu> = ArrayList<PieMenu>(),
	var m_iCurrentIndex: Int = -1,
	var m_iMaxIndex: Int = 0,
	var m_iLastFrame: Int = 0,
	var m_oCenter: Vec2 = Vec2(),
	var m_iMouseButton: MouseButton = MouseButton.None,
	var m_bClose: Boolean = false
)

val s_oPieMenuContext: PieMenuContext = PieMenuContext()

fun beginPieMenuEx(): Unit {
	++s_oPieMenuContext.m_iCurrentIndex
	++s_oPieMenuContext.m_iMaxIndex
	
	if (s_oPieMenuContext.m_oPieMenuStack.size <= s_oPieMenuContext.m_iCurrentIndex) {
		s_oPieMenuContext.m_oPieMenuStack.add(PieMenu())
	}
	
	val oPieMenu: PieMenu = s_oPieMenuContext.m_oPieMenuStack[s_oPieMenuContext.m_iCurrentIndex]
	oPieMenu.m_iCurrentIndex = 0
	oPieMenu.m_fMaxItemSqrDiameter = 0f
	
	if (!isMouseReleased(s_oPieMenuContext.m_iMouseButton)) {
		oPieMenu.m_iHoveredItem = -1
	}
	
	if (s_oPieMenuContext.m_iCurrentIndex > 0) {
		oPieMenu.m_fMaxItemSqrDiameter = s_oPieMenuContext.m_oPieMenuStack[s_oPieMenuContext.m_iCurrentIndex - 1].m_fMaxItemSqrDiameter
	}
}

fun endPieMenuEx(): Unit {
	assert(s_oPieMenuContext.m_iCurrentIndex >= 0)
	
	--s_oPieMenuContext.m_iCurrentIndex
}

fun beginPiePopup(pName: String, iMouseButton: MouseButton): Boolean {
	if (isPopupOpen(pName)) {
		pushStyleColor(Col.WindowBg, Vec4(0, 0, 0, 0))
		pushStyleColor(Col.Border, Vec4(0, 0, 0, 0))
		pushStyleVar(StyleVar.WindowRounding, 0.0f)
		pushStyleVar(StyleVar.Alpha, 1.0f)
		
		s_oPieMenuContext.m_iMouseButton = iMouseButton
		s_oPieMenuContext.m_bClose = false
		
		setNextWindowPos(Vec2(-100f, -100f ), Cond.Appearing )
		
		if (beginPopup(pName)) {
			val iCurrentFrame = g.frameCount
			if (s_oPieMenuContext.m_iLastFrame < (iCurrentFrame - 1)) {
				s_oPieMenuContext.m_oCenter = io.mousePos
			}
			s_oPieMenuContext.m_iLastFrame = iCurrentFrame
			
			s_oPieMenuContext.m_iMaxIndex = -1
			beginPieMenuEx()
			
			return true
			
		} else {
			end()
			popStyleColor(2)
			popStyleVar(2)
		}
	}
	return false
}

fun endPiePopup(): Unit {
	endPieMenuEx()
	
	val oStyle: Style = g.style
	
	val pDrawList: DrawList = g.currentWindow!!.drawList
	pDrawList.pushClipRectFullScreen()
	
	val oMousePos: Vec2 = io.mousePos
	val oDragDelta = Vec2(oMousePos.x - s_oPieMenuContext.m_oCenter.x, oMousePos.y - s_oPieMenuContext.m_oCenter.y)
	val fDragDistSqr: Float = oDragDelta.x*oDragDelta.x + oDragDelta.y*oDragDelta.y
	
	var fCurrentRadius: Float = c_iRadiusEmpty.toFloat()
	
	val oArea = Rect(s_oPieMenuContext.m_oCenter, s_oPieMenuContext.m_oCenter )
	
	var bItemHovered = false
	
	val c_fDefaultRotate: Float = -glm.PIf / 2f
	var fLastRotate = c_fDefaultRotate
	
	for (iIndex in 0..s_oPieMenuContext.m_iMaxIndex) {
		val oPieMenu: PieMenu = s_oPieMenuContext.m_oPieMenuStack[iIndex]
		
		val fMenuHeight: Float = glm.sqrt(oPieMenu.m_fMaxItemSqrDiameter)
		
		val fMinRadius: Float = fCurrentRadius
		val fMaxRadius: Float = fMinRadius + (fMenuHeight * oPieMenu.m_iCurrentIndex) / ( 2f )
		
		val item_arc_span: Float = 2 * glm.PIf / glm.max(c_iMinItemCount + c_iMinItemCountPerLevel * iIndex, oPieMenu.m_iCurrentIndex)
		var drag_angle: Float = glm.atan(oDragDelta.y, oDragDelta.x)
		
		val fRotate: Float = fLastRotate - item_arc_span * ( oPieMenu.m_iCurrentIndex - 1f ) / 2f
		var item_hovered: Int = -1
		
		for (item_n in 0 until oPieMenu.m_iCurrentIndex) {
			val item_label: String = oPieMenu.m_oItemNames[ item_n ]
//			val inner_spacing: Float = oStyle.itemInnerSpacing.x / fMinRadius / 2;
			val fMinInnerSpacing: Float = oStyle.itemInnerSpacing.x / ( fMinRadius * 2f )
			val fMaxInnerSpacing: Float = oStyle.itemInnerSpacing.x / ( fMaxRadius * 2f )
			val item_inner_ang_min: Float = item_arc_span * ( item_n - 0.5f + fMinInnerSpacing ) + fRotate
			val item_inner_ang_max: Float = item_arc_span * ( item_n + 0.5f - fMinInnerSpacing ) + fRotate
			val item_outer_ang_min: Float = item_arc_span * ( item_n - 0.5f + fMaxInnerSpacing ) + fRotate
			val item_outer_ang_max: Float = item_arc_span * ( item_n + 0.5f - fMaxInnerSpacing ) + fRotate
			
			var hovered = false
			
			if (fDragDistSqr >= fMinRadius * fMinRadius && fDragDistSqr < fMaxRadius * fMaxRadius) {
				
				while((drag_angle - item_inner_ang_min) < 0f) {
					drag_angle += 2f * glm.PIf
				}
				
				while((drag_angle - item_inner_ang_min) > 2f * glm.PIf) {
					drag_angle -= 2f * glm.PIf
				}

				if (drag_angle >= item_inner_ang_min && drag_angle < item_inner_ang_max) {
					hovered = true
					bItemHovered = !oPieMenu.m_oItemIsSubMenu[item_n]
				}
			}

			val arc_segments: Int = 1 + (32 * item_arc_span / (2 * glm.PIf)).toInt()
			
			var iColor: Int
//			iColor = if (hovered) COL32(100, 100, 150, 255) else COL32(70, 70, 70, 255);
//			iColor = getColorU32(if (hovered) Col.HeaderHovered else Col.FrameBg);
			iColor = getColorU32(if (hovered) Col.Button else Col.ButtonHovered)
			//iColor |= 0xFF000000;

			val fAngleStepInner: Float = (item_inner_ang_max - item_inner_ang_min) / arc_segments
			val fAngleStepOuter: Float = ( item_outer_ang_max - item_outer_ang_min ) / arc_segments
			pDrawList.primReserve(arc_segments * 6, (arc_segments + 1) * 2)
			
			for (iSeg in 0..arc_segments) {
				val fCosInner: Float = glm.cos(item_inner_ang_min + fAngleStepInner * iSeg)
				val fSinInner: Float = glm.sin(item_inner_ang_min + fAngleStepInner * iSeg)
				val fCosOuter: Float = glm.cos(item_outer_ang_min + fAngleStepOuter * iSeg)
				val fSinOuter: Float = glm.sin(item_outer_ang_min + fAngleStepOuter * iSeg)
				
				if (iSeg < arc_segments) {
					pDrawList.primWriteIdx(pDrawList._vtxCurrentIdx + 0)
					pDrawList.primWriteIdx(pDrawList._vtxCurrentIdx + 2)
					pDrawList.primWriteIdx(pDrawList._vtxCurrentIdx + 1)
					pDrawList.primWriteIdx(pDrawList._vtxCurrentIdx + 3)
					pDrawList.primWriteIdx(pDrawList._vtxCurrentIdx + 2)
					pDrawList.primWriteIdx(pDrawList._vtxCurrentIdx + 1)
				}
				pDrawList.primWriteVtx(Vec2(s_oPieMenuContext.m_oCenter.x + fCosInner * (fMinRadius + oStyle.itemInnerSpacing.x), s_oPieMenuContext.m_oCenter.y + fSinInner * (fMinRadius + oStyle.itemInnerSpacing.x)), Vec2(0f, 0f), iColor)
				pDrawList.primWriteVtx(Vec2(s_oPieMenuContext.m_oCenter.x + fCosOuter * (fMaxRadius - oStyle.itemInnerSpacing.x), s_oPieMenuContext.m_oCenter.y + fSinOuter * (fMaxRadius - oStyle.itemInnerSpacing.x)), Vec2(0f, 0f), iColor)
			}

			val fRadCenter: Float = (item_arc_span * item_n) + fRotate
			val oOuterCenter = Vec2(s_oPieMenuContext.m_oCenter.x + glm.cos(fRadCenter) * fMaxRadius, s_oPieMenuContext.m_oCenter.y + glm.sin( fRadCenter ) * fMaxRadius )
			oArea.add(oOuterCenter)
			
			if (oPieMenu.m_oItemIsSubMenu[item_n]) {
				val oTrianglePos = Array<Vec2>(3, { Vec2() })
				
				val fRadLeft: Float = fRadCenter - 5f / fMaxRadius
				val fRadRight: Float = fRadCenter + 5f / fMaxRadius
				
				oTrianglePos[0].x = s_oPieMenuContext.m_oCenter.x + glm.cos(fRadCenter) * (fMaxRadius - 5f)
				oTrianglePos[0].y = s_oPieMenuContext.m_oCenter.y + glm.sin(fRadCenter) * (fMaxRadius - 5f)
				oTrianglePos[1].x = s_oPieMenuContext.m_oCenter.x + glm.cos(fRadLeft) * (fMaxRadius - 10f)
				oTrianglePos[1].y = s_oPieMenuContext.m_oCenter.y + glm.sin(fRadLeft) * (fMaxRadius - 10f)
				oTrianglePos[2].x = s_oPieMenuContext.m_oCenter.x + glm.cos(fRadRight) * (fMaxRadius - 10f)
				oTrianglePos[2].y = s_oPieMenuContext.m_oCenter.y + glm.sin(fRadRight) * (fMaxRadius - 10f)
				
				pDrawList.addTriangleFilled(oTrianglePos[0], oTrianglePos[1], oTrianglePos[2], COL32(255, 255, 255, 255))
			}

			val text_size: Vec2 = oPieMenu.m_oItemSizes[item_n]
			val text_pos = Vec2(
				s_oPieMenuContext.m_oCenter.x + glm.cos((item_inner_ang_min + item_inner_ang_max) * 0.5f) * (fMinRadius + fMaxRadius) * 0.5f - text_size.x * 0.5f,
				s_oPieMenuContext.m_oCenter.y + glm.sin((item_inner_ang_min + item_inner_ang_max) * 0.5f) * (fMinRadius + fMaxRadius) * 0.5f - text_size.y * 0.5f)
			pDrawList.addText(text_pos, COL32(255, 255, 255, 255), item_label)
			
			if (hovered) {
				item_hovered = item_n
			}
		}

		fCurrentRadius = fMaxRadius
		
		oPieMenu.m_fLastMaxItemSqrDiameter = oPieMenu.m_fMaxItemSqrDiameter
		
		oPieMenu.m_iHoveredItem = item_hovered
		
		if (fDragDistSqr >= fMaxRadius * fMaxRadius) {
			item_hovered = oPieMenu.m_iLastHoveredItem
		}

		oPieMenu.m_iLastHoveredItem = item_hovered
		
		fLastRotate = item_arc_span * oPieMenu.m_iLastHoveredItem + fRotate
		if (item_hovered == -1 || !oPieMenu.m_oItemIsSubMenu[item_hovered]) {
			break
		}
	}

	pDrawList.popClipRect()
	
	if (oArea.min.x < 0f) {
		s_oPieMenuContext.m_oCenter.x = ( s_oPieMenuContext.m_oCenter.x - oArea.min.x )
	}
	
	if (oArea.min.y < 0f) {
		s_oPieMenuContext.m_oCenter.y = ( s_oPieMenuContext.m_oCenter.y - oArea.min.y )
	}

	val oDisplaySize: Vec2i = io.displaySize
	
	if (oArea.max.x > oDisplaySize.x) {
		s_oPieMenuContext.m_oCenter.x = ( s_oPieMenuContext.m_oCenter.x - oArea.max.x ) + oDisplaySize.x
	}
	
	if (oArea.max.y > oDisplaySize.y) {
		s_oPieMenuContext.m_oCenter.y = ( s_oPieMenuContext.m_oCenter.y - oArea.max.y ) + oDisplaySize.y
	}

	if (s_oPieMenuContext.m_bClose || (!bItemHovered && isMouseReleased(s_oPieMenuContext.m_iMouseButton))) {
		closeCurrentPopup()
	}

	endPopup()
	popStyleColor(2)
	popStyleVar(2)
}

fun beginPieMenu(pName: String, bEnabled: Boolean = true): Boolean
{
	val oPieMenu: PieMenu = s_oPieMenuContext.m_oPieMenuStack[s_oPieMenuContext.m_iCurrentIndex]
	
	val oTextSize: Vec2 = calcTextSize(pName, true, -1f)
	
	if (oPieMenu.m_oItemSizes.size <= oPieMenu.m_iCurrentIndex) {
		oPieMenu.m_oItemSizes.add(oTextSize)
	} else {
		oPieMenu.m_oItemSizes[oPieMenu.m_iCurrentIndex] = oTextSize
	}

	val fSqrDiameter: Float = oTextSize.x * oTextSize.x + oTextSize.y * oTextSize.y
	
	if (fSqrDiameter > oPieMenu.m_fMaxItemSqrDiameter) {
		oPieMenu.m_fMaxItemSqrDiameter = fSqrDiameter
	}

	if (oPieMenu.m_oItemIsSubMenu.size <= oPieMenu.m_iCurrentIndex) {
		oPieMenu.m_oItemIsSubMenu.add(true)
		oPieMenu.m_oItemNames.add(pName)
	} else {
		oPieMenu.m_oItemIsSubMenu[oPieMenu.m_iCurrentIndex] = true
		oPieMenu.m_oItemNames[oPieMenu.m_iCurrentIndex] = pName
	}

	if (oPieMenu.m_iLastHoveredItem == oPieMenu.m_iCurrentIndex) {
		++oPieMenu.m_iCurrentIndex
		
		beginPieMenuEx()
		return true
	}
	++oPieMenu.m_iCurrentIndex
	
	return false
}

fun endPieMenu(): Unit {
	assert(s_oPieMenuContext.m_iCurrentIndex >= 0)
	--s_oPieMenuContext.m_iCurrentIndex
}

fun pieMenuItem(pName: String, bEnabled: Boolean = true): Boolean {
	val oPieMenu: PieMenu = s_oPieMenuContext.m_oPieMenuStack[s_oPieMenuContext.m_iCurrentIndex]
	
	val oTextSize: Vec2 = calcTextSize(pName, true, -1f)
	
	if (oPieMenu.m_oItemSizes.size <= oPieMenu.m_iCurrentIndex) {
		oPieMenu.m_oItemSizes.add(oTextSize)
	} else {
		oPieMenu.m_oItemSizes[oPieMenu.m_iCurrentIndex] = oTextSize
	}

	val fSqrDiameter: Float = oTextSize.x * oTextSize.x + oTextSize.y * oTextSize.y
	
	if (fSqrDiameter > oPieMenu.m_fMaxItemSqrDiameter) {
		oPieMenu.m_fMaxItemSqrDiameter = fSqrDiameter
	}

	if (oPieMenu.m_oItemIsSubMenu.size <= oPieMenu.m_iCurrentIndex) {
		oPieMenu.m_oItemIsSubMenu.add(false)
		oPieMenu.m_oItemNames.add(pName)
	} else {
		oPieMenu.m_oItemIsSubMenu[oPieMenu.m_iCurrentIndex] = false
		oPieMenu.m_oItemNames[oPieMenu.m_iCurrentIndex] = pName
	}

	val bActive: Boolean = oPieMenu.m_iCurrentIndex == oPieMenu.m_iHoveredItem
	++oPieMenu.m_iCurrentIndex
	
	if (bActive) {
		s_oPieMenuContext.m_bClose = true
	}
	
	return bActive
}