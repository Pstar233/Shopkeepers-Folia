package com.nisovin.shopkeepers.commands.lib.arguments;

import java.util.Collections;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.entity.Player;

import com.nisovin.shopkeepers.commands.lib.CommandInput;
import com.nisovin.shopkeepers.commands.lib.argument.filter.ArgumentFilter;
import com.nisovin.shopkeepers.commands.lib.context.CommandContextView;
import com.nisovin.shopkeepers.lang.Messages;
import com.nisovin.shopkeepers.text.Text;
import com.nisovin.shopkeepers.util.bukkit.EntityUtils;

/**
 * Provides suggestions for the UUIDs of online players.
 * <p>
 * By default this accepts any UUID regardless of whether it corresponds to an online player.
 */
public class PlayerUUIDArgument extends ObjectUUIDArgument {

	public static final int DEFAULT_MINIMUM_COMPLETION_INPUT = ObjectUUIDArgument.DEFAULT_MINIMUM_COMPLETION_INPUT;

	// Note: Not providing a default argument filter that only accepts uuids of online players,
	// because this can be achieved more efficiently by using PlayerByUUIDArgument instead.

	public PlayerUUIDArgument(String name) {
		this(name, ArgumentFilter.acceptAny());
	}

	public PlayerUUIDArgument(String name, ArgumentFilter<? super UUID> filter) {
		this(name, filter, DEFAULT_MINIMUM_COMPLETION_INPUT);
	}

	public PlayerUUIDArgument(
			String name,
			ArgumentFilter<? super UUID> filter,
			int minimumCompletionInput
	) {
		super(name, filter, minimumCompletionInput);
	}

	@Override
	public Text getMissingArgumentErrorMsg() {
		Text text = Messages.commandPlayerArgumentMissing;
		text.setPlaceholderArguments(this.getDefaultErrorMsgArgs());
		return text;
	}

	// Using the uuid argument's 'invalid argument' message if the uuid is invalid.
	// Using the filter's 'invalid argument' message if the uuid is not accepted.

	/**
	 * Gets the default uuid completion suggestions.
	 * 
	 * @param input
	 *            the command input, not <code>null</code>
	 * @param context
	 *            the command context, not <code>null</code>
	 * @param minimumCompletionInput
	 *            the minimum input length before completion suggestions are provided
	 * @param uuidPrefix
	 *            the uuid prefix, may be empty, not <code>null</code>
	 * @param playerFilter
	 *            only suggestions for players accepted by this filter are included
	 * @return the player uuid completion suggestions
	 */
	public static Iterable<? extends UUID> getDefaultCompletionSuggestions(
			CommandInput input,
			CommandContextView context,
			int minimumCompletionInput,
			String uuidPrefix,
			ArgumentFilter<? super Player> playerFilter
	) {
		// Only provide suggestions if there is a minimum length input:
		if (uuidPrefix.length() < minimumCompletionInput) {
			return Collections.emptyList();
		}

		String normalizedUUIDPrefix = uuidPrefix.toLowerCase(Locale.ROOT);
		return EntityUtils.getOnlinePlayersStream()
				.filter(player -> playerFilter.test(input, context, player))
				.map(Player::getUniqueId)
				.filter(uuid -> {
					// Assumption: UUID#toString is already lowercase (normalized).
					return uuid.toString().startsWith(normalizedUUIDPrefix);
				})::iterator;
	}

	@Override
	protected Iterable<? extends UUID> getCompletionSuggestions(
			CommandInput input,
			CommandContextView context,
			String idPrefix
	) {
		return getDefaultCompletionSuggestions(
				input,
				context,
				minimumCompletionInput,
				idPrefix,
				ArgumentFilter.acceptAny()
		);
	}
}
