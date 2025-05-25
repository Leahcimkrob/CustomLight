package Bloody_Mind.package.CustomLight;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CustomLight extends JavaPlugin {
    private final Map<Integer, Integer> modelIdToLightLevel = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadModelIds();

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkAndPlaceLight(player);
                }
            }
        }.runTaskTimer(this, 0, 20);
    }

    private void loadModelIds() {
        FileConfiguration config = getConfig();
        modelIdToLightLevel.clear();
        if (config.isConfigurationSection("glowing_items")) {
            Set<String> keys = config.getConfigurationSection("glowing_items").getKeys(false);
            for (String key : keys) {
                try {
                    int id = Integer.parseInt(key);
                    int level = config.getInt("glowing_items." + key);
                    if (level >= 0 && level <= 15) {
                        modelIdToLightLevel.put(id, level);
                    } else {
                        getLogger().warning("Ungültiger Lichtlevel für Model-ID " + key + ": " + level);
                    }
                } catch (NumberFormatException e) {
                    getLogger().warning("Ungültige Model-ID in config: " + key);
                }
            }
        }
        getLogger().info("Registrierte leuchtende Items: " + modelIdToLightLevel);
    }

    private void checkAndPlaceLight(Player player) {
        if (player.getInventory().getHelmet() == null) return;

        CustomStack customStack = CustomStack.byItemStack(player.getInventory().getHelmet());
        if (customStack == null) return;

        int modelId = customStack.getCustomModelData();
        if (!modelIdToLightLevel.containsKey(modelId)) return;

        int level = modelIdToLightLevel.get(modelId);
        Block lightBlock = player.getLocation().add(0, 2, 0).getBlock();
        if (lightBlock.getType() == Material.AIR || lightBlock.getType() == Material.LIGHT) {
            BlockData data = Bukkit.createBlockData(Material.LIGHT);
            if (data instanceof Levelled) {
                ((Levelled) data).setLevel(level);
            }
            lightBlock.setBlockData(data, false);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("clreload")) {
            reloadConfig();
            loadModelIds();
            sender.sendMessage("§a[Ethria-Light] Konfiguration neu geladen.");
            return true;
        }
        return false;
    }
}
