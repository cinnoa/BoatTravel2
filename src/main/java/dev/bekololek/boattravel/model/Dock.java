package dev.bekololek.boattravel.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Persistent dock record. Knows the location of its sign and the actual home
 * (water) block where players arrive/depart.
 */
public class Dock {

    private final String name;
    private final String signWorld;
    private final int signX;
    private final int signY;
    private final int signZ;
    private double homeX;
    private double homeY;
    private double homeZ;
    private float homeYaw;
    private boolean homeSet;

    public Dock(String name, String signWorld, int signX, int signY, int signZ,
                double homeX, double homeY, double homeZ, float homeYaw, boolean homeSet) {
        this.name = name;
        this.signWorld = signWorld;
        this.signX = signX;
        this.signY = signY;
        this.signZ = signZ;
        this.homeX = homeX;
        this.homeY = homeY;
        this.homeZ = homeZ;
        this.homeYaw = homeYaw;
        this.homeSet = homeSet;
    }

    public String getName() { return name; }
    public String getSignWorld() { return signWorld; }
    public int getSignX() { return signX; }
    public int getSignY() { return signY; }
    public int getSignZ() { return signZ; }
    public double getHomeX() { return homeX; }
    public double getHomeY() { return homeY; }
    public double getHomeZ() { return homeZ; }
    public float getHomeYaw() { return homeYaw; }
    public boolean isHomeSet() { return homeSet; }

    public void setHome(Location location) {
        this.homeX = location.getX();
        this.homeY = location.getY();
        this.homeZ = location.getZ();
        this.homeYaw = location.getYaw();
        this.homeSet = true;
    }

    public Location getSignLocation() {
        return new Location(Bukkit.getWorld(signWorld), signX, signY, signZ);
    }

    public Location getHomeLocation() {
        var world = Bukkit.getWorld(signWorld);
        if (world == null || !homeSet) return null;
        return new Location(world, homeX, homeY, homeZ, homeYaw, 0f);
    }

    public String signKey() {
        return signWorld + "," + signX + "," + signY + "," + signZ;
    }

    public void save(ConfigurationSection section) {
        section.set("sign-world", signWorld);
        section.set("sign-x", signX);
        section.set("sign-y", signY);
        section.set("sign-z", signZ);
        section.set("home-set", homeSet);
        section.set("home-x", homeX);
        section.set("home-y", homeY);
        section.set("home-z", homeZ);
        section.set("home-yaw", homeYaw);
    }

    public static Dock load(String name, ConfigurationSection s) {
        // Older v8 stored home under "arr-*" keys; tolerate both for migration.
        boolean homeSet = s.getBoolean("home-set", s.contains("arr-x"));
        double x = s.contains("home-x") ? s.getDouble("home-x") : s.getDouble("arr-x");
        double y = s.contains("home-y") ? s.getDouble("home-y") : s.getDouble("arr-y");
        double z = s.contains("home-z") ? s.getDouble("home-z") : s.getDouble("arr-z");
        double yaw = s.contains("home-yaw") ? s.getDouble("home-yaw") : s.getDouble("arr-yaw");
        return new Dock(
                name,
                s.getString("sign-world"),
                s.getInt("sign-x"),
                s.getInt("sign-y"),
                s.getInt("sign-z"),
                x, y, z,
                (float) yaw,
                homeSet
        );
    }
}
