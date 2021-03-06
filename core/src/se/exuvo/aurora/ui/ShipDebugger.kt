package se.exuvo.aurora.ui

import com.artemis.Component
import com.artemis.utils.Bag
import glm_.vec2.Vec2
import imgui.Col
import imgui.DataType
import imgui.ImGui
import imgui.WindowFlag
import imgui.internal.sections.ButtonFlag
import org.apache.commons.math3.util.FastMath
import se.exuvo.aurora.galactic.AdvancedMunitionHull
import se.exuvo.aurora.galactic.AmmunitionPart
import se.exuvo.aurora.galactic.Battery
import se.exuvo.aurora.galactic.BeamWeapon
import se.exuvo.aurora.galactic.CargoType
import se.exuvo.aurora.galactic.ChargedPart
import se.exuvo.aurora.galactic.DamagePattern
import se.exuvo.aurora.galactic.FueledPart
import se.exuvo.aurora.galactic.MissileLauncher
import se.exuvo.aurora.galactic.Part
import se.exuvo.aurora.galactic.PartRef
import se.exuvo.aurora.galactic.PassiveSensor
import se.exuvo.aurora.galactic.Player
import se.exuvo.aurora.galactic.PoweredPart
import se.exuvo.aurora.galactic.PoweringPart
import se.exuvo.aurora.galactic.Railgun
import se.exuvo.aurora.galactic.Resource
import se.exuvo.aurora.galactic.Shield
import se.exuvo.aurora.galactic.SimpleMunitionHull
import se.exuvo.aurora.galactic.TargetingComputer
import se.exuvo.aurora.starsystems.components.AmmunitionPartState
import se.exuvo.aurora.starsystems.components.ArmorComponent
import se.exuvo.aurora.starsystems.components.CargoComponent
import se.exuvo.aurora.starsystems.components.ChargedPartState
import se.exuvo.aurora.starsystems.components.FueledPartState
import se.exuvo.aurora.starsystems.components.HPComponent
import se.exuvo.aurora.starsystems.components.PartStatesComponent
import se.exuvo.aurora.starsystems.components.PartsHPComponent
import se.exuvo.aurora.starsystems.components.PassiveSensorState
import se.exuvo.aurora.starsystems.components.PowerComponent
import se.exuvo.aurora.starsystems.components.PoweredPartState
import se.exuvo.aurora.starsystems.components.PoweringPartState
import se.exuvo.aurora.starsystems.components.ShieldComponent
import se.exuvo.aurora.starsystems.components.ShipComponent
import se.exuvo.aurora.starsystems.components.SolarIrradianceComponent
import se.exuvo.aurora.starsystems.components.TargetingComputerState
import se.exuvo.aurora.starsystems.systems.WeaponSystem
import se.exuvo.aurora.ui.UIScreen.UIWindow
import se.exuvo.aurora.utils.Units
import se.exuvo.aurora.utils.forEachFast
import se.exuvo.aurora.utils.imgui.ShipUI
import se.exuvo.aurora.utils.printEntity
import se.unlogic.standardutils.reflection.ReflectionUtils

class ShipDebugger : UIWindow() {
	
	var useShadow = true
	var lastDebugTime = 0L
	var powerAvailiableValues = FloatArray(60)
	var powerRequestedValues = FloatArray(60)
	var powerUsedValues = FloatArray(60)
	var arrayIndex = 0
	var addResource = Resource.NUCLEAR_FISSION
	var addResourceAmount = 1
	var weaponTestDistance = 1000000.0
	var selectionIndex = 0
	var testDmgAmount = 10000L
	
	override fun draw() {
		if (visible) {
			with (ImGui) {
				with (imgui.dsl) {
		
					if (begin("Ship debug", ::visible, WindowFlag.AlwaysAutoResize.i)) {
						
						if (useShadow) {
							if (button("Shadow")) {
								useShadow = false
							}
						} else {
							if (button(" Live ")) {
								useShadow = true
							}
						}
						sameLine()
						
						val selectedEntities = Player.current.selection
		
						if (selectedEntities.isEmpty()) {
							textUnformatted("Nothing selected")
		
						} else {
							
							if (selectionIndex > selectedEntities.size() - 1) {
								selectionIndex = 0
							}
							
							sliderScalar("Selection", DataType.Int, ::selectionIndex, 0, selectedEntities.size() - 1, "${1 + selectionIndex} / ${selectedEntities.size()}", 2.0f)
							
							val entityRef =
									if (useShadow) {
										selectedEntities[selectionIndex].system.shadow.resolveEntityReference(selectedEntities[selectionIndex])
									} else {
										galaxy.resolveEntityReference(selectedEntities[selectionIndex])
									}
							
							if (entityRef != null) {
								
								val system = entityRef.system
								val world = if (useShadow) system.shadow.world else system.world
								
								//TODO toggle to select shadow or paused real
								
								val shipMapper = world.getMapper(ShipComponent::class.java)
								val powerMapper = world.getMapper(PowerComponent::class.java)
								val irradianceMapper = world.getMapper(SolarIrradianceComponent::class.java)
								val partStatesMapper = world.getMapper(PartStatesComponent::class.java)
								val shieldMapper = world.getMapper(ShieldComponent::class.java)
								val armorMapper = world.getMapper(ArmorComponent::class.java)
								val partsHPMapper = world.getMapper(PartsHPComponent::class.java)
								val hpMapper = world.getMapper(HPComponent::class.java)
								val cargoMapper = world.getMapper(CargoComponent::class.java)
			
								val entityID = entityRef.entityID
								
								val ship = shipMapper.get(entityID)
								val partStates = partStatesMapper.get(entityID)
								val shield = shieldMapper.get(entityID)
								val armor = armorMapper.get(entityID)
								val partsHP = partsHPMapper.get(entityID)
//								val hullHP = hpMapper.get(entityID)
								val cargoC = cargoMapper.get(entityID)
								
								textUnformatted("Entity ID ${entityRef.entityID} ${printEntity(entityRef.entityID, world)}")
								
								if (collapsingHeader("Components", 0)) { // TreeNodeFlag.DefaultOpen.i
		
									val components = world.componentManager.getComponentsFor(entityRef.entityID, Bag<Component>())
									components.sort({ o1, o2 ->
										o1::class.simpleName!!.compareTo(o2::class.simpleName!!)
									})
									
									components.forEachFast{ component ->
		
										if (treeNode(component::class.java.simpleName)) {
		
											val fields = ReflectionUtils.getFields(component::class.java)
											
											fields.forEachFast{ field ->
												ReflectionUtils.fixFieldAccess(field)
												
												if (Collection::class.java.isAssignableFrom(field.getType())) {
													val collection = field.get(component) as Collection<*>
													
													if (treeNode("${field.name}: ${collection.size}")) {
														for(item in collection) {
															textUnformatted("$item")
														}
														treePop()
													}
												} else {
													textUnformatted("${field.name}: ${field.get(component)}")
												}
											}
		
											treePop()
										}
									}
								}
		
								if (ship != null) {
		
									if (collapsingHeader("Parts", 0)) { // TreeNodeFlag.DefaultOpen.i
										
										sliderScalar("Weapon test range", DataType.Double, ::weaponTestDistance, 100.0, Units.AU * 1000, Units.distanceToString(weaponTestDistance.toLong()), 8.0f)
		
										ship.hull.getPartRefs().forEachFast{ partRef ->
											if (treeNode("${partRef.part::class.simpleName} ${partRef.part.name}")) {
		
												if (partRef.part is PoweringPart) {
													val state = partStates[partRef][PoweringPartState::class]
													textUnformatted("availablePower ${Units.powerToString(state.availiablePower)}")
													textUnformatted("producedPower ${Units.powerToString(state.producedPower)}")
												}
		
												if (partRef.part is PoweredPart) {
													val state = partStates[partRef][PoweredPartState::class]
													textUnformatted("requestedPower ${Units.powerToString(state.requestedPower)}")
													textUnformatted("givenPower ${Units.powerToString(state.givenPower)}")
												}
		
												if (partRef.part is ChargedPart) {
													val state = partStates[partRef][ChargedPartState::class]
													textUnformatted("charge ${Units.powerToString(state.charge)}")
													textUnformatted("expectedFullAt ${Units.secondsToString(state.expectedFullAt)}")
												}
		
												if (partRef.part is PassiveSensor) {
													val state = partStates[partRef][PassiveSensorState::class]
													textUnformatted("lastScan ${state.lastScan}")
												}
		
												if (partRef.part is AmmunitionPart) {
													val state = partStates[partRef][AmmunitionPartState::class]
													textUnformatted("type ${state.type?.name}")
													textUnformatted("amount ${state.amount}/${partRef.part.ammunitionAmount}")
													textUnformatted("reloadedAt ${Units.secondsToString(state.reloadedAt)}")
												}
		
												if (partRef.part is FueledPart) {
													val state = partStates[partRef][FueledPartState::class]
													textUnformatted("fuelEnergyRemaining ${state.fuelEnergyRemaining}")
													textUnformatted("totalFuelEnergyRemaining ${state.totalFuelEnergyRemaining}")
												}
		
												if (partRef.part is TargetingComputer) {
													
													val state = partStates[partRef][TargetingComputerState::class]
													textUnformatted("target ${state.target?.entityID}")
													textUnformatted("lockCompletionAt ${state.lockCompletionAt}")
													
													if (treeNode("linkedWeapons ${state.linkedWeapons.size()}###linked")) {
														for(weaponRef in state.linkedWeapons) {
															textUnformatted("$weaponRef")
														}
														treePop()
													}
													
													if (treeNode("readyWeapons ${state.readyWeapons.size()}###ready")) {
														for(weaponRef in state.readyWeapons) {
															textUnformatted("$weaponRef")
														}
														treePop()
													}
													
													if (treeNode("chargingWeapons ${state.chargingWeapons.size}###charging")) {
														for(weaponRef in state.chargingWeapons) {
															textUnformatted("$weaponRef")
														}
														treePop()
													}
													
													if (treeNode("reloadingWeapons ${state.reloadingWeapons.size}###reloading")) {
														for(weaponRef in state.reloadingWeapons) {
															textUnformatted("$weaponRef")
														}
														treePop()
													}
													
													if (treeNode("disabledWeapons ${state.disabledWeapons.size()}###disabled")) {
														for(weaponRef in state.disabledWeapons) {
															textUnformatted("$weaponRef")
														}
														treePop()
													}
													
												} else if (partRef.part is BeamWeapon) {
													
													val weaponTestDistanceL = weaponTestDistance.toLong()
													
													textUnformatted("radialDivergence ${partRef.part.getRadialDivergence() * 1000} mrad")
													textUnformatted("beamRadiusAtDistance ${partRef.part.getBeamRadiusAtDistance(weaponTestDistanceL)} m")
													textUnformatted("beamArea ${partRef.part.getBeamArea(weaponTestDistanceL)} m²")
													textUnformatted("deliveredEnergyTo1MSquareAtDistance ${Units.powerToString(partRef.part.getDeliveredEnergyTo1MSquareAtDistance(weaponTestDistanceL))}")
													
													val projectileSpeed = Units.C * 1000
													val timeToIntercept = FastMath.ceil(weaponTestDistance / projectileSpeed).toLong()
													val galacticTime = timeToIntercept + galaxy.time
													val galacticDays = (galacticTime / (60 * 60 * 24)).toInt()
													
													textUnformatted("projectileSpeed $projectileSpeed m/s")
													textUnformatted("timeToIntercept ${timeToIntercept} s, at ${Units.daysToDate(galacticDays)} ${Units.secondsToString(galacticTime)}")
													
												} else if (partRef.part is Railgun) {
													
													val ammoState = partStates[partRef][AmmunitionPartState::class]
													
													val munitionClass = ammoState.type as? SimpleMunitionHull
													
													if (munitionClass != null) {
													
														val projectileSpeed = (partRef.part.capacitor * partRef.part.efficiency) / (100L * munitionClass.loadedMass)
														val weaponTestDistance = weaponTestDistance.toLong()
														val timeToIntercept = FastMath.max(1, weaponTestDistance / projectileSpeed)
														val galacticTime = timeToIntercept + galaxy.time
														val galacticDays = (galacticTime / (60 * 60 * 24)).toInt()
														
														textUnformatted("projectileSpeed $projectileSpeed m/s")
														textUnformatted("timeToIntercept ${timeToIntercept} s, at ${Units.daysToDate(galacticDays)} ${Units.secondsToString(galacticTime)}")
													}
													
												} else if (partRef.part is MissileLauncher) {
													
													val ammoState = partStates[partRef][AmmunitionPartState::class]
													
													val munitionClass = ammoState.type as? AdvancedMunitionHull
													
													if (munitionClass != null) {
														
														val missileAcceleration = munitionClass.getAverageAcceleration()
														val missileLaunchSpeed = partRef.part.launchForce / munitionClass.loadedMass
														
														textUnformatted("launchSpeed $missileLaunchSpeed m/s + acceleration ${missileAcceleration} m/s²")
														
														val a: Double = missileAcceleration.toDouble() / 2
														val b: Double = missileLaunchSpeed.toDouble()
														val c: Double = -weaponTestDistance
														
														val timeToIntercept = FastMath.ceil(WeaponSystem.getPositiveRootOfQuadraticEquation(a, b, c)).toLong()
														
														val galacticTime = timeToIntercept + galaxy.time
														val galacticDays = (galacticTime / (60 * 60 * 24)).toInt()
														val impactVelocity = missileLaunchSpeed + missileAcceleration * FastMath.min(timeToIntercept, munitionClass.thrustTime.toLong())
														
														textUnformatted("impactVelocity $impactVelocity m/s")
														textUnformatted("timeToIntercept $timeToIntercept s / thrustTime ${munitionClass.thrustTime} s")
														textUnformatted("interceptAt ${Units.daysToDate(galacticDays)} ${Units.secondsToString(galacticTime)}")
													}
												}
		
												treePop()
											}
										}
									}
									
									if (collapsingHeader("Health", 0)) { // TreeNodeFlag.DefaultOpen.i
										
										spacing()
										
										group {
											if (shield != null) {
												if (ShipUI.shieldBar(ship, shield)) {
													tooltip {
														ship.hull.shields.forEachFast { partRef ->
															val charge = partStates[partRef][ChargedPartState::class].charge
															val capacity = (partRef.part as Shield).capacitor
															
															val text = "${partRef.part.name} ${Units.capacityToString(charge)} / ${Units.capacityToString(capacity)}"
															
															if (!partStates.isPartEnabled(partRef)) {
																
																withStyleColor(Col.Text, ShipUI.emptyArmorColor) {
																	textUnformatted(text)
																}
																
															} else {
																textUnformatted(text)
															}
														}
													}
												}
												currentWindow.dc.cursorPos.y += 2
											}
											
											ShipUI.armor(ship, armor, { _, y, armorHP, maxArmorHP ->
												setTooltip("$armorHP / $maxArmorHP, resistance ${ship.hull.armorEnergyPerDamage[y]}")
											})
										}
										sameLine()
										group {
											if (shield != null) {
												text("%s / %s", Units.capacityToString(shield.shieldHP), Units.capacityToString(ship.hull.maxShieldHP))
											}
											textUnformatted("ArmorHP ${armor.getTotalHP()} / ${ship.hull.maxArmorHP}")
											textUnformatted("PartHP ${partsHP.totalPartHP} / ${ship.hull.maxPartHP}")
										}
										
										val sortedParts = Bag<PartRef<Part>>(ship.hull.getPartRefs().size)
										ship.hull.getPartRefs().forEachFast{ partRef ->
											sortedParts.add(partRef)
										}
										sortedParts.sort{ p1, p2 ->
											val hitChance1 = (100 * p1.part.volume) / ship.hull.volume
											val hitChance2 = (100 * p2.part.volume) / ship.hull.volume
											
											(hitChance2 - hitChance1).toInt()
										}
										
										sortedParts.forEachFast{ partRef ->
											val hitChance = (100 * partRef.part.volume) / ship.hull.volume
											textUnformatted("${partsHP.getPartHP(partRef)} / ${partRef.part.maxHealth.toInt()} ${String.format("%3d", hitChance)}% ${partRef.part}")
										}
										
										sliderScalar("Damage amount", DataType.Long, ::testDmgAmount, 0L, 1_000_000L, "$testDmgAmount", 2.5f)
										
										val buttonFlags = if (useShadow) ButtonFlag.Disabled.i else 0
										
										if (buttonEx("damage", Vec2(), buttonFlags)) {
											val weaponSystem = world.getSystem(WeaponSystem::class.java)
											weaponSystem.applyDamage(entityRef.entityID, testDmgAmount, DamagePattern.LASER)
											system.skipClearShadowChanged = true
										}
										
										if (buttonEx("kill armor", Vec2(), buttonFlags)) {
											for (y in 0 until ship.hull.armorLayers) {
												for (x in 0 until ship.hull.getArmorWidth()) {
													armor[y][x] = 0u
												}
											}
											
											system.changed(entityID, armorMapper)
											system.skipClearShadowChanged = true
										}
										
										if (buttonEx("repair", Vec2(), buttonFlags)) {
											for (y in 0 until ship.hull.armorLayers) {
												for (x in 0 until ship.hull.getArmorWidth()) {
													armor[y][x] = ship.hull.armorBlockHP[y]
												}
											}
											
											for (partRef in ship.hull.getPartRefs()) {
												partsHP.setPartHP(partRef, partRef.part.maxHealth.toInt())
											}
											
											system.changed(entityID, armorMapper, partsHPMapper)
											system.skipClearShadowChanged = true
										}
									}
		
									if (collapsingHeader("Power", 0)) {
		
										val solarIrradiance = irradianceMapper.get(entityRef.entityID)
		
										if (solarIrradiance != null) {
		
											textUnformatted("Solar irradiance ${solarIrradiance.irradiance} W/m2")
										}
		
										val powerComponent = powerMapper.get(entityRef.entityID)
		
										if (powerComponent != null) {
		
											separator()
		
											val now = System.currentTimeMillis()
		
											if (now - lastDebugTime > 500) {
												lastDebugTime = now
		
												powerAvailiableValues[arrayIndex] = powerComponent.totalAvailablePower.toFloat()
												powerRequestedValues[arrayIndex] = powerComponent.totalRequestedPower.toFloat()
												powerUsedValues[arrayIndex] = powerComponent.totalUsedPower.toFloat() / powerComponent.totalAvailablePower.toFloat()
												arrayIndex++
		
												if (arrayIndex >= 60) {
													arrayIndex = 0
												}
											}
		
											plotLines("AvailablePower", { powerAvailiableValues[(arrayIndex + it) % 60] }, 60, 0, "", 0f, Float.MAX_VALUE, Vec2(0, 50))
											plotLines("RequestedPower", { powerRequestedValues[(arrayIndex + it) % 60] }, 60, 0, "", 0f, Float.MAX_VALUE, Vec2(0, 50))
											plotLines("UsedPower", { powerUsedValues[(arrayIndex + it) % 60] }, 60, 0, "", 0f, 1f, Vec2(0, 50))
		
											if (treeNode("Producers")) {
												powerComponent.poweringParts.forEach({
													val partRef = it
													val poweringState = partStates[partRef][PoweringPartState::class]
		
													val power = if (poweringState.availiablePower == 0L) 0f else poweringState.producedPower / poweringState.availiablePower.toFloat()
		
													progressBar(power, Vec2(), "${Units.powerToString(poweringState.producedPower)}/${Units.powerToString(poweringState.availiablePower)}")
		
													sameLine(0f, style.itemInnerSpacing.x)
													textUnformatted("${partRef.part}")
		
													if (partRef is FueledPart && partRef is PoweringPart) {
		
														val fueledState = partStates[partRef][FueledPartState::class]
														val fuelRemaining = Units.secondsToString(fueledState.fuelEnergyRemaining / partRef.power)
														val totalFuelRemaining = Units.secondsToString(fueledState.totalFuelEnergyRemaining / partRef.power)
		
														textUnformatted("Fuel $fuelRemaining/$totalFuelRemaining W")
													}
		
													if (partRef.part is Battery) {
		
														val chargedState = partStates[partRef][ChargedPartState::class]
														val charge = chargedState.charge
														val maxCharge = partRef.part.capacitor
														val charged = if (maxCharge == 0L) 0f else charge / maxCharge.toFloat()
		
														progressBar(charged, Vec2(), "${Units.powerToString(charge)}/${Units.powerToString(maxCharge)}s")
		
														if (poweringState.producedPower > 0L) {
		
															sameLine(0f, style.itemInnerSpacing.x)
															textUnformatted(Units.secondsToString(charge / poweringState.producedPower))
														}
													}
												})
		
												treePop()
											}
		
											if (treeNode("Consumers")) {
												powerComponent.poweredParts.forEach({
													val part = it
													val poweredState = partStates[part][PoweredPartState::class]
		
													val power = if (poweredState.requestedPower == 0L) 0f else poweredState.givenPower / poweredState.requestedPower.toFloat()
													progressBar(power, Vec2(), "${Units.powerToString(poweredState.givenPower)}/${Units.powerToString(poweredState.requestedPower)}")
		
													sameLine(0f, style.itemInnerSpacing.x)
													textUnformatted("${part.part}")
												})
		
												treePop()
											}
										}
									}
		
									if (collapsingHeader("Cargo", 0)) { //TreeNodeFlags.DefaultOpen.i
		
										CargoType.values().forEach {
											val cargo = it
		
											val usedVolume = cargoC.getUsedCargoVolume(cargo)
											val maxVolume = cargoC.getMaxCargoVolume(cargo)
											val usedMass = cargoC.getUsedCargoMass(cargo)
											val usage = if (maxVolume == 0L) 0f else usedVolume / maxVolume.toFloat()
											progressBar(usage, Vec2(), "$usedMass kg, ${usedVolume / 1000}/${maxVolume / 1000} m³")
		
											sameLine(0f, style.itemInnerSpacing.x)
											textUnformatted(cargo.name)
		
											if (cargo == CargoType.AMMUNITION) {
												val munitions = cargoC.munitions
												if (munitions != null) {
													for (entry in munitions.entries) {
														textUnformatted("${entry.value} ${entry.key}")
													}
												}
											}
										}
		
										separator();
		
										if (beginCombo("", addResource.name)) { // The second parameter is the label previewed before opening the combo.
											for (resource in Resource.values()) {
												val isSelected = addResource == resource
		
												if (selectable(resource.name, isSelected)) {
													addResource = resource
												}
		
												if (isSelected) { // Set the initial focus when opening the combo (scrolling + for keyboard navigation support in the upcoming navigation branch)
													setItemDefaultFocus()
												}
											}
											endCombo()
										}
		
										inputScalar("kg", DataType.Int, ::addResourceAmount, 10, 100, "%d", 0)
										
										val buttonFlags = if (useShadow) ButtonFlag.Disabled.i else 0
										
										if (buttonEx("Add", Vec2(), buttonFlags)) {
											if (!cargoC.addCargo(addResource, addResourceAmount.toLong())) {
												println("Cargo does not fit")
											}
										}
		
										sameLine(0f, style.itemInnerSpacing.x)
										
										if (buttonEx("Remove", Vec2(), buttonFlags)) {
											if (cargoC.retrieveCargo(addResource, addResourceAmount.toLong()) != addResourceAmount.toLong()) {
												println("Does not have enough of specified cargo")
											}
										}
		
										if (treeNode("Each resource")) {
											Resource.values().forEach {
												val resource = it
		
												val usedVolume = cargoC.getUsedCargoVolume(resource)
												val maxVolume = cargoC.getMaxCargoVolume(resource)
												val usedMass = cargoC.getUsedCargoMass(resource)
												val usage = if (maxVolume == 0L) 0f else usedVolume / maxVolume.toFloat()
												progressBar(usage, Vec2(), "$usedMass kg, ${usedVolume / 1000}/${maxVolume / 1000} m³")
		
												sameLine(0f, style.itemInnerSpacing.x)
												textUnformatted(resource.name)
											}
		
											treePop()
										}
									}
								}
							}
						}
		
					}
					end()
				}
			}
		}
	}
}
