
package com.dbsmp.trade;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class DBSMPTrade extends JavaPlugin implements Listener {

    private final Map<UUID, UUID> pendingTrades =
            new HashMap<UUID, UUID>();

    private final Map<UUID, TradeOffer> offers =
            new HashMap<UUID, TradeOffer>();

    private static final String SHOP_TITLE =
            ChatColor.DARK_PURPLE + "DBSMP Market";

    private static final String CONFIRM_TITLE =
            ChatColor.GREEN + "Confirm Trade";

    private static final String PREFIX =
            ChatColor.DARK_PURPLE + "[DBSMP] " + ChatColor.GRAY;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("DBSMPTrade enabled.");
    }

    @Override
    public void onDisable() {
        pendingTrades.clear();
        offers.clear();
    }

    private ItemStack menuItem(Material material, String name,
                               String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);

        java.util.List<String> lines =
                new java.util.ArrayList<String>();

        for (String line : lore) {
            lines.add(ChatColor.GRAY + line);
        }

        meta.setLore(lines);
        item.setItemMeta(meta);
        return item;
    }

    private void openMarket(Player player) {
        Inventory inv = Bukkit.createInventory(
                null, 27, SHOP_TITLE);

        inv.setItem(10, menuItem(Material.BREAD,
                "Buy 16 Bread - $150",
                "Click to buy"));

        inv.setItem(12, menuItem(Material.IRON_INGOT,
                "Buy 8 Iron Ingots - $400",
                "Click to buy"));

        inv.setItem(14, menuItem(Material.GOLD_INGOT,
                "Buy 8 Gold Ingots - $600",
                "Click to buy"));

        inv.setItem(16, menuItem(Material.DIAMOND,
                "Buy 1 Diamond - $1,000",
                "Click to buy"));

        player.openInventory(inv);
    }

    private void openConfirm(Player sender, Player target) {
        Inventory inv = Bukkit.createInventory(
                null, 9, CONFIRM_TITLE);

        inv.setItem(3, menuItem(Material.EMERALD,
                "Accept trade with " + target.getName(),
                "Click to accept"));

        inv.setItem(5, menuItem(Material.REDSTONE_BLOCK,
                "Cancel trade",
                "Click to cancel"));

        pendingTrades.put(sender.getUniqueId(),
                target.getUniqueId());

        target.sendMessage(PREFIX + sender.getName()
                + " wants to trade with you. Use /dbtrade "
                + sender.getName() + " to review.");

        sender.openInventory(inv);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        Player player = (Player) sender;
        String name = command.getName().toLowerCase();

        if (name.equals("dbmarket")) {
            openMarket(player);
            return true;
        }

        if (name.equals("dbsell")) {
            player.sendMessage(PREFIX
                    + "Selling is not connected to your economy yet.");
            player.sendMessage(PREFIX
                    + "Use the market after the economy connection is added.");
            return true;
        }

        if (name.equals("dbtrade")) {
            if (args.length != 1) {
                player.sendMessage(PREFIX
                        + "Usage: /dbtrade <player>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);

            if (target == null || target.equals(player)) {
                player.sendMessage(PREFIX
                        + "Choose another online player.");
                return true;
            }

            openConfirm(player, target);
            return true;
        }

        return false;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String title = event.getInventory().getTitle();

        if (SHOP_TITLE.equals(title)) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) {
                return;
            }

            int slot = event.getRawSlot();

            if (slot == 10) {
                buy(player, Material.BREAD, 16, 150);
            } else if (slot == 12) {
                buy(player, Material.IRON_INGOT, 8, 400);
            } else if (slot == 14) {
                buy(player, Material.GOLD_INGOT, 8, 600);
            } else if (slot == 16) {
                buy(player, Material.DIAMOND, 1, 1000);
            }

            return;
        }

        if (CONFIRM_TITLE.equals(title)) {
            event.setCancelled(true);

            UUID targetId = pendingTrades.get(player.getUniqueId());
            if (targetId == null) {
                player.closeInventory();
                player.sendMessage(PREFIX
                        + "That trade request has expired.");
                return;
            }

            if (event.getRawSlot() == 5) {
                pendingTrades.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(PREFIX + "Trade cancelled.");
                return;
            }

            if (event.getRawSlot() == 3) {
                Player target = Bukkit.getPlayer(targetId);

                if (target == null) {
                    player.closeInventory();
                    player.sendMessage(PREFIX
                            + "That player is offline.");
                    pendingTrades.remove(player.getUniqueId());
                    return;
                }

                pendingTrades.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(PREFIX
                        + "Trade request accepted. "
                        + "Item-for-item trading is not implemented yet.");
                target.sendMessage(PREFIX + player.getName()
                        + " accepted the request.");
            }
        }
    }

    private void buy(Player player, Material material,
                     int amount, double price) {
        player.sendMessage(PREFIX
                + "The market needs the DBSMPEconomy connection "
                + "before purchases can charge your balance.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        pendingTrades.remove(id);
        offers.remove(id);
        pendingTrades.values().remove(id);
    }

    private static class TradeOffer {
        private final UUID player;
        private final ItemStack item;

        private TradeOffer(UUID player, ItemStack item) {
            this.player = player;
            this.item = item;
        }
    }
}
