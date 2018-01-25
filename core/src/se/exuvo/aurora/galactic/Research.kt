package se.exuvo.aurora.galactic

import org.apache.log4j.Logger
import java.lang.NullPointerException

abstract class ResearchJob(val researchPoints: Int, var progress: Int = 0) {

}

class DiscoveryResearchJob(val category: ResearchCategory) : ResearchJob(1000)
class TechnologyResearchJob(val tech: Technology) : ResearchJob(tech.researchPoints)
class DesignResearchJob(val part: Part, researchPoints: Int) : ResearchJob(researchPoints)

class ResearchTeam() {
	var currentJob: ResearchJob? = null
	val theoreticalTheory = HashMap<TheoreticalTheory, Int>()
}

enum class ResearchCategory(val parent: ResearchCategory? = null) {
	MISSILES,
	LASERS,
	RAILGUNS,
	POWER,
	INDUSTRY,
	BIOLOGY,
	PROPULSION,
	ELECTRONICS,
	INFANTRY,

	LAUNCHERS(MISSILES),
	MAGAZINES(MISSILES),
	WARHEADS(MISSILES),

	WAVELENGTHS(LASERS),
	LENSES(LASERS),

	PROJECTILES(RAILGUNS),
	WEAR_REDUCTION(RAILGUNS),

	COOLING(POWER),
	SOLAR_CELLS(POWER),
	FISSION(POWER),
	FUSION(POWER),
	CAPACITORS(POWER),
	BATTERIES(POWER),
	FLYWHEELS(POWER),
	SHIELDS(POWER),

	MINING(INDUSTRY),
	PRODUCTION(INDUSTRY),
	REFINING(INDUSTRY),
	ARMOR(INDUSTRY),
	
	COLONISATION(BIOLOGY),
	G_LIMITS(BIOLOGY),

	ELECTRICAL_THRUSTERS(PROPULSION),
	CHEMICAL_THRUSTERS(PROPULSION),
	NUCLEAR_THRUSTERS(PROPULSION),
	EXOTIC_PROPULSION(PROPULSION),

	ELECTRO_MAGNETIC_SENSOR(ELECTRONICS),
	THERMAL_SENSOR(ELECTRONICS),
	OPTICAL_SENSOR(ELECTRONICS),
	RADAR_SENSOR(ELECTRONICS),
	GRAVIMETRY_SENSOR(ELECTRONICS),
	TARGETING_ALGORITHMS(ELECTRONICS),
	PROCESSORS(ELECTRONICS),

	INFANTRY_WEAPONS(INFANTRY),
	INFANTRY_ARMOR(INFANTRY),
	;
}

enum class TheoreticalTheory(val applicableCategories: List<ResearchCategory>) {
	PHYSICS(listOf(
					ResearchCategory.LASERS,
					ResearchCategory.FISSION,
					ResearchCategory.FUSION,
					ResearchCategory.WAVELENGTHS,
					ResearchCategory.WARHEADS,
					ResearchCategory.SOLAR_CELLS,
					ResearchCategory.GRAVIMETRY_SENSOR,
					ResearchCategory.EXOTIC_PROPULSION)),
	ENGINEERING(listOf(
					ResearchCategory.LAUNCHERS,
					ResearchCategory.MAGAZINES,
					ResearchCategory.REFINING,
					ResearchCategory.MINING,
					ResearchCategory.OPTICAL_SENSOR,
					ResearchCategory.THERMAL_SENSOR,
					ResearchCategory.RADAR_SENSOR)),
	BIOLOGY(listOf(
					ResearchCategory.COLONISATION,
					ResearchCategory.G_LIMITS)),
	MATERIAL_SCIENCE(listOf(
					ResearchCategory.WEAR_REDUCTION,
					ResearchCategory.ARMOR,
					ResearchCategory.FLYWHEELS,
					ResearchCategory.PROJECTILES,
					ResearchCategory.BATTERIES)),
	PROPULSION(listOf(
					ResearchCategory.PROPULSION)),
	;
}

enum class PracticalTheory(val applicableCategories: List<ResearchCategory>) {
	MISSILES(listOf(
					ResearchCategory.LAUNCHERS)),
	LASER(listOf(
					ResearchCategory.LASERS,
					ResearchCategory.CAPACITORS,
					ResearchCategory.COOLING)),
	RAILGUNS(listOf(
					ResearchCategory.RAILGUNS,
					ResearchCategory.CAPACITORS,
					ResearchCategory.COOLING)),
	REACTORS(listOf(
					ResearchCategory.FISSION,
					ResearchCategory.FUSION)),
	ELECTRICAL_THRUSTERS(listOf(
					ResearchCategory.ELECTRICAL_THRUSTERS)),
	CHEMICAL_THRUSTERS(listOf(
					ResearchCategory.CHEMICAL_THRUSTERS)),
	NUCLEAR_THRUSTERS(listOf(
					ResearchCategory.NUCLEAR_THRUSTERS)),
	MINING(listOf(
					ResearchCategory.MINING)),
	REFINING(listOf(
					ResearchCategory.REFINING)),
	INFANTRY(listOf(
					ResearchCategory.INFANTRY)),
	;
}

class Technology(val code: String, // Image is mapped from name
								 val category: ResearchCategory,
								 val researchPoints: Int,
								 val requirementNames: List<String>,
								 val name: String,
								 val description: String,
								 val discoveryChance: Float = 1f
) {
	val requirements: List<Technology>

	init {
		if (requirementNames.isEmpty()) {
			requirements = emptyList()
		} else {
			requirements = ArrayList()
		}

		var techList = technologies[category]

		if (techList == null) {
			techList = HashMap()
			technologies[category] = techList
		}

		techList.put(name, this)
	}

	companion object {
		val technologies = HashMap<ResearchCategory, HashMap<String, Technology>>()

		fun initTech() {

			// Missiles
			Technology("Launchers 1", ResearchCategory.LAUNCHERS, 10, emptyList(), "", "")
			Technology("Warheads 1", ResearchCategory.WARHEADS, 10, emptyList(), "", "")
			Technology("Magazines 1", ResearchCategory.MAGAZINES, 10, emptyList(), "", "")

			// Lasers
			Technology("Collimating lense 1", ResearchCategory.LENSES, 10, emptyList(), "", "")
			Technology("Wavelengths 1", ResearchCategory.WAVELENGTHS, 10, emptyList(), "", "")

			// Railguns
			Technology("Projectiles 1", ResearchCategory.PROJECTILES, 10, emptyList(), "", "")
			Technology("Wear reduction 1", ResearchCategory.WEAR_REDUCTION, 10, emptyList(), "", "")

			// POWER
			Technology("Solar Cells 1", ResearchCategory.SOLAR_CELLS, 10, emptyList(), "", "")
			Technology("Fission Reactor 1", ResearchCategory.FISSION, 10, emptyList(), "", "")
			Technology("Fusion Reactor 1", ResearchCategory.FUSION, 10, emptyList(), "", "")
			Technology("Batteries 1", ResearchCategory.BATTERIES, 10, emptyList(), "", "")
			Technology("Flywheels 1", ResearchCategory.FLYWHEELS, 10, emptyList(), "", "")
			Technology("Capacitors 1", ResearchCategory.CAPACITORS, 10, emptyList(), "", "")
			Technology("Shields 1", ResearchCategory.SHIELDS, 10, emptyList(), "", "")
			// https://en.wikipedia.org/wiki/Spacecraft_thermal_control
			Technology("Buffered Cooling 1", ResearchCategory.COOLING, 10, emptyList(), "", "")
			Technology("Emissive Cooling 1", ResearchCategory.COOLING, 10, emptyList(), "", "")
			Technology("Thermoelectric Cooling 1", ResearchCategory.COOLING, 10, emptyList(), "", "") // https://en.wikipedia.org/wiki/Thermoelectric_cooling

			// Industry			
			Technology("Mining 1", ResearchCategory.MINING, 10, emptyList(), "", "")
			Technology("Civilian Production 1", ResearchCategory.PRODUCTION, 10, emptyList(), "", "")
			Technology("Military Production 1", ResearchCategory.PRODUCTION, 10, emptyList(), "", "")
			Technology("Shipyards 1", ResearchCategory.PRODUCTION, 10, emptyList(), "", "")
			Technology("Mineral Refining 1", ResearchCategory.REFINING, 10, emptyList(), "", "")
			Technology("Combustible Refining 1", ResearchCategory.REFINING, 10, emptyList(), "", "")
			Technology("Fissile Refining 1", ResearchCategory.REFINING, 10, emptyList(), "", "")

			// Colonisation
			Technology("Atmospherics 1", ResearchCategory.COLONISATION, 10, emptyList(), "", "")
			Technology("Infrastructure 1", ResearchCategory.COLONISATION, 10, emptyList(), "", "")
			Technology("Agriculture 1", ResearchCategory.COLONISATION, 10, emptyList(), "", "")
			Technology("Agriculture 1", ResearchCategory.G_LIMITS, 10, emptyList(), "", "")
			Technology("Agriculture 1", ResearchCategory.G_LIMITS, 10, emptyList(), "", "")
			Technology("Agriculture 1", ResearchCategory.G_LIMITS, 10, emptyList(), "", "")

			// Propulsion
			// https://en.wikipedia.org/wiki/Nuclear_pulse_propulsion
			Technology("Electrostatic Thruster 1", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Gridded Ion Thruster", "")
			Technology("Electrostatic Thruster 2", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Hall Effect Thruster", "")
			Technology("Electrostatic Thruster 4", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Field Emmision Electric Propulsion", "")
			Technology("Electrothermal Thruster 1", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Resistojet", "")
			Technology("Electrothermal Thruster 2", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Arcjet", "")
			Technology("Electrothermal Thruster 3", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Microwave Arcjet", "")
			Technology("Electromagnetic Thruster 1", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Pulsed Plasma Thruster", "")
			Technology("Electromagnetic Thruster 2", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Pulsed Inductive Thruster", "")
			Technology("Electromagnetic Thruster 3", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Electrodeless Plasma Thruster", "")
			Technology("Electromagnetic Thruster 4", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Helicon Double Layer Thruster", "")
			Technology("Electromagnetic Thruster 5", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Magnetoplasma Thruster", "")
			Technology("Electromagnetic Thruster 6", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Applied-Field Magnetoplasmadynamic Thruster", "")
			Technology("Electromagnetic Thruster 7", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Self-Field Magnetoplasmadynamic Thruster", "")
			// https://en.wikipedia.org/wiki/Nuclear_propulsion
			// https://en.wikipedia.org/wiki/Nuclear_thermal_rocket
			// https://en.wikipedia.org/wiki/Nuclear_pulse_propulsion
			Technology("Nuclear Thermal Thruster 1", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Solid Core Thermal Thruster", "")
			Technology("Nuclear Thermal Thruster 2", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Pulsed Thermal Thruster", "")
			Technology("Nuclear Thermal Thruster 3", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Liquid Core Thermal Thruster", "")
			Technology("Nuclear Thermal Thruster 4", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Gas Core Thermal Thruster", "")
			Technology("Direct Nuclear Thruster 1", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Rotating Fission-Fragment Reactor", "") // High wear
			Technology("Direct Nuclear Thruster 2", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Dusty Plasma Reactor", "") // High wear
			Technology("Direct Nuclear Thruster 3", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Gas Core Reactor", "")
			Technology("Direct Nuclear Thruster 4", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Nuclear Salt-Water Reactor", "")
			Technology("Direct Nuclear Thruster 5", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Magneto-Inertial Fusion Reactor", "")
			Technology("Nuclear Pulse Thruster 1", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Orion", "")
			Technology("Nuclear Pulse Thruster 2", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Medusa", "")
			Technology("Nuclear Pulse Thruster 3", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Longshot", "")
			Technology("Nuclear Pulse Thruster 4", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Daedalus", "")
			Technology("Nuclear Pulse Thruster 5", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Antimatter-Catalyzed Nuclear Pulse Propulsion", "")
			// https://en.wikipedia.org/wiki/Rocket_engine
			// https://en.wikipedia.org/wiki/Rocket_propellant#Liquid_propellants
			Technology("Chemical Thruster 1", ResearchCategory.CHEMICAL_THRUSTERS, 10, emptyList(), "Solid-propellant thruster", "")
			Technology("Chemical Thruster 2", ResearchCategory.CHEMICAL_THRUSTERS, 10, emptyList(), "Hybrid-propellant Nitrous Oxide + Plexiglass", "")
			Technology("Chemical Thruster 3", ResearchCategory.CHEMICAL_THRUSTERS, 10, emptyList(), "Hybrid-propellant Hydrogen Peroxide + HTPB (Cross-Linked Rubber)", "")
			Technology("Chemical Thruster 4", ResearchCategory.CHEMICAL_THRUSTERS, 10, emptyList(), "Liquid-propellant LOX + RP-1 (Kerosene)", "")
			Technology("Chemical Thruster 5", ResearchCategory.CHEMICAL_THRUSTERS, 10, emptyList(), "Liquid-propellant LOX + LH", "")
			Technology("Chemical Thruster 6", ResearchCategory.CHEMICAL_THRUSTERS, 10, emptyList(), "Liquid-propellant Nitrogen Tetroxide + Hydrazine", "")
			// https://en.wikipedia.org/wiki/Alcubierre_drive
			Technology("Alcubierre Drive 1", ResearchCategory.EXOTIC_PROPULSION, 10, emptyList(), "Alcubierre Drive", "") 
			// https://en.wikipedia.org/wiki/Hyperdrive
			Technology("Hyperdrive 1", ResearchCategory.EXOTIC_PROPULSION, 10, emptyList(), "Hyperdrive", "")
			// https://en.wikipedia.org/wiki/Jump_drive
			Technology("Jump Drive 1", ResearchCategory.EXOTIC_PROPULSION, 10, emptyList(), "Jump Drive", "") // Charge time
			Technology("Jump Drive 2", ResearchCategory.EXOTIC_PROPULSION, 10, emptyList(), "Blink Drive", "") // No charge time

			// Sensors
			Technology("EM Sensor 1", ResearchCategory.ELECTRO_MAGNETIC_SENSOR, 10, emptyList(), "", "")
			Technology("Grav Sensor 1", ResearchCategory.GRAVIMETRY_SENSOR, 10, emptyList(), "", "")
			Technology("Thermal Sensor 1", ResearchCategory.THERMAL_SENSOR, 10, emptyList(), "", "")
			Technology("Optical Sensor 1", ResearchCategory.OPTICAL_SENSOR, 10, emptyList(), "", "")
			Technology("RADAR Sensor 1", ResearchCategory.RADAR_SENSOR, 10, emptyList(), "", "")

			// Computation
			Technology("Targeting Computers 1", ResearchCategory.TARGETING_ALGORITHMS, 10, emptyList(), "", "")
			Technology("Processors 1", ResearchCategory.PROCESSORS, 10, emptyList(), "", "")
			Technology("AI 1", ResearchCategory.ELECTRONICS, 10, emptyList(), "", "")

			// Infantry
			Technology("Boarding 1", ResearchCategory.INFANTRY, 10, emptyList(), "", "")
			Technology("Ground Weapons 1", ResearchCategory.INFANTRY_WEAPONS, 10, emptyList(), "", "")
			Technology("Space Safe Weapons 1", ResearchCategory.INFANTRY_WEAPONS, 10, emptyList(), "", "")
			Technology("Ground Armor 1", ResearchCategory.INFANTRY_ARMOR, 10, emptyList(), "", "")
			Technology("Space Suits 1", ResearchCategory.INFANTRY_ARMOR, 10, emptyList(), "", "")

			for (techs in technologies.values) {
				for (tech in techs.values) {
					if (tech.requirements is MutableList) {
						for (techname in tech.requirementNames) {
							tech.requirements.add(getTech(techname))
						}
					}
				}
			}

			var techCount = technologies.values.sumBy { it.size }

			println("Loaded $techCount technologies")
		}

		fun getTech(name: String): Technology {
			for (techs in technologies.values) {
				val tech = techs.get(name)

				if (tech != null) {
					return tech
				}
			}

			throw NullPointerException("Found no technology with name $name")
		}
	}
}
