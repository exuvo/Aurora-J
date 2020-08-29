package se.exuvo.aurora.utils

import com.artemis.World
import com.artemis.utils.Bag
import com.artemis.utils.ImmutableBag
import com.artemis.utils.IntBag
import org.apache.logging.log4j.LogManager
import org.jasypt.digest.StandardStringDigester
import se.exuvo.aurora.starsystems.components.NameComponent
import se.exuvo.aurora.starsystems.components.UUIDComponent
import kotlin.reflect.KProperty0
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

private val utilLog = LogManager.getLogger("se.exuvo.aurora.utils")

fun clamp(value: Float, min: Float, max: Float) = value.coerceIn(min, max)
fun clamp(value: Double, min: Double, max: Double) = value.coerceIn(min, max)
fun clamp(value: Int, min: Int, max: Int) = value.coerceIn(min, max)
fun clamp(value: Long, min: Long, max: Long) = value.coerceIn(min, max)

inline fun IntBag.forEachFast(action: (entityID: Int) -> Unit) {
	for (i in 0 until size()) {
		action(data[i])
	}
}

inline fun IntBag.forEachFast(action: (index: Int, entityID: Int) -> Unit) {
	for (i in 0 until size()) {
		action(i, data[i])
	}
}

inline fun <T> Bag<T>.forEachFast(action: (T) -> Unit) {
	for (i in 0 until size()) {
		action(data[i])
	}
}

inline fun <T> Bag<T>.forEachFast(action: (index: Int, T) -> Unit) {
	for (i in 0 until size()) {
		action(i, data[i])
	}
}

inline fun <T> ImmutableBag<T>.forEachFast(action: (T) -> Unit) {
	for (i in 0 until size()) {
		action(get(i))
	}
}

inline fun <T> ImmutableBag<T>.forEachFast(action: (index: Int, T) -> Unit) {
	for (i in 0 until size()) {
		action(i, get(i))
	}
}

inline fun <T> List<T>.forEachFast(block: (T) -> Unit) {
	for (i in 0 until size) {
		block(this[i])
	}
}

inline fun <T> List<T>.forEachFast(block: (index: Int, T) -> Unit) {
	for (i in 0 until size) {
		block(i, this[i])
	}
}

inline fun <T> Array<T>.forEachFast(block: (T) -> Unit) {
	for (i in 0 until size) {
		block(this[i])
	}
}

inline fun <T> Array<T>.forEachFast(block: (index: Int, T) -> Unit) {
	for (i in 0 until size) {
		block(i, this[i])
	}
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T> Bag<T>.isNotEmpty(): Boolean = !isEmpty()

operator fun <T> Bag<T>.plusAssign(element: T): Unit  {
	add(element)
}

fun getUUID(entityID: Int, world: World): String {

	return world.getMapper(UUIDComponent::class.java).get(entityID)?.uuid.toString();
}

fun getEntityName(entityID: Int, world: World): String {

	val nameComponent = world.getMapper(NameComponent::class.java).get(entityID)

	if (nameComponent != null) {
		return nameComponent.name
	}

	return "";
}

fun printEntity(entityID: Int, world: World): String {

	return "${getEntityName(entityID, world)} (${getUUID(entityID, world)})"
}

inline fun <reified T> T.callPrivateFunc(name: String, vararg args: Any?): Any? =
	T::class
		.declaredMemberFunctions
		.firstOrNull { it.name == name }
		?.apply { isAccessible = true }
		?.call(this, *args)

inline fun <reified T : Any, R> T.getPrivateProperty(name: String): R? =
	T::class
		.memberProperties
		.firstOrNull { it.name == name }
		?.apply { isAccessible = true }
		?.get(this) as? R

inline fun <T> Bag<T>.sumByLong(selector: (T) -> Long): Long {
    var sum = 0L
		for (i in 0 .. size() - 1) {
			sum += selector(data[i])
		}
    return sum
}

inline fun <T> List<T>.sumByLong(selector: (T) -> Long): Long {
    var sum = 0L
		for (i in 0 .. size - 1) {
			sum += selector(this[i])
		}
    return sum
}

inline fun <T> Iterable<T>.sumByLong(selector: (T) -> Long): Long {
    var sum = 0L
    for (element in this) {
        sum += selector(element)
    }
    return sum
}

//fun KProperty0<*>.resetDelegate() {
//	isAccessible = true
//	(getDelegate() as ResetableLazy).reset()
//}

inline fun <reified R> KProperty0<*>.delegateAs(): R {
	isAccessible = true
	return getDelegate() as R
}

//fun ResetableLazy(init: () -> Int): ResetableLazyInt {
//	return ResetableLazyInt(init)
//}
//
//fun ResetableLazy(init: () -> Long): ResetableLazyLong {
//	return ResetableLazyLong(init)
//}
//
//fun <T: Any> ResetableLazy(init: () -> T): ResetableLazyGeneric<T> {
//	return ResetableLazyGeneric<T>(init)
//}
//
//interface ResetableLazy {
//	fun reset()
//}
//
//class ResetableLazyGeneric<out T>(private val initializer: () -> T): Lazy<T>, ResetableLazy {
//	private var _value: Any? = null
//
//	override val value: T
//		get() {
//			if (_value == null) {
//				_value = initializer()
//			}
//			@Suppress("UNCHECKED_CAST")
//			return _value as T
//		}
//	
//	override fun isInitialized(): Boolean = _value != null
//
//	override fun toString(): String = if (isInitialized()) value.toString() else "Lazy value not initialized yet."
//	
//	override fun reset() {
//		_value = null
//	}
//}
//
//class ResetableLazyInt(private val initializer: () -> Int): ResetableLazy {
//	private var _value: Int = 0
//	private var initialized = false
//
//	operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
//			if (!initialized) {
//				_value = initializer()
//			}
//			return _value
//		}
//	
//	override fun toString(): String = if (initialized) _value.toString() else "Lazy value not initialized yet."
//	
//	override fun reset() {
//		initialized = false
//	}
//}
//
//class ResetableLazyLong(private val initializer: () -> Long): ResetableLazy {
//	private var _value: Long = 0
//	private var initialized = false
//
//	operator fun getValue(thisRef: Any?, property: KProperty<*>): Long {
//			if (!initialized) {
//				_value = initializer()
//			}
//			return _value
//		}
//	
//	override fun toString(): String = if (initialized) _value.toString() else "Lazy value not initialized yet."
//	
//	override fun reset() {
//		initialized = false
//	}
//}

fun exponentialAverage(newValue: Double, expAverage: Double, delay: Double) : Double = newValue + Math.pow(Math.E, -1/delay) * (expAverage - newValue)

object GameUtils {

	public val stringDigester: StandardStringDigester

	init {
		stringDigester = StandardStringDigester()
		stringDigester.initialize()
	}
}