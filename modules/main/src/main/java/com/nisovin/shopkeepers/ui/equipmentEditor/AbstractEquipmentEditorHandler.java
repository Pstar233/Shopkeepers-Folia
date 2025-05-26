package com.nisovin.shopkeepers.ui.equipmentEditor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.DragType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.ShopkeepersPlugin;
import com.nisovin.shopkeepers.api.ui.UISession;
import com.nisovin.shopkeepers.api.util.UnmodifiableItemStack;
import com.nisovin.shopkeepers.lang.Messages;
import com.nisovin.shopkeepers.shopkeeper.player.PlaceholderItems;
import com.nisovin.shopkeepers.ui.AbstractUIType;
import com.nisovin.shopkeepers.ui.UIHandler;
import com.nisovin.shopkeepers.ui.UIHelpers;
import com.nisovin.shopkeepers.ui.state.UIState;
import com.nisovin.shopkeepers.util.annotations.ReadOnly;
import com.nisovin.shopkeepers.util.annotations.ReadWrite;
import com.nisovin.shopkeepers.util.inventory.ChestLayout;
import com.nisovin.shopkeepers.util.inventory.InventoryViewUtils;
import com.nisovin.shopkeepers.util.inventory.ItemUtils;
import com.nisovin.shopkeepers.util.java.EnumUtils;
import com.nisovin.shopkeepers.util.java.Validate;

import javax.swing.table.TableStringConverter;

public abstract class AbstractEquipmentEditorHandler extends UIHandler {

	// Assumption: These collections do not get externally modified while the editor is in-use!
	// Element order matches order in the editor.
	private final List<? extends EquipmentSlot> supportedSlots;
	private final Map<? extends EquipmentSlot, ? extends UnmodifiableItemStack> currentEquipment;
	private final BiConsumer<EquipmentSlot, @Nullable UnmodifiableItemStack> onEquipmentChanged;

	protected AbstractEquipmentEditorHandler(
			AbstractUIType uiType,
			List<? extends EquipmentSlot> supportedSlots,
			Map<? extends EquipmentSlot, ? extends UnmodifiableItemStack> currentEquipment,
			BiConsumer<EquipmentSlot, @Nullable UnmodifiableItemStack> onEquipmentChanged
	) {
		super(uiType);

		Validate.notNull(supportedSlots, "supportedSlots is null");
		Validate.notNull(currentEquipment, "currentEquipment is null");
		Validate.notNull(onEquipmentChanged, "onEquipmentChanged is null");

		this.supportedSlots = supportedSlots;
		this.currentEquipment = currentEquipment;
		this.onEquipmentChanged = onEquipmentChanged;
	}

	@Override
	public boolean canAccess(Player player, boolean silent) {
		return true;
	}

	@Override
	protected boolean isWindow(InventoryView view) {
		Validate.notNull(view, "view is null");
		return view.getType() == InventoryType.CHEST
				&& view.getTitle().equals(Messages.equipmentEditorTitle);
	}

	@Override
	protected boolean openWindow(UISession uiSession, UIState uiState) {
		Validate.notNull(uiSession, "uiSession is null");
		this.validateState(uiState);

		Player player = uiSession.getPlayer();

		int inventorySize = ChestLayout.getRequiredSlots(supportedSlots.size());
		Inventory inventory = Bukkit.createInventory(player, inventorySize, Messages.equipmentEditorTitle);

		for (int slotIndex = 0; slotIndex < supportedSlots.size(); slotIndex++) {
			EquipmentSlot equipmentSlot = supportedSlots.get(slotIndex);
			if (slotIndex >= inventorySize) break;

			@Nullable UnmodifiableItemStack equipmentItem = currentEquipment.get(equipmentSlot);
			ItemStack editorItem = this.toEditorEquipmentItem(equipmentSlot, ItemUtils.asItemStackOrNull(equipmentItem));
			inventory.setItem(slotIndex, editorItem);
		}

		return player.openInventory(inventory) != null;
	}

	private @Nullable ItemStack toEditorEquipmentItem(EquipmentSlot equipmentSlot, @ReadOnly @Nullable ItemStack item) {
		ItemStack editorItem;

		if (ItemUtils.isEmpty(item)) {
			editorItem = new ItemStack(Material.ARMOR_STAND);
		} else {
			assert item != null;
			editorItem = item.clone();
		}
		assert editorItem != null;

		this.setEditorEquipmentItemMeta(editorItem, equipmentSlot);

		return editorItem;
	}

	private void setEditorEquipmentItemMeta(@ReadWrite ItemStack item, EquipmentSlot equipmentSlot) {
		String displayName;
		switch (equipmentSlot.name()) {
		case "HAND":
			displayName = Messages.equipmentSlotMainhand;
			break;
		case "OFF_HAND":
			displayName = Messages.equipmentSlotOffhand;
			break;
		case "FEET":
			displayName = Messages.equipmentSlotFeet;
			break;
		case "LEGS":
			displayName = Messages.equipmentSlotLegs;
			break;
		case "CHEST":
			displayName = Messages.equipmentSlotChest;
			break;
		case "HEAD":
			displayName = Messages.equipmentSlotHead;
			break;
		case "BODY": // TODO Added in Bukkit 1.20.5
			displayName = Messages.equipmentSlotBody;
			break;
		case "SADDLE": // TODO Added in Bukkit 1.21.5
			displayName = Messages.equipmentSlotSaddle;
			break;
		default:
			// Fallback:
			displayName = EnumUtils.formatEnumName(equipmentSlot.name());
			break;
		}

		ItemUtils.setDisplayNameAndLore(
				item,
				displayName,
				Messages.equipmentSlotLore
		);
	}

	@Override
	protected void onInventoryClickEarly(UISession uiSession, InventoryClickEvent event) {
		assert uiSession != null && event != null;
		event.setCancelled(true);
		if (event.isShiftClick()) return; // Ignoring shift clicks
		if (this.isAutomaticShiftLeftClick()) {
			// Ignore automatically triggered shift left-clicks:
			return;
		}

		int rawSlot = event.getRawSlot();
		if (rawSlot < 0) return;

		InventoryView view = event.getView();

		if (InventoryViewUtils.isTopInventory(view, rawSlot)) {
			this.handleEditorInventoryClick(uiSession, event);
		} else if (InventoryViewUtils.isPlayerInventory(view, rawSlot)) {
			this.handlePlayerInventoryClick(event);
		}
	}

	private void handlePlayerInventoryClick(InventoryClickEvent event) {
		assert event.isCancelled();
		UIHelpers.swapCursor(event.getView(), event.getRawSlot());
	}

	private void handleEditorInventoryClick(UISession uiSession, InventoryClickEvent event) {
		assert event.isCancelled();
		InventoryView view = event.getView();
		this.handleEditorInventoryClick(
				uiSession,
				view,
				event.getRawSlot(),
				event.isLeftClick(),
				event.isRightClick(),
				() -> ItemUtils.cloneOrNullIfEmpty(view.getCursor())
		);
	}

	private void handleEditorInventoryClick(
			UISession uiSession,
			InventoryView view,
			int rawSlot,
			boolean leftClick,
			boolean rightClick,
			Supplier<@Nullable ItemStack> getCursorCopy
	) {
		// Assert: The involved inventory event was cancelled.
		if (rawSlot >= supportedSlots.size()) return;

		EquipmentSlot equipmentSlot = supportedSlots.get(rawSlot);
		Inventory inventory = view.getTopInventory();

		if (rightClick) {
			// Clear the equipment slot:
			Bukkit.getRegionScheduler().run(ShopkeepersPlugin.getInstance(), view.getPlayer().getLocation(), task -> {
				if (view.getPlayer().getOpenInventory() != view) return;

				inventory.setItem(rawSlot, this.toEditorEquipmentItem(equipmentSlot, null));
				onEquipmentChanged(uiSession, equipmentSlot, null);
			});
			return;
		}

		ItemStack cursorClone = getCursorCopy.get();
		if (leftClick && !ItemUtils.isEmpty(cursorClone)) {
			assert cursorClone != null;
			// Place the item from the cursor:
			Bukkit.getRegionScheduler().run(ShopkeepersPlugin.getInstance(), view.getPlayer().getLocation(), task -> {
				if (view.getPlayer().getOpenInventory() != view) return;

				cursorClone.setAmount(1);

				// Replace placeholder item, if this is one:
				ItemStack substitutedItem = PlaceholderItems.replaceNonNull(cursorClone);

				// Inform about the new equipment item:
				// No item copy required: The item is already a copy, and for the item in the editor
				// we create a separate copy subsequently.
				onEquipmentChanged(uiSession, equipmentSlot, UnmodifiableItemStack.of(substitutedItem));

				// Update the item in the editor:
				// This copies the item internally (but irrelevant, because we already create a copy
				// for the editor item anyway):
				inventory.setItem(rawSlot, this.toEditorEquipmentItem(equipmentSlot, substitutedItem));
			});
		}
	}

	protected void onEquipmentChanged(UISession uiSession, EquipmentSlot slot, @Nullable UnmodifiableItemStack item) {
		onEquipmentChanged.accept(slot, item);
	}

	@Override
	protected void onInventoryDragEarly(UISession uiSession, InventoryDragEvent event) {
		assert uiSession != null && event != null;
		event.setCancelled(true);
		ItemStack cursorClone = event.getOldCursor(); // Already a copy
		if (ItemUtils.isEmpty(cursorClone)) return;
		assert cursorClone != null;

		Set<Integer> rawSlots = event.getRawSlots();
		if (rawSlots.size() != 1) return;

		int rawSlot = rawSlots.iterator().next();
		if (rawSlot < 0) return;

		InventoryView view = event.getView();

		if (InventoryViewUtils.isTopInventory(view, rawSlot)) {
			boolean isLeftClick = event.getType() == DragType.EVEN;
			boolean isRightClick = event.getType() == DragType.SINGLE;
			this.handleEditorInventoryClick(
					uiSession,
					view,
					rawSlot,
					isLeftClick,
					isRightClick,
					() -> cursorClone
			);
		} else {
			if (InventoryViewUtils.isPlayerInventory(view, rawSlot)) {
				// The cancelled drag event resets the cursor afterwards, so we need this delay:
				UIHelpers.swapCursorDelayed(view, rawSlot);
			}
		}
	}

	@Override
	protected void onInventoryClose(UISession uiSession, @Nullable InventoryCloseEvent closeEvent) {
		// TODO Return to the editor if the user closed the inventory? But we cannot properly detect
		// currently whether the player themselves close the inventory.
		// Nothing to do by default.
	}
}
