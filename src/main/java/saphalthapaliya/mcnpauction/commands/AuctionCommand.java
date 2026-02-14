package saphalthapaliya.mcnpauction.commands;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import saphalthapaliya.mcnpauction.MCNPAuction;
import saphalthapaliya.mcnpauction.gui.AuctionGUI;
import saphalthapaliya.mcnpauction.utils.ItemUtils;
import saphalthapaliya.mcnpauction.utils.PermissionUtils;
import saphalthapaliya.mcnpauction.utils.TimeUtils;

import java.util.List;

public class AuctionCommand implements CommandExecutor {
    
    private final MCNPAuction plugin;
    
    public AuctionCommand(MCNPAuction plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessageWithPrefix("player-only"));
            return true;
        }
        
        Player player = (Player) sender;
        
        // No arguments - open GUI
        if (args.length == 0) {
            if (!player.hasPermission("mcnpauction.use")) {
                player.sendMessage(plugin.getConfigManager().getMessageWithPrefix("no-permission"));
                return true;
            }
            new AuctionGUI(plugin, player).open();
            return true;
        }
        
        // Sell command
        if (args[0].equalsIgnoreCase("sell")) {
            return handleSell(player, args);
        }
        
        // Help command
        if (args[0].equalsIgnoreCase("help")) {
            sendHelp(player);
            return true;
        }
        
        // Invalid command - open GUI
        new AuctionGUI(plugin, player).open();
        return true;
    }
    
    private boolean handleSell(Player player, String[] args) {
        if (!player.hasPermission("mcnpauction.sell")) {
            player.sendMessage(plugin.getConfigManager().getMessageWithPrefix("no-permission"));
            return true;
        }
        
        // Check usage
        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getMessageWithPrefix("invalid-usage",
                "{usage}", "/auction sell <price> [time]"));
            return true;
        }
        
        // Check item in hand
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(plugin.getConfigManager().getMessageWithPrefix("sell-no-item"));
            return true;
        }
        
        // Check if item is blocked
        List<String> blockedItems = plugin.getConfig().getStringList("blocked-items");
        if (blockedItems.contains(item.getType().name())) {
            player.sendMessage(plugin.getConfigManager().getMessageWithPrefix("sell-blocked-item"));
            return true;
        }
        
        // Parse price
        double price;
        try {
            price = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getConfigManager().getMessageWithPrefix("invalid-usage",
                "{usage}", "/auction sell <price> [time]"));
            return true;
        }
        
        // Validate price
        double minPrice = plugin.getConfig().getDouble("auction.min-price");
        double maxPrice = plugin.getConfig().getDouble("auction.max-price");
        if (price < minPrice || price > maxPrice) {
            player.sendMessage(plugin.getConfigManager().getMessageWithPrefix("sell-invalid-price",
                "{min}", String.format("%.2f", minPrice),
                "{max}", String.format("%.2f", maxPrice)));
            return true;
        }
        
        // Parse duration (optional)
        long duration = plugin.getConfig().getLong("auction.duration.default");
        if (args.length >= 3) {
            try {
                duration = TimeUtils.parseDuration(args[2]);
            } catch (IllegalArgumentException e) {
                player.sendMessage(plugin.getConfigManager().getMessageWithPrefix("invalid-usage",
                    "{usage}", "/auction sell <price> [time] (e.g., 1h, 30m, 1d)"));
                return true;
            }
        }
        
        // Validate duration
        long minDuration = plugin.getConfig().getLong("auction.duration.min");
        long maxDuration = plugin.getConfig().getLong("auction.duration.max");
        if (duration < minDuration || duration > maxDuration) {
            player.sendMessage(plugin.getConfigManager().getMessageWithPrefix("invalid-usage",
                "{usage}", "Duration must be between " + TimeUtils.formatTime(minDuration * 1000) + " and " + TimeUtils.formatTime(maxDuration * 1000)));
            return true;
        }
        
        // Check auction limit
        int limit = PermissionUtils.getAuctionLimit(player);
        int currentCount = plugin.getAuctionManager().getAuctionCount(player.getUniqueId());
        if (currentCount >= limit && limit != -1) {
            player.sendMessage(plugin.getConfigManager().getMessageWithPrefix("sell-limit-reached",
                "{limit}", String.valueOf(limit)));
            return true;
        }
        
        // Check cooldown
        if (!player.hasPermission("mcnpauction.nocooldown")) {
            if (plugin.getCooldownManager().hasCooldown(player.getUniqueId())) {
                long remaining = plugin.getCooldownManager().getRemainingCooldown(player.getUniqueId());
                player.sendMessage(plugin.getConfigManager().getMessageWithPrefix("sell-cooldown",
                    "{time}", TimeUtils.formatTime(remaining * 1000)));
                return true;
            }
        }
        
        // Calculate tax
        double tax = 0;
        if (!player.hasPermission("mcnpauction.notax")) {
            double taxRate = plugin.getConfig().getDouble("auction.tax-percentage");
            tax = price * taxRate;
            
            // Check if player has enough money for tax
            if (!plugin.getEconomy().has(player, tax)) {
                player.sendMessage(plugin.getConfigManager().getMessageWithPrefix("buy-insufficient-funds",
                    "{price}", String.format("%.2f", tax)));
                return true;
            }
            
            // Withdraw tax
            plugin.getEconomy().withdrawPlayer(player, tax);
        }
        
        // Clone item and remove from inventory
        ItemStack auctionItem = item.clone();
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        
        // Create auction
        plugin.getAuctionManager().createAuction(player, auctionItem, price, duration);
        
        // Set cooldown
        int cooldownSeconds = plugin.getConfig().getInt("auction.sell-cooldown");
        if (!player.hasPermission("mcnpauction.nocooldown")) {
            plugin.getCooldownManager().setCooldown(player.getUniqueId(), cooldownSeconds);
        }
        
        // Send success message
        player.sendMessage(plugin.getConfigManager().getMessageWithPrefix("sell-success",
            "{item}", ItemUtils.getItemName(auctionItem),
            "{amount}", String.valueOf(auctionItem.getAmount()),
            "{price}", String.format("%.2f", price),
            "{time}", TimeUtils.formatTime(duration * 1000)));
        
        if (tax > 0) {
            double taxPercent = plugin.getConfig().getDouble("auction.tax-percentage") * 100;
            player.sendMessage(plugin.getConfigManager().getMessage("sell-tax-notice",
                "{tax}", String.format("%.2f", tax),
                "{percent}", String.format("%.1f", taxPercent)));
        }
        
        // Play sound
        if (plugin.getConfig().getBoolean("gui.sounds.enabled")) {
            try {
                Sound sound = Sound.valueOf(plugin.getConfig().getString("gui.sounds.on-sell"));
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException ignored) {}
        }
        
        return true;
    }
    
    private void sendHelp(Player player) {
        player.sendMessage(plugin.getConfigManager().getMessage("help-header"));
        player.sendMessage(plugin.getConfigManager().getMessage("help-open"));
        player.sendMessage(plugin.getConfigManager().getMessage("help-sell"));
        if (player.hasPermission("mcnpauction.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("help-admin"));
        }
        player.sendMessage(plugin.getConfigManager().getMessage("help-footer"));
    }
    }
            
