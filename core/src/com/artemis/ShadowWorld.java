package com.artemis;

import com.artemis.injection.CachedInjector;
import com.artemis.injection.Injector;
import com.artemis.utils.Bag;
import com.artemis.utils.ImmutableBag;
import com.artemis.utils.IntBag;

import java.util.*;

import static com.artemis.WorldConfiguration.ASPECT_SUBSCRIPTION_MANAGER_IDX;
import static com.artemis.WorldConfiguration.COMPONENT_MANAGER_IDX;
import static com.artemis.WorldConfiguration.ENTITY_MANAGER_IDX;

/**
 * The primary instance for the framework.
 * <p>
 * It contains all the systems. You must use this to create, delete and
 * retrieve entities. It is also important to set the delta each game loop
 * iteration, and initialize before game loop.
 * </p>
 * @author Arni Arent
 * @author junkdog
 */
public class ShadowWorld {
	
	/** Manages all entities for the world. */
	private final EntityManager em;
	
	/** Manages all component-entity associations for the world. */
	private final ComponentManager cm;
	
	/**
	 * Creates a world without custom systems.
	 * <p>
	 * {@link com.artemis.EntityManager}, {@link ComponentManager} and {@link AspectSubscriptionManager} are
	 * available by default.
	 * </p>
	 * Why are you using this? Use {@link #World(WorldConfiguration)} to create a world with your own systems.
	 */
	public ShadowWorld() {
		this(new WorldConfiguration());
	}
	
	/**
	 * Creates a new world.
	 * <p>
	 * {@link com.artemis.EntityManager}, {@link ComponentManager} and {@link AspectSubscriptionManager} are
	 * available by default, on top of your own systems.
	 * </p>
	 * @see WorldConfigurationBuilder
	 * @see WorldConfiguration
	 */
	public ShadowWorld(WorldConfiguration configuration) {
		cm = new ComponentManager(configuration.expectedEntityCount());
		em = new EntityManager(configuration.expectedEntityCount());
		
		configuration.initialize(this, partition.injector, asm);
	}
	
	/**
	 * Disposes all systems. Only necessary if either need to free
	 * managed resources upon bringing the world to an end.
	 * @throws ArtemisMultiException
	 * 		if any system throws an exception.
	 */
	public void dispose() {
		List<Throwable> exceptions = new ArrayList<Throwable>();
		
		try {
			em.dispose();
		} catch (Exception e) {
			exceptions.add(e);
		}
		
		try {
			cm.dispose();
		} catch (Exception e) {
			exceptions.add(e);
		}
		
		if (exceptions.size() > 0)
			throw new ArtemisMultiException(exceptions);
	}
	
	/**
	 * Gets the <code>composition id</code> uniquely identifying the
	 * component composition of an entity. Each composition identity maps
	 * to one unique <code>BitVector</code>.
	 *
	 * @param entityId Entity for which to get the composition id
	 * @return composition identity of entity
	 */
	public int compositionId(int entityId) {
		return cm.getIdentity(entityId);
	}
	
	/**
	 * Returns a manager that takes care of all the entities in the world.
	 * @return entity manager
	 */
	public EntityManager getEntityManager() {
		return em;
	}
	
	/**
	 * Returns a manager that takes care of all the components in the world.
	 * @return component manager
	 */
	public ComponentManager getComponentManager() {
		return cm;
	}
	
	/**
	 * Delete the entity from the world.
	 *
	 * The entity is considered to be in a final state once invoked;
	 * adding or removing components from an entity scheduled for
	 * deletion will likely throw exceptions.
	 *
	 * @param entityId
	 * 		the entity to delete
	 */
	public void delete(int entityId) {
		batchProcessor.delete(entityId);
	}
	
	/**
	 * Create and return a new or reused entity id. Entity is
	 * automatically added to the world.
	 *
	 * @return assigned entity id, where id >= 0.
	 */
	public int create() {
		int entityId = em.create();
		return entityId;
	}
	
	/**
	 * Create and return an {@link Entity} wrapping a new or reused entity instance.
	 * Entity is automatically added to the world.
	 *
	 * Use {@link Entity#edit()} to set up your newly created entity.
	 *
	 * You can also create entities using:
	 * - {@link com.artemis.utils.EntityBuilder} Convenient entity creation. Not useful when pooling.
	 * - {@link com.artemis.Archetype} Fastest, low level, no parameterized components.
	 *
	 * @return assigned entity id
	 */
	public int create(Archetype archetype) {
		int entityId = em.create();
		
		archetype.transmuter.perform(entityId);
		cm.setIdentity(entityId, archetype.compositionId);
		
		return entityId;
	}
	
	/**
	 * Retrieves a ComponentMapper instance for fast retrieval of components
	 * from entities.
	 *
	 * Odb automatically injects component mappers into systems, calling this
	 * method is usually not required.,
	 *
	 * @param <T>
	 * 		class type of the component
	 * @param type
	 * 		type of component to get mapper for
	 * @return mapper for specified component type
	 */
	public <T extends Component> ComponentMapper<T> getMapper(Class<T> type) {
		return cm.getMapper(type);
	}
}
