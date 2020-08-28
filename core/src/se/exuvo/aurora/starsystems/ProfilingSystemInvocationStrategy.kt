package se.exuvo.aurora.starsystems

import se.exuvo.aurora.starsystems.CustomSystemInvocationStrategy
import se.exuvo.aurora.starsystems.StarSystem
import se.exuvo.aurora.utils.forEachFast

class ProfilingSystemInvocationStrategy(starSystem: StarSystem) : CustomSystemInvocationStrategy(starSystem) {

	override fun initialize() {
		super.initialize()
	}

	override fun process() {
		
		val profilerEvents = starSystem.workingShadow.profilerEvents
		
		profilerEvents.start("updateEntityStates")
		updateEntityStates()
		profilerEvents.end()

		profilerEvents.start("preSystems")
		preSystems.forEachFast { i, system ->
			if (!disabled.unsafeGet(i)) {
				profilerEvents.start("${system::class.simpleName}")
				system.preProcessSystem()
				profilerEvents.end()
			}
		}
		profilerEvents.end()

		profilerEvents.start("systems")
		systems.forEachFast { i, system ->
			if (!disabled.unsafeGet(i)) {
				profilerEvents.start("${system::class.simpleName}")
				system.process()
				profilerEvents.end()
				profilerEvents.start("updateEntityStates")
				updateEntityStates()
				profilerEvents.end()
			}
		}
		profilerEvents.end()

		profilerEvents.start("postSystems")
		postSystems.forEachFast { i, system ->
			if (!disabled.unsafeGet(i)) {
				profilerEvents.start("${system::class.simpleName}")
				system.postProcessSystem()
				profilerEvents.end()
			}
		}
		profilerEvents.end()
	}
}
