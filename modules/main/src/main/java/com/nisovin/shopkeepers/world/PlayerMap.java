package com.nisovin.shopkeepers.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.util.java.Validate;
import com.nisovin.shopkeepers.util.logging.Log;

/**
 * Tracks the location of players to optimize operations such as finding nearby players.
 * <p>
 * We currently only track players by the world they are in, since we expect that a more granular
 * tracking, e.g. by chunk, does not provide much of an additional benefit on typical servers: For
 * example, when Minecraft's entity AI needs to find the nearby players, they also iterate over the
 * list of players inside the entity's world.<br/>
 * Unlike Bukkit's {@link World#getPlayers()}, {@link #getPlayers(String)} does not instantiate a
 * new list on every invocation, which is more suitable for tasks that run very frequently, such as
 * every tick.
 */
public final class PlayerMap {

	final class PlayerListener implements Listener {

		PlayerListener() {
		}

		// Note: We don't add a new empty world entry when a world is loaded, but only once a player
		// enters it. Once the world entry has been created, we keep it around for as long as the
		// world is still loaded.

		@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
		void onWorldUnload(WorldUnloadEvent event) {
			World world = event.getWorld();
			removeWorldData(world.getName());
		}

		@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
		void onPlayerJoin(PlayerJoinEvent event) {
			// Check if the player is still online (some other plugin might have kicked the player
			// during the event):
			Player player = event.getPlayer();
			if (!player.isOnline()) return; // Player is no longer online

			addPlayer(event.getPlayer());
		}

		@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
		void onPlayerTeleport(PlayerTeleportEvent event) {
			// The target location can be null in some circumstances (e.g. when a player enters an
			// end gateway, but there is no end world). We ignore the event in this case.
			Location toLocation = event.getTo();
			if (toLocation == null) return;

			World fromWorld = Unsafe.assertNonNull(event.getFrom().getWorld());
			World toWorld = Unsafe.assertNonNull(toLocation.getWorld());
			if (!fromWorld.equals(toWorld)) return;

			Player player = event.getPlayer();
			updatePlayer(player, fromWorld, toWorld);
		}

		@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
		void onPlayerQuit(PlayerQuitEvent event) {
			removePlayer(event.getPlayer());
		}
	}

	private static final class WorldData {

		private final List<Player> players = new ArrayList<>();
		private final List<Player> playersView = Collections.unmodifiableList(players);

		private WorldData(String worldName) {
			Validate.notNull(worldName, "worldName is null");
		}
	}

	private final Plugin plugin;
	private final Map<String, WorldData> worlds = new HashMap<>();
	private final PlayerListener listener = new PlayerListener();

	public PlayerMap(Plugin plugin) {
		Validate.notNull(plugin, "plugin is null");
		this.plugin = plugin;
	}

	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(listener, plugin);

		for (Player player : Bukkit.getOnlinePlayers()) {
			assert player != null;
			// This creates the necessary world entries:
			this.addPlayer(player);
		}
	}

	public void onDisable() {
		HandlerList.unregisterAll(listener);
		worlds.clear();
	}

	// DATA

	// Returns null if there is no data for the specified world.
	private @Nullable WorldData getWorldData(String worldName) {
		assert worldName != null;
		return worlds.get(worldName);
	}

	private WorldData getOrCreateWorldData(String worldName) {
		assert worldName != null;
		WorldData worldData = worlds.computeIfAbsent(worldName, WorldData::new);
		assert worldData != null;
		return worldData;
	}

	private @Nullable WorldData removeWorldData(String worldName) {
		assert worldName != null;
		return worlds.remove(worldName);
	}

	private void addPlayer(Player player) {
		this.addPlayer(player.getWorld(), player);
	}

	private void addPlayer(World world, Player player) {
		String worldName = world.getName();
		WorldData worldData = this.getOrCreateWorldData(worldName);
		worldData.players.add(player);
	}

	private void removePlayer(Player player) {
		this.removePlayer(player.getWorld(), player);
	}

	private void removePlayer(World world, Player player) {
		String worldName = world.getName();
		@Nullable WorldData worldData = this.getWorldData(worldName);
		if (worldData == null) {
			Log.debug("PlayerMap: Failed to remove player '" + player.getName()
					+ "': Data for world '" + worldName + "' not found.");
			return;
		}

		if (!worldData.players.remove(player)) {
			Log.debug("PlayerMap: Failed to remove player '" + player.getName()
					+ "': Player not found in data for world '" + worldName + "'.");
		}
	}

	private void updatePlayer(Player player, World oldWorld, World newWorld) {
		this.removePlayer(oldWorld, player);
		this.addPlayer(newWorld, player);
	}

	// QUERIES

	/**
	 * Gets an unmodifiable view on the players inside the specified world.
	 * <p>
	 * Unlike {@link World#getPlayers()}, this does not instantiate a new list and is therefore also
	 * suited to be called very frequently.
	 * 
	 * @param worldName
	 *            the world name
	 * @return an unmodifiable view on the players inside the world, not <code>null</code>
	 */
	public List<? extends Player> getPlayers(String worldName) {
		@Nullable WorldData worldData = this.getWorldData(worldName);
		if (worldData == null) return Collections.emptyList();

		return worldData.playersView;
	}
}
