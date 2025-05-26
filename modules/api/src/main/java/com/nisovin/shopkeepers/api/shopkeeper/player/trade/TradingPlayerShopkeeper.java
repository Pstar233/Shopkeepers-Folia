package com.nisovin.shopkeepers.api.shopkeeper.player.trade;

import java.util.List;

import com.nisovin.shopkeepers.api.shopkeeper.offers.TradeOffer;
import com.nisovin.shopkeepers.api.shopkeeper.player.PlayerShopkeeper;

/**
 * Trades arbitrary items.
 * <p>
 * Unlike the buying or selling shopkeepers, this type of shopkeeper is not limited to trading
 * currency items.
 */
public interface TradingPlayerShopkeeper extends PlayerShopkeeper {

	// OFFERS

	/**
	 * Gets the offers of this shopkeeper.
	 * <p>
	 * There can be multiple different offers for the same kind of item.
	 * 
	 * @return an unmodifiable view on the shopkeeper's offers
	 */
	public List<? extends TradeOffer> getOffers();

	/**
	 * Clears the shopkeeper's offers.
	 */
	public void clearOffers();

	/**
	 * Sets the shopkeeper's offers.
	 * <p>
	 * This replaces the shopkeeper's previous offers.
	 * 
	 * @param offers
	 *            the new offers
	 */
	public void setOffers(List<? extends TradeOffer> offers);

	/**
	 * Adds the given offer to the shopkeeper.
	 * <p>
	 * The offer gets added to the end of the current offers. If you want to insert, replace or
	 * reorder offers, use {@link #setOffers(List)} instead.
	 * 
	 * @param offer
	 *            the offer to add
	 */
	public void addOffer(TradeOffer offer);

	/**
	 * Adds the given offers to the shopkeeper.
	 * <p>
	 * The offers get added to the end of the current offers. If you want to insert, replace or
	 * reorder offers, use {@link #setOffers(List)} instead.
	 * 
	 * @param offers
	 *            the offers to add
	 */
	public void addOffers(List<? extends TradeOffer> offers);
}
