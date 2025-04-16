package com.nisovin.shopkeepers.util.bukkit;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.api.user.User;
import com.nisovin.shopkeepers.api.util.ChunkCoords;
import com.nisovin.shopkeepers.api.util.UnmodifiableItemStack;
import com.nisovin.shopkeepers.compat.NMSManager;
import com.nisovin.shopkeepers.spigot.text.SpigotText;
import com.nisovin.shopkeepers.text.HoverEventText;
import com.nisovin.shopkeepers.text.Text;
import com.nisovin.shopkeepers.text.TextBuilder;
import com.nisovin.shopkeepers.util.annotations.ReadOnly;
import com.nisovin.shopkeepers.util.inventory.ItemUtils;
import com.nisovin.shopkeepers.util.java.StringUtils;
import com.nisovin.shopkeepers.util.java.Validate;
import com.nisovin.shopkeepers.util.text.MessageArguments;

/**
 * Text and messaging utilities.
 * <p>
 * In contrast to {@link StringUtils}, this contains utilities that are more Minecraft and messaging
 * specific.
 */
public final class TextUtils {

	/*
	 * PLAIN TEXT
	 */

	// FORMATTING AND CONVERSION

	private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat(
			"0.##",
			new DecimalFormatSymbols(Locale.US)
	);
	private static final DecimalFormat DECIMAL_FORMAT_PRECISE = new DecimalFormat(
			"0.####",
			new DecimalFormatSymbols(Locale.US)
	);
	static {
		DECIMAL_FORMAT.setGroupingUsed(false);
		DECIMAL_FORMAT_PRECISE.setGroupingUsed(false);
	}

	private static final String UNKNOWN_PLAYER = "[unknown]";

	public static String format(double number) {
		return Unsafe.assertNonNull(DECIMAL_FORMAT.format(number));
	}

	public static String formatPrecise(double number) {
		return Unsafe.assertNonNull(DECIMAL_FORMAT_PRECISE.format(number));
	}

	public static String getLocationString(Location location) {
		return getLocationString(
				LocationUtils.getWorld(location).getName(),
				location.getX(),
				location.getY(),
				location.getZ()
		);
	}

	public static String getLocationString(Block block) {
		Validate.notNull(block, "block is null");
		return getLocationString(
				block.getWorld().getName(),
				block.getX(),
				block.getY(),
				block.getZ()
		);
	}

	public static String getLocationString(BlockLocation blockLocation) {
		Validate.notNull(blockLocation, "blockLocation is null");
		return getLocationString(
				blockLocation.getWorldName(),
				blockLocation.getX(),
				blockLocation.getY(),
				blockLocation.getZ()
		);
	}

	public static String getLocationString(BlockLocation blockLocation, double yaw) {
		Validate.notNull(blockLocation, "blockLocation is null");
		return getLocationString(
				blockLocation.getWorldName(),
				blockLocation.getX(),
				blockLocation.getY(),
				blockLocation.getZ(),
				yaw
		);
	}

	// More performant variant if coordinates are integers:
	public static String getLocationString(@Nullable String worldName, int x, int y, int z) {
		return worldName + "," + x + "," + y + "," + z;
	}

	public static String getLocationString(
			@Nullable String worldName,
			int x,
			int y,
			int z,
			double yaw
	) {
		return worldName + "," + x + "," + y + "," + z + "," + DECIMAL_FORMAT.format(yaw);
	}

	public static String getLocationString(
			@Nullable String worldName,
			double x,
			double y,
			double z
	) {
		return worldName
				+ "," + DECIMAL_FORMAT.format(x)
				+ "," + DECIMAL_FORMAT.format(y)
				+ "," + DECIMAL_FORMAT.format(z);
	}

	public static String getChunkString(ChunkCoords chunk) {
		Validate.notNull(chunk, "chunk is null");
		return getChunkString(chunk.getWorldName(), chunk.getChunkX(), chunk.getChunkZ());
	}

	public static String getChunkString(Chunk chunk) {
		Validate.notNull(chunk, "chunk is null");
		return getChunkString(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
	}

	public static String getChunkString(String worldName, int cx, int cz) {
		return worldName + "," + cx + "," + cz;
	}

	public static String getPlayerString(Player player) {
		Validate.notNull(player, "player is null");
		return getPlayerString(player.getName(), player.getUniqueId());
	}

	public static String getPlayerString(User user) {
		Validate.notNull(user, "user is null");
		return getPlayerString(user.getLastKnownName(), user.getUniqueId());
	}

	public static String getPlayerString(@Nullable String playerName, @Nullable UUID playerUUID) {
		// Either of them might be null.
		if (playerName != null) {
			return playerName + (playerUUID == null ? "" : " (" + playerUUID + ")");
		} else if (playerUUID != null) {
			return playerUUID.toString();
		} else {
			return UNKNOWN_PLAYER;
		}
	}

	public static String getPlayerNameOrUUID(
			@Nullable String playerName,
			@Nullable UUID playerUUID
	) {
		// Either of them might be null.
		// Prefer name, else use uuid.
		if (playerName != null) {
			return playerName;
		} else if (playerUUID != null) {
			return playerUUID.toString();
		} else {
			return UNKNOWN_PLAYER;
		}
	}

	public static String getPlayerNameOrUnknown(@Nullable String playerName) {
		return getPlayerNameOrUUID(playerName, null);
	}

	public static final char COLOR_CHAR_ALTERNATIVE = '&';
	private static final Pattern STRIP_COLOR_ALTERNATIVE_PATTERN = Pattern.compile(
			"(?i)" + COLOR_CHAR_ALTERNATIVE + "[0-9A-FK-ORX]"
	);
	private static final String COLOR_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

	public static boolean isAnyColorChar(char c) {
		return c == ChatColor.COLOR_CHAR || c == COLOR_CHAR_ALTERNATIVE;
	}

	// Only checks for the Minecraft color code character, not the alternative color code character.
	public static boolean containsColorChar(String text) {
		return StringUtils.contains(text, ChatColor.COLOR_CHAR);
	}

	// Reverse of ChatColor#translateAlternateColorCodes:
	public static String translateColorCodesToAlternative(char altColorChar, String text) {
		Validate.notNull(text, "text is null");
		char[] c = text.toCharArray();
		for (int i = 0; i < c.length - 1; i++) {
			if (c[i] == ChatColor.COLOR_CHAR && COLOR_CODES.indexOf(c[i + 1]) > -1) {
				c[i] = altColorChar;
				c[i + 1] = Character.toLowerCase(c[i + 1]);
			}
		}
		return new String(c);
	}

	public static String stripColor(String text) {
		Validate.notNull(text, "text is null");
		if (text.isEmpty()) return text;
		String uncolored = Unsafe.assertNonNull(ChatColor.stripColor(text));
		uncolored = STRIP_COLOR_ALTERNATIVE_PATTERN.matcher(uncolored).replaceAll("");
		return uncolored;
	}

	public static String decolorize(String text) {
		Validate.notNull(text, "text is null");
		if (text.isEmpty()) return text;
		return translateColorCodesToAlternative(COLOR_CHAR_ALTERNATIVE, text);
	}

	public static List<String> decolorize(List<? extends String> list) {
		Validate.notNull(list, "list is null");
		List<String> decolored = new ArrayList<>(list.size());
		for (String string : list) {
			decolored.add(translateColorCodesToAlternative(COLOR_CHAR_ALTERNATIVE, string));
		}
		return decolored;
	}

	// Decolorizes String entries, otherwise adopts them as they are:
	public static List<?> decolorizeUnknown(List<?> list) {
		Validate.notNull(list, "list is null");
		List<@Nullable Object> decolored = new ArrayList<>(list.size());
		for (Object entry : list) {
			Object decolorizedEntry = entry;
			if (entry instanceof String) {
				decolorizedEntry = translateColorCodesToAlternative(
						COLOR_CHAR_ALTERNATIVE,
						(String) entry
				);
			}
			decolored.add(decolorizedEntry);
		}
		return decolored;
	}

	/**
	 * Translates {@code &}-based color codes to Minecraft's {@code §}-based color codes.
	 * 
	 * @param text
	 *            the text with {@code &}-based color codes, not <code>null</code>
	 * @return the text with Minecraft's color codes
	 */
	public static String colorize(String text) {
		Validate.notNull(text, "text is null");
		if (text.isEmpty()) return text;
		return ChatColor.translateAlternateColorCodes(COLOR_CHAR_ALTERNATIVE, text);
	}

	/**
	 * Translates {@code &}-based color codes to Minecraft's {@code §}-based color codes.
	 * 
	 * @param list
	 *            the texts with {@code &}-based color codes, not <code>null</code>
	 * @return a new list containing the corresponding texts with Minecraft's color codes
	 */
	public static List<String> colorize(List<? extends String> list) {
		Validate.notNull(list, "list is null");
		List<String> colored = new ArrayList<>(list.size());
		for (String text : list) {
			colored.add(colorize(text));
		}
		return colored;
	}

	// Colorizes String entries, otherwise adopts them as they are:
	public static List<?> colorizeUnknown(List<?> list) {
		Validate.notNull(list, "list is null");
		List<@Nullable Object> colored = new ArrayList<>(list.size());
		for (Object entry : list) {
			Object colorizedEntry = entry;
			if (entry instanceof String) {
				colorizedEntry = colorize((String) entry);
			}
			colored.add(colorizedEntry);
		}
		return colored;
	}

	// HEX COLORS

	public static final char HEX_CHAR = '#';
	private static final String HEX_DIGITS = "0123456789AaBbCcDdEeFf";

	private static boolean isHexDigit(char c) {
		return (HEX_DIGITS.indexOf(c) != -1);
	}

	// "#aabbcc"
	public static boolean isHexCode(String string) {
		if (string.length() != 7) return false;
		if (string.charAt(0) != '#') return false;
		for (int i = 1; i < 7; i++) {
			char c = string.charAt(i);
			if (!isHexDigit(c)) return false;
		}
		return true;
	}

	// "&x&a&a&b&b&c&c"
	public static boolean isBukkitHexCode(String string) {
		if (string.length() != 14) return false;

		if (!isAnyColorChar(string.charAt(0))) return false;
		char c1 = string.charAt(1);
		if (c1 != 'x' && c1 != 'X') return false;

		for (int i = 2; i < 14; i += 2) {
			if (!isAnyColorChar(string.charAt(i))) return false;
			if (!isHexDigit(string.charAt(i + 1))) return false;
		}
		return true;
	}

	// Converts from "#aabbcc" to "&x&a&a&b&b&c&c" (using the given formatting code)
	public static String toBukkitHexCode(String hexCode, char formattingCode) {
		Validate.notEmpty(hexCode, "hexCode is null or empty");
		Validate.isTrue(hexCode.length() == 7, () -> "Invalid hexCode: " + hexCode);
		StringBuilder bukkitHexCode = new StringBuilder(14);
		bukkitHexCode.append(formattingCode).append('x');
		for (int i = 1; i < 7; i++) {
			char c = hexCode.charAt(i);
			bukkitHexCode.append(formattingCode).append(c);
		}
		return bukkitHexCode.toString();
	}

	// Converts all "&#aabbcc" to "&x&a&a&b&b&c&c" (uses the found color code character)
	public static String convertHexColorsToBukkit(String text) {
		Validate.notNull(text, "text is null");
		StringBuilder result = new StringBuilder(text);
		for (int i = 0; i < result.length() - 1; i++) {
			char colorChar = result.charAt(i);
			if (!isAnyColorChar(colorChar)) continue;

			if (result.charAt(i + 1) != '#') continue;
			if (i + 7 >= result.length()) continue;
			if (!isHexCode(result.substring(i + 1, i + 8))) continue;

			result.setCharAt(i, colorChar);
			result.setCharAt(i + 1, 'x');
			for (int j = 2; j < 14; j += 2) {
				result.insert(i + j, colorChar);
			}
			i += 13;
		}
		return result.toString();
	}

	// Converts from "&x&a&a&b&b&c&c" or "§x§a§a§b§b§c§c" to "#aabbcc"
	public static String fromBukkitHexCode(String bukkitHexCode) {
		Validate.notEmpty(bukkitHexCode, "bukkitHexCode is null or empty");
		Validate.isTrue(bukkitHexCode.length() == 14,
				() -> "Invalid bukkitHexCode: " + bukkitHexCode);
		StringBuilder hexCode = new StringBuilder(7);
		hexCode.append('#');
		for (int i = 3; i < 14; i += 2) {
			char c = bukkitHexCode.charAt(i);
			hexCode.append(c);
		}
		return hexCode.toString();
	}

	// SENDING

	public static void sendMessage(CommandSender recipient, String message) {
		Validate.notNull(recipient, "recipient is null");
		Validate.notNull(message, "message is null");
		// Skip sending if message is empty: Allows disabling of messages.
		if (message.isEmpty()) return;

		// Send (potentially multi-line) message:
		for (String line : StringUtils.splitLines(message, true)) {
			recipient.sendMessage(line);
		}
	}

	public static void sendMessage(
			CommandSender recipient,
			String message,
			Map<? extends String, @NonNull ?> arguments
	) {
		// Replace message arguments and then send:
		sendMessage(recipient, StringUtils.replaceArguments(message, arguments));
	}

	public static void sendMessage(
			CommandSender recipient,
			String message,
			@NonNull Object... argumentPairs
	) {
		// Replace message arguments and then send:
		sendMessage(recipient, StringUtils.replaceArguments(message, argumentPairs));
	}

	/*
	 * TEXT COMPONENTS
	 */

	public static Text getPlayerText(Player player) {
		Validate.notNull(player, "player is null");
		String playerName = Unsafe.assertNonNull(player.getName());
		String playerUUIDString = player.getUniqueId().toString();
		return Text.hoverEvent(Text.of(playerUUIDString))
				.childInsertion(playerUUIDString)
				.childText(playerName)
				.buildRoot();
	}

	public static Text getPlayerText(@Nullable String playerName, @Nullable UUID playerUUID) {
		// Either of them might be null.
		// Prefer name, else use uuid.
		if (playerName != null) {
			if (playerUUID != null) {
				String playerUUIDString = playerUUID.toString();
				return Text.hoverEvent(Text.of(playerUUIDString))
						.childInsertion(playerUUIDString)
						.childText(playerName)
						.buildRoot();
			} else {
				return Text.of(playerName);
			}
		} else if (playerUUID != null) {
			return Text.of(playerUUID.toString());
		} else {
			return Text.of(UNKNOWN_PLAYER);
		}
	}

	public static Text getItemText(@Nullable UnmodifiableItemStack itemStack) {
		return getItemText(ItemUtils.asItemStackOrNull(itemStack));
	}

	public static Text getItemText(@ReadOnly @Nullable ItemStack itemStack) {
		if (itemStack == null) return Text.text("");
		return TextUtils.getItemHover(itemStack)
				.child(getMaterialNameForDisplay(itemStack))
				.getRoot();
	}

	public static TextBuilder getItemHover(@ReadOnly ItemStack itemStack) {
		Validate.notNull(itemStack, "itemStack is null");
		String itemSNBT = NMSManager.getProvider().getItemSNBT(itemStack);
		if (itemSNBT == null) {
			// Item SNBT is not supported.
			return Text.text("");
		} else {
			return Text.hoverEvent(HoverEventText.Action.SHOW_ITEM, Text.of(itemSNBT));
		}
	}

	/**
	 * Formats the name of the given {@link Material} to a more user-friendly representation.
	 * <p>
	 * This first attempts to use a client-translatable Text, and if not available, falls back to
	 * using {@link ItemUtils#formatMaterialName(Material)}. If the given material is
	 * <code>null</code>, this returns an empty Text.
	 * 
	 * @param material
	 *            the material, can be <code>null</code>
	 * @return the formatted material name, not <code>null</code>
	 */
	public static Text getMaterialNameForDisplay(@Nullable Material material) {
		if (material == null) return Text.EMPTY;
		Text formattedName = Text.of(ItemUtils.formatMaterialName(material));
		// Note: This might not necessarily match the name that is usually displayed for an
		// ItemStack, but rather the translated item type name (for example for items such as
		// different types of potions, skulls, etc.).
		String translationKey = material.getTranslationKey();
		// We use the formatted name as fallback.
		return Text.translatable(translationKey).child(formattedName).getRoot();
	}

	/**
	 * Formats the {@link Material} name of the given {@link ItemStack} to a more user-friendly
	 * representation.
	 * <p>
	 * If the given item stack is <code>null</code>, this returns an empty Text.
	 * 
	 * @param itemStack
	 *            the item stack, can be <code>null</code>
	 * @return the formatted material name, not <code>null</code>
	 * @see #getMaterialNameForDisplay(Material)
	 */
	public static Text getMaterialNameForDisplay(@Nullable ItemStack itemStack) {
		return getMaterialNameForDisplay(itemStack != null ? itemStack.getType() : null);
	}

	// SENDING

	public static void sendMessage(CommandSender recipient, Text message) {
		SpigotText.sendMessage(recipient, message);
	}

	public static void sendMessage(
			CommandSender recipient,
			Text message,
			MessageArguments arguments
	) {
		Validate.notNull(recipient, "recipient is null");
		Validate.notNull(message, "message is null");
		// Assign arguments and then send:
		message.setPlaceholderArguments(arguments);
		sendMessage(recipient, message);
	}

	public static void sendMessage(
			CommandSender recipient,
			Text message,
			Map<? extends String, @NonNull ?> arguments
	) {
		Validate.notNull(recipient, "recipient is null");
		Validate.notNull(message, "message is null");
		// Assign arguments and then send:
		message.setPlaceholderArguments(arguments);
		sendMessage(recipient, message);
	}

	public static void sendMessage(
			CommandSender recipient,
			Text message,
			@NonNull Object... argumentPairs
	) {
		Validate.notNull(recipient, "recipient is null");
		Validate.notNull(message, "message is null");
		// Assign arguments and then send:
		message.setPlaceholderArguments(argumentPairs);
		sendMessage(recipient, message);
	}

	private TextUtils() {
	}
}
