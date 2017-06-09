package se.exuvo.aurora.utils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DummyReentrantReadWriteLock extends ReentrantReadWriteLock {

	private static final long serialVersionUID = -6347283347989805211L;

	public static final DummyReentrantReadWriteLock INSTANCE = new DummyReentrantReadWriteLock();
	private static final DummyReadLock READ_INSTANCE = new DummyReadLock(INSTANCE);
	private static final DummyWriteLock WRITE_INSTANCE = new DummyWriteLock(INSTANCE);

	public DummyReadLock readLock() {
		return READ_INSTANCE;
	}

	public DummyWriteLock writeLock() {
		return WRITE_INSTANCE;
	}

	private DummyReentrantReadWriteLock() {}

	public static class DummyReadLock extends ReentrantReadWriteLock.ReadLock {

		private static final long serialVersionUID = 7608807168442679450L;

		protected DummyReadLock(ReentrantReadWriteLock lock) {
			super(lock);
		}

		@Override
		public void lock() {}

		@Override
		public void lockInterruptibly() throws InterruptedException {}

		@Override
		public boolean tryLock() {
			return true;
		}

		@Override
		public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
			return true;
		}

		@Override
		public void unlock() {}

		@Override
		public Condition newCondition() {
			throw new UnsupportedOperationException();
		}

	}

	public static class DummyWriteLock extends ReentrantReadWriteLock.WriteLock {

		private static final long serialVersionUID = -2724273564839340322L;

		protected DummyWriteLock(ReentrantReadWriteLock lock) {
			super(lock);
		}

		@Override
		public void lock() {}

		@Override
		public void lockInterruptibly() throws InterruptedException {}

		@Override
		public boolean tryLock() {
			return true;
		}

		@Override
		public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
			return true;
		}

		@Override
		public void unlock() {}

		@Override
		public Condition newCondition() {
			throw new UnsupportedOperationException();
		}

	}
}
