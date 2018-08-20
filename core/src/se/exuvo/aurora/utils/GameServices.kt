package se.exuvo.aurora.utils

import com.badlogic.gdx.utils.Disposable
import java.util.*
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
object GameServices : Disposable {
	private val services = HashMap<KClass<*>, Any>()

	operator fun <T : Any> get(serviceClass: KClass<T>) = services[serviceClass] as T
	fun <T : Any> tryGet(serviceClass: KClass<T>) = services[serviceClass] as? T

	fun put(service: Any) {
		services[service::class] = service
	}

	fun put(service: Any, savedClass: KClass<*>) {
		services[savedClass] = service
	}

	override fun dispose() {
		services.map { it.value as? Disposable }.filterNotNull().forEach { it.dispose() }
		services.clear()
	}
}