package se.exuvo.aurora.starsystems.components

import com.artemis.Component
import com.badlogic.gdx.graphics.Color
import se.exuvo.aurora.galactic.Empire
import se.exuvo.aurora.starsystems.StarSystem
import com.artemis.PooledComponent

class ChangingWorldComponent() : Component()

class TextComponent() : PooledComponent(), CloneableComponent<TextComponent> {
	lateinit var text: MutableList<String>

	fun set(text: MutableList<String> = ArrayList()): TextComponent {
		this.text = text
		return this
	}
	
	override fun reset(): Unit {
		text.clear()
	}
	
	override fun copy(tc: TextComponent) {
		if (text.hashCode() != tc.hashCode()) {
			tc.set(ArrayList(text))
		}
	}
}

class TintComponent() : PooledComponent(), CloneableComponent<TintComponent> {
	lateinit var color: Color

	fun set(color: Color = Color(Color.WHITE)): TintComponent {
		this.color = color
		return this
	}
	
	override fun reset(): Unit {}
	override fun copy(tc: TintComponent) {
		tc.set(color)
	}
}

class RenderComponent() : PooledComponent(), CloneableComponent<RenderComponent> {
	
	override fun reset(): Unit {}
	override fun copy(tc: RenderComponent) {}
}

// in m
class CircleComponent() : PooledComponent(), CloneableComponent<CircleComponent> {
	var radius: Float = 1f
	
	fun set(radius: Float): CircleComponent {
		this.radius = radius
		return this
	}
	
	override fun reset(): Unit {}
	override fun copy(tc: CircleComponent) {
		tc.set(radius)
	}
}

class StarSystemComponent() : PooledComponent(), CloneableComponent<StarSystemComponent> {
	lateinit var system: StarSystem

	fun set(system: StarSystem): StarSystemComponent {
		this.system = system
		return this
	}
	
	override fun reset(): Unit {}
	override fun copy(tc: StarSystemComponent) {
		tc.set(system)
	}
}

class EmpireComponent() : PooledComponent(), CloneableComponent<EmpireComponent> {
	lateinit var empire: Empire

	fun set(empire: Empire): EmpireComponent {
		this.empire = empire
		return this
	}
	
	override fun reset(): Unit {}
	override fun copy(tc: EmpireComponent) {
		tc.set(empire)
	}
}

// In kg
class MassComponent() : PooledComponent(), CloneableComponent<MassComponent> {
	var mass: Double = 0.0

	fun set(mass: Double): MassComponent {
		this.mass = mass
		return this
	}
	
	override fun reset(): Unit {}
	override fun copy(tc: MassComponent) {
		tc.set(mass)
	}
}

// W/m2 @ 1 AU. https://en.wikipedia.org/wiki/Solar_constant
class SunComponent() : Component(), CloneableComponent<SunComponent> {
	var solarConstant: Int = 1361

	fun set(solarConstant: Int): SunComponent {
		this.solarConstant = solarConstant
		return this
	}
	
	override fun copy(tc: SunComponent) {
		tc.set(solarConstant)
	}
}

class AsteroidComponent() : Component(), CloneableComponent<AsteroidComponent> {
	
	override fun copy(tc: AsteroidComponent) {
	}
}

// W/m2
class SolarIrradianceComponent() : PooledComponent(), CloneableComponent<SolarIrradianceComponent> {
	var irradiance: Int = 0

	fun set(irradiance: Int): SolarIrradianceComponent {
		this.irradiance = irradiance
		return this
	}
	
	override fun reset(): Unit {}
	override fun copy(tc: SolarIrradianceComponent) {
		tc.set(irradiance)
	}
}

class TimedLifeComponent() : PooledComponent(), CloneableComponent<TimedLifeComponent> {
	var endTime: Long = 0
	
	override fun reset(): Unit {}
	override fun copy(tc: TimedLifeComponent) {
		tc.endTime = endTime
	}
}
