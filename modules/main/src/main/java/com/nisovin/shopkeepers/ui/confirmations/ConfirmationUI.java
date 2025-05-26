package com.nisovin.shopkeepers.ui.confirmations;

import org.bukkit.entity.Player;

import com.nisovin.shopkeepers.SKShopkeepersPlugin;
import com.nisovin.shopkeepers.util.java.Validate;

public final class ConfirmationUI {

	public static void requestConfirmation(
			Player player,
			ConfirmationUIConfig config,
			Runnable action,
			Runnable onCancelled
	) {
		Validate.notNull(player, "player is null");
		Validate.notNull(config, "config is null");
		Validate.notNull(action, "action is null");
		Validate.notNull(onCancelled, "onCancelled is null");

		ConfirmationUIHandler ui = new ConfirmationUIHandler(config, action, onCancelled, player.getLocation());
		// Note: This also closes any previous UI and thereby also aborts any previously active UI
		// confirmation request.
		SKShopkeepersPlugin.getInstance().getUIRegistry().requestUI(ui, player);
	}

	private ConfirmationUI() {
	}
}
