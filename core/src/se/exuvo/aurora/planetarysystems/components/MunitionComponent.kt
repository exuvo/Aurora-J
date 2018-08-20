package se.exuvo.aurora.planetarysystems.components

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
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.galactic.PartRef
import kotlin.Suppress
import se.exuvo.aurora.galactic.MunitionClass
import com.artemis.Component

class MunitionComponent() : Component() {
	lateinit var munitionClass: MunitionClass
	var constructionTime: Long = -1
	var commissionDay: Int? = null
	lateinit var armor: Array<Int>
	lateinit var partEnabled: Array<Boolean>
	lateinit var partState: Array<PartState>
	var mass: Long = 0

	fun set(munitionClass: MunitionClass,
					constructionTime: Long
	) {
		this.munitionClass = munitionClass
		this.constructionTime = constructionTime
		
		armor = Array<Int>(munitionClass.getSurfaceArea(), { munitionClass.armorLayers })
		partEnabled = Array<Boolean>(munitionClass.getParts().size, { true })
		partState = Array<PartState>(munitionClass.getParts().size, { PartState() })
		
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
