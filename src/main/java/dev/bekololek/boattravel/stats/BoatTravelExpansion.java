package dev.bekololek.boattravel.stats;

import dev.bekololek.boattravel.Main;
import dev.bekololek.boattravel.managers.StatsManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * Exposes BoatTravel stats to PlaceholderAPI.
 *
 *   %boattravel_stat_&lt;name&gt;%        — player's own stat
 *   %boattravel_global_&lt;name&gt;%      — server-wide stat
 *   %boattravel_top_&lt;stat&gt;_&lt;n&gt;%     — name of the player at rank n
 *   %boattravel_topvalue_&lt;stat&gt;_&lt;n&gt;% — that player's value
 */
public class BoatTravelExpansion extends PlaceholderExpansion {

    private final Main plugin;
    private final StatsManager statsManager;

    public BoatTravelExpansion(Main plugin, StatsManager statsManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
    }

    @Override public String getIdentifier() { return "boattravel"; }
    @Override public String getAuthor() { return "Cinnoa"; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null || params.isBlank()) return "";

        if (params.startsWith("stat_")) {
            if (player == null) return "";
            return String.valueOf(statsManager.getPlayerStat(player.getUniqueId(), params.substring("stat_".length())));
        }
        if (params.startsWith("global_")) {
            return String.valueOf(statsManager.getGlobalStat(params.substring("global_".length())));
        }
        if (params.startsWith("top_") || params.startsWith("topvalue_")) {
            boolean wantValue = params.startsWith("topvalue_");
            String rest = wantValue ? params.substring("topvalue_".length()) : params.substring("top_".length());
            int lastUnderscore = rest.lastIndexOf('_');
            if (lastUnderscore < 0) return "";
            String stat = rest.substring(0, lastUnderscore);
            int rank;
            try { rank = Integer.parseInt(rest.substring(lastUnderscore + 1)); }
            catch (NumberFormatException ex) { return ""; }
            var rows = statsManager.getTopPlayers(stat, Math.max(rank, 10));
            if (rank < 1 || rank > rows.size()) return "";
            var row = rows.get(rank - 1);
            if (!wantValue) return row.getKey();
            double v = row.getValue().doubleValue();
            return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.2f", v);
        }
        return "";
    }
}
