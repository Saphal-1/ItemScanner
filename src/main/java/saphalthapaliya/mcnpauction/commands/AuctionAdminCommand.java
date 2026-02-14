
package saphalthapaliya.mcnpauction.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import saphalthapaliya.mcnpauction.MCNPAuction;
import saphalthapaliya.mcnpauction.models.Auction;

public class AuctionAdminCommand implements CommandExecutor {
    
    private final MCNPAuction plugin;
    
    public AuctionAdminCommand(MCNPAuction plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mcnpauction.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessageWithPrefix("no-permission"));
            return true;
        }
        
        if (args.length == 0) {
            sendAdminHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.getConfigManager().reloadConfigs();
                sender.sendMessage(plugin.getConfigManager().getMessageWithPrefix("reload-success"));
                break;
                
            case "remove":
                if (args.length < 2) {
                    sender.sendMessage(plugin.getConfigManager().getMessageWithPrefix("invalid-usage",
                        "{usage}", "/auctionadmin remove <id>"));
                    return true;
                }
                
                try {
                    int id = Integer.parseInt(args[1]);
                    Auction auction = plugin.getAuctionManager().getAuction(id);
                    
                    if (auction == null) {
                        sender.sendMessage(plugin.getConfigManager().getMessageWithPrefix("admin-no-auction"));
                        return true;
                    }
                    
                    plugin.getAuctionManager().removeAuction(id);
                    sender.sendMessage(plugin.getConfigManager().getMessageWithPrefix("admin-removed",
                        "{id}", String.valueOf(id),
                        "{player}", auction.getSellerName()));
                } catch (NumberFormatException e) {
                    sender.sendMessage(plugin.getConfigManager().getMessageWithPrefix("invalid-usage",
                        "{usage}", "/auctionadmin remove <id>"));
                }
                break;
                
            case "return":
                plugin.getAuctionManager().getExpiredAuctions().forEach(
                    auction -> plugin.getAuctionManager().expireAuction(auction)
                );
                sender.sendMessage(plugin.getConfigManager().getMessageWithPrefix("admin-returned"));
                break;
                
            default:
                sendAdminHelp(sender);
                break;
        }
        
        return true;
    }
    
    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage("§8§m          §r §6§lAuction Admin §8§m          ");
        sender.sendMessage("§e/auctionadmin reload §7- Reload configuration");
        sender.sendMessage("§e/auctionadmin remove <id> §7- Remove auction by ID");
        sender.sendMessage("§e/auctionadmin return §7- Return all expired auctions");
        sender.sendMessage("§8§m                                    ");
    }
  }
