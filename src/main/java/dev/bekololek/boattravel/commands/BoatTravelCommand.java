package dev.bekololek.boattravel.commands;

import dev.bekololek.boattravel.Main;
import dev.bekololek.boattravel.managers.DockManager;
import dev.bekololek.boattravel.managers.EconomyManager;
import dev.bekololek.boattravel.managers.RouteManager;
import dev.bekololek.boattravel.managers.StatsManager;
import dev.bekololek.boattravel.managers.TravelSignManager;
import dev.bekololek.boattravel.managers.VoyageManager;
import dev.bekololek.boattravel.model.Dock;
import dev.bekololek.boattravel.model.Route;
import dev.bekololek.boattravel.utils.MessageConfig;
import dev.bekololek.boattravel.utils.MessageUtils;
import dev.bekololek.boattravel.utils.TownyHelper;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /boattravel (alias /bt) command tree.
 * <pre>
 *   /bt reload
 *   /bt cancel
 *   /bt docks
 *   /bt info &lt;dock&gt;
 *   /bt sethome &lt;dock&gt;
 *   /bt route create &lt;name&gt; &lt;origin&gt; &lt;destination&gt;
 *   /bt route addpoint &lt;name&gt;
 *   /bt route removelast &lt;name&gt;
 *   /bt route boattype &lt;name&gt; &lt;type&gt;
 *   /bt route enable|disable|delete|info &lt;name&gt;
 *   /bt route list
 *   /bt stats [player &lt;name&gt;|top &lt;stat&gt;]
 *   /bt music toggle|on|off
 * </pre>
 */
public class BoatTravelCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final DockManager dockManager;
    private final RouteManager routeManager;
    private final TravelSignManager travelSignManager;
    private final EconomyManager economyManager;
    private final StatsManager statsManager;
    private final VoyageManager voyageManager;

    public BoatTravelCommand(Main plugin, DockManager dockManager, RouteManager routeManager,
                             TravelSignManager travelSignManager, EconomyManager economyManager,
                             StatsManager statsManager, VoyageManager voyageManager) {
        this.plugin = plugin;
        this.dockManager = dockManager;
        this.routeManager = routeManager;
        this.travelSignManager = travelSignManager;
        this.economyManager = economyManager;
        this.statsManager = statsManager;
        this.voyageManager = voyageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload"  -> handleReload(sender);
            case "cancel"  -> handleCancel(sender);
            case "docks"   -> handleDocksList(sender);
            case "info"    -> handleDockInfo(sender, args);
            case "sethome" -> handleSetHome(sender, args);
            case "route"   -> handleRoute(sender, args);
            case "stats"   -> handleStats(sender, args);
            case "music"   -> handleMusic(sender, args);
            default        -> sendUsage(sender);
        }
        return true;
    }

    // ── /bt reload ───────────────────────────────────────────────────────────

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("boattravel.admin")) { noPerm(sender); return; }
        plugin.reloadConfig();
        MessageConfig.reload(plugin);
        dockManager.load();
        routeManager.load();
        travelSignManager.load();
        travelSignManager.refreshAll(routeManager, dockManager, economyManager);
        sender.sendMessage(MessageUtils.success("BoatTravel configuration reloaded."));
    }

    // ── /bt cancel ───────────────────────────────────────────────────────────

    private void handleCancel(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.error("Only players can cancel voyages."));
            return;
        }
        if (!player.hasPermission("boattravel.cancel")) { noPerm(sender); return; }
        if (!voyageManager.isOnVoyage(player.getUniqueId())) {
            player.sendMessage(MessageUtils.warn("You are not currently on a voyage."));
            return;
        }
        voyageManager.cancelVoyage(player.getUniqueId());
    }

    // ── /bt docks ────────────────────────────────────────────────────────────

    private void handleDocksList(CommandSender sender) {
        if (!sender.hasPermission("boattravel.list")) { noPerm(sender); return; }
        sender.sendMessage(MessageUtils.header("Docks"));
        var docks = new ArrayList<>(dockManager.getAll());
        docks.sort(Comparator.comparing(Dock::getName, String.CASE_INSENSITIVE_ORDER));
        if (docks.isEmpty()) {
            sender.sendMessage(MessageUtils.text("  (none yet)"));
            return;
        }
        for (Dock dock : docks) {
            String town = dock.isHomeSet() ? TownyHelper.getTownNameOrWilderness(plugin, dock.getHomeLocation()) : "";
            if (town.isBlank()) town = TownyHelper.getTownNameOrWilderness(plugin, dock.getSignLocation());
            String suffix = town.isBlank() ? "" : " (" + town + ")";
            sender.sendMessage(MessageUtils.listRow("  ", dock.getName(), suffix));
        }
    }

    // ── /bt info <dock> ──────────────────────────────────────────────────────

    private void handleDockInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("boattravel.list")) { noPerm(sender); return; }
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.usage("/bt info", "<dock>"));
            return;
        }
        Dock dock = dockManager.getByName(args[1]);
        if (dock == null) { sender.sendMessage(MessageUtils.error("Unknown dock.")); return; }

        sender.sendMessage(MessageUtils.header("Dock: " + dock.getName()));
        sender.sendMessage(MessageUtils.text("  Sign world: ").append(MessageUtils.var(dock.getSignWorld())));
        sender.sendMessage(MessageUtils.text("  Home set: ").append(MessageUtils.var(String.valueOf(dock.isHomeSet()))));
        if (dock.isHomeSet()) {
            String coords = String.format("%.1f, %.1f, %.1f", dock.getHomeX(), dock.getHomeY(), dock.getHomeZ());
            sender.sendMessage(MessageUtils.text("  Home: ").append(MessageUtils.var(coords)));
        }
        String town = dock.isHomeSet() ? TownyHelper.getTownNameOrWilderness(plugin, dock.getHomeLocation()) : "";
        if (town.isBlank()) town = TownyHelper.getTownNameOrWilderness(plugin, dock.getSignLocation());
        if (!town.isBlank()) {
            sender.sendMessage(MessageUtils.text("  Town: ").append(MessageUtils.var(town)));
        }

        var related = new ArrayList<Route>();
        for (Route route : routeManager.getAll()) {
            if (route.usesDock(dock.getName())) related.add(route);
        }
        if (!related.isEmpty()) {
            sender.sendMessage(MessageUtils.text("  Routes:"));
            for (Route route : related) {
                sender.sendMessage(MessageUtils.listRow("    ", route.getName(),
                        " (" + route.getOriginDock() + " -> " + route.getDestinationDock() + ")"));
            }
        }
    }

    // ── /bt sethome <dock> ───────────────────────────────────────────────────

    private void handleSetHome(CommandSender sender, String[] args) {
        if (!sender.hasPermission("boattravel.admin")) { noPerm(sender); return; }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.error("Only players can set dock home positions."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.usage("/bt sethome", "<dock>"));
            return;
        }
        Dock dock = dockManager.getByName(args[1]);
        if (dock == null) { sender.sendMessage(MessageUtils.error("Unknown dock.")); return; }

        if (!dockManager.setHome(args[1], player.getLocation())) {
            sender.sendMessage(MessageUtils.error("Could not set dock home."));
            return;
        }
        sender.sendMessage(MessageUtils.prefixed(Component.empty()
                .append(MessageUtils.text("Home set for "))
                .append(MessageUtils.var(dock.getName()))
                .append(MessageUtils.text("."))));
        travelSignManager.refreshAll(routeManager, dockManager, economyManager);
    }

    // ── /bt route … ──────────────────────────────────────────────────────────

    private void handleRoute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("boattravel.admin")) { noPerm(sender); return; }
        if (args.length < 2) { sendRouteUsage(sender); return; }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list"       -> routeList(sender);
            case "create"     -> routeCreate(sender, args);
            case "delete"     -> routeSimple(sender, args, "delete");
            case "enable"     -> routeSimple(sender, args, "enable");
            case "disable"    -> routeSimple(sender, args, "disable");
            case "info"       -> routeInfo(sender, args);
            case "addpoint"   -> routeAddPoint(sender, args);
            case "removelast" -> routeRemoveLast(sender, args);
            case "boattype"   -> routeBoatType(sender, args);
            default           -> sendRouteUsage(sender);
        }
    }

    private void routeList(CommandSender sender) {
        sender.sendMessage(MessageUtils.header("Routes"));
        var routes = new ArrayList<>(routeManager.getAll());
        routes.sort(Comparator.comparing(Route::getName, String.CASE_INSENSITIVE_ORDER));
        if (routes.isEmpty()) {
            sender.sendMessage(MessageUtils.text("  (none yet)"));
            return;
        }
        for (Route route : routes) {
            String state = route.isEnabled() ? "" : " [disabled]";
            sender.sendMessage(MessageUtils.listRow("  ", route.getName(),
                    " (" + route.getOriginDock() + " -> " + route.getDestinationDock()
                            + ", " + route.getWaypoints().size() + " points)" + state));
        }
    }

    private void routeCreate(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(MessageUtils.usage("/bt route create", "<name> <origin> <destination>"));
            return;
        }
        String name = args[2];
        String origin = args[3];
        String destination = args[4];
        if (routeManager.exists(name)) {
            sender.sendMessage(MessageUtils.error("A route with that name already exists."));
            return;
        }
        if (dockManager.getByName(origin) == null) {
            sender.sendMessage(MessageUtils.error("Unknown origin dock."));
            return;
        }
        if (dockManager.getByName(destination) == null) {
            sender.sendMessage(MessageUtils.error("Unknown destination dock."));
            return;
        }
        routeManager.createRoute(name, origin, destination);
        sender.sendMessage(MessageUtils.prefixed(Component.empty()
                .append(MessageUtils.text("Created route "))
                .append(MessageUtils.var(name))
                .append(MessageUtils.text(". Add waypoints with /bt route addpoint "))
                .append(MessageUtils.var(name))
                .append(MessageUtils.text("."))));
    }

    private void routeSimple(CommandSender sender, String[] args, String op) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.usage("/bt route " + op, "<name>"));
            return;
        }
        String name = args[2];
        Route route = routeManager.getByName(name);
        if (route == null) {
            sender.sendMessage(MessageUtils.error("Unknown route."));
            return;
        }
        switch (op) {
            case "enable" -> {
                routeManager.setEnabled(name, true);
                sender.sendMessage(MessageUtils.prefixed(Component.empty()
                        .append(MessageUtils.text("Enabled "))
                        .append(MessageUtils.var(name))
                        .append(MessageUtils.text("."))));
            }
            case "disable" -> {
                routeManager.setEnabled(name, false);
                sender.sendMessage(MessageUtils.prefixed(Component.empty()
                        .append(MessageUtils.text("Disabled "))
                        .append(MessageUtils.var(name))
                        .append(MessageUtils.text("."))));
            }
            case "delete" -> {
                routeManager.deleteRoute(name);
                sender.sendMessage(MessageUtils.prefixed(Component.empty()
                        .append(MessageUtils.text("Deleted route "))
                        .append(MessageUtils.var(name))
                        .append(MessageUtils.text("."))));
            }
        }
        travelSignManager.refreshAll(routeManager, dockManager, economyManager);
    }

    private void routeInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.usage("/bt route info", "<name>"));
            return;
        }
        Route route = routeManager.getByName(args[2]);
        if (route == null) {
            sender.sendMessage(MessageUtils.error("Unknown route."));
            return;
        }
        sender.sendMessage(MessageUtils.header("Route: " + route.getName()));
        sender.sendMessage(MessageUtils.text("  Origin: ").append(MessageUtils.var(route.getOriginDock())));
        sender.sendMessage(MessageUtils.text("  Destination: ").append(MessageUtils.var(route.getDestinationDock())));
        sender.sendMessage(MessageUtils.text("  Points: ").append(MessageUtils.var(String.valueOf(route.getWaypoints().size()))));
        sender.sendMessage(MessageUtils.text("  Distance: ").append(MessageUtils.var(String.format("%.1f", route.totalDistance()) + " blocks")));
        sender.sendMessage(MessageUtils.text("  Boat: ").append(MessageUtils.var(route.getBoatType().name())));
        sender.sendMessage(MessageUtils.text("  Enabled: ").append(MessageUtils.var(String.valueOf(route.isEnabled()))));
        boolean valid = routeManager.isValid(route, dockManager);
        sender.sendMessage(MessageUtils.text("  Valid: ").append(MessageUtils.var(String.valueOf(valid))));
    }

    private void routeAddPoint(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.error("Only players can add route points."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.usage("/bt route addpoint", "<name>"));
            return;
        }
        String name = args[2];
        if (!routeManager.exists(name)) {
            sender.sendMessage(MessageUtils.error("Unknown route."));
            return;
        }
        if (!routeManager.addPoint(name, player.getLocation())) {
            sender.sendMessage(MessageUtils.error("Could not add waypoint."));
            return;
        }
        Route route = routeManager.getByName(name);
        sender.sendMessage(MessageUtils.prefixed(Component.empty()
                .append(MessageUtils.text("Waypoint #"))
                .append(MessageUtils.var(String.valueOf(route.getWaypoints().size())))
                .append(MessageUtils.text(" added to "))
                .append(MessageUtils.var(name))
                .append(MessageUtils.text("."))));
        travelSignManager.refreshAll(routeManager, dockManager, economyManager);
    }

    private void routeRemoveLast(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.usage("/bt route removelast", "<name>"));
            return;
        }
        String name = args[2];
        if (!routeManager.exists(name)) {
            sender.sendMessage(MessageUtils.error("Unknown route."));
            return;
        }
        if (!routeManager.removeLastPoint(name)) {
            sender.sendMessage(MessageUtils.warn("That route has no waypoints to remove."));
            return;
        }
        sender.sendMessage(MessageUtils.prefixed(Component.empty()
                .append(MessageUtils.text("Removed last waypoint from "))
                .append(MessageUtils.var(name))
                .append(MessageUtils.text("."))));
        travelSignManager.refreshAll(routeManager, dockManager, economyManager);
    }

    private void routeBoatType(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MessageUtils.usage("/bt route boattype", "<name> <type>"));
            return;
        }
        String name = args[2];
        if (!routeManager.exists(name)) {
            sender.sendMessage(MessageUtils.error("Unknown route."));
            return;
        }
        Boat.Type type;
        try { type = Boat.Type.valueOf(args[3].toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) {
            sender.sendMessage(MessageUtils.error("Unknown boat type. Valid: "
                    + Arrays.stream(Boat.Type.values()).map(Enum::name).collect(Collectors.joining(", "))));
            return;
        }
        routeManager.setBoatType(name, type);
        sender.sendMessage(MessageUtils.prefixed(Component.empty()
                .append(MessageUtils.text("Boat type for "))
                .append(MessageUtils.var(name))
                .append(MessageUtils.text(" set to "))
                .append(MessageUtils.var(type.name()))
                .append(MessageUtils.text("."))));
    }

    // ── /bt stats ────────────────────────────────────────────────────────────

    private void handleStats(CommandSender sender, String[] args) {
        if (!sender.hasPermission("boattravel.stats")) { noPerm(sender); return; }

        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtils.error("Specify a player from console: /bt stats player <name>"));
                return;
            }
            printPlayerStats(sender, player.getUniqueId(), player.getName());
            return;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("player")) {
            if (args.length < 3) {
                sender.sendMessage(MessageUtils.usage("/bt stats player", "<name>"));
                return;
            }
            if (!sender.hasPermission("boattravel.stats.others")) { noPerm(sender); return; }
            var target = plugin.getServer().getOfflinePlayer(args[2]);
            if (target == null || target.getUniqueId() == null) {
                sender.sendMessage(MessageUtils.error("Unknown player."));
                return;
            }
            printPlayerStats(sender, target.getUniqueId(), target.getName() != null ? target.getName() : args[2]);
            return;
        }

        if (sub.equals("top")) {
            if (args.length < 3) {
                sender.sendMessage(MessageUtils.usage("/bt stats top", "<stat>"));
                return;
            }
            printTop(sender, args[2]);
            return;
        }

        sender.sendMessage(MessageUtils.usage("/bt stats", "[player|top] <arg>"));
    }

    private void printPlayerStats(CommandSender sender, UUID uuid, String name) {
        sender.sendMessage(MessageUtils.header("Stats: " + name));
        for (String stat : StatsManager.leaderboardStats()) {
            Object value = statsManager.getPlayerStat(uuid, stat);
            sender.sendMessage(Component.empty()
                    .append(Component.text("  " + StatsManager.statLabel(stat) + ": ", MessageConfig.statsLabelColor))
                    .append(decorated(String.valueOf(value), MessageConfig.statsValueColor, MessageConfig.statsValueDecorations)));
        }
        Object fav = statsManager.getPlayerStat(uuid, "favorite_destination");
        sender.sendMessage(Component.empty()
                .append(Component.text("  Favorite Destination: ", MessageConfig.statsLabelColor))
                .append(decorated(String.valueOf(fav), MessageConfig.statsValueColor, MessageConfig.statsValueDecorations)));
    }

    private void printTop(CommandSender sender, String stat) {
        var stats = StatsManager.leaderboardStats();
        if (!stats.contains(stat.toLowerCase(Locale.ROOT))) {
            sender.sendMessage(MessageUtils.error("Unknown stat. Valid: " + String.join(", ", stats)));
            return;
        }
        sender.sendMessage(MessageUtils.header("Top: " + StatsManager.statLabel(stat)));
        var rows = statsManager.getTopPlayers(stat, 10);
        if (rows.isEmpty()) {
            sender.sendMessage(MessageUtils.text("  (no entries)"));
            return;
        }
        int rank = 1;
        for (var row : rows) {
            sender.sendMessage(Component.empty()
                    .append(Component.text("  " + rank + ". ", MessageConfig.statsRankColor))
                    .append(Component.text(row.getKey(), MessageConfig.statsLabelColor))
                    .append(Component.text(" — ", MessageConfig.statsSeparatorColor))
                    .append(decorated(formatNumber(row.getValue()), MessageConfig.statsValueColor, MessageConfig.statsValueDecorations)));
            rank++;
        }
    }

    private String formatNumber(Number value) {
        double d = value.doubleValue();
        return d == Math.floor(d) ? String.valueOf((long) d) : String.format("%.2f", d);
    }

    private Component decorated(String text, net.kyori.adventure.text.format.TextColor color,
                                 java.util.Set<net.kyori.adventure.text.format.TextDecoration> decorations) {
        Component c = Component.text(text, color);
        for (var d : decorations) c = c.decorate(d);
        return c;
    }

    // ── /bt music ────────────────────────────────────────────────────────────

    private void handleMusic(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.error("Only players can toggle voyage music."));
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "toggle";
        boolean enabled;
        switch (sub) {
            case "on"     -> { voyageManager.setMusicEnabled(player.getUniqueId(), true); enabled = true; }
            case "off"    -> { voyageManager.setMusicEnabled(player.getUniqueId(), false); enabled = false; }
            case "toggle" -> { enabled = voyageManager.toggleMusic(player.getUniqueId()); }
            default       -> {
                sender.sendMessage(MessageUtils.usage("/bt music", "<toggle|on|off>"));
                return;
            }
        }
        player.sendMessage(MessageUtils.prefixed(Component.empty()
                .append(MessageUtils.text("Voyage music: "))
                .append(MessageUtils.var(enabled ? "on" : "off"))
                .append(MessageUtils.text("."))));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(MessageUtils.header("BoatTravel"));
        sender.sendMessage(MessageUtils.usage("/bt", "docks"));
        sender.sendMessage(MessageUtils.usage("/bt info", "<dock>"));
        sender.sendMessage(MessageUtils.usage("/bt sethome", "<dock>"));
        sender.sendMessage(MessageUtils.usage("/bt route", "<list|create|...>"));
        sender.sendMessage(MessageUtils.usage("/bt stats", "[player|top]"));
        sender.sendMessage(MessageUtils.usage("/bt music", "<toggle|on|off>"));
        sender.sendMessage(MessageUtils.usage("/bt cancel", ""));
        sender.sendMessage(MessageUtils.usage("/bt reload", ""));
    }

    private void sendRouteUsage(CommandSender sender) {
        sender.sendMessage(MessageUtils.header("Routes"));
        sender.sendMessage(MessageUtils.usage("/bt route list", ""));
        sender.sendMessage(MessageUtils.usage("/bt route create", "<name> <origin> <destination>"));
        sender.sendMessage(MessageUtils.usage("/bt route addpoint", "<name>"));
        sender.sendMessage(MessageUtils.usage("/bt route removelast", "<name>"));
        sender.sendMessage(MessageUtils.usage("/bt route boattype", "<name> <type>"));
        sender.sendMessage(MessageUtils.usage("/bt route enable", "<name>"));
        sender.sendMessage(MessageUtils.usage("/bt route disable", "<name>"));
        sender.sendMessage(MessageUtils.usage("/bt route delete", "<name>"));
        sender.sendMessage(MessageUtils.usage("/bt route info", "<name>"));
    }

    private void noPerm(CommandSender sender) {
        sender.sendMessage(MessageUtils.error("You don't have permission to do that."));
    }

    // ── Tab completion ───────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return startsWithFilter(List.of("reload", "cancel", "docks", "info", "sethome", "route", "stats", "music"), args[0]);
        }
        String top = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (top) {
                case "info", "sethome" -> dockNames(args[1]);
                case "route" -> startsWithFilter(List.of("list", "create", "addpoint", "removelast", "boattype",
                        "enable", "disable", "delete", "info"), args[1]);
                case "stats" -> startsWithFilter(List.of("player", "top"), args[1]);
                case "music" -> startsWithFilter(List.of("toggle", "on", "off"), args[1]);
                default -> List.of();
            };
        }
        if (top.equals("route") && args.length >= 3) {
            String sub = args[1].toLowerCase(Locale.ROOT);
            if (args.length == 3) {
                return switch (sub) {
                    case "addpoint", "removelast", "enable", "disable", "delete", "info", "boattype", "create"
                            -> routeNames(args[2]);
                    default -> List.of();
                };
            }
            if (args.length == 4 && sub.equals("create")) return dockNames(args[3]);
            if (args.length == 4 && sub.equals("boattype")) {
                return startsWithFilter(Arrays.stream(Boat.Type.values()).map(Enum::name).toList(), args[3]);
            }
            if (args.length == 5 && sub.equals("create")) return dockNames(args[4]);
        }
        if (top.equals("stats") && args.length >= 3) {
            String sub = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("top")) {
                return startsWithFilter(StatsManager.leaderboardStats(), args[2]);
            }
            if (sub.equals("player")) {
                return startsWithFilter(plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName).toList(), args[2]);
            }
        }
        return List.of();
    }

    private List<String> dockNames(String prefix) {
        return startsWithFilter(dockManager.getAll().stream().map(Dock::getName).toList(), prefix);
    }

    private List<String> routeNames(String prefix) {
        return startsWithFilter(routeManager.getAll().stream().map(Route::getName).toList(), prefix);
    }

    private List<String> startsWithFilter(List<String> options, String prefix) {
        String lc = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lc)).toList();
    }
}
