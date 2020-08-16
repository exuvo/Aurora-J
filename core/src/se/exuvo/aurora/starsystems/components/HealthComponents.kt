package se.exuvo.aurora.starsystems.components

import com.artemis.Component
import com.artemis.utils.Bag
import se.exuvo.aurora.galactic.AdvancedMunitionHull
import se.exuvo.aurora.galactic.Part
import se.exuvo.aurora.galactic.PartRef
import se.exuvo.aurora.galactic.ShipHull
import se.exuvo.aurora.utils.forEachFast
import uk.co.omegaprime.btreemap.LongObjectBTreeMap
import java.security.InvalidParameterException

class ShieldComponent() : Component(), CloneableComponent<ShieldComponent> {
	var shieldHP = 0L
	
	fun set(hull: ShipHull, partStates: PartStatesComponent): Long {
		if (hull.shields.isEmpty()) {
			throw InvalidParameterException("no shields on this hull $hull")
		}
		
		var shieldHP = 0L
		
		hull.shields.forEachFast { partRef ->
			shieldHP += partStates[partRef][ChargedPartState::class].charge
		}
		
		return shieldHP
	}
	
	override fun copy(tc: ShieldComponent) {
		tc.shieldHP = shieldHP
	}
}

class ArmorComponent() : Component(), CloneableComponent<ArmorComponent> {
	var armor: Array<ByteArray>? = null // [layer][armor column] = hp
	
	fun set(hull: ShipHull) {
		armor = Array<ByteArray>(hull.armorLayers, { layer -> ByteArray(hull.getArmorWidth(), { hull.armorBlockHP[layer] }) }) // 1 armor block per m2
	}
	
	fun set(hull: AdvancedMunitionHull) {
		val armorWidth = hull.getSurfaceArea() / 1000000
		armor = Array<ByteArray>(hull.armorLayers, { _ -> ByteArray(armorWidth, { hull.armorBlockHP }) }) // 1 armor block per m2
	}
	
	override fun copy(tc: ArmorComponent) {
		val armor = armor!!
		val tcArmor = tc.armor
		
		if (tcArmor == null || tcArmor.size != armor.size || tcArmor[0].size != armor[0].size) {
			tc.armor = Array<ByteArray>(armor.size, {layer -> ByteArray(armor[0].size, { column -> armor[layer][column] }) })
			
		} else {
			armor.forEachIndexed { layerIndex, layer ->
				layer.forEachIndexed { columnIndex, hp ->
					tcArmor[layerIndex][columnIndex] = hp
				}
			}
		}
	}
	
	operator fun get(index: Int): ByteArray = armor!![index]
}

class PartsHPComponent() : Component(), CloneableComponent<PartsHPComponent> {
	var totalPartHP: Int = -1
	lateinit var partHP: ByteArray
	var damageablePartsMaxVolume = 0L
	val damageableParts = LongObjectBTreeMap.create<Bag<PartRef<Part>>>()
	
	fun set(hull: ShipHull) {
		partHP = ByteArray(hull.getParts().size, { hull[it].part.maxHealth })
		totalPartHP = partHP.size * 128 + partHP.sum()
		
		hull.getPartRefs().forEachFast { partRef ->
			addDamageablePart(partRef)
		}
	}
	
	override fun copy(tc: PartsHPComponent) {
		if (tc.totalPartHP == -1 || tc.partHP.size != partHP.size) {
			tc.partHP = ByteArray(partHP.size, { partHP[it] })
			
		} else {
			partHP.forEachIndexed { index, hp ->
				tc.partHP[index] = hp
			}
		}
		
		tc.totalPartHP = totalPartHP
		tc.damageablePartsMaxVolume = damageablePartsMaxVolume
	}
	
	
	fun getPartHP(partRef: PartRef<out Part>): Int {
		return 128 + partHP[partRef.index]
	}
	
	@Suppress("NAME_SHADOWING")
	fun setPartHP(partRef: PartRef<Part>, health: Int, damageablePartsEntry: Map.Entry<Long, Bag<PartRef<Part>>>? = null) {
		if (health < 0 || health > (128 + partRef.part.maxHealth)) {
			throw IllegalArgumentException()
		}
		
		val oldHP = 128 + partHP[partRef.index]
		
		if (oldHP == 0 && health > 0) {
			
			addDamageablePart(partRef)
			
		} else if (oldHP > 0 && health == 0) {
			
			var damageablePartsEntry = damageablePartsEntry
			val volume = partRef.part.volume
			
			if (damageablePartsEntry == null) {
				damageablePartsEntry = getDamageablePartsEntry(volume)!!
			}
			
			damageableParts.remove(damageablePartsEntry.key)
			
			if (damageablePartsEntry.value.size() == 1) {

//				println("removing $volume, single")
				
				if (damageableParts.size == 0) {
					damageablePartsMaxVolume = 0
					
				} else if (volume == damageablePartsMaxVolume) {
					damageablePartsMaxVolume = damageableParts.lastKeyLong()
				}
				
			} else {

//				println("removing $volume, multi")
				
				damageablePartsEntry.value.remove(partRef)
				
				val newVolume = damageablePartsEntry.key - volume
				damageableParts.put(newVolume, damageablePartsEntry.value)
				
				if (damageablePartsMaxVolume >= newVolume) {
					damageablePartsMaxVolume = damageableParts.lastKeyLong()
				}
			}
		}
		
		totalPartHP += health - oldHP
		
		partHP[partRef.index] = (health - 128).toByte()
	}
	
	private fun getDamageablePartsEntry(volume: Long): Map.Entry<Long, Bag<PartRef<Part>>>? {
		if (damageableParts.size > 0) {
			var entry = damageableParts.ceilingEntry(volume)
			
			while(entry != null) {

//				println("get $volume, entry ${entry.key} = ${entry.value[0].part.volume} * ${entry.value.size()}")
				
				if (entry.value[0].part.volume == volume) {
					return entry
				}
				
				entry = damageableParts.higherEntry(entry.key)
			}
		}
		
		return null
	}
	
	private fun addDamageablePart(partRef: PartRef<Part>) {
		val part = partRef.part
		val volume = part.volume
		
		var list: Bag<PartRef<Part>>? = null
		
		if (damageableParts.size > 0) {
			val entry = getDamageablePartsEntry(volume)
			
			if (entry != null) {
				list = entry.value
				
				damageableParts.remove(entry.key)
				val newVolume = entry.key + volume
				damageableParts.put(newVolume, list)
				
				if (newVolume > damageablePartsMaxVolume) {
					damageablePartsMaxVolume = newVolume
				}

//				println("adding $volume, appending")
			}
		}
		
		if (list == null) {
			list = Bag<PartRef<Part>>(4)
			damageableParts.put(volume, list)
			
			if (volume > damageablePartsMaxVolume) {
				damageablePartsMaxVolume = volume
			}

//			println("adding $volume, new")
		}
		
		list.add(partRef)
	}
}

class HPComponent() : Component(), CloneableComponent<HPComponent> {
	var health: Short = -1
	
	fun set(health: Short) {
		this.health = health
	}
	
	fun set(hull: AdvancedMunitionHull) {
		var sum = 0
		hull.getParts().forEachFast { part ->
			sum += 128 + part.maxHealth
		}
		health = sum.toShort()
	}
	
	override fun copy(tc: HPComponent) {
		tc.health = health
	}
}

