package bloody_mind.customLight;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CustomLight extends JavaPlugin {
    private final Map<Integer, Integer> modelIdToLightLevel = new HashMap<>();
    private String reloadMessage;
    private final Map<UUID, Integer> lastModelId = new HashMap<>();
    private final Map<UUID, String> lastLocationKey = new HashMap<>();
    private final Map<UUID, Location> lastLightBlockLocation = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    int modelId = -1;
                    ItemStack helmet = player.getInventory().getHelmet();
                    if (helmet != null && helmet.hasItemMeta() && helmet.getItemMeta().hasCustomModelData()) {
                        modelId = helmet.getItemMeta().getCustomModelData();
                    }
                    String locationKey = player.getWorld().getName() + ":" +
                            player.getLocation().getBlockX() + ":" +
                            player.getLocation().getBlockY() + ":" +
                            player.getLocation().getBlockZ();

                    Location lightLocation = player.getLocation().add(0, 2, 0).getBlock().getLocation();

                    if (modelIdToLightLevel.containsKey(modelId)) {
                        checkAndPlaceLight(player, modelId);
                        lastLightBlockLocation.put(player.getUniqueId(), lightLocation);
                    } else {
                        Location lastLoc = lastLightBlockLocation.remove(player.getUniqueId());
                        if (lastLoc != null) {
                            Block block = lastLoc.getBlock();
                            if (block.getType() == Material.LIGHT) {
                                block.setType(Material.AIR);
                            }
                        }
                    }

                    if (lastModelId.getOrDefault(player.getUniqueId(), -2) != modelId
                            || !locationKey.equals(lastLocationKey.get(player.getUniqueId()))) {
                        lastModelId.put(player.getUniqueId(), modelId);
                        lastLocationKey.put(player.getUniqueId(), locationKey);
                    }
                }
            }
        }.runTaskTimer(this, 0, 10);
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
                    }
                } catch (NumberFormatException e) {
                    // ignore invalid config entry
                }
            }
        }
        reloadMessage = config.getString("reload-message", "§a[Ethria-Light] Konfiguration neu geladen.");
    }

    private void checkAndPlaceLight(Player player, int modelId) {
        if (!modelIdToLightLevel.containsKey(modelId)) {
            return;
        }

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