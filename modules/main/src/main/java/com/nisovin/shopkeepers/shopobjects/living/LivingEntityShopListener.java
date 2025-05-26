package com.nisovin.shopkeepers.shopobjects.living;

import java.util.Collection;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.CreeperPowerEvent;
import org.bukkit.event.entity.CreeperPowerEvent.PowerCause;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent.Action;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.PigZapEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.SheepDyeWoolEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.projectiles.ProjectileSource;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.SKShopkeepersPlugin;
import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.api.shopobjects.DefaultShopObjectTypes;
import com.nisovin.shopkeepers.api.shopobjects.ShopObject;
import com.nisovin.shopkeepers.config.Settings;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.shopkeeper.registry.SKShopkeeperRegistry;
import com.nisovin.shopkeepers.util.bukkit.EntityUtils;
import com.nisovin.shopkeepers.util.bukkit.EventUtils;
import com.nisovin.shopkeepers.util.interaction.InteractionUtils;
import com.nisovin.shopkeepers.util.interaction.TestPlayerInteractEntityEvent;
import com.nisovin.shopkeepers.util.logging.Log;

class LivingEntityShopListener implements Listener {

	private final SKShopkeepersPlugin plugin;
	private final SKShopkeeperRegistry shopkeeperRegistry;

	LivingEntityShopListener(SKShopkeepersPlugin plugin) {
		this.plugin = plugin;
		this.shopkeeperRegistry = plugin.getShopkeeperRegistry();
	}

	void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, plugin);
		// Ensure that our interact event handlers are always executed first, even after plugin
		// reloads:
		// In order to not change the order among the already registered event handlers of our own
		// plugin, we move them all together to the front of their handler lists.
		EventUtils.enforceExecuteFirst(
				PlayerInteractEntityEvent.class,
				EventPriority.LOWEST,
				plugin
		);
		EventUtils.enforceExecuteFirst(
				PlayerInteractAtEntityEvent.class,
				EventPriority.LOWEST,
				plugin
		);
	}

	void onDisable() {
		HandlerList.unregisterAll(this);
	}

	// We want to bypass other plugins by default, so that shops can also be opened in protected
	// regions.
	// We cancel the event to prevent any vanilla mechanics from taking place, and also to indicate
	// to other plugins, and to later event handlers of our own plugin, that they can safely ignore
	// the event (e.g. to prevent protection plugins from sending their 'interaction denied' message
	// for shopkeeper entities). For that purpose, we handle and cancel the event as early as
	// possible (LOWEST priority).
	// Using a higher event priority with a setting to ignore whether the event got already
	// cancelled by other plugins is not an option, because then these other plugins will already
	// have handled the event and we have no chance to avoid their side effects (e.g. protection
	// plugins will already have sent the player their 'interaction denied' message, even though we
	// open the shop UI afterwards anyway).
	// In some usecases it may be desired by the server admin that we take into account whether some
	// other plugin wants to cancel the interaction. For those situations the setting
	// 'check-shop-interaction-result' can be used to call an additional interaction event that
	// other plugins can react to and which determines whether we handle the interaction. Since this
	// might cause side effects in general due to other plugins handling the event, this is disabled
	// by default. This also requires that we listen to and cancel the event as early as possible,
	// so that the event is not handled twice by other plugins in this situation.
	// There is still the potential for conflicts with other event handlers that run on LOWEST
	// priority as well. If these are event handlers of our own plugin (for which we control the
	// order in which they are registered and handle the event), we usually want to ignore the event
	// if it has already been cancelled, because this indicates that the event has triggered some
	// other mechanic that takes precedence.
	// And if the event has been cancelled by another plugin, we cannot differentiate between
	// whether this is one of the cases that we could relatively safely bypass (for example, if some
	// protection plugin reacts on LOWEST event priority and has sent its 'interaction denied'
	// message, it may be reasonable to still open the shop menu regardless to at least remain
	// functional in that case), or if this is a case in which we definitively do not want to
	// trigger our action (for example, if another plugin defines a mechanic that is triggered by
	// the same event we usually want that only one of these actions takes place).
	// For the above reasons we therefore ignore the event if it has already been cancelled.
	// One option to resolve these conflicts with other plugins that listen on LOWEST event priority
	// as well might seem to be the 'loadbefore' entry in the plugin.yml file. However, this is not
	// an acceptable solution, because it depends on being aware of and explicitly specifying these
	// other plugins, and it breaks whenever the Shopkeepers plugin is dynamically reloaded, because
	// that re-registers all event handlers and thereby moves them to the back of the registered
	// event handlers.
	// In an attempt to resolve these conflicts with other plugins anyway (for instance,
	// GriefPrevention, a popular protection plugin, reacts on LOWEST event priority), we forcefully
	// move our event handler(s) to the front of the relevant handler list. This ensures that our
	// event handler(s) are executed first, even if our plugin has been dynamically reloaded.
	// If another plugin is supposed to still execute before us so that it can cancel our event
	// handling, it could either apply a similar trick, or the server admin can enable the already
	// mentioned 'check-shop-interaction-result' setting.
	// The reasoning outlined here does not only apply to this specific event handler, but also to
	// other event handlers in this plugin for which we chose to execute at event priority LOWEST.
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	void onEntityInteract(PlayerInteractEntityEvent event) {
		// Ignore our own fake interact event:
		if (event instanceof TestPlayerInteractEntityEvent) return;

		// If the clicked entity is a complex entity part, we continue with its parent:
		Entity clickedEntity = EntityUtils.resolveComplexEntity(event.getRightClicked());

		Player player = event.getPlayer();
		boolean isInteractAtEvent = (event instanceof PlayerInteractAtEntityEvent);
		Log.debug(() -> "Player " + player.getName() + " is interacting (" + (event.getHand())
				+ ") " + (isInteractAtEvent ? "at " : "with ") + clickedEntity.getType()
				+ " at " + clickedEntity.getLocation());

		// We only deal with living entities here:
		if (!(clickedEntity instanceof LivingEntity)) return;

		// Also checks for Citizens NPC shopkeepers:
		AbstractShopkeeper shopkeeper = shopkeeperRegistry.getShopkeeperByEntity(clickedEntity);
		if (shopkeeper == null) {
			Log.debug("  Non-shopkeeper");
			return;
		}

		// Ignore if already cancelled. This resolves conflicts with other event handlers that also
		// run at LOWEST priority.
		if (event.isCancelled()) {
			Log.debug("  Ignoring already cancelled event.");
			return;
		}

		// Always cancel all interactions with shopkeepers to prevent any default behaviors:
		// For Citizen shopkeepers, server admins can decide whether they want the usual NPC
		// interaction behavior to take place (e.g. triggering attached NPC commands) in addition to
		// the shopkeeper-specific behavior.
		if (Settings.cancelCitizenNpcInteractions
				|| shopkeeper.getShopObject().getType() != DefaultShopObjectTypes.CITIZEN()) {
			Log.debug("  Cancelling entity interaction");
			event.setCancelled(true);
			// Update inventory in case the interaction would trigger an item action normally (such
			// as animal feeding):
			player.updateInventory();
		}

		// The PlayerInteractAtEntityEvent gets sometimes called additionally to the
		// PlayerInteractEntityEvent.
		// We cancel the event but don't process it any further.
		if (isInteractAtEvent) {
			Log.debug("  Ignoring InteractAtEntity event");
			return;
		}

		// Only trigger shopkeeper interaction for main-hand events:
		if (event.getHand() != EquipmentSlot.HAND) {
			Log.debug("  Ignoring off-hand interaction");
			return;
		}

		// Check the entity interaction result by calling another interact event:
		if (Settings.checkShopInteractionResult) {
			if (!InteractionUtils.checkEntityInteract(player, clickedEntity)) {
				Log.debug("  Cancelled by another plugin");
				return;
			}
		}

		// Handle interaction:
		shopkeeper.onPlayerInteraction(player);
	}

	// This event gets sometimes called additionally to the PlayerInteractEntityEvent
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	void onEntityInteractAt(PlayerInteractAtEntityEvent event) {
		this.onEntityInteract(event);
	}

	// TODO Many of those behaviors might no longer be active, once all entities use NoAI (once
	// legacy mob behavior is no longer supported).

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onEntityTarget(EntityTargetEvent event) {
		Entity entity = event.getEntity();
		@Nullable Entity target = event.getTarget();
		if (shopkeeperRegistry.isShopkeeper(entity)
				|| (target != null && shopkeeperRegistry.isShopkeeper(target))) {
			event.setCancelled(true);
		}
	}

	// TODO Allow Citizen shopkeepers to get hurt if they are configured to be vulnerable? What
	// about other aspects, such as being targeted by other mobs, creeper charging, enderman
	// teleporting, etc.? Waiting for further clarification of actual usecases.
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onEntityDamage(EntityDamageEvent event) {
		// Block damaging of shopkeepers:
		Entity entity = event.getEntity();
		if (shopkeeperRegistry.isShopkeeper(entity)) {
			event.setCancelled(true);

			// If damaged by another mob, reset the mob's target. Future targeting should get
			// prevented somewhere else (see EntityTargetEvent).
			if (event instanceof EntityDamageByEntityEvent entityDamageByEntityEvent
					&& entityDamageByEntityEvent.getDamager() instanceof Mob attacker
					&& entity.equals(attacker.getTarget())) {
				attacker.setTarget(null);
			}
			return;
		}

		// Prevent damage caused by shopkeeper mobs (e.g. touching a puffed pufferfish shopkeeper):
		if (event instanceof EntityDamageByEntityEvent entityDamageByEntityEvent
				&& shopkeeperRegistry.isShopkeeper(entityDamageByEntityEvent.getDamager())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onEntityEnterVehicle(VehicleEnterEvent event) {
		Entity entity = event.getEntered();
		if (shopkeeperRegistry.isShopkeeper(entity)) {
			event.setCancelled(true);
		}
	}

	// Example: Creepers.

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onExplodePrime(ExplosionPrimeEvent event) {
		if (shopkeeperRegistry.isShopkeeper(event.getEntity())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onExplode(EntityExplodeEvent event) {
		if (shopkeeperRegistry.isShopkeeper(event.getEntity())) {
			event.setCancelled(true);
			Log.debug(() -> "Cancelled event for living shop: " + event.getEventName());
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onCreeperCharged(CreeperPowerEvent event) {
		if (event.getCause() != PowerCause.LIGHTNING) return;
		if (shopkeeperRegistry.isShopkeeper(event.getEntity())) {
			event.setCancelled(true);
		}
	}

	// Example: Enderman.

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onEntityChangeBlock(EntityChangeBlockEvent event) {
		if (shopkeeperRegistry.isShopkeeper(event.getEntity())) {
			event.setCancelled(true);
		}
	}

	// This is supposed to cancel all vanilla teleporting.
	// In Paper, this is also called for plugin invoked teleports, including in cases in which we
	// try to move the shopkeeper entity. We workaround this issue by forcefully uncanceling the
	// teleport event again later in the event handling for all our own plugin triggered teleports.
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onEntityTeleport(EntityTeleportEvent event) {
		if (shopkeeperRegistry.isShopkeeper(event.getEntity())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onEntityPortalTeleport(EntityPortalEvent event) {
		if (shopkeeperRegistry.isShopkeeper(event.getEntity())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onPigZap(PigZapEvent event) {
		if (shopkeeperRegistry.isShopkeeper(event.getEntity())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onSheepDyed(SheepDyeWoolEvent event) {
		if (shopkeeperRegistry.isShopkeeper(event.getEntity())) {
			event.setCancelled(true);
		}
	}

	// Handles all kinds of events, such as for example villagers struck by lightning turning into
	// witches.
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onEntityTransform(EntityTransformEvent event) {
		if (shopkeeperRegistry.isShopkeeper(event.getEntity())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onPotionSplash(PotionSplashEvent event) {
		for (LivingEntity entity : event.getAffectedEntities()) {
			assert entity != null;
			if (shopkeeperRegistry.isShopkeeper(entity)) {
				event.setIntensity(entity, 0.0D);
			}
		}
	}

	// Allow sleeping if the only nearby monsters are shopkeepers:
	// Note: Cancellation state also reflects default behavior.
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
	void onPlayerEnterBed(PlayerBedEnterEvent event) {
		// Bed entering prevented due to nearby monsters?
		if (event.getBedEnterResult() != BedEnterResult.NOT_SAFE) return;

		// Find nearby monsters that prevent bed entering (see MC EntityHuman):
		Block bedBlock = event.getBed();
		Collection<Entity> monsters = Unsafe.castNonNull(bedBlock.getWorld().getNearbyEntities(
				bedBlock.getLocation(),
				8.0D, 5.0D, 8.0D,
				(entity) -> {
					// TODO Bukkit API to check if monster prevents sleeping?
					// E.g. PigZombies only prevent sleeping if they are angered.
					if (!(entity instanceof Monster)) return false;
					if (entity instanceof PigZombie) {
						return ((PigZombie) entity).isAngry();
					}
					return true;
				}
		));

		for (Entity entity : monsters) {
			if (!shopkeeperRegistry.isShopkeeper(entity)) {
				// Found non-shopkeeper entity. Do nothing (keep bed entering prevented):
				return;
			}
		}
		// Sleeping is only prevented due to nearby shopkeepers. -> Bypass and allow sleeping:
		Log.debug(() -> "Allowing sleeping of player '" + event.getPlayer().getName()
				+ "': The only nearby monsters are shopkeepers.");
		event.setUseBed(Result.ALLOW);
	}

	// Example: Blazes or skeletons.

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onEntityLaunchProjectile(ProjectileLaunchEvent event) {
		ProjectileSource source = event.getEntity().getShooter();
		if (!(source instanceof LivingEntity)) return;
		if (shopkeeperRegistry.isShopkeeper((LivingEntity) source)) {
			event.setCancelled(true);
		}
	}

	// Example: Snowmans.

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onEntityBlockForm(EntityBlockFormEvent event) {
		if (shopkeeperRegistry.isShopkeeper(event.getEntity())) {
			event.setCancelled(true);
		}
	}

	// Example: Chicken laying eggs.

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onEntityDropItem(EntityDropItemEvent event) {
		if (shopkeeperRegistry.isShopkeeper(event.getEntity())) {
			event.setCancelled(true);
		}
	}

	// Prevent shopkeeper entities from being affected by potion effects:
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onEntityPotionEffectEvent(EntityPotionEffectEvent event) {
		if (event.getAction() != Action.ADDED) return;

		AbstractShopkeeper shopkeeper = shopkeeperRegistry.getShopkeeperByEntity(event.getEntity());
		if (shopkeeper == null) return;

		// Ignore the default potion effects:
		ShopObject shopObject = shopkeeper.getShopObject();
		if (shopObject instanceof SKLivingShopObject<?>) {
			SKLivingShopObject<?> livingShopObject = (SKLivingShopObject<?>) shopObject;
			if (livingShopObject.getDefaultPotionEffects().contains(event.getNewEffect())) {
				return;
			}
		}

		event.setCancelled(true);
	}

	// Prevent shopkeeper entities from getting set on fire (e.g. monsters in daylight).
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onEntityCombustEvent(EntityCombustEvent event) {
		// If the entity is standing in lava, fire, etc., we ignore the event.
		if (event instanceof EntityCombustByBlockEvent) return;

		Entity entity = event.getEntity();
		if (shopkeeperRegistry.isShopkeeper(entity)) {
			event.setCancelled(true);
		}
	}

	// Prevent shopkeeper entities (e.g. allays) from picking up items.
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onEntityPickupItemEvent(EntityPickupItemEvent event) {
		if (shopkeeperRegistry.isShopkeeper(event.getEntity())) {
			event.setCancelled(true);
		}
	}
}
