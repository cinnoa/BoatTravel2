package dev.bekololek.boattravel.listeners;

import dev.bekololek.boattravel.Main;
import dev.bekololek.boattravel.managers.DockManager;
import dev.bekololek.boattravel.managers.EconomyManager;
import dev.bekololek.boattravel.managers.RouteManager;
import dev.bekololek.boattravel.managers.TravelSignManager;
import dev.bekololek.boattravel.model.Dock;
import dev.bekololek.boattravel.model.TravelSign;
import dev.bekololek.boattravel.utils.MessageConfig;
import dev.bekololek.boattravel.utils.MessageUtils;
import dev.bekololek.boattravel.utils.TownyHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;

/**
 * Handles dock-sign and voyage-sign placement, formatting, and removal.
 */
public class SignListener implements Listener {

    private final Main plugin;
    private final DockManager dockManager;
    private final RouteManager routeManager;
    private final TravelSignManager travelSignManager;
    private final EconomyManager economyManager;

    public SignListener(Main plugin, DockManager dockManager, RouteManager routeManager,
                        TravelSignManager travelSignManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.dockManager = dockManager;
        this.routeManager = routeManager;
        this.travelSignManager = travelSignManager;
        this.economyManager = economyManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent e) {
        String line0 = plain(e.line(0)).trim();
        if ("[Dock]".equalsIgnoreCase(line0)) {
            handleDockSign(e);
        } else if ("[Voyage]".equalsIgnoreCase(line0)) {
            handleVoyageSign(e);
        }
    }

    private void handleDockSign(SignChangeEvent e) {
        if (!e.getPlayer().hasPermission("boattravel.dock")) {
            e.getPlayer().sendMessage(MessageUtils.error("You don't have permission to create dock signs."));
            e.setCancelled(true);
            return;
        }

        String dockName = plain(e.line(1)).trim();
        if (dockName.isEmpty()) {
            e.getPlayer().sendMessage(MessageUtils.error("Line 2 must contain the dock name."));
            e.setCancelled(true);
            return;
        }
        if (dockManager.isNameTaken(dockName)) {
            e.getPlayer().sendMessage(MessageUtils.prefixed(Component.empty()
                    .append(MessageUtils.text("A dock named "))
                    .append(MessageUtils.var(dockName))
                    .append(MessageUtils.text(" already exists."))));
            e.setCancelled(true);
            return;
        }

        Block block = e.getBlock();
        Dock dock = new Dock(dockName, block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                block.getX() + 0.5, block.getY(), block.getZ() + 0.5,
                e.getPlayer().getLocation().getYaw(), false);
        dockManager.register(dock);
        applyDockFormat(e, dock);
        e.getPlayer().sendMessage(MessageUtils.prefixed(Component.empty()
                .append(MessageUtils.text("Dock "))
                .append(MessageUtils.var(dockName))
                .append(MessageUtils.text(" registered. Use /bt sethome "))
                .append(MessageUtils.var(dockName))
                .append(MessageUtils.text(" while standing in the water."))));
        travelSignManager.refreshAll(routeManager, dockManager, economyManager);
    }

    private void handleVoyageSign(SignChangeEvent e) {
        if (!e.getPlayer().hasPermission("boattravel.travel")) {
            e.getPlayer().sendMessage(MessageUtils.error("You don't have permission to create voyage signs."));
            e.setCancelled(true);
            return;
        }

        String routeName = plain(e.line(1)).trim();
        if (routeName.isEmpty()) {
            e.getPlayer().sendMessage(MessageUtils.error("Line 2 must contain the route name."));
            e.setCancelled(true);
            return;
        }

        Block block = e.getBlock();
        TravelSign ts = new TravelSign(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), routeName);
        travelSignManager.register(ts);
        plugin.getServer().getScheduler().runTask(plugin,
                () -> travelSignManager.refreshSign(ts, routeManager, dockManager, economyManager));
        e.getPlayer().sendMessage(MessageUtils.prefixed(Component.empty()
                .append(MessageUtils.text("Voyage sign linked to route "))
                .append(MessageUtils.var(routeName))
                .append(MessageUtils.text("."))));
    }

    private void applyDockFormat(SignChangeEvent e, Dock dock) {
        String town = dock.isHomeSet() ? TownyHelper.getTownNameOrWilderness(plugin, dock.getHomeLocation()) : "";
        if (town.isBlank()) town = TownyHelper.getTownNameOrWilderness(plugin, dock.getSignLocation());
        e.line(0, TravelSignManager.dockHeader());
        e.line(1, Component.text(dock.getName(), MessageConfig.signDestinationColor));
        e.line(2, town.isBlank() ? Component.empty() : MessageUtils.signTown(town));
        e.line(3, Component.empty());
    }

    public void refreshDockSign(Dock dock) {
        Block block = dock.getSignLocation().getBlock();
        if (!(block.getState() instanceof Sign sign)) return;
        String town = dock.isHomeSet() ? TownyHelper.getTownNameOrWilderness(plugin, dock.getHomeLocation()) : "";
        if (town.isBlank()) town = TownyHelper.getTownNameOrWilderness(plugin, dock.getSignLocation());
        var front = sign.getSide(org.bukkit.block.sign.Side.FRONT);
        front.line(0, TravelSignManager.dockHeader());
        front.line(1, Component.text(dock.getName(), MessageConfig.signDestinationColor));
        front.line(2, town.isBlank() ? Component.empty() : MessageUtils.signTown(town));
        front.line(3, Component.empty());
        sign.update(true, false);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        if (!(block.getState() instanceof Sign)) return;

        Dock dock = dockManager.getBySignLocation(block.getLocation());
        if (dock != null) {
            dockManager.unregister(dock);
            e.getPlayer().sendMessage(MessageUtils.prefixed(Component.empty()
                    .append(MessageUtils.text("Dock "))
                    .append(MessageUtils.var(dock.getName()))
                    .append(MessageUtils.text(" unregistered."))));
            travelSignManager.refreshAll(routeManager, dockManager, economyManager);
            return;
        }

        TravelSign ts = travelSignManager.getByKey(TravelSign.keyFor(block.getLocation()));
        if (ts != null) {
            travelSignManager.unregister(ts);
            e.getPlayer().sendMessage(MessageUtils.prefixed(Component.empty()
                    .append(MessageUtils.text("Voyage sign for route "))
                    .append(MessageUtils.var(ts.getRouteName()))
                    .append(MessageUtils.text(" removed."))));
        }
    }

    private String plain(Component c) {
        if (c == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(c);
    }
}
