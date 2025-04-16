package com.nisovin.shopkeepers.shopobjects.block.base;

import org.bukkit.block.Block;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.SKShopkeepersPlugin;
import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import com.nisovin.shopkeepers.api.shopobjects.ShopObjectType;
import com.nisovin.shopkeepers.shopkeeper.registry.SKShopkeeperRegistry;
import com.nisovin.shopkeepers.util.java.Validate;

/**
 * Implements common default behaviors for {@link BaseBlockShopObject}.
 */
public class BaseBlockShops {

	private final SKShopkeepersPlugin plugin;
	private final SKShopkeeperRegistry shopkeeperRegistry;
	private final BaseBlockShopListener blockShopListener;

	public BaseBlockShops(SKShopkeepersPlugin plugin) {
		this.plugin = plugin;
		this.shopkeeperRegistry = plugin.getShopkeeperRegistry();
		this.blockShopListener = new BaseBlockShopListener(plugin, Unsafe.initialized(this));
	}

	public void onEnable() {
		// TODO Enable and disable dynamically as new shop object types (e.g. externally) are
		// registered or as shop object types are dynamically disabled or enabled.
		if (!this.shallEnable()) return;

		blockShopListener.onEnable();
	}

	private boolean shallEnable() {
		// Only enable if there is at least one enabled base block shop object type:
		for (ShopObjectType<?> shopObjectType : plugin.getShopObjectTypeRegistry().getRegisteredTypes()) {
			if (shopObjectType instanceof BaseBlockShopObjectType<?> && shopObjectType.isEnabled()) {
				return true;
			}
		}
		return false;
	}

	public void onDisable() {
		blockShopListener.onDisable();
	}

	/**
	 * Checks whether the given shopkeeper has a {@link BaseBlockShopObject} that uses the default
	 * block shop behaviors.
	 * 
	 * @param shopkeeper
	 *            the shopkeeper, not <code>null</code>
	 * @return <code>true</code> if the shopkeeper uses the default {@link BaseBlockShopObject}
	 *         behaviors
	 */
	public boolean isBaseBlockShop(Shopkeeper shopkeeper) {
		return shopkeeper.getShopObject() instanceof BaseBlockShopObject;
	}

	/**
	 * Checks whether the given block is a shopkeeper that uses the default
	 * {@link BaseBlockShopObject} behaviors.
	 * 
	 * @param block
	 *            the block
	 * @return <code>true</code> if the block is a shopkeeper that uses the default
	 *         {@link BaseBlockShopObject} behaviors
	 */
	public boolean isBaseBlockShop(Block block) {
		Validate.notNull(block, "block is null");
		return isBaseBlockShop(
				block.getWorld().getName(),
				block.getX(),
				block.getY(),
				block.getZ()
		);
	}

	/**
	 * Checks whether the block at the specified coordinates is a shopkeeper that uses the default
	 * {@link BaseBlockShopObject} behaviors.
	 * 
	 * @param worldName
	 *            the world name
	 * @param blockX
	 *            the block's x coordinate
	 * @param blockY
	 *            the block's y coordinate
	 * @param blockZ
	 *            the block's z coordinate
	 * @return <code>true</code> if the block is a shopkeeper that uses the default
	 *         {@link BaseBlockShopObject} behaviors
	 */
	public boolean isBaseBlockShop(String worldName, int blockX, int blockY, int blockZ) {
		Shopkeeper shopkeeper = shopkeeperRegistry.getShopkeeperByBlock(
				worldName,
				blockX,
				blockY,
				blockZ
		);
		return shopkeeper != null && this.isBaseBlockShop(shopkeeper);
	}

	/**
	 * Cancels all subsequent {@link BlockPhysicsEvent}s at the specified block location.
	 * 
	 * @param block
	 *            the block, or <code>null</code> to reset
	 */
	public void cancelNextBlockPhysics(@Nullable Block block) {
		blockShopListener.cancelNextBlockPhysics(block);
	}
}
