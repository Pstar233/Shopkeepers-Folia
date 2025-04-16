package com.nisovin.shopkeepers.shopcreation;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.lang.Messages;
import com.nisovin.shopkeepers.util.bukkit.TextUtils;

class ShopCreationItemSelectionTask implements Runnable {

    /**
	 * 我们发送商店创建项目选择消息之前的时间（以刻度为单位）。
	 * <p>
	 * 只有在此延迟后玩家仍持有物品时，我们才会发送消息。这
	 * 避免了玩家通过
	 * 鼠标滚轮。
	 */
	private static final long DELAY_TICKS = 5L; // 0.25 seconds

	// By player UUID:
	private static final Map<UUID, ShopCreationItemSelectionTask> activeTasks = new HashMap<>();

	/**
	 * Starts this task for the given player.
	 * <p>
	 * Any already active task for the player is cancelled.
	 * 
	 * @param plugin
	 *            the plugin, not <code>null</code>
	 * @param player
	 *            the player, not <code>null</code>
	 */
	static void start(Plugin plugin, Player player) {
		assert plugin != null && player != null;
		// If there is already an active task, we cancel and restart it (i.e. we reuse it):
		ShopCreationItemSelectionTask task = activeTasks.computeIfAbsent(
				player.getUniqueId(),
				uuid -> new ShopCreationItemSelectionTask(plugin, player)
		);
		assert task != null;
		task.start();
	}

	/**
	 * Cleans up and cancels any currently active task for the given player.
	 * 
	 * @param player
	 *            the player, not <code>null</code>
	 */
	static void cleanupAndCancel(Player player) {
		assert player != null;
		ShopCreationItemSelectionTask task = activeTasks.remove(player.getUniqueId());
		if (task != null) {
			task.cancel();
		}
	}

	/**
	 * This needs to be called on plugin disable.
	 * <p>
	 * This cleans up any currently active tasks.
	 */
	static void onDisable() {
		// Note: It is not required to manually cancel the active tasks on plugin disable. They are
		// cancelled anyway.
		activeTasks.clear();
	}

	private static void cleanup(Player player) {
		activeTasks.remove(player.getUniqueId());
	}

	// -----

	private final Plugin plugin;
	private final Player player;
	private @Nullable ScheduledTask bukkitTask = null;

	// Use the static 'start' factory method.
	private ShopCreationItemSelectionTask(Plugin plugin, Player player) {
		assert plugin != null && player != null;
		this.plugin = plugin;
		this.player = player;
	}

	private void start() {
		// Cancel previous task if already active:
		this.cancel();
		Location location = player.getLocation();
		bukkitTask = Bukkit.getRegionScheduler().runDelayed(plugin, location, task -> run(), DELAY_TICKS);
	}

	// Note: Performs no cleanup.
	private void cancel() {
		if (bukkitTask != null) {
			bukkitTask.cancel();
			bukkitTask = null;
		}
	}

	@Override
	public void run() {
		// Cleanup:
		cleanup(player);

		if (!player.isOnline()) return; // No longer online
		if (!ShopCreationItem.isShopCreationItem(player.getInventory().getItemInMainHand())) {
			// No longer holding the shop creation item in hand:
			return;
		}

		// Note: We do not check if the player has the permission to create shops here again. We
		// checked that earlier already, before starting this task. Even if there has been a change
		// to that in the meantime, there is no major harm caused by sending the selection message
		// anyway. The task's delay is short enough that this does not matter.

		// Inform the player about the shop creation item's usage:
		TextUtils.sendMessage(player, Messages.creationItemSelected);
	}
}
