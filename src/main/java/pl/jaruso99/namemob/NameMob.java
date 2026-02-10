package pl.jaruso99.namemob;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class NameMob extends JavaPlugin implements Listener, CommandExecutor {

    private NamespacedKey ownerKey;
    private NamespacedKey dateKey;
    private NamespacedKey locationKey;

    private NamespacedKey typeKey;

    @Override
    public void onEnable() {
        ownerKey = new NamespacedKey(this, "owner");
        dateKey = new NamespacedKey(this, "date");
        locationKey = new NamespacedKey(this, "location");
        typeKey = new NamespacedKey(this, "type");

        getCommand("mob").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Ta komenda jest tylko dla graczy!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(ChatColor.DARK_AQUA + "--- NameMob Help ---");
            player.sendMessage(ChatColor.AQUA + "/mob tool " + ChatColor.WHITE + "- Daje narzędzie do sprawdzania mobów");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("tool")) {
            if (!player.hasPermission("namemob.admin")) {
                player.sendMessage(ChatColor.RED + "Nie masz uprawnień!");
                return true;
            }

            player.getInventory().addItem(getMobTool());
            player.sendMessage(ChatColor.BLUE + "Otrzymałeś moblog tool!");
            return true;
        }

        player.sendMessage(ChatColor.RED + "Użycie: /mob tool");
        return true;
    }

    private ItemStack getMobTool() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.BLUE + "moblog");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Użyj na mobie, aby sprawdzić");
            lore.add(ChatColor.GRAY + "kto i kiedy go zrespił.");
            meta.setLore(lore);
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (isMobTool(item)) {
            event.getItemDrop().remove();
            event.getPlayer().sendMessage(ChatColor.RED + "Twój moblog tool został usunięty!");
        }
    }

    private boolean isMobTool(ItemStack item) {
        if (item == null || item.getType() != Material.DIAMOND_PICKAXE) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getDisplayName().equals(ChatColor.BLUE + "moblog");
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            LivingEntity entity = event.getEntity();
            
            // We need to find the player who used the egg. 
            // Since CreatureSpawnEvent doesn't directly provide the player for eggs in all versions,
            // we look for the nearest player within a small radius.
            Player spawner = null;
            double nearest = 10.0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                double dist = p.getLocation().distance(entity.getLocation());
                if (dist < nearest) {
                    nearest = dist;
                    spawner = p;
                }
            }

            if (spawner != null) {
                PersistentDataContainer data = entity.getPersistentDataContainer();
                data.set(ownerKey, PersistentDataType.STRING, spawner.getName());
                data.set(typeKey, PersistentDataType.STRING, entity.getType().name());
                
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
                data.set(dateKey, PersistentDataType.STRING, dtf.format(LocalDateTime.now()));
                
                String loc = String.format("X: %d, Y: %d, Z: %d, World: %s", 
                    entity.getLocation().getBlockX(), 
                    entity.getLocation().getBlockY(), 
                    entity.getLocation().getBlockZ(),
                    entity.getWorld().getName());
                data.set(locationKey, PersistentDataType.STRING, loc);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (isMobTool(item)) {
            event.setCancelled(true);
            Entity entity = event.getRightClicked();
            PersistentDataContainer data = entity.getPersistentDataContainer();

            if (data.has(ownerKey, PersistentDataType.STRING)) {
                String owner = data.get(ownerKey, PersistentDataType.STRING);
                String date = data.get(dateKey, PersistentDataType.STRING);
                String loc = data.get(locationKey, PersistentDataType.STRING);
                String type = data.has(typeKey, PersistentDataType.STRING) ? data.get(typeKey, PersistentDataType.STRING) : entity.getType().name();

                player.sendMessage(ChatColor.DARK_AQUA + "--- Mob Info ---");
                player.sendMessage(ChatColor.AQUA + "Rodzaj moba: " + ChatColor.WHITE + type);
                player.sendMessage(ChatColor.AQUA + "Zrespiony przez: " + ChatColor.WHITE + owner);
                player.sendMessage(ChatColor.AQUA + "Data: " + ChatColor.WHITE + date);
                player.sendMessage(ChatColor.AQUA + "Lokalizacja: " + ChatColor.WHITE + loc);
            } else {
                player.sendMessage(ChatColor.RED + "Ten mob nie ma zapisanego logu.");
            }
        }
    }
}
