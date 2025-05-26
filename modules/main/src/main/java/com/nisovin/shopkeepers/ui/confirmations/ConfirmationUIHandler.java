package com.nisovin.shopkeepers.ui.confirmations;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.ui.UISession;
import com.nisovin.shopkeepers.lang.Messages;
import com.nisovin.shopkeepers.ui.SKDefaultUITypes;
import com.nisovin.shopkeepers.ui.UIHandler;
import com.nisovin.shopkeepers.ui.state.UIState;
import com.nisovin.shopkeepers.util.bukkit.TextUtils;
import com.nisovin.shopkeepers.util.inventory.ItemUtils;
import com.nisovin.shopkeepers.util.java.Validate;

public class ConfirmationUIHandler extends UIHandler {

	private static final int INVENTORY_SIZE = 9;
	private static final int SLOT_CONFIRM = 0;
	private static final int SLOT_CANCEL = 8;

	private final ConfirmationUIConfig config;
	private final Runnable action;
	// This is invoked when the player explicitly presses the 'cancel' button. It is not invoked if
	// the player closes the inventory, either directly or because something else closes it (for
	// example when another inventory is opened for the player).
	private final Runnable onCancelled;
	private boolean playerDecided = false;
	private Location location;

	ConfirmationUIHandler(ConfirmationUIConfig config, Runnable action, Runnable onCancelled, Location location) {
		super(SKDefaultUITypes.CONFIRMATION());
		Validate.notNull(config, "config is null");
		Validate.notNull(action, "action is null");
		Validate.notNull(onCancelled, "onCancelled is null");
		this.config = config;
		this.action = action;
		this.onCancelled = onCancelled;
		this.location = location;
	}

	@Override
	public boolean canAccess(Player player, boolean silent) {
		// Players cannot directly request this UI themselves. It is always opened for them in some
		// context.
		return true;
	}

	@Override
	protected boolean isWindow(InventoryView view) {
		Validate.notNull(view, "view is null");
		return view.getType() == InventoryType.CHEST
				&& view.getTopInventory().getSize() == INVENTORY_SIZE;
	}

	@Override
	protected boolean openWindow(UISession uiSession, UIState uiState) {
		Validate.notNull(uiSession, "uiSession is null");
		this.validateState(uiState);

		Player player = uiSession.getPlayer();
		Inventory inventory = Bukkit.createInventory(player, INVENTORY_SIZE, config.getTitle());

		ItemStack confirmItem = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
		confirmItem = ItemUtils.setDisplayNameAndLore(
				confirmItem,
				Messages.confirmationUiConfirm,
				config.getConfirmationLore()
		);
		inventory.setItem(SLOT_CONFIRM, confirmItem);

		ItemStack cancelItem = new ItemStack(Material.RED_STAINED_GLASS_PANE);
		cancelItem = ItemUtils.setDisplayNameAndLore(
				cancelItem,
				Messages.confirmationUiCancel,
				Messages.confirmationUiCancelLore
		);
		inventory.setItem(SLOT_CANCEL, cancelItem);

		return player.openInventory(inventory) != null;
	}

	@Override
	protected void onInventoryClickEarly(UISession uiSession, InventoryClickEvent event) {
		assert uiSession != null && event != null;
		event.setCancelled(true);
		if (this.isAutomaticShiftLeftClick()) {
			// Ignore automatically triggered shift left-clicks:
			return;
		}

		int slot = event.getRawSlot();
		if (slot == SLOT_CONFIRM) {
			playerDecided = true;
			uiSession.closeDelayedAndRunTask(action, location);
		} else if (slot == SLOT_CANCEL) {
			playerDecided = true;
			uiSession.closeDelayedAndRunTask(onCancelled, location);
		}
	}

	@Override
	protected void onInventoryDragEarly(UISession uiSession, InventoryDragEvent event) {
		assert uiSession != null && event != null;
		event.setCancelled(true);
	}

	@Override
	protected void onInventoryClose(UISession uiSession, @Nullable InventoryCloseEvent closeEvent) {
		assert uiSession != null;
		if (!playerDecided) {
			Player player = uiSession.getPlayer();
			TextUtils.sendMessage(player, Messages.confirmationUiAborted);
		}
	}
}
