package se.exuvo.aurora.goap.interfaces;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import net.mostlyoriginal.api.utils.pooling.PoolsCollection;

public class ReGoapState<T, W> {

	// can change to object
	private Map<T, W> values;
	private final Map<T, W> bufferA;
	private final Map<T, W> bufferB;

	public static int DefaultSize = 20;

	private ReGoapState() {
		bufferA = new HashMap<T, W>(DefaultSize);
		bufferB = new HashMap<T, W>(DefaultSize);
		values = bufferA;
	}

	private ReGoapState<T, W> init(ReGoapState<T, W> old) {
		values.clear();
		if (old != null) {
			synchronized (old.values) {
				for (Map.Entry<T, W> pair : old.getValues().entrySet()) {
					values.put(pair.getKey(), pair.getValue());
				}
			}
		}
		return this;
	}

	public ReGoapState<T, W> add(ReGoapState<T, W> a, ReGoapState<T, W> b) {
		ReGoapState<T, W> result;
		synchronized (a.values) {
			result = instantiate(a);
		}
		synchronized (b.values) {
			for (Map.Entry<T, W> pair : b.values.entrySet())
				result.values.put(pair.getKey(), pair.getValue());
			return result;
		}
	}

	public void addFromState(ReGoapState<T, W> b) {
		synchronized (values) {
			synchronized (b.values) {
				for (Map.Entry<T, W> pair : b.values.entrySet())
					values.put(pair.getKey(), pair.getValue());
			}
		}
	}

	public int getSize() {
		return values.size();
	}

	public boolean hasAny(ReGoapState<T, W> other) {
		synchronized (values) {
			synchronized (other.values) {
				for (Map.Entry<T, W> pair : other.values.entrySet()) {
					W thisValue = values.get(pair.getKey());
					if (pair.getValue().equals(thisValue)) return true;
				}
				return false;
			}
		}
	}

	public boolean hasAnyConflict(ReGoapState<T, W> other) // used only in backward for now
	{
		synchronized (values) {
			synchronized (other.values) {
				for (Map.Entry<T, W> pair : other.values.entrySet()) {
					W otherValue = pair.getValue();

					// not here, ignore this check
					W thisValue = values.get(pair.getKey());
					if (thisValue == null) continue;
					if (!otherValue.equals(thisValue)) return true;
				}
				return false;
			}
		}
	}

	// this method is more relaxed than the other, also accepts conflits that are fixed by "changes"
	public boolean hasAnyConflict(ReGoapState<T, W> changes, ReGoapState<T, W> other) {
		synchronized (values) {
			synchronized (other.values) {
				for (Map.Entry<T, W> pair : other.values.entrySet()) {
					W otherValue = pair.getValue();

					// not here, ignore this check
					W thisValue = values.get(pair.getKey());
					if (thisValue == null) continue;
					W effectValue = changes.values.get(pair.getKey());
					if (!otherValue.equals(thisValue) && !thisValue.equals(effectValue)) return true;
				}
				return false;
			}
		}
	}

	public int missingDifference(ReGoapState<T, W> other) {
		return missingDifference(other, Integer.MAX_VALUE);
	}

	public int missingDifference(ReGoapState<T, W> other, int stopAt) {
		synchronized (values) {
			int count = 0;

			for (Map.Entry<T, W> pair : values.entrySet()) {
				W otherValue = other.values.get(pair.getKey());
				if (!pair.getValue().equals(otherValue)) {
					count++;
					if (count >= stopAt) break;
				}
			}
			return count;
		}

	}

	public int missingDifference(ReGoapState<T, W> other, ReGoapState<T, W> difference) {
		return missingDifference(other, Integer.MAX_VALUE);
	}

	public int missingDifference(ReGoapState<T, W> other, ReGoapState<T, W> difference, int stopAt) {
		return missingDifference(other, difference, stopAt, null);
	}

	public int missingDifference(ReGoapState<T, W> other, ReGoapState<T, W> difference, int stopAt, BiFunction<Map.Entry<T, W>, W, Boolean> predicate) {
		return missingDifference(other, difference, stopAt, predicate, false);
	}

	// write differences in "difference"
	public int missingDifference(ReGoapState<T, W> other, ReGoapState<T, W> difference, int stopAt, BiFunction<Map.Entry<T, W>, W, Boolean> predicate, boolean test) {
		synchronized (values) {
			int count = 0;

			for (Map.Entry<T, W> pair : values.entrySet()) {
				W otherValue = other.values.get(pair.getKey());
				if (!pair.getValue().equals(otherValue) && (predicate == null || predicate.apply(pair, otherValue))) {
					count++;
					if (difference != null) difference.values.put(pair.getKey(), pair.getValue());
					if (count >= stopAt) break;
				}
			}
			return count;
		}

	}

	public int replaceWithMissingDifference(ReGoapState<T, W> other) {
		return replaceWithMissingDifference(other, Integer.MAX_VALUE);
	}

	public int replaceWithMissingDifference(ReGoapState<T, W> other, int stopAt) {
		return replaceWithMissingDifference(other, stopAt, null);
	}

	public int replaceWithMissingDifference(ReGoapState<T, W> other, int stopAt, BiFunction<Map.Entry<T, W>, W, Boolean> predicate) {
		return replaceWithMissingDifference(other, stopAt, predicate, false);
	}

	// keep only missing differences in values
	public int replaceWithMissingDifference(ReGoapState<T, W> other, int stopAt, BiFunction<Map.Entry<T, W>, W, Boolean> predicate, boolean test) {
		synchronized (values) {
			int count = 0;
			Map<T, W> buffer = values;
			values = values == bufferA ? bufferB : bufferA;
			values.clear();

			for (Map.Entry<T, W> pair : buffer.entrySet()) {
				W otherValue = other.values.get(pair.getKey());
				if (!pair.getValue().equals(otherValue) && (predicate == null || predicate.apply(pair, otherValue))) {
					count++;
					values.put(pair.getKey(), pair.getValue());
					if (count >= stopAt) break;
				}
			}
			return count;
		}

	}

	public ReGoapState<T, W> clone() {
		return instantiate(this);
	}

	private static PoolsCollection pools = new PoolsCollection();

	public void recycle() {
		pools.free(this);
	}

	public static <T, W> ReGoapState<T, W> instantiate() {
		return instantiate(null);
	}

	@SuppressWarnings("unchecked")
	public static <T, W> ReGoapState<T, W> instantiate(ReGoapState<T, W> old) {
		return pools.obtain(ReGoapState.class).init(old);
	}

	public String toString() {
		synchronized (values) {
			String result = "";
			for (Map.Entry<T, W> pair : values.entrySet())
				result += String.format("'{0}': {1}, ", pair.getKey(), pair.getValue());
			return result;
		}
	}

	public W get(T key) {
		synchronized (values) {
			return values.get(key);
		}
	}

	public void set(T key, W value) {
		synchronized (values) {
			values.put(key, value);
		}
	}

	public void remove(T key) {
		synchronized (values) {
			values.remove(key);
		}
	}

	public Map<T, W> getValues() {
		synchronized (values) {
			return values;
		}
	}

	public boolean hasKey(T key) {
		synchronized (values) {
			return values.containsKey(key);
		}
	}

	public void clear() {
		synchronized (values) {
			values.clear();
		}
	}
}
