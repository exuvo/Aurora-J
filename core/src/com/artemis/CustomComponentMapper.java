package com.artemis;

import se.exuvo.aurora.starsystems.StarSystem;

public class CustomComponentMapper<A extends Component> extends ComponentMapper<A> {
	
	protected StarSystem starSystem;
	
	public CustomComponentMapper(Class type, World world, StarSystem starSystem) {
		super(type, world);
		this.starSystem = starSystem;
	}
	
	@Override
	public void remove(int entityID) {
		A component = get(entityID);
		if (component != null) {
			removeTransmuter.transmuteNoOperation(entityID);
			purgatory.mark(entityID);
			
			starSystem.changed(entityID, type.getIndex());
		}
	}
	
	@Override
	protected void internalRemove(int entityID) { // triggers no composition id update
		A component = get(entityID);
		if (component != null) {
			purgatory.mark(entityID);
			
			starSystem.changed(entityID, type.getIndex());
		}
	}
	
	@Override
	public A create(int entityID) {
		A component = get(entityID);
		if (component == null || purgatory.unmark(entityID)) {
			// running transmuter first, as it performs som validation
			createTransmuter.transmuteNoOperation(entityID);
			component = createNew();
			components.unsafeSet(entityID, component);
			
			starSystem.changed(entityID, type.getIndex());
		}
		
		return component;
	}
	
	@Override
	public A internalCreate(int entityID) {
		A component = get(entityID);
		if (component == null || purgatory.unmark(entityID)) {
			component = createNew();
			components.unsafeSet(entityID, component);
			
			starSystem.changed(entityID, type.getIndex());
		}
		
		return component;
	}
}
