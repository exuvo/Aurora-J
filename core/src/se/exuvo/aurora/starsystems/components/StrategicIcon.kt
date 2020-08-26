package se.exuvo.aurora.starsystems.components

import com.artemis.PooledComponent
import com.badlogic.gdx.graphics.g2d.TextureRegion
import se.exuvo.aurora.Assets

class StrategicIconComponent() : PooledComponent(), CloneableComponent<StrategicIconComponent> {
	lateinit var baseTexture: TextureRegion
	var centerTexture: TextureRegion? = null
	
	fun set(icon: StrategicIcon): StrategicIconComponent {
		baseTexture = Assets.textures.findRegion(icon.base.path)
		
		if (icon.center.path != "") {
			centerTexture = Assets.textures.findRegion(icon.center.path)
		} else {
			centerTexture = null
		}
		
		return this
	}
	
	fun set(baseTexture: TextureRegion, centerTexture: TextureRegion?): StrategicIconComponent {
		this.baseTexture = baseTexture
		this.centerTexture = centerTexture
		return this
	}
	
	override fun reset(): Unit {}
	override fun copy(tc: StrategicIconComponent) {
		tc.set(baseTexture, centerTexture)
	}
}

// Icon shape/outline
enum class StrategicIconBase(val path: String, val massLimit: Long) {
	GIGANTIC("strategic/gigantic", 1_000_000L),
	MASSIVE("strategic/massive", 300_000L),
	HUGE("strategic/huge", 125_000L),
	LARGE("strategic/large", 50_000L),
	MEDIUM("strategic/medium", 10_000L),
	SMALL("strategic/small", 2000L),
	TINY("strategic/tiny", 200L),
	
	BOMBER("strategic/bomber", 30L),
	FIGHTER("strategic/fighter", 20L),
	
	COLONY("strategic/colony", 0L),
	OUTPOST("strategic/outpost", 0L),
	
	STARBASE("strategic/starbase", 0L),
	FORTRESS("strategic/fortress", 0L),
	
	ASTEROID1("strategic/asteroid1", 0L),
	ASTEROID2("strategic/asteroid2", 0L),
	MINE("strategic/mine", 0L),
	NONE("strategic/unknown1", 0L),
	;
	companion object {
		val ships = listOf(GIGANTIC, MASSIVE, HUGE, LARGE, MEDIUM, SMALL, TINY)
	}
}

enum class StrategicIconCenter(val path: String) {
	RAILGUN1("strategic/cRailgun1"),
	RAILGUN2("strategic/cRailgun2"),
	LASER1("strategic/cLaser1"),
	LASER2("strategic/cLaser2"),
	MISSILE1("strategic/cMissile1"),
	MISSILE2("strategic/cMissile2"),
	MISSILE3("strategic/cMissile3"),
	MISSILE4("strategic/cMissile4"),
	THREE("strategic/cThree"),
	INTEL("strategic/cIntel"),
	HEALING_CIRCLE("strategic/cHealingCircle"),
	BRACKETS1("strategic/cBrackets1"),
	BRACKETS2("strategic/cBrackets2"),
	NONE(""),
}

// TODO add corner/side extras
data class StrategicIcon(val base: StrategicIconBase, var center: StrategicIconCenter) {
	companion object {
		val NONE = StrategicIcon(StrategicIconBase.NONE, StrategicIconCenter.NONE)
	}
}