package com.nisovin.shopkeepers.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.SKShopkeepersPlugin;
import com.nisovin.shopkeepers.util.bukkit.LocationUtils;
import com.nisovin.shopkeepers.util.logging.Log;

/**
 * Tries to bypass other plugins that block the spawning of mobs (e.g. region protection plugins).
 */
public class ForcingCreatureSpawner implements Listener {

	private final SKShopkeepersPlugin plugin;

	private @Nullable Location nextSpawnLocation = null;
	private @Nullable EntityType nextEntityType = null;

	public ForcingCreatureSpawner(SKShopkeepersPlugin plugin) {
		this.plugin = plugin;
	}

	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, plugin);
	}

	public void onDisable() {
		HandlerList.unregisterAll(this);

		// Reset any pending forced spawn:
		this.resetForcedCreatureSpawn();
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	void onCreatureSpawn(CreatureSpawnEvent event) {
		if (nextSpawnLocation == null) return;
		if (this.matchesForcedCreatureSpawn(event)) {
			event.setCancelled(false);
		} else {
			// Unexpected.
			Log.debug(() -> "Forced entity spawning seems to be out of sync: "
					+ "Forced spawning was activated for an entity of type " + nextEntityType
					+ " at location " + nextSpawnLocation + ", but a different entity of type "
					+ event.getEntityType() + " was spawned at location " + event.getLocation()
					+ ".");
		}

		this.resetForcedCreatureSpawn();
	}

	private boolean matchesForcedCreatureSpawn(CreatureSpawnEvent event) {
		return event.getEntityType() == nextEntityType
				&& LocationUtils.getSafeDistanceSquared(event.getLocation(), nextSpawnLocation) < 0.6D;
	}

	/**
	 * Tries to force the subsequent spawn attempt for the specified entity type at the specified
	 * location.
	 * 
	 * @param location
	 *            the spawn location
	 * @param entityType
	 *            the entity type
	 */
	public void forceCreatureSpawn(Location location, EntityType entityType) {
		this.nextSpawnLocation = location;
		this.nextEntityType = entityType;
	}

	/**
	 * Resets any pending forced creature spawn.
	 */
	public void resetForcedCreatureSpawn() {
		nextSpawnLocation = null;
		nextEntityType = null;
	}
}
