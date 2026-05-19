package dev.bekololek.boattravel.listeners;

import dev.bekololek.boattravel.Main;
import dev.bekololek.boattravel.managers.DockManager;
import dev.bekololek.boattravel.managers.RouteManager;
import dev.bekololek.boattravel.managers.TravelSignManager;
import dev.bekololek.boattravel.managers.VoyageManager;
import dev.bekololek.boattravel.model.Route;
import dev.bekololek.boattravel.model.TravelSign;
import dev.bekololek.boattravel.utils.MessageConfig;
import dev.bekololek.boattravel.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.spigotmc.event.entity.EntityDismountEvent;

/**
 * Voyage-time event glue. Responsible for:
 *  - Starting voyages when a player right-clicks a voyage sign (with a
 *    one-tap confirmation flow per the spec).
 *  - Keeping the player riding the seat — cancelling dismounts mid-voyage so
 *    they cannot shift off and start walking on water.
 *  - Blocking foreign teleports while in flight so external plugins don't
 *    fight the voyage path.
 *  - Cancelling damage to the rider, the seat, and the cosmetic boat.
 */
public class VoyageListener implements Listener {

    private final Main plugin;
    private final VoyageManager voyageManager;
    private final RouteManager routeManager;
    private final DockManager dockManager;
    private final TravelSignManager travelSignManager;

    public VoyageListener(Main plugin, VoyageManager voyageManager, RouteManager routeManager,
                          DockManager dockManager, TravelSignManager travelSignManager) {
        this.plugin = plugin;
        this.voyageManager = voyageManager;
        this.routeManager = routeManager;
        this.dockManager = dockManager;
        this.travelSignManager = travelSignManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null) return;
        if (!(block.getState() instanceof Sign)) return;

        TravelSign ts = travelSignManager.getByKey(TravelSign.keyFor(block.getLocation()));
        if (ts == null) return;

        e.setCancelled(true);
        Player player = e.getPlayer();

        if (!player.hasPermission("boattravel.use")) {
            player.sendMessage(MessageUtils.error("You don't have permission to take voyages."));
            return;
        }

        if (voyageManager.isOnVoyage(player.getUniqueId())) {
            player.sendMessage(MessageUtils.warn("You are already on a voyage."));
            return;
        }

        Route route = routeManager.getByName(ts.getRouteName());
        String validation = voyageManager.validateRouteForPlayer(route);
        if (validation != null) {
            player.sendMessage(MessageUtils.error(validation));
            return;
        }

        if (voyageManager.needsConfirmation(player, ts.getRouteName())) {
            voyageManager.primeConfirmation(player, ts.getRouteName());
            player.sendActionBar(Component.text(MessageConfig.actionbarConfirmText, MessageConfig.actionbarConfirmColor));
            return;
        }

        voyageManager.startVoyage(player, route);
    }

    /**
     * Players try to dismount by hitting shift. While on a voyage we hold them
     * in the seat — they cannot bail mid-water.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDismount(EntityDismountEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (!voyageManager.isOnVoyage(player.getUniqueId())) return;
        if (!(e.getDismounted() instanceof ArmorStand)) return;
        // Allow internal dismount during teleport/end-of-voyage by using the bypass set.
        if (voyageManager.isBypassing(player.getUniqueId())) return;
        e.setCancelled(true);
    }

    /**
     * Block external teleports while on a voyage. The voyage flow itself uses
     * the bypass set so internal teleports always go through.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player player = e.getPlayer();
        if (!voyageManager.isOnVoyage(player.getUniqueId())) return;
        if (voyageManager.isBypassing(player.getUniqueId())) return;

        // Allow vanilla vehicle-driven motion (which doesn't reach this event anyway, but be safe).
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) return;

        e.setCancelled(true);
        player.sendMessage(MessageUtils.warn("You can't teleport during a voyage. Use /bt cancel first."));
    }

    /** No damage during a voyage to the player, the seat, or the cosmetic boat. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player player) {
            if (voyageManager.isOnVoyage(player.getUniqueId())) {
                e.setCancelled(true);
            }
            return;
        }
        // Seat or boat: cancel damage so dropped ravagers, lightning, etc. don't break the ride.
        for (var voyage : voyageManager.allVoyages()) {
            if (voyage.getSeat() != null && voyage.getSeat().getUniqueId().equals(e.getEntity().getUniqueId())) {
                e.setCancelled(true);
                return;
            }
            if (voyage.getVisualBoat() != null && voyage.getVisualBoat().getUniqueId().equals(e.getEntity().getUniqueId())) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
