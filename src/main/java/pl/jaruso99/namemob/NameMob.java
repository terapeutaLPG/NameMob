package pl.jaruso99.namemob;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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

public class NameMob extends JavaPlugin implements Listener, CommandExecutor {

    private NamespacedKey spawnedByKey;
    private NamespacedKey spawnedByUuidKey;
    private NamespacedKey spawnTimeKey;

    @Override
    public void onEnable() {
        spawnedByKey = new NamespacedKey(this, "spawned_by");
        spawnedByUuidKey = new NamespacedKey(this, "spawned_by_uuid");
        spawnTimeKey = new NamespacedKey(this, "spawn_time");

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
        if (item == null || item.getType() != Material.DIAMOND_PICKAXE) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getDisplayName().equals(ChatColor.BLUE + "moblog");
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        // sprawdzamy czy mob powstał z spawn egg
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }

        LivingEntity entity = event.getEntity();
        Player spawner = null;
        double nearest = 10.0;

        for (Player p : Bukkit.getOnlinePlayers()) {
            // pomijamy graczy z innych światów
            if (!p.getWorld().equals(entity.getWorld())) {
                continue;
            }

            double dist = p.getLocation().distance(entity.getLocation());

            if (dist < nearest) {
                nearest = dist;
                spawner = p;
            }
        }

        // jeśli nie znaleziono gracza
        if (spawner == null) {
            return;
        }

        // zapis danych w mobie
        PersistentDataContainer data = entity.getPersistentDataContainer();

        data.set(spawnedByKey, PersistentDataType.STRING, spawner.getName());
        data.set(spawnedByUuidKey, PersistentDataType.STRING, spawner.getUniqueId().toString());

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        data.set(spawnTimeKey, PersistentDataType.STRING, dtf.format(LocalDateTime.now()));
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (isMobTool(item)) {
            event.setCancelled(true);
            Entity entity = event.getRightClicked();
            PersistentDataContainer data = entity.getPersistentDataContainer();

            if (data.has(spawnedByKey, PersistentDataType.STRING)) {
                String owner = data.get(spawnedByKey, PersistentDataType.STRING);
                String date = data.get(spawnTimeKey, PersistentDataType.STRING);

                player.sendMessage(ChatColor.DARK_AQUA + "--- Mob Info ---");
                player.sendMessage(ChatColor.AQUA + "Spawned by " + ChatColor.WHITE + owner + ChatColor.AQUA + " at " + ChatColor.WHITE + date);
            } else {
                player.sendMessage(ChatColor.RED + "Ten mob nie ma zapisanego logu.");
            }
        }
    }
}
