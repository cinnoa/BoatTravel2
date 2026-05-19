package dev.bekololek.boattravel.managers;

import dev.bekololek.boattravel.Main;
import dev.bekololek.boattravel.model.Dock;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Persists docks to docks.yml and indexes them by name and sign-block location.
 */
public class DockManager {

    private final Main plugin;
    private final File dataFile;
    private final Map<String, Dock> byName = new HashMap<>();
    private final Map<String, Dock> bySign = new HashMap<>();

    public DockManager(Main plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "docks.yml");
        load();
    }

    public void load() {
        byName.clear();
        bySign.clear();
        if (!dataFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        var section = cfg.getConfigurationSection("docks");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            Dock dock = Dock.load(key, section.getConfigurationSection(key));
            index(dock);
        }
        plugin.getLogger().info("Loaded " + byName.size() + " docks.");
    }

    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Dock dock : byName.values()) {
            dock.save(cfg.createSection("docks." + dock.getName()));
        }
        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save docks.yml: " + e.getMessage());
        }
    }

    private void index(Dock dock) {
        byName.put(dock.getName().toLowerCase(), dock);
        bySign.put(dock.signKey(), dock);
    }

    private void deindex(Dock dock) {
        byName.remove(dock.getName().toLowerCase());
        bySign.remove(dock.signKey());
    }

    public boolean isNameTaken(String name) {
        return byName.containsKey(name.toLowerCase());
    }

    public void register(Dock dock) {
        index(dock);
        save();
    }

    public void unregister(Dock dock) {
        deindex(dock);
        save();
    }

    public Dock getByName(String name) {
        return name == null ? null : byName.get(name.toLowerCase());
    }

    public Dock getBySignLocation(Location loc) {
        String key = loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
        return bySign.get(key);
    }

    public Collection<Dock> getAll() {
        return Collections.unmodifiableCollection(byName.values());
    }

    public boolean setHome(String dockName, Location location) {
        Dock dock = getByName(dockName);
        if (dock == null) return false;
        dock.setHome(location);
        save();
        return true;
    }
}
