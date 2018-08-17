package se.exuvo.aurora.planetarysystems.components

import com.badlogic.ashley.core.Component
import se.exuvo.aurora.galactic.CargoType
import se.exuvo.aurora.galactic.ContainerPart
import se.exuvo.aurora.galactic.Part
import se.exuvo.aurora.galactic.Resource
import se.exuvo.aurora.galactic.ShipClass
import java.security.InvalidParameterException
import java.util.ArrayList
import java.lang.IllegalArgumentException
import se.exuvo.aurora.galactic.PoweredPart
import se.exuvo.aurora.galactic.ChargedPart
import se.exuvo.aurora.galactic.AmmunitionPart
import com.badlogic.gdx.utils.Queue
import se.exuvo.aurora.galactic.ReloadablePart
import se.exuvo.aurora.galactic.FueledPart
import se.exuvo.aurora.galactic.PoweringPart
import kotlin.reflect.KClass
import se.exuvo.aurora.galactic.PassiveSensor
import com.badlogic.ashley.core.Entity
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.galactic.PartRef
import kotlin.Suppress
import se.exuvo.aurora.galactic.MunitionClass

class MunitionComponent(var munitionClass: MunitionClass, val constructionTime: Long) : Component {
	var commissionDay: Int? = null
	val armor = Array<Int>(munitionClass.getSurfaceArea(), { munitionClass.armorLayers })
	val partEnabled = Array<Boolean>(munitionClass.getParts().size, { true })
	val partState = Array<PartState>(munitionClass.getParts().size, { PartState() })
	var mass: Long = 0

	init {
		
		partState.forEachIndexed { partIndex, state ->
			val partRef = munitionClass[partIndex]
			
			if (partRef.part is PoweringPart) {
				state.put(PoweringPartState())
			}
			
			if (partRef.part is PoweredPart) {
				state.put(PoweredPartState())
			}
			
			if (partRef.part is ChargedPart) {
				state.put(ChargedPartState())
			}
			
			if (partRef.part is PassiveSensor) {
				state.put(PassiveSensorState())
			}
			
			if (partRef.part is FueledPart) {
				state.put(FueledPartState())
			}
		}
	}
	
	fun getPartState(partRef: PartRef<out Part>): PartState {
		return partState[partRef.index]
	}
	
	fun isPartEnabled(partRef: PartRef<out Part>): Boolean {
		return partEnabled[partRef.index]
	}
	
	fun setPartEnabled(partRef: PartRef<out Part>, enabled: Boolean) {
		partEnabled[partRef.index] = enabled
	}
}
