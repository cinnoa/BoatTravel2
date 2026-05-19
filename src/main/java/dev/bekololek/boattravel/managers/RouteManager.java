package dev.bekololek.boattravel.managers;

import dev.bekololek.boattravel.Main;
import dev.bekololek.boattravel.model.Dock;
import dev.bekololek.boattravel.model.Route;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Boat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists named routes to routes.yml. Routes live independently of any sign
 * — breaking a voyage sign does not remove the route.
 */
public class RouteManager {

    private final Main plugin;
    private final File dataFile;
    private final Map<String, Route> routes = new HashMap<>();

    public RouteManager(Main plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "routes.yml");
        load();
    }

    public void load() {
        routes.clear();
        if (!dataFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        var section = cfg.getConfigurationSection("routes");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            Route route = Route.load(key, section.getConfigurationSection(key));
            routes.put(key.toLowerCase(), route);
        }
        plugin.getLogger().info("Loaded " + routes.size() + " routes.");
    }

    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        List<Route> ordered = new ArrayList<>(routes.values());
        ordered.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (Route route : ordered) {
            route.save(cfg.createSection("routes." + route.getName()));
        }
        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save routes.yml: " + e.getMessage());
        }
    }

    public boolean exists(String routeName) {
        return routeName != null && routes.containsKey(routeName.toLowerCase());
    }

    public Route getByName(String routeName) {
        return routeName == null ? null : routes.get(routeName.toLowerCase());
    }

    public Collection<Route> getAll() {
        return Collections.unmodifiableCollection(routes.values());
    }

    public boolean createRoute(String routeName, String originDock, String destinationDock) {
        if (exists(routeName)) return false;
        routes.put(routeName.toLowerCase(), new Route(routeName, originDock, destinationDock, List.of(), Boat.Type.OAK, true));
        save();
        return true;
    }

    public boolean deleteRoute(String routeName) {
        if (routeName == null) return false;
        Route removed = routes.remove(routeName.toLowerCase());
        if (removed == null) return false;
        save();
        return true;
    }

    public boolean addPoint(String routeName, org.bukkit.Location location) {
        Route route = getByName(routeName);
        if (route == null) return false;
        route.addPoint(location);
        save();
        return true;
    }

    public boolean removeLastPoint(String routeName) {
        Route route = getByName(routeName);
        if (route == null || !route.removeLastPoint()) return false;
        save();
        return true;
    }

    public boolean setEnabled(String routeName, boolean enabled) {
        Route route = getByName(routeName);
        if (route == null) return false;
        route.setEnabled(enabled);
        save();
        return true;
    }

    public boolean setBoatType(String routeName, Boat.Type type) {
        Route route = getByName(routeName);
        if (route == null) return false;
        route.setBoatType(type);
        save();
        return true;
    }

    /** Validates that the route is enabled, has both docks set up, ≥2 waypoints, single world, and non-zero length. */
    public boolean isValid(Route route, DockManager dockManager) {
        if (route == null) return false;
        Dock origin = dockManager.getByName(route.getOriginDock());
        Dock destination = dockManager.getByName(route.getDestinationDock());
        if (!route.isEnabled()) return false;
        if (origin == null || destination == null) return false;
        if (!origin.isHomeSet() || !destination.isHomeSet()) return false;
        if (!route.hasEnoughPoints()) return false;
        if (!route.isWorldConsistent()) return false;
        return route.totalDistance() > 0.0;
    }
}
