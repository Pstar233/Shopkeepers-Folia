package com.nisovin.shopkeepers.ui.trading;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.PlayerInventory;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.events.ShopkeeperTradeEvent;
import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import com.nisovin.shopkeepers.api.shopkeeper.TradingRecipe;
import com.nisovin.shopkeepers.api.util.UnmodifiableItemStack;
import com.nisovin.shopkeepers.util.inventory.ItemUtils;
import com.nisovin.shopkeepers.util.java.KeyValueStore;
import com.nisovin.shopkeepers.util.java.MapBasedKeyValueStore;
import com.nisovin.shopkeepers.util.java.Validate;

/**
 * Information about a trade that is currently being processed.
 */
public final class Trade {

	private final TradingContext tradingContext;
	private final int tradeNumber;
	private final TradingRecipe tradingRecipe;
	private final ItemStack offeredItem1;
	private final @Nullable ItemStack offeredItem2;
	private final boolean swappedItemOrder;
	private final KeyValueStore metadata = new MapBasedKeyValueStore();
	private final ShopkeeperTradeEvent tradeEvent;

	private boolean tradeEventCalled = false;

	/**
	 * Creates a new {@link Trade}.
	 * 
	 * @param tradingContext
	 *            the trading context, not <code>null</code>
	 * @param tradeNumber
	 *            the trade number
	 * @param tradingRecipe
	 *            the used trading recipe, not <code>null</code>
	 * @param offeredItem1
	 *            the first offered item, not <code>null</code> or empty
	 * @param offeredItem2
	 *            the second offered item, can be <code>null</code>
	 * @param swappedItemOrder
	 *            <code>true</code> if the offered items are placed in reverse order inside the
	 *            merchant inventory
	 */
	Trade(
			TradingContext tradingContext,
			int tradeNumber,
			TradingRecipe tradingRecipe,
			ItemStack offeredItem1,
			@Nullable ItemStack offeredItem2,
			boolean swappedItemOrder
	) {
		Validate.notNull(tradingContext, "tradingContext is null");
		Validate.isTrue(tradeNumber >= 1, "tradeNumber is less than 1");
		Validate.notNull(tradingRecipe, "tradingRecipe is null");
		Validate.notNull(offeredItem1, "offeredItem1 is null");
		this.tradingContext = tradingContext;
		this.tradeNumber = tradeNumber;
		this.tradingRecipe = tradingRecipe;
		this.offeredItem1 = offeredItem1;
		this.offeredItem2 = offeredItem2;
		this.swappedItemOrder = swappedItemOrder;

		// Prepare the offered items for the trade event: Clone and ensure that the stack sizes
		// match the trading recipe.
		ItemStack eventOfferedItem1 = ItemUtils.copyWithAmount(
				offeredItem1,
				tradingRecipe.getItem1().getAmount()
		);
		ItemStack eventOfferedItem2 = ItemUtils.cloneOrNullIfEmpty(offeredItem2);
		if (eventOfferedItem2 != null) {
			// Not null: Minecraft disables the trade if there is second offered item but the trade
			// only expects a single item.
			UnmodifiableItemStack recipeItem2 = Unsafe.assertNonNull(tradingRecipe.getItem2());
			eventOfferedItem2.setAmount(recipeItem2.getAmount());
		}

		this.tradeEvent = new ShopkeeperTradeEvent(
				tradingContext.getShopkeeper(),
				tradingContext.getTradingPlayer(),
				tradingContext.getInventoryClickEvent(),
				tradingRecipe,
				UnmodifiableItemStack.ofNonNull(eventOfferedItem1),
				UnmodifiableItemStack.of(eventOfferedItem2),
				swappedItemOrder
		);
	}

	/**
	 * Gets the {@link TradingContext}.
	 * 
	 * @return the trading context, not <code>null</code>
	 */
	public TradingContext getTradingContext() {
		return tradingContext;
	}

	/**
	 * Gets the involved {@link Shopkeeper}.
	 * 
	 * @return the shopkeeper, not <code>null</code>
	 */
	public Shopkeeper getShopkeeper() {
		return tradingContext.getShopkeeper();
	}

	/**
	 * Gets the involved {@link InventoryClickEvent}.
	 * <p>
	 * Do not modify this event or any of the involved items! The event has to be kept cancelled!
	 * 
	 * @return the inventory click event, not <code>null</code>
	 */
	public InventoryClickEvent getInventoryClickEvent() {
		return tradingContext.getInventoryClickEvent();
	}

	/**
	 * Gets the involved {@link MerchantInventory}.
	 * 
	 * @return the merchant inventory, not <code>null</code>
	 */
	public MerchantInventory getMerchantInventory() {
		return tradingContext.getMerchantInventory();
	}

	/**
	 * Gets the trading {@link Player}.
	 * 
	 * @return the trading player, not <code>null</code>
	 */
	public Player getTradingPlayer() {
		return tradingContext.getTradingPlayer();
	}

	/**
	 * Gets the {@link PlayerInventory} of the trading player.
	 * 
	 * @return the player inventory, not <code>null</code>
	 */
	public PlayerInventory getPlayerInventory() {
		return tradingContext.getPlayerInventory();
	}

	/**
	 * Gets the number of this trade among the trades that are triggered by the same inventory
	 * click.
	 * <p>
	 * This returns {@code 1} if this is the first trade that is being processed.
	 * 
	 * @return the number of this trade
	 */
	public int getTradeNumber() {
		return tradeNumber;
	}

	/**
	 * Gets the used {@link TradingRecipe}.
	 * 
	 * @return the trading recipe, not <code>null</code>
	 */
	public TradingRecipe getTradingRecipe() {
		return tradingRecipe;
	}

	/**
	 * Gets the item offered by the player that matches the first required item of the
	 * {@link #getTradingRecipe() used trading recipe}.
	 * <p>
	 * Due to the possibility of the input items being matched to the required items in
	 * {@link #isItemOrderSwapped() reverse order}, the returned item stack is not necessarily the
	 * item in the first input slot of the merchant inventory.
	 * <p>
	 * The type of the returned item equals that of the first required item of the used trading
	 * recipe, but its metadata might be different. However, depending on the item matching rules of
	 * the used Minecraft version and the Shopkeepers settings (e.g. with strict item comparisons
	 * being disabled), the offered item might still be accepted for the trade nevertheless.
	 * <p>
	 * The returned item stack is not a copy and might get modified once the trade is applied! The
	 * item stack size matches the original stack size of the item used by the player.
	 * 
	 * @return the offered item that matches the first required item, not <code>null</code> or empty
	 */
	public ItemStack getOfferedItem1() {
		return offeredItem1;
	}

	/**
	 * Gets the item offered by the player that matches the second required item of the
	 * {@link #getTradingRecipe() used trading recipe}.
	 * <p>
	 * Due to the possibility of the input items being matched to the required items in
	 * {@link #isItemOrderSwapped() reverse order}, the returned item stack is not necessarily the
	 * item in the second input slot of the merchant inventory.
	 * <p>
	 * The type of the returned item equals that of the second required item of the used trading
	 * recipe, but its metadata might be different. However, depending on the item matching rules of
	 * the used Minecraft version and the Shopkeepers settings (e.g. with strict item comparisons
	 * being disabled), the offered item might still be accepted for the trade nevertheless.
	 * <p>
	 * The returned item stack is not a copy and might get modified once the trade is applied! The
	 * item stack size matches the original stack size of the item used by the player.
	 * 
	 * @return the offered item that matches the second required item, or <code>null</code> if the
	 *         trade requires no second item
	 */
	public @Nullable ItemStack getOfferedItem2() {
		return offeredItem2;
	}

	/**
	 * Gets whether the {@link #getOfferedItem1() first} and {@link #getOfferedItem2() second}
	 * offered items are placed in reverse order inside the input slots of the merchant inventory.
	 * 
	 * @return <code>true</code> if the input items are matched in reverse order to the used trading
	 *         recipe
	 */
	public boolean isItemOrderSwapped() {
		return swappedItemOrder;
	}

	/**
	 * Gets the {@link KeyValueStore} that stores additional arbitrary metadata related to this
	 * {@link Trade}.
	 * 
	 * @return the metadata key-value store, not <code>null</code>
	 */
	public KeyValueStore getMetadata() {
		return metadata;
	}

	/**
	 * Gets the corresponding {@link ShopkeeperTradeEvent}.
	 * 
	 * @return the trade event, not <code>null</code>
	 */
	public ShopkeeperTradeEvent getTradeEvent() {
		return tradeEvent;
	}

	/**
	 * Calls the {@link #getTradeEvent() trade event}, giving other plugins a chance to cancel or
	 * alter the trade before it gets applied.
	 * 
	 * @return the trade event
	 */
	public ShopkeeperTradeEvent callTradeEvent() {
		tradeEventCalled = true;

		Bukkit.getServer().getPluginManager().callEvent(tradeEvent);
		return tradeEvent;
	}

	/**
	 * Whether the {@link #getTradeEvent() trade event} has already been called.
	 * 
	 * @return <code>true</code> if the trade event was already called
	 */
	public boolean isTradeEventCalled() {
		return tradeEventCalled;
	}
}
