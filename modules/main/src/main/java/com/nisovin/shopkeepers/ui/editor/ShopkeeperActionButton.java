package com.nisovin.shopkeepers.ui.editor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import com.nisovin.shopkeepers.api.events.ShopkeeperEditedEvent;
import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import com.nisovin.shopkeepers.util.java.Validate;

/**
 * An {@link ActionButton} for simple one-click shopkeeper editing actions.
 * <p>
 * Successful actions trigger a {@link ShopkeeperEditedEvent} and a save of the shopkeeper.
 */
public abstract class ShopkeeperActionButton extends ActionButton {

	public ShopkeeperActionButton() {
		this(false);
	}

	public ShopkeeperActionButton(boolean placeAtEnd) {
		super(placeAtEnd);
	}

	@Override
	protected boolean isApplicable(AbstractEditorHandler editorHandler) {
		return super.isApplicable(editorHandler) && (editorHandler instanceof EditorHandler);
	}

	protected Shopkeeper getShopkeeper() {
		AbstractEditorHandler editorHandler = this.getEditorHandler();
		Validate.State.notNull(editorHandler,
				"This button has not yet been added to any editor handler!");
		assert editorHandler instanceof EditorHandler;
		return Unsafe.<EditorHandler>castNonNull(editorHandler).getShopkeeper();
	}

	@Override
	protected void onActionSuccess(EditorSession editorSession, InventoryClickEvent clickEvent) {
		Shopkeeper shopkeeper = this.getShopkeeper();

		// Call shopkeeper edited event:
		Player player = editorSession.getPlayer();
		Bukkit.getServer().getPluginManager().callEvent(new ShopkeeperEditedEvent(shopkeeper, player));

		// Save:
		shopkeeper.save();
	}
}
