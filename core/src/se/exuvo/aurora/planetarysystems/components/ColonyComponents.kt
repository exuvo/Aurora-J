package se.exuvo.aurora.empires.components

import com.artemis.Component


class ColonyComponent() : Component() {
	var population: Int = 0
	
	fun set(population: Int): ColonyComponent {
		this.population = population
		return this
	}
}
