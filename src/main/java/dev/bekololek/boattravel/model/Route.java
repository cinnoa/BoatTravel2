package dev.bekololek.boattravel.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Boat;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A named, ordered list of waypoints connecting two docks. Routes live in
 * routes.yml independent of any signs that point to them.
 */
public class Route {

    /** Single waypoint on a route. */
    public record RoutePoint(String world, double x, double y, double z) {
        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }

        /**
         * Build a point from a player's location, snapping Y to the water
         * surface so admins don't have to be exact when running /bt route addpoint.
         */
        public static RoutePoint fromLocation(Location location) {
            Location snapped = snapToWaterSurface(location);
            return new RoutePoint(snapped.getWorld().getName(), snapped.getX(), snapped.getY(), snapped.getZ());
        }

        private static Location snapToWaterSurface(Location location) {
            if (location == null || location.getWorld() == null) return location;
            World world = location.getWorld();
            int x = location.getBlockX();
            int z = location.getBlockZ();
            int startY = Math.min(world.getMaxHeight() - 1, Math.max(world.getMinHeight(), location.getBlockY() + 8));
            int minY = world.getMinHeight();

            for (int y = startY; y >= minY; y--) {
                Material type = world.getBlockAt(x, y, z).getType();
                Material above = y + 1 < world.getMaxHeight() ? world.getBlockAt(x, y + 1, z).getType() : Material.AIR;
                if (type == Material.WATER && (above == Material.AIR || above == Material.CAVE_AIR
                        || above == Material.VOID_AIR || above == Material.WATER)) {
                    int top = y;
                    while (top + 1 < world.getMaxHeight() && world.getBlockAt(x, top + 1, z).getType() == Material.WATER) {
                        top++;
                    }
                    return new Location(world, location.getX(), top + 0.62D, location.getZ(), location.getYaw(), 0f);
                }
            }
            return new Location(world, location.getX(), location.getY(), location.getZ(), location.getYaw(), 0f);
        }
    }

    private final String name;
    private final String originDock;
    private final String destinationDock;
    private final List<RoutePoint> waypoints;
    private Boat.Type boatType;
    private boolean enabled;

    public Route(String name, String originDock, String destinationDock, List<RoutePoint> waypoints,
                 Boat.Type boatType, boolean enabled) {
        this.name = name;
        this.originDock = originDock;
        this.destinationDock = destinationDock;
        this.waypoints = new ArrayList<>(waypoints);
        this.boatType = boatType;
        this.enabled = enabled;
    }

    public String getName() { return name; }
    public String getOriginDock() { return originDock; }
    public String getDestinationDock() { return destinationDock; }
    public Boat.Type getBoatType() { return boatType; }
    public void setBoatType(Boat.Type boatType) { this.boatType = boatType; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<RoutePoint> getWaypoints() { return Collections.unmodifiableList(waypoints); }

    public void addPoint(Location location) { waypoints.add(RoutePoint.fromLocation(location)); }
    public boolean removeLastPoint() {
        if (waypoints.isEmpty()) return false;
        waypoints.remove(waypoints.size() - 1);
        return true;
    }

    public boolean usesDock(String dockName) {
        return originDock.equalsIgnoreCase(dockName) || destinationDock.equalsIgnoreCase(dockName);
    }

    public boolean hasEnoughPoints() {
        return waypoints.size() >= 2;
    }

    public double totalDistance() {
        double total = 0.0;
        for (int i = 1; i < waypoints.size(); i++) {
            Location a = waypoints.get(i - 1).toLocation();
            Location b = waypoints.get(i).toLocation();
            if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) continue;
            if (!a.getWorld().equals(b.getWorld())) continue;
            total += a.distance(b);
        }
        return total;
    }

    public boolean isWorldConsistent() {
        String base = null;
        for (RoutePoint point : waypoints) {
            if (base == null) base = point.world();
            if (!base.equals(point.world())) return false;
        }
        return true;
    }

    /** First waypoint with its facing aimed at the second waypoint. */
    public Location getSpawnLocation() {
        if (waypoints.isEmpty()) return null;
        Location first = waypoints.get(0).toLocation();
        if (first == null) return null;
        if (waypoints.size() > 1) {
            Location next = waypoints.get(1).toLocation();
            if (next != null && next.getWorld() != null && first.getWorld() != null && first.getWorld().equals(next.getWorld())) {
                Vector dir = next.toVector().subtract(first.toVector());
                if (dir.lengthSquared() > 0.0001) {
                    first.setDirection(dir.normalize());
                }
            }
        }
        return first;
    }

    public void save(ConfigurationSection section) {
        section.set("origin", originDock);
        section.set("destination", destinationDock);
        section.set("boat-type", boatType.name());
        section.set("enabled", enabled);
        int i = 0;
        for (RoutePoint point : waypoints) {
            String base = "points." + i++;
            section.set(base + ".world", point.world());
            section.set(base + ".x", point.x());
            section.set(base + ".y", point.y());
            section.set(base + ".z", point.z());
        }
    }

    public static Route load(String name, ConfigurationSection section) {
        List<RoutePoint> points = new ArrayList<>();
        ConfigurationSection pointSection = section.getConfigurationSection("points");
        if (pointSection != null) {
            List<String> keys = new ArrayList<>(pointSection.getKeys(false));
            keys.sort((a, b) -> Integer.compare(Integer.parseInt(a), Integer.parseInt(b)));
            for (String key : keys) {
                ConfigurationSection p = pointSection.getConfigurationSection(key);
                if (p == null) continue;
                points.add(new RoutePoint(
                        p.getString("world"),
                        p.getDouble("x"),
                        p.getDouble("y"),
                        p.getDouble("z")
                ));
            }
        }
        Boat.Type type;
        try {
            type = Boat.Type.valueOf(section.getString("boat-type", "OAK").toUpperCase());
        } catch (IllegalArgumentException ex) {
            type = Boat.Type.OAK;
        }
        return new Route(
                name,
                section.getString("origin"),
                section.getString("destination"),
                points,
                type,
                section.getBoolean("enabled", true)
        );
    }
}
