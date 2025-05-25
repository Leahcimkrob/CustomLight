package bloody_mind.customLight;

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
import java.util.UUID;

public class CustomLight extends JavaPlugin {
    private final Map<Integer, Integer> modelIdToLightLevel = new HashMap<>();
    private String reloadMessage;
    // Für Performance: Letzte Positionen und Modell-IDs der Spieler merken
    private final Map<UUID, Integer> lastModelId = new HashMap<>();
    private final Map<UUID, String> lastLocationKey = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        // Task: Nur Spieler mit leuchtendem Helm und Positions-/Helmänderung werden geprüft
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    CustomStack customStack = CustomStack.byItemStack(player.getInventory().getHelmet());
                    int modelId = -1;
                    if (customStack != null) {
                        try {
                            modelId = Integer.parseInt(customStack.getId());
                        } catch (NumberFormatException e) {
                        // ID war doch kein int, ggf. Fehlerbehandlung oder Logging
                        modelId = -1;
                        }
                    }
                    String locationKey = player.getWorld().getName() + ":" + player.getLocation().getBlockX() + ":" + player.getLocation().getBlockY() + ":" + player.getLocation().getBlockZ();
                    if (lastModelId.getOrDefault(player.getUniqueId(), -2) != modelId || !locationKey.equals(lastLocationKey.get(player.getUniqueId()))) {
                        lastModelId.put(player.getUniqueId(), modelId);
                        lastLocationKey.put(player.getUniqueId(), locationKey);
                        checkAndPlaceLight(player, modelId);
                    }
                }
            }
        }.runTaskTimer(this, 0, 10); // 2x pro Sekunde (kann weiter verringert werden)
    }

    private void loadConfigValues() {
        reloadConfig();
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
        reloadMessage = config.getString("reload-message", "§a[Ethria-Light] Konfiguration neu geladen.");
        getLogger().info("Registrierte leuchtende Items: " + modelIdToLightLevel);
    }

    private void checkAndPlaceLight(Player player, int modelId) {
        getLogger().info("checkAndPlaceLight für " + player.getName() + ", modelId=" + modelId);
        if (!modelIdToLightLevel.containsKey(modelId)) {
            getLogger().info("modelId nicht in Map: " + modelId);
            return;
        }

        int level = modelIdToLightLevel.get(modelId);
        Block lightBlock = player.getLocation().add(0, 2, 0).getBlock();
        getLogger().info("Setze Licht an " + lightBlock.getLocation() + " auf Level " + level + ", vorher: " + lightBlock.getType());
        if (lightBlock.getType() == Material.AIR || lightBlock.getType() == Material.LIGHT) {
            BlockData data = Bukkit.createBlockData(Material.LIGHT);
            if (data instanceof Levelled) {
               ((Levelled) data).setLevel(level);
            }
            lightBlock.setBlockData(data, false);
            getLogger().info("Block an " + lightBlock.getLocation() + " auf LIGHT gesetzt.");
        } else {
            getLogger().info("Block an " + lightBlock.getLocation() + " ist kein AIR/LIGHT sondern " + lightBlock.getType());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("clreload")) {
            if (sender.hasPermission("customlight.reload")) {
                loadConfigValues();
                sender.sendMessage(reloadMessage);
            } else {
                sender.sendMessage("§cDu hast keine Berechtigung für diesen Befehl.");
            }
            return true;
        }
        return false;
    }
}