package com.nisovin.shopkeepers.items;

import org.bukkit.Bukkit;
import org.checkerframework.checker.nullness.qual.PolyNull;

import com.nisovin.shopkeepers.api.events.UpdateItemEvent;
import com.nisovin.shopkeepers.api.util.UnmodifiableItemStack;
import com.nisovin.shopkeepers.util.inventory.ItemData;
import com.nisovin.shopkeepers.util.inventory.ItemUtils;

/**
 * 与项目更新相关的帮助程序。请参阅 {@link UpdateItemEvent}。
 */
public class ItemUpdates {

	/**
	 * Calls an {@link UpdateItemEvent} for the given non-empty(!) item.
	 * 
	 * @param item
	 *            the item to call an item update event for, not <code>null</code> or empty
	 * @return the called {@link UpdateItemEvent}
	 */
	public static UpdateItemEvent callUpdateItemEvent(UnmodifiableItemStack item) {
		UpdateItemEvent updateItemEvent = new UpdateItemEvent(item);
		Bukkit.getPluginManager().callEvent(updateItemEvent);
		return updateItemEvent;
	}

	/**
	 * Calls an {@link UpdateItemEvent} for the given item, but only if it is not empty.
	 * 
	 * @param item
	 *            the item to call an item update event for
	 * @return the updated item, or the given item instance itself if it was not altered
	 */
	public static @PolyNull UnmodifiableItemStack updateItem(@PolyNull UnmodifiableItemStack item) {
		if (ItemUtils.isEmpty(item)) return item;
		assert item != null;

		UpdateItemEvent updateItemEvent = callUpdateItemEvent(item);
		if (!updateItemEvent.isItemAltered()) return item;

		// Item copy to ensure the item data is immutable:
		return ItemUtils.nonNullUnmodifiableClone(ItemUtils.asItemStack(updateItemEvent.getItem()));
	}

	/**
	 * Calls an {@link UpdateItemEvent} for the given item data, but only if it is not empty.
	 * 
	 * @param itemData
	 *            the item data to call an item update event for
	 * @return the updated item data, or the given item data instance itself if it was not altered
	 */
	public static @PolyNull ItemData updateItemData(@PolyNull ItemData itemData) {
		var item = itemData == null ? null : itemData.asUnmodifiableItemStack();
		if (ItemUtils.isEmpty(item)) return itemData;
		assert item != null;

		UpdateItemEvent updateItemEvent = callUpdateItemEvent(item);
		if (!updateItemEvent.isItemAltered()) return itemData;

		// Item copy to ensure the item data is immutable:
		return new ItemData(ItemUtils.asItemStack(updateItemEvent.getItem()));
	}

	private ItemUpdates() {
	}
}
