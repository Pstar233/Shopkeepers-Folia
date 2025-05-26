package com.nisovin.shopkeepers.api.ui;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;

/**
 * Represents the UI session of a player for as long as the player has the UI open.
 */
public interface UISession {

	/**
	 * Gets the {@link UIType}.
	 * 
	 * @return the UI type, not <code>null</code>
	 */
	public UIType getUIType();

	/**
	 * Gets the player involved in this UI session.
	 * 
	 * @return the player, not <code>null</code>
	 */
	public Player getPlayer();

	/**
	 * Gets the {@link Shopkeeper} associated with this UI session.
	 * 
	 * @return the shopkeeper, or <code>null</code> if there is no associated shopkeeper
	 */
	public @Nullable Shopkeeper getShopkeeper();

	/**
	 * Checks if the UI is active currently.
	 * <p>
	 * When the UI has been {@link #deactivateUI() deactivated} any inventory interactions by the
	 * player are ignored (they get cancelled, but not processed any further beyond that).
	 * <p>
	 * A typical use for this is to deactivate the UI when the session is about to end / when the
	 * inventory view is about to get closed. This is usually required because it is not safe to
	 * immediately close the inventory view as reaction to inventory interactions by the player.
	 * Instead, the closing of the inventory view is postponed to happen the next tick. The UI can
	 * be deactivated in this case to prevent the processing of any further inventory interactions
	 * that might otherwise happen in the meantime.
	 * 
	 * @return <code>true</code> if the UI is active
	 */
	public boolean isUIActive();

	/**
	 * Deactivates the UI.
	 * 
	 * @see #isUIActive()
	 */
	public void deactivateUI();

	/**
	 * Activates the UI.
	 * 
	 * @see #isUIActive()
	 */
	public void activateUI();

	/**
	 * Checks if this session is still valid.
	 * <p>
	 * The session becomes invalid once the player has closed the inventory view.
	 * 
	 * @return <code>true</code> if the session is still valid
	 */
	public boolean isValid();

	/**
	 * Closes this UI session.
	 * <p>
	 * The closing is handled similarly as if the player closed the UI manually (or a plugin or the
	 * server called {@link Player#closeInventory()}).
	 * <p>
	 * This may not be safe to call during UI / inventory events.
	 */
	public void close();

	/**
	 * {@link #deactivateUI() Deactivates} the UI and {@link #close() closes} this UI session after
	 * a short delay (within the next tick).
	 */
	public void closeDelayed();

	/**
	 * {@link #deactivateUI() Deactivates} the UI, {@link #close() closes} this UI session after a
	 * short delay (within the next tick) and then runs the given task.
	 *
	 * @param task    the task, or <code>null</code>
	 * @param location 位置
	 */
	public void closeDelayedAndRunTask(@Nullable Runnable task, Location location);

	/**
	 * Aborts this UI session.
	 * <p>
	 * Unlike {@link #close()} this has the semantic of the UI being forcefully closed without the
	 * player's intent. Some types of UI might handle this differently, e.g. by not saving any not
	 * yet applied changes.
	 * <p>
	 * This may not be safe to call during UI / inventory events.
	 */
	public void abort();

	/**
	 * {@link #deactivateUI() Deactivates} the UI and {@link #abort() aborts} this UI session after
	 * a short delay (within the next tick).
	 */
	public void abortDelayed();

	/**
	 * {@link #deactivateUI() Deactivates} the UI, {@link #abort() aborts} this UI session after a
	 * short delay (within the next tick) and then runs the given task.
	 * 
	 * @param task
	 *            the task, or <code>null</code>
	 */
	public void abortDelayedAndRunTask(@Nullable Runnable task);
}
