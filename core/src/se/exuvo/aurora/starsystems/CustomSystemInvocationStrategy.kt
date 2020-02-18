package se.exuvo.aurora.starsystems.systems

import com.artemis.SystemInvocationStrategy
import com.artemis.utils.Bag
import com.artemis.WorldConfigurationBuilder.Priority
import com.artemis.utils.Sort
import se.exuvo.aurora.utils.forEachFast

open class CustomSystemInvocationStrategy : SystemInvocationStrategy() {
	lateinit var preSystems: Bag<PreSystem>
	lateinit var postSystems: Bag<PostSystem>

	override fun initialize() {
		preSystems = Bag(PreSystem::class.java, systems.size())
		postSystems = Bag(PostSystem::class.java, systems.size())

		systems.forEachFast { system ->

			if (system is PreSystem) {
				preSystems.add(system)
			}

			if (system is PostSystem) {
				postSystems.add(system)
			}
		}

		Sort.instance().sort(preSystems, object : Comparator<PreSystem> {
			override fun compare(o1: PreSystem, o2: PreSystem): Int {
				return -o1.getPreProcessPriority().compareTo(o2.getPreProcessPriority())
			}
		})
		
		Sort.instance().sort(postSystems, object : Comparator<PostSystem> {
			override fun compare(o1: PostSystem, o2: PostSystem): Int {
				return -o1.getPostProcessPriority().compareTo(o2.getPostProcessPriority())
			}
		})
	}

	override fun process() {
		
		updateEntityStates()

		preSystems.forEachFast { i, system ->
			if (!disabled.unsafeGet(i)) {
				system.preProcessSystem()
			}
		}

		systems.forEachFast { i, system ->
			if (!disabled.unsafeGet(i)) {
				system.process()
				updateEntityStates()
			}
		}

		postSystems.forEachFast { i, system ->
			if (!disabled.unsafeGet(i)) {
				system.postProcessSystem()
			}
		}
	}
}

interface PreSystem {
	fun preProcessSystem()
	fun getPreProcessPriority() = Priority.NORMAL
}

interface PostSystem {
	fun postProcessSystem()
	fun getPostProcessPriority() = Priority.NORMAL
}