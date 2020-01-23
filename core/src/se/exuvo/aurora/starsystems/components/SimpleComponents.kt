package se.exuvo.aurora.starsystems.components

import com.artemis.Component
import com.badlogic.gdx.graphics.Color
import se.exuvo.aurora.galactic.Empire
import se.exuvo.aurora.starsystems.StarSystem
import com.badlogic.gdx.graphics.g2d.TextureRegion
import java.util.ArrayDeque
import com.artemis.PooledComponent

class ChangingWorldComponent() : Component()

class TextComponent() : PooledComponent() {
	lateinit var text: MutableList<String>

	fun set(text: MutableList<String> = ArrayList()): TextComponent {
		this.text = text
		return this
	}
	
	override fun reset(): Unit {
		text.clear()
	}
}

class TintComponent() : PooledComponent() {
	lateinit var color: Color

	fun set(color: Color = Color(Color.WHITE)): TintComponent {
		this.color = color
		return this
	}
	
	override fun reset(): Unit {}
}

class RenderComponent() : PooledComponent() {
	
	override fun reset(): Unit {}
}

class StrategicIconComponent() : PooledComponent() {
	lateinit var texture: TextureRegion
	
	fun set(texture: TextureRegion): StrategicIconComponent {
		this.texture = texture
		return this
	}
	
	override fun reset(): Unit {}
}

class CircleComponent() : PooledComponent() {
	var radius: Float = 1f
	
	fun set(radius: Float): CircleComponent {
		this.radius = radius
		return this
	}
	
	override fun reset(): Unit {}
}

class StarSystemComponent() : PooledComponent() {
	lateinit var system: StarSystem

	fun set(system: StarSystem): StarSystemComponent {
		this.system = system
		return this
	}
	
	override fun reset(): Unit {}
}

class OwnerComponent() : PooledComponent() {
	lateinit var empire: Empire

	fun set(empire: Empire): OwnerComponent {
		this.empire = empire
		return this
	}
	
	override fun reset(): Unit {}
}

// In kg
class MassComponent() : PooledComponent() {
	var mass: Double = 0.0

	fun set(mass: Double): MassComponent {
		this.mass = mass
		return this
	}
	
	override fun reset(): Unit {}
}

// W/m2 @ 1 AU. https://en.wikipedia.org/wiki/Solar_constant
class SunComponent() : Component() {
	var solarConstant: Int = 1361

	fun set(solarConstant: Int): SunComponent {
		this.solarConstant = solarConstant
		return this
	}
}

// W/m2
class SolarIrradianceComponent() : PooledComponent() {
	var irradiance: Int = 0

	fun set(irradiance: Int): SolarIrradianceComponent {
		this.irradiance = irradiance
		return this
	}
	
	override fun reset(): Unit {}
}

class TimedLifeComponent() : PooledComponent() {
	var endTime: Long = 0
	
	override fun reset(): Unit {}
}
