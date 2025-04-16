package com.nisovin.shopkeepers.util.interaction;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class InteractionUtils {

	/**
	 * Checks if the player can interact with the given block.
	 * <p>
	 * This works by clearing the items in the player's main and off hand, calling a dummy
	 * {@link PlayerInteractEvent} for plugins to react to, and then restoring the items in the
	 * player's main and off hand.
	 * <p>
	 * We may also want to invoke this for block locations that are currently empty (i.e.
	 * {@link Material#isAir()}), e.g. to check access to the location. But since some region
	 * protection plugins ignore interactions with empty blocks, we temporarily place a dummy
	 * non-empty block if the location is currently empty.
	 * <p>
	 * Since this involves calling a dummy {@link PlayerInteractEvent}, plugins reacting to the
	 * event might cause all kinds of side effects. Therefore, this should only be used in very
	 * specific situations, such as for specific blocks.
	 * 
	 * @param player
	 *            the player
	 * @param block
	 *            the block to check interaction with
	 * @return <code>true</code> if no plugin denied block interaction
	 */
	public static boolean checkBlockInteract(Player player, Block block) {
		// Simulating a right-click on the block to check if access is denied:
		// Making sure that block access is really denied, and that the event is not cancelled
		// because of denying usage with the items in hands:
		PlayerInventory playerInventory = player.getInventory();
		ItemStack itemInMainHand = playerInventory.getItemInMainHand();
		ItemStack itemInOffHand = playerInventory.getItemInOffHand();
		playerInventory.setItemInMainHand(null);
		playerInventory.setItemInOffHand(null);

		// Temporarily place a non-empty block if the block location is empty:
		Material blockType = block.getType();
		if (blockType.isAir()) {
			// Skip physics to not accidentally affect neighboring blocks:
			block.setType(Material.STONE, false);
		}

		TestPlayerInteractEvent dummyInteractEvent = new TestPlayerInteractEvent(
				player,
				Action.RIGHT_CLICK_BLOCK,
				null,
				block,
				BlockFace.UP
		);
		Bukkit.getPluginManager().callEvent(dummyInteractEvent);
		boolean canAccessBlock = (dummyInteractEvent.useInteractedBlock() != Result.DENY);

		// Reset the block type again (without physics):
		if (blockType.isAir()) {
			block.setType(blockType, false);
		}

		// Resetting items in main and off hand:
		playerInventory.setItemInMainHand(itemInMainHand);
		playerInventory.setItemInOffHand(itemInOffHand);
		return canAccessBlock;
	}

	/**
	 * Checks if the player can interact with the given entity.
	 * <p>
	 * This works by clearing the items in the player's main and off hand, calling a dummy
	 * {@link PlayerInteractEntityEvent} for plugins to react to, and then restoring the items in
	 * the player's main and off hand.
	 * <p>
	 * Since this involves calling a dummy {@link PlayerInteractEntityEvent}, plugins reacting to
	 * the event might cause all kinds of side effects. Therefore, this should only be used in very
	 * specific situations, such as for specific entities, and its usage should be optional (i.e.
	 * guarded by a config setting).
	 * 
	 * @param player
	 *            the player
	 * @param entity
	 *            the entity to check interaction with
	 * @return <code>true</code> if no plugin denied interaction
	 */
	public static boolean checkEntityInteract(Player player, Entity entity) {
		// Simulating a right-click on the entity to check if access is denied:
		// Making sure that entity access is really denied, and that the event is not cancelled
		// because of denying usage with the items in hands:
		PlayerInventory playerInventory = player.getInventory();
		ItemStack itemInMainHand = playerInventory.getItemInMainHand();
		ItemStack itemInOffHand = playerInventory.getItemInOffHand();
		playerInventory.setItemInMainHand(null);
		playerInventory.setItemInOffHand(null);

		TestPlayerInteractEntityEvent dummyInteractEvent = new TestPlayerInteractEntityEvent(
				player,
				entity
		);
		Bukkit.getPluginManager().callEvent(dummyInteractEvent);
		boolean canAccessEntity = !dummyInteractEvent.isCancelled();

		// Resetting items in main and off hand:
		playerInventory.setItemInMainHand(itemInMainHand);
		playerInventory.setItemInOffHand(itemInOffHand);
		return canAccessEntity;
	}

	private InteractionUtils() {
	}
}
