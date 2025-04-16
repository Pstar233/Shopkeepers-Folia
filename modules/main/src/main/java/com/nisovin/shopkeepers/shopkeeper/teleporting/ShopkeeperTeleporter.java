package com.nisovin.shopkeepers.shopkeeper.teleporting;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import com.nisovin.shopkeepers.lang.Messages;
import com.nisovin.shopkeepers.util.bukkit.TeleportHelper;
import com.nisovin.shopkeepers.util.bukkit.TextUtils;

/**
 * Helper for teleporting players to shopkeepers.
 */
public final class ShopkeeperTeleporter {

	private static final double TELEPORT_DISTANCE = 2.0D;
	private static final TeleportHelper TELEPORT_HELPER = TeleportHelper.DEFAULT;

	public static boolean teleport(Player player, Shopkeeper shopkeeper, boolean force, @Nullable CommandSender sender) {
		if (shopkeeper.isVirtual()) {
			if (sender != null) {
				TextUtils.sendMessage(sender, Messages.teleportVirtualShopkeeper);
			}
			return false;
		}

		var shopObject = shopkeeper.getShopObject();
		@Nullable Location shopkeeperLocation = shopObject.getLocation();
		if (shopkeeperLocation == null) {
			shopkeeperLocation = shopkeeper.getLocation();

			if (shopkeeperLocation == null) {
				if (sender != null) {
					TextUtils.sendMessage(sender, Messages.teleportShopkeeperWorldNotLoaded);
				}
				return false;
			}
		}
		assert shopkeeperLocation != null;

		// Teleport the player a few blocks in front of the shopkeeper:
		shopkeeperLocation.setYaw(shopkeeper.getYaw());
		shopkeeperLocation.setPitch(0);
		Location destination = shopkeeperLocation.clone()
				.add(shopkeeperLocation.getDirection().multiply(TELEPORT_DISTANCE));

		final int shopOffsetX = shopkeeperLocation.getBlockX() - destination.getBlockX();
		final int shopOffsetZ = shopkeeperLocation.getBlockZ() - destination.getBlockZ();

		@Nullable Location teleportLocation = TELEPORT_HELPER.findSafeDestination(
				destination,
				player,
				// Skip the shopkeeper's location: If the shopkeeper is surrounded by blocks, this
				// can trap the player because the shopkeeper cannot be damaged and its bounding box
				// can prevent the player from breaking any of the surrounding blocks.
				// If the force parameter is used, we check the shopkeeper's location last but still
				// allow to teleport.
				offset -> offset.getX() == shopOffsetX && offset.getZ() == shopOffsetZ ? force
						? 1 : Integer.MAX_VALUE
						: 0
		);
		if (teleportLocation == null) {
			if (!force) {
				if (sender != null) {
					TextUtils.sendMessage(sender, Messages.teleportNoSafeLocationFound);
				}
				return false;
			}

			// Force: Teleport to the shopkeeper's location, even if it is unsafe.
			teleportLocation = shopkeeperLocation;
		}
		assert teleportLocation != null;

		// Let the player face the shopkeeper:
		// Ignoring pitch for now: This would require taking the shopkeeper's (eye) height into
		// account, for all types of shopkeepers.
		var teleportPlayerEyeLocationVector = teleportLocation.toVector();
		teleportPlayerEyeLocationVector.setY(teleportPlayerEyeLocationVector.getY() + player.getEyeHeight(true));
		teleportLocation.setDirection(shopkeeperLocation.toVector().subtract(teleportPlayerEyeLocationVector));
		teleportLocation.setPitch(0);

		player.teleportAsync(teleportLocation).thenAccept(aBoolean -> {
			if (!aBoolean) {
				if (sender != null) {
					TextUtils.sendMessage(sender, Messages.teleportFailed);
				}
				return;
			}

			if (sender != null) {
				TextUtils.sendMessage(sender, Messages.teleportSuccess,
						"player", TextUtils.getPlayerText(player),
						"shop", shopkeeper.getDisplayName()
				);
			}
		});

		return true;
	}

	private ShopkeeperTeleporter() {
	}
}
