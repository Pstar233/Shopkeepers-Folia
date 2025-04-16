package com.nisovin.shopkeepers.shopkeeper.spawning;

import java.util.function.Consumer;

import com.nisovin.shopkeepers.api.ShopkeepersAPI;
import com.nisovin.shopkeepers.api.ShopkeepersPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.shopkeeper.spawning.ShopkeeperSpawnState.State;
import com.nisovin.shopkeepers.shopobjects.AbstractShopObject;
import com.nisovin.shopkeepers.util.java.Validate;
import com.nisovin.shopkeepers.util.taskqueue.TaskQueue;

/**
 * 用于对店主的生成进行负载均衡的队列。
 * <p>
 * 生成店主在性能方面可能相对昂贵。为了避免性能
 * 当激活具有大量店主的 chunk 时掉落，我们使用此队列来分配
 * 在几个刻内生成店主。
 * <p>
 * 店主可能在等待生成时已被勾选。商店物品可以
 * 使用 {@link AbstractShopObject#isSpawningScheduled（）} 来检查它们当前是否仍处于待处理状态
 * 来生成。
 */
public class ShopkeeperSpawnQueue extends TaskQueue<AbstractShopkeeper> {

	// With this configuration we can spawn around 40 shopkeepers per second.
	// A more frequently running task has a higher general overhead.
	private static final int SPAWN_TASK_PERIOD_TICKS = 3;
	// On my test setup, and without any GC taking place, the spawning of a shopkeeper seems to take
	// between 0.05-0.25ms, with an average of around 0.1ms.
	private static final int SPAWNS_PER_EXECUTION = 6;

	private final Consumer<? super AbstractShopkeeper> spawner;

	ShopkeeperSpawnQueue(Plugin plugin, Consumer<? super AbstractShopkeeper> spawner) {
		super(plugin, SPAWN_TASK_PERIOD_TICKS, SPAWNS_PER_EXECUTION);
		Validate.notNull(spawner, "spawner is null");
		this.spawner = spawner;
	}

	private static class SpawnerTask implements Runnable {

		private final Runnable parentTask;

		SpawnerTask(Runnable parentTask) {
			assert parentTask != null;
			this.parentTask = parentTask;
		}

		@Override
		public void run() {
			parentTask.run();
		}
	}

	private void setQueued(AbstractShopkeeper shopkeeper) {
		assert shopkeeper != null;
		ShopkeeperSpawnState spawnState = shopkeeper.getComponents().getOrAdd(ShopkeeperSpawnState.class);
		assert !spawnState.isSpawningScheduled();
		spawnState.setState(State.QUEUED);
	}

	private void resetQueued(AbstractShopkeeper shopkeeper) {
		assert shopkeeper != null;
		ShopkeeperSpawnState spawnState = shopkeeper.getComponents().getOrAdd(ShopkeeperSpawnState.class);
		// If this assertion throws: Make sure that the shopkeeper is getting removed from the queue
		// when the spawn state changes in the meantime, e.g. by calling
		// ShopkeeperSpawner#updateSpawnState instead of setting the spawn state directly.
		assert spawnState.getState() == State.QUEUED : "spawnState != QUEUED";
		spawnState.setState(State.DESPAWNED);
	}

	@Override
	protected void onAdded(AbstractShopkeeper shopkeeper) {
		super.onAdded(shopkeeper);
		// Mark the shopkeeper as 'queued':
		this.setQueued(shopkeeper);
	}

	@Override
	protected void onRemoval(AbstractShopkeeper shopkeeper) {
		super.onRemoval(shopkeeper);
		// Reset the shopkeeper's 'queued' state:
		this.resetQueued(shopkeeper);
	}

	@Override
	protected Runnable createTask() {
		return new SpawnerTask(super.createTask());
	}

	@Override
	protected void process(AbstractShopkeeper shopkeeper) {
		Location location = shopkeeper.getLocation();
		Bukkit.getRegionScheduler().run(ShopkeepersPlugin.getInstance(), location, task -> {
			// Reset the shopkeeper's 'queued' state:
			this.resetQueued(shopkeeper);

			// Spawn the shopkeeper:
			spawner.accept(shopkeeper);
		});
	}
}
