package se.exuvo.aurora.galactic

import com.artemis.utils.Bag
import se.unlogic.standardutils.numbers.NumberUtils

abstract class ResearchJob(val researchPoints: Int, var progress: Int = 0) {

}

class DiscoveryResearchJob(val category: ResearchCategory) : ResearchJob(1000)
class TechnologyResearchJob(val tech: Technology) : ResearchJob(tech.researchPoints)
class DesignResearchJob(val part: Part, researchPoints: Int) : ResearchJob(researchPoints)

class ResearchTeam(var name: String) {
	var currentJob: ResearchJob? = null
	val theoreticalTheory = HashMap<TheoreticalTheory, Int>()
	
	init {
		for (theory in TheoreticalTheory.values()) {
			theoreticalTheory[theory] = (Math.random() * 10).toInt()
		}
	}
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
	ACTIVE_SENSORS(ELECTRONICS),
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
					ResearchCategory.ACTIVE_SENSORS)),
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

class TechnologyList {
	val byCode = HashMap<String, Technology>()
	val sorted = Bag<Technology>(Technology::class.java)
}

class Technology(val code: String,
								 val category: ResearchCategory,
								 val researchPoints: Int,
								 val requirementNames: List<String>,
								 val name: String,
								 val description: String,
								 val discoveryChance: Float = 1f
) {
	val requirements = Bag<Technology>(Technology::class.java)

	init {
		var techList = technologies[category]

		if (techList == null) {
			techList = TechnologyList()
			technologies[category] = techList
		}
		
		techList.byCode[code] = this
		techList.sorted.add(this)
	}
	
	fun getNumber(): Int {
		val split = code.split(" ")
		val number = NumberUtils.toInt(split[split.size - 1])
		return number!!
	}

	companion object {
		val technologies = HashMap<ResearchCategory, TechnologyList>()

		fun initTech() {

			// Missiles
			Technology("Launchers 1", ResearchCategory.LAUNCHERS, 10, emptyList(), "", "")
			Technology("Launch catapults 1", ResearchCategory.LAUNCHERS, 10, emptyList(), "", "")
			Technology("Explosive Warheads 1", ResearchCategory.WARHEADS, 10, emptyList(), "", "")
			Technology("EMP Warheads 1", ResearchCategory.WARHEADS, 10, emptyList(), "", "")
			Technology("Burst Magazines 1", ResearchCategory.MAGAZINES, 10, emptyList(), "", "")

			// Lasers
			Technology("Collimating lense 1", ResearchCategory.LENSES, 10, emptyList(), "", "")
			Technology("Wavelengths 1", ResearchCategory.WAVELENGTHS, 10, emptyList(), "", "")

			// Railguns
			// https://en.wikipedia.org/wiki/Railgun
			Technology("Railguns 1", ResearchCategory.RAILGUNS, 10, emptyList(), "", "")
			Technology("Plasma Railguns 1", ResearchCategory.RAILGUNS, 10, emptyList(), "Plasma Railgun", "")
			Technology("Projectiles 1", ResearchCategory.PROJECTILES, 10, emptyList(), "", "")
			Technology("Wear reduction 1", ResearchCategory.WEAR_REDUCTION, 10, emptyList(), "", "")

			// POWER
			Technology("Solar Cells 1", ResearchCategory.SOLAR_CELLS, 10, emptyList(), "", "")
			Technology("Fission Reactor 1", ResearchCategory.FISSION, 10, emptyList(), "", "")
			Technology("Fusion Reactor 1", ResearchCategory.FUSION, 10, emptyList(), "", "")
			Technology("Antimatter Reactor 1", ResearchCategory.FUSION, 10, emptyList(), "", "")
			Technology("Batteries 1", ResearchCategory.BATTERIES, 10, emptyList(), "", "")
			Technology("Flywheels 1", ResearchCategory.FLYWHEELS, 10, emptyList(), "", "")
			Technology("Capacitors 1", ResearchCategory.CAPACITORS, 10, emptyList(), "", "")
			Technology("Shields 1", ResearchCategory.SHIELDS, 10, emptyList(), "", "")
			// https://en.wikipedia.org/wiki/Spacecraft_thermal_control
			// https://en.wikipedia.org/wiki/Thermoelectric_cooling
			Technology("Buffered Cooling 1", ResearchCategory.COOLING, 10, emptyList(), "", "")
			Technology("Passive Cooling 1", ResearchCategory.COOLING, 10, emptyList(), "", "")
			Technology("Active Cooling 1", ResearchCategory.COOLING, 10, emptyList(), "", "")

			// Industry
			Technology("Mining 1", ResearchCategory.MINING, 10, emptyList(), "", "")
			Technology("Civilian Production 1", ResearchCategory.PRODUCTION, 10, emptyList(), "", "")
			Technology("Military Production 1", ResearchCategory.PRODUCTION, 10, emptyList(), "", "")
			Technology("Shipyards 1", ResearchCategory.PRODUCTION, 10, emptyList(), "", "") // Faster build time, chemical fuel cost to launch ships to space
			Technology("Orbital Shipyards 1", ResearchCategory.PRODUCTION, 10, emptyList(), "", "")
			Technology("Mineral Refining 1", ResearchCategory.REFINING, 10, emptyList(), "", "")
			Technology("Combustible Refining 1", ResearchCategory.REFINING, 10, emptyList(), "", "")
			Technology("Fissile Refining 1", ResearchCategory.REFINING, 10, emptyList(), "", "")

			// Colonisation
			Technology("Atmospherics 1", ResearchCategory.COLONISATION, 10, emptyList(), "", "")
			Technology("Infrastructure 1", ResearchCategory.COLONISATION, 10, emptyList(), "", "")
			Technology("Agriculture 1", ResearchCategory.COLONISATION, 10, emptyList(), "", "")
			Technology("Dampeners 1", ResearchCategory.G_LIMITS, 10, emptyList(), "", "")
			Technology("The Expanse Juice 1", ResearchCategory.G_LIMITS, 10, emptyList(), "", "")
			Technology("G Suits 1", ResearchCategory.G_LIMITS, 10, emptyList(), "", "") // Military only

			// Propulsion
			// http://www.projectrho.com/public_html/rocket/enginelist.php
			// https://en.wikipedia.org/wiki/Nuclear_pulse_propulsion
			Technology("Electrostatic Thruster 1", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Gridded Ion Thruster", "")
			Technology("Electrostatic Thruster 2", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Hall Effect Thruster", "")
			Technology("Electrostatic Thruster 3", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Field Emmision Electric Propulsion", "")
			Technology("Electrothermal Thruster 1", ResearchCategory.ELECTRICAL_THRUSTERS, 10, listOf("Electrostatic Thruster 3"), "Resistojet", "")
			Technology("Electrothermal Thruster 2", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Arcjet", "")
			Technology("Electrothermal Thruster 3", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Microwave Arcjet", "")
			Technology("Electromagnetic Thruster 1", ResearchCategory.ELECTRICAL_THRUSTERS, 10, listOf("Electrothermal Thruster 3"), "Pulsed Plasma Thruster", "")
			Technology("Electromagnetic Thruster 2", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Pulsed Inductive Thruster", "")
			Technology("Electromagnetic Thruster 3", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Electrodeless Plasma Thruster", "")
			Technology("Electromagnetic Thruster 4", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Helicon Double Layer Thruster", "")
			Technology("Electromagnetic Thruster 5", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Magnetoplasma Thruster", "")
			Technology("Electromagnetic Thruster 6", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Applied-Field Magnetoplasmadynamic Thruster", "")
			Technology("Electromagnetic Thruster 7", ResearchCategory.ELECTRICAL_THRUSTERS, 10, emptyList(), "Self-Field Magnetoplasmadynamic Thruster", "")
			// https://en.wikipedia.org/wiki/Nuclear_propulsion
			// https://en.wikipedia.org/wiki/Nuclear_thermal_rocket
			// https://en.wikipedia.org/wiki/Nuclear_pulse_propulsion
			Technology("Nuclear Thermal Thruster 1", ResearchCategory.NUCLEAR_THRUSTERS, 10, listOf("Fission Reactor 1"), "Solid Core Thermal Thruster", "")
			Technology("Nuclear Thermal Thruster 2", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Pulsed Thermal Thruster", "")
			Technology("Nuclear Thermal Thruster 3", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Liquid Core Thermal Thruster", "")
			Technology("Nuclear Thermal Thruster 4", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Gas Core Thermal Thruster", "")
			Technology("Direct Nuclear Thruster 1", ResearchCategory.NUCLEAR_THRUSTERS, 10, listOf("Nuclear Thermal Thruster 4"), "Rotating Fission-Fragment Reactor", "") // High wear
			Technology("Direct Nuclear Thruster 2", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Dusty Plasma Reactor", "") // High wear
			Technology("Direct Nuclear Thruster 3", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Gas Core Reactor", "")
			Technology("Direct Nuclear Thruster 4", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Nuclear Salt-Water Reactor", "")
			Technology("Direct Nuclear Thruster 5", ResearchCategory.NUCLEAR_THRUSTERS, 10, listOf("Fusion Reactor 1"), "Magneto-Inertial Fusion Reactor", "")
			Technology("Nuclear Pulse Thruster 1", ResearchCategory.NUCLEAR_THRUSTERS, 10, listOf("Nuclear Thermal Thruster 2"), "Orion", "")
			Technology("Nuclear Pulse Thruster 2", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Medusa", "")
			Technology("Nuclear Pulse Thruster 3", ResearchCategory.NUCLEAR_THRUSTERS, 10, listOf("Fusion Reactor 1"), "Longshot", "")
			Technology("Nuclear Pulse Thruster 4", ResearchCategory.NUCLEAR_THRUSTERS, 10, emptyList(), "Daedalus", "")
			Technology("Nuclear Pulse Thruster 5", ResearchCategory.NUCLEAR_THRUSTERS, 10, listOf("Antimatter Reactor 1"), "Antimatter-Catalyzed Nuclear Pulse Propulsion", "")
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
			Technology("Hyperdrive 1", ResearchCategory.EXOTIC_PROPULSION, 10, listOf("Alcubierre Drive 1"), "Hyperdrive", "") // Inflicts damage during travel
			Technology("Hyperdrive 2", ResearchCategory.EXOTIC_PROPULSION, 10, emptyList(), "Safe Hyperdrive", "")
			// https://en.wikipedia.org/wiki/Jump_drive
			Technology("Jump Drive 1", ResearchCategory.EXOTIC_PROPULSION, 10, listOf("Jump Gates 3"), "Jump Drive", "") // Charge time
			Technology("Jump Drive 2", ResearchCategory.EXOTIC_PROPULSION, 10, emptyList(), "Blink Drive", "") // No charge time
			// https://en.wikipedia.org/wiki/Jumpgate
			Technology("Jump Gates 1", ResearchCategory.EXOTIC_PROPULSION, 10, emptyList(), "Small Jump Gate", "") // Fixed destination
			Technology("Jump Gates 2", ResearchCategory.EXOTIC_PROPULSION, 10, emptyList(), "Medium Jump Gate", "")
			Technology("Jump Gates 3", ResearchCategory.EXOTIC_PROPULSION, 10, emptyList(), "Large Jump Gate", "")
			Technology("Wormhole Gates 1", ResearchCategory.EXOTIC_PROPULSION, 10, listOf("Jump Gates 3"), "Stargate", "") // Can travel to any other known stargate
			Technology("Wormhole Gates 2", ResearchCategory.EXOTIC_PROPULSION, 10, emptyList(), "Ship Scale Stargate", "")

			// Sensors
			Technology("EM Sensor 1", ResearchCategory.ELECTRO_MAGNETIC_SENSOR, 10, emptyList(), "", "")
			Technology("Grav Sensor 1", ResearchCategory.GRAVIMETRY_SENSOR, 10, emptyList(), "", "")
			Technology("Thermal Sensor 1", ResearchCategory.THERMAL_SENSOR, 10, emptyList(), "", "")
			Technology("Optical Sensor 1", ResearchCategory.OPTICAL_SENSOR, 10, emptyList(), "", "")
			Technology("RADAR Sensor 1", ResearchCategory.ACTIVE_SENSORS, 10, emptyList(), "", "")
			Technology("LIDAR Sensor 1", ResearchCategory.ACTIVE_SENSORS, 10, emptyList(), "", "")
			
			// ECM https://en.wikipedia.org/wiki/Electronic_countermeasure
			// 	Jamming is accomplished by a friendly platform transmitting signals on the radar frequency to produce a noise level sufficient to hide echos.
			// 	The jammer's continuous transmissions will provide a clear direction to the enemy radar, but no range information.
			Technology("RADAR Range Jammer 1", ResearchCategory.ACTIVE_SENSORS, 10, listOf("RADAR Sensor 1"), "", "")
			Technology("LIDAR Range Jammer 1", ResearchCategory.ACTIVE_SENSORS, 10, listOf("LIDAR Sensor 1"), "", "")
			// Deception may use a transponder to mimic the radar echo with a delay to indicate incorrect range.
			Technology("RADAR Range Spoofer 1", ResearchCategory.ACTIVE_SENSORS, 10, listOf("RADAR Sensor 1"), "", "")
			Technology("LIDAR Range Spoofer 1", ResearchCategory.ACTIVE_SENSORS, 10, listOf("LIDAR Sensor 1"), "", "")
			// Transponders may alternatively increase return echo strength to make a small decoy appear to be a larger target.
			Technology("RADAR Strength Spoofer 1", ResearchCategory.ACTIVE_SENSORS, 10, listOf("RADAR Sensor 1"), "", "")
			Technology("RADAR Strength Spoofer 2", ResearchCategory.ACTIVE_SENSORS, 10, emptyList(), "", "")
			Technology("LIDAR Strength Spoofer 1", ResearchCategory.ACTIVE_SENSORS, 10, listOf("LIDAR Sensor 1"), "", "")
			Technology("LIDAR Strength Spoofer 2", ResearchCategory.ACTIVE_SENSORS, 10, emptyList(), "", "")
			Technology("EM Strength Spoofer 1", ResearchCategory.ELECTRO_MAGNETIC_SENSOR, 10, listOf("EM Sensor 1"), "", "")
			Technology("Thermal Strength Spoofer 1", ResearchCategory.THERMAL_SENSOR, 10, listOf("Thermal Sensor 1"), "", "")
			// Target modifications include radar absorbing coatings and modifications of the surface shape to either "stealth" a high-value target or enhance reflections from a decoy.
			Technology("RADAR Stealth 1", ResearchCategory.ACTIVE_SENSORS, 10, listOf("RADAR Sensor 1"), "", "")
			Technology("LIDAR Stealth 1", ResearchCategory.ACTIVE_SENSORS, 10, listOf("LIDAR Sensor 1"), "", "")
			Technology("RADAR Enhanced Reflections 1", ResearchCategory.ACTIVE_SENSORS, 10, listOf("RADAR Sensor 1"), "", "")
			Technology("LIDAR Enhanced Reflections 1", ResearchCategory.ACTIVE_SENSORS, 10, listOf("LIDAR Sensor 1"), "", "")

			// Computation
			Technology("Targeting Computers 1", ResearchCategory.TARGETING_ALGORITHMS, 10, emptyList(), "", "")
			Technology("Missile Computers 1", ResearchCategory.TARGETING_ALGORITHMS, 10, emptyList(), "Coast Mode", "")
			Technology("Missile Computers 2", ResearchCategory.TARGETING_ALGORITHMS, 10, emptyList(), "Stage At Set Distance", "")
			Technology("Missile Computers 3", ResearchCategory.TARGETING_ALGORITHMS, 10, emptyList(), "Retargeting", "")
			Technology("Missile Computers 4", ResearchCategory.TARGETING_ALGORITHMS, 10, emptyList(), "Avoid Overkill", "")
			Technology("Missile Computers 5", ResearchCategory.TARGETING_ALGORITHMS, 10, emptyList(), "Avoid Friendly During Retargeting", "")
			Technology("Missile Computers 6", ResearchCategory.TARGETING_ALGORITHMS, 10, emptyList(), "Manual Retargeting", "")
			Technology("Missile Computers 7", ResearchCategory.TARGETING_ALGORITHMS, 10, emptyList(), "Course Correction", "") // If fired at enemy that dissapeared during flight and then appeared somewhere else
			Technology("Processors 1", ResearchCategory.PROCESSORS, 10, emptyList(), "", "")
			Technology("Drone AI 1", ResearchCategory.ELECTRONICS, 10, emptyList(), "Move To Point", "")
			Technology("Drone AI 2", ResearchCategory.ELECTRONICS, 10, emptyList(), "Move To Distance", "")
			Technology("Drone AI 3", ResearchCategory.ELECTRONICS, 10, emptyList(), "Follow", "")
			Technology("Drone AI 4", ResearchCategory.ELECTRONICS, 10, emptyList(), "Enable/Disable Parts", "")
			Technology("Drone AI 5", ResearchCategory.ELECTRONICS, 10, emptyList(), "Use Active Parts", "")
			Technology("Drone AI 6", ResearchCategory.ELECTRONICS, 10, emptyList(), "Flee When Damaged", "")
			Technology("Drone AI 7", ResearchCategory.ELECTRONICS, 10, emptyList(), "Auto Return For Resupply", "")
			Technology("Drone AI 8", ResearchCategory.ELECTRONICS, 10, emptyList(), "Attack", "")
			Technology("Drone AI 9", ResearchCategory.ELECTRONICS, 10, emptyList(), "Delay Order", "")

			// Infantry
			Technology("Boarding 1", ResearchCategory.INFANTRY, 10, emptyList(), "", "")
			Technology("Ground Weapons 1", ResearchCategory.INFANTRY_WEAPONS, 10, emptyList(), "", "")
			Technology("Space Safe Weapons 1", ResearchCategory.INFANTRY_WEAPONS, 10, emptyList(), "", "")
			Technology("Ground Armor 1", ResearchCategory.INFANTRY_ARMOR, 10, emptyList(), "", "")
			Technology("Space Suits 1", ResearchCategory.INFANTRY_ARMOR, 10, emptyList(), "", "")

			var reqirements = 0
			var autoRequirements = 0
			
			for (techs in technologies.values) {
				for (tech in techs.sorted) {
					if (tech.requirementNames.isNotEmpty()) {
						for (techname in tech.requirementNames) {
							tech.requirements.add(getTech(techname)!!)
							reqirements++
						}
					}

					// Auto add requirements
					val split = tech.code.split(" ")
					val number = NumberUtils.toInt(split[split.size - 1])

					if (number != null) {

						val requirementName = tech.code.substring(0, tech.code.length - split[split.size - 1].length) + (number - 1)
						val requirement = getTech(requirementName);

						if (requirement != null) {
							autoRequirements++
							tech.requirements.add(requirement)
						}
					}
				}
			}
			
			var techCount = technologies.values.sumBy { it.sorted.size }

			println("Loaded $techCount technologies, $reqirements requirements, $autoRequirements auto requirements")
		}

		fun getTech(code: String): Technology? {
			for (techs in technologies.values) {
				val tech = techs.byCode[code]

				if (tech != null) {
					return tech
				}
			}

			return null
		}
	}
}
