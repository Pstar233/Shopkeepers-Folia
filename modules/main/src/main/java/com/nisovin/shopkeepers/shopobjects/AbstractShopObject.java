package com.nisovin.shopkeepers.shopobjects;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.SKShopkeepersPlugin;
import com.nisovin.shopkeepers.api.events.ShopkeeperAddedEvent;
import com.nisovin.shopkeepers.api.events.UpdateItemEvent;
import com.nisovin.shopkeepers.api.shopkeeper.ShopCreationData;
import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import com.nisovin.shopkeepers.api.shopkeeper.ShopkeeperRegistry;
import com.nisovin.shopkeepers.api.shopkeeper.player.PlayerShopkeeper;
import com.nisovin.shopkeepers.api.shopobjects.ShopObject;
import com.nisovin.shopkeepers.api.shopobjects.ShopObjectType;
import com.nisovin.shopkeepers.api.shopobjects.living.LivingShopObject;
import com.nisovin.shopkeepers.api.storage.ShopkeeperStorage;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.shopkeeper.ShopkeeperData;
import com.nisovin.shopkeepers.shopkeeper.ShopkeeperPropertyValuesHolder;
import com.nisovin.shopkeepers.shopkeeper.registry.ShopObjectRegistry;
import com.nisovin.shopkeepers.shopkeeper.spawning.ShopkeeperSpawnState;
import com.nisovin.shopkeepers.ui.editor.Button;
import com.nisovin.shopkeepers.ui.editor.EditorHandler;
import com.nisovin.shopkeepers.util.annotations.ReadWrite;
import com.nisovin.shopkeepers.util.bukkit.BlockFaceUtils;
import com.nisovin.shopkeepers.util.bukkit.LocationUtils;
import com.nisovin.shopkeepers.util.data.property.BasicProperty;
import com.nisovin.shopkeepers.util.data.property.Property;
import com.nisovin.shopkeepers.util.data.property.validation.java.StringValidators;
import com.nisovin.shopkeepers.util.data.property.value.PropertyValuesHolder;
import com.nisovin.shopkeepers.util.data.serialization.DataSerializer;
import com.nisovin.shopkeepers.util.data.serialization.InvalidDataException;
import com.nisovin.shopkeepers.util.data.serialization.java.StringSerializers;
import com.nisovin.shopkeepers.util.java.Validate;
import org.jetbrains.annotations.NotNull;

/**
 * 所有 shop 对象实现的抽象基类。
 * <p>
 * 确保在每次可能需要的数据更改时调用 {@link AbstractShopkeeper#markDirty（）}
 * 进行持久化。
 * <p>
 * shop 对象在生成时需要 {@link #onIdChanged（） register} 自身，
 * despawned，或者其对象 ID 可能已更改。如果此类型的 shop 对象管理其
 * {@link AbstractShopObjectType#mustBeSpawned（） spawning} 自身，它可能需要注册自身
 * 早于由 Shopkeepers 插件管理的 shop 对象：例如，如果
 * 商店对象已在数据块加载期间或之后不久生成，因此需要注册
 * 此时，而不是当店主的区块被激活时。如果商店
 * 对象能够在创建或加载店主之前生成，则可能需要注册
 * @link #onShopkeeperAdded本身。
 * <p>
 * 如果这种类型的商店对象能够移动、传送或生成到不同的区块中，则
 * {@link ShopkeeperRegistry} 需要通过以下方式了解这些位置更改
 * {@link AbstractShopkeeper#setLocation（Location） 更新对应
 *店主。如果店主当前没有 tick （即如果其上一个区块未激活）
 * 目前），这些位置更新需要快速进行，以便 shopkeeper 的
 * 新位置，如果当前已加载，则可以激活并尽快开始店主的滴答作响
 * 尽可能。
 */
public abstract class AbstractShopObject implements ShopObject {

	private static final String DATA_KEY_SHOP_OBJECT_TYPE = "type";

	/**
	 * Shop object type id.
	 */
	public static final Property<String> SHOP_OBJECT_TYPE_ID = new BasicProperty<String>()
			.name("type-id")
			.dataKeyAccessor(DATA_KEY_SHOP_OBJECT_TYPE, StringSerializers.STRICT)
			.validator(StringValidators.NON_EMPTY)
			.build();

	/**
	 * Shop object type, derived from the serialized {@link #SHOP_OBJECT_TYPE_ID shop object type
	 * id}.
	 */
	public static final Property<AbstractShopObjectType<?>> SHOP_OBJECT_TYPE = new BasicProperty<AbstractShopObjectType<?>>()
			.dataKeyAccessor(DATA_KEY_SHOP_OBJECT_TYPE, new DataSerializer<AbstractShopObjectType<?>>() {
				@Override
				public @Nullable Object serialize(AbstractShopObjectType<?> value) {
					Validate.notNull(value, "value is null");
					return value.getIdentifier();
				}

				@Override
				public AbstractShopObjectType<?> deserialize(
						Object data
				) throws InvalidDataException {
					String shopObjectTypeId = StringSerializers.STRICT_NON_EMPTY.deserialize(data);
					SKShopObjectTypesRegistry shopObjectTypeRegistry = SKShopkeepersPlugin.getInstance().getShopObjectTypeRegistry();
					AbstractShopObjectType<?> shopObjectType = shopObjectTypeRegistry.get(shopObjectTypeId);
					if (shopObjectType == null) {
						throw new InvalidDataException("Unknown shop object type: "
								+ shopObjectTypeId);
					}
					return shopObjectType;
				}
			})
			.build();

	protected final AbstractShopkeeper shopkeeper; // Not null
	protected final PropertyValuesHolder properties; // Not null

	private @Nullable Object lastId = null;
	private boolean tickActivity = false;

	// Fresh creation
	protected AbstractShopObject(
			AbstractShopkeeper shopkeeper,
			@Nullable ShopCreationData creationData
	) {
		assert shopkeeper != null;
		this.shopkeeper = shopkeeper;
		this.properties = new ShopkeeperPropertyValuesHolder(shopkeeper);
	}

	@Override
	public abstract AbstractShopObjectType<?> getType();

	/**
	 * Gets the shopkeeper associated with this shop object.
	 * 
	 * @return the shopkeeper, not <code>null</code>
	 */
	public final AbstractShopkeeper getShopkeeper() {
		return shopkeeper;
	}

	/**
	 * Loads the shop object's data from the given {@link ShopObjectData}.
	 * <p>
	 * The data is expected to already have been {@link ShopkeeperData#migrate(String) migrated}.
	 * <p>
	 * The given shop object data is expected to contain the shop object's type identifier. Loading
	 * fails if the given data was originally meant for a different shop object type.
	 * <p>
	 * Any stored data elements (such as for example item stacks, etc.) and collections of data
	 * elements are assumed to not be modified, neither by the shop object, nor in contexts outside
	 * the shop object. If the shop object can guarantee not to modify these data elements, it is
	 * allowed to directly store them without copying them first.
	 * 
	 * @param shopObjectData
	 *            the shop object data, not <code>null</code>
	 * @throws InvalidDataException
	 *             if the data cannot be loaded
	 */
	public void load(ShopObjectData shopObjectData) throws InvalidDataException {
		Validate.notNull(shopObjectData, "shopObjectData is null");
		ShopObjectType<?> shopObjectType = shopObjectData.get(SHOP_OBJECT_TYPE);
		assert shopObjectType != null;
		if (shopObjectType != this.getType()) {
			throw new InvalidDataException(
					"Shop object data is for a different shop object type (expected: "
							+ this.getType().getIdentifier() + ", got: "
							+ shopObjectType.getIdentifier() + ")!"
			);
		}
	}

	/**
	 * Saves the shop object's data to the given {@link ShopObjectData}.
	 * <p>
	 * It is assumed that the data stored in the given {@link ShopObjectData} does not change
	 * afterwards and can be serialized asynchronously. The shop object must therefore ensure that
	 * this data is not modified, for example by only inserting immutable data, or always making
	 * copies of the inserted data.
	 * <p>
	 * Some types of shop objects may rely on externally stored data and only save a reference to
	 * that external data as part of their shop object data. However, in some situations, such as
	 * when creating a {@link Shopkeeper#createSnapshot(String) shopkeeper snapshot}, it may be
	 * necessary to also save that external data as part of the shop object data in order to later
	 * be able to restore it. The {@code saveAll} parameter indicates whether the shop object should
	 * try to also save any external data.
	 * 
	 * @param shopObjectData
	 *            the shop object data, not <code>null</code>
	 * @param saveAll
	 *            <code>true</code> to also save any data that would usually be stored externally
	 */
	public void save(ShopObjectData shopObjectData, boolean saveAll) {
		Validate.notNull(shopObjectData, "shopObjectData is null");
		shopObjectData.set("type", this.getType().getIdentifier());
	}

	/**
	 * This is called at the end of shopkeeper construction, when the shopkeeper has been fully
	 * loaded and setup, and can be used to perform any remaining initial shop object setup.
	 * <p>
	 * The shopkeeper has not yet been registered at this point! If the registration fails, or if
	 * the shopkeeper is created for some other purpose, the {@link #remove()} and {@link #delete()}
	 * methods may never get called for this shop object. For any setup that relies on cleanup
	 * during {@link #remove()} or {@link #delete()},
	 * {@link #onShopkeeperAdded(ShopkeeperAddedEvent.Cause)} may be better suited.
	 */
	public void setup() {
	}

	// ITEM UPDATES

	/**
	 * Calls an {@link UpdateItemEvent} and updates each item stored by this shop object in the
	 * given {@link ShopObjectData}, such as {@link LivingShopObject#getEquipment() equipment}
	 * items, etc. This modifies the given {@link ShopObjectData}.
	 * <p>
	 * This is usually called automatically as part of {@link Shopkeeper#updateItems()}.
	 * <p>
	 * Data that fails to load is logged but otherwise ignored.
	 * 
	 * @param logPrefix
	 *            a prefix for log messages, not <code>null</code>
	 * @param shopObjectData
	 *            the {@link ShopObjectData} to update, not <code>null</code>
	 * @return the number of updated items
	 */
	public int updateItems(String logPrefix, @ReadWrite ShopObjectData shopObjectData) {
		return 0;
	}
	// LIFE CYCLE

	/**
	 * This is called when the shopkeeper is added to the {@link ShopkeeperRegistry}.
	 * <p>
	 * Usually, the shopkeeper has not yet been spawned or activated at this point. However, if this
	 * this type of shop object handles it spawning {@link AbstractShopObjectType#mustBeSpawned()
	 * itself}, and the shop object is currently already {@link #isSpawned() spawned}, this may
	 * {@link #onIdChanged() register} the spawned shop object.
	 * 
	 * @param cause
	 *            the cause of the addition, not <code>null</code>
	 */
	public void onShopkeeperAdded(ShopkeeperAddedEvent.Cause cause) {
	}

	/**
	 * This is called when the {@link ShopObject} is removed, usually when the corresponding
	 * shopkeeper is removed from the {@link ShopkeeperRegistry}. The shopkeeper has already been
	 * marked as {@link Shopkeeper#isValid() invalid} at this point.
	 * <p>
	 * This can for example be used to disable any active components (e.g. listeners) for this shop
	 * object.
	 * <p>
	 * If this type of shop object handles its spawning
	 * {@link AbstractShopObjectType#mustBeSpawned() itself}, and the shop object is currently
	 * {@link #isSpawned() spawned}, the shop object needs to mark itself as despawned and
	 * {@link #onIdChanged() unregister} itself when this is called.
	 */
	public void remove() {
	}

	/**
	 * This is called when the {@link ShopObject} is permanently deleted.
	 * <p>
	 * This is called after {@link #remove()}.
	 * <p>
	 * This can for example be used to clean up any persistent data corresponding to this shop
	 * object.
	 */
	public void delete() {
	}

	// ATTACHED BLOCK FACE

	/**
	 * Sets the {@link BlockFace} against which the shop object is attached.
	 * <p>
	 * The block face is relative to the block the shopkeeper is attached to. E.g. a shulker
	 * attached to the south of a block has an "AttachFace" of "north".
	 * <p>
	 * Not all types of shop objects might use or store the attached block face.
	 * 
	 * @param attachedBlockFace
	 *            the block side block face, not <code>null</code>
	 */
	public void setAttachedBlockFace(BlockFace attachedBlockFace) {
		Validate.notNull(attachedBlockFace, "attachedBlockFace is null");
		Validate.isTrue(BlockFaceUtils.isBlockSide(attachedBlockFace),
				"attachedBlockFace is not a block side");
	}

	// SPAWNING

	/**
	 * Gets the object id by which the shopkeeper is currently registered inside the
	 * {@link ShopObjectRegistry}.
	 * <p>
	 * This method is meant to only be used internally by the Shopkeepers plugin itself!
	 * 
	 * @return the object id, or <code>null</code>
	 */
	public final @Nullable Object getLastId() {
		return lastId;
	}

	/**
	 * Sets the object id by which the shopkeeper is currently registered inside the
	 * {@link ShopObjectRegistry}.
	 * <p>
	 * This method is meant to only be used internally by the Shopkeepers plugin itself!
	 * 
	 * @param lastId
	 *            the object id, can be <code>null</code>
	 */
	public final void setLastId(@Nullable Object lastId) {
		this.lastId = lastId;
	}

	/**
	 * 获取唯一标识此 {@link ShopObject} 的对象，而该对象是
	 * {@link #isSpawned（） 生成}。
	 * <p>
	 * 该 ID 在所有当前生成的商店对象中必须是唯一的，包括其他类型的
	 * 商店物品。它必须适合用作 {@link Object#hashCode（） hash-based} 中的键
	 * 数据结构，并且在生成 Shop 对象时不会更改。ID 可能会随时更改
	 * 重新生成 Shop 对象。
	 *
	 * @return shop 对象的 id，如果当前未生成，则<code>为 null</code>
	 */
	public abstract @Nullable Object getId();

	/**
	 * 只要此 shop 对象的 {@link #getId（） id} 可能具有
	 * 已更改，例如当此商店对象已生成 {@link #spawn（） 时}、{@link #despawn（）
	 * despawned} 来命名，或者由于其他原因更改了其 ID。
	 * <p>
	 * 这将更新店主当前在
	 * {@link ShopkeeperRegistry}：如果 shop 对象是新生成的，这将进行注册
	 * 当前商店对象 ID。如果 shop 对象已消失，这将取消注册任何
	 * 以前注册的对象 ID。如果商店对象 ID 已更改，则两者都将取消注册
	 * 任何以前的对象 ID，然后注册当前对象 ID。
	 */
	protected final void onIdChanged() {
		ShopObjectRegistry shopObjectRegistry = SKShopkeepersPlugin.getInstance()
				.getShopkeeperRegistry().getShopObjectRegistry();
		shopObjectRegistry.updateShopObjectRegistration(shopkeeper);
	}

	/**
	 * Checks if the spawning of this shop object is scheduled, for example by some external
	 * component.
	 * <p>
	 * The shop object should usually avoid spawning itself while its spawning is still scheduled.
	 * 
	 * @return <code>true</code> if a spawn of this shop object is scheduled
	 */
	protected final boolean isSpawningScheduled() {
		return shopkeeper.getComponents()
				.getOrAdd(ShopkeeperSpawnState.class)
				.isSpawningScheduled();
	}

	@Override
	public abstract boolean isActive();

	/**
	 * Spawns the shop object into the world at its spawn location.
	 * <p>
	 * This may have no effect if the shop object has already been spawned. To respawn this shop
	 * object if it is currently already spawned, one can use {@link #respawn()}.
	 * <p>
	 * This needs to call {@link #onIdChanged()} if the shop object was successfully spawned.
	 * 
	 * @return <code>false</code> if the spawning failed, or <code>true</code> if the shop object
	 *         either is already spawned or has successfully been spawned
	 */
	public abstract boolean spawn();

	/**
	 * Removes this shop object from the world.
	 * <p>
	 * This has no effect if the shop object is not spawned currently.
	 * <p>
	 * This needs to call {@link #onIdChanged()} if the shop object was successfully despawned.
	 */
	public abstract void despawn();

	/**
	 * Respawns this shop object.
	 * <p>
	 * This is the same as calling both {@link #despawn()} and then {@link #spawn()}. However, this
	 * has no effect if the shop object is not {@link #isSpawned() spawned} currently.
	 * 
	 * @return <code>true</code> if the shop object was successfully respawned
	 */
	public final boolean respawn() {
		if (!this.isSpawned()) return false;
		this.despawn();
		return this.spawn();
	}

	@Override
	public abstract @Nullable Location getLocation();

	/**
     * Teleports this shop object to its intended spawn location.
     * <p>
     * This can be used to move the shop object after the {@link Shopkeeper#getLocation() location}
     * of its associated shopkeeper has changed. This behaves similar to {@link #respawn()}, but may
     * be implemented more efficiently since it does not necessarily require the shop object to be
     * respawned.
     * <p>
     * This method has no effect if the world of the shopkeeper's location is not loaded currently.
     * <p>
     * If this type of shop object handles its {@link AbstractShopObjectType#mustBeSpawned()
     * spawning} itself, this may need to spawn the shop object (e.g. if its new location is in a
     * loaded chunk). Otherwise, the shop object can ignore the call to this method if it is not
     * {@link #isSpawned() spawned} currently.
     * <p>
     * Note: There is intentionally no method to teleport a shop object to a specific location,
     * because the location of a shop object is meant to always match the location of its associated
     * shopkeeper. If a shop object is able to change its location independently of the location of
     * its shopkeeper, it is required to manually
     * {@link AbstractShopkeeper#setLocation(Location, BlockFace) update} the location of the
     * shopkeeper in order to keep it synchronized.
     *
     * @return <code>true</code> if the shop object was successfully moved
     */
	public abstract @NotNull CompletableFuture<Boolean> move();

	// TICKING

	/**
	 * This is called when the shopkeeper starts ticking.
	 */
	public void onStartTicking() {
	}

	/**
	 * This is called when the shopkeeper stops ticking.
	 */
	public void onStopTicking() {
	}

	/**
	 * This is called at the beginning of a shopkeeper tick.
	 */
	public void onTickStart() {
		// Reset activity indicator:
		tickActivity = false;
	}

	/**
	 * This is called periodically (roughly once per second) for shopkeepers in active chunks.
	 * <p>
	 * This can for example be used to check if everything is still okay with the shop object, such
	 * as if it still exists and if it is still in its expected location and state. If any of these
	 * checks fail, the shop object may be respawned, teleported back into place, or otherwise
	 * brought back into its expected state.
	 * <p>
	 * However, note that shop objects may already be ticked while they are still
	 * {@link #isSpawningScheduled() scheduled} to be spawned. It is usually recommended to skip any
	 * shop object checks and spawning attempts until after the scheduled spawning attempt took
	 * place.
	 * <p>
	 * This is also called for shop objects that manage their spawning and despawning
	 * {@link AbstractShopObjectType#mustBeSpawned() manually}.
	 * <p>
	 * If the checks to perform are potentially costly performance-wise, or not required to happen
	 * every second, the shop object may decide to run them only every X invocations. For debugging
	 * purposes, the shop object can indicate tick activity by calling
	 * {@link #indicateTickActivity()} whenever it is doing actual work.
	 * <p>
	 * The ticking of shop objects in active chunks may be spread across multiple ticks and might
	 * therefore not happen for all shopkeepers within the same tick.
	 * <p>
	 * If the shopkeeper of this ticked shop object is marked as {@link AbstractShopkeeper#isDirty()
	 * dirty}, a {@link ShopkeeperStorage#saveDelayed() delayed save} will subsequently be
	 * triggered.
	 * <p>
	 * When overriding this method, consider calling the parent class version of this method.
	 */
	public void onTick() {
	}

	/**
	 * This is called at the end of a shopkeeper tick.
	 */
	public void onTickEnd() {
	}

	/**
	 * Subclasses can call this method to indicate activity during their last tick. When enabled,
	 * this can for example be used to visualize ticking activity, for example for debugging
	 * purposes.
	 */
	protected void indicateTickActivity() {
		tickActivity = true;
	}

	/**
	 * Gets the location at which particles for the shopkeeper's tick visualization shall be
	 * spawned.
	 * 
	 * @return the location, or possibly (but not necessarily) <code>null</code> if the shop object
	 *         is not spawned currently, or if it does not support the tick visualization
	 */
	public abstract @Nullable Location getTickVisualizationParticleLocation();

	/**
	 * Visualizes the shop object's activity during the last tick.
	 */
	public void visualizeLastTick() {
		// Default visualization:
		if (!tickActivity) return;

		Location particleLocation = this.getTickVisualizationParticleLocation();
		if (particleLocation == null) return;

		World world = LocationUtils.getWorld(particleLocation);
		world.spawnParticle(Particle.ANGRY_VILLAGER, particleLocation, 1);
	}

	// NAMING

	@Override
	public int getNameLengthLimit() {
		return AbstractShopkeeper.MAX_NAME_LENGTH;
	}

	@Override
	public @Nullable String prepareName(@Nullable String name) {
		if (name == null) return null;
		String prepared = name;
		// Trim to max name length:
		int lengthLimit = this.getNameLengthLimit();
		if (name.length() > lengthLimit) {
			prepared = name.substring(0, lengthLimit);
		}
		return prepared;
	}

	@Override
	public abstract void setName(@Nullable String name);

	@Override
	public abstract @Nullable String getName();

	// PLAYER SHOP OWNER

	/**
	 * This is called by {@link PlayerShopkeeper}s when their owner has changed.
	 */
	public void onShopOwnerChanged() {
	}

	// EDITOR ACTIONS

	/**
	 * Creates the editor buttons for editing this shop object.
	 * <p>
	 * This is usually only invoked once, when the {@link EditorHandler} is set up for the
	 * shopkeeper. So it is not possible to dynamically add or remove buttons with this method.
	 * <p>
	 * In order to allow for subtypes to more easily add or modify the returned editor buttons, this
	 * method is expected to return a new modifiable list with each invocation.
	 * 
	 * @return the editor buttons
	 */
	public List<Button> createEditorButtons() {
		return new ArrayList<>(); // None by default, modifiable by subtypes
	}
}
