package com.nisovin.shopkeepers.util.bukkit;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EquipmentSlot;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.util.java.EnumUtils;

/**
 * Helpers related to entity equipment.
 */
public class EquipmentUtils {

	/**
	 * Checks whether the given entity type supports {@link LivingEntity#getEquipment()}.
	 * 
	 * @param entityType
	 *            the entity type
	 * @return <code>true</code> if the entity type supports {@link LivingEntity#getEquipment()}
	 */
	public static boolean supportsEquipment(EntityType entityType) {
		if (entityType == EntityType.ARMOR_STAND
				|| entityType == EntityType.PLAYER) {
			return true;
		}

		Class<?> entityClass = entityType.getEntityClass();
		if (entityClass == null) return false;

		return Mob.class.isAssignableFrom(entityClass);
	}

	// TODO Added in Bukkit 1.20.5
	public static final Optional<EquipmentSlot> EQUIPMENT_SLOT_BODY;

	// Common supported equipment slot combinations:
	// Lists for fast iteration and lookup by index. No duplicate or null elements.
	// Element order consistent with EquipmentSlot enum.
	public static final List<? extends EquipmentSlot> EQUIPMENT_SLOTS;
	public static final List<? extends EquipmentSlot> EQUIPMENT_SLOTS_HANDS_AND_ARMOR;
	public static final List<? extends EquipmentSlot> EQUIPMENT_SLOTS_HANDS_AND_HEAD;
	public static final List<? extends EquipmentSlot> EQUIPMENT_SLOTS_MAINHAND_AND_HEAD;
	public static final List<? extends EquipmentSlot> EQUIPMENT_SLOTS_HANDS;
	public static final List<? extends EquipmentSlot> EQUIPMENT_SLOTS_MAINHAND;
	public static final List<? extends EquipmentSlot> EQUIPMENT_SLOTS_HEAD;
	public static final List<? extends EquipmentSlot> EQUIPMENT_SLOTS_BODY;

	static {
		@Nullable EquipmentSlot bodySlot = EnumUtils.valueOf(EquipmentSlot.class, "BODY");
		EQUIPMENT_SLOT_BODY = Optional.ofNullable(bodySlot);

		EQUIPMENT_SLOTS = Collections.unmodifiableList(Arrays.asList(EquipmentSlot.values()));

		EQUIPMENT_SLOTS_HANDS_AND_ARMOR = Collections.unmodifiableList(Arrays.asList(
				EquipmentSlot.HAND,
				EquipmentSlot.OFF_HAND,
				EquipmentSlot.FEET,
				EquipmentSlot.LEGS,
				EquipmentSlot.CHEST,
				EquipmentSlot.HEAD
		));

		EQUIPMENT_SLOTS_HANDS_AND_HEAD = Collections.unmodifiableList(Arrays.asList(
				EquipmentSlot.HAND,
				EquipmentSlot.OFF_HAND,
				EquipmentSlot.HEAD
		));

		EQUIPMENT_SLOTS_MAINHAND_AND_HEAD = Collections.unmodifiableList(Arrays.asList(
				EquipmentSlot.HAND,
				EquipmentSlot.HEAD
		));

		EQUIPMENT_SLOTS_HANDS = Collections.unmodifiableList(Arrays.asList(
				EquipmentSlot.HAND,
				EquipmentSlot.OFF_HAND
		));

		EQUIPMENT_SLOTS_MAINHAND = Collections.singletonList(EquipmentSlot.HAND);

		EQUIPMENT_SLOTS_HEAD = Collections.singletonList(EquipmentSlot.HEAD);

		// Added in MC 1.20.5:
		EQUIPMENT_SLOTS_BODY = bodySlot == null ? Collections.emptyList()
				: Collections.singletonList(Unsafe.assertNonNull(bodySlot));
	}

	/**
	 * Gets the {@link EquipmentSlot}s that entities of the specified type support, i.e. that affect
	 * their visual appearance when a (supported) item is equipped in these slots.
	 * 
	 * @param entityType
	 *            the entity type
	 * @return an unmodifiable view on the supported equipment slots, not <code>null</code>, can be
	 *         empty
	 */
	public static List<? extends EquipmentSlot> getSupportedEquipmentSlots(EntityType entityType) {
		switch (entityType.name()) {
		case "PLAYER":
		case "ARMOR_STAND":
		case "ZOMBIE":
		case "ZOMBIE_VILLAGER":
		case "DROWNED":
		case "HUSK":
		case "GIANT":
		case "SKELETON":
		case "WITHER_SKELETON":
		case "STRAY":
		case "BOGGED":
		case "PIGLIN":
		case "PIGLIN_BRUTE":
		case "ZOMBIFIED_PIGLIN":
			return EQUIPMENT_SLOTS_HANDS_AND_ARMOR;
		case "PILLAGER": // Head: Only certain items are rendered
			return EQUIPMENT_SLOTS_HANDS_AND_HEAD;
		case "VILLAGER": // Head: Only certain items are rendered
		case "WANDERING_TRADER": // Head: Only certain items are rendered
			// The main hand item item is only visible when chasing a target.
			// "Johnny" is a separate property, without influence on the visibility of the axe.
		case "VINDICATOR": // Head: Only certain items
			return EQUIPMENT_SLOTS_MAINHAND_AND_HEAD;
		case "VEX":
		case "ALLAY":
			return EQUIPMENT_SLOTS_HANDS;
		case "FOX":
		case "DOLPHIN":
		case "WITCH":
			return EQUIPMENT_SLOTS_MAINHAND;
		case "EVOKER": // Head: Only certain items are rendered
		case "ILLUSIONER": // Head: Only certain items are rendered
			return EQUIPMENT_SLOTS_HEAD;
		case "LLAMA": // Carpet (EquipmentSlot added in Bukkit 1.20.5)
		case "TRADER_LLAMA": // Carpet (EquipmentSlot added in Bukkit 1.20.5)
		case "HORSE": // Horse armor (EquipmentSlot added in Bukkit 1.20.5)
		case "WOLF": // Wolf armor MC 1.20.5
			return EQUIPMENT_SLOTS_BODY;
		default:
			return Collections.emptyList();
		}

		/* Notes on other mobs:
		 * - Strider: Saddle is a separate property.
		 * - Snow golem: Pumpkin head is a separate property.
		 * - Pig: Saddle is a separate property.
		 * - Mule: Chest is a separate property.
		 * - Donkey: Chest is a separate property.
		 * - Enderman: Carried block is a separate property.
		 */
	}

	private EquipmentUtils() {
	}
}
