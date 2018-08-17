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
import com.badlogic.gdx.Input
import se.exuvo.aurora.screens.ImGuiScreen
import se.exuvo.aurora.screens.GalaxyScreen

@Suppress("UNCHECKED_CAST")
object KeyMappings {
	val log = Logger.getLogger(this.javaClass)
	@JvmField
	var loaded = false
	
	private val keyActions = HashMap<KClass<out InputProcessor>, Array<out KeyAction>>()
	private val keyRawMaps = HashMap<KClass<InputProcessor>, MutableMap<Int, KeyMapping>>()
	private val keyTranslatedMaps = HashMap<KClass<InputProcessor>, MutableMap<Char, KeyMapping>>()

	fun getRaw(keycode: Int, inputProcessor: KClass<out InputProcessor>) = keyRawMaps[inputProcessor]?.get(keycode)?.action
	fun getTranslated(char: Char, inputProcessor: KClass<out InputProcessor>) = keyTranslatedMaps[inputProcessor]?.get(char.toUpperCase())?.action

	fun put(inputProcessor: KClass<InputProcessor>, mapping: KeyMapping) {

		if (mapping.type == KeyPressType.RAW) {
			
			var map = keyRawMaps[inputProcessor]

			if (map == null) {
				map = HashMap<Int, KeyMapping>();
				keyRawMaps[inputProcessor] = map
			}
			
			map[mapping.key] = mapping

		} else {
			
			var map = keyTranslatedMaps[inputProcessor]

			if (map == null) {
				map = HashMap<Char, KeyMapping>();
				keyTranslatedMaps[inputProcessor] = map
			}
			
			map[mapping.key.toChar()] = mapping
		}
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

								if (keyType == KeyPressType.RAW) {
									put(inputProcessor.kotlin as KClass<InputProcessor>, KeyMapping(enum as KeyAction, Input.Keys.valueOf(key), KeyPressType.RAW))
									
								} else {
									put(inputProcessor.kotlin as KClass<InputProcessor>, KeyMapping(enum as KeyAction, key[0].toInt(), KeyPressType.TRANSLATED))
								}
								
								loadedKeys++
							}
						}
					}
				}
			}

			log.info("Loaded $loadedKeys key bindings")

		} else {

			log.info("Found no saved key bindings, using default")
			
			for (entry in keyActions.entries) {
				val inputProcessor = entry.key

				for (action in entry.value) {

					if (action.defaultKey != null) {
						put(inputProcessor as KClass<InputProcessor>, KeyMapping(action, action.defaultKey!!, action.defaultType))
					}
				}
			}
		}
		
		loaded = true
	}

	@JvmStatic
	fun save() {
		log.info("Saving keybinds")

		val keysElement = Settings.getNode("Keybinds")

		if (keysElement != null) {
			val children = keysElement.getChildNodes()
			
			for (a in 0..children.length - 1) {
				keysElement.removeChild(children.item(0))
			}
		}

		for (entry in keyRawMaps.entries) {
			for (mappings in entry.value) {
				val processor = entry.key
				val key = mappings.key
				val mapping = mappings.value
				val enumClass = mapping.action::class.simpleName

				Settings.set("Keybinds/${processor.java.name}/$enumClass/${mapping.type.name}/${mapping.action.name}", Input.Keys.toString(key))
			}
		}
		
		for (entry in keyTranslatedMaps.entries) {
			for (mappings in entry.value) {
				val processor = entry.key
				val key = mappings.key
				val mapping = mappings.value
				val enumClass = mapping.action::class.simpleName

				Settings.set("Keybinds/${processor.java.name}/$enumClass/${mapping.type.name}/${mapping.action.name}", key.toChar().toString())
			}
		}
	}

	init {
		keyActions[PlanetarySystemScreen::class] = KeyActions_PlanetarySystemScreen.values()
		keyActions[ImGuiScreen::class] = KeyActions_ImGuiScreen.values()
		keyActions[GalaxyScreen::class] = KeyActions_GalaxyScreen.values()
	}
}

class KeyMapping(val action: KeyAction, val key: Int, val type: KeyPressType)

enum class KeyPressType {
	RAW, //keycode
	TRANSLATED //char
}

interface KeyAction {
	val defaultKey: Int?
	val defaultType: KeyPressType
	val name: String
}

enum class KeyActions_ImGuiScreen(override val defaultKey: Int? = null,
																			override val defaultType: KeyPressType
) : KeyAction {
	DEBUG(Input.Keys.GRAVE),
	;
	
	constructor (char: Char): this(char.toInt(), KeyPressType.TRANSLATED)
	constructor (keyCode: Int): this(keyCode, KeyPressType.RAW)
}

enum class KeyActions_PlanetarySystemScreen(override val defaultKey: Int? = null,
																			override val defaultType: KeyPressType
) : KeyAction {
	SPEED_UP('+'),
	SPEED_DOWN('-'),
	GENERATE_SYSTEM('G'),
	PAUSE(Input.Keys.SPACE),
	MAP('M'),
	ATTACK('A'),
	;
	
	constructor (char: Char): this(char.toInt(), KeyPressType.TRANSLATED)
	constructor (keyCode: Int): this(keyCode, KeyPressType.RAW)
}

enum class KeyActions_GalaxyScreen(override val defaultKey: Int? = null,
																			override val defaultType: KeyPressType
) : KeyAction {
	SPEED_UP('+'),
	SPEED_DOWN('-'),
	PAUSE(Input.Keys.SPACE),
	MAP('M'),
	;
	
	constructor (char: Char): this(char.toInt(), KeyPressType.TRANSLATED)
	constructor (keyCode: Int): this(keyCode, KeyPressType.RAW)
}
