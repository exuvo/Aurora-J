package se.exuvo.aurora.galactic

import org.apache.logging.log4j.LogManager
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import com.artemis.utils.Sort
import se.exuvo.aurora.starsystems.components.EntityUUID
import se.exuvo.aurora.starsystems.components.EntityReference
import com.artemis.utils.Bag
import com.artemis.utils.IntBag
import com.badlogic.gdx.graphics.Color
import se.exuvo.aurora.starsystems.StarSystem
import se.exuvo.aurora.starsystems.components.ShipOrder
import uk.co.omegaprime.btreemap.LongObjectBTreeMap
import java.util.concurrent.ArrayBlockingQueue

class Empire(var name: String) {
companion object {
		val empireIDGenerator = AtomicInteger()
		val GAIA = Empire("Gaia")
	}

	val id = empireIDGenerator.getAndIncrement()
	val log = LogManager.getLogger(this.javaClass)
	val lock = ReentrantReadWriteLock()

	var funds: Long = 0
	val colonies = Bag<EntityReference>()
	val stations = Bag<EntityReference>()
	val technologies = HashMap<String, Technology>()
	val parts = ArrayList<Part>()
	val researchTeams = ArrayList<ResearchTeam>()
	val practicalTheory = HashMap<PracticalTheory, Int>()
	val shipHulls = ArrayList<ShipHull>()
	val hullClasses = ArrayList<ShipHullClass>()
	val commandQueue = ArrayBlockingQueue<Command>(128) // For local player and ai
	val color = Color.RED //TODO allow picks from https://gamedev.stackexchange.com/questions/46463/how-can-i-find-an-optimum-set-of-colors-for-10-players
	
	init {
		hullClasses.add(ShipHullClass("Dreadnought", "BA"))
		hullClasses.add(ShipHullClass("Battleship", "BB"))
		hullClasses.add(ShipHullClass("Battlecruiser", "BC"))
		hullClasses.add(ShipHullClass("Heavy Cruiser", "CA"))
		hullClasses.add(ShipHullClass("Light Cruiser", "CL"))
		hullClasses.add(ShipHullClass("Command Cruiser", "CC"))
		hullClasses.add(ShipHullClass("Carrier", "CV"))
		hullClasses.add(ShipHullClass("Destroyer", "DA"))
		hullClasses.add(ShipHullClass("Frigate", "DB"))
		hullClasses.add(ShipHullClass("Corvette", "DC"))
		hullClasses.add(ShipHullClass("Fighter", "FF"))
		
		// Logistics
		hullClasses.add(ShipHullClass("Collier", "LC"))
		hullClasses.add(ShipHullClass("Ammunition Transport", "LA"))
		hullClasses.add(ShipHullClass("Combat Stores Transport", "LS"))
		hullClasses.add(ShipHullClass("Tanker", "LT"))
		hullClasses.add(ShipHullClass("Construction Ship", "LB"))
		hullClasses.add(ShipHullClass("Colony Ship", "LE"))
		hullClasses.add(ShipHullClass("Freighter", "LF"))
		
		hullClasses.sortWith(object : Comparator<ShipHullClass> {
			override fun compare(o1: ShipHullClass, o2: ShipHullClass): Int {
				return o1.code.compareTo(o2.code)
			}
		})
		
		researchTeams += ResearchTeam("Einstein")
		researchTeams += ResearchTeam("Hawking")
		
		practicalTheory[PracticalTheory.CHEMICAL_THRUSTERS] = 5
		practicalTheory[PracticalTheory.INFANTRY] = 5
		practicalTheory[PracticalTheory.REFINING] = 5
	}
}