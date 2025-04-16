package com.nisovin.shopkeepers.compat;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.config.Settings;
import com.nisovin.shopkeepers.util.bukkit.ConfigUtils;
import com.nisovin.shopkeepers.util.bukkit.NamespacedKeyUtils;
import com.nisovin.shopkeepers.util.bukkit.TextUtils;
import com.nisovin.shopkeepers.util.inventory.ItemMigration;
import com.nisovin.shopkeepers.util.inventory.ItemUtils;
import com.nisovin.shopkeepers.util.java.TimeUtils;
import com.nisovin.shopkeepers.util.java.Validate;
import com.nisovin.shopkeepers.util.logging.Log;

/**
 * 测试此插件对服务器 API 实现的一些假设。
 * <p>
 * 任何被证明是错误的假设都会导致插件关闭以避免任何
 * 插件未按预期行为可能导致的损坏。
 * <p>
 * 理想情况下，这些测试应该是轻量级的，以免服务器启动延迟太久。
 */
public class ServerAssumptionsTest {

	private static @Nullable Boolean result = null; // Null if the tests were not yet run

/**
	 * 运行服务器假设测试。
	 * <p>
	 * 测试仅在初始插件启动期间运行一次。任何后续调用
	 * （例如，在后续的软插件重新加载期间）返回上一个测试的结果
	 *执行。
	 *
	 * 如果测试通过，则@return <code>true</code>，如果其中一个测试失败，则为 <code>false</code>。
	 */
	public static boolean run() {
		if (result != null) {
			Log.debug("Skipping already run server assumption tests.");
			return Unsafe.assertNonNull(result);
		}

		long startTime = System.nanoTime();
		Log.debug("Testing server assumptions ...");

		try {
			ServerAssumptionsTest test = new ServerAssumptionsTest();
			test.runAll();

			// Tests passed:
			result = true;
			double durationMillis = TimeUtils.convert(
					System.nanoTime() - startTime,
					TimeUnit.NANOSECONDS,
					TimeUnit.MILLISECONDS
			);
			Log.debug(() -> "Server assumption tests passed (" + TextUtils.format(durationMillis)
					+ " ms).");
		} catch (Exception e) {
			result = false;
			Log.severe("Server assumption test failed: " + e.getMessage());
		}
		return Unsafe.assertNonNull(result);
	}

	/**
	 * This exception is thrown when one of the tested server assumptions is detected to be
	 * incorrect.
	 */
	private static class ServerAssumptionFailedException extends Exception {

		private static final long serialVersionUID = 8174065546288633858L;

		/**
		 * Creates a new {@link ServerAssumptionFailedException}.
		 * 
		 * @param message
		 *            the exception message, not <code>null</code> or empty
		 */
		public ServerAssumptionFailedException(String message) {
			super(Validate.notEmpty(message, "message is null or empty"));
		}
	}

	private static void assumption(
			boolean expression,
			String errorMessage
	) throws ServerAssumptionFailedException {
		if (!expression) {
			throw new ServerAssumptionFailedException(errorMessage);
		}
	}

	private static void assumeIsSimilar(
			ItemStack expected,
			ItemStack actual,
			String errorMessage
	) throws ServerAssumptionFailedException {
		if (!expected.isSimilar(actual)) {
			String detailMessage = "";
			if (Settings.debug) {
				detailMessage = ": Expected [" + ConfigUtils.toConfigYaml("item", expected)
						+ "] got [" + ConfigUtils.toConfigYaml("item", actual) + "]";
			}

			throw new ServerAssumptionFailedException(errorMessage + detailMessage);
		}
	}

	private static void assumeEquals(
			ItemStack expected,
			ItemStack actual,
			String errorMessage
	) throws ServerAssumptionFailedException {
		if (!expected.equals(actual)) {
			String detailMessage = "";
			if (Settings.debug) {
				detailMessage = ": Expected [" + ConfigUtils.toConfigYaml("item", expected)
						+ "] got [" + ConfigUtils.toConfigYaml("item", actual) + "]";
			}

			throw new ServerAssumptionFailedException(errorMessage + detailMessage);
		}
	}

	private final ItemStack bukkitItemStack;
	private final ItemStack bukkitItemStack2;
	private final ItemStack craftItemStack;
	private final ItemStack craftItemStack2;

	private final ItemStack deserializedItemStack;
	private final ItemStack deserializedCraftItemStack;

	private ServerAssumptionsTest() {
		// Setup:
		this.bukkitItemStack = createComplexItemStack();
		this.bukkitItemStack2 = createComplexItemStack();

		this.craftItemStack = toCraftItemStackCopy(bukkitItemStack);
		this.craftItemStack2 = toCraftItemStackCopy(bukkitItemStack2);

		this.deserializedItemStack = deserialize(bukkitItemStack);
		this.deserializedCraftItemStack = toCraftItemStackCopy(deserializedItemStack);
	}

	// Same as TestItemStacks#createItemStackComplete()
	private static ItemStack createComplexItemStack() {
		ItemStack itemStack = new ItemStack(Material.DIAMOND_SWORD);
		// Note: SPIGOT-7571: Use Json-based display names and lore (e.g. via
		// ItemUtils#setDisplayNameAndLore) instead of ItemMeta#setDisplayName / #setLore.
		ItemUtils.setDisplayNameAndLore(
				itemStack,
				"{\"text\":\"Custom Name\",\"color\":\"red\"}",
				Arrays.asList("{\"text\":\"lore1\",\"color\":\"green\"}", "lore2")
		);
		ItemUtils.setItemName(itemStack, "{\"text\":\"Custom item name\",\"color\":\"red\"}");

		ItemMeta itemMeta = Unsafe.assertNonNull(itemStack.getItemMeta());
		itemMeta.setMaxStackSize(65);
		itemMeta.setRarity(ItemRarity.EPIC);
		itemMeta.setHideTooltip(true);
		itemMeta.setCustomModelData(1);
		//itemMeta.setFireResistant(true); // TODO Replaced with damage resistance in MC 1.21.2/3
		itemMeta.setUnbreakable(true);
		((Damageable) itemMeta).setDamage(2);
		((Damageable) itemMeta).setMaxDamage(10);
		((Repairable) itemMeta).setRepairCost(3);

		ToolComponent tool = itemMeta.getTool();
		tool.setDefaultMiningSpeed(1.5f);
		tool.setDamagePerBlock(2);
		tool.addRule(Material.STONE, 0.5f, true);
		itemMeta.setTool(tool);

		itemMeta.setEnchantmentGlintOverride(true);
		itemMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
		itemMeta.addEnchant(Enchantment.SHARPNESS, 2, true);
		itemMeta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED,
				new AttributeModifier(
						new UUID(1L, 1L),
						"attack speed bonus",
						2,
						Operation.ADD_NUMBER,
						EquipmentSlotGroup.HAND
				)
		);
		itemMeta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED,
				new AttributeModifier(
						new UUID(2L, 2L),
						"attack speed bonus 2",
						0.5,
						Operation.MULTIPLY_SCALAR_1,
						EquipmentSlotGroup.OFFHAND
				)
		);
		itemMeta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED,
				new AttributeModifier(
						new UUID(3L, 3L),
						"max health bonus",
						2,
						Operation.ADD_NUMBER,
						EquipmentSlotGroup.HAND
				)
		);
		itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

		FoodComponent food = itemMeta.getFood();
		food.setNutrition(2);
		food.setSaturation(2.5f);
		food.setCanAlwaysEat(true);
		// food.setEatSeconds(5.5f);
		// food.addEffect(new PotionEffect(PotionEffectType.BLINDNESS, 5, 1), 0.5f);
		itemMeta.setFood(food);
		// TODO MC 1.21.2/3:
		// - EquippableComponent
		// - UseCooldownComponent
		// - enchantable, tooltip style, item model, is glider, damage resistance /replaces fire
		// resistance), use remainder, equippable
		// - PotionMeta: custom name

		// Note: This data ends up getting stored in an arbitrary order internally.
		PersistentDataContainer customTags = itemMeta.getPersistentDataContainer();
		customTags.set(
				NamespacedKeyUtils.create("some_plugin", "some-key"),
				PersistentDataType.STRING,
				"some value"
		);
		PersistentDataContainer customContainer = customTags.getAdapterContext().newPersistentDataContainer();
		customContainer.set(
				NamespacedKeyUtils.create("inner_plugin", "inner-key"),
				PersistentDataType.FLOAT,
				0.3F
		);
		customTags.set(
				NamespacedKeyUtils.create("some_plugin", "some-other-key"),
				PersistentDataType.TAG_CONTAINER,
				customContainer
		);
		itemStack.setItemMeta(itemMeta);
		return itemStack;
	}

	private static ItemStack toCraftItemStackCopy(ItemStack itemStack) {
		return ItemMigration.migrateNonNullItemStack(itemStack);
	}

	private static ItemStack deserialize(ItemStack itemStack) {
		String serialized = ConfigUtils.toConfigYaml("item", itemStack);
		ItemStack deserialized = ConfigUtils.fromConfigYaml(serialized, "item");
		return Validate.State.notNull(
				deserialized,
				() -> "Deserialized ItemStack is null! Original: " + itemStack
		);
	}

	// TESTS

	/**
	 * Runs all tests.
	 * 
	 * @throws ServerAssumptionFailedException
	 *             if one of the tests failed
	 */
	private void runAll() throws ServerAssumptionFailedException {
		this.testItemStackComparisons();
		this.testDeserializedItemStackComparisons();
		this.testInventoryItems();
	}

	private void testItemStackComparisons() throws ServerAssumptionFailedException {
		// Item stacks are similar to themselves:
		assumeIsSimilar(
				bukkitItemStack,
				bukkitItemStack,
				"Bukkit ItemStack#isSimilar(self)"
		);
		assumeIsSimilar(
				craftItemStack,
				craftItemStack,
				"CraftItemStack#isSimilar(self)"
		);

		// Item stacks are equal to themselves:
		assumeEquals(
				bukkitItemStack,
				bukkitItemStack,
				"Bukkit ItemStack#equals(self)"
		);
		assumeEquals(
				craftItemStack,
				craftItemStack,
				"CraftItemStack#equals(self)"
		);

		// Item stacks are similar to each other:
		assumeIsSimilar(
				bukkitItemStack,
				bukkitItemStack2,
				"Bukkit ItemStack#isSimilar(other Bukkit ItemStack)"
		);
		assumeIsSimilar(
				bukkitItemStack,
				craftItemStack,
				"Bukkit ItemStack#isSimilar(CraftItemStack)"
		);
		assumeIsSimilar(
				craftItemStack,
				craftItemStack2,
				"CraftItemStack#isSimilar(other CraftItemStack)"
		);
		assumeIsSimilar(
				craftItemStack,
				bukkitItemStack,
				"CraftItemStack#isSimilar(Bukkit ItemStack)"
		);

		// Item stacks are equal to each other:
		assumeEquals(
				bukkitItemStack,
				bukkitItemStack2,
				"Bukkit ItemStack#equals(other Bukkit ItemStack)"
		);
		assumeEquals(
				bukkitItemStack,
				craftItemStack,
				"Bukkit ItemStack#equals(CraftItemStack)"
		);
		assumeEquals(
				craftItemStack,
				craftItemStack2,
				"CraftItemStack#equals(other CraftItemStack)"
		);
		assumeEquals(
				craftItemStack,
				bukkitItemStack,
				"CraftItemStack#equals(Bukkit ItemStack)"
		);
	}

	private void testDeserializedItemStackComparisons() throws ServerAssumptionFailedException {
		// Item stacks are similar to deserialized item stack:
		assumeIsSimilar(
				bukkitItemStack,
				deserializedItemStack,
				"Bukkit ItemStack#isSimilar(Deserialized ItemStack)"
		);
		assumeIsSimilar(
				craftItemStack,
				deserializedItemStack,
				"CraftItemStack#isSimilar(Deserialized ItemStack)"
		);

		// Item stacks are similar to deserialized CraftItemStack:
		assumeIsSimilar(
				bukkitItemStack,
				deserializedCraftItemStack,
				"Bukkit ItemStack#isSimilar(Deserialized CraftItemStack)"
		);
		assumeIsSimilar(
				craftItemStack,
				deserializedCraftItemStack,
				"CraftItemStack#isSimilar(Deserialized CraftItemStack)"
		);

		// Deserialized item stack is similar to item stacks:
		assumeIsSimilar(
				deserializedItemStack,
				bukkitItemStack,
				"Deserialized ItemStack#isSimilar(Bukkit ItemStack)"
		);
		assumeIsSimilar(
				deserializedItemStack,
				craftItemStack,
				"Deserialized ItemStack#isSimilar(CraftItemStack)"
		);

		// Deserialized CraftItemStack is similar to item stacks:
		assumeIsSimilar(
				deserializedCraftItemStack,
				bukkitItemStack,
				"Deserialized CraftItemStack#isSimilar(Bukkit ItemStack)"
		);
		assumeIsSimilar(
				deserializedCraftItemStack,
				craftItemStack,
				"Deserialized CraftItemStack#isSimilar(CraftItemStack)"
		);
	}

	private void testInventoryItems() throws ServerAssumptionFailedException {
		Inventory inventory = Bukkit.createInventory(null, 9);
		ItemStack bukkitItemStack = new ItemStack(Material.STONE, 1);

		// Inventory#setItem copies the item stack:
		inventory.setItem(0, bukkitItemStack);
		bukkitItemStack.setAmount(2);
		ItemStack fromInventory = Unsafe.assertNonNull(inventory.getItem(0));
		assumption(
				fromInventory.getAmount() == 1,
				"Inventory#setItem did not copy the ItemStack!"
		);

		// Inventory#getItem returns a live wrapper of the underlying item stack:
		fromInventory.setAmount(3);
		ItemStack fromInventory2 = Unsafe.assertNonNull(inventory.getItem(0));
		assumption(
				fromInventory2.getAmount() == 3,
				"Inventory#getItem did not return a live wrapper of the inventory's ItemStack!"
		);
	}
}
