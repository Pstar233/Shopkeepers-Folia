package com.nisovin.shopkeepers.text;

import com.nisovin.shopkeepers.util.java.Validate;
import com.nisovin.shopkeepers.util.text.MessageArguments;

public class HoverEventText extends TextBuilder {

	public enum Action {
		/**
		 * The hover text. Can be multi-line by using the newline character {@code \n}.
		 */
		SHOW_TEXT,
		/**
		 * Requires the hover text to be the item's stringified NBT data.
		 */
		SHOW_ITEM,
		/**
		 * Requires the hover text to be the entity's stringified NBT data.
		 */
		SHOW_ENTITY;
	}

	private final Action action; // Not null
	// Can be unbuilt, gets built if the containing Text gets built:
	private final Text value; // Not null, can be empty

	HoverEventText(Action action, Text value) {
		Validate.notNull(action, "action is null");
		Validate.notNull(value, "value is null");
		this.action = action;
		this.value = value;
	}

	// BUILD

	@Override
	public Text build() {
		super.build();
		// Delegate to hover text:
		buildIfRequired(value);
		return this;
	}

	// HOVER EVENT

	/**
	 * Gets the {@link HoverEventText.Action}.
	 * 
	 * @return the hover event action, not <code>null</code>
	 */
	public Action getAction() {
		return action;
	}

	/**
	 * Gets the hover event value (e.g. the hover text).
	 * 
	 * @return the hover event value, not <code>null</code>
	 */
	public Text getValue() {
		return value;
	}

	// PLACEHOLDER ARGUMENTS

	@Override
	public Text setPlaceholderArguments(MessageArguments arguments) {
		super.setPlaceholderArguments(arguments);
		// Delegate to hover text:
		value.setPlaceholderArguments(arguments);
		return this;
	}

	@Override
	public Text clearPlaceholderArguments() {
		super.clearPlaceholderArguments();
		// Delegate to hover text:
		value.clearPlaceholderArguments();
		return this;
	}

	// PLAIN TEXT

	@Override
	public boolean isPlainText() {
		return false;
	}

	// COPY

	@Override
	public Text copy() {
		HoverEventText copy = new HoverEventText(action, value.copy());
		copy.copy(this, true);
		return copy.build();
	}

	// JAVA OBJECT

	@Override
	protected void appendToStringFeatures(StringBuilder builder) {
		builder.append(", action=");
		builder.append(this.getAction());
		builder.append(", value=");
		builder.append(this.getValue());
		super.appendToStringFeatures(builder);
	}
}
