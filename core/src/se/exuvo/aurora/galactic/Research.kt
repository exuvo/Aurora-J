package se.exuvo.aurora.galactic

import org.apache.log4j.Logger
import java.lang.NullPointerException

enum class ResearchCategory {
	MISSILES,
	LASER,
	RAILGUNS,
	POWER,
	INDUSTRY,
	COLONISATION,
	PROPULSION,
	ELECTRONICS,
	INFANTRY;
}

class Technology(val name: String, // Image is mapped from name
								 val category: ResearchCategory,
								 val researchPoints: Int,
								 val requirementNames: List<String>,
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
			Technology("Launchers 1", ResearchCategory.MISSILES, 10, emptyList())
			Technology("Warheads 1", ResearchCategory.MISSILES, 10, emptyList())
			Technology("Magazines 1", ResearchCategory.MISSILES, 10, emptyList())

			// Lasers
			Technology("Collimating lense 1", ResearchCategory.LASER, 10, emptyList())
			Technology("Wavelengths 1", ResearchCategory.LASER, 10, emptyList())

			// Railguns
			Technology("Projectiles 1", ResearchCategory.RAILGUNS, 10, emptyList())
			Technology("Wear reduction 1", ResearchCategory.RAILGUNS, 10, emptyList())

			// POWER
			Technology("Solar Cells 1", ResearchCategory.POWER, 10, emptyList())
			Technology("Fission Reactor 1", ResearchCategory.POWER, 10, emptyList())
			Technology("Fusion Reactor 1", ResearchCategory.POWER, 10, emptyList())
			Technology("Batteries 1", ResearchCategory.POWER, 10, emptyList())
			Technology("Capacitors 1", ResearchCategory.POWER, 10, emptyList())
			Technology("Shields 1", ResearchCategory.POWER, 10, emptyList())
			Technology("Cooling 1", ResearchCategory.POWER, 10, emptyList())

			// Industry			
			Technology("Mining 1", ResearchCategory.INDUSTRY, 10, emptyList())
			Technology("Production 1", ResearchCategory.INDUSTRY, 10, emptyList())
			Technology("Mineral Refining 1", ResearchCategory.INDUSTRY, 10, emptyList())
			Technology("Combustible Refining 1", ResearchCategory.INDUSTRY, 10, emptyList())
			Technology("Fissile Refining 1", ResearchCategory.INDUSTRY, 10, emptyList())

			// Colonisation
			Technology("Atmospherics 1", ResearchCategory.COLONISATION, 10, emptyList())
			Technology("Infrastructure 1", ResearchCategory.COLONISATION, 10, emptyList())
			Technology("Agriculture 1", ResearchCategory.COLONISATION, 10, emptyList())

			// Propulsion
			Technology("Electrical Thruster 1", ResearchCategory.PROPULSION, 10, emptyList())
			Technology("Nuclear Thruster 1", ResearchCategory.PROPULSION, 10, emptyList())
			Technology("Chemical Thruster 1", ResearchCategory.PROPULSION, 10, emptyList())
			Technology("Alcubierre Drive 1", ResearchCategory.PROPULSION, 10, emptyList())

			// Sensors
			Technology("EM Sensor 1", ResearchCategory.ELECTRONICS, 10, emptyList())
			Technology("Thermal Sensor 1", ResearchCategory.ELECTRONICS, 10, emptyList())
			Technology("Optical Sensor 1", ResearchCategory.ELECTRONICS, 10, emptyList())
			Technology("RADAR Sensor 1", ResearchCategory.ELECTRONICS, 10, emptyList())

			// Computation
			Technology("Targeting Computers 1", ResearchCategory.ELECTRONICS, 10, emptyList())
			Technology("Processors 1", ResearchCategory.ELECTRONICS, 10, emptyList())

			// Infantry
			Technology("Boarding 1", ResearchCategory.INFANTRY, 10, emptyList())
			Technology("Weapons 1", ResearchCategory.INFANTRY, 10, emptyList())
			Technology("Armor 1", ResearchCategory.INFANTRY, 10, emptyList())

			for (techs in technologies.values) {
				for (tech in techs.values) {
					if (tech.requirements is MutableList) {
						for (techname in tech.requirementNames) {
							tech.requirements.add(getTech(techname))
						}
					}
				}
			}
			
			var techCount = technologies.values.sumBy{it.size}
			
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
