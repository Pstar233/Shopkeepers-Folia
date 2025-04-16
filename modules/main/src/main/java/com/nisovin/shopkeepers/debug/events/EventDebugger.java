package com.nisovin.shopkeepers.debug.events;

import org.bukkit.Bukkit;

import com.nisovin.shopkeepers.SKShopkeepersPlugin;
import com.nisovin.shopkeepers.config.Settings;
import com.nisovin.shopkeepers.debug.Debug;
import com.nisovin.shopkeepers.debug.DebugOptions;
import com.nisovin.shopkeepers.util.java.Validate;

/**
 * 用于调试事件处理程序的工具。
 */
public class EventDebugger {

	private final SKShopkeepersPlugin plugin;

	public EventDebugger(SKShopkeepersPlugin plugin) {
		Validate.notNull(plugin, "plugin is null");
		this.plugin = plugin;
	}

	public void onEnable() {
		if (Settings.debug) {
			// 如果启用，则注册调试侦听器：
			// Run delayed 还可以捕获其他插件的事件/事件侦听器。
			Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
				boolean logAllEvent = Debug.isDebugging(DebugOptions.logAllEvents);
				boolean printListeners = Debug.isDebugging(DebugOptions.printListeners);
				if (logAllEvent || printListeners) {
					DebugListener.register(logAllEvent, printListeners);
				}
			}, 10L);
		}
	}

	public void onDisable() {
	}
}
