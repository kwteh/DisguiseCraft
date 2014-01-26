package pgDev.bukkit.DisguiseCraft.listeners;


import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import pgDev.bukkit.DisguiseCraft.DisguiseCraft;
import pgDev.bukkit.DisguiseCraft.api.PlayerUndisguiseEvent;
import pgDev.bukkit.DisguiseCraft.disguise.Disguise;

public class WorldChangeUpdater implements Runnable {
	final DisguiseCraft plugin;
	final PlayerChangedWorldEvent event;

	public WorldChangeUpdater(DisguiseCraft plugin, PlayerChangedWorldEvent event) {
		this.plugin = plugin;
		this.event = event;
	}
	
	@Override
	public void run() {
		// World Change is like a join
		plugin.showWorldDisguises(event.getPlayer());
		
		// Handle disguise wearer going through a portal
		if (plugin.disguiseDB.containsKey(event.getPlayer().getName())) {
			Player disguisee = event.getPlayer();
			Disguise disguise = plugin.disguiseDB.get(disguisee.getName());
			
			plugin.undisguiseToWorld(disguisee, event.getFrom());
			
			if (disguise.hasPermission(disguisee)) {
				// Show the disguise to the people in the new world
				plugin.disguiseToWorld(disguisee, disguisee.getWorld());
			} else {
				// Pass the event
				PlayerUndisguiseEvent ev = new PlayerUndisguiseEvent(disguisee);
				plugin.getServer().getPluginManager().callEvent(ev);
				if (ev.isCancelled()) {
					plugin.disguiseToWorld(disguisee, disguisee.getWorld());
				} else {
					plugin.unDisguisePlayer(disguisee);
					disguisee.sendMessage(ChatColor.RED + "You've been undisguised because you do not have permissions to wear that disguise in this world.");
				}
			}
		}
	}

}
