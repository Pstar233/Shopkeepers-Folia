package com.nisovin.shopkeepers.commands.shopkeepers;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.nisovin.shopkeepers.api.ShopkeepersPlugin;
import com.nisovin.shopkeepers.api.ui.DefaultUITypes;
import com.nisovin.shopkeepers.commands.arguments.ShopkeeperArgument;
import com.nisovin.shopkeepers.commands.arguments.ShopkeeperFilter;
import com.nisovin.shopkeepers.commands.arguments.TargetShopkeeperFallback;
import com.nisovin.shopkeepers.commands.lib.Command;
import com.nisovin.shopkeepers.commands.lib.CommandException;
import com.nisovin.shopkeepers.commands.lib.CommandInput;
import com.nisovin.shopkeepers.commands.lib.arguments.PlayerArgument;
import com.nisovin.shopkeepers.commands.lib.context.CommandContextView;
import com.nisovin.shopkeepers.commands.util.ShopkeeperArgumentUtils.TargetShopkeeperFilter;
import com.nisovin.shopkeepers.lang.Messages;
import com.nisovin.shopkeepers.shopkeeper.player.AbstractPlayerShopkeeper;
import com.nisovin.shopkeepers.util.bukkit.TextUtils;

class CommandTransfer extends Command {

	private static final String ARGUMENT_SHOPKEEPER = "shopkeeper";
	private static final String ARGUMENT_NEW_OWNER = "new-owner";

	CommandTransfer() {
		super("transfer");

		// Set permission:
		this.setPermission(ShopkeepersPlugin.TRANSFER_PERMISSION);

		// Set description:
		this.setDescription(Messages.commandDescriptionTransfer);

		// Arguments:
		this.addArgument(new TargetShopkeeperFallback(
				new ShopkeeperArgument(ARGUMENT_SHOPKEEPER,
						ShopkeeperFilter.PLAYER
								.and(ShopkeeperFilter.withAccess(DefaultUITypes.EDITOR()))),
				TargetShopkeeperFilter.PLAYER
		));
		this.addArgument(new PlayerArgument(ARGUMENT_NEW_OWNER)); // New owner has to be online
		// TODO Allow offline player?
	}

	@Override
	protected void execute(CommandInput input, CommandContextView context) throws CommandException {
		CommandSender sender = input.getSender();

		AbstractPlayerShopkeeper shopkeeper = context.get(ARGUMENT_SHOPKEEPER);
		Player newOwner = context.get(ARGUMENT_NEW_OWNER);

		// Check that the sender can edit this shopkeeper:
		if (!shopkeeper.canEdit(sender, false)) {
			return;
		}

		// Set new owner:
		shopkeeper.setOwner(newOwner);

		// Success:
		TextUtils.sendMessage(sender, Messages.ownerSet,
				"owner", TextUtils.getPlayerText(newOwner)
		);

		// Save:
		ShopkeepersPlugin.getInstance().getShopkeeperStorage().save();
	}
}
