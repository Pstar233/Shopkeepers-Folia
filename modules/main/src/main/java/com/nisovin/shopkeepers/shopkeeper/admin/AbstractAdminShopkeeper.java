package com.nisovin.shopkeepers.shopkeeper.admin;

import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.shopkeeper.ShopCreationData;
import com.nisovin.shopkeepers.api.shopkeeper.ShopkeeperCreateException;
import com.nisovin.shopkeepers.api.shopkeeper.admin.AdminShopCreationData;
import com.nisovin.shopkeepers.api.shopkeeper.admin.AdminShopkeeper;
import com.nisovin.shopkeepers.api.ui.DefaultUITypes;
import com.nisovin.shopkeepers.lang.Messages;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.shopkeeper.ShopkeeperData;
import com.nisovin.shopkeepers.ui.SKDefaultUITypes;
import com.nisovin.shopkeepers.ui.trading.TradingHandler;
import com.nisovin.shopkeepers.util.bukkit.PermissionUtils;
import com.nisovin.shopkeepers.util.bukkit.TextUtils;
import com.nisovin.shopkeepers.util.data.property.BasicProperty;
import com.nisovin.shopkeepers.util.data.property.DataKeyAccessor;
import com.nisovin.shopkeepers.util.data.property.EmptyDataPredicates;
import com.nisovin.shopkeepers.util.data.property.Property;
import com.nisovin.shopkeepers.util.data.serialization.InvalidDataException;
import com.nisovin.shopkeepers.util.data.serialization.java.StringSerializers;
import com.nisovin.shopkeepers.util.java.StringUtils;

public abstract class AbstractAdminShopkeeper
		extends AbstractShopkeeper implements AdminShopkeeper {

	public static class AdminShopTradingHandler extends TradingHandler {

		protected AdminShopTradingHandler(AbstractAdminShopkeeper shopkeeper) {
			super(SKDefaultUITypes.TRADING(), shopkeeper);
		}

		@Override
		public AbstractAdminShopkeeper getShopkeeper() {
			return (AbstractAdminShopkeeper) super.getShopkeeper();
		}

		@Override
		public boolean canAccess(Player player, boolean silent) {
			if (!super.canAccess(player, silent)) return false;

			// Check trading permission:
			String tradePermission = this.getShopkeeper().getTradePermission();
			if (tradePermission != null && !PermissionUtils.hasPermission(player, tradePermission)) {
				if (!silent) {
					this.debugNotOpeningUI(player, "Player is missing the custom trade permission '"
							+ tradePermission + "'.");
					TextUtils.sendMessage(player, Messages.missingCustomTradePerm);
				}
				return false;
			}

			return true;
		}
	}

	// Null if no additional trading permission is required. Not empty.
	private @Nullable String tradePermission = null;

	/**
	 * Creates a new and not yet initialized {@link AbstractAdminShopkeeper}.
	 * <p>
	 * See {@link AbstractShopkeeper} for details on initialization.
	 */
	protected AbstractAdminShopkeeper() {
	}

	/**
	 * Expects an {@link AdminShopCreationData}.
	 */
	@Override
	protected void loadFromCreationData(int id, ShopCreationData shopCreationData)
			throws ShopkeeperCreateException {
		super.loadFromCreationData(id, shopCreationData);
	}

	@Override
	protected void setup() {
		if (this.getUIHandler(DefaultUITypes.TRADING()) == null) {
			this.registerUIHandler(new AdminShopTradingHandler(this));
		}
		super.setup();
	}

	@Override
	public void loadDynamicState(ShopkeeperData shopkeeperData) throws InvalidDataException {
		super.loadDynamicState(shopkeeperData);
		this.loadTradePermission(shopkeeperData);
	}

	@Override
	public void saveDynamicState(ShopkeeperData shopkeeperData, boolean saveAll) {
		super.saveDynamicState(shopkeeperData, saveAll);
		this.saveTradePermission(shopkeeperData);
	}

	// TRADE PERMISSION

	public static final Property<@Nullable String> TRADE_PERMISSION = new BasicProperty<@Nullable String>()
			.dataAccessor(new DataKeyAccessor<>("tradePerm", StringSerializers.SCALAR)
					.emptyDataPredicate(EmptyDataPredicates.EMPTY_STRING)
			)
			.nullable() // Null if no additional trading permission is required
			.defaultValue(null)
			.build();

	private void loadTradePermission(ShopkeeperData shopkeeperData) throws InvalidDataException {
		assert shopkeeperData != null;
		this._setTradePermission(shopkeeperData.get(TRADE_PERMISSION));
	}

	private void saveTradePermission(ShopkeeperData shopkeeperData) {
		assert shopkeeperData != null;
		shopkeeperData.set(TRADE_PERMISSION, tradePermission);
	}

	@Override
	public @Nullable String getTradePermission() {
		return tradePermission;
	}

	@Override
	public void setTradePermission(@Nullable String tradePermission) {
		this._setTradePermission(tradePermission);
		this.markDirty();
	}

	private void _setTradePermission(@Nullable String tradePermission) {
		this.tradePermission = StringUtils.getNotEmpty(tradePermission);
	}
}
