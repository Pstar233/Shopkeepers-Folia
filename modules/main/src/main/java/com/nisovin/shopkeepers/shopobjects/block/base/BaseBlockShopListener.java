package com.nisovin.shopkeepers.shopobjects.block.base;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.SKShopkeepersPlugin;
import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import com.nisovin.shopkeepers.config.Settings;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.shopkeeper.registry.SKShopkeeperRegistry;
import com.nisovin.shopkeepers.util.bukkit.BlockFaceUtils;
import com.nisovin.shopkeepers.util.bukkit.EventUtils;
import com.nisovin.shopkeepers.util.bukkit.MutableBlockLocation;
import com.nisovin.shopkeepers.util.bukkit.TextUtils;
import com.nisovin.shopkeepers.util.interaction.InteractionUtils;
import com.nisovin.shopkeepers.util.interaction.TestPlayerInteractEvent;
import com.nisovin.shopkeepers.util.logging.Log;

class BaseBlockShopListener implements Listener {

	// Local copy as array (enables a very high-performance iteration):
	// This includes all directions that physic updates can propagate from, regardless of whether
	// the block shops can be attached in that direction.
	private static final @NonNull BlockFace[] BLOCK_SIDES = BlockFaceUtils.getBlockSides()
			.toArray(new @NonNull BlockFace[0]);

	private final SKShopkeepersPlugin plugin;
	private final BaseBlockShops baseBlockShops;
	private final SKShopkeeperRegistry shopkeeperRegistry;

	private final MutableBlockLocation cancelNextBlockPhysics = new MutableBlockLocation();

	BaseBlockShopListener(SKShopkeepersPlugin plugin, BaseBlockShops blockShops) {
		this.plugin = plugin;
		this.baseBlockShops = blockShops;
		this.shopkeeperRegistry = plugin.getShopkeeperRegistry();
	}

	void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, plugin);
		// Ensure that our interact event handler is always executed first, even after plugin
		// reloads:
		// In order to not change the order among the already registered event handlers of our own
		// plugin, we move them all together to the front of the handler list.
		EventUtils.enforceExecuteFirst(PlayerInteractEvent.class, EventPriority.LOWEST, plugin);
	}

	void onDisable() {
		HandlerList.unregisterAll(this);
	}

	// Null to clear
	void cancelNextBlockPhysics(@Nullable Block block) {
		if (block == null) {
			cancelNextBlockPhysics.setWorldName(null);
		} else {
			cancelNextBlockPhysics.set(block);
		}
	}

	// See LivingEntityShopListener for the reasoning behind using event priority LOWEST and
	// ignoring cancelled events.
	// The shop creation item reacts to player interactions as well. If a player interacts with a
	// base block shop while holding a shop creation item in his hand, we want the base block shop
	// interaction to take precedence. This listener therefore has to be registered before the shop
	// creation listener.
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	void onPlayerInteract(PlayerInteractEvent event) {
		// Ignore our own fake interact event:
		if (event instanceof TestPlayerInteractEvent) return;

		// Check for base block shop interaction:
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

		Player player = event.getPlayer();
		Block block = Unsafe.assertNonNull(event.getClickedBlock());
		Log.debug(() -> "Player " + player.getName() + " is interacting (" + event.getHand()
				+ ") with block at " + TextUtils.getLocationString(block));

		AbstractShopkeeper shopkeeper = shopkeeperRegistry.getShopkeeperByBlock(block);
		if (shopkeeper == null) {
			Log.debug("  Non-shopkeeper");
			return;
		}
		if (!baseBlockShops.isBaseBlockShop(shopkeeper)) {
			Log.debug("  Not using default block shop behaviors");
			return;
		}

		// Keep track of the previous interaction result:
		boolean useInteractedBlock = (event.useInteractedBlock() != Result.DENY);

		// Always cancel interactions with shopkeepers, to prevent any default behavior:
		event.setCancelled(true); // Also cancels the item interaction
		// Update inventory in case the interaction would trigger an item action normally:
		player.updateInventory();

		// Ignore if already cancelled. This resolves conflicts with other event handlers that also
		// run at LOWEST priority, such as for example Shopkeepers' shop creation item listener.
		if (!useInteractedBlock) {
			Log.debug("  Ignoring already cancelled block interaction");
			return;
		}

		// Only trigger shopkeeper interaction for main-hand events:
		if (event.getHand() != EquipmentSlot.HAND) {
			Log.debug("  Ignoring off-hand interaction");
			return;
		}

		// Check the block interaction result by calling another interact event:
		if (Settings.checkShopInteractionResult) {
			if (!InteractionUtils.checkBlockInteract(player, block)) {
				Log.debug("  Cancelled by another plugin");
				return;
			}
		}

		// Handle interaction:
		shopkeeper.onPlayerInteraction(player);
	}

	// Protect shop blocks:

	private boolean isProtectedBlock(Block block) {
		// Check if the block itself is a base block shop:
		if (baseBlockShops.isBaseBlockShop(block)) {
			return true;
		}

		// Check if there is a base block shop attached to this block:
		String worldName = block.getWorld().getName();
		int blockX = block.getX();
		int blockY = block.getY();
		int blockZ = block.getZ();
		for (BlockFace blockFace : BLOCK_SIDES) {
			// Note: Avoiding getting the adjacent block slightly improves the performance.
			int adjacentX = blockX + blockFace.getModX();
			int adjacentY = blockY + blockFace.getModY();
			int adjacentZ = blockZ + blockFace.getModZ();
			Shopkeeper shopkeeper = shopkeeperRegistry.getShopkeeperByBlock(
					worldName,
					adjacentX,
					adjacentY,
					adjacentZ
			);
			if (shopkeeper == null || !baseBlockShops.isBaseBlockShop(shopkeeper)) continue;

			BaseBlockShopObject blockShop = (BaseBlockShopObject) shopkeeper.getShopObject();
			BlockFace attachedFace = blockShop.getAttachedBlockFace();
			if (blockFace == attachedFace) {
				// The block shop is attached to the given block:
				return true;
			}
			// Else continue: There might be other block shops that are actually attached to the
			// block in the remaining block directions.
		}
		return false;
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onBlockBreak(BlockBreakEvent event) {
		Block block = event.getBlock();
		if (this.isProtectedBlock(block)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onBlockPlace(BlockPlaceEvent event) {
		Block block = event.getBlock();
		if (baseBlockShops.isBaseBlockShop(block)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onBlockPhysics(BlockPhysicsEvent event) {
		Block block = event.getBlock();
		World world = block.getWorld();
		String worldName = world.getName();
		int blockX = block.getX();
		int blockY = block.getY();
		int blockZ = block.getZ();
		if (this.checkCancelPhysics(worldName, blockX, blockY, blockZ)) {
			event.setCancelled(true);
			return;
		}
		// Spigot changed the behavior of this event in MC 1.13 to reduce the number of event calls:
		// Related: https://hub.spigotmc.org/jira/browse/SPIGOT-4256
		for (BlockFace blockFace : BLOCK_SIDES) {
			// Note: Avoiding getting the adjacent block slightly improves performance of handling
			// this event.
			int adjacentX = blockX + blockFace.getModX();
			int adjacentY = blockY + blockFace.getModY();
			int adjacentZ = blockZ + blockFace.getModZ();
			if (this.checkCancelPhysics(worldName, adjacentX, adjacentY, adjacentZ)) {
				event.setCancelled(true);
				return;
			}
		}
	}

	private boolean checkCancelPhysics(String worldName, int blockX, int blockY, int blockZ) {
		if (cancelNextBlockPhysics.matches(worldName, blockX, blockY, blockZ)) {
			return true;
		} else if (baseBlockShops.isBaseBlockShop(worldName, blockX, blockY, blockZ)) {
			return true;
		}
		return false;
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onEntityExplosion(EntityExplodeEvent event) {
		List<Block> blockList = event.blockList();
		this.removeProtectedBlocks(blockList);
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onBlockExplosion(BlockExplodeEvent event) {
		List<Block> blockList = event.blockList();
		this.removeProtectedBlocks(blockList);
	}

	private void removeProtectedBlocks(List<? extends Block> blockList) {
		assert blockList != null;
		blockList.removeIf(this::isProtectedBlock);
	}
}
