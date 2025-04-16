package com.nisovin.shopkeepers.shopkeeper.admin.regular;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.shopkeeper.TradingRecipe;
import com.nisovin.shopkeepers.api.shopkeeper.admin.regular.RegularAdminShopkeeper;
import com.nisovin.shopkeepers.api.shopkeeper.offers.TradeOffer;
import com.nisovin.shopkeepers.api.ui.DefaultUITypes;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.shopkeeper.SKDefaultShopTypes;
import com.nisovin.shopkeepers.shopkeeper.ShopkeeperData;
import com.nisovin.shopkeepers.shopkeeper.admin.AbstractAdminShopkeeper;
import com.nisovin.shopkeepers.shopkeeper.migration.Migration;
import com.nisovin.shopkeepers.shopkeeper.migration.MigrationPhase;
import com.nisovin.shopkeepers.shopkeeper.migration.ShopkeeperDataMigrator;
import com.nisovin.shopkeepers.shopkeeper.offers.SKTradeOffer;
import com.nisovin.shopkeepers.util.annotations.ReadWrite;
import com.nisovin.shopkeepers.util.data.property.BasicProperty;
import com.nisovin.shopkeepers.util.data.property.Property;
import com.nisovin.shopkeepers.util.data.serialization.InvalidDataException;
import com.nisovin.shopkeepers.util.java.CollectionUtils;
import com.nisovin.shopkeepers.util.java.Validate;
import com.nisovin.shopkeepers.util.logging.Log;

public class SKRegularAdminShopkeeper
		extends AbstractAdminShopkeeper implements RegularAdminShopkeeper {

	// There can be multiple different offers for the same kind of item:
	private final List<SKTradeOffer> offers = new ArrayList<>();
	private final List<? extends SKTradeOffer> offersView = Collections.unmodifiableList(offers);

	/**
	 * Creates a not yet initialized {@link SKRegularAdminShopkeeper}.
	 * <p>
	 * See {@link AbstractShopkeeper} for details on initialization.
	 */
	protected SKRegularAdminShopkeeper() {
	}

	@Override
	protected void setup() {
		if (this.getUIHandler(DefaultUITypes.EDITOR()) == null) {
			this.registerUIHandler(new RegularAdminShopEditorHandler(this));
		}
		super.setup();
	}

	@Override
	public void loadDynamicState(ShopkeeperData shopkeeperData) throws InvalidDataException {
		super.loadDynamicState(shopkeeperData);
		this.loadOffers(shopkeeperData);
	}

	@Override
	public void saveDynamicState(ShopkeeperData shopkeeperData, boolean saveAll) {
		super.saveDynamicState(shopkeeperData, saveAll);
		this.saveOffers(shopkeeperData);
	}

	// ITEM UPDATES

	@Override
	protected int updateItems(String logPrefix, @ReadWrite ShopkeeperData shopkeeperData) {
		int updatedItems = super.updateItems(logPrefix, shopkeeperData);
		updatedItems += updateOfferItems(logPrefix, shopkeeperData);
		return updatedItems;
	}

	private static int updateOfferItems(String logPrefix, @ReadWrite ShopkeeperData shopkeeperData) {
		try {
			var updatedOffers = new ArrayList<TradeOffer>(shopkeeperData.get(OFFERS));
			var updatedItems = SKTradeOffer.updateItems(updatedOffers, logPrefix);
			if (updatedItems > 0) {
				shopkeeperData.set(OFFERS, updatedOffers);
				return updatedItems;
			}
		} catch (InvalidDataException e) {
			Log.warning(logPrefix + "Failed to load '" + OFFERS.getName() + "'!", e);
		}
		return 0;
	}

	//

	@Override
	public RegularAdminShopType getType() {
		return SKDefaultShopTypes.ADMIN_REGULAR();
	}

	@Override
	public boolean hasTradingRecipes(@Nullable Player player) {
		return !this.getOffers().isEmpty();
	}

	@Override
	public List<? extends TradingRecipe> getTradingRecipes(@Nullable Player player) {
		// SKTradeOffer extends SKTradingRecipe and reports to not be out-of-stock. Both
		// SKTradeOffer and TradingRecipe are immutable. We can therefore reuse the offers as
		// trading recipes, and don't have to create new trading recipes for them.
		return offersView;
	}

	// OFFERS

	private static final String DATA_KEY_OFFERS = "recipes";
	public static final Property<List<? extends TradeOffer>> OFFERS = new BasicProperty<List<? extends TradeOffer>>()
			.dataKeyAccessor(DATA_KEY_OFFERS, SKTradeOffer.LIST_SERIALIZER)
			.useDefaultIfMissing()
			.defaultValue(Collections.emptyList())
			.build();

	static {
		// Register shopkeeper data migrations:
		ShopkeeperDataMigrator.registerMigration(new Migration(
				"admin-offers",
				MigrationPhase.ofShopkeeperClass(SKRegularAdminShopkeeper.class)
		) {
			@Override
			public boolean migrate(
					ShopkeeperData shopkeeperData,
					String logPrefix
			) throws InvalidDataException {
				return SKTradeOffer.migrateOffers(
						shopkeeperData.getDataValue(DATA_KEY_OFFERS),
						logPrefix
				);
			}
		});
	}

	private void loadOffers(ShopkeeperData shopkeeperData) throws InvalidDataException {
		assert shopkeeperData != null;
		this._setOffers(shopkeeperData.get(OFFERS));
	}

	private void saveOffers(ShopkeeperData shopkeeperData) {
		assert shopkeeperData != null;
		shopkeeperData.set(OFFERS, this.getOffers());
	}

	@Override
	public List<? extends TradeOffer> getOffers() {
		return offersView;
	}

	@Override
	public void clearOffers() {
		this._clearOffers();
		this.markDirty();
	}

	private void _clearOffers() {
		offers.clear();
	}

	@Override
	public void setOffers(List<? extends TradeOffer> offers) {
		Validate.notNull(offers, "offers is null");
		Validate.noNullElements(offers, "offers contains null");
		this._setOffers(offers);
		this.markDirty();
	}

	private void _setOffers(List<? extends TradeOffer> offers) {
		assert offers != null && !CollectionUtils.containsNull(offers);
		this._clearOffers();
		this._addOffers(offers);
	}

	@Override
	public void addOffer(TradeOffer offer) {
		Validate.notNull(offer, "offer is null");
		this._addOffer(offer);
		this.markDirty();
	}

	private void _addOffer(TradeOffer offer) {
		assert offer != null;
		Validate.isTrue(offer instanceof SKTradeOffer, "offer is not of type SKTradeOffer");
		SKTradeOffer skOffer = (SKTradeOffer) offer;

		// Add the new offer:
		offers.add(skOffer);
	}

	@Override
	public void addOffers(List<? extends TradeOffer> offers) {
		Validate.notNull(offers, "offers is null");
		Validate.noNullElements(offers, "offers contains null");
		this._addOffers(offers);
		this.markDirty();
	}

	private void _addOffers(List<? extends TradeOffer> offers) {
		assert offers != null && !CollectionUtils.containsNull(offers);
		offers.forEach(this::_addOffer);
	}
}
