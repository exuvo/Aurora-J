package se.exuvo.aurora.ui

import glm_.vec2.Vec2
import imgui.ImGui
import imgui.TabBarFlag
import imgui.WindowFlag
import imgui.dsl
import imgui.or
import se.exuvo.aurora.galactic.DesignResearchJob
import se.exuvo.aurora.galactic.DiscoveryResearchJob
import se.exuvo.aurora.galactic.Player
import se.exuvo.aurora.galactic.ResearchCategory
import se.exuvo.aurora.galactic.Technology
import se.exuvo.aurora.galactic.TechnologyList
import se.exuvo.aurora.galactic.TechnologyResearchJob
import se.exuvo.aurora.ui.UIScreen.UIWindow
import se.exuvo.aurora.utils.forEachFast

class ResearchScreen : UIWindow() {

	override fun draw() {
		if (visible) {
			with(ImGui) {
				with(imgui.dsl) {
					
					val itemSpaceX = style.itemSpacing.x
					val itemSpaceY = style.itemSpacing.y
					
					window("Research", ::visible, WindowFlag.None.i) {
						
						val empire = Player.current.empire!!
						
						child("Research teams", Vec2(200, 0), true, WindowFlag.None.i) {
							
							var first = true
							
							empire.researchTeams.forEachFast { team ->
								if (!first) {
									spacing()
								}
								first = false
								
								group {
									textUnformatted(team.name)
									
									val job = team.currentJob
									indent(5f)
									
									if (job === null) {
										textUnformatted("Idle")
										
									} else {
										when (job) {
											is DiscoveryResearchJob -> textUnformatted("Discovering in ${job.category}")
											is TechnologyResearchJob -> textUnformatted("Researching ${job.tech.name}")
											is DesignResearchJob -> textUnformatted("Designing ${job.part.name}")
										}
									}
								}
								
								if (isItemHovered()) {
									tooltip {
										textUnformatted("Theoretical Theory:")
										team.theoreticalTheory.forEach { (theory, number) ->
											textUnformatted("$theory = $number")
										}
									}
								}
							}
							
							separator()
							textUnformatted("Practical Theory:")
							empire.practicalTheory.forEach { (theory, number) ->
								textUnformatted("$theory = $number")
							}
						}
						
						sameLine()
						
						child("Categories", Vec2(0, 0), false, WindowFlag.None.i) {
							if (beginTabBar("Tabs", TabBarFlag.Reorderable or TabBarFlag.TabListPopupButton or TabBarFlag.FittingPolicyResizeDown)) {
								
								ResearchCategory.values().forEachFast { category ->
									if (category.parent == null && beginTabItem(category.name)) {
										
										var firstSubCategory = true
										
										ResearchCategory.values().forEachFast { subCategory ->
											if (subCategory.parent === category) {
												
												if (!firstSubCategory) {
													sameLine()
												}
												firstSubCategory = false
												
												group {
													textUnformatted(subCategory.name)
													val techList = Technology.technologies[subCategory]
													
													if (techList != null) {
														var firstRootTech = true
														
														techLoop@ for (i in 0 until techList.sorted.size) {
															val tech = techList.sorted.data[i]
															
															for (j in 0 until tech.requirements.size()) {
																val requirement = tech.requirements.data[j]
																if (requirement.category === subCategory) {
																	continue@techLoop
																}
															}
															
															if (!firstRootTech) {
																sameLine()
															}
															firstRootTech = false
															
															group {
																printTech(tech)
																listSubTechs(tech, techList)
															}
														}
													}
												}
											}
										}
										
										endTabItem()
									}
								}
								
								endTabBar()
							}
						}
					}
				}
			}
		}
	}
	
	fun listSubTechs(tech: Technology, techList: TechnologyList) {
		with(ImGui) {
			with(imgui.dsl) {
				var firstSubTech = true
				
				for (i in 0 until techList.sorted.size()) {
					val subTech = techList.sorted.data[i]
					
					for (j in 0 until subTech.requirements.size()) {
						val requirement = subTech.requirements.data[j]
						
						if (requirement === tech) {
							
							if (!firstSubTech) {
								sameLine()
							}
							firstSubTech = false
							
							group {
								printTech(subTech)
								listSubTechs(subTech, techList)
							}
							
							break
						}
					}
				}
			}
		}
	}
	
	fun printTech(tech: Technology) {
		with(ImGui) {
			with(imgui.dsl) {
				
				//TODO replace with icon
				textUnformatted(tech.code)
				
				if (isItemHovered()) {
					tooltip {
						textUnformatted("${tech.name}")
						textUnformatted("${tech.researchPoints} tech points")
						
						textUnformatted("Requires:")
						indent(5f)
						if (tech.requirements.isEmpty) {
							textUnformatted("Nothing")
							
						} else {
							tech.requirements.forEachFast { requirement ->
								textUnformatted("${requirement.code}")
								
								var first = true
								var category: ResearchCategory? = requirement.category
								while (category != null) {
									sameLine(0f, 0f)
									if (first) {
										textUnformatted(" - ")
										first = false
									} else {
										textUnformatted(".")
									}
									
									sameLine(0f, 0f)
									textUnformatted(category.name)
									category = category.parent
								}
							}
						}
						unindent(5f)
						
						textUnformatted("${tech.description}")
					}
				}
			}
		}
	}
}
