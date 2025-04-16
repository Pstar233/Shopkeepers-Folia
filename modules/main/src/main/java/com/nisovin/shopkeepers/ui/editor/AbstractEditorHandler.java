package com.nisovin.shopkeepers.ui.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.api.ui.UISession;
import com.nisovin.shopkeepers.api.util.UnmodifiableItemStack;
import com.nisovin.shopkeepers.config.Settings;
import com.nisovin.shopkeepers.lang.Messages;
import com.nisovin.shopkeepers.shopkeeper.TradingRecipeDraft;
import com.nisovin.shopkeepers.ui.AbstractUIType;
import com.nisovin.shopkeepers.ui.UIHandler;
import com.nisovin.shopkeepers.ui.state.UIState;
import com.nisovin.shopkeepers.ui.villager.editor.VillagerEditorHandler;
import com.nisovin.shopkeepers.util.annotations.ReadOnly;
import com.nisovin.shopkeepers.util.bukkit.SoundEffect;
import com.nisovin.shopkeepers.util.inventory.InventoryViewUtils;
import com.nisovin.shopkeepers.util.inventory.ItemUtils;
import com.nisovin.shopkeepers.util.java.MathUtils;
import com.nisovin.shopkeepers.util.java.StringUtils;
import com.nisovin.shopkeepers.util.java.Validate;

/**
 * Base class for editor UIs which support editing multiple pages of trades and offer additional
 * editor buttons.
 * <p>
 * For example used by {@link EditorHandler} and {@link VillagerEditorHandler}.
 */
public abstract class AbstractEditorHandler extends UIHandler {

	private static final SoundEffect PAGE_TURN_SOUND = new SoundEffect(Sound.ITEM_BOOK_PAGE_TURN);

	protected static final int COLUMNS_PER_ROW = 9;
	// 9 columns, column = [0,8]
	protected static final int TRADES_COLUMNS = COLUMNS_PER_ROW;

	protected static final int TRADES_ROW_1_START = 0;
	protected static final int TRADES_ROW_1_END = TRADES_ROW_1_START + TRADES_COLUMNS - 1;
	protected static final int TRADES_ROW_2_START = TRADES_ROW_1_END + (COLUMNS_PER_ROW - TRADES_COLUMNS) + 1;
	protected static final int TRADES_ROW_2_END = TRADES_ROW_2_START + TRADES_COLUMNS - 1;
	protected static final int TRADES_ROW_3_START = TRADES_ROW_2_END + (COLUMNS_PER_ROW - TRADES_COLUMNS) + 1;
	protected static final int TRADES_ROW_3_END = TRADES_ROW_3_START + TRADES_COLUMNS - 1;

	protected static final int TRADES_PAGE_BAR_START = TRADES_ROW_3_END + (COLUMNS_PER_ROW - TRADES_COLUMNS) + 1;
	protected static final int TRADES_PAGE_BAR_END = TRADES_PAGE_BAR_START + TRADES_COLUMNS - 1;
	protected static final int TRADES_PAGE_ICON = TRADES_PAGE_BAR_START + (TRADES_PAGE_BAR_END - TRADES_PAGE_BAR_START) / 2;
	protected static final int TRADES_SETUP_ICON = TRADES_PAGE_ICON - 1;

	protected static final int BUTTONS_START = TRADES_PAGE_BAR_END + (COLUMNS_PER_ROW - TRADES_COLUMNS) + 1;
	// TODO If there are more buttons than we can fit into two rows, move the excess buttons into a
	// separate (paged) inventory view and add an editor button that opens it.
	protected static final int BUTTON_MAX_ROWS = 2;

	// slot = column + offset:
	protected static final int RESULT_ITEM_OFFSET = TRADES_ROW_1_START;
	protected static final int ITEM_1_OFFSET = TRADES_ROW_3_START;
	protected static final int ITEM_2_OFFSET = TRADES_ROW_2_START;

	private final @Nullable Button[] tradesPageBarButtons = new @Nullable Button[TRADES_PAGE_BAR_END - TRADES_PAGE_BAR_START + 1];
	private final List<Button> buttons = new ArrayList<>();
	private int buttonRows = 1;
	private final @Nullable Button[] bakedButtons = new @Nullable Button[BUTTON_MAX_ROWS * COLUMNS_PER_ROW];
	private boolean dirtyButtons = false;
	private boolean setup = false; // lazy setup

	protected final TradingRecipesAdapter tradingRecipesAdapter;

	private final Map<UUID, EditorSession> editorSessions = new HashMap<>();

	protected AbstractEditorHandler(
			AbstractUIType uiType,
			TradingRecipesAdapter tradingRecipesAdapter
	) {
		super(uiType);
		Validate.notNull(tradingRecipesAdapter, "tradingRecipesAdapter is null");
		this.tradingRecipesAdapter = tradingRecipesAdapter;
	}

	/**
	 * Performs any setup that has to happen once for this UI.
	 * <p>
	 * This may for example set up the UI contents, such as registering buttons.
	 * <p>
	 * Setup is performed lazily, before the first time the UI is opened.
	 */
	private void setup() {
		if (setup) return;
		setup = true;
		this.setupTradesPageBarButtons();
		this.setupButtons();
	}

	// INVENTORY LAYOUT

	protected int getInventorySize() {
		return COLUMNS_PER_ROW * (4 + this.getButtonRows());
	}

	protected boolean isResultRow(int rawSlot) {
		return rawSlot >= TRADES_ROW_1_START && rawSlot <= TRADES_ROW_1_END;
	}

	protected boolean isItem1Row(int rawSlot) {
		return rawSlot >= TRADES_ROW_3_START && rawSlot <= TRADES_ROW_3_END;
	}

	protected boolean isItem2Row(int rawSlot) {
		return rawSlot >= TRADES_ROW_2_START && rawSlot <= TRADES_ROW_2_END;
	}

	protected boolean isTradesArea(int rawSlot) {
		return this.isResultRow(rawSlot) || this.isItem1Row(rawSlot) || this.isItem2Row(rawSlot);
	}

	// First column has index 0.
	// Only guaranteed to return valid results if the slot is within the trades area.
	protected int getTradeColumn(int rawSlot) {
		return rawSlot % 9;
	}

	protected boolean isTradeColumn(int column) {
		return column >= 0 && column < TRADES_COLUMNS;
	}

	protected int getResultItemSlot(int column) {
		return column + RESULT_ITEM_OFFSET;
	}

	protected int getItem1Slot(int column) {
		return column + ITEM_1_OFFSET;
	}

	protected int getItem2Slot(int column) {
		return column + ITEM_2_OFFSET;
	}

	protected boolean isTradesPageBar(int rawSlot) {
		return rawSlot >= TRADES_PAGE_BAR_START && rawSlot <= TRADES_PAGE_BAR_END;
	}

	protected boolean isButtonArea(int rawSlot) {
		return rawSlot >= BUTTONS_START && rawSlot <= this.getButtonsEnd();
	}

	// Depends on the number of buttons rows currently used:
	protected int getButtonsEnd() {
		return BUTTONS_START + (this.getButtonRows() * COLUMNS_PER_ROW) - 1;
	}

	// TRADES AREA

	// assert: [1, 10].
	protected int getMaxTradesPages() {
		return Settings.maxTradesPages;
	}

	/**
	 * Gets the {@link TradingRecipeDraft} that is used for trade columns that don't contain any
	 * trade yet.
	 * <p>
	 * This is expected to always return the same placeholder items.
	 * <p>
	 * The placeholder items are expected to not match any items that players are able to set up
	 * trades with.
	 * 
	 * @return the {@link TradingRecipeDraft} to use for empty trade columns, not <code>null</code>
	 */
	protected TradingRecipeDraft getEmptyTrade() {
		return TradingRecipeDraft.EMPTY;
	}

	/**
	 * Gets the items that are used for empty slots of partially set up trades.
	 * <p>
	 * This is expected to always return the same placeholder items.
	 * <p>
	 * The placeholder items are expected to not match any items that players are able to set up
	 * trades with.
	 * 
	 * @return a {@link TradingRecipeDraft} with the items to use for empty slots of partially set
	 *         up trades, not <code>null</code>
	 */
	protected TradingRecipeDraft getEmptyTradeSlotItems() {
		return TradingRecipeDraft.EMPTY;
	}

	private boolean isEmptyResultItem(@ReadOnly @Nullable ItemStack slotItem) {
		ItemStack item = ItemUtils.getNullIfEmpty(slotItem);
		if (item == null) return true;
		if (ItemUtils.equals(this.getEmptyTrade().getResultItem(), item)) return true;
		if (ItemUtils.equals(this.getEmptyTradeSlotItems().getResultItem(), item)) return true;
		return false;
	}

	private boolean isEmptyItem1(@ReadOnly @Nullable ItemStack slotItem) {
		ItemStack item = ItemUtils.getNullIfEmpty(slotItem);
		if (item == null) return true;
		if (ItemUtils.equals(this.getEmptyTrade().getItem1(), item)) return true;
		if (ItemUtils.equals(this.getEmptyTradeSlotItems().getItem1(), item)) return true;
		return false;
	}

	private boolean isEmptyItem2(@ReadOnly @Nullable ItemStack slotItem) {
		ItemStack item = ItemUtils.getNullIfEmpty(slotItem);
		if (item == null) return true;
		if (ItemUtils.equals(this.getEmptyTrade().getItem2(), item)) return true;
		if (ItemUtils.equals(this.getEmptyTradeSlotItems().getItem2(), item)) return true;
		return false;
	}

	// Returns null if the slot is empty or if the item matches an empty slot placeholder item.
	protected final @Nullable ItemStack getTradeResultItem(Inventory inventory, int column) {
		assert inventory != null;
		assert this.isTradeColumn(column);
		ItemStack item = inventory.getItem(this.getResultItemSlot(column));
		if (this.isEmptyResultItem(item)) return null;
		return item;
	}

	// Returns null if the slot is empty or if the item matches an empty slot placeholder item.
	protected final @Nullable ItemStack getTradeItem1(Inventory inventory, int column) {
		assert inventory != null;
		assert this.isTradeColumn(column);
		ItemStack item = inventory.getItem(this.getItem1Slot(column));
		if (this.isEmptyItem1(item)) return null;
		return item;
	}

	// Returns null if the slot is empty or if the item matches an empty slot placeholder item.
	protected final @Nullable ItemStack getTradeItem2(Inventory inventory, int column) {
		assert inventory != null;
		assert this.isTradeColumn(column);
		ItemStack item = inventory.getItem(this.getItem2Slot(column));
		if (this.isEmptyItem2(item)) return null;
		return item;
	}

	// Use TradingRecipeDraft#EMPTY to insert an empty trade column.
	protected void setTradeColumn(Inventory inventory, int column, TradingRecipeDraft recipe) {
		assert inventory != null;
		assert this.isTradeColumn(column);
		assert recipe != null;

		TradingRecipeDraft emptySlotItems;
		if (recipe.isEmpty()) {
			emptySlotItems = this.getEmptyTrade();
		} else {
			emptySlotItems = this.getEmptyTradeSlotItems();
		}

		// Insert placeholders for empty slots:
		UnmodifiableItemStack resultItem = ItemUtils.getFallbackIfNull(
				recipe.getResultItem(),
				emptySlotItems.getResultItem()
		);
		UnmodifiableItemStack item1 = ItemUtils.getFallbackIfNull(
				recipe.getItem1(),
				emptySlotItems.getItem1()
		);
		UnmodifiableItemStack item2 = ItemUtils.getFallbackIfNull(
				recipe.getItem2(),
				emptySlotItems.getItem2()
		);

		// The inventory implementations create NMS copies of the items, so we do not need to copy
		// them ourselves here:
		inventory.setItem(this.getResultItemSlot(column), ItemUtils.asItemStackOrNull(resultItem));
		inventory.setItem(this.getItem1Slot(column), ItemUtils.asItemStackOrNull(item1));
		inventory.setItem(this.getItem2Slot(column), ItemUtils.asItemStackOrNull(item2));
	}

	// TODO Avoid creating new TradingRecipeDraft objects here and instead update the drafts of the
	// session?
	// This replaces items matching the empty slot placeholders with null items in the returned
	// TradingRecipeDraft.
	protected TradingRecipeDraft getTradingRecipe(Inventory inventory, int column) {
		assert inventory != null;
		assert this.isTradeColumn(column);
		ItemStack resultItem = this.getTradeResultItem(inventory, column);
		ItemStack item1 = this.getTradeItem1(inventory, column);
		ItemStack item2 = this.getTradeItem2(inventory, column);
		return new TradingRecipeDraft(resultItem, item1, item2);
	}

	protected final void updateTradeColumn(Inventory inventory, int column) {
		TradingRecipeDraft recipe = this.getTradingRecipe(inventory, column);
		this.setTradeColumn(inventory, column, recipe);
	}

	protected final boolean isEmptyTrade(Inventory inventory, int column) {
		assert inventory != null;
		assert this.isTradeColumn(column);
		ItemStack resultItem = this.getTradeResultItem(inventory, column);
		if (resultItem != null) return false;
		ItemStack item1 = this.getTradeItem1(inventory, column);
		if (item1 != null) return false;
		ItemStack item2 = this.getTradeItem2(inventory, column);
		if (item2 != null) return false;
		return true;
	}

	// EDITOR BUTTONS

	private @Nullable Button[] getTradesPageBarButtons() {
		this.setupTradesPageBarButtons();
		return tradesPageBarButtons;
	}

	private @Nullable Button getTradesPageBarButton(int rawSlot) {
		if (!this.isTradesPageBar(rawSlot)) return null;
		return this._getTradesPageBarButton(rawSlot);
	}

	private @Nullable Button _getTradesPageBarButton(int rawSlot) {
		assert this.isTradesPageBar(rawSlot);
		return tradesPageBarButtons[rawSlot - TRADES_PAGE_BAR_START];
	}

	protected void setupTradesPageBarButtons() {
		Button prevPageButton = this.createPrevPageButton();
		prevPageButton.setSlot(TRADES_PAGE_BAR_START);
		tradesPageBarButtons[0] = prevPageButton;

		Button tradeSetupButton = this.createTradeSetupButton();
		tradeSetupButton.setSlot(TRADES_SETUP_ICON);
		tradesPageBarButtons[TRADES_SETUP_ICON - TRADES_PAGE_BAR_START] = tradeSetupButton;

		Button currentPageButton = this.createCurrentPageButton();
		currentPageButton.setSlot(TRADES_PAGE_ICON);
		tradesPageBarButtons[TRADES_PAGE_ICON - TRADES_PAGE_BAR_START] = currentPageButton;

		Button nextPageButton = this.createNextPageButton();
		nextPageButton.setSlot(TRADES_PAGE_BAR_END);
		tradesPageBarButtons[TRADES_PAGE_BAR_END - TRADES_PAGE_BAR_START] = nextPageButton;
	}

	protected Button createPrevPageButton() {
		return new ActionButton() {
			@Override
			public @Nullable ItemStack getIcon(EditorSession editorSession) {
				int page = editorSession.getCurrentPage();
				if (page <= 1) return null;
				return createPrevPageIcon(page);
			}

			@Override
			protected void playButtonClickSound(Player player, boolean actionSuccess) {
				if (actionSuccess) {
					PAGE_TURN_SOUND.play(player);
				}
			}

			@Override
			protected boolean runAction(
					EditorSession editorSession,
					InventoryClickEvent clickEvent
			) {
				// Ignore double clicks:
				if (clickEvent.getClick() == ClickType.DOUBLE_CLICK) return false;

				// Switch to previous page:
				int currentPage = editorSession.getCurrentPage();
				return switchPage(editorSession, currentPage - 1, true);
			}
		};
	}

	protected Button createNextPageButton() {
		return new ActionButton() {
			@Override
			public @Nullable ItemStack getIcon(EditorSession editorSession) {
				int page = editorSession.getCurrentPage();
				if (page >= getMaxTradesPages()) return null;
				return createNextPageIcon(page);
			}

			@Override
			protected void playButtonClickSound(Player player, boolean actionSuccess) {
				if (actionSuccess) {
					PAGE_TURN_SOUND.play(player);
				}
			}

			@Override
			protected boolean runAction(
					EditorSession editorSession,
					InventoryClickEvent clickEvent
			) {
				// Ignore double clicks:
				if (clickEvent.getClick() == ClickType.DOUBLE_CLICK) return false;

				// Switch to next page:
				int currentPage = editorSession.getCurrentPage();
				return switchPage(editorSession, currentPage + 1, true);
			}
		};
	}

	protected Button createCurrentPageButton() {
		return new Button() {
			@Override
			public @Nullable ItemStack getIcon(EditorSession editorSession) {
				int page = editorSession.getCurrentPage();
				return createCurrentPageIcon(page);
			}

			@Override
			protected void onClick(EditorSession editorSession, InventoryClickEvent clickEvent) {
				// Current page button: Does nothing.
			}
		};
	}

	protected Button createTradeSetupButton() {
		return new Button() {
			@Override
			public @Nullable ItemStack getIcon(EditorSession editorSession) {
				return createTradeSetupIcon();
			}

			@Override
			protected void onClick(EditorSession editorSession, InventoryClickEvent clickEvent) {
				// Trade setup button: Does nothing.
			}
		};
	}

	protected ItemStack createPrevPageIcon(int page) {
		int prevPage = 1;
		String prevPageText = "-";
		if (page > 1) {
			prevPage = (page - 1);
			prevPageText = String.valueOf(prevPage);
		}
		String itemName = StringUtils.replaceArguments(Messages.buttonPreviousPage,
				"prev_page", prevPageText,
				"page", page,
				"max_page", getMaxTradesPages()
		);
		ItemStack item = Settings.previousPageItem.createItemStack();
		ItemUtils.setItemMeta(item, itemName, Messages.buttonPreviousPageLore, ItemUtils.MAX_STACK_SIZE);
		// Note: Can exceed the item's natural max stack size.
		item.setAmount(MathUtils.trim(prevPage, 1, ItemUtils.MAX_STACK_SIZE));
		return item;
	}

	protected ItemStack createNextPageIcon(int page) {
		int nextPage = 1;
		String nextPageText = "-";
		if (page < getMaxTradesPages()) {
			nextPage = (page + 1);
			nextPageText = String.valueOf(nextPage);
		}
		String itemName = StringUtils.replaceArguments(Messages.buttonNextPage,
				"next_page", nextPageText,
				"page", page,
				"max_page", getMaxTradesPages()
		);
		ItemStack item = Settings.nextPageItem.createItemStack();
		ItemUtils.setItemMeta(item, itemName, Messages.buttonNextPageLore, ItemUtils.MAX_STACK_SIZE);
		// Note: Can exceed the item's natural max stack size.
		item.setAmount(MathUtils.trim(nextPage, 1, ItemUtils.MAX_STACK_SIZE));
		return item;
	}

	protected ItemStack createCurrentPageIcon(int page) {
		String itemName = StringUtils.replaceArguments(Messages.buttonCurrentPage,
				"page", page,
				"max_page", getMaxTradesPages()
		);
		ItemStack item = Settings.currentPageItem.createItemStack();
		ItemUtils.setItemMeta(item, itemName, Messages.buttonCurrentPageLore, ItemUtils.MAX_STACK_SIZE);
		// Note: Can exceed the item's natural max stack size.
		item.setAmount(MathUtils.trim(page, 1, ItemUtils.MAX_STACK_SIZE));
		return item;
	}

	protected abstract ItemStack createTradeSetupIcon();

	private void bakeButtons() {
		if (!dirtyButtons) return;

		// Reset buttons:
		buttons.forEach(button -> button.setSlot(Button.NO_SLOT));

		// Clear array:
		Arrays.fill(bakedButtons, null);

		// Insert buttons:
		int frontIndex = 0;
		this.buttonRows = Math.min(BUTTON_MAX_ROWS, ((buttons.size() - 1) / COLUMNS_PER_ROW) + 1);
		int endIndex = buttonRows * COLUMNS_PER_ROW - 1;
		for (int i = 0; i < buttons.size(); ++i) {
			Button button = buttons.get(i);
			int buttonIndex;
			if (button.isPlaceAtEnd()) {
				buttonIndex = endIndex;
				endIndex--;
			} else {
				buttonIndex = frontIndex;
				frontIndex++;
			}
			if (bakedButtons[buttonIndex] != null) {
				// There is not enough space for the remaining buttons.
				break;
			}
			bakedButtons[buttonIndex] = button;
			button.setSlot(BUTTONS_START + buttonIndex);
		}
	}

	private int getButtonRows() {
		this.bakeButtons();
		return buttonRows;
	}

	private @Nullable Button[] getBakedButtons() {
		this.bakeButtons();
		return bakedButtons;
	}

	private @Nullable Button getButton(int rawSlot) {
		if (!this.isButtonArea(rawSlot)) return null;
		return this._getButton(rawSlot);
	}

	private @Nullable Button _getButton(int rawSlot) {
		assert this.isButtonArea(rawSlot);
		this.bakeButtons();
		return bakedButtons[rawSlot - BUTTONS_START];
	}

	public void addButton(Button button) {
		Validate.notNull(button, "button is null");
		Validate.isTrue(button.isApplicable(this),
				"button is not applicable to this editor handler");
		button.setEditorHandler(this); // Validates that the button isn't used elsewhere yet
		buttons.add(button);
		dirtyButtons = true;
	}

	protected final void addButtons(Iterable<? extends Button> buttons) {
		Validate.notNull(buttons, "buttons is null");
		for (Button button : buttons) {
			this.addButton(button);
		}
	}

	protected void addButtonOrIgnore(@Nullable Button button) {
		if (button == null) return; // Ignore
		this.addButton(button);
	}

	protected final void addButtonsOrIgnore(Iterable<? extends @Nullable Button> buttons) {
		Validate.notNull(buttons, "buttons is null");
		for (Button button : buttons) {
			this.addButtonOrIgnore(button);
		}
	}

	protected void setupButtons() {
		// None by default.
	}

	// PLAYER SESSIONS

	/**
	 * Creates a new {@link EditorSession}.
	 * <p>
	 * The given list of recipes is not copied, but edited directly.
	 * 
	 * @param uiSession
	 *            the UI session of the editing player, not <code>null</code>
	 * @param recipes
	 *            the trading recipe drafts, not <code>null</code> and expected to be modifiable
	 * @param inventory
	 *            the editor inventory, not <code>null</code>
	 * @return the editor session, not <code>null</code>
	 */
	protected EditorSession createEditorSession(
			UISession uiSession,
			List<TradingRecipeDraft> recipes,
			Inventory inventory
	) {
		return new EditorSession(uiSession, recipes, inventory);
	}

	protected @Nullable EditorSession getEditorSession(Player player) {
		Validate.notNull(player, "player is null");
		return editorSessions.get(player.getUniqueId());
	}

	@Override
	public boolean openWindow(UISession uiSession, UIState uiState) {
		Validate.notNull(uiSession, "uiSession is null");
		this.validateState(uiState);

		Player player = uiSession.getPlayer();

		// Lazy setup:
		this.setup();

		// Set up session:
		List<TradingRecipeDraft> recipes = tradingRecipesAdapter.getTradingRecipes();

		// Create inventory:
		Inventory inventory = Bukkit.createInventory(
				player,
				this.getInventorySize(),
				this.getEditorTitle()
		);
		EditorSession editorSession = this.createEditorSession(uiSession, recipes, inventory);
		editorSessions.put(player.getUniqueId(), editorSession);

		// Determine the initial page:
		int page = 1;
		if (uiState instanceof EditorUIState) {
			EditorUIState editorState = (EditorUIState) uiState;
			page = this.getValidPage(editorState.getCurrentPage());
		}

		// Set up and open the initial page:
		editorSession.setPage(page);
		this.setupCurrentPage(editorSession);

		return player.openInventory(inventory) != null;
	}

	protected abstract String getEditorTitle();

	protected void setupCurrentPage(EditorSession editorSession) {
		assert editorSession != null;

		// Setup inventory:
		this.setupTradeColumns(editorSession);
		this.setupTradesPageBar(editorSession);
		this.setupButtons(editorSession);
	}

	protected void setupTradeColumns(EditorSession editorSession) {
		assert editorSession != null;
		// Insert trades (this replaces all previous items inside the trades area):
		Inventory inventory = editorSession.getInventory();
		int page = editorSession.getCurrentPage();
		assert page >= 1;
		List<TradingRecipeDraft> recipes = editorSession.getRecipes();
		int recipeStartIndex = (page - 1) * TRADES_COLUMNS;
		for (int column = 0; column < TRADES_COLUMNS; column++) {
			int recipeIndex = recipeStartIndex + column;
			if (recipeIndex < recipes.size()) {
				// Insert trading recipe:
				TradingRecipeDraft recipe = recipes.get(recipeIndex);
				this.setTradeColumn(inventory, column, recipe);
			} else {
				// Insert empty slot placeholders:
				this.setTradeColumn(inventory, column, TradingRecipeDraft.EMPTY);
			}
		}
	}

	protected void setupTradesPageBar(EditorSession editorSession) {
		assert editorSession != null;
		Inventory inventory = editorSession.getInventory();
		// Clear page bar area:
		for (int i = TRADES_PAGE_BAR_START; i <= TRADES_PAGE_BAR_END; ++i) {
			inventory.setItem(i, null);
		}

		// Insert buttons:
		@Nullable Button[] buttons = this.getTradesPageBarButtons();
		for (int i = 0; i < buttons.length; ++i) {
			Button button = buttons[i];
			if (button == null) continue;
			ItemStack icon = button.getIcon(editorSession);
			if (icon == null) continue;
			inventory.setItem(button.getSlot(), icon);
		}
	}

	// Note: This cannot deal with new button rows being required due to newly added buttons (which
	// would require creating and freshly open a new inventory, resulting in flicker).
	protected void updateButtons(EditorSession editorSession) {
		this.setupButtons(editorSession);
	}

	void updateButtonsInAllSessions() {
		editorSessions.values().forEach(editorSession -> {
			this.updateButtons(editorSession);
			editorSession.updateInventory();
		});
	}

	// Also used to refresh all button icons in an already open inventory.
	protected void setupButtons(EditorSession editorSession) {
		Inventory inventory = editorSession.getInventory();
		final int inventorySize = inventory.getSize();
		@Nullable Button[] buttons = this.getBakedButtons();
		for (int buttonIndex = 0; buttonIndex < buttons.length; ++buttonIndex) {
			int slot = BUTTONS_START + buttonIndex;
			if (slot >= inventorySize) {
				// This can be reached if called on a previously set up inventory.
				break;
			}

			ItemStack icon = null;
			Button button = buttons[buttonIndex];
			if (button != null) {
				icon = button.getIcon(editorSession);
			}
			// Null will clear the slot (which is required if this is called to refresh the buttons
			// in an already set up inventory):
			inventory.setItem(slot, icon);
		}
	}

	void updateButtonInAllSessions(Button button) {
		int slot = button.getSlot();
		editorSessions.values().forEach(editorSession -> {
			ItemStack icon = button.getIcon(editorSession);
			editorSession.getInventory().setItem(slot, icon);
			editorSession.updateInventory();
		});
	}

	protected final int getValidPage(int targetPage) {
		return Math.max(1, Math.min(this.getMaxTradesPages(), targetPage));
	}

	// Returns true if the page has changed.
	protected boolean switchPage(
			EditorSession editorSession,
			int targetPage,
			boolean saveCurrentPage
	) {
		int newPage = this.getValidPage(targetPage);
		int currentPage = editorSession.getCurrentPage();
		if (newPage == currentPage) return false; // Page has not changed

		// Save the current page:
		if (saveCurrentPage) {
			this.saveEditorPage(editorSession);
		}

		// Update page:
		editorSession.setPage(newPage);
		this.setupCurrentPage(editorSession);
		editorSession.updateInventory();
		return true;
	}

	@Override
	public boolean canAccess(Player player, boolean silent) {
		Validate.notNull(player, "player is null");
		// Permission for the type of shopkeeper is checked in the AdminShopkeeper specific
		// EditorHandler.
		// Owner is checked in the PlayerShopkeeper specific EditorHandler.
		return true;
	}

	@Override
	protected boolean isWindow(InventoryView view) {
		Validate.notNull(view, "view is null");
		return view.getTitle().equals(this.getEditorTitle());
	}

	@Override
	protected UIState captureState(UISession uiSession) {
		EditorSession editorSession = Unsafe.assertNonNull(
				this.getEditorSession(uiSession.getPlayer())
		);
		return new EditorUIState(editorSession.getCurrentPage());
	}

	@Override
	protected boolean isAcceptedState(UIState uiState) {
		if (super.isAcceptedState(uiState)) return true;
		if (uiState instanceof EditorUIState) return true;
		return false;
	}

	@Override
	protected void restoreState(UISession uiSession, UIState uiState) {
		this.validateState(uiState);
		EditorUIState editorState = (EditorUIState) uiState;
		EditorSession editorSession = Unsafe.assertNonNull(
				this.getEditorSession(uiSession.getPlayer())
		);
		this.switchPage(editorSession, editorState.getCurrentPage(), true);
	}

	@Override
	protected void onInventoryDragEarly(UISession uiSession, InventoryDragEvent event) {
		assert uiSession != null && event != null;
		// Dragging is allowed by default only inside the player inventory and the trades area:
		if (event.isCancelled()) return; // Already cancelled

		InventoryView view = event.getView();
		Set<Integer> slots = Unsafe.castNonNull(event.getRawSlots());
		for (Integer rawSlotInteger : slots) {
			int rawSlot = rawSlotInteger;
			if (this.isTradesArea(rawSlot)) continue;
			if (InventoryViewUtils.isPlayerInventory(view, rawSlot)) continue;

			event.setCancelled(true);
			break;
		}
	}

	@Override
	protected void onInventoryClickEarly(UISession uiSession, InventoryClickEvent event) {
		assert uiSession != null && event != null;
		if (this.isAutomaticShiftLeftClick()) {
			// Ignore automatically triggered shift left-clicks:
			return;
		}

		EditorSession editorSession = Unsafe.assertNonNull(
				this.getEditorSession(uiSession.getPlayer())
		);

		int rawSlot = event.getRawSlot();
		if (this.isTradesArea(rawSlot)) {
			// Trades area:
			this.handleTradesClick(editorSession, event);
		} else if (this.isTradesPageBar(rawSlot)) {
			// Trades page bar:
			this.handleTradesPageBarClick(editorSession, event);
		} else if (this.isButtonArea(rawSlot)) {
			// Editor buttons:
			this.handleButtonClick(editorSession, event);
		} else if (InventoryViewUtils.isPlayerInventory(event.getView(), rawSlot)) {
			// Player inventory:
			this.handlePlayerInventoryClick(editorSession, event);
		}
	}

	protected void handleTradesClick(EditorSession editorSession, InventoryClickEvent event) {
		assert this.isTradesArea(event.getRawSlot());
	}

	protected void handleTradesPageBarClick(
			EditorSession editorSession,
			InventoryClickEvent event
	) {
		assert this.isTradesPageBar(event.getRawSlot());
		event.setCancelled(true);
		int rawSlot = event.getRawSlot();
		Button button = this._getTradesPageBarButton(rawSlot);
		if (button != null) {
			button.onClick(editorSession, event);
		}
	}

	protected void handleButtonClick(EditorSession editorSession, InventoryClickEvent event) {
		assert this.isButtonArea(event.getRawSlot());
		event.setCancelled(true);
		int rawSlot = event.getRawSlot();
		Button button = this._getButton(rawSlot);
		if (button != null) {
			button.onClick(editorSession, event);
		}
	}

	protected void handlePlayerInventoryClick(
			EditorSession editorSession,
			InventoryClickEvent event
	) {
		assert InventoryViewUtils.isPlayerInventory(event.getView(), event.getRawSlot());
	}

	protected int getNewAmountAfterEditorClick(
			InventoryClickEvent event,
			int currentAmount,
			int minAmount,
			int maxAmount
	) {
		// Validate bounds:
		if (minAmount > maxAmount) return currentAmount; // No valid value possible
		if (minAmount == maxAmount) return minAmount; // Only one valid value possible

		int newAmount = currentAmount;
		ClickType clickType = event.getClick();
		switch (clickType) {
		case LEFT:
			newAmount += 1;
			break;
		case SHIFT_LEFT:
			newAmount += 10;
			break;
		case RIGHT:
			newAmount -= 1;
			break;
		case SHIFT_RIGHT:
			newAmount -= 10;
			break;
		case MIDDLE:
			newAmount = minAmount;
			break;
		case NUMBER_KEY:
			assert event.getHotbarButton() >= 0;
			newAmount = event.getHotbarButton() + 1;
			break;
		default:
			break;
		}
		// Bounds:
		if (newAmount < minAmount) newAmount = minAmount;
		if (newAmount > maxAmount) newAmount = maxAmount;
		return newAmount;
	}

	@Override
	protected void onInventoryClose(UISession uiSession, @Nullable InventoryCloseEvent closeEvent) {
		// Cleanup session:
		Player player = uiSession.getPlayer();
		EditorSession editorSession = editorSessions.remove(player.getUniqueId());
		editorSession = Validate.State.notNull(editorSession, "editorSession is null");

		if (closeEvent != null) {
			// Only save if caused by an inventory close event (i.e. if the session has not been
			// 'aborted'):
			this.saveEditor(editorSession);
		}
	}

	/**
	 * Saves the current page of the editor interface to the given {@link EditorSession}.
	 * 
	 * @param editorSession
	 *            the editor session, not <code>null</code>
	 */
	protected void saveEditorPage(EditorSession editorSession) {
		assert editorSession != null;
		Inventory inventory = editorSession.getInventory();
		int page = editorSession.getCurrentPage();
		assert page >= 1;
		List<TradingRecipeDraft> recipes = editorSession.getRecipes();

		int recipesPerPage = COLUMNS_PER_ROW;
		int startIndex = (page - 1) * recipesPerPage;
		int endIndex = startIndex + TRADES_COLUMNS - 1;
		// Add empty recipes to support the recipes of the current page:
		for (int i = recipes.size(); i <= endIndex; ++i) {
			recipes.add(TradingRecipeDraft.EMPTY);
		}

		// Replace recipes:
		for (int column = 0; column < TRADES_COLUMNS; column++) {
			TradingRecipeDraft recipeDraft = this.getTradingRecipe(inventory, column);
			int recipeIndex = startIndex + column;
			recipes.set(recipeIndex, recipeDraft);
		}
	}

	/**
	 * Saves the current state of the editor interface.
	 * 
	 * @param editorSession
	 *            the editor session, not <code>null</code>
	 */
	protected void saveEditor(EditorSession editorSession) {
		assert editorSession != null;
		// Save the current editor page from the UI to the session:
		this.saveEditorPage(editorSession);

		// Save the recipes from the session:
		this.saveRecipes(editorSession);
	}

	/**
	 * Saves (i.e. applies) the trading recipes of the given {@link EditorSession}.
	 * 
	 * @param editorSession
	 *            the editor session, not <code>null</code>
	 */
	protected abstract void saveRecipes(EditorSession editorSession);
}
