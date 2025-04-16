package com.nisovin.shopkeepers.playershops.inactivity;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.SKShopkeepersPlugin;
import com.nisovin.shopkeepers.config.Settings;
import com.nisovin.shopkeepers.util.bukkit.Ticks;
import com.nisovin.shopkeepers.util.java.Validate;

import java.util.concurrent.TimeUnit;

/**
 * 处理非活跃玩家拥有的商店的移除。
 */
public class PlayerInactivity {

	private final SKShopkeepersPlugin plugin;
	private final DeleteInactivePlayerShopsTask task;

	public PlayerInactivity(SKShopkeepersPlugin plugin) {
		Validate.notNull(plugin, "plugin is null");
		this.plugin = plugin;
		this.task = new DeleteInactivePlayerShopsTask(plugin);
	}

	public void onEnable() {
		if (Settings.playerShopkeeperInactiveDays <= 0) return; // Feature is disabled

		// Delete inactive player shops, once shortly after plugin startup, and then periodically:
		task.start();
	}

	public void onDisable() {
		task.stop();
	}

/**
	 * 此任务会定期触发对非活动商店的检测和移除
	 *球员。
	 * <p>
	 * 该任务也会在启动后不久运行。
	 * <p>
	 * 由于我们以天为单位来衡量玩家的不活跃状态，并且由于检查
	 * 非活跃玩家相对注重性能，我们很少运行此任务。它
	 * 也不要求此任务完全按照指定的时间间隔运行，即
	 * 不太可能，因为服务器滞后会显著影响确切的间隔持续时间。这
	 * 此任务的主要目的是考虑长时间运行的服务器
	 * 持续时间。
	 */
	private final class DeleteInactivePlayerShopsTask implements Runnable {

		// ~4 hours (can be noticeably longer if the server lags)
		private static final long INTERVAL_TICKS = Ticks.PER_SECOND * 60 * 60 * 4L;

		private final Plugin plugin;
		private @Nullable ScheduledTask task = null;

		public DeleteInactivePlayerShopsTask(Plugin plugin) {
			Validate.notNull(plugin, "plugin is null");
			this.plugin = plugin;
		}

		public void start() {
			this.stop(); // Stop the task if it is already running

			// 该任务在启动后不久运行一次，然后以较大的间隔定期运行：
			task = Bukkit.getAsyncScheduler().runAtFixedRate(plugin,t -> {
				run();
			}, 5L * 50, INTERVAL_TICKS * 50, TimeUnit.MILLISECONDS);
		}

		public void stop() {
			if (task != null) {
				task.cancel();
				task = null;
			}
		}

		@Override
		public void run() {
			deleteShopsOfInactivePlayers();
		}
	}

	// TODO Also add a command to manually detect and then optionally delete inactive player shops?
	public void deleteShopsOfInactivePlayers() {
		if (Settings.playerShopkeeperInactiveDays <= 0) return; // Feature is disabled
		new DeleteShopsOfInactivePlayersProcedure(plugin).start();
	}
}
