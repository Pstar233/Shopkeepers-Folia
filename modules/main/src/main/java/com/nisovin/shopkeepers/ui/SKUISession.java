package com.nisovin.shopkeepers.ui;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.Nullable;
import com.nisovin.shopkeepers.SKShopkeepersPlugin;
import com.nisovin.shopkeepers.api.ShopkeepersPlugin;
import com.nisovin.shopkeepers.api.ui.UISession;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.ui.state.UIState;
import com.nisovin.shopkeepers.util.bukkit.SchedulerUtils;
import com.nisovin.shopkeepers.util.java.Validate;

public final class SKUISession implements UISession {

	private final UIHandler uiHandler;
	private final Player player;
	// Can be null for UIs that are not associated with some shopkeeper:
	private final @Nullable AbstractShopkeeper shopkeeper;
	private boolean uiActive = true;
	private boolean valid = true;

	public SKUISession(
			UIHandler uiHandler,
			Player player,
			@Nullable AbstractShopkeeper shopkeeper
	) {
		Validate.notNull(uiHandler, "uiHandler is null");
		Validate.notNull(player, "player is null");
		this.uiHandler = uiHandler;
		this.player = player;
		this.shopkeeper = shopkeeper; // Can be null
	}

	@Override
	public final AbstractUIType getUIType() {
		return uiHandler.getUIType();
	}

	public final UIHandler getUIHandler() {
		return uiHandler;
	}

	@Override
	public final Player getPlayer() {
		return player;
	}

	@Override
	public final @Nullable AbstractShopkeeper getShopkeeper() {
		return shopkeeper;
	}

	@Override
	public boolean isUIActive() {
		return uiActive;
	}

	@Override
	public void deactivateUI() {
		uiActive = false;
	}

	@Override
	public void activateUI() {
		uiActive = true;
	}

	final void onSessionEnd() {
		valid = false;
	}

	@Override
	public final boolean isValid() {
		return valid;
	}

	@Override
	public void close() {
		if (!this.isValid()) return;
		// This triggers an InventoryCloseEvent which ends the UI session:
		player.closeInventory();
	}

	@Override
	public void closeDelayed() {
//		this.closeDelayedAndRunTask(null, );
	}

	@Override
	public void closeDelayedAndRunTask(@Nullable Runnable task, Location location) {
		if (!this.isValid()) return;

		this.deactivateUI();
		// This fails during plugin disable. However, all UIs will be closed anyway.
		SchedulerUtils.runRegionScheduler(ShopkeepersPlugin.getInstance(), () -> {
			if (!this.isValid()) return;
			close();
			if (task != null) {
				task.run();
			}
		}, location);
	}

	@Override
	public void abort() {
		SKShopkeepersPlugin.getInstance().getUIRegistry().abort(this);
	}

	@Override
	public void abortDelayed() {
		this.abortDelayedAndRunTask(null);
	}

	@Override
	public void abortDelayedAndRunTask(@Nullable Runnable task) {
		if (!this.isValid()) return;

		this.deactivateUI();
		// This fails during plugin disable. However, all UIs will be closed anyway.
		SchedulerUtils.runRegionScheduler(ShopkeepersPlugin.getInstance(), () -> {
			if (!this.isValid()) return;
			abort();
			if (task != null) {
				task.run();
			}
		}, player.getLocation());
	}

	/**
	 * Captures the current {@link UIState}.
	 * <p>
	 * Not all types of UIs may support this, or may be able to fully restore all aspects of the
	 * current UI session.
	 * 
	 * @return the {@link UIState}, not <code>null</code>, but may be {@link UIState#EMPTY}
	 */
	public final UIState captureState() {
		return uiHandler.captureState(this);
	}

	/**
	 * Tries to restore the given {@link UIState} in a best-effort manner.
	 * <p>
	 * Any current state is silently replaced with the captured state.
	 * 
	 * @param uiState
	 *            the {@link UIState}, not <code>null</code>
	 * @throws IllegalArgumentException
	 *             if the given {@link UIState} is incompatible with the UI type of this session
	 */
	public final void restoreState(UIState uiState) {
		uiHandler.restoreState(this, uiState);
	}
}
