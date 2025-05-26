package com.nisovin.shopkeepers.text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.checkerframework.checker.nullness.qual.NonNull;

import com.nisovin.shopkeepers.util.java.Validate;

/**
 * A {@link Text} that gets translated on the client.
 * <p>
 * In case translatable texts are not supported or when converting to {@link #toPlainText() plain}
 * or {@link #toFormat() plain format} text, its {@link #getChild() child} is used as fallback.
 * Otherwise, the child is ignored.
 */
public class TranslatableText extends TextBuilder {

	private final String translationKey; // Not null or empty
	// Only contains already built Texts.
	// Not null, can be empty, unmodifiable view.
	private List<? extends Text> translationArguments = Collections.emptyList();

	TranslatableText(String translationKey) {
		Validate.notEmpty(translationKey, "translationKey is null or empty");
		this.translationKey = translationKey;
	}

	// TRANSLATABLE

	/**
	 * Gets the translation key.
	 * 
	 * @return the translationKey, not <code>null</code>
	 */
	public String getTranslationKey() {
		return translationKey;
	}

	/**
	 * Gets the translation arguments.
	 * 
	 * @return an unmodifiable view on the translation arguments, not <code>null</code>, can be
	 *         empty
	 */
	public List<? extends Text> getTranslationArguments() {
		return translationArguments;
	}

	/**
	 * Sets the translation arguments.
	 * <p>
	 * Any non-{@link Text} argument gets first converted to a corresponding Text by using its
	 * {@link Object#toString() String representation}. If the argument is a {@link Supplier} it
	 * will be invoked to obtain the actual argument.
	 * <p>
	 * Any {@link Text} translation arguments that are not yet {@link TextBuilder#isBuilt() built}
	 * may get built by this method.
	 * 
	 * @param translationArguments
	 *            the translation arguments, or empty to unset, not <code>null</code>
	 * @return this Text
	 */
	public TranslatableText setTranslationArguments(List<@NonNull ?> translationArguments) {
		Validate.notNull(translationArguments, "translationArguments is null");
		if (translationArguments.isEmpty()) {
			this.translationArguments = Collections.emptyList(); // Resetting is always allowed
		} else {
			List<Text> translationTextArguments = new ArrayList<>(translationArguments.size());
			translationArguments.forEach(argument -> {
				Validate.notNull(argument, "translationArguments contains null");
				Validate.isTrue(argument != this, "translationArguments contains this Text itself");

				Text argumentText = Text.of(argument);
				Validate.isTrue(argumentText.getParent() == null,
						"translationArguments contains a non-root Text");
				translationTextArguments.add(argumentText);
			});

			// Build all unbuilt arguments:
			translationTextArguments.forEach(TranslatableText::buildIfRequired);

			this.translationArguments = Collections.unmodifiableList(translationTextArguments);
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
		TranslatableText copy = new TranslatableText(translationKey);
		copy.copy(this, true);
		return copy.build();
	}

	/**
	 * This will not copy the translation key. Any currently set translation arguments get (deeply)
	 * copied.
	 */
	@Override
	public TranslatableText copy(Text sourceText, boolean copyChilds) {
		super.copy(sourceText, copyChilds);
		this.copyTranslationArguments(sourceText);
		return this;
	}

	protected void copyTranslationArguments(Text sourceText) {
		if (sourceText instanceof TranslatableText) {
			TranslatableText translatableSourceText = (TranslatableText) sourceText;
			this.setTranslationArguments(copyAll(translatableSourceText.getTranslationArguments()));
		}
	}

	private static List<? extends Text> copyAll(Collection<? extends Text> toCopy) {
		assert toCopy != null;
		if (toCopy.isEmpty()) {
			return Collections.emptyList();
		}

		List<Text> copies = new ArrayList<>(toCopy.size());
		toCopy.forEach(text -> {
			copies.add(text.copy());
		});
		return copies;
	}

	// JAVA OBJECT

	@Override
	protected void appendToStringFeatures(StringBuilder builder) {
		builder.append(", translationKey=");
		builder.append(translationKey);
		builder.append(", translationArguments=");
		builder.append(translationArguments);
		super.appendToStringFeatures(builder);
	}
}
