package dev.bekololek.boattravel.listeners;

import dev.bekololek.boattravel.Main;
import dev.bekololek.boattravel.managers.StatsManager;
import dev.bekololek.boattravel.managers.VoyageManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Returns disconnected travelers to their origin (with a refund) and updates
 * stat-tracked player names when they rejoin under a new alias.
 */
public class SessionListener implements Listener {

    private final Main plugin;
    private final VoyageManager voyageManager;
    private final StatsManager statsManager;

    public SessionListener(Main plugin, VoyageManager voyageManager, StatsManager statsManager) {
        this.plugin = plugin;
        this.voyageManager = voyageManager;
        this.statsManager = statsManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent e) {
        voyageManager.handleDisconnect(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent e) {
        statsManager.updateName(e.getPlayer());
        // Defer to next tick so other plugins finish their join handling first.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline()) {
                voyageManager.deliverPendingReturn(e.getPlayer());
            }
        }, 2L);
    }
}
