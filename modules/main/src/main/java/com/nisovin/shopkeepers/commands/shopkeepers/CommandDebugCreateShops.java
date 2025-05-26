package com.nisovin.shopkeepers.commands.shopkeepers;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.nisovin.shopkeepers.SKShopkeepersPlugin;
import com.nisovin.shopkeepers.api.ShopkeepersPlugin;
import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.api.shopkeeper.DefaultShopTypes;
import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import com.nisovin.shopkeepers.api.shopkeeper.admin.AdminShopCreationData;
import com.nisovin.shopkeepers.api.shopkeeper.admin.AdminShopType;
import com.nisovin.shopkeepers.api.shopobjects.DefaultShopObjectTypes;
import com.nisovin.shopkeepers.api.shopobjects.ShopObjectType;
import com.nisovin.shopkeepers.api.shopobjects.living.LivingShopEquipment;
import com.nisovin.shopkeepers.api.shopobjects.living.LivingShopObject;
import com.nisovin.shopkeepers.api.util.UnmodifiableItemStack;
import com.nisovin.shopkeepers.commands.lib.CommandException;
import com.nisovin.shopkeepers.commands.lib.CommandInput;
import com.nisovin.shopkeepers.commands.lib.arguments.LiteralArgument;
import com.nisovin.shopkeepers.commands.lib.arguments.PositiveIntegerArgument;
import com.nisovin.shopkeepers.commands.lib.commands.PlayerCommand;
import com.nisovin.shopkeepers.commands.lib.context.CommandContextView;
import com.nisovin.shopkeepers.text.Text;
import com.nisovin.shopkeepers.util.bukkit.BlockFaceUtils.BlockFaceDirections;

class CommandDebugCreateShops extends PlayerCommand {

	private static final String ARGUMENT_SHOP_COUNT = "shopCount";
	private static final String ARGUMENT_TEST_EQUIPMENT = "testEquipment";

	private final SKShopkeepersPlugin plugin;

	CommandDebugCreateShops(SKShopkeepersPlugin plugin) {
		super("debugCreateShops");
		this.plugin = plugin;

		// Set permission:
		this.setPermission(ShopkeepersPlugin.DEBUG_PERMISSION);

		// Set description:
		this.setDescription(Text.of("Creates lots of shopkeepers for stress testing."));

		// Hidden debugging command:
		this.setHiddenInParentHelp(true);

		// Arguments:
		this.addArgument(new PositiveIntegerArgument(ARGUMENT_SHOP_COUNT).orDefaultValue(10));
		this.addArgument(new LiteralArgument(ARGUMENT_TEST_EQUIPMENT).optional());
	}

	@Override
	protected void execute(CommandInput input, CommandContextView context) throws CommandException {
		assert (input.getSender() instanceof Player);
		Player player = (Player) input.getSender();

		boolean testEquipment = context.has(ARGUMENT_TEST_EQUIPMENT);
		if (testEquipment) {
			// We ignore the shop count argument in this case.
			this.spawnEquipmentTest(player);
			return;
		}

		int shopCount = context.get(ARGUMENT_SHOP_COUNT);
		// Not using BoundedIntegerArgument for now due to missing descriptive error messages.
		// TODO Use this in the future.
		if (shopCount > 1000) {
			player.sendMessage(ChatColor.RED + "Shopkeeper count to high, limiting to 1000!");
			shopCount = 1000;
		}

		this.spawnCount(player, shopCount);
	}

	private void spawnCount(Player player, int shopCount) {
		player.sendMessage(ChatColor.GREEN + "Creating up to " + shopCount
				+ " shopkeepers, starting here!");

		final int stepSize = 2;
		BlockFace blockFace = BlockFaceDirections.CARDINAL.fromYaw(player.getEyeLocation().getYaw());

		AdminShopType<?> shopType = DefaultShopTypes.ADMIN_REGULAR();
		ShopObjectType<?> shopObjectType = Unsafe.assertNonNull(
				DefaultShopObjectTypes.LIVING().get(EntityType.VILLAGER)
		);

		int created = 0;
		Location currrentSpawnLocation = player.getLocation();
		for (int i = 0; i < shopCount; i++) {
			Shopkeeper shopkeeper = plugin.handleShopkeeperCreation(AdminShopCreationData.create(
					player,
					shopType,
					shopObjectType,
					currrentSpawnLocation.clone(),
					null
			));
			if (shopkeeper != null) {
				created++;
			}

			currrentSpawnLocation.add(
					stepSize * blockFace.getModX(),
					0,
					stepSize * blockFace.getModZ()
			);
		}
		player.sendMessage(ChatColor.GREEN + "Done! Created " + ChatColor.YELLOW + created
				+ ChatColor.GREEN + " shopkeepers!");
	}

	private void spawnEquipmentTest(Player player) {
		player.sendMessage(ChatColor.GREEN + "Creating a shopkeeper with equipment for each enabled"
				+ " mob type, starting here!");

		final int stepSize = 3;
		BlockFace blockFace = BlockFaceDirections.CARDINAL.fromYaw(player.getEyeLocation().getYaw());

		AdminShopType<?> shopType = DefaultShopTypes.ADMIN_REGULAR();

		int created = 0;
		Location currrentSpawnLocation = player.getLocation();
		for (ShopObjectType<?> shopObjectType : DefaultShopObjectTypes.LIVING().getAll()) {
			if (!shopObjectType.isEnabled()) continue;

			Shopkeeper shopkeeper = plugin.handleShopkeeperCreation(AdminShopCreationData.create(
					player,
					shopType,
					shopObjectType,
					currrentSpawnLocation.clone(),
					null
			));

			currrentSpawnLocation.add(
					stepSize * blockFace.getModX(),
					0,
					stepSize * blockFace.getModZ()
			);

			if (shopkeeper == null) continue;

			created++;

			// For testing purposes: Add equipment to all mobs, even if they might not support /
			// render it.
			LivingShopObject shopObject = (LivingShopObject) shopkeeper.getShopObject();
			LivingShopEquipment equipment = shopObject.getEquipment();

			equipment.setItem(EquipmentSlot.HAND, UnmodifiableItemStack.of(new ItemStack(Material.DIAMOND_SWORD)));
			equipment.setItem(EquipmentSlot.OFF_HAND, UnmodifiableItemStack.of(new ItemStack(Material.IRON_SWORD)));
			// Some mobs don't support helmets, but certain items/blocks render fine:
			equipment.setItem(EquipmentSlot.HEAD, UnmodifiableItemStack.of(new ItemStack(Material.EMERALD_BLOCK)));
			equipment.setItem(EquipmentSlot.CHEST, UnmodifiableItemStack.of(new ItemStack(Material.DIAMOND_CHESTPLATE)));
			equipment.setItem(EquipmentSlot.LEGS, UnmodifiableItemStack.of(new ItemStack(Material.DIAMOND_LEGGINGS)));
			equipment.setItem(EquipmentSlot.FEET, UnmodifiableItemStack.of(new ItemStack(Material.DIAMOND_BOOTS)));
			// BODY: Only supported by specific mobs and for specific items (e.g. horse and wolf
			// armor, llama carpet).
			// SADDLE: Only supported by specific mob types (horse, llama, strider, pig), which come
			// with a dedicated editor option.
		}

		player.sendMessage(ChatColor.GREEN + "Done! Created " + ChatColor.YELLOW + created
				+ ChatColor.GREEN + " shopkeepers!");
	}
}
