package se.exuvo.aurora.utils

import com.badlogic.gdx.utils.Disposable
import java.util.*
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
object GameServices : Disposable {
	private val services = HashMap<KClass<*>, Any>()

	operator fun <T : Any> get(serviceClass: KClass<T>) = services[serviceClass] as T
	operator fun <T : Any> invoke(serviceClass: KClass<T>) = services[serviceClass] as? T
	
	operator fun <T : Any> plus(service: T) = put(service)

	fun <T : Any> put(service: T) {
		services[service::class] = service
	}

	fun <T : Any> put(service: T, savedClass: KClass<T>) {
		services[savedClass] = service
	}

	override fun dispose() {
		services.map { it.value as? Disposable }.filterNotNull().forEach { it.dispose() }
		services.clear()
	}
}

@Suppress("UNCHECKED_CAST")
class Storage: Disposable, Iterable<Disposable> {
	private val storage = HashMap<KClass<*>, Disposable>()

	operator fun <T : Disposable> get(storageClass: KClass<T>) = storage[storageClass] as T
	operator fun <T : Disposable> invoke(storageClass: KClass<T>) = storage[storageClass] as? T
	
	operator fun <T : Disposable> plus(data: T) = put(data)

	fun <T : Disposable> put(data: T) {
		storage[data::class] = data
	}

	fun <T : Disposable> put(data: T, savedClass: KClass<T>) {
		storage[savedClass] = data
	}
	
	override fun iterator() = storage.values.iterator()

	override fun dispose() {
		storage.values.forEach { it.dispose() }
		storage.clear()
	}
}