package com.nisovin.shopkeepers.shopobjects.living;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.nisovin.shopkeepers.api.ShopkeepersPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Breedable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Steerable;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.SKShopkeepersPlugin;
import com.nisovin.shopkeepers.api.events.ShopkeeperEditedEvent;
import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.api.shopkeeper.ShopCreationData;
import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import com.nisovin.shopkeepers.api.shopobjects.living.LivingShopEquipment;
import com.nisovin.shopkeepers.api.shopobjects.living.LivingShopObject;
import com.nisovin.shopkeepers.api.util.ChunkCoords;
import com.nisovin.shopkeepers.compat.NMSManager;
import com.nisovin.shopkeepers.config.Settings;
import com.nisovin.shopkeepers.debug.DebugOptions;
import com.nisovin.shopkeepers.debug.events.DebugListener;
import com.nisovin.shopkeepers.debug.events.EventDebugListener;
import com.nisovin.shopkeepers.items.ItemUpdates;
import com.nisovin.shopkeepers.lang.Messages;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.shopobjects.ShopObjectData;
import com.nisovin.shopkeepers.shopobjects.ShopkeeperMetadata;
import com.nisovin.shopkeepers.shopobjects.entity.AbstractEntityShopObject;
import com.nisovin.shopkeepers.ui.editor.Button;
import com.nisovin.shopkeepers.ui.editor.EditorSession;
import com.nisovin.shopkeepers.ui.editor.ShopkeeperActionButton;
import com.nisovin.shopkeepers.ui.equipmentEditor.EquipmentEditorUI;
import com.nisovin.shopkeepers.util.annotations.ReadOnly;
import com.nisovin.shopkeepers.util.annotations.ReadWrite;
import com.nisovin.shopkeepers.util.bukkit.EntityUtils;
import com.nisovin.shopkeepers.util.bukkit.EquipmentUtils;
import com.nisovin.shopkeepers.util.bukkit.LocationUtils;
import com.nisovin.shopkeepers.util.bukkit.TextUtils;
import com.nisovin.shopkeepers.util.bukkit.Ticks;
import com.nisovin.shopkeepers.util.bukkit.WorldUtils;
import com.nisovin.shopkeepers.util.data.property.BasicProperty;
import com.nisovin.shopkeepers.util.data.property.Property;
import com.nisovin.shopkeepers.util.data.property.value.PropertyValue;
import com.nisovin.shopkeepers.util.data.serialization.InvalidDataException;
import com.nisovin.shopkeepers.util.inventory.ItemUtils;
import com.nisovin.shopkeepers.util.inventory.PotionUtils;
import com.nisovin.shopkeepers.util.java.CyclicCounter;
import com.nisovin.shopkeepers.util.java.RateLimiter;
import com.nisovin.shopkeepers.util.logging.Log;
import org.jetbrains.annotations.NotNull;

public class SKLivingShopObject<E extends LivingEntity>
		extends AbstractEntityShopObject implements LivingShopObject {

	public static final Property<LivingShopEquipment> EQUIPMENT = new BasicProperty<LivingShopEquipment>()
			.dataKeyAccessor("equipment", SKLivingShopEquipment.SERIALIZER)
			.defaultValueSupplier(SKLivingShopEquipment::new)
			.omitIfDefault()
			.build();

	/**
	 * We check from slightly below the top of the spawn block (= offset) in a range of up to one
	 * block below the spawn block (= range) for a location to spawn the shopkeeper entity at.
	 */
	protected static final double SPAWN_LOCATION_OFFSET = 0.98D;
	protected static final double SPAWN_LOCATION_RANGE = 2.0D;

	protected static final int CHECK_PERIOD_SECONDS = 10;
	protected static final int CHECK_PERIOD_TICKS = Ticks.PER_SECOND * CHECK_PERIOD_SECONDS;
	private static final CyclicCounter nextCheckingOffset = new CyclicCounter(
			1,
			CHECK_PERIOD_SECONDS + 1
	);
	// If the entity could not be respawned this amount of times, we throttle its tick rate (i.e.
	// the rate at which we attempt to respawn it):
	protected static final int MAX_RESPAWN_ATTEMPTS = 5;
	protected static final int THROTTLED_CHECK_PERIOD_SECONDS = 60;

	private static final Location sharedLocation = new Location(null, 0, 0, 0);

	protected final LivingShops livingShops;
	private final SKLivingShopObjectType<?> livingObjectType;

	private final PropertyValue<LivingShopEquipment> equipmentProperty = new PropertyValue<>(EQUIPMENT)
			.onValueChanged(Unsafe.initialized(this)::onEquipmentPropertyChanged)
			.build(properties);

	private @Nullable E entity;
	private @Nullable Location lastSpawnLocation = null;
	private int respawnAttempts = 0;
	private boolean debuggingSpawn = false;
	// Shared among all living shopkeepers to prevent spam:
	private static long lastSpawnDebugMillis = 0L;
	private static final long SPAWN_DEBUG_THROTTLE_MILLIS = TimeUnit.MINUTES.toMillis(5);

	// Initial threshold between [1, CHECK_PERIOD_SECONDS] for load balancing:
	private final int checkingOffset = nextCheckingOffset.getAndIncrement();
	private final RateLimiter checkLimiter = new RateLimiter(CHECK_PERIOD_SECONDS, checkingOffset);
	private boolean skipRespawnAttemptsIfPeaceful = false;

	protected SKLivingShopObject(
			LivingShops livingShops,
			SKLivingShopObjectType<?> livingObjectType,
			AbstractShopkeeper shopkeeper,
			@Nullable ShopCreationData creationData
	) {
		super(shopkeeper, creationData);
		this.livingShops = livingShops;
		this.livingObjectType = livingObjectType;

		// Setup the equipment changed listener for the initial default value:
		this.setEquipmentChangedListener();
	}

	@Override
	public SKLivingShopObjectType<?> getType() {
		return livingObjectType;
	}

	@Override
	public EntityType getEntityType() {
		return livingObjectType.getEntityType();
	}

	@Override
	public void load(ShopObjectData shopObjectData) throws InvalidDataException {
		super.load(shopObjectData);
		equipmentProperty.load(shopObjectData);
	}

	@Override
	public void save(ShopObjectData shopObjectData, boolean saveAll) {
		super.save(shopObjectData, saveAll);
		equipmentProperty.save(shopObjectData);
	}

	// ITEM UPDATES

	@Override
	public int updateItems(String logPrefix, @ReadWrite ShopObjectData shopObjectData) {
		int updatedItems = super.updateItems(logPrefix, shopObjectData);
		updatedItems += updateEquipmentItems(logPrefix, shopObjectData);
		return updatedItems;
	}

	private static int updateEquipmentItems(
			String logPrefix,
			@ReadWrite ShopObjectData shopObjectData
	) {
		LivingShopEquipment shopEquipment;
		try {
			shopEquipment = shopObjectData.get(EQUIPMENT);
		} catch (InvalidDataException e) {
			Log.warning(logPrefix + "Failed to load '" + EQUIPMENT.getName() + "'!", e);
			return 0;
		}

		int updatedItems = 0;

		for (EquipmentSlot slot : EquipmentUtils.EQUIPMENT_SLOTS) {
			var item = shopEquipment.getItem(slot);
			if (item == null) continue;

			var newItem = ItemUpdates.updateItem(item);
			if (newItem == item) continue; // Not changed

			Log.debug(DebugOptions.itemUpdates, logPrefix + "Updated equipment item for slot "
					+ slot.name());
			shopEquipment.setItem(slot, newItem);
			updatedItems += 1;
		}

		if (updatedItems > 0) {
			shopObjectData.set(EQUIPMENT, shopEquipment);
		}

		return updatedItems;
	}

	// ACTIVATION

	@Override
	public @Nullable E getEntity() {
		return entity;
	}

	private @Nullable Location getSpawnLocation() {
		Location spawnLocation = shopkeeper.getLocation();
		if (spawnLocation == null) return null; // World not loaded

		spawnLocation.add(0.5D, 0.0D, 0.5D); // Center of block

		if (this.shallAdjustSpawnLocation()) {
			this.adjustSpawnLocation(spawnLocation);
		}

		return spawnLocation;
	}

	protected boolean shallAdjustSpawnLocation() {
		return true;
	}

	// Shopkeepers might be located 1 block above passable (especially in the past) or non-full
	// blocks. In order to not have these shopkeepers hover but stand on the ground, we determine
	// the exact spawn location their entity would fall to within the range of up to 1 block below
	// their spawn block.
	// This also applies with gravity disabled, and even if the block below their spawn block is air
	// now: Passable blocks like grass or non-full blocks like carpets or slabs might have been
	// broken since the shopkeeper was created. We still want to place the shopkeeper nicely on the
	// ground in those cases.
	private void adjustSpawnLocation(Location spawnLocation) {
		// The entity may be able to stand on certain types of fluids:
		Set<? extends Material> collidableFluids = EntityUtils.getCollidableFluids(
				this.getEntityType()
		);
		// However, if the spawn location is inside a fluid (i.e. underwater or inside of lava), we
		// ignore this aspect (i.e. the entity sinks to the ground even if it can usually stand on
		// top of the liquid).
		// We don't check the spawn block itself but the block above in order to also spawn entities
		// that are in shallow liquids on top of the liquid.
		if (!collidableFluids.isEmpty()) {
			World world = Unsafe.assertNonNull(spawnLocation.getWorld());
			Block blockAbove = world.getBlockAt(
					shopkeeper.getX(),
					shopkeeper.getY() + 1,
					shopkeeper.getZ()
			);
			if (blockAbove.isLiquid()) {
				collidableFluids = Collections.emptySet();
			}
		}

		// We check for collisions from the top of the block:
		spawnLocation.add(0.0D, SPAWN_LOCATION_OFFSET, 0.0D);

		double distanceToGround = WorldUtils.getCollisionDistanceToGround(
				spawnLocation,
				SPAWN_LOCATION_RANGE,
				collidableFluids
		);

		if (distanceToGround == SPAWN_LOCATION_RANGE) {
			// No collision within the checked range: Remove the initial offset from the spawn
			// location again.
			distanceToGround = SPAWN_LOCATION_OFFSET;
		}

		// Adjust the spawn location:
		spawnLocation.add(0.0D, -distanceToGround, 0.0D);
	}

	// Any preparation that needs to be done before spawning. Might only allow limited operations.
	protected void prepareEntity(@NonNull E entity) {
		// Assign metadata for easy identification by other plugins:
		ShopkeeperMetadata.apply(entity);

		// Don't save the entity to the world data:
		entity.setPersistent(false);

		// Apply name (if it has/uses one):
		this.applyName(entity, shopkeeper.getName());

		// Clear equipment:
		// Doing this during entity preparation resolves some issue with the equipment not getting
		// cleared (at least not visually).
		EntityEquipment equipment = entity.getEquipment();
		// Currently, there is no type of living entity without equipment. But since the API
		// specifies this as nullable, we check for this here just in case this changes in the
		// future.
		if (equipment != null) {
			equipment.clear();
		}

		// Some entities (e.g. striders) may randomly spawn with a saddle that does not count as
		// equipment: TODO MC 1.21.5+: Might no longer be the case due to the saddle being part of
		// the equipment now.
		if (entity instanceof Steerable) {
			Steerable steerable = (Steerable) entity;
			steerable.setSaddle(false);
		}

		// Any version-specific preparation:
		NMSManager.getProvider().prepareEntity(entity);
	}

	// Any clean up that needs to happen for the entity. The entity might not be fully setup yet.
	protected void cleanUpEntity() {
		Entity entity = Unsafe.assertNonNull(this.entity);

		Location location = new Location(entity.getLocation().getWorld(), entity.getX(), entity.getY(), entity.getZ());
		Bukkit.getRegionScheduler().run(ShopkeepersPlugin.getInstance(), location, task -> {

			// 禁用 AI：
			this.cleanupAI();

			// 再次删除元数据：
			ShopkeeperMetadata.remove(entity);

			// 删除实体（如果尚未删除）：
			if (!entity.isDead()) {
				entity.remove();
			}

			this.entity = null;
		});
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean spawn() {
		if (entity != null) {
			return true; // Already spawned
		}

		// Prepare spawn location:
		Location spawnLocation = this.getSpawnLocation();
		if (spawnLocation == null) {
			return false; // World not loaded
		}
		World world = Unsafe.assertNonNull(spawnLocation.getWorld());

		// Spawn entity:
		// TODO Check if the block is passable before spawning there?
		EntityType entityType = this.getEntityType();
		Class<? extends Entity> entityClass = Unsafe.assertNonNull(entityType.getEntityClass());
		// Note: We expect this type of entity to be spawnable, and not result in an
		// IllegalArgumentException.
		this.entity = (E) world.spawn(spawnLocation, entityClass, entity -> {
			assert entity != null;
			// Note: This callback is run after the entity has been prepared (this includes the
			// creation of random equipment and the random spawning of passengers) and right before
			// the entity gets added to the world (which triggers the corresponding
			// CreatureSpawnEvent).

			// Debugging entity spawning:
			if (entity.isDead()) {
				Log.debug("Spawning shopkeeper entity is dead already!");
			}

			// Prepare entity, before it gets spawned:
			prepareEntity((E) entity);

			// Try to bypass entity-spawn blocking plugins (right before this specific entity is
			// about to get spawned):
			livingShops.forceCreatureSpawn(spawnLocation, entityType);
		});
		E entity = this.entity;
		assert entity != null;

		boolean success = this.isActive();
		if (success) {
			// Remember the spawn location:
			this.lastSpawnLocation = spawnLocation;

			// Further setup entity after it was successfully spawned:
			// Some entities randomly spawn with passengers:
			for (Entity passenger : entity.getPassengers()) {
				passenger.remove();
			}
			// Some entities might automatically mount on nearby entities (like baby zombies on
			// chicken):
			entity.eject();
			entity.setRemoveWhenFarAway(false);
			entity.setCanPickupItems(false);

			// This is also required so that certain Minecraft behaviors (e.g. the panic behavior of
			// nearby villagers) ignore the shopkeeper entities. Otherwise, the shopkeeper entities
			// can be abused for mob farms (e.g. villages spawn more iron golems when villagers are
			// in panic due to nearby hostile mob shopkeepers).
			entity.setInvulnerable(true);

			// Disable aging and breeding:
			if (entity instanceof Ageable) {
				Ageable ageable = (Ageable) entity;
				ageable.setAdult();
			}
			if (entity instanceof Breedable) {
				Breedable breedable = (Breedable) entity;
				breedable.setBreed(false);
				breedable.setAgeLock(true);
			}

			// Any version-specific setup:
			NMSManager.getProvider().setupSpawnedEntity(entity);

			// Overwrite AI:
			this.overwriteAI();
			// Register the shop object for our custom AI processing:
			livingShops.getLivingEntityAI().addShopObject(this);

			// Prevent raider shopkeepers from participating in nearby raids:
			if (entity instanceof Raider) {
				Raider raider = (Raider) entity;
				raider.setCanJoinRaid(false);
			}

			// Apply sub-type:
			this.onSpawn();

			// Reset all state related to respawn throttling:
			respawnAttempts = 0;
			this.resetTickRate();
			skipRespawnAttemptsIfPeaceful = false;

			// Inform about the object id change:
			this.onIdChanged();
		} else {
			// Failure:
			// Debug, if not already debugging and cooldown is over:
			boolean debug = (Settings.debug && !debuggingSpawn && entity.isDead()
					&& (System.currentTimeMillis() - lastSpawnDebugMillis) > SPAWN_DEBUG_THROTTLE_MILLIS
					&& ChunkCoords.isChunkLoaded(entity.getLocation()));

			// Due to an open Spigot 1.17 issue, entities report as 'invalid' after being spawned
			// during chunk loads. In order to not spam with warnings, this warning has been
			// replaced with a debug output for now.
			// TODO Replace this with a warning again once the underlying issue has been resolved in
			// Spigot.
			Log.debug("Failed to spawn shopkeeper entity: Entity dead: " + entity.isDead()
					+ ", entity valid: " + entity.isValid()
					+ ", chunk loaded: " + ChunkCoords.isChunkLoaded(entity.getLocation())
					+ ", debug -> " + debug);

			// Reset the entity:
			this.cleanUpEntity();

			// TODO Config option to delete the shopkeeper on failed spawn attempt? Check for this
			// during shop creation?

			// Debug entity spawning:
			if (debug) {
				// Print chunk's entity counts:
				EntityUtils.printEntityCounts(spawnLocation.getChunk());

				// Try again and log event activity:
				debuggingSpawn = true;
				lastSpawnDebugMillis = System.currentTimeMillis();
				Log.info("Trying again and logging event activity ..");

				// Log all events occurring during spawning, and their registered listeners:
				DebugListener debugListener = DebugListener.register(true, true);

				// Log creature spawn handling:
				EventDebugListener<CreatureSpawnEvent> spawnListener = new EventDebugListener<>(
						CreatureSpawnEvent.class,
						(priority, event) -> {
							LivingEntity spawnedEntity = event.getEntity();
							Log.info("  CreatureSpawnEvent (" + priority + "): "
									+ "cancelled: " + event.isCancelled()
									+ ", dead: " + spawnedEntity.isDead()
									+ ", valid: " + spawnedEntity.isValid()
									+ ", chunk loaded: "
									+ ChunkCoords.isChunkLoaded(spawnedEntity.getLocation())
							);
						}
				);

				// Try to spawn the entity again:
				success = this.spawn();

				// Unregister listeners again:
				debugListener.unregister();
				spawnListener.unregister();
				debuggingSpawn = false;
				Log.info(".. Done. Successful: " + success);
			}
		}
		return success;
	}

	/**
	 * This method is called right after the entity was spawned.
	 * <p>
	 * It can be used to apply additional mob type specific setup.
	 */
	protected void onSpawn() {
		assert this.getEntity() != null;
		this.updatePotionEffects();
		this.applyEquipment();
	}

	protected void overwriteAI() {
		E entity = Unsafe.assertNonNull(this.entity);

		// Setting the entity non-collidable:
		entity.setCollidable(false);
		// TODO Only required to handle the 'look-at-nearby-player' behavior. Maybe replace this
		// with something own?
		NMSManager.getProvider().overwriteLivingEntityAI(entity);

		// Disable AI (also disables gravity) and replace it with our own handling:
		this.setNoAI(entity);

		if (Settings.silenceLivingShopEntities) {
			entity.setSilent(true);
		}

		if (Settings.disableGravity) {
			this.setNoGravity(entity);
			// When gravity is disabled, we may also be able to disable collisions / the pushing of
			// mobs via the noclip flag. However, this might not properly work for Vex, since they
			// disable their noclip again after their movement.
			// TODO Still required? Bukkit's setCollidable API might actually work now.
			// But this might also provide a small performance benefit.
			NMSManager.getProvider().setNoclip(entity);
		}
	}

	protected final void setNoAI(@NonNull E entity) {
		entity.setAI(false);
		// Note on Bukkit's 'isAware' flag added in MC 1.15: Disables the AI logic similarly to
		// NoAI, but the mob can still move when being pushed around or due to gravity.
		// The collidable API has been reworked to actually work now. Together with the isAware flag
		// this could be an alternative to using NoAI and then having to handle gravity on our own.
		// However, for now we prefer using NoAI. This might be safer in regards to potential future
		// issues and also automatically handles other cases, like players pushing entities around
		// by hitting them.

		// Making sure that Spigot's entity activation range does not keep this entity ticking,
		// because it assumes that it is currently falling:
		// TODO This can be removed once Spigot ignores NoAI entities.
		NMSManager.getProvider().setOnGround(entity, true);
	}

	protected final void setNoGravity(E entity) {
		entity.setGravity(false);

		// Making sure that Spigot's entity activation range does not keep this entity ticking,
		// because it assumes that it is currently falling:
		NMSManager.getProvider().setOnGround(entity, true);
	}

	protected void cleanupAI() {
		// Disable AI:
		livingShops.getLivingEntityAI().removeShopObject(this);
	}

	@Override
	public void despawn() {
		if (entity == null) return;

		// Clean up entity:
		this.cleanUpEntity();
		lastSpawnLocation = null;

		// Inform about the object id change:
		this.onIdChanged();
	}

	@Override
	public @NotNull CompletableFuture<Boolean> move() {
		Entity entity = this.entity;
		if (entity == null) return CompletableFuture.completedFuture(false); // Ignore if not spawned

		Location spawnLocation = this.getSpawnLocation();
		if (spawnLocation == null) return CompletableFuture.completedFuture(false);

		this.lastSpawnLocation = spawnLocation;
		@NotNull CompletableFuture<Boolean> teleportSuccess = SKShopkeepersPlugin.getInstance().getForcingEntityTeleporter().teleport(entity, spawnLocation);

		// Inform the AI system:
		livingShops.getLivingEntityAI().updateLocation(this);

		return teleportSuccess;
	}

	// TICKING

	@Override
	public void onTick() {
		super.onTick();
		if (checkLimiter.request()) {
			if (this.isSpawningScheduled()) {
				Log.debug(DebugOptions.regularTickActivities, () -> shopkeeper.getLogPrefix()
						+ "Spawning is scheduled. Skipping entity check.");
				return;
			}

			this.check();

			// Indicate ticking activity for visualization:
			this.indicateTickActivity();
		}
	}

	private boolean isTickRateThrottled() {
		return (checkLimiter.getThreshold() == THROTTLED_CHECK_PERIOD_SECONDS);
	}

	private void throttleTickRate() {
		if (this.isTickRateThrottled()) return; // Already throttled
		Log.debug("Throttling tick rate");
		checkLimiter.setThreshold(THROTTLED_CHECK_PERIOD_SECONDS);
		checkLimiter.setRemainingThreshold(THROTTLED_CHECK_PERIOD_SECONDS + checkingOffset);
	}

	private void resetTickRate() {
		checkLimiter.setThreshold(CHECK_PERIOD_SECONDS);
		checkLimiter.setRemainingThreshold(checkingOffset);
	}

	private void check() {
		if (!this.isActive()) {
			this.respawnInactiveEntity();
		} else {
			this.teleportBackIfMoved();
			this.updatePotionEffects();
		}
	}

	// True if the entity was respawned.
	private boolean respawnInactiveEntity() {
		assert !this.isActive();
		if (skipRespawnAttemptsIfPeaceful) {
			// Null if the world is not loaded:
			Location shopkeeperLocation = shopkeeper.getLocation();
			if (shopkeeperLocation != null
					&& LocationUtils.getWorld(shopkeeperLocation).getDifficulty() == Difficulty.PEACEFUL) {
				Log.debug(DebugOptions.regularTickActivities, () -> shopkeeper.getLocatedLogPrefix()
						+ this.getEntityType() + " is missing. "
						+ "Skipping respawn attempt due to peaceful difficulty.");
				return false;
			} else {
				skipRespawnAttemptsIfPeaceful = false;
			}
		}
		assert !skipRespawnAttemptsIfPeaceful;

		@Nullable E entity = this.entity;
		if (entity != null) {
			Location entityLocation = entity.getLocation();
			if (ChunkCoords.isSameChunk(shopkeeper.getLocation(), entityLocation)) {
				// Check if the entity was removed due to the world's difficulty:
				if (entity.isDead()
						&& EntityUtils.isRemovedOnPeacefulDifficulty(this.getEntityType())
						&& LocationUtils.getWorld(entityLocation).getDifficulty() == Difficulty.PEACEFUL) {
					skipRespawnAttemptsIfPeaceful = true;
					// This is a warning in order to inform server admins about the issue right
					// away.
					// This is only logged once per affected shopkeeper and then skipped until the
					// difficulty is
					// changed.
					Log.warning(shopkeeper.getLocatedLogPrefix() + this.getEntityType()
							+ " was removed due to the world's difficulty being set to peaceful."
							+ " Respawn attempts are skipped until the difficulty is changed.");
					// No return here because we still need to clean up the old entity.
				} else {
					// The entity has been removed (e.g. by another plugin), or the chunk silently
					// unloaded (without a
					// corresponding ChunkUnloadEvent):
					Log.debug(() -> shopkeeper.getLocatedLogPrefix() + this.getEntityType() +
							" was removed. Maybe by another plugin, or the chunk was silently "
							+ "unloaded. (dead: " + entity.isDead() + ", valid: " + entity.isValid()
							+ ", chunk loaded: " + ChunkCoords.isChunkLoaded(entityLocation) + ")");
				}
			} // Else: The entity might have moved into a chunk that was then unloaded.

			// Despawn (i.e. cleanup) the previously spawned but no longer active entity:
			this.despawn();

			if (skipRespawnAttemptsIfPeaceful) {
				return false;
			}
		}

		Log.debug(() -> shopkeeper.getLocatedLogPrefix() + this.getEntityType()
				+ " is missing. Attempting respawn.");

		boolean spawned = this.spawn(); // This will load the chunk if necessary
		if (!spawned) {
			// TODO Maybe add a setting to remove shopkeeper if it can't be spawned a certain amount
			// of times?
			Log.debug("  Respawn failed");
			respawnAttempts += 1;
			if (respawnAttempts >= MAX_RESPAWN_ATTEMPTS) {
				// Throttle the rate at which we attempt to respawn the entity:
				this.throttleTickRate();
			}
		} // Else: respawnAttempts and tick rate got reset.
		return spawned;
	}

	// This is not only relevant when gravity is enabled, but also to react to other plugins
	// teleporting shopkeeper entities around or enabling their AI again.
	private void teleportBackIfMoved() {
		assert this.isActive();
		E entity = Unsafe.assertNonNull(this.entity);
		// Note: Comparing the entity's current location with the last spawn location (instead of
		// freshly calculating the 'intended' spawn location) not only provides a small performance
		// benefit, but also ensures that shopkeeper mobs don't start to fall if the block below
		// them is broken and gravity is disabled (which is confusing when gravity is supposed to be
		// disabled).
		// However, to account for shopkeepers that have previously been placed above passable or
		// non-full blocks, which might also have been broken since then, we still place the
		// shopkeeper mob up to one block below their location when they are respawned (we just
		// don't move them there dynamically during this check). If the mob is supposed to
		// dynamically move when the block below it is broken, gravity needs to be enabled.
		Location entityLoc = Unsafe.assertNonNull(entity.getLocation(sharedLocation));
		Location lastSpawnLocation = Unsafe.assertNonNull(this.lastSpawnLocation);
		// This also account for the worlds being different:
		if (LocationUtils.getDistanceSquared(entityLoc, lastSpawnLocation) > 0.2D) {
			// The squared distance 0.2 triggers for distances slightly below 0.5. Since we spawn
			// the entity at the center of the spawn block, this ensures that we teleport it back
			// into place whenever it changes its block.
			// Teleport back:
			Log.debug(DebugOptions.regularTickActivities, () -> shopkeeper.getLocatedLogPrefix()
					+ "Entity moved (" + TextUtils.getLocationString(entityLoc)
					+ "). Teleporting back.");
			// We freshly determine a potentially new spawn location:
			// The previous spawn location might no longer be ideal. For example, if the shopkeeper
			// previously spawned slightly below its actual spawn location (due to there missing
			// some block), players might want to reset the shopkeeper's location by letting it fall
			// due to gravity and then placing a block below its actual spawn location for the
			// shopkeeper to now be able to stand on.
			// Non-null: This is only called for shopkeepers in active chunks, i.e. loaded worlds.
			Location spawnLocation = Unsafe.assertNonNull(this.getSpawnLocation());
			spawnLocation.setYaw(entityLoc.getYaw());
			spawnLocation.setPitch(entityLoc.getPitch());
			this.lastSpawnLocation = spawnLocation;

			SKShopkeepersPlugin.getInstance().getForcingEntityTeleporter().teleport(entity, spawnLocation);

			this.overwriteAI();
		}
		sharedLocation.setWorld(null); // Reset
	}

	public void teleportBack() {
		@Nullable E entity = this.getEntity(); // Null if not spawned
		if (entity == null) return;

		Location lastSpawnLocation = Unsafe.assertNonNull(this.lastSpawnLocation);
		Location entityLoc = entity.getLocation();
		lastSpawnLocation.setYaw(entityLoc.getYaw());
		lastSpawnLocation.setPitch(entityLoc.getPitch());

		SKShopkeepersPlugin.getInstance().getForcingEntityTeleporter().teleport(entity, lastSpawnLocation);
	}

	// AI

	/**
	 * This is called whenever the AI of the entity is ticked, while it is in range of players. The
	 * tick rate is defined by {@link Settings#mobBehaviorTickPeriod}. The AI might not be ticked
	 * while the entity is currently falling.
	 */
	public void tickAI() {
		LivingEntity entity = this.getEntity();
		if (entity == null) return; // Unexpected

		// Look at nearby players: Implemented by manually running the vanilla AI goal.
		// In order to compensate for a reduced tick rate, we invoke the AI multiple times.
		// Otherwise, the entity would turn its head more slowly and track the player for an
		// increased duration.
		NMSManager.getProvider().tickAI(entity, Settings.mobBehaviorTickPeriod);
	}

	// NAMING

	@Override
	public void setName(@Nullable String name) {
		@Nullable E entity = this.entity;
		if (entity == null) return;
		this.applyName(entity, name);
	}

	protected void applyName(@NonNull E entity, @Nullable String name) {
		if (Settings.showNameplates && name != null && !name.isEmpty()) {
			String preparedName = this.prepareName(Messages.nameplatePrefix + name);
			// Set entity name plate:
			entity.setCustomName(preparedName);
			entity.setCustomNameVisible(Settings.alwaysShowNameplates);
		} else {
			// Remove name plate:
			entity.setCustomName(null);
			entity.setCustomNameVisible(false);
		}
	}

	@Override
	public @Nullable String getName() {
		@Nullable E entity = this.entity;
		if (entity == null) return null;
		return entity.getCustomName();
	}

	// POTION EFFECTS

	/**
	 * The default {@link PotionEffect}s to apply to the spawned entity.
	 * <p>
	 * These effects are added to any newly spawned entity, and periodically re-added if missing or
	 * nearly expired. Any potion effects not included here are prevented from being added to the
	 * spawned entity, and also periodically removed if detected.
	 * <p>
	 * This can for example be used if a certain mob type requires a certain potion effect to
	 * properly function as a shopkeeper.
	 * <p>
	 * This might be called relatively frequently to check if a given effect equals one of the
	 * default effects. It is therefore recommended that this returns a cached collection, instead
	 * of creating a new collection on each invocation.
	 * 
	 * @return an unmodifiable view on the entity's default potion effects, not <code>null</code>
	 */
	protected Collection<? extends PotionEffect> getDefaultPotionEffects() {
		// None by default:
		return Collections.emptySet();
	}

	private void updatePotionEffects() {
		@Nullable E entity = this.getEntity();
		if (entity == null) return;

		Collection<? extends PotionEffect> defaultPotionEffects = this.getDefaultPotionEffects();
		Collection<PotionEffect> activePotionEffects = Unsafe.castNonNull(entity.getActivePotionEffects());

		// Re-add missing and nearly expired default potion effects:
		defaultPotionEffects.forEach(effect -> {
			@Nullable PotionEffect activeEffect = PotionUtils.findIgnoreDuration(activePotionEffects, effect);
			if (activeEffect != null
					&& (activeEffect.getDuration() == PotionEffect.INFINITE_DURATION
							|| activeEffect.getDuration() > CHECK_PERIOD_TICKS)) {
				return;
			}

			if (activeEffect != null) {
				// Remove the nearly expired effect:
				entity.removePotionEffect(effect.getType());
			}

			entity.addPotionEffect(effect);
		});

		// Remove non-default potion effects:
		activePotionEffects.forEach(effect -> {
			// No duration check here: If the effect matches a default effect and is nearly expired,
			// we already replaced it above.
			// Note: Doing these two operations in this order avoids having to refetch or update the
			// activePotionEffects list for the subsequent 'add-missing-default-effects' operation.
			if (PotionUtils.findIgnoreDuration(defaultPotionEffects, effect) != null) {
				return;
			}

			entity.removePotionEffect(effect.getType());
		});
	}

	// EDITOR ACTIONS

	@Override
	public List<Button> createEditorButtons() {
		List<Button> editorButtons = super.createEditorButtons();
		if (!this.getEditableEquipmentSlots().isEmpty()) {
			editorButtons.add(this.getEquipmentEditorButton());
		}
		return editorButtons;
	}

	// EQUIPMENT

	/**
	 * The editable {@link EquipmentSlot}s.
	 * <p>
	 * Limits which equipment slots can be edited inside the equipment editor. If empty, the
	 * equipment editor button is completely omitted from the shopkeeper editor.
	 * <p>
	 * This only controls whether users can edit the equipment in the editor. Arbitrary equipment
	 * can still be applied programmatically via the API.
	 * <p>
	 * Can be overridden by sub-types.
	 * 
	 * @return An unmodifiable view on the editable equipment slots. Not <code>null</code>, but can
	 *         be empty. The order of the returned slots defines the order in the editor.
	 */
	protected List<? extends EquipmentSlot> getEditableEquipmentSlots() {
		if (Settings.enableAllEquipmentEditorSlots) {
			return EquipmentUtils.EQUIPMENT_SLOTS;
		}

		switch (this.getEntityType()) {
		case PIG: // Dedicated button for saddle
		case STRIDER: // Dedicated button for saddle
		case LLAMA: // Dedicated button for carpet (armor slot)
		case TRADER_LLAMA: // Dedicated button for carpet (armor slot)
		case HORSE: // Dedicated button for horse armor (armor slot) and saddle
		case MULE: // Dedicated button for saddle
		case DONKEY: // Dedicated button for saddle
		case CAMEL: // Dedicated button for saddle
		case ZOMBIE_HORSE: // Dedicated button for saddle
		case SKELETON_HORSE: // Dedicated button for saddle
			return Collections.emptyList();
		case VINDICATOR: // The main hand item is only visible during a chase.
			return EquipmentUtils.EQUIPMENT_SLOTS_HEAD;
		case ENDERMAN: // Item in hand is mapped to the carried block
			return EquipmentUtils.EQUIPMENT_SLOTS_MAINHAND;
		default:
			return EquipmentUtils.getSupportedEquipmentSlots(this.getEntityType());
		}
	}

	@Override
	public LivingShopEquipment getEquipment() {
		return equipmentProperty.getValue();
	}

	private void onEquipmentPropertyChanged() {
		this.setEquipmentChangedListener();

		// Apply the new equipment and inform sub-types, but don't forcefully mark the shopkeeper as
		// dirty here: This is already handled by the equipment property itself, if necessary.
		this.onEquipmentChanged();
	}

	private void setEquipmentChangedListener() {
		((SKLivingShopEquipment) this.getEquipment()).setChangedListener(this::handleEquipmentChanged);
	}

	private void handleEquipmentChanged() {
		shopkeeper.markDirty();
		this.onEquipmentChanged();
	}

	// Can be overridden in sub-types.
	protected void onEquipmentChanged() {
		this.applyEquipment();
	}

	private void applyEquipment() {
		@Nullable E entity = this.getEntity();
		if (entity == null) return; // Not spawned

		@Nullable EntityEquipment entityEquipment = entity.getEquipment();
		if (entityEquipment == null) return;

		LivingShopEquipment shopEquipment = this.getEquipment();

		// Iterate over all equipment slots, to also clear any no longer equipped slots:
		for (EquipmentSlot slot : EquipmentUtils.EQUIPMENT_SLOTS) {
			// No item copy required: Setting the equipment copies the item internally.
			@Nullable ItemStack item = ItemUtils.asItemStackOrNull(shopEquipment.getItem(slot));
			this.setEquipment(entityEquipment, slot, item);
		}
	}

	// Can be overridden by sub-types to for example enforce specific equipment, or apply default
	// equipment.
	protected void setEquipment(
			EntityEquipment entityEquipment,
			EquipmentSlot slot,
			@ReadOnly @Nullable ItemStack item
	) {
		assert entityEquipment != null && slot != null;

		@Nullable ItemStack itemToSet = item;

		// We give entities which would usually burn in sunlight an indestructible item as helmet.
		// This results in less EntityCombustEvents that need to be processed.
		// Note: The fire resistance potion effect does not avoid the EntityCombustEvent.
		// Note: Phantoms also burn in sunlight, but setting a helmet has no effect for them.
		EntityType entityType = this.getEntityType();
		if (slot == EquipmentSlot.HEAD
				&& entityType != EntityType.PHANTOM
				&& EntityUtils.burnsInSunlight(entityType)) {
			if (ItemUtils.isEmpty(itemToSet)) {
				// Buttons are unbreakable and small enough to not be visible inside the entity's
				// head (even for their baby variants).
				itemToSet = new ItemStack(Material.STONE_BUTTON);
			} else {
				assert itemToSet != null;
				// Make the given item indestructible.
				// "Destructible": Has max damage, has damage component, and not unbreakable.
				itemToSet = ItemUtils.setUnbreakable(itemToSet.clone());
			}
		}

		// This copies the item internally:
		entityEquipment.setItem(slot, itemToSet);
	}

	private ItemStack getEquipmentEditorItem() {
		return ItemUtils.setDisplayNameAndLore(
				new ItemStack(Material.ARMOR_STAND),
				Messages.buttonEquipment,
				Messages.buttonEquipmentLore
		);
	}

	private Button getEquipmentEditorButton() {
		return new ShopkeeperActionButton() {
			@Override
			public @Nullable ItemStack getIcon(EditorSession editorSession) {
				return getEquipmentEditorItem();
			}

			@Override
			protected boolean runAction(
					EditorSession editorSession,
					InventoryClickEvent clickEvent
			) {
				editorSession.getUISession().closeDelayedAndRunTask(() -> {
					openEquipmentEditor(editorSession.getPlayer(), false);
				});
				return true;
			}

			@Override
			protected void onActionSuccess(EditorSession editorSession, InventoryClickEvent clickEvent) {
				// The button only opens the equipment editor: Skip the ShopkeeperEditedEvent and
				// saving.
			}
		};
	}

	@Override
	public boolean openEquipmentEditor(Player player, boolean editAllSlots) {
		return EquipmentEditorUI.request(
				shopkeeper,
				player,
				editAllSlots ? EquipmentUtils.EQUIPMENT_SLOTS : this.getEditableEquipmentSlots(),
				this.getEquipment().getItems(),
				(equipmentSlot, item) -> {
					this.getEquipment().setItem(equipmentSlot, item);

					// Call shopkeeper edited event:
					Shopkeeper shopkeeper = this.getShopkeeper();
					Bukkit.getPluginManager().callEvent(new ShopkeeperEditedEvent(shopkeeper, player));

					// Save:
					shopkeeper.save();
				}
		);
	}
}
