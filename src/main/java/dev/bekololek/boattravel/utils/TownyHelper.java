package dev.bekololek.boattravel.utils;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflection-based Towny integration. Towny is an optional dep; if missing we
 * return empty strings. Calls are protected from Towny version drift by using
 * reflection lookups and silently falling back when a method is unavailable.
 */
public final class TownyHelper {

    private static final Set<String> loggedWorldFailures = ConcurrentHashMap.newKeySet();

    private TownyHelper() {}

    public static boolean hasTowny(Plugin plugin) {
        Plugin towny = plugin.getServer().getPluginManager().getPlugin("Towny");
        return towny != null && towny.isEnabled();
    }

    /** Returns the configured "Wilderness" text if Towny is present but no town at this location. */
    public static String getTownNameOrWilderness(Plugin plugin, Location location) {
        if (!hasTowny(plugin)) return "";
        String town = getTownName(plugin, location);
        return town.isBlank() ? MessageConfig.signWildernessText : town;
    }

    public static String getTownName(Plugin plugin, Location location) {
        Plugin towny = plugin.getServer().getPluginManager().getPlugin("Towny");
        if (towny == null || !towny.isEnabled()) return "";
        if (location == null || location.getWorld() == null) return "";
        try {
            Class<?> apiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);

            String name = tryTownNameMethod(apiClass, api, location);
            if (!name.isBlank()) return name;

            Object town = tryTownLookup(apiClass, api, location);
            if (town != null) {
                Method getName = town.getClass().getMethod("getName");
                Object result = getName.invoke(town);
                return result == null ? "" : String.valueOf(result);
            }
        } catch (Throwable throwable) {
            String key = location.getWorld().getName();
            if (loggedWorldFailures.add(key)) {
                plugin.getLogger().warning("Towny was detected but BoatTravel could not resolve the town name in world '"
                        + key + "'. Signs will leave the town line blank until a compatible Towny API method is found. Root cause: "
                        + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            }
        }
        return "";
    }

    private static String tryTownNameMethod(Class<?> apiClass, Object api, Location location) {
        try {
            Method method = apiClass.getMethod("getTownName", Location.class);
            Object result = method.invoke(api, location);
            return result == null ? "" : String.valueOf(result);
        } catch (Throwable ignored) {
        }
        try {
            Method method = apiClass.getMethod("getTownName", Block.class);
            Object result = method.invoke(api, location.getBlock());
            return result == null ? "" : String.valueOf(result);
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static Object tryTownLookup(Class<?> apiClass, Object api, Location location) {
        for (Class<?> param : new Class<?>[]{Location.class, Block.class}) {
            try {
                Method getTown = apiClass.getMethod("getTown", param);
                return getTown.invoke(api, param == Location.class ? location : location.getBlock());
            } catch (Throwable ignored) {
            }
            try {
                Method getTownBlock = apiClass.getMethod("getTownBlock", param);
                Object townBlock = getTownBlock.invoke(api, param == Location.class ? location : location.getBlock());
                if (townBlock == null) continue;
                try {
                    Method hasTown = townBlock.getClass().getMethod("hasTown");
                    Object result = hasTown.invoke(townBlock);
                    if (result instanceof Boolean b && !b) return null;
                } catch (Throwable ignored) {
                }
                Method getTown = townBlock.getClass().getMethod("getTown");
                return getTown.invoke(townBlock);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
