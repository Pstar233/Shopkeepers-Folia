package com.nisovin.shopkeepers.user;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.api.user.User;
import com.nisovin.shopkeepers.util.java.LRUCache;
import com.nisovin.shopkeepers.util.java.Validate;

public final class SKUser implements User {

	private static final Map<UUID, User> cache = new LRUCache<>(100);

	/**
	 * Gets a {@link User} with the specified unique id and last known name.
	 * <p>
	 * This method may cache and reuse the returned {@link User} object for future calls of this
	 * method.
	 * 
	 * @param uniqueId
	 *            the unique id, not <code>null</code>
	 * @param lastKnownName
	 *            the last known name, not <code>null</code> or empty
	 * @return the user, not <code>null</code>
	 */
	public static User of(UUID uniqueId, String lastKnownName) {
		User user = cache.compute(uniqueId, (uuid, oldUser) -> {
			if (oldUser != null && oldUser.getLastKnownName().equals(lastKnownName)) {
				assert oldUser.getUniqueId().equals(uniqueId);
				return oldUser;
			} else {
				return new SKUser(uniqueId, lastKnownName);
			}
		});
		assert user != null;
		return user;
	}

	/**
	 * Gets a {@link User} for the given {@link Player}.
	 * <p>
	 * This method may cache and reuse the returned {@link User} object for future calls of this
	 * method.
	 * 
	 * @param player
	 *            the player
	 * @return the user, not <code>null</code>
	 */
	public static User of(Player player) {
		return of(player.getUniqueId(), player.getName());
	}

	/**
	 * A {@link User} with nil UUID and undefined name.
	 * <p>
	 * The name of this user is not guaranteed to remain constant across plugin versions, so do not
	 * depend on it always matching its current value.
	 * <p>
	 * This can for example be used as an alternative non-<code>null</code> representation of an
	 * unset, unknown, or missing User.
	 */
	public static final User EMPTY = of(new UUID(0L, 0L), "[unset]");

	/////

	private final UUID uniqueId; // Not null
	private final String lastKnownName; // Not null or empty

	private SKUser(UUID uniqueId, String lastKnownName) {
		Validate.notNull(uniqueId, "uniqueId is null");
		Validate.notEmpty(lastKnownName, "lastKnownName is null or empty");
		this.uniqueId = uniqueId;
		this.lastKnownName = lastKnownName;
	}

	@Override
	public UUID getUniqueId() {
		return uniqueId;
	}

	@Override
	public String getLastKnownName() {
		return lastKnownName;
	}

	@Override
	public String getName() {
		Player player = this.getPlayer();
		if (player != null) {
			return Unsafe.assertNonNull(player.getName());
		}
		return lastKnownName;
	}

	@Override
	public String getDisplayName() {
		Player player = this.getPlayer();
		if (player != null) return player.getDisplayName();
		return lastKnownName;
	}

	@Override
	public boolean isOnline() {
		return (this.getPlayer() != null);
	}

	@Override
	public @Nullable Player getPlayer() {
		return Bukkit.getPlayer(uniqueId);
	}

	@Override
	public OfflinePlayer getOfflinePlayer() {
		return Bukkit.getOfflinePlayer(uniqueId); // Non-blocking
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("SKUser [uniqueId=");
		builder.append(uniqueId);
		builder.append(", name=");
		builder.append(lastKnownName);
		builder.append("]");
		return builder.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + uniqueId.hashCode();
		result = prime * result + lastKnownName.hashCode();
		return result;
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof User)) return false;
		User other = (User) obj;
		if (!uniqueId.equals(other.getUniqueId())) return false;
		if (!lastKnownName.equals(other.getLastKnownName())) return false;
		return true;
	}
}
