package dev.bekololek.boattravel;

import dev.bekololek.boattravel.commands.BoatTravelCommand;
import dev.bekololek.boattravel.listeners.SessionListener;
import dev.bekololek.boattravel.listeners.SignListener;
import dev.bekololek.boattravel.listeners.VoyageListener;
import dev.bekololek.boattravel.managers.DockManager;
import dev.bekololek.boattravel.managers.EconomyManager;
import dev.bekololek.boattravel.managers.RouteManager;
import dev.bekololek.boattravel.managers.StatsManager;
import dev.bekololek.boattravel.managers.TravelSignManager;
import dev.bekololek.boattravel.managers.VoyageManager;
import dev.bekololek.boattravel.stats.BoatTravelExpansion;
import dev.bekololek.boattravel.utils.MessageConfig;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private DockManager dockManager;
    private RouteManager routeManager;
    private TravelSignManager travelSignManager;
    private EconomyManager economyManager;
    private StatsManager statsManager;
    private VoyageManager voyageManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        MessageConfig.reload(this);

        dockManager = new DockManager(this);
        routeManager = new RouteManager(this);
        travelSignManager = new TravelSignManager(this);
        economyManager = new EconomyManager(this);
        statsManager = new StatsManager(this);
        statsManager.load();
        statsManager.startAutoSave();

        voyageManager = new VoyageManager(this, routeManager, dockManager,
                travelSignManager, economyManager, statsManager);

        // Refresh signs after a tick so the worlds are fully loaded.
        getServer().getScheduler().runTaskLater(this, () ->
                travelSignManager.refreshAll(routeManager, dockManager, economyManager), 20L);

        getServer().getPluginManager().registerEvents(
                new SignListener(this, dockManager, routeManager, travelSignManager, economyManager), this);
        getServer().getPluginManager().registerEvents(
                new VoyageListener(this, voyageManager, routeManager, dockManager, travelSignManager), this);
        getServer().getPluginManager().registerEvents(
                new SessionListener(this, voyageManager, statsManager), this);

        BoatTravelCommand commandHandler = new BoatTravelCommand(
                this, dockManager, routeManager, travelSignManager,
                economyManager, statsManager, voyageManager);
        var command = getCommand("boattravel");
        if (command != null) {
            command.setExecutor(commandHandler);
            command.setTabCompleter(commandHandler);
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new BoatTravelExpansion(this, statsManager).register();
                getLogger().info("PlaceholderAPI expansion registered.");
            } catch (Throwable ex) {
                getLogger().warning("Failed to register PlaceholderAPI expansion: " + ex.getMessage());
            }
        }

        getLogger().info("BoatTravel v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (voyageManager != null) voyageManager.abortAll();
        if (statsManager != null) statsManager.saveSync();
    }

    public DockManager getDockManager() { return dockManager; }
    public RouteManager getRouteManager() { return routeManager; }
    public TravelSignManager getTravelSignManager() { return travelSignManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public VoyageManager getVoyageManager() { return voyageManager; }
}
