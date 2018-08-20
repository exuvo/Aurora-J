package se.exuvo.aurora.planetarysystems.systems

import com.artemis.SystemInvocationStrategy
import com.artemis.utils.Bag
import com.artemis.WorldConfigurationBuilder.Priority
import com.artemis.utils.Sort

class CustomSystemInvocationStrategy : SystemInvocationStrategy() {
	lateinit var preSystems: Bag<PreSystem>
	lateinit var postSystems: Bag<PostSystem>

	override fun initialize() {
		preSystems = Bag(PreSystem::class.java, systems.size())
		postSystems = Bag(PostSystem::class.java, systems.size())

		systems.forEach { i, data ->
			val system = data[i]

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
//		println("process")
		
		updateEntityStates()

		preSystems.forEach { i, data ->
			if (!disabled.get(i)) {
//				println("pre ${data[i]::class.simpleName}")
				data[i].preProcessSystem()
			}
		}

		systems.forEach { i, data ->
			if (!disabled.get(i)) {
//				println("pro ${data[i]::class.simpleName}")
				data[i].process()
				updateEntityStates()
			}
		}

		postSystems.forEach { i, data ->
			if (!disabled.get(i)) {
//				println("post ${data[i]::class.simpleName}")
				data[i].postProcessSystem()
			}
		}
	}
}

inline fun <E> Bag<E>.forEach(action: (index: Int, data2: Array<E>) -> Unit) {
	for (i in 0..size() - 1) {
		action(i, data)
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