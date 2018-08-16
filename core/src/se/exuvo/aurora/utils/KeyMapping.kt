package se.exuvo.aurora.utils.keys

import com.badlogic.gdx.utils.Disposable
import java.util.*
import kotlin.reflect.KClass
import com.badlogic.gdx.InputProcessor
import se.exuvo.settings.Settings
import org.w3c.dom.Element
import org.apache.log4j.Logger
import se.exuvo.aurora.screens.PlanetarySystemScreen
import se.unlogic.standardutils.reflection.ReflectionUtils

@Suppress("UNCHECKED_CAST")
object KeyMappings {
	val log = Logger.getLogger(this.javaClass)
	private val keyActions = HashMap<KClass<out InputProcessor>, Array<out KeyAction>>()
	private val keyRawMaps = HashMap<KClass<InputProcessor>, MutableMap<Char, KeyMapping>>()
	private val keyTranslatedMaps = HashMap<KClass<InputProcessor>, MutableMap<Char, KeyMapping>>()

	fun getRaw(char: Char, inputProcessor: KClass<InputProcessor>) = keyRawMaps[inputProcessor]?.get(char)
	fun getTranslated(char: Char, inputProcessor: KClass<InputProcessor>) = keyTranslatedMaps[inputProcessor]?.get(char)

	fun put(inputProcessor: KClass<InputProcessor>, mapping: KeyMapping) {

		var map: MutableMap<Char, KeyMapping>?

		if (mapping.type == KeyPressType.RAW) {
			map = keyRawMaps[inputProcessor]
		} else {
			map = keyTranslatedMaps[inputProcessor]
		}

		if (map == null) {
			map = HashMap<Char, KeyMapping>();

			if (mapping.type == KeyPressType.RAW) {
				keyRawMaps[inputProcessor] = map
			} else {
				keyTranslatedMaps[inputProcessor] = map
			}
		}

		map[mapping.key] = mapping
	}

	fun load() {
		log.info("Loading keybinds")

		val keysElement = Settings.getNode("Keybinds")

		if (keysElement != null) {

			var loadedKeys = 0
			val children = keysElement.getChildNodes()

			for (a in 0..children.length - 1) {
				val node2 = children.item(a)
				val children2 = node2.getChildNodes()
				val processorName = node2.nodeName

				for (b in 0..children2.length - 1) {
					val node3 = children2.item(b)
					val children3 = node3.getChildNodes()
					val enumName = node3.nodeName

					for (c in 0..children3.length - 1) {
						val node4 = children3.item(c)
						val children4 = node4.getChildNodes()
						val type = node4.nodeName

						for (d in 0..children4.length - 1) {
							val node5 = children4.item(d)

							if (!"#text".equals(node5.nodeName)) {

								val action = node5.nodeName
								val key = node5.textContent

//								println("Key $processorName, $enumName, $type, action $action, key $key")

								val inputProcessor = Class.forName(processorName)
								val enumClass = Class.forName("se.exuvo.aurora.utils.keys." + enumName)
								val keyType = KeyPressType.valueOf(type)
								val enumValueOf = ReflectionUtils.getMethod(enumClass, "valueOf", enumClass, String::class.java)
								val enum = enumValueOf.invoke(null, action)

//								println(" Parsed $inputProcessor, $enumClass, $keyType, $enum")

								put(inputProcessor.kotlin as KClass<InputProcessor>, KeyMapping(enum as KeyAction, key[0], keyType))
								loadedKeys++
							}
						}
					}
				}
			}
			
			println("Loaded $loadedKeys key bindings")
			
		} else {
			
			for (entry in keyActions.entries) {
				val inputProcessor = entry.key

				for (action in entry.value) {

					if (action.defaultKey != null) {
						put(inputProcessor as KClass<InputProcessor>, KeyMapping(action, action.defaultKey!!, action.defaultType))
					}
				}
			}
		}
	}

	private fun saveKeyMap(keyMap: HashMap<KClass<InputProcessor>, MutableMap<Char, KeyMapping>>) {
		for (entry in keyMap.entries) {
			for (mappings in entry.value) {
				val processor = entry.key
				val key = mappings.key
				val mapping = mappings.value
				val enumClass = mapping.action::class.simpleName

				Settings.set("Keybinds/${processor.java.name}/$enumClass/${mapping.type.name}/${mapping.action.name}", key.toString())
			}
		}
	}

	fun save() {
		log.info("Saving keybinds")

		Settings.remove("Keybinds")

		saveKeyMap(keyRawMaps)
		saveKeyMap(keyTranslatedMaps)
	}

	init {
		keyActions[PlanetarySystemScreen::class] = KeyActions_PlanetarySystem.values()
	}
}

class KeyMapping(val action: KeyAction, val key: Char, val type: KeyPressType)

enum class KeyPressType {
	RAW,
	TRANSLATED
}

interface KeyAction {
	val defaultKey: Char?
	val defaultType: KeyPressType
	val name: String
}

enum class KeyActions_PlanetarySystem(override val defaultKey: Char? = null,
																			override val defaultType: KeyPressType = KeyPressType.TRANSLATED
) : KeyAction {
	ZOOM_IN('+'),
	ZOOM_OUT('-'),
	DEBUG('§', KeyPressType.RAW)
	;
}
