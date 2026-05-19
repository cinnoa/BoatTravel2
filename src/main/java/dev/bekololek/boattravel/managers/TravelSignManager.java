package dev.bekololek.boattravel.managers;

import dev.bekololek.boattravel.Main;
import dev.bekololek.boattravel.model.Route;
import dev.bekololek.boattravel.model.TravelSign;
import dev.bekololek.boattravel.utils.MessageConfig;
import dev.bekololek.boattravel.utils.MessageUtils;
import dev.bekololek.boattravel.utils.TownyHelper;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists voyage signs and keeps their visual content in sync with the
 * referenced route's state. All colors/glyphs flow through {@link MessageConfig}
 * so an admin can recolor every sign from config.yml without restarting.
 */
public class TravelSignManager {

    private final Main plugin;
    private final File dataFile;
    private final Map<String, TravelSign> signs = new HashMap<>();

    public TravelSignManager(Main plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "travel-signs.yml");
        load();
    }

    public void load() {
        signs.clear();
        if (!dataFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        var section = cfg.getConfigurationSection("signs");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            TravelSign ts = TravelSign.load(section.getConfigurationSection(key));
            signs.put(ts.key(), ts);
        }
        plugin.getLogger().info("Loaded " + signs.size() + " travel signs.");
    }

    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        int i = 0;
        for (TravelSign ts : signs.values()) {
            ts.save(cfg.createSection("signs.s" + i++));
        }
        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save travel-signs.yml: " + e.getMessage());
        }
    }

    public void register(TravelSign ts) {
        signs.put(ts.key(), ts);
        save();
    }

    public void unregister(TravelSign ts) {
        signs.remove(ts.key());
        save();
    }

    public TravelSign getByKey(String key) {
        return signs.get(key);
    }

    public Collection<TravelSign> getAll() {
        return Collections.unmodifiableCollection(signs.values());
    }

    public List<TravelSign> getPointingAtRoute(String routeName) {
        List<TravelSign> result = new ArrayList<>();
        for (TravelSign ts : signs.values()) {
            if (ts.getRouteName().equalsIgnoreCase(routeName)) result.add(ts);
        }
        return result;
    }

    public void refreshAll(RouteManager routeManager, DockManager dockManager, EconomyManager economyManager) {
        for (TravelSign sign : signs.values()) {
            refreshSign(sign, routeManager, dockManager, economyManager);
        }
    }

    /** Build the sign-header component: ✦ % Title % ✦ with all colors from MessageConfig. */
    public static Component signHeader(String title) {
        return MessageUtils.signHeader(title);
    }

    /** Convenience: the "Dock" sign header. */
    public static Component dockHeader() { return signHeader(MessageConfig.signDockTitle); }
    /** Convenience: the "Voyage" sign header. */
    public static Component voyageHeader() { return signHeader(MessageConfig.signVoyageTitle); }

    /**
     * Recomputes a voyage sign's visible content based on the current state of
     * the referenced route. Falls back to "Unavailable" if the route is missing
     * or invalid.
     */
    public void refreshSign(TravelSign ts, RouteManager routeManager, DockManager dockManager, EconomyManager economyManager) {
        var loc = ts.getBlockLocation();
        if (loc.getWorld() == null) return;
        Block block = loc.getBlock();
        if (!(block.getState() instanceof Sign sign)) return;
        SignSide front = sign.getSide(Side.FRONT);

        Route route = routeManager.getByName(ts.getRouteName());
        boolean valid = routeManager.isValid(route, dockManager);

        front.line(0, voyageHeader());

        if (!valid || route == null) {
            front.line(1, Component.text(ts.getRouteName(), MessageConfig.signDestinationColor));
            front.line(2, Component.empty());
            front.line(3, MessageUtils.signUnavailable());
            sign.update(true, false);
            return;
        }

        var destDock = dockManager.getByName(route.getDestinationDock());
        String destName = destDock != null ? destDock.getName() : route.getDestinationDock();
        String town = "";
        if (destDock != null) {
            town = TownyHelper.getTownNameOrWilderness(plugin, destDock.getHomeLocation());
            if (town.isBlank()) town = TownyHelper.getTownNameOrWilderness(plugin, destDock.getSignLocation());
        }
        double price = economyManager.calculateCost(route.totalDistance());

        front.line(1, MessageUtils.signDestination(destName));
        front.line(2, town.isBlank() ? Component.empty() : MessageUtils.signTown(town));
        if (price <= 0.0) {
            front.line(3, MessageUtils.signFree());
        } else {
            front.line(3, MessageUtils.signPrice(economyManager.format(price)));
        }
        sign.update(true, false);
    }
}
