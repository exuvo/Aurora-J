/*******************************************************************************
 * Copyright (c) 2010 Robert "Unlogic" Olofsson (unlogic@unlogic.se). All rights reserved. This program and the accompanying materials are
 * made available under the terms of the GNU Lesser Public License v3 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-3.0-standalone.html
 ******************************************************************************/
package se.unlogic.standardutils.threads;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ThreadPoolTaskGroupHandler<T extends TaskGroup> implements TaskGroupHandler<T, SimpleExecutionController<T>>, Runnable {

	protected final ReentrantLock taskGroupRemoveLock = new ReentrantLock();
	protected final Condition taskGroupRemoveCondition = taskGroupRemoveLock.newCondition();

	protected final ReentrantLock taskGroupAddLock = new ReentrantLock();
	protected final Condition taskGroupAddCondition = taskGroupAddLock.newCondition();

	protected final CopyOnWriteArrayList<SimpleExecutionController<T>> taskGroupList = new CopyOnWriteArrayList<SimpleExecutionController<T>>();

	protected int taskGroupIndex = 0;

	protected Status status = Status.RUNNING;

	protected final ArrayList<Thread> threads = new ArrayList<Thread>();

	public ThreadPoolTaskGroupHandler(String name, int poolSize, boolean daemon) {

		while (threads.size() < poolSize) {

			Thread thread = new Thread(this, "TaskGroupHandler " + name + " thread " + (threads.size() + 1));
			thread.setDaemon(daemon);

			threads.add(thread);

			thread.start();
		}
	}

	public void run() {

		Runnable task = null;

		while (true) {

			try {

				while (!taskGroupList.isEmpty() && status != Status.SHUTDOWN) {

					SimpleExecutionController<T> executionController;

					try {
						executionController = taskGroupList.get(getIndex());

					} catch (IndexOutOfBoundsException e) {

						continue;
					}

					task = executionController.getTaskQueue().poll();

					if (task == null) {

						boolean signalExecutionComplete = false;

						taskGroupRemoveLock.lock();

						try {

							if (executionController.getInitialTaskCount() == executionController.getCompletedTaskCount() && taskGroupList.contains(executionController)) {

								taskGroupList.remove(executionController);
								taskGroupRemoveCondition.signalAll();
								signalExecutionComplete = true;

							} else {

								break;
							}

						} finally {

							taskGroupRemoveLock.unlock();

							if (signalExecutionComplete) {

								executionController.executionComplete();
							}
						}

					} else {

						try {
							task.run();

						} catch (Throwable e) {

							e.printStackTrace();

						} finally {

							executionController.incrementCompletedTaskCount();
						}
					}
				}

				taskGroupAddLock.lock();

				try {
					if (status == Status.TERMINATING) {

						// Last thread sets shutdown status
						if (getLiveThreads() == 1) {

							status = Status.SHUTDOWN;
						}

						return;
					}

					if (isEmpty()) {

						taskGroupAddCondition.await();
					}

				} catch (InterruptedException e) {

				} finally {
					taskGroupAddLock.unlock();
				}

			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
	}

	public boolean isEmpty() {

		if (taskGroupList.isEmpty()) {
			return true;
		}

		for (SimpleExecutionController<T> controller : taskGroupList) {

			if (!controller.getTaskQueue().isEmpty()) {

				return false;
			}
		}

		return true;
	}

	public int getLiveThreads() {

		int activeThreadCount = 0;

		for (Thread thread : threads) {

			if (thread.isAlive()) {

				activeThreadCount++;
			}
		}

		return activeThreadCount;
	}

	protected synchronized int getIndex() {

		taskGroupIndex++;

		if (taskGroupIndex >= taskGroupList.size()) {

			taskGroupIndex = 0;
		}

		return taskGroupIndex;
	}

	void remove(SimpleExecutionController<T> executionController) {

		taskGroupRemoveLock.lock();

		try {

			taskGroupList.remove(executionController);
			taskGroupRemoveCondition.signalAll();

		} finally {

			taskGroupRemoveLock.unlock();
		}
	}

	void add(SimpleExecutionController<T> executionController) {

		taskGroupAddLock.lock();

		try {

			if (status == Status.RUNNING) {

				taskGroupList.add(executionController);
				taskGroupAddCondition.signalAll();

			} else {

				throw new RejectedExecutionException("TaskGroupHandler status " + status);
			}

		} finally {

			taskGroupAddLock.unlock();
		}
	}

	public void abortAllTaskGroups() {

		taskGroupRemoveLock.lock();

		try {

			while (!taskGroupList.isEmpty()) {

				for (SimpleExecutionController<T> executionController : taskGroupList) {

					executionController.abort();
				}
			}

		} finally {

			taskGroupRemoveLock.unlock();
		}

	}

	public SimpleExecutionController<T> execute(T taskGroup) throws RejectedExecutionException {

		if (status == Status.RUNNING) {

			return new SimpleExecutionController<T>(taskGroup, this);

		} else {

			throw new RejectedExecutionException("TaskGroupHandler status " + status);
		}
	}

	public int getTaskGroupCount() {

		return taskGroupList.size();
	}

	public List<SimpleExecutionController<T>> getTaskGroups() {

		return new ArrayList<SimpleExecutionController<T>>(taskGroupList);
	}

	public int getTotalTaskCount() {

		int taskCount = 0;

		for (SimpleExecutionController<T> executionController : taskGroupList) {

			taskCount += executionController.getTaskQueue().size();
		}

		return taskCount;
	}

	public Status getStatus() {

		return status;
	}

	public void awaitTermination() throws InterruptedException {

		taskGroupRemoveLock.lock();

		try {

			while (!taskGroupList.isEmpty()) {

				taskGroupRemoveCondition.await();
			}

		} finally {

			taskGroupRemoveLock.unlock();
		}
	}

	public void awaitTermination(long timeout) throws InterruptedException {

		taskGroupRemoveLock.lock();

		try {

			while (!taskGroupList.isEmpty()) {

				taskGroupRemoveCondition.await(timeout, TimeUnit.MILLISECONDS);
			}

		} finally {

			taskGroupRemoveLock.unlock();
		}
	}

	public void shutdown() {

		taskGroupAddLock.lock();

		try {

			if (status == Status.RUNNING) {

				status = Status.TERMINATING;
				taskGroupAddCondition.signalAll();
			}

		} finally {

			taskGroupAddLock.unlock();
		}
	}

	public void shutdownNow() {

		shutdown();
		abortAllTaskGroups();
	}
}
