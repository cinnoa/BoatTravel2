package dev.bekololek.boattravel.managers;

import dev.bekololek.boattravel.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Player + global voyage statistics. Persisted to stats.yml with a self-describing
 * schema block so external tooling can render it without code knowledge.
 */
public class StatsManager {

    private final Main plugin;
    private final File statsFile;
    private final Map<UUID, PlayerStats> players = new HashMap<>();

    private static final List<StatDef> PLAYER_SCHEMA = List.of(
            new StatDef("voyages_completed",    "Voyages Completed",      "int",    null,       true),
            new StatDef("voyages_cancelled",    "Voyages Cancelled",      "int",    null,       true),
            new StatDef("distance_traveled",    "Distance Traveled",      "double", "blocks",   true),
            new StatDef("longest_voyage",       "Longest Voyage",         "double", "blocks",   true),
            new StatDef("money_spent",          "Money Spent",            "double", "currency", true),
            new StatDef("favorite_destination", "Favorite Destination",   "string", null,       false),
            new StatDef("destinations",         "Visits per Destination", "map",    null,       false)
    );

    private static final List<StatDef> GLOBAL_SCHEMA = List.of(
            new StatDef("total_voyages",            "Total Voyages",            "int",    null,       false),
            new StatDef("total_distance",           "Total Distance",           "double", "blocks",   false),
            new StatDef("total_money_spent",        "Total Money Spent",        "double", "currency", false),
            new StatDef("most_popular_destination", "Most Popular Destination", "string", null,       false),
            new StatDef("biggest_traveler",         "Biggest Traveler",         "string", null,       false),
            new StatDef("longest_voyage_ever",      "Longest Voyage Ever",      "double", "blocks",   false)
    );

    record StatDef(String key, String label, String type, String unit, boolean leaderboard) {}

    public StatsManager(Main plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
    }

    public static class PlayerStats {
        String name;
        int voyagesCompleted;
        int voyagesCancelled;
        double distanceTraveled;
        double longestVoyage;
        double moneySpent;
        final Map<String, Integer> destinations = new LinkedHashMap<>();

        String favoriteDestination() {
            String best = null;
            int max = 0;
            for (var entry : destinations.entrySet()) {
                if (entry.getValue() > max) {
                    max = entry.getValue();
                    best = entry.getKey();
                }
            }
            return best != null ? best : "None";
        }
    }

    public void load() {
        if (!statsFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(statsFile);

        ConfigurationSection playersSection = yaml.getConfigurationSection("players");
        if (playersSection == null) return;

        for (String uuidStr : playersSection.getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(uuidStr); }
            catch (IllegalArgumentException e) { continue; }

            ConfigurationSection sec = playersSection.getConfigurationSection(uuidStr);
            if (sec == null) continue;

            PlayerStats ps = new PlayerStats();
            ps.name = sec.getString("name", "Unknown");
            ps.voyagesCompleted = sec.getInt("voyages_completed", 0);
            ps.voyagesCancelled = sec.getInt("voyages_cancelled", 0);
            ps.distanceTraveled = sec.getDouble("distance_traveled", 0);
            ps.longestVoyage = sec.getDouble("longest_voyage", 0);
            ps.moneySpent = sec.getDouble("money_spent", 0);

            ConfigurationSection destSec = sec.getConfigurationSection("destinations");
            if (destSec != null) {
                for (String dock : destSec.getKeys(false)) {
                    ps.destinations.put(dock, destSec.getInt(dock, 0));
                }
            }
            players.put(uuid, ps);
        }
        plugin.getLogger().info("Loaded stats for " + players.size() + " players.");
    }

    public void save() { saveToYaml(true); }

    public void saveSync() { saveToYaml(false); }

    private void saveToYaml(boolean async) {
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("plugin", "BoatTravel");
        yaml.set("version", 1);

        for (StatDef def : PLAYER_SCHEMA) {
            String base = "schema.player." + def.key();
            yaml.set(base + ".label", def.label());
            yaml.set(base + ".type", def.type());
            if (def.unit() != null) yaml.set(base + ".unit", def.unit());
            yaml.set(base + ".leaderboard", def.leaderboard());
        }
        for (StatDef def : GLOBAL_SCHEMA) {
            String base = "schema.global." + def.key();
            yaml.set(base + ".label", def.label());
            yaml.set(base + ".type", def.type());
            if (def.unit() != null) yaml.set(base + ".unit", def.unit());
        }

        yaml.set("global.total_voyages",            players.values().stream().mapToInt(p -> p.voyagesCompleted).sum());
        yaml.set("global.total_distance",           players.values().stream().mapToDouble(p -> p.distanceTraveled).sum());
        yaml.set("global.total_money_spent",        players.values().stream().mapToDouble(p -> p.moneySpent).sum());
        yaml.set("global.most_popular_destination", computeMostPopularDestination());
        yaml.set("global.biggest_traveler",         computeBiggestTraveler());
        yaml.set("global.longest_voyage_ever",      players.values().stream().mapToDouble(p -> p.longestVoyage).max().orElse(0.0));

        for (var entry : players.entrySet()) {
            String path = "players." + entry.getKey().toString();
            PlayerStats ps = entry.getValue();
            yaml.set(path + ".name", ps.name);
            yaml.set(path + ".voyages_completed", ps.voyagesCompleted);
            yaml.set(path + ".voyages_cancelled", ps.voyagesCancelled);
            yaml.set(path + ".distance_traveled", ps.distanceTraveled);
            yaml.set(path + ".longest_voyage", ps.longestVoyage);
            yaml.set(path + ".money_spent", ps.moneySpent);
            yaml.set(path + ".favorite_destination", ps.favoriteDestination());
            for (var dest : ps.destinations.entrySet()) {
                yaml.set(path + ".destinations." + dest.getKey(), dest.getValue());
            }
        }

        if (async) {
            new BukkitRunnable() {
                @Override public void run() {
                    try { yaml.save(statsFile); }
                    catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Failed to save stats.yml", e); }
                }
            }.runTaskAsynchronously(plugin);
        } else {
            try { yaml.save(statsFile); }
            catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Failed to save stats.yml", e); }
        }
    }

    public void startAutoSave() {
        new BukkitRunnable() {
            @Override public void run() { save(); }
        }.runTaskTimer(plugin, 6000L, 6000L); // every 5 minutes
    }

    private PlayerStats getOrCreate(Player player) {
        PlayerStats ps = players.computeIfAbsent(player.getUniqueId(), k -> new PlayerStats());
        ps.name = player.getName();
        return ps;
    }

    public void recordVoyageComplete(Player player, String destinationName, double distance, double cost) {
        PlayerStats ps = getOrCreate(player);
        ps.voyagesCompleted++;
        ps.distanceTraveled += distance;
        ps.moneySpent += cost;
        if (distance > ps.longestVoyage) ps.longestVoyage = distance;
        ps.destinations.merge(destinationName, 1, Integer::sum);
    }

    public void recordVoyageCancel(Player player) {
        PlayerStats ps = getOrCreate(player);
        ps.voyagesCancelled++;
    }

    public void updateName(Player player) {
        PlayerStats ps = players.get(player.getUniqueId());
        if (ps != null) ps.name = player.getName();
    }

    public Object getPlayerStat(UUID uuid, String statName) {
        PlayerStats ps = players.get(uuid);
        if (ps == null) return statDefault(statName);
        return switch (statName.toLowerCase()) {
            case "voyages_completed"    -> ps.voyagesCompleted;
            case "voyages_cancelled"    -> ps.voyagesCancelled;
            case "distance_traveled"    -> (int) Math.round(ps.distanceTraveled);
            case "longest_voyage"       -> (int) Math.round(ps.longestVoyage);
            case "money_spent"          -> String.format("%.2f", ps.moneySpent);
            case "favorite_destination" -> ps.favoriteDestination();
            default -> 0;
        };
    }

    private Object statDefault(String statName) {
        return switch (statName.toLowerCase()) {
            case "favorite_destination" -> "None";
            case "money_spent"          -> "0.00";
            default -> 0;
        };
    }

    public Object getGlobalStat(String statName) {
        return switch (statName.toLowerCase()) {
            case "total_voyages"            -> players.values().stream().mapToInt(p -> p.voyagesCompleted).sum();
            case "total_distance"           -> (int) Math.round(players.values().stream().mapToDouble(p -> p.distanceTraveled).sum());
            case "total_money_spent"        -> String.format("%.2f", players.values().stream().mapToDouble(p -> p.moneySpent).sum());
            case "most_popular_destination" -> computeMostPopularDestination();
            case "biggest_traveler"         -> computeBiggestTraveler();
            case "longest_voyage_ever"      -> (int) Math.round(players.values().stream().mapToDouble(p -> p.longestVoyage).max().orElse(0));
            default -> 0;
        };
    }

    private String computeMostPopularDestination() {
        Map<String, Integer> totals = new HashMap<>();
        for (PlayerStats ps : players.values()) {
            for (var entry : ps.destinations.entrySet()) {
                totals.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        return totals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("None");
    }

    private String computeBiggestTraveler() {
        return players.values().stream()
                .max((a, b) -> Integer.compare(a.voyagesCompleted, b.voyagesCompleted))
                .map(ps -> ps.name)
                .orElse("None");
    }

    public List<Map.Entry<String, Number>> getTopPlayers(String statName, int limit) {
        List<Map.Entry<String, Number>> list = new ArrayList<>();
        for (var entry : players.entrySet()) {
            PlayerStats ps = entry.getValue();
            Number value = switch (statName.toLowerCase()) {
                case "voyages_completed" -> ps.voyagesCompleted;
                case "voyages_cancelled" -> ps.voyagesCancelled;
                case "distance_traveled" -> ps.distanceTraveled;
                case "longest_voyage"    -> ps.longestVoyage;
                case "money_spent"       -> ps.moneySpent;
                default -> 0;
            };
            list.add(new AbstractMap.SimpleEntry<>(ps.name, value));
        }
        list.sort((a, b) -> Double.compare(b.getValue().doubleValue(), a.getValue().doubleValue()));
        return list.subList(0, Math.min(limit, list.size()));
    }

    public static List<String> leaderboardStats() {
        return PLAYER_SCHEMA.stream().filter(StatDef::leaderboard).map(StatDef::key).toList();
    }

    public static String statLabel(String statName) {
        for (StatDef def : PLAYER_SCHEMA) if (def.key().equals(statName.toLowerCase())) return def.label();
        for (StatDef def : GLOBAL_SCHEMA) if (def.key().equals(statName.toLowerCase())) return def.label();
        return statName;
    }
}
