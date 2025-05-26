package com.nisovin.shopkeepers.shopkeeper.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.SKShopkeepersPlugin;
import com.nisovin.shopkeepers.api.events.PlayerShopkeeperHireEvent;
import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import com.nisovin.shopkeepers.api.shopkeeper.ShopkeeperRegistry;
import com.nisovin.shopkeepers.api.shopkeeper.player.PlayerShopkeeper;
import com.nisovin.shopkeepers.api.ui.UISession;
import com.nisovin.shopkeepers.api.util.UnmodifiableItemStack;
import com.nisovin.shopkeepers.config.Settings;
import com.nisovin.shopkeepers.config.Settings.DerivedSettings;
import com.nisovin.shopkeepers.lang.Messages;
import com.nisovin.shopkeepers.playershops.PlayerShopsLimit;
import com.nisovin.shopkeepers.ui.SKDefaultUITypes;
import com.nisovin.shopkeepers.ui.hiring.HiringHandler;
import com.nisovin.shopkeepers.ui.state.UIState;
import com.nisovin.shopkeepers.util.bukkit.TextUtils;
import com.nisovin.shopkeepers.util.inventory.InventoryUtils;
import com.nisovin.shopkeepers.util.inventory.ItemUtils;
import com.nisovin.shopkeepers.util.java.Validate;
import com.nisovin.shopkeepers.util.logging.Log;

public class PlayerShopHiringHandler extends HiringHandler {

	protected static final int HIRE_COST = 4;
	protected static final int BUTTON_HIRE_1 = 2;
	protected static final int BUTTON_HIRE_2 = 6;

	public PlayerShopHiringHandler(AbstractPlayerShopkeeper shopkeeper) {
		super(SKDefaultUITypes.HIRING(), shopkeeper);
	}

	@Override
	public AbstractPlayerShopkeeper getShopkeeper() {
		return (AbstractPlayerShopkeeper) super.getShopkeeper();
	}

	@Override
	protected boolean openWindow(UISession uiSession, UIState uiState) {
		Validate.notNull(uiSession, "uiSession is null");
		this.validateState(uiState);

		Player player = uiSession.getPlayer();
		PlayerShopkeeper shopkeeper = this.getShopkeeper();
		Inventory inventory = Bukkit.createInventory(player, 9, Messages.forHireTitle);

		ItemStack hireItem = DerivedSettings.hireButtonItem.createItemStack();
		inventory.setItem(BUTTON_HIRE_1, hireItem);
		inventory.setItem(BUTTON_HIRE_2, hireItem);

		UnmodifiableItemStack hireCost = shopkeeper.getHireCost();
		if (hireCost == null) return false;
		// Inventory#setItem copies the item, so we do not need to copy it ourselves here.
		inventory.setItem(HIRE_COST, ItemUtils.asItemStack(hireCost));

		return player.openInventory(inventory) != null;
	}

	private boolean canPlayerHireShopType(Player player, Shopkeeper shopkeeper) {
		if (!Settings.hireRequireCreationPermission) return true;
		if (!shopkeeper.getType().hasPermission(player)) return false;
		if (!shopkeeper.getShopObject().getType().hasPermission(player)) return false;
		return true;
	}

	private int getOwnedShopsCount(Player player) {
		assert player != null;
		ShopkeeperRegistry shopkeeperRegistry = SKShopkeepersPlugin.getInstance().getShopkeeperRegistry();
		return shopkeeperRegistry.getPlayerShopkeepersByOwner(player.getUniqueId()).size();
	}

	@Override
	protected void onInventoryClickEarly(UISession uiSession, InventoryClickEvent event) {
		super.onInventoryClickEarly(uiSession, event);
		if (this.isAutomaticShiftLeftClick()) {
			// Ignore automatically triggered shift left-clicks:
			return;
		}

		Player player = uiSession.getPlayer();
		PlayerShopkeeper shopkeeper = this.getShopkeeper();
		int slot = event.getRawSlot();
		if (slot == BUTTON_HIRE_1 || slot == BUTTON_HIRE_2) {
			// TODO Prevent hiring own shops?
			// Actually: This feature was originally meant for admins to set up pre-existing shops.
			// Handle hiring:
			// Check if the player can hire (create) this type of shopkeeper:
			if (!this.canPlayerHireShopType(player, shopkeeper)) {
				// Missing permission to hire this type of shopkeeper:
				TextUtils.sendMessage(player, Messages.cannotHireShopType);
				uiSession.abortDelayed();
				return;
			}

			UnmodifiableItemStack hireCost = shopkeeper.getHireCost();
			if (hireCost == null) {
				// The shopkeeper is no longer for hire.
				// TODO Maybe instead ensure that we always close all hiring UIs when the hiring
				// item changes.
				// TODO Send a feedback message to the player
				uiSession.abortDelayed();
				return;
			}

			// Check if the player can afford to hire the shopkeeper, and calculate the resulting
			// player inventory:
			PlayerInventory playerInventory = player.getInventory();
			@Nullable ItemStack[] newPlayerInventoryContents = Unsafe.castNonNull(playerInventory.getContents());
			if (InventoryUtils.removeItems(newPlayerInventoryContents, hireCost) != 0) {
				// The player cannot afford to hire the shopkeeper:
				TextUtils.sendMessage(player, Messages.cannotHire);
				// Close window for this player:
				uiSession.abortDelayed();
				return;
			}

			// Call event:
			int maxShopsLimit = PlayerShopsLimit.getMaxShopsLimit(player);
			PlayerShopkeeperHireEvent hireEvent = new PlayerShopkeeperHireEvent(
					shopkeeper,
					player,
					newPlayerInventoryContents,
					maxShopsLimit
			);
			Bukkit.getServer().getPluginManager().callEvent(hireEvent);
			if (hireEvent.isCancelled()) {
				Log.debug("PlayerShopkeeperHireEvent was cancelled!");
				// Close window for this player:
				uiSession.abortDelayed();
				return;
			}

			// Check max shops limit:
			maxShopsLimit = hireEvent.getMaxShopsLimit();
			if (maxShopsLimit != Integer.MAX_VALUE) {
				int ownedShopsCount = this.getOwnedShopsCount(player);
				if (ownedShopsCount >= maxShopsLimit) {
					TextUtils.sendMessage(player, Messages.tooManyShops);
					uiSession.abortDelayed();
					return;
				}
			}

			// Hire the shopkeeper:
			// Apply player inventory changes:
			InventoryUtils.setContents(playerInventory, newPlayerInventoryContents);
			shopkeeper.setForHire((UnmodifiableItemStack)null);
			shopkeeper.setOwner(player);
			shopkeeper.save();
			TextUtils.sendMessage(player, Messages.hired);

			// Close all open windows for the shopkeeper:
			shopkeeper.abortUISessionsDelayed();
		}
	}
}
