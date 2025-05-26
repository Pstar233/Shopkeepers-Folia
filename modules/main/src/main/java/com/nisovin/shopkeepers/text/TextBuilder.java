package com.nisovin.shopkeepers.text;

import java.util.function.Supplier;

import org.bukkit.ChatColor;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.util.java.Validate;
import com.nisovin.shopkeepers.util.text.MessageArguments;

/**
 * The base class for builders that allow the fluent construction of {@link Text Texts}.
 * <p>
 * Once {@link #build()} has been called, this Text and all its child and subsequent Texts can no
 * longer be modified.
 * <p>
 * Use one of the factory methods in {@link Text} to create an instance.
 */
public abstract class TextBuilder extends AbstractText {

	private boolean built = false;

	protected TextBuilder() {
	}

	// BUILDER

	/**
	 * Checks whether this {@link Text} has already been built.
	 * 
	 * @return <code>true</code> if built
	 */
	public boolean isBuilt() {
		return built;
	}

	protected void validateModification() {
		Validate.State.isTrue(!built, "This Text has already been built!");
	}

	/**
	 * Prevents further modification of this {@link Text}.
	 * <p>
	 * This also builds any not yet build childs and subsequent Texts.
	 * <p>
	 * This has no effect if this text has already been built.
	 * 
	 * @return this as built Text
	 */
	public Text build() {
		if (this.isBuilt()) return this; // Already built
		built = true;

		// Delegate:
		buildIfRequired(this.getChild());
		buildIfRequired(this.getNext());
		return this;
	}

	/**
	 * Shortcut for getting the root and building the Text from there if it's a {@link TextBuilder}.
	 * 
	 * @return the root Text
	 */
	public Text buildRoot() {
		Text root = this.getRoot();
		buildIfRequired(root);
		return root;
	}

	protected static void buildIfRequired(@Nullable Object text) {
		if (text instanceof TextBuilder) { // Also checks for null
			((TextBuilder) text).build(); // No effect if already built
		}
	}

	protected static boolean isUnbuiltText(@Nullable Object text) {
		return (text instanceof TextBuilder) && !((TextBuilder) text).isBuilt();
	}

	// PLACEHOLDER ARGUMENTS

	@Override
	public Text setPlaceholderArguments(MessageArguments arguments) {
		Validate.State.isTrue(this.isBuilt(), "Cannot set placeholder arguments of unbuilt Text!");
		return super.setPlaceholderArguments(arguments);
	}

	// CHILD

	/**
	 * Sets the child Text.
	 * <p>
	 * The child Text may be an unbuilt {@link TextBuilder} as well. It gets built once this Text
	 * gets built.
	 * 
	 * @param <T>
	 *            the type of the child text
	 * @param child
	 *            the child text, not <code>null</code>
	 * @return the child Text
	 */
	public <T extends Text> T child(T child) {
		this.validateModification();
		this.setChild(child);
		// Returning the child Text here allows for convenient chaining when fluently building a
		// Text.
		return child;
	}

	// NEXT

	/**
	 * Sets the next {@link Text}.
	 * <p>
	 * The next Text may be an unbuilt {@link TextBuilder} as well. It gets built once this Text
	 * gets built.
	 * 
	 * @param <T>
	 *            the type of the next text
	 * @param next
	 *            the next text, not <code>null</code>
	 * @return the next Text
	 */
	public <T extends Text> T next(T next) {
		this.validateModification();
		this.setNext(next);
		// Returning the next Text here allows for convenient chaining when fluently building a
		// Text.
		return next;
	}

	/**
	 * Sets the {@link #child(Text) child} and {@link #next(Text) next} Text.
	 * <p>
	 * This a convenience shortcut when fluently constructing a Text which uses both a child and
	 * next Text.
	 * 
	 * @param child
	 *            the child Text, or <code>null</code> to unset it
	 * @param next
	 *            the next Text, or <code>null</code> to unset it
	 * @return this Text
	 */
	public TextBuilder childAndNext(@Nullable Text child, @Nullable Text next) {
		this.validateModification();
		this.setChild(child);
		this.setNext(next);
		return this;
	}

	// FLUENT CHILD BUILDER

	/**
	 * Creates a new {@link TextBuilder} with the given text and sets it as {@link #child(Text)
	 * child} Text.
	 * 
	 * @param text
	 *            the text, not <code>null</code>
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder childText(String text) {
		return this.child(Text.text(text));
	}

	/**
	 * Creates a new {@link TextBuilder} which uses the String representation of the given object as
	 * its text and sets it as {@link #child(Text) child} Text.
	 * <p>
	 * If the given object is a {@link Supplier}, it gets invoked to obtain the actual object. If
	 * the object is <code>null</code>, the String {@code "null"} is used.
	 * 
	 * @param object
	 *            the object to convert to a Text
	 * @return the new {@link TextBuilder}
	 * @throws IllegalArgumentException
	 *             if the given object is already a Text
	 */
	// TODO A common issue is to accidentally pass another Text here, which is not supported at
	// runtime. Somehow change this to already detect and prevent this during compile time.
	public TextBuilder childText(@Nullable Object object) {
		return this.child(Text.text(object));
	}

	/**
	 * Creates a new {@link TextBuilder} with the newline symbol as text and sets it as
	 * {@link #child(Text) child} Text.
	 * 
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder childNewline() {
		return this.child(Text.newline());
	}

	/**
	 * Creates a new {@link TextBuilder} with the given formatting and sets it as
	 * {@link #child(Text) child} Text.
	 * <p>
	 * The formatting may be a {@link ChatColor#isColor() color}, a {@link ChatColor#isFormat()
	 * format}, or {@link ChatColor#RESET}.
	 * 
	 * @param formatting
	 *            the formatting, not <code>null</code>
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder childFormatting(ChatColor formatting) {
		return this.child(Text.formatting(formatting));
	}

	/**
	 * Creates a new {@link TextBuilder} with the given color and sets it as {@link #child(Text)
	 * child} Text.
	 * <p>
	 * This is simply an alias for {@link #formatting(ChatColor)} and actually accepts any type of
	 * formatting.
	 * 
	 * @param color
	 *            the color, not <code>null</code>
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder childColor(ChatColor color) {
		return this.child(Text.color(color));
	}

	/**
	 * Creates a new {@link TextBuilder} with a formatting reset and sets it as {@link #child(Text)
	 * child} Text.
	 * <p>
	 * This is a shortcut for {@link #formatting(ChatColor)} with {@link ChatColor#RESET}.
	 * 
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder childReset() {
		return this.child(Text.reset());
	}

	/**
	 * Creates a new translatable {@link TextBuilder} and sets it as {@link #child(Text) child}
	 * Text.
	 * 
	 * @param translationKey
	 *            the translation key, not <code>null</code> or empty
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder childTranslatable(String translationKey) {
		return this.child(Text.translatable(translationKey));
	}

	/**
	 * Creates a new placeholder {@link TextBuilder} and sets it as {@link #child(Text) child} Text.
	 * 
	 * @param placeholderKey
	 *            the placeholder key, not <code>null</code> or empty
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder childPlaceholder(String placeholderKey) {
		return this.child(Text.placeholder(placeholderKey));
	}

	/**
	 * Creates a new {@link TextBuilder} with the specified hover event and sets it as
	 * {@link #child(Text) child} Text.
	 * 
	 * @param content
	 *            the hover event content, not <code>null</code>
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder childHoverEvent(HoverEventText.Content content) {
		return this.child(Text.hoverEvent(content));
	}

	/**
	 * Creates a new {@link TextBuilder} with the specified hover text and sets it as
	 * {@link #child(Text) child} Text.
	 * <p>
	 * This is a shortcut for the corresponding {@link #childHoverEvent(HoverEventText.Content)}.
	 * 
	 * @param hoverText
	 *            the hover text, not <code>null</code>
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder childHoverEvent(Text hoverText) {
		return this.child(Text.hoverEvent(hoverText));
	}

	/**
	 * Creates a new {@link TextBuilder} with the specified click event and sets it as
	 * {@link #child(Text) child} Text.
	 * 
	 * @param action
	 *            the click event action, not <code>null</code>
	 * @param value
	 *            the click event value, not <code>null</code>
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder childClickEvent(ClickEventText.Action action, String value) {
		return this.child(Text.clickEvent(action, value));
	}

	/**
	 * Creates a new {@link TextBuilder} with the given insertion text and sets it as
	 * {@link #child(Text) child} Text.
	 * <p>
	 * Uses an empty {@link #childText(String)} instead if the insertion is <code>null</code> or
	 * empty.
	 * 
	 * @param insertion
	 *            the insertion text
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder childInsertion(@Nullable String insertion) {
		return this.child(Text.insertion(insertion));
	}

	// FLUENT NEXT BUILDER

	/**
	 * Creates a new {@link TextBuilder} with the given text and sets it as {@link #next(Text) next}
	 * Text.
	 * 
	 * @param text
	 *            the text, not <code>null</code>
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder text(String text) {
		return this.next(Text.text(text));
	}

	/**
	 * Creates a new {@link TextBuilder} which uses the String representation of the given object as
	 * its text and sets it as {@link #next(Text) next} Text.
	 * <p>
	 * If the given object is a {@link Supplier}, it gets invoked to obtain the actual object. If
	 * the object is <code>null</code>, the String {@code "null"} is used.
	 * 
	 * @param object
	 *            the object to convert to a Text
	 * @return the new {@link TextBuilder}
	 * @throws IllegalArgumentException
	 *             if the given object is already a Text
	 */
	public TextBuilder text(@Nullable Object object) {
		return this.next(Text.text(object));
	}

	/**
	 * Creates a new {@link TextBuilder} with the newline symbol as text and sets it as
	 * {@link #next(Text) next} Text.
	 * 
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder newline() {
		return this.next(Text.newline());
	}

	/**
	 * Creates a new {@link TextBuilder} with the given formatting and sets it as {@link #next(Text)
	 * next} Text.
	 * <p>
	 * The formatting may be a {@link ChatColor#isColor() color}, a {@link ChatColor#isFormat()
	 * format}, or {@link ChatColor#RESET}.
	 * 
	 * @param formatting
	 *            the formatting, not <code>null</code>
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder formatting(ChatColor formatting) {
		return this.next(Text.formatting(formatting));
	}

	/**
	 * Creates a new {@link TextBuilder} with the given color and sets it as {@link #next(Text)
	 * next} Text.
	 * <p>
	 * This is simply an alias for {@link #formatting(ChatColor)} and actually accepts any type of
	 * formatting.
	 * 
	 * @param color
	 *            the color, not <code>null</code>
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder color(ChatColor color) {
		return this.next(Text.color(color));
	}

	/**
	 * Creates a new {@link TextBuilder} with a formatting reset and sets it as {@link #next(Text)
	 * next} Text.
	 * <p>
	 * This is a shortcut for {@link #formatting(ChatColor)} with {@link ChatColor#RESET}.
	 * 
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder reset() {
		return this.next(Text.reset());
	}

	/**
	 * Creates a new translatable {@link TextBuilder} and sets it as {@link #next(Text) next} Text.
	 * 
	 * @param translationKey
	 *            the translation key, not <code>null</code> or empty
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder translatable(String translationKey) {
		return this.next(Text.translatable(translationKey));
	}

	/**
	 * Creates a new placeholder {@link TextBuilder} and sets it as {@link #next(Text) next} Text.
	 * 
	 * @param placeholderKey
	 *            the placeholder key, not <code>null</code> or empty
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder placeholder(String placeholderKey) {
		return this.next(Text.placeholder(placeholderKey));
	}

	/**
	 * Creates a new {@link TextBuilder} with the specified hover event and sets it as
	 * {@link #next(Text) next} Text.
	 * 
	 * @param content
	 *            the hover event content, not <code>null</code>
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder hoverEvent(HoverEventText.Content content) {
		return this.next(Text.hoverEvent(content));
	}

	/**
	 * Creates a new {@link TextBuilder} with the specified hover text and sets it as
	 * {@link #next(Text) next} Text.
	 * <p>
	 * This is a shortcut for the corresponding {@link #hoverEvent(HoverEventText.Content)}.
	 * 
	 * @param hoverText
	 *            the hover text, not <code>null</code>
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder hoverEvent(Text hoverText) {
		return this.next(Text.hoverEvent(hoverText));
	}

	/**
	 * Creates a new {@link TextBuilder} with the specified click event and sets it as
	 * {@link #next(Text) next} Text.
	 * 
	 * @param action
	 *            the click event action, not <code>null</code>
	 * @param value
	 *            the click event value, not <code>null</code>
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder clickEvent(ClickEventText.Action action, String value) {
		return this.next(Text.clickEvent(action, value));
	}

	/**
	 * Creates a new {@link TextBuilder} with the given insertion text and sets it as
	 * {@link #next(Text) next} Text.
	 * <p>
	 * Uses an empty next {@link #text(String)} instead if the insertion is <code>null</code> or
	 * empty.
	 * 
	 * @param insertion
	 *            the insertion text
	 * @return the new {@link TextBuilder}
	 */
	public TextBuilder insertion(@Nullable String insertion) {
		return this.next(Text.insertion(insertion));
	}

	// COPY

	@Override
	public abstract Text copy();

	/**
	 * Copies the properties of the given source Text.
	 * <p>
	 * Copied child and subsequent Texts will be unmodifiable already.
	 * 
	 * @param sourceText
	 *            the source Text
	 * @param copyChilds
	 *            <code>true</code> to also (deeply) copy the child and subsequent Texts,
	 *            <code>false</code> to omit them and keep any currently set child and subsequent
	 *            Texts
	 * @return this
	 */
	public TextBuilder copy(Text sourceText, boolean copyChilds) {
		Validate.notNull(sourceText, "sourceText is null");
		if (copyChilds) {
			this.copyChild(sourceText);
			this.copyNext(sourceText);
		}
		return this;
	}

	// Implemented in separate methods to allow for individual overriding:

	protected void copyChild(Text sourceText) {
		Text sourceChild = sourceText.getChild();
		if (sourceChild != null) {
			this.child(sourceChild.copy());
		}
	}

	protected void copyNext(Text sourceText) {
		Text sourceNext = sourceText.getNext();
		if (sourceNext != null) {
			this.next(sourceNext.copy());
		}
	}

	// JAVA OBJECT

	// Allows for easier extension in subclasses.
	protected void appendToStringFeatures(StringBuilder builder) {
		builder.append(", child=");
		builder.append(this.getChild());
		builder.append(", next=");
		builder.append(this.getNext());
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(this.getClass().getSimpleName());
		builder.append(" [");
		// Built property is always appended first (and without the comma):
		builder.append("built=");
		builder.append(this.isBuilt());
		this.appendToStringFeatures(builder);
		builder.append("]");
		return builder.toString();
	}
}
