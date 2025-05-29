package bloody_mind.customLight;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Location;
import org.bukkit.ChatColor;

import java.util.*;

public class CustomLight extends JavaPlugin implements TabCompleter {
    private final Map<Integer, Integer> modelIdToLightLevel = new HashMap<>();
    private String reloadMessage;
    private String nopermission;
    private String nocommand;
    private final Map<UUID, Integer> lastModelId = new HashMap<>();
    private final Map<UUID, String> lastLocationKey = new HashMap<>();
    // Speichere Lichtblock-Positionen als Set für effiziente contains/remove-Operationen
    private final Map<UUID, Set<Location>> lightBlockLocations = new HashMap<>();
    private List<String> commandAliases = new ArrayList<>();
    private List<String> helpMessages = Arrays.asList(
            "/customlight reload - Konfiguration neu laden"
    );
    private int removalRadius = 1;
    private int updateInterval = 10;
    private boolean removeAllOnHelmetOff = true;
    private int maxLightBlocksPerPlayer = 3; // jetzt konfigurierbar

    @Override
    public void onEnable() {
        saveDefaultConfig();
        updateConfigAliases();
        loadConfigValues();
        registerCommands();

        startLightTask();
    }

    private void startLightTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    int modelId = -1;
                    ItemStack helmet = player.getInventory().getHelmet();
                    if (helmet != null && helmet.hasItemMeta() && helmet.getItemMeta().hasCustomModelData()) {
                        modelId = helmet.getItemMeta().getCustomModelData();
                    }
                    String locationKey = player.getWorld().getName() + ":"
                            + player.getLocation().getBlockX() + ":"
                            + player.getLocation().getBlockY() + ":"
                            + player.getLocation().getBlockZ();

                    Location lightLocation = player.getLocation().add(0, 2, 0).getBlock().getLocation();

                    if (modelIdToLightLevel.containsKey(modelId)) {
                        placeAndTrackLightBlock(player, modelId, lightLocation);

                        // Entferne zu weit entfernte Lichtblöcke
                        removeDistantLightBlocks(player, lightLocation);

                        // Begrenze die Anzahl der gespeicherten Lichtblöcke pro Spieler
                        trimPlayerLightBlocks(player);
                    } else {
                        // Helm abgenommen oder ungültig: entferne alle gespeicherten Lichtblöcke
                        if (removeAllOnHelmetOff) {
                            removeAllLightBlocks(player);
                        }
                    }

                    if (lastModelId.getOrDefault(player.getUniqueId(), -2) != modelId
                            || !locationKey.equals(lastLocationKey.get(player.getUniqueId()))) {
                        lastModelId.put(player.getUniqueId(), modelId);
                        lastLocationKey.put(player.getUniqueId(), locationKey);
                    }
                }
            }
        }.runTaskTimer(this, 0, updateInterval);
    }

    private void updateConfigAliases() {
        FileConfiguration config = getConfig();
        List<String> aliases = config.getStringList("command-aliases");
        Set<String> defaults = new LinkedHashSet<>(Collections.singletonList("clreload"));
        if (aliases == null || aliases.isEmpty()) {
            aliases = new ArrayList<>(defaults);
        } else {
            aliases.addAll(defaults);
            aliases = new ArrayList<>(new LinkedHashSet<>(aliases)); // Duplikate entfernen, Reihenfolge erhalten
        }
        config.set("command-aliases", aliases);
        saveConfig();
        commandAliases = aliases;
    }

    private void registerCommands() {
        PluginCommand cmd = getCommand("customlight");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setAliases(commandAliases); // Aliase NUR aus Config!
            cmd.setTabCompleter(this);
        }
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
        this.removalRadius = config.getInt("radius", 1);
        this.updateInterval = config.getInt("update-interval", 10);
        this.removeAllOnHelmetOff = config.getBoolean("remove-all-on-helmet-off", true);
        this.maxLightBlocksPerPlayer = config.getInt("max-light-blocks-per-player", 3);

        reloadMessage = config.getString("reload-message", "§a[Ethria-Light] Konfiguration neu geladen.");
        nopermission = config.getString("nopermission", "§a[Ethria-Light] Du hast keine Berechtigung für diesen Befehl.");
        nocommand = config.getString("nocommand", "§a[Ethria-Light] Unbekannter Befehl. Benutze /customlight help");
        commandAliases = config.getStringList("command-aliases");
        helpMessages = config.getStringList("help");
        if (helpMessages == null || helpMessages.isEmpty()) {
            helpMessages = Arrays.asList(
                    "/customlight reload - Konfiguration neu laden"
            );
        }
    }

    // Lichtblock setzen und tracken
    private void placeAndTrackLightBlock(Player player, int modelId, Location lightLocation) {
        int level = modelIdToLightLevel.get(modelId);
        Block lightBlock = lightLocation.getBlock();
        if (lightBlock.getType() == Material.AIR || lightBlock.getType() == Material.LIGHT) {
            BlockData data = Bukkit.createBlockData(Material.LIGHT);
            if (data instanceof Levelled) {
                ((Levelled) data).setLevel(level);
            }
            lightBlock.setBlockData(data, false);
        }
        Set<Location> locations = lightBlockLocations.computeIfAbsent(player.getUniqueId(), k -> new LinkedHashSet<>());
        locations.add(lightLocation);
    }

    // Entferne Lichtblöcke, die zu weit vom Spieler weg sind
    private void removeDistantLightBlocks(Player player, Location currentLightLoc) {
        Set<Location> locations = lightBlockLocations.get(player.getUniqueId());
        if (locations == null) return;
        Iterator<Location> it = locations.iterator();
        while (it.hasNext()) {
            Location loc = it.next();
            if (loc.distance(currentLightLoc) > removalRadius) {
                Block block = loc.getBlock();
                if (block.getType() == Material.LIGHT) {
                    block.setType(Material.AIR);
                }
                it.remove();
            }
        }
    }

    // Maximal erlaubte Lichtblöcke pro Spieler limitieren (FIFO-Prinzip)
    private void trimPlayerLightBlocks(Player player) {
        Set<Location> locations = lightBlockLocations.get(player.getUniqueId());
        if (locations == null) return;
        while (locations.size() > maxLightBlocksPerPlayer) {
            Location oldest = locations.iterator().next();
            Block block = oldest.getBlock();
            if (block.getType() == Material.LIGHT) {
                block.setType(Material.AIR);
            }
            locations.remove(oldest);
        }
    }

    // Entferne alle gespeicherten Lichtblöcke eines Spielers
    private void removeAllLightBlocks(Player player) {
        Set<Location> locations = lightBlockLocations.remove(player.getUniqueId());
        if (locations != null) {
            for (Location loc : locations) {
                Block block = loc.getBlock();
                if (block.getType() == Material.LIGHT) {
                    block.setType(Material.AIR);
                }
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("customlight") || commandAliases.contains(label.toLowerCase())) {
            if (!sender.hasPermission("customlight.use")) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', nopermission));
                return true;
            }

            if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("help"))) {
                for (String helpEntry : helpMessages) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', helpEntry));
                }
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("customlight.reload")) {
                    loadConfigValues();
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', reloadMessage));
                } else {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', nopermission));
                }
                return true;
            }
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', nocommand));
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if ((command.getName().equalsIgnoreCase("customlight") || commandAliases.contains(alias.toLowerCase())) && args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("reload");
            completions.add("help");
            return completions;
        }
        return Collections.emptyList();
    }
}