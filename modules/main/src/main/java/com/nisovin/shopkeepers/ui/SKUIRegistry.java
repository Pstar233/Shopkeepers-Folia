package com.nisovin.shopkeepers.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.ShopkeepersPlugin;
import com.nisovin.shopkeepers.api.events.PlayerOpenUIEvent;
import com.nisovin.shopkeepers.api.events.ShopkeeperOpenUIEvent;
import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import com.nisovin.shopkeepers.api.ui.UIRegistry;
import com.nisovin.shopkeepers.api.ui.UISession;
import com.nisovin.shopkeepers.api.ui.UIType;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.types.AbstractTypeRegistry;
import com.nisovin.shopkeepers.ui.state.UIState;
import com.nisovin.shopkeepers.util.bukkit.SchedulerUtils;
import com.nisovin.shopkeepers.util.java.Validate;
import com.nisovin.shopkeepers.util.logging.Log;

public class SKUIRegistry extends AbstractTypeRegistry<AbstractUIType>
		implements UIRegistry<AbstractUIType> {

	private final ShopkeepersPlugin plugin;
	private final UIListener uiListener;

	// Player id -> UI session
	private final Map<UUID, SKUISession> uiSessions = new HashMap<>();
	private final Collection<? extends SKUISession> uiSessionsView
			= Collections.unmodifiableCollection(uiSessions.values());

	public SKUIRegistry(ShopkeepersPlugin plugin) {
		Validate.notNull(plugin, "plugin is null");
		this.plugin = plugin;
		this.uiListener = new UIListener(plugin, Unsafe.initialized(this));
	}

	public void onEnable() {
		uiListener.onEnable();
	}

	public void onDisable() {
		// Close all open UIs:
		this.abortUISessions();
		uiListener.onDisable();
	}

	@Override
	protected String getTypeName() {
		return "UI type";
	}

	public boolean requestUI(UIType uiType, AbstractShopkeeper shopkeeper, Player player) {
		return this.requestUI(uiType, shopkeeper, player, UIState.EMPTY);
	}

	public boolean requestUI(
			UIType uiType,
			AbstractShopkeeper shopkeeper,
			Player player,
			UIState uiState
	) {
		Validate.notNull(uiType, "uiHandler is null");
		Validate.notNull(shopkeeper, "shopkeeper is null");
		Validate.notNull(player, "player is null");

		String uiIdentifier = uiType.getIdentifier();
		UIHandler uiHandler = shopkeeper.getUIHandler(uiType);
		if (uiHandler == null) {
			Log.debug(() -> shopkeeper.getLogPrefix() + "Cannot open UI '" + uiIdentifier
					+ "' for player " + player.getName()
					+ ": This shopkeeper does not support this type of UI.");
			return false;
		}

		// Ignore the given UI state if it is incompatible:
		UIState effectiveUIState = uiState;
		if (!uiHandler.isAcceptedState(uiState)) {
			Log.debug(() -> shopkeeper.getLogPrefix()
					+ "Ignoring incompatible captured UI state of type "
					+ uiState.getClass().getName() + "' for player " + player.getName() + ".");
			effectiveUIState = UIState.EMPTY;
		}

		return this.requestUI(uiHandler, player, effectiveUIState);
	}

	public boolean requestUI(UIHandler uiHandler, Player player) {
		return this.requestUI(uiHandler, player, false, UIState.EMPTY);
	}

	public boolean requestUI(UIHandler uiHandler, Player player, UIState uiState) {
		return this.requestUI(uiHandler, player, false, uiState);
	}

	public boolean requestUI(UIHandler uiHandler, Player player, boolean silentRequest) {
		return this.requestUI(uiHandler, player, silentRequest, UIState.EMPTY);
	}

	public boolean requestUI(
			UIHandler uiHandler,
			Player player,
			boolean silentRequest,
			UIState uiState
	) {
		Validate.notNull(uiHandler, "uiHandler is null");
		Validate.notNull(player, "player is null");

		UIType uiType = uiHandler.getUIType();
		String uiIdentifier = uiType.getIdentifier();
		String playerName = player.getName();

		AbstractShopkeeper shopkeeper = null; // Can be null
		if (uiHandler instanceof ShopkeeperUIHandler) {
			shopkeeper = ((ShopkeeperUIHandler) uiHandler).getShopkeeper();
		}

		if (!player.isValid()) {
			Log.debug(() -> "Player " + playerName + " cannot open UI '" + uiIdentifier
					+ "': Player not connected.");
			return false;
		}

		if (shopkeeper != null && !shopkeeper.isValid()) {
			Log.debug(() -> "Player " + playerName + " cannot open UI '" + uiIdentifier
					+ "': Shopkeeper not valid.");
			return false;
		}

		if (!uiHandler.canOpen(player, silentRequest)) {
			Log.debug(() -> "Player " + playerName + " cannot open UI '" + uiIdentifier + "'.");
			return false;
		}

		SKUISession oldSession = this.getUISession(player);
		// Filter out duplicate open requests:
		if (oldSession != null && oldSession.getUIHandler().equals(uiHandler)) {
			Log.debug(() -> "UI '" + uiIdentifier + "'" + " is already open for player "
					+ playerName + ".");
			return false;
		}

		// Call event:
		PlayerOpenUIEvent openUIEvent;
		if (shopkeeper == null) {
			openUIEvent = new PlayerOpenUIEvent(uiType, player, silentRequest);
		} else {
			openUIEvent = new ShopkeeperOpenUIEvent(shopkeeper, uiType, player, silentRequest);
		}
		Bukkit.getServer().getPluginManager().callEvent(openUIEvent);
		if (openUIEvent.isCancelled()) {
			Log.debug(() -> "A plugin cancelled the opening of UI '" + uiIdentifier
					+ "' for player " + playerName + ".");
			return false;
		}

		// Close any previous inventory view before we start a new UI session:
		// Opening a new inventory view should already automatically close any previous inventory.
		// However, we need to do this before we start the new UI session, otherwise the closing of
		// the previous inventory view is incorrectly interpreted as the new UI being closed, which
		// immediately ends the new UI session again (possibly even with the UI remaining open
		// without an active UI session).
		// Also, we cannot be sure what the UI handler is actually doing inside openWindow().
		// This will call a PlayerCloseInventoryEvent, which also ends any previous UI session.
		player.closeInventory();
		assert this.getUISession(player) == null;

		// Register event handlers for any not yet handled types of inventory events:
		Set<? extends Class<? extends InventoryEvent>> additionalInventoryEvents
				= uiHandler.getAdditionalInventoryEvents();
		assert additionalInventoryEvents != null;
		additionalInventoryEvents.forEach(uiListener::registerEventType);

		// Start a new UI session:
		SKUISession session = new SKUISession(uiHandler, player, shopkeeper);
		uiSessions.put(player.getUniqueId(), session);
		this.onSessionStarted(session);

		// Open the new UI:
		Log.debug(() -> "Opening UI '" + uiIdentifier + "' ...");
		boolean isOpen = uiHandler.openWindow(session, uiState);
		if (!isOpen) {
			Log.debug(() -> "Failed to open UI '" + uiIdentifier + "'!");
			this.endUISession(player, null);
			return false;
		}
		return true;
	}

	@Override
	public Collection<? extends SKUISession> getUISessions() {
		return uiSessionsView;
	}

	@Override
	public Collection<? extends SKUISession> getUISessions(Shopkeeper shopkeeper) {
		Validate.notNull(shopkeeper, "shopkeeper is null");
		List<SKUISession> sessions = new ArrayList<>();
		uiSessionsView.forEach(uiSession -> {
			if (uiSession.getShopkeeper() == shopkeeper) {
				sessions.add(uiSession);
			}
		});
		return sessions;
	}

	@Override
	public Collection<? extends SKUISession> getUISessions(
			Shopkeeper shopkeeper,
			UIType uiType
	) {
		Validate.notNull(shopkeeper, "shopkeeper is null");
		Validate.notNull(uiType, "uiType is null");
		List<SKUISession> sessions = new ArrayList<>();
		uiSessionsView.forEach(uiSession -> {
			if (uiSession.getShopkeeper() == shopkeeper && uiSession.getUIType() == uiType) {
				sessions.add(uiSession);
			}
		});
		return sessions;
	}

	@Override
	public Collection<? extends UISession> getUISessions(UIType uiType) {
		Validate.notNull(uiType, "uiType is null");
		List<SKUISession> sessions = new ArrayList<>();
		uiSessionsView.forEach(uiSession -> {
			if (uiSession.getUIType() == uiType) {
				sessions.add(uiSession);
			}
		});
		return sessions;
	}

	@Override
	public @Nullable SKUISession getUISession(Player player) {
		Validate.notNull(player, "player is null");
		return uiSessions.get(player.getUniqueId());
	}

	void onInventoryClose(InventoryCloseEvent closeEvent) {
		assert closeEvent != null;
		if (!(closeEvent.getPlayer() instanceof Player)) {
			return;
		}
		Player player = (Player) closeEvent.getPlayer();

		UISession session = this.getUISession(player);
		if (session == null) return;

		Log.debug(() -> "Player " + player.getName() + " closed UI '"
				+ session.getUIType().getIdentifier() + "'.");
		this.endUISession(player, closeEvent);
	}

	public void onPlayerQuit(Player player) {
		// TODO This might have no effect, because CraftBukkit triggers an inventory close event
		// prior to the player quitting.
		this.endUISession(player, null);
	}

	// closeEvent can be null.
	void endUISession(Player player, @Nullable InventoryCloseEvent closeEvent) {
		assert player != null;
		SKUISession session = uiSessions.remove(player.getUniqueId());
		if (session == null) return;

		this.onSessionEnded(session, closeEvent);
	}

	private void onSessionStarted(SKUISession session) {
		Log.debug(() -> "UI session '" + session.getUIType().getIdentifier()
				+ "' started for player " + session.getPlayer().getName() + ".");
	}

	// closeEvent can be null.
	private void onSessionEnded(SKUISession session, @Nullable InventoryCloseEvent closeEvent) {
		Log.debug(() -> "UI session '" + session.getUIType().getIdentifier()
				+ "' ended for player " + session.getPlayer().getName() + ".");
		session.onSessionEnd(); // Inform session
		session.getUIHandler().onInventoryClose(session, closeEvent); // Inform UI handler
	}

	// Called by SKUISession.
	void abort(SKUISession uiSession) {
		assert uiSession != null;
		if (!uiSession.isValid()) return;

		Player player = uiSession.getPlayer();
		this.endUISession(player, null);
		player.closeInventory();
	}

	@Override
	public void abortUISessions() {
		// Copy to prevent concurrent modifications:
		new ArrayList<>(this.getUISessions()).forEach(SKUISession::abort);
		assert uiSessions.isEmpty();
	}

	@Override
	public void abortUISessions(Shopkeeper shopkeeper) {
		// Note: The returned collection is already a copy.
		this.getUISessions(shopkeeper).forEach(uiSession -> {
			uiSession.abort();
		});
	}

	@Override
	public void abortUISessionsDelayed(Shopkeeper shopkeeper) {
		Validate.notNull(shopkeeper, "shopkeeper is null");

		// Deactivate currently active UIs for this shopkeeper:
		this.deactivateUIs(shopkeeper);

		if (shopkeeper.getLocation() != null) {
			SchedulerUtils.runRegionScheduler(plugin, () -> {
				this.abortUISessions(shopkeeper);
			}, shopkeeper.getLocation());
		}else {
			SchedulerUtils.runAsyncTaskOrOmit(plugin, ()->{
				this.abortUISessions(shopkeeper);
			});
		}
	}

	private void deactivateUIs(Shopkeeper shopkeeper) {
		assert shopkeeper != null;
		uiSessionsView.forEach(uiSession -> {
			if (uiSession.getShopkeeper() == shopkeeper) {
				uiSession.deactivateUI();
			}
		});
	}
}
