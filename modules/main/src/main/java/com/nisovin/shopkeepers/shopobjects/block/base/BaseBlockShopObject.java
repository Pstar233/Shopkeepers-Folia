package com.nisovin.shopkeepers.shopobjects.block.base;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.nisovin.shopkeepers.SKShopkeepersPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.api.shopkeeper.ShopCreationData;
import com.nisovin.shopkeepers.debug.DebugOptions;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.shopobjects.ShopkeeperMetadata;
import com.nisovin.shopkeepers.shopobjects.block.AbstractBlockShopObject;
import com.nisovin.shopkeepers.util.java.CyclicCounter;
import com.nisovin.shopkeepers.util.java.RateLimiter;
import com.nisovin.shopkeepers.util.logging.Log;
import org.jetbrains.annotations.NotNull;

/**
 * Extension of {@link AbstractBlockShopObject} with additional common block spawning and setup
 * logic.
 * <p>
 * The corresponding {@link #getType() shop object type} is expected to inherit from
 * {@link BaseBlockShopObjectType}.
 */
public abstract class BaseBlockShopObject extends AbstractBlockShopObject {

	private static final int CHECK_PERIOD_SECONDS = 10;
	private static final CyclicCounter nextCheckingOffset = new CyclicCounter(
			1,
			CHECK_PERIOD_SECONDS + 1
	);
	private static final long RESPAWN_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(3);

	private final BaseBlockShops blockShops;

	// Initial threshold between [1, CHECK_PERIOD_SECONDS] for load balancing:
	private final RateLimiter checkLimiter = new RateLimiter(
			CHECK_PERIOD_SECONDS,
			nextCheckingOffset.getAndIncrement()
	);

	private @Nullable Block block = null;
	private long lastFailedRespawnAttemptMillis = 0;

	protected BaseBlockShopObject(
			BaseBlockShops blockShops,
			AbstractShopkeeper shopkeeper,
			@Nullable ShopCreationData creationData
	) {
		super(shopkeeper, creationData);
		this.blockShops = blockShops;
	}

	/**
	 * Gets the {@link BaseBlockShops}.
	 * 
	 * @return the {@link BaseBlockShops}
	 */
	public BaseBlockShops getBlockShops() {
		return blockShops;
	}

	// ACTIVATION

	@Override
	public @Nullable Block getBlock() {
		return block;
	}

	/**
	 * Checks if the given block type is valid for this shop object type.
	 * <p>
	 * This is for example used by {@link #isActive()} to verify that the block is correctly
	 * spawned.
	 * 
	 * @param blockType
	 *            the block type
	 * @return <code>true</code> if the block type is valid
	 */
	protected abstract boolean isValidBlockType(Material blockType);

	/**
	 * Gets the {@link BlockFace} the block is (expected to be) attached to.
	 * <p>
	 * This does not necessarily take into account whether the shop object is currently
	 * {@link #isSpawned() spawned} or {@link #isActive() active}.
	 * 
	 * @return the attached block face, or <code>null</code> if the block is not attached to any
	 *         side
	 */
	public abstract @Nullable BlockFace getAttachedBlockFace();

	@Override
	public boolean isActive() {
		Block block = this.getBlock();
		if (block == null) return false; // Not spawned
		// The shopkeeper is despawned on chunk unload:
		assert Unsafe.assertNonNull(shopkeeper.getChunkCoords()).isChunkLoaded();
		if (!this.isValidBlockType(block.getType())) return false; // No longer of the expected type
		return true;
	}

	@Override
	public boolean spawn() {
		if (block != null) {
			return true; // Already spawned
		}

		Location spawnLocation = shopkeeper.getLocation();
		if (spawnLocation == null) {
			return false;
		}

		// If re-spawning fails due to the block dropping for some reason, e.g. if the attached
		// block is missing, this could be abused for drop farming. We therefore limit the number of
		// spawn attempts:
		if (System.currentTimeMillis() - lastFailedRespawnAttemptMillis < RESPAWN_TIMEOUT_MILLIS) {
			Log.debug(() -> shopkeeper.getLocatedLogPrefix() + "Spawn cooldown.");
			return false;
		}

		// Place the block:
		// This replaces any currently existing block at that location.
		Block spawnBlock = spawnLocation.getBlock();
		BlockData blockData = this.createBlockData();
		if (blockData == null) {
			Log.debug(() -> shopkeeper.getLocatedLogPrefix() + "Failed to create block data.");
			return false;
		}

		// Cancel block physics for this placed block if needed:
		blockShops.cancelNextBlockPhysics(spawnBlock);
		spawnBlock.setBlockData(blockData, false); // Skip physics update
		// Cleanup state if no block physics were triggered:
		blockShops.cancelNextBlockPhysics(null);

		// Check if the block placement has failed for some reason:
		if (!this.isValidBlockType(spawnBlock.getType())) {
			lastFailedRespawnAttemptMillis = System.currentTimeMillis();
			this.cleanUpBlock(spawnBlock);
			return false;
		}

		// Remember the block (indicates that this shop object has been spawned):
		this.block = spawnBlock;
		// Assign metadata for easy identification by other plugins:
		ShopkeeperMetadata.apply(block);

		// Other block setup:
		this.updateBlock();

		// Inform about the object id change:
		this.onIdChanged();

		return true;
	}

	/**
	 * Creates the {@link BlockData} to spawn the block with.
	 * 
	 * @return the block data, or <code>null</code> to abort the spawning
	 */
	protected abstract @Nullable BlockData createBlockData();

	/**
	 * 根据区块商店的当前状态更新生成的区块。
	 * <p>
	 * 如果区块商店当前未激活，即未生成，则这可能不起作用
	 * 或者如果块当前不是预期的类型。
	 */
	protected abstract void updateBlock();

	@Override
	public void despawn() {
		Block block = this.block;
		if (block == null) return;

		// 清理:
		this.cleanUpBlock(block);

		// 删除块
		Location location = block.getLocation();
		Bukkit.getRegionScheduler().run(SKShopkeepersPlugin.getInstance(), location, task -> {
			block.setType(Material.AIR, false);
			this.block = null;

			//通知对象 ID 更改：
			this.onIdChanged();
		});
	}

	// Any clean up that needs to happen for the block.
	protected void cleanUpBlock(Block block) {
		assert block != null;
		// Remove the metadata again:
		ShopkeeperMetadata.remove(block);
	}

	@Override
	public @NotNull CompletableFuture<Boolean> move() {
		if (!this.isSpawned()) return CompletableFuture.completedFuture(false);
		return CompletableFuture.completedFuture(this.respawn());
	}

	// TICKING

	@Override
	public void onTick() {
		super.onTick();
		if (!checkLimiter.request()) {
			return;
		}

		if (this.isSpawningScheduled()) {
			Log.debug(DebugOptions.regularTickActivities, () -> shopkeeper.getLogPrefix()
					+ "Spawning is scheduled. Skipping block check.");
			return;
		}

		// Indicate ticking activity for visualization:
		this.indicateTickActivity();

		// This is only called for shopkeepers in active (i.e. loaded) chunks, and shopkeepers are
		// despawned on chunk unload:
		assert Unsafe.assertNonNull(shopkeeper.getChunkCoords()).isChunkLoaded();

		if (!this.isActive()) {
			Log.debug(() -> shopkeeper.getLocatedLogPrefix()
					+ "Block is missing! Attempting respawn.");
			// Cleanup any previously spawned block, and then respawn:
			this.despawn();
			boolean success = this.spawn();
			if (!success) {
				Log.warning(shopkeeper.getLocatedLogPrefix() + "Block could not be spawned!");
			}
			return;
		}
	}

	// NAMING

	@Override
	public void setName(@Nullable String name) {
		// Blocks do not have names. However, this method is usually called when the shopkeeper is
		// renamed, and some blocks (e.g. signs) may have a way to visualize the shopkeeper's name.
		// So let's update the block here.
		this.updateBlock();
	}

	@Override
	public @Nullable String getName() {
		// Blocks do not have names. Any shopkeeper name visualization, e.g. via sign content, is
		// language file specific.
		return null;
	}

	// PLAYER SHOP OWNER

	@Override
	public void onShopOwnerChanged() {
		// Update the block (e.g. sign contents):
		this.updateBlock();
	}
}
