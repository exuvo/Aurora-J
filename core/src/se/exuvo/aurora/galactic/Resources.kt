package se.exuvo.aurora.galactic

// Amounts should be stored in m3
// Density in kg per m3
enum class Resource(val density: Int) {
	// No storage requirements
	GENERIC(1), // Steel, Concrete, Carbonfiber, Glass, Ceramics
	METAL_LIGHT(1), // Aluminium, Titanium
	METAL_CONDUCTIVE(1), // Copper, Gold
	SEMICONDUCTORS(1), // Silicon, germanium
	RARE_EARTH(1), // Neodymium. https://en.wikipedia.org/wiki/Rare-earth_element
	MAINTENANCE_SUPPLIES(1),
	// Requires radiation shielding
	NUCLEAR_FISSION(1), // Uranium, Thorium
	NUCLEAR_FUSION(1), // Deuterium-Tritium, Helium3. See 'Fusion Reactor Fuel Modes' at http://forum.kerbalspaceprogram.com/index.php?/topic/155255-12213-kspi-extended-11414-05-7-2017-support-release-thread/
	// Requires temperature control
	ROCKET_FUEL(1), // LOX + kerosene, LOX + H, nitrogen tetroxide + hydrazine. https://en.wikipedia.org/wiki/Rocket_propellant#Liquid_propellants
	// Requires temperature control and atmosphere
	LIFE_SUPPORT(1), // Food, Water, Air
	MISSILES(1),
	SABOTS(1),
	ITEMS(0);
}

enum class CargoType(val resources: List<Resource>) {
	NORMAL(listOf(Resource.MAINTENANCE_SUPPLIES, Resource.GENERIC, Resource.METAL_LIGHT, Resource.METAL_CONDUCTIVE, Resource.SEMICONDUCTORS, Resource.RARE_EARTH, Resource.ITEMS)),
	AMMUNITION(listOf(Resource.MISSILES, Resource.SABOTS)),
	FUEL(listOf(Resource.ROCKET_FUEL)),
	LIFE_SUPPORT(listOf(Resource.LIFE_SUPPORT)),
	NUCLEAR(listOf(Resource.NUCLEAR_FISSION, Resource.NUCLEAR_FUSION))
}
