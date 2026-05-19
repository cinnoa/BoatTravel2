package dev.bekololek.boattravel.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Physical voyage sign tracked by location. Holds only a pointer (the route
 * name) — breaking the sign does NOT delete the underlying route.
 */
public class TravelSign {

    private final String world;
    private final int bx;
    private final int by;
    private final int bz;
    private final String routeName;

    public TravelSign(String world, int bx, int by, int bz, String routeName) {
        this.world = world;
        this.bx = bx;
        this.by = by;
        this.bz = bz;
        this.routeName = routeName;
    }

    public String getWorld() { return world; }
    public int getBx() { return bx; }
    public int getBy() { return by; }
    public int getBz() { return bz; }
    public String getRouteName() { return routeName; }

    public Location getBlockLocation() {
        return new Location(Bukkit.getWorld(world), bx, by, bz);
    }

    public String key() {
        return world + "," + bx + "," + by + "," + bz;
    }

    public static String keyFor(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public void save(ConfigurationSection section) {
        section.set("world", world);
        section.set("x", bx);
        section.set("y", by);
        section.set("z", bz);
        section.set("route", routeName);
    }

    public static TravelSign load(ConfigurationSection s) {
        return new TravelSign(
                s.getString("world"),
                s.getInt("x"),
                s.getInt("y"),
                s.getInt("z"),
                s.getString("route")
        );
    }
}
