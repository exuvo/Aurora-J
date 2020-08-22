package se.exuvo.aurora.utils.quadtree;

/**
 * @author Dragon Energy
 * @author exuvo
 */
public class IntList {
	private int[] data;
	private int fieldsPerElement = 0;
	private int size = 0;
	private int capacity;
	private int nextFreeElement = -1;
	
	/**
	 * Creates a new list of elements which each consist of integer fields.
	 * @param fieldsPerElement specifies the number of integer fields each element has.
	 */
	IntList(int fieldsPerElement) {
		this(fieldsPerElement, 16);
	}
	
	/**
	 * Creates a new list of elements which each consist of integer fields.
	 * @param fieldsPerElement specifies the number of integer fields each element has.
	 * @param initialElementCapacity initial capacity will be fieldsPerElement * initialElementCapacity
	 */
	IntList(int fieldsPerElement, int initialElementCapacity) {
		this.fieldsPerElement = fieldsPerElement;
		capacity = fieldsPerElement * initialElementCapacity;
		data = new int[capacity];
	}
	
	/**
	 * Returns the number of elements in the list.
	 */
	public int size() {
		return size;
	}
	
	/**
	 * Returns the value of the specified field for the nth element.
	 */
	public int get(int n, int field) {
		assert n >= 0 && n < size && field >= 0 && field < fieldsPerElement;
		return data[n * fieldsPerElement + field];
	}
	
	/**
	 * Sets the value of the specified field for the nth element.
 	 */
	void set(int n, int field, int val) {
		assert n >= 0 && n < size && field >= 0 && field < fieldsPerElement;
		data[n * fieldsPerElement + field] = val;
	}
	
	/**
	 * Clears the list, making it empty.
 	 */
	void clear() {
		size = 0;
		nextFreeElement = -1;
	}
	
	/**
	 * Inserts an element to the back of the list and returns an index to it.
 	 */
	int pushBack() {
		final int new_pos = (size + 1) * fieldsPerElement;
		
		// If the list is full, we need to reallocate the buffer to make room
		// for the new element.
		if (new_pos > capacity) {
			// Use double the size for the new capacity.
			final int new_cap = new_pos * 2;
			
			// Allocate new array and copy former contents.
			int new_array[] = new int[new_cap];
			System.arraycopy(data, 0, new_array, 0, capacity);
			data = new_array;
			
			// Set the old capacity to the new capacity.
			capacity = new_cap;
		}
		return size++;
	}
	
	/**
	 * Removes the element at the back of the list.
 	 */
	void popBack() {
		// Just decrement the list size.
		assert size > 0;
		--size;
	}
	
	/**
	 * Inserts an element to a vacant position in the list and returns an index to it.
 	 */
	int insert() {
		// If there's a free index in the free list, pop that and use it.
		if (nextFreeElement != -1) {
			final int index = nextFreeElement;
			final int pos = index * fieldsPerElement;
			
			// Set the free index to the next free index.
			nextFreeElement = data[pos];
			
			// Return the free index.
			return index;
		}
		// Otherwise insert to the back of the array.
		return pushBack();
	}
	
	/**
	 * Removes the nth element in the list.
 	 */
	void erase(int n) {
		// Push the element to the free list.
		final int pos = n * fieldsPerElement;
		data[pos] = nextFreeElement;
		nextFreeElement = n;
	}
	
	void copy(IntList other) {
		assert(fieldsPerElement == other.fieldsPerElement);
		
//		if (fieldsPerElement != other.fieldsPerElement) {
//			throw new IllegalStateException(fieldsPerElement + " != " + other.fieldsPerElement);
//		}
		
		if (capacity < other.capacity) {
			data = new int[other.capacity];
			capacity = other.capacity;
		}
		
		System.arraycopy(other.data, 0, data, 0, other.capacity);
		size = other.size;
		nextFreeElement = other.nextFreeElement;
	}
}