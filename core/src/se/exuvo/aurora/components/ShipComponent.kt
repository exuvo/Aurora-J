package se.exuvo.aurora.components

import com.badlogic.ashley.core.Component
import se.exuvo.aurora.shipcomponents.ShipClass

data class ShipComponent(var shipClass: ShipClass, val constructionDay: Int, var commissionDay: Int? = null) : Component {
	var armor = Array<Array<Boolean>>(shipClass.getWidth(), { Array<Boolean>(shipClass.armorLayers, { true }) })
}
