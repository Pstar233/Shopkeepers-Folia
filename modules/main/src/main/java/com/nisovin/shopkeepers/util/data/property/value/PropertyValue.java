package com.nisovin.shopkeepers.util.data.property.value;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.util.data.container.DataContainer;
import com.nisovin.shopkeepers.util.data.property.Property;
import com.nisovin.shopkeepers.util.data.serialization.InvalidDataException;
import com.nisovin.shopkeepers.util.data.serialization.MissingDataException;
import com.nisovin.shopkeepers.util.java.Validate;
import com.nisovin.shopkeepers.util.logging.Log;

/**
 * Stores the value for a {@link Property} and provides operations to get, set, save, and load it.
 * <p>
 * After construction, the {@link PropertyValue} needs to be configured and then
 * {@link #build(PropertyValuesHolder) built} before it is ready to be used. Each
 * {@link PropertyValue} is owned by exactly one {@link PropertyValuesHolder}.
 * <p>
 * If the {@link Property} provides no valid {@link Property#getDefaultValue() default value}, an
 * initial value has to be explicitly set or loaded before {@link #getValue()} is safe to be used.
 *
 * @param <T>
 *            the type of the stored value
 */
public class PropertyValue<T> {

	/**
	 * A flag that specifies an additional aspect of how {@link PropertyValue#setValue(Object, Set)}
	 * behaves.
	 * <p>
	 * {@link UpdateFlag#toString()} should return a String that allows the identification of this
	 * update flag.
	 */
	public interface UpdateFlag {
	}

	/**
	 * The default {@link UpdateFlag}s.
	 */
	public enum DefaultUpdateFlag implements UpdateFlag {
		/**
		 * Whether to mark the {@link PropertyValue#getHolder() holder} as
		 * {@link AbstractPropertyValuesHolder#markDirty() dirty} when the value has changed.
		 */
		MARK_DIRTY
	}

	/**
	 * An unmodifiable set of the {@link UpdateFlag}s that are used by {@link #setValue(Object)},
	 * i.e. when no update flags were specified.
	 */
	public static final Set<? extends UpdateFlag> DEFAULT_UPDATE_FLAGS = Collections.singleton(
			DefaultUpdateFlag.MARK_DIRTY
	);

	private final Property<T> property;
	private @Nullable AbstractPropertyValuesHolder holder; // Not null once built
	private @Nullable ValueChangeListener<T> valueChangeListener = null;

	private @Nullable T value;
	// Whether the value has not yet been initialized to a valid value.
	private boolean requireInitialValue = false;

	/**
	 * Creates a new {@link PropertyValue} for the given {@link Property}.
	 * 
	 * @param property
	 *            the associated property, not <code>null</code>
	 */
	public PropertyValue(Property<T> property) {
		Validate.notNull(property, "property is null");
		this.property = property;
	}

	/**
	 * Checks if this {@link PropertyValue} has already been {@link #build(PropertyValuesHolder)
	 * built} and added to a {@link #getHolder() holder}.
	 * 
	 * @return <code>true</code> if this {@link PropertyValue} has already been built
	 */
	private final boolean isBuilt() {
		return (holder != null);
	}

	/**
	 * Verifies that this {@link PropertyValue} has already been {@link #build(PropertyValuesHolder)
	 * built}.
	 */
	protected final void validateBuilt() {
		Validate.State.isTrue(this.isBuilt(), "PropertyValue has not yet been built!");
	}

	/**
	 * Verifies that this {@link PropertyValue} has not yet been {@link #build(PropertyValuesHolder)
	 * built}.
	 */
	protected final void validateNotBuilt() {
		Validate.State.isTrue(!this.isBuilt(), "PropertyValue has already been built!");
	}

	/**
	 * Builds this {@link PropertyValue} and adds it to the given {@link PropertyValuesHolder}.
	 * <p>
	 * This performs the remaining setup and validation of this {@link PropertyValue}, and can only
	 * occur once. Once built, certain aspects of this {@link PropertyValue} can no longer be
	 * further configured.
	 * 
	 * @param <P>
	 *            the type of this {@link PropertyValue}
	 * @param holder
	 *            the holder, not <code>null</code>
	 * @return this {@link PropertyValue}
	 */
	@SuppressWarnings("unchecked")
	public final <P extends PropertyValue<T>> P build(PropertyValuesHolder holder) {
		this.validateNotBuilt();
		Validate.notNull(holder, "holder is null");
		Validate.isTrue(holder instanceof AbstractPropertyValuesHolder,
				"holder is not of type AbstractPropertyValuesHolder");

		// Post construction validation and setup:
		if (property.hasDefaultValue()) {
			value = property.getDefaultValue();
		} else {
			// The property has no valid default value. A valid initial value has to be set (or
			// loaded), before the value of this PropertyValue can be accessed. Otherwise, this
			// PropertyValue will throw an exception when its uninitialized value is attempted to be
			// accessed.
			requireInitialValue = true;
		}
		this.postConstruct();

		this.holder = (AbstractPropertyValuesHolder) holder;
		this.holder.add(this);
		return (P) this;
	}

	/**
	 * This is called when the {@link PropertyValue} is {@link #build(PropertyValuesHolder) built}
	 * and can be overridden by subclasses to perform any remaining setup and validation.
	 */
	protected void postConstruct() {
	}

	/**
	 * Gets the {@link Property} that is associated with this {@link PropertyValue}.
	 * 
	 * @return the property, not <code>null</code>
	 */
	public final Property<T> getProperty() {
		return property;
	}

	/**
	 * Gets the {@link PropertyValuesHolder} that owns this {@link PropertyValue}.
	 * 
	 * @return the holder, not <code>null</code>
	 */
	public final PropertyValuesHolder getHolder() {
		this.validateBuilt();
		return Unsafe.assertNonNull(holder);
	}

	/**
	 * Gets the value.
	 * 
	 * @return the value
	 * @throws IllegalStateException
	 *             if the property provides no valid {@link Property#getDefaultValue() default
	 *             value} and no value has been set or loaded yet
	 */
	public final T getValue() {
		this.validateBuilt();
		if (requireInitialValue) {
			assert holder != null;
			throw new IllegalStateException(holder.getLogPrefix() + "Value for property '"
					+ property.getName() + "' has not yet been initialized!");
		}
		return Unsafe.cast(value);
	}

	/**
	 * Sets the value.
	 * <p>
	 * This calls {@link #setValue(Object, Set)} using {@link #DEFAULT_UPDATE_FLAGS} for the update
	 * flags.
	 * 
	 * @param value
	 *            the new value
	 */
	public final void setValue(T value) {
		this.setValue(value, DEFAULT_UPDATE_FLAGS);
	}

	/**
	 * Sets the value.
	 * <p>
	 * The given {@link UpdateFlag}s specify additional aspects of how this operation behaves.
	 * 
	 * @param value
	 *            the new value
	 * @param updateFlags
	 *            the update flags
	 */
	public final void setValue(T value, Set<? extends UpdateFlag> updateFlags) {
		this.validateBuilt();
		Validate.notNull(updateFlags, "updateFlags is null");

		// Validation:
		property.validateValue(value);

		// Update the value:
		// Not using #getValue() here, to bypass the requireInitialValue check.
		T oldValue = Unsafe.cast(this.value);
		if (Objects.equals(oldValue, value)) return; // Value has not changed
		this.value = value;
		this.requireInitialValue = false;

		// Post-value-change actions:
		if (updateFlags.contains(DefaultUpdateFlag.MARK_DIRTY)) {
			assert holder != null;
			holder.markDirty();
		}
		this.onValueChanged(oldValue, value, updateFlags);
		if (valueChangeListener != null) {
			valueChangeListener.onValueChanged(this, oldValue, value, updateFlags);
		}
	}

	/**
	 * This method is invoked whenever the value of this {@link PropertyValue} has changed.
	 * <p>
	 * This method might not be invoked for calls to {@link #setValue(Object, Set)} if the value did
	 * not actually change.
	 * 
	 * @param oldValue
	 *            the old value, can be <code>null</code> for the first initialization of the value
	 *            of this {@link PropertyValue} even if the property is not
	 *            {@link Property#isNullable nullable}
	 * @param newValue
	 *            the new value, valid according to {@link Property#validateValue(Object)}
	 * @param updateFlags
	 *            the update flags, not <code>null</code>, not meant to be modified
	 */
	protected void onValueChanged(T oldValue, T newValue, Set<? extends UpdateFlag> updateFlags) {
		// Can be overridden to react to value changes.
	}

	/**
	 * Registers the given {@link ValueChangeListener} to react to value changes.
	 * <p>
	 * This method can only be called once (it is not possible to register multiple
	 * {@link ValueChangeListener}s), and only while the {@link PropertyValue} has not yet been
	 * {@link #build(PropertyValuesHolder) built}.
	 * <p>
	 * The given listener might not be invoked for calls to {@link #setValue(Object, Set)} if the
	 * value did not actually change.
	 * 
	 * @param <P>
	 *            the type of this {@link PropertyValue}
	 * @param listener
	 *            the value change listener, not <code>null</code>
	 * @return this {@link PropertyValue}
	 */
	@SuppressWarnings("unchecked")
	public final <P extends PropertyValue<T>> P onValueChanged(ValueChangeListener<T> listener) {
		this.validateNotBuilt();
		Validate.State.isTrue(this.valueChangeListener == null,
				"Another listener has already been set!");
		Validate.notNull(listener, "listener is null");
		this.valueChangeListener = listener;
		return (P) this;
	}

	/**
	 * Registers a {@link ValueChangeListener} that reacts to value changes by calling the given
	 * {@link Runnable}.
	 * <p>
	 * This method is a shortcut for creating and {@link #onValueChanged(ValueChangeListener)
	 * registering} a {@link ValueChangeListener} that calls the given {@link Runnable}.
	 * 
	 * @param <P>
	 *            the type of this {@link PropertyValue}
	 * @param runnable
	 *            the runnable, not <code>null</code>
	 * @return this {@link PropertyValue}
	 * @see #onValueChanged(ValueChangeListener)
	 */
	public final <P extends PropertyValue<T>> P onValueChanged(Runnable runnable) {
		Validate.notNull(runnable, "runnable is null");
		return this.onValueChanged((property, oldValue, newValue, updateFlags) -> runnable.run());
	}

	/**
	 * Loads the value of this {@link PropertyValue} from the given {@link DataContainer}.
	 * <p>
	 * This uses {@link Property#load(DataContainer)} to load the value.
	 * <p>
	 * If the data for the value is missing, and the property provides no automatic fallback value,
	 * but has a {@link Property#getDefaultValue() default value}, a warning is logged, the
	 * property's default value is used, and the {@link #getHolder() holder} is marked as
	 * {@link AbstractPropertyValuesHolder#markDirty() dirty}.
	 * 
	 * @param dataContainer
	 *            the data container, not <code>null</code>
	 * @throws InvalidDataException
	 *             if the value cannot be loaded
	 */
	public final void load(DataContainer dataContainer) throws InvalidDataException {
		this.validateBuilt();
		// TODO Allow callers to pass UpdateFlags to this method?
		// Does not mark the holder as dirty:
		Set<? extends UpdateFlag> updateFlags = Collections.emptySet();
		T value;
		try {
			value = property.load(dataContainer); // Can load null
		} catch (MissingDataException e) {
			if (!property.hasDefaultValue()) {
				throw e;
			}

			value = property.getDefaultValue();
			updateFlags = DEFAULT_UPDATE_FLAGS; // Marks the holder as dirty
			assert holder != null;
			Log.warning(holder.getLogPrefix() + "Missing data for property '" + property.getName()
					+ "'. Using the default value now: '" + property.toString(value) + "'");
		}
		this.setValue(value, updateFlags);
	}

	/**
	 * Saves the value of this {@link PropertyValue} into the given {@link DataContainer}.
	 * <p>
	 * This uses {@link Property#save(DataContainer, Object)} to save the value.
	 * 
	 * @param dataContainer
	 *            the data container, not <code>null</code>
	 */
	public final void save(DataContainer dataContainer) {
		this.validateBuilt();
		property.save(dataContainer, value);
	}
}
