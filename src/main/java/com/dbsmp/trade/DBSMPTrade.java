package com.dbsmp.trade;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class DBSMPTrade extends JavaPlugin implements Listener {
    private static final String PREFIX =
            ChatColor.DARK_PURPLE + "[DBSMP] " + ChatColor.GRAY;
    private static final String TITLE =
            ChatColor.DARK_PURPLE + "DBSMP Market";

    private Plugin economy;
    private Method balanceMethod;
    private Method withdrawMethod;
    private Method depositMethod;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        connectEconomy();
        getLogger().info("DBSMPTrade enabled.");
    }

    private void connectEconomy() {
        economy = Bukkit.getPluginManager().getPlugin("DBSMPEconomy");
        if (economy == null || !economy.isEnabled()) {
            getLogger().warning("DBSMPEconomy not found; market is disabled.");
            return;
        }

        try {
            Class<?> c = economy.getClass();
            balanceMethod = c.getMethod("getPlayerBalance", Player.class);
            withdrawMethod = c.getMethod(
                    "withdrawPlayerMoney", Player.class, double.class);
            depositMethod = c.getMethod(
                    "depositPlayerMoney", Player.class, double.class);
            getLogger().info("Connected to DBSMPEconomy.");
        } catch (Exception e) {
            economy = null;
            getLogger().severe("Economy methods are missing. Check DBSMPEconomy.");
        }
    }

    private boolean ready() {
        return economy != null && economy.isEnabled()
                && balanceMethod != null
                && withdrawMethod != null && depositMethod != null;
    }

    private double balance(Player p) {
        try {
            return ((Number) balanceMethod.invoke(economy, p)).doubleValue();
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean withdraw(Player p, double amount) {
        try {
            return Boolean.TRUE.equals(
                    withdrawMethod.invoke(economy, p, amount));
        } catch (Exception e) {
            return false;
        }
    }

    private void deposit(Player p, double amount) {
        try {
            depositMethod.invoke(economy, p, amount);
        } catch (Exception e) {
            getLogger().warning("Could not deposit sale money.");
        }
    }

    private String cash(double n) {
        return "$" + String.format(java.util.Locale.US, "%,.0f", n);
    }

    private ItemStack item(Material type, String name) {
        ItemStack stack = new ItemStack(type);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        stack.setItemMeta(meta);
        return stack;
    }

    private void openMarket(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        inv.setItem(10, item(Material.BREAD, "Buy 16 Bread - $150"));
        inv.setItem(12, item(Material.IRON_INGOT, "Buy 8 Iron - $400"));
        inv.setItem(14, item(Material.GOLD_INGOT, "Buy 8 Gold - $600"));
        inv.setItem(16, item(Material.DIAMOND, "Buy 1 Diamond - $1,000"));
        p.openInventory(inv);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd,
                             String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        Player p = (Player) sender;
        String name = cmd.getName().toLowerCase();

        if (name.equals("dbmarket")) {
            openMarket(p);
            return true;
        }

        if (name.equals("dbsell")) {
            if (!ready()) {
                p.sendMessage(PREFIX + ChatColor.RED
                        + "Economy connection unavailable.");
                return true;
            }

            ItemStack hand = p.getItemInHand();
            if (hand == null || hand.getType() == Material.AIR) {
                p.sendMessage(PREFIX + "Hold an item to sell.");
                return true;
            }

            double unitPrice = sellPrice(hand.getType());
            if (unitPrice <= 0) {
                p.sendMessage(PREFIX + "The market doesn't buy that item.");
                return true;
            }

            Material soldType = hand.getType();
            int amount = hand.getAmount();
            double total = unitPrice * amount;

            p.setItemInHand(null);
            deposit(p, total);
            p.sendMessage(PREFIX + "Sold " + amount + " "
                    + soldType.name().toLowerCase() + " for "
                    + cash(total) + ". Balance: " + cash(balance(p)));
            return true;
        }

        if (name.equals("dbtrade")) {
            p.sendMessage(PREFIX
                    + "Player-to-player item trading isn't implemented yet.");
            return true;
        }

        return false;
    }

    private double sellPrice(Material m) {
        if (m == Material.BREAD) return 3;
        if (m == Material.IRON_INGOT) return 25;
        if (m == Material.GOLD_INGOT) return 35;
        if (m == Material.DIAMOND) return 100;
        return 0;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!TITLE.equals(e.getInventory().getTitle())) return;
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player)) return;
        if (e.getRawSlot() < 0
                || e.getRawSlot() >= e.getInventory().getSize()) return;

        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();

        if (slot == 10) buy(p, Material.BREAD, 16, 150);
        else if (slot == 12) buy(p, Material.IRON_INGOT, 8, 400);
        else if (slot == 14) buy(p, Material.GOLD_INGOT, 8, 600);
        else if (slot == 16) buy(p, Material.DIAMOND, 1, 1000);
    }

    private void buy(Player p, Material material, int amount, double price) {
        if (!ready()) {
            p.sendMessage(PREFIX + ChatColor.RED
                    + "Economy connection unavailable.");
            return;
        }

        double current = balance(p);
        if (current < price) {
            p.sendMessage(PREFIX + "You need "
                    + cash(price - current) + " more.");
            return;
        }

        if (!withdraw(p, price)) {
            p.sendMessage(PREFIX + "Payment failed; no items were given.");
            return;
        }

        java.util.HashMap<Integer, ItemStack> leftovers =
                p.getInventory().addItem(new ItemStack(material, amount));
        for (ItemStack left : leftovers.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }

        p.sendMessage(PREFIX + "Bought " + amount + " "
                + material.name().toLowerCase() + " for " + cash(price)
                + ". Balance: " + cash(balance(p)));
        p.closeInventory();
    }
}
