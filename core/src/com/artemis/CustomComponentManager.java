package com.artemis;

import com.artemis.ComponentManager;
import com.artemis.ComponentMapper;
import com.artemis.ComponentType;
import se.exuvo.aurora.starsystems.StarSystem;

public class CustomComponentManager extends ComponentManager {
	
	protected StarSystem starSystem;
	
	/**
	 * Creates a new instance of {@link ComponentManager}.
	 *
	 * @param entityContainerSize
	 */
	public CustomComponentManager(int entityContainerSize, StarSystem starSystem) {
		super(entityContainerSize);
		this.starSystem = starSystem;
	}
	
	void registerComponentType(ComponentType ct, int capacity) {
		int index = ct.getIndex();
		ComponentMapper mapper = new CustomComponentMapper(ct.getType(), world, starSystem);
		mapper.components.ensureCapacity(capacity);
		mappers.set(index, mapper);
	}
}
