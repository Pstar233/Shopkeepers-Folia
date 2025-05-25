package com.nisovin.shopkeepers.shopkeeper.player;

import java.util.List;

import com.nisovin.shopkeepers.SKShopkeepersPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.ShopkeepersPlugin;
import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import com.nisovin.shopkeepers.api.shopkeeper.player.PlayerShopkeeper;
import com.nisovin.shopkeepers.api.ui.UISession;
import com.nisovin.shopkeepers.api.util.UnmodifiableItemStack;
import com.nisovin.shopkeepers.config.Settings;
import com.nisovin.shopkeepers.config.Settings.DerivedSettings;
import com.nisovin.shopkeepers.currency.Currencies;
import com.nisovin.shopkeepers.currency.Currency;
import com.nisovin.shopkeepers.lang.Messages;
import com.nisovin.shopkeepers.shopkeeper.TradingRecipeDraft;
import com.nisovin.shopkeepers.ui.SKDefaultUITypes;
import com.nisovin.shopkeepers.ui.editor.ActionButton;
import com.nisovin.shopkeepers.ui.editor.Button;
import com.nisovin.shopkeepers.ui.editor.EditorHandler;
import com.nisovin.shopkeepers.ui.editor.EditorSession;
import com.nisovin.shopkeepers.ui.editor.ShopkeeperActionButton;
import com.nisovin.shopkeepers.ui.editor.TradingRecipesAdapter;
import com.nisovin.shopkeepers.util.annotations.ReadOnly;
import com.nisovin.shopkeepers.util.bukkit.PermissionUtils;
import com.nisovin.shopkeepers.util.bukkit.TextUtils;
import com.nisovin.shopkeepers.util.inventory.ItemUtils;
import com.nisovin.shopkeepers.util.java.StringUtils;
import com.nisovin.shopkeepers.util.java.Validate;
import com.nisovin.shopkeepers.util.logging.Log;

public abstract class PlayerShopEditorHandler extends EditorHandler {

	// Note: In the editor item1 is representing the low cost item and item2 the high cost item, but
	// in the corresponding trading recipe they will be swapped if they are both present.

	protected PlayerShopEditorHandler(
			AbstractPlayerShopkeeper shopkeeper,
			TradingRecipesAdapter tradingRecipesAdapter
	) {
		super(SKDefaultUITypes.EDITOR(), shopkeeper, tradingRecipesAdapter);
	}

	@Override
	public AbstractPlayerShopkeeper getShopkeeper() {
		return (AbstractPlayerShopkeeper) super.getShopkeeper();
	}

	@Override
	public boolean canAccess(Player player, boolean silent) {
		if (!super.canAccess(player, silent)) return false;

		// Check the owner:
		if (!this.getShopkeeper().isOwner(player)
				&& !PermissionUtils.hasPermission(player, ShopkeepersPlugin.BYPASS_PERMISSION)) {
			if (!silent) {
				this.debugNotOpeningUI(player, "Player is not owning this shop.");
				TextUtils.sendMessage(player, Messages.notOwner);
			}
			return false;
		}
		return true;
	}

	@Override
	protected void setupShopkeeperButtons() {
		super.setupShopkeeperButtons();
		this.addButtonOrIgnore(this.createContainerButton());
		this.addButtonOrIgnore(this.createTradeNotificationsButton());
	}

	protected @Nullable Button createContainerButton() {
		if (!Settings.enableContainerOptionOnPlayerShop) {
			return null;
		}
		return new ActionButton() {
			@Override
			public @Nullable ItemStack getIcon(EditorSession editorSession) {
				return DerivedSettings.containerButtonItem.createItemStack();
			}

			@Override
			protected boolean runAction(
					EditorSession editorSession,
					InventoryClickEvent clickEvent
			) {
				// 关闭 UI 还会触发当前编辑器状态的保存：
				editorSession.getUISession().closeDelayedAndRunTask(() -> {
					// Open the shop container inventory:
					Player player = editorSession.getPlayer();
					PlayerShopkeeper shopkeeper = getShopkeeper();
					if (!player.isValid() || !shopkeeper.isValid()) return;

					System.out.println("触发 runAction |");
					Bukkit.getRegionScheduler().execute(SKShopkeepersPlugin.getInstance(), shopkeeper.getLocation(), ()->{
						shopkeeper.openContainerWindow(player);
					});
				});
				return true;
			}
		};
	}

	protected @Nullable Button createTradeNotificationsButton() {
		if (!Settings.notifyShopOwnersAboutTrades) {
			return null;
		}
		return new ShopkeeperActionButton() {
			@Override
			public @Nullable ItemStack getIcon(EditorSession editorSession) {
				AbstractPlayerShopkeeper shopkeeper = (AbstractPlayerShopkeeper) this.getShopkeeper();
				ItemStack iconItem = Settings.tradeNotificationsItem.createItemStack();
				String state = shopkeeper.isNotifyOnTrades() ? Messages.stateEnabled : Messages.stateDisabled;
				String displayName = StringUtils.replaceArguments(Messages.buttonTradeNotifications,
						"state", state
				);
				List<? extends String> lore = StringUtils.replaceArguments(
						Messages.buttonTradeNotificationsLore,
						"state", state
				);
				ItemUtils.setDisplayNameAndLore(iconItem, displayName, lore);
				return iconItem;
			}

			@Override
			protected boolean runAction(
					EditorSession editorSession,
					InventoryClickEvent clickEvent
			) {
				AbstractPlayerShopkeeper shopkeeper = (AbstractPlayerShopkeeper) this.getShopkeeper();
				shopkeeper.setNotifyOnTrades(!shopkeeper.isNotifyOnTrades());
				return true;
			}
		};
	}

	@Override
	protected void onInventoryDragEarly(UISession uiSession, InventoryDragEvent event) {
		// Cancel all inventory clicks and handle everything on our own:
		// TODO Maybe allow certain inventory actions which only affect the player's inventory?
		event.setCancelled(true);
		super.onInventoryDragEarly(uiSession, event);
	}

	@Override
	protected void onInventoryClickEarly(UISession uiSession, InventoryClickEvent event) {
		// Cancel all inventory clicks and handle everything on our own:
		// TODO Maybe allow certain inventory actions which only affect the player's inventory (like
		// moving items around)?
		event.setCancelled(true);
		super.onInventoryClickEarly(uiSession, event);
	}

	// Returns the new item.
	// Returns null if the new item is empty or matches the empty slot item.
	protected @Nullable ItemStack updateItemAmountOnClick(
			InventoryClickEvent event,
			int minAmount,
			@Nullable UnmodifiableItemStack emptySlotItem
	) {
		Validate.isTrue(minAmount >= 0, "minAmount cannot be negative");
		assert event.isCancelled();
		// Ignore in certain situations:
		ItemStack clickedItem = event.getCurrentItem();
		if (ItemUtils.isEmpty(clickedItem) || ItemUtils.equals(emptySlotItem, clickedItem)) {
			return null;
		}

		clickedItem = Unsafe.assertNonNull(clickedItem);

		// Get new item amount:
		int currentItemAmount = clickedItem.getAmount();
		int newItemAmount = this.getNewAmountAfterEditorClick(
				event,
				currentItemAmount,
				minAmount,
				clickedItem.getMaxStackSize()
		);
		assert newItemAmount >= minAmount;
		assert newItemAmount <= clickedItem.getMaxStackSize();

		// Update item in inventory:
		if (newItemAmount == 0) {
			// Place empty slot item:
			event.setCurrentItem(ItemUtils.asItemStackOrNull(emptySlotItem));
			return null;
		} else {
			clickedItem.setAmount(newItemAmount);
			return clickedItem;
		}
	}

	protected void updateTradeCostItemOnClick(
			InventoryClickEvent event,
			@Nullable Currency currency,
			@Nullable UnmodifiableItemStack emptySlotItem
	) {
		assert event != null;
		assert event.isCancelled();
		// Ignore in certain situations:
		if (currency == null) return;

		// Get new item amount:
		ItemStack clickedItem = event.getCurrentItem(); // Can be null
		int currentItemAmount = 0;
		boolean isCurrencyItem = currency.getItemData().matches(clickedItem);
		if (isCurrencyItem) {
			assert clickedItem != null;
			currentItemAmount = clickedItem.getAmount();
		}
		int newItemAmount = this.getNewAmountAfterEditorClick(
				event,
				currentItemAmount,
				0,
				currency.getMaxStackSize()
		);
		assert newItemAmount >= 0;
		assert newItemAmount <= currency.getMaxStackSize();

		// Update item in inventory:
		if (newItemAmount == 0) {
			// Place empty slot item:
			event.setCurrentItem(ItemUtils.asItemStackOrNull(emptySlotItem));
		} else {
			if (isCurrencyItem) {
				assert clickedItem != null;
				// Only update the amount of the already existing currency item:
				clickedItem.setAmount(newItemAmount);
			} else {
				// Place a new currency item:
				ItemStack currencyItem = currency.getItemData().createItemStack(newItemAmount);
				event.setCurrentItem(currencyItem);
			}
		}
	}

	// Note: In case the cost is too large to represent, it sets the cost to zero.
	// (So opening and closing the editor window will remove the offer, instead of setting the costs
	// to a lower value than what was previously somehow specified)
	protected static TradingRecipeDraft createTradingRecipeDraft(
			@ReadOnly ItemStack resultItem,
			int cost
	) {
		ItemStack highCostItem = null;
		ItemStack lowCostItem = null;

		int remainingCost = cost;
		if (Currencies.isHighCurrencyEnabled()) {
			Currency highCurrency = Currencies.getHigh();
			int highCost = 0;
			if (remainingCost > Settings.highCurrencyMinCost) {
				highCost = Math.min(
						(remainingCost / highCurrency.getValue()),
						highCurrency.getMaxStackSize()
				);
			}
			if (highCost > 0) {
				remainingCost -= (highCost * highCurrency.getValue());
				highCostItem = Currencies.getHigh().getItemData().createItemStack(highCost);
			}
		}
		if (remainingCost > 0) {
			Currency baseCurrency = Currencies.getBase();
			if (remainingCost <= baseCurrency.getMaxStackSize()) {
				lowCostItem = Currencies.getBase().getItemData().createItemStack(remainingCost);
			} else {
				// Cost is too large to represent: Reset cost to zero.
				assert lowCostItem == null;
				highCostItem = null;
			}
		}

		return new TradingRecipeDraft(resultItem, lowCostItem, highCostItem);
	}

	protected static int getPrice(Shopkeeper shopkeeper, TradingRecipeDraft recipe) {
		Validate.notNull(recipe, "recipe is null");
		int price = 0;

		UnmodifiableItemStack item1 = recipe.getItem1();
		Currency currency1 = Currencies.match(item1);
		if (currency1 != null) {
			assert item1 != null;
			price += (currency1.getValue() * item1.getAmount());
		} else if (!ItemUtils.isEmpty(item1)) {
			// Unexpected.
			Log.debug(shopkeeper.getLogPrefix() + "Price item 1 does not match any currency!");
		}

		UnmodifiableItemStack item2 = recipe.getItem2();
		Currency currency2 = Currencies.match(item2);
		if (currency2 != null) {
			assert item2 != null;
			price += (currency2.getValue() * item2.getAmount());
		} else if (!ItemUtils.isEmpty(item2)) {
			// Unexpected.
			Log.debug(shopkeeper.getLogPrefix() + "Price item 2 does not match any currency!");
		}
		return price;
	}
}
