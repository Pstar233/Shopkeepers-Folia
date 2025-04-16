package com.nisovin.shopkeepers.shopobjects.entity;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.shopkeeper.ShopCreationData;
import com.nisovin.shopkeepers.api.shopobjects.entity.EntityShopObject;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.shopobjects.AbstractShopObject;

public abstract class AbstractEntityShopObject
		extends AbstractShopObject implements EntityShopObject {

	protected AbstractEntityShopObject(
			AbstractShopkeeper shopkeeper,
			@Nullable ShopCreationData creationData
	) {
		super(shopkeeper, creationData);
	}

	@Override
	public abstract AbstractEntityShopObjectType<?> getType();

	@Override
	public boolean isSpawned() {
		return (this.getEntity() != null);
	}

	@Override
	public boolean isActive() {
		// Note: Entity#isValid also checks if the entity is dead.
		// Note: The entity's valid flag may get set one tick after the chunk was unloaded. We might
		// also want to check if the entity is still valid when handling chunk unloads, and the
		// entity might be located in a chunk different to the one that is currently being unloaded
		// (and that other chunk might already have been unloaded).
		// In the past we therefore also checked if the chunk in which the entity is currently
		// located is still loaded. However, on later versions of Spigot (late 1.14.1 and above),
		// Entity#isValid also already checks if the chunk is currently loaded, so this is no longer
		// required.
		Entity entity = this.getEntity();
		return entity != null && entity.isValid();
	}

	@Override
	public @Nullable Location getLocation() {
		Entity entity = this.getEntity();
		return (entity != null) ? entity.getLocation() : null;
	}

	@Override
	public @Nullable Object getId() {
		Entity entity = this.getEntity();
		if (entity == null) return null; // Not spawned
		return EntityShopObjectIds.getObjectId(entity);
	}

	@Override
	public @Nullable Location getTickVisualizationParticleLocation() {
		Entity entity = this.getEntity();
		if (entity == null) return null;
		// Return location slightly above the entity:
		Location entityLocation = entity.getLocation();
		return entityLocation.add(0.0D, entity.getHeight() + 0.4D, 0.0D);
	}
}
