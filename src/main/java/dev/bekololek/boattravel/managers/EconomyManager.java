package dev.bekololek.boattravel.managers;

import dev.bekololek.boattravel.Main;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Thin wrapper around Vault. If Vault or an economy provider is missing, all
 * voyages are free and {@code charge}/{@code refund} are no-ops.
 */
public class EconomyManager {

    private final Main plugin;
    private Economy economy = null;

    public EconomyManager(Main plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    private void setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return;
        economy = rsp.getProvider();
        plugin.getLogger().info("Vault economy hooked: " + economy.getName());
    }

    public boolean isAvailable() { return economy != null; }

    public double calculateCost(double distance) {
        double perBlock = plugin.getConfig().getDouble("cost-per-block", 0.0);
        double flatCost = plugin.getConfig().getDouble("flat-cost", 0.0);
        return (perBlock * distance) + flatCost;
    }

    public boolean charge(Player player, double cost) {
        if (cost <= 0 || !isAvailable()) return true;
        if (!economy.has(player, cost)) return false;
        return economy.withdrawPlayer(player, cost).transactionSuccess();
    }

    public void refund(Player player, double amount) {
        if (amount <= 0 || !isAvailable()) return;
        economy.depositPlayer(player, amount);
    }

    public boolean canAfford(Player player, double cost) {
        if (cost <= 0 || !isAvailable()) return true;
        return economy.has(player, cost);
    }

    public String format(double amount) {
        if (economy != null) return economy.format(amount);
        return String.format("$%.2f", amount);
    }
}
