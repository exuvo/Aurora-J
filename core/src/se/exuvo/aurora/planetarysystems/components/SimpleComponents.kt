package se.exuvo.aurora.planetarysystems.components

import com.artemis.Component
import com.badlogic.gdx.graphics.Color
import se.exuvo.aurora.galactic.Empire
import se.exuvo.aurora.planetarysystems.PlanetarySystem
import com.badlogic.gdx.graphics.g2d.TextureRegion

class ChangingWorldComponent() : Component()

class TextComponent() : Component() {
	lateinit var text: MutableList<String>

	fun set(text: MutableList<String> = ArrayList()): TextComponent {
		this.text = text
		return this
	}
}

class TintComponent() : Component() {
	lateinit var color: Color

	fun set(color: Color = Color(Color.WHITE)): TintComponent {
		this.color = color
		return this
	}
}

class RenderComponent() : Component()

class StrategicIconComponent() : Component() {
	lateinit var texture: TextureRegion
	
	fun set(texture: TextureRegion): StrategicIconComponent {
		this.texture = texture
		return this
	}
}

class CircleComponent() : Component() {
	var radius: Float = 1f
	
	fun set(radius: Float): CircleComponent {
		this.radius = radius
		return this
	}
}

class PlanetarySystemComponent() : Component() {
	lateinit var system: PlanetarySystem

	fun set(system: PlanetarySystem): PlanetarySystemComponent {
		this.system = system
		return this
	}
}

class OwnerComponent() : Component() {
	lateinit var empire: Empire

	fun set(empire: Empire): OwnerComponent {
		this.empire = empire
		return this
	}
}

// In kg
class MassComponent() : Component() {
	var mass: Double = 0.0

	fun set(mass: Double): MassComponent {
		this.mass = mass
		return this
	}
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
class SolarIrradianceComponent() : Component() {
	var irradiance: Int = 0

	fun set(irradiance: Int): SolarIrradianceComponent {
		this.irradiance = irradiance
		return this
	}
}
