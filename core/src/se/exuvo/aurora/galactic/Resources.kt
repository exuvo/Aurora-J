package se.exuvo.aurora.galactic

// Weight should be stored in kg, Volume in cm³
// specificVolume in cm³/kg
enum class Resource(val specificVolume: Int) {
	// No storage requirements
	GENERIC(densityToVolume(5f)), // Steel(11.7 g/cm³), Concrete(2.4 g/cm³), Carbonfiber, Glass(2.5 g/cm³), Ceramics(4 g/cm³)
	METAL_LIGHT(densityToVolume(3.6f)), // Aluminium(2.7 g/cm³), Titanium(4.5 g/cm³)
	METAL_CONDUCTIVE(densityToVolume(12f)), // Copper(9 g/cm³), Gold(19 g/cm³)
	SEMICONDUCTORS(densityToVolume(2.3f)), // Silicon(2.3 g/cm³), germanium
	RARE_EARTH(densityToVolume(7f)), // Neodymium(7.0 g/cm³). https://en.wikipedia.org/wiki/Rare-earth_element
	MAINTENANCE_SUPPLIES(densityToVolume(1f)),
	MISSILES(0),
	SABOTS(0),
	// Requires radiation shielding
	NUCLEAR_FISSION(densityToVolume(15f)), // Uranium(19.1 g/cm³), Thorium(11.7 g/cm³)
	NUCLEAR_WASTE(densityToVolume(10f)),
	// Requires temperature control
	// Liquid deuterium (162 kg/m³) https://en.wikipedia.org/wiki/Deuterium#Data_for_elemental_deuterium
	NUCLEAR_FUSION(densityToVolume(0.1624f)), // Deuterium-Tritium, Helium3. See 'Fusion Reactor Fuel Modes' at http://forum.kerbalspaceprogram.com/index.php?/topic/155255-12213-kspi-extended-11414-05-7-2017-support-release-thread/
	ROCKET_FUEL(densityToVolume(1.5f)), // ca 1.5 g/cm³, LOX + kerosene, LOX + H, nitrogen tetroxide + hydrazine. https://en.wikipedia.org/wiki/Rocket_propellant#Liquid_propellants
	// Requires temperature control and atmosphere
	LIFE_SUPPORT(densityToVolume(0.8f)) // Food, Water, Air
}

// Density in g/cm³.  1 g/cm³ = 1000 kg/m³
fun densityToVolume(density: Float): Int {
	return (1000 / density).toInt()
}

enum class CargoType(val resources: List<Resource>) {
	NORMAL(listOf(Resource.MAINTENANCE_SUPPLIES, Resource.GENERIC, Resource.METAL_LIGHT, Resource.METAL_CONDUCTIVE, Resource.SEMICONDUCTORS, Resource.RARE_EARTH)),
	AMMUNITION(listOf(Resource.MISSILES, Resource.SABOTS)),
	FUEL(listOf(Resource.ROCKET_FUEL)),
	LIFE_SUPPORT(listOf(Resource.LIFE_SUPPORT)),
	NUCLEAR(listOf(Resource.NUCLEAR_FISSION, Resource.NUCLEAR_WASTE, Resource.NUCLEAR_FUSION))
}
