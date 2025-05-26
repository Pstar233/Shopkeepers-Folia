package com.nisovin.shopkeepers.util.bukkit;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitWorker;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.util.java.Validate;

/**
 * Scheduler related utilities.
 */
public final class SchedulerUtils {

	public static int getActiveAsyncTasks(Plugin plugin) {
		if (true) {
			return 0;
		}
		Validate.notNull(plugin, "plugin is null");
		int workers = 0;
		for (BukkitWorker worker : Bukkit.getScheduler().getActiveWorkers()) {
			if (worker.getOwner().equals(plugin)) {
				workers++;
			}
		}
		return workers;
	}

	private static void validatePluginTask(Plugin plugin, Runnable task) {
		Validate.notNull(plugin, "plugin is null");
		Validate.notNull(task, "task is null");
	}

	/**
	 * Checks if the current thread is the server's main thread.
	 * 
	 * @return <code>true</code> if currently running on the main thread
	 */
	public static boolean isMainThread() {
		return Bukkit.isPrimaryThread();
	}

	/**
	 * 如果需要，将给定的任务安排在主线程上运行。
	 * <p>
	 * 如果当前线程已经是主线程，则任务将立即运行。
	 * 否则，它会尝试将任务安排在服务器的主线程上运行。然而
	 * 如果插件被禁用，则不会安排任务。
	 *
	 * @param plugin 用于调度的插件，而不是 <code>null</code>
	 * @param task 任务，而不是 <code>null</code>
	 * 如果任务已运行或已成功计划运行，则为 <code>@return true</code>，
	 * <code>否则为 false</code>
	 */
	public static boolean runOnMainThreadOrOmit(Plugin plugin, Runnable task) {
		validatePluginTask(plugin, task);
		if (isMainThread()) {
			task.run();
			return true;
		} else {
			return (runGlobalTaskScheduler(plugin, task, 1) != null);
		}
	}

	/**
	 * 区域线程调度器（立刻执行）
	 * @param plugin
	 * @param task
	 * @param location
	 * @return
	 */
	public static @Nullable BukkitTask runRegionScheduler(Plugin plugin, Runnable task, Location location) {
		validatePluginTask(plugin, task);
		// Tasks can only be registered while enabled:
		if (plugin.isEnabled()) {
			try {
				Bukkit.getRegionScheduler().run(plugin, location, task1 -> {
					task.run();
				});
			} catch (IllegalPluginAccessException e) {
				// Couldn't register task: The plugin got disabled just now.
			}
		}
		return null;
	}

	public static @Nullable BukkitTask runTaskLaterOrOmit(Plugin plugin, Runnable task, long delay, Location location) {
		validatePluginTask(plugin, task);
		// Tasks can only be registered while enabled:
		if (plugin.isEnabled()) {
			try {
				Bukkit.getRegionScheduler().runDelayed(plugin, location, task1 -> {
					task.run();
				}, delay);
			} catch (IllegalPluginAccessException e) {
				// Couldn't register task: The plugin got disabled just now.
			}
		}
		return null;
	}

	/**
	 * 全局任务调度器
	 * @param plugin
	 * @param task
	 * @param delay
	 * @return
	 */
	public static @Nullable BukkitTask runGlobalTaskScheduler(Plugin plugin, Runnable task, long delay) {
		validatePluginTask(plugin, task);
		// Tasks can only be registered while enabled:
		if (plugin.isEnabled()) {
			try {
				Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t->{
					task.run();
				}, delay);
			} catch (IllegalPluginAccessException e) {
				// Couldn't register task: The plugin got disabled just now.
			}
		}
		return null;
	}

	/**
	 * 异步执行任务(立刻执行)
	 * @param plugin
	 * @param task
	 * @return
	 */
	public static @Nullable ScheduledTask runAsyncTaskOrOmit(Plugin plugin, Runnable task) {
		return runAsyncTaskLaterOrOmit(plugin, task, 0L);
	}

	/**
	 * 稍后异步运行任务
	 * @param plugin
	 * @param task
	 * @param delay
	 * @return
	 */
	public static @Nullable ScheduledTask runAsyncTaskLaterOrOmit(
			Plugin plugin,
			Runnable task,
			long delay
	) {
		validatePluginTask(plugin, task);
		// Tasks can only be registered while enabled:
		if (plugin.isEnabled()) {
			try {
				return Bukkit.getAsyncScheduler().runDelayed(plugin, t -> task.run(), (delay / 20), TimeUnit.SECONDS);
			} catch (IllegalPluginAccessException e) {
				// Couldn't register task: The plugin got disabled just now.
			}
		}
		return null;
	}

	/**
	 * Awaits the completion of async tasks of the specified plugin.
	 * <p>
	 * If a logger is specified, it will be used to print informational messages suited to the
	 * context of this method being called during disabling of the plugin.
	 * 
	 * @param plugin
	 *            the plugin
	 * @param asyncTasksTimeoutSeconds
	 *            the duration to wait for async tasks to finish in seconds (can be <code>0</code>)
	 * @param logger
	 *            the logger used for printing informational messages, can be <code>null</code>
	 * @return the number of remaining async tasks that are still running after waiting for the
	 *         specified duration
	 */
	public static int awaitAsyncTasksCompletion(
			Plugin plugin,
			int asyncTasksTimeoutSeconds,
			@Nullable Logger logger
	) {
		Validate.notNull(plugin, "plugin is null");
		Validate.isTrue(asyncTasksTimeoutSeconds >= 0, "asyncTasksTimeoutSeconds cannot be negative");

		int activeAsyncTasks = getActiveAsyncTasks(plugin);
		if (activeAsyncTasks > 0 && asyncTasksTimeoutSeconds > 0) {
			if (logger != null) {
				logger.info("Waiting up to " + asyncTasksTimeoutSeconds + " seconds for "
						+ activeAsyncTasks + " remaining async tasks to finish ...");
			}

			final long asyncTasksTimeoutMillis = TimeUnit.SECONDS.toMillis(asyncTasksTimeoutSeconds);
			final long waitStartNanos = System.nanoTime();
			long waitDurationMillis = 0L;
			do {
				// Periodically check again:
				try {
					Thread.sleep(25L);
				} catch (InterruptedException e) {
					// Ignore, but reset interrupt flag:
					Thread.currentThread().interrupt();
				}
				// Update the number of active async task before breaking from loop:
				activeAsyncTasks = getActiveAsyncTasks(plugin);

				// Update waiting duration and compare to timeout:
				waitDurationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - waitStartNanos);
				if (waitDurationMillis > asyncTasksTimeoutMillis) {
					// Timeout reached, abort waiting..
					break;
				}
			} while (activeAsyncTasks > 0);

			if (waitDurationMillis > 1 && logger != null) {
				logger.info("Waited " + waitDurationMillis + " ms for async tasks to finish.");
			}
		}

		if (activeAsyncTasks > 0 && logger != null) {
			// Severe, since this can potentially result in data loss, depending on what the tasks
			// are doing:
			logger.severe("There are still " + activeAsyncTasks
					+ " remaining async tasks active! Disabling anyway now.");
		}
		return activeAsyncTasks;
	}

	private SchedulerUtils() {
	}
}
