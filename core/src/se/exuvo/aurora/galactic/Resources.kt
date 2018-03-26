package se.exuvo.aurora.galactic

// Weight should be stored in grams, Volume in cm3
// Density in g per cm3
enum class Resource(val density: Int) {
	// No storage requirements
	GENERIC(8), // Steel(11.7 g/cm³), Concrete, Carbonfiber, Glass, Ceramics
	METAL_LIGHT(3), // Aluminium(2.7 g/cm³), Titanium(4.5 g/cm³)
	METAL_CONDUCTIVE(13), // Copper(9 g/cm³), Gold(19 g/cm³)
	SEMICONDUCTORS(2), // Silicon(2.3 g/cm³), germanium
	RARE_EARTH(7), // Neodymium(7.0 g/cm³). https://en.wikipedia.org/wiki/Rare-earth_element
	MAINTENANCE_SUPPLIES(1),
	MISSILES(0),
	SABOTS(0),
	ITEMS(0),
	// Requires radiation shielding
	NUCLEAR_FISSION(15), // Uranium(19.1 g/cm³), Thorium(11.7 g/cm³)
	// Requires temperature control
	// Liquid deuterium (162 g/cm³) https://en.wikipedia.org/wiki/Deuterium#Data_for_elemental_deuterium
	NUCLEAR_FUSION(162), // Deuterium-Tritium, Helium3. See 'Fusion Reactor Fuel Modes' at http://forum.kerbalspaceprogram.com/index.php?/topic/155255-12213-kspi-extended-11414-05-7-2017-support-release-thread/
	ROCKET_FUEL(1), // ca 1.5 g/cm³, LOX + kerosene, LOX + H, nitrogen tetroxide + hydrazine. https://en.wikipedia.org/wiki/Rocket_propellant#Liquid_propellants
	// Requires temperature control and atmosphere
	LIFE_SUPPORT(1) // Food, Water, Air
}

enum class CargoType(val resources: List<Resource>) {
	NORMAL(listOf(Resource.MAINTENANCE_SUPPLIES, Resource.GENERIC, Resource.METAL_LIGHT, Resource.METAL_CONDUCTIVE, Resource.SEMICONDUCTORS, Resource.RARE_EARTH, Resource.ITEMS)),
	AMMUNITION(listOf(Resource.MISSILES, Resource.SABOTS)),
	FUEL(listOf(Resource.ROCKET_FUEL)),
	LIFE_SUPPORT(listOf(Resource.LIFE_SUPPORT)),
	NUCLEAR(listOf(Resource.NUCLEAR_FISSION, Resource.NUCLEAR_FUSION))
}
