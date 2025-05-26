package com.nisovin.shopkeepers.text;

import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.util.UnmodifiableItemStack;
import com.nisovin.shopkeepers.util.java.Validate;
import com.nisovin.shopkeepers.util.text.MessageArguments;

public class HoverEventText extends TextBuilder {
	/**
	 * Hover event content.
	 */
	public interface Content {
		/**
		 * Creates a copy of this content, if necessary, i.e. if the content is potentially holding
		 * mutable state.
		 * 
		 * @return a copy of this content, or this content itself if the content is immutable
		 */
		public Content copy();
	}

	/**
	 * Shows text when hovered.
	 */
	public static class TextContent implements Content {

		private final Text text; // Not null, can be empty, built in the constructor if necessary

		/**
		 * Creates a new {@link TextContent}.
		 * <p>
		 * The given text can be multi-line by using the newline character {@code \n}.
		 * <p>
		 * The given text is automatically built if not yet built. Parameters supplied to the
		 * {@link HoverEventText} are forwarded to the given text.
		 * 
		 * @param text
		 *            the hover text
		 */
		public TextContent(Text text) {
			Validate.notNull(text, "text is null");
			buildIfRequired(text);
			this.text = text;
		}

		/**
		 * Gets the hover text.
		 * 
		 * @return the text
		 */
		public Text getText() {
			return text;
		}

		@Override
		public Content copy() {
			// Copy, since the text may store instance specific placeholders:
			return new TextContent(text.copy());
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("TextContent [text=");
			builder.append(text);
			builder.append("]");
			return builder.toString();
		}
	}

	/**
	 * Shows item information when hovered.
	 */
	public static class ItemContent implements Content {

		private final UnmodifiableItemStack item;

		/**
		 * Creates a new {@link ItemContent}.
		 * 
		 * @param item
		 *            the item stack, assumed to be immutable
		 */
		public ItemContent(UnmodifiableItemStack item) {
			Validate.notNull(item, "item is null");
			this.item = item;
		}

		/**
		 * The item.
		 * 
		 * @return the item
		 */
		public UnmodifiableItemStack getItem() {
			return item;
		}

		@Override
		public Content copy() {
			return this; // Assumed immutable
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("ItemContent [item=");
			builder.append(item.getType());
			builder.append("]");
			return builder.toString();
		}
	}

	/**
	 * Shows entity information when hovered.
	 */
	public static class EntityContent implements Content {

		private final NamespacedKey type;
		private final UUID uuid;
		private final @Nullable Text name;

		/**
		 * Creates a new {@link EntityContent}.
		 * 
		 * @param type
		 *            the entity type key
		 * @param uuid
		 *            the entity unique id
		 * @param name
		 *            the entity name, or <code>null</code>
		 */
		public EntityContent(NamespacedKey type, UUID uuid, @Nullable Text name) {
			Validate.notNull(type, "type is null");
			Validate.notNull(uuid, "uuid is null");
			buildIfRequired(name);
			this.type = type;
			this.uuid = uuid;
			this.name = name;
		}

		/**
		 * The entity type key.
		 * 
		 * @return the type
		 */
		public NamespacedKey getType() {
			return type;
		}

		/**
		 * The entity unique id.
		 * 
		 * @return the uuid
		 */
		public UUID getUuid() {
			return uuid;
		}

		/**
		 * The optional entity name.
		 * 
		 * @return the name, or <code>null</code>
		 */
		public @Nullable Text getName() {
			return name;
		}

		@Override
		public Content copy() {
			return this; // Assumed immutable. The name does not support placeholders.
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("EntityContent [type=");
			builder.append(type);
			builder.append(", uuid=");
			builder.append(uuid);
			builder.append(", name=");
			builder.append(name);
			builder.append("]");
			return builder.toString();
		}
	}

	private final Content content; // Not null

	HoverEventText(Content content) {
		Validate.notNull(content, "content is null");
		this.content = content;
	}

	// BUILD

	@Override
	public Text build() {
		super.build();
		return this;
	}

	// HOVER EVENT

	/**
	 * Gets the hover event content.
	 * 
	 * @return the hover event content, not <code>null</code>
	 */
	public Content getContent() {
		return content;
	}

	// PLACEHOLDER ARGUMENTS

	@Override
	public Text setPlaceholderArguments(MessageArguments arguments) {
		super.setPlaceholderArguments(arguments);
		// Delegate to hover text:
		if (content instanceof TextContent textContent) {
			textContent.getText().setPlaceholderArguments(arguments);
		}
		return this;
	}

	@Override
	public Text clearPlaceholderArguments() {
		super.clearPlaceholderArguments();
		// Delegate to hover text:
		if (content instanceof TextContent textContent) {
			textContent.getText().clearPlaceholderArguments();
		}
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
		HoverEventText copy = new HoverEventText(content.copy());
		copy.copy(this, true);
		return copy.build();
	}

	// JAVA OBJECT

	@Override
	protected void appendToStringFeatures(StringBuilder builder) {
		builder.append(", content=");
		builder.append(this.getContent());
		super.appendToStringFeatures(builder);
	}
}
