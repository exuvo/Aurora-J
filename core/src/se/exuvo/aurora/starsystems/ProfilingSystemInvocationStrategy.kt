package se.exuvo.aurora.starsystems.systems

import com.artemis.SystemInvocationStrategy
import com.artemis.utils.Bag
import com.artemis.WorldConfigurationBuilder.Priority
import com.artemis.utils.Sort
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.ui.ProfilerWindow

class ProfilingSystemInvocationStrategy(val profiler: ProfilerWindow) : CustomSystemInvocationStrategy() {

	override fun initialize() {
		super.initialize()
		
		
	}

	override fun process() {
		
		profiler.systemEvents.clear()
		profiler.systemEvents.start("process")
		
		profiler.systemEvents.start("updateEntityStates")
		updateEntityStates()
		profiler.systemEvents.end()

		profiler.systemEvents.start("preSystems")
		preSystems.forEachFast { i, system ->
			if (!disabled.unsafeGet(i)) {
				profiler.systemEvents.start("${system::class.simpleName}")
				system.preProcessSystem()
				profiler.systemEvents.end()
			}
		}
		profiler.systemEvents.end()

		profiler.systemEvents.start("systems")
		systems.forEachFast { i, system ->
			if (!disabled.unsafeGet(i)) {
				profiler.systemEvents.start("${system::class.simpleName}")
				system.process()
				profiler.systemEvents.end()
				profiler.systemEvents.start("updateEntityStates")
				updateEntityStates()
				profiler.systemEvents.end()
			}
		}
		profiler.systemEvents.end()

		profiler.systemEvents.start("postSystems")
		postSystems.forEachFast { i, system ->
			if (!disabled.unsafeGet(i)) {
				profiler.systemEvents.start("${system::class.simpleName}")
				system.postProcessSystem()
				profiler.systemEvents.end()
			}
		}
		profiler.systemEvents.end()
		
		profiler.systemEvents.end()
	}
}
