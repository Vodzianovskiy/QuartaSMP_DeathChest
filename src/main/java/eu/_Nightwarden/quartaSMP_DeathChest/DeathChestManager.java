package eu._Nightwarden.quartaSMP_DeathChest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DeathChestManager {

    private final Plugin plugin;
    private final File dataFile;
    private final Map<UUID, DeathChest> deathChests = new ConcurrentHashMap<>();
    private final Map<UUID, org.bukkit.inventory.Inventory> openInventories = new ConcurrentHashMap<>();
    private final Map<LocationKey, UUID> chestsByLocation = new ConcurrentHashMap<>();
    private final Set<UUID> lockedDeathChests = ConcurrentHashMap.newKeySet();
    private final NamespacedKey pdcKey;
    private BukkitTask pendingSaveTask;
    private HologramManager hologramManager;

    public DeathChestManager(Plugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "deathchests.yml");
        this.pdcKey = new NamespacedKey(plugin, "quarta_deathchest_id");
    }

    public void setHologramManager(HologramManager hologramManager) {
        this.hologramManager = hologramManager;
    }

    public NamespacedKey getPdcKey() {
        return pdcKey;
    }

    public Map<UUID, org.bukkit.inventory.Inventory> getOpenInventories() {
        return openInventories;
    }

    // ─────────────────────────────────────────────
    // CRUD операции
    // ─────────────────────────────────────────────

    public void addDeathChest(DeathChest deathChest) {
        deathChests.put(deathChest.getId(), deathChest);
        chestsByLocation.put(LocationKey.from(deathChest), deathChest.getId());
    }

    public void removeDeathChest(UUID id) {
        DeathChest removed = deathChests.remove(id);
        if (removed != null) {
            chestsByLocation.remove(LocationKey.from(removed));
            lockedDeathChests.remove(id);
        }
    }

    public DeathChest getDeathChest(UUID id) {
        return deathChests.get(id);
    }

    public DeathChest getDeathChestByLocation(Location location) {
        if (location == null || location.getWorld() == null) return null;
        UUID id = chestsByLocation.get(LocationKey.from(location));
        return id == null ? null : deathChests.get(id);
    }

    public List<DeathChest> getDeathChestsByOwner(UUID ownerUUID) {
        List<DeathChest> result = new ArrayList<>();
        for (DeathChest dc : deathChests.values()) {
            if (dc.getOwnerUUID().equals(ownerUUID)) {
                result.add(dc);
            }
        }
        return result;
    }

    public Collection<DeathChest> getAllDeathChests() {
        return deathChests.values();
    }

    public boolean tryLockDeathChest(UUID id) {
        return lockedDeathChests.add(id);
    }

    public void unlockDeathChest(UUID id) {
        lockedDeathChests.remove(id);
    }

    public boolean isLocked(UUID id) {
        return lockedDeathChests.contains(id);
    }

    // ─────────────────────────────────────────────
    // PDC теги на блоке
    // ─────────────────────────────────────────────

    public void setBlockTag(Location location, UUID deathChestId) {
        var block = location.getBlock();
        if (block.getState() instanceof TileState tileState) {
            var pdc = tileState.getPersistentDataContainer();
            pdc.set(pdcKey, PersistentDataType.STRING, deathChestId.toString());
            tileState.update();
        }
    }

    public UUID getBlockTag(Location location) {
        var block = location.getBlock();
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) return null;
        if (block.getState() instanceof TileState tileState) {
            var pdc = tileState.getPersistentDataContainer();
            String idStr = pdc.get(pdcKey, PersistentDataType.STRING);
            if (idStr != null) {
                try {
                    return UUID.fromString(idStr);
                } catch (IllegalArgumentException e) {
                    // ignore
                }
            }
        }
        // Fallback: если PDC тег не найден, ищем по координатам в deathChests
        DeathChest dc = getDeathChestByLocation(location);
        if (dc != null) {
            // Восстанавливаем PDC тег
            setBlockTag(location, dc.getId());
            return dc.getId();
        }
        return null;
    }

    public void removeBlockTag(Location location) {
        var block = location.getBlock();
        if (block.getState() instanceof TileState tileState) {
            var pdc = tileState.getPersistentDataContainer();
            pdc.remove(pdcKey);
            tileState.update();
        }
    }

    // ─────────────────────────────────────────────
    // Создание физического блока сундука
    // ─────────────────────────────────────────────

    public boolean placeChestBlock(DeathChest deathChest) {
        Location loc = deathChest.getLocation();
        if (loc == null) return false;
        DeathChest existingAtLocation = getDeathChestByLocation(loc);
        if (existingAtLocation != null && !existingAtLocation.getId().equals(deathChest.getId())) return false;
        if (!isReplaceableAir(loc) || !hasSolidSupport(loc)) return false;

        loc.getBlock().setType(Material.TRAPPED_CHEST);
        setBlockTag(loc, deathChest.getId());
        return true;
    }

    public Location findSafeChestLocation(Location origin) {
        if (origin == null || origin.getWorld() == null) return origin;

        World world = origin.getWorld();
        int baseX = origin.getBlockX();
        int baseY = Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 1, origin.getBlockY()));
        int baseZ = origin.getBlockZ();
        int searchRadius = Math.max(1, plugin.getConfig().getInt("chest.placement.search-radius", 8));
        int verticalDown = Math.max(8, plugin.getConfig().getInt("chest.placement.vertical-search-down", 96));

        for (int radius = 0; radius <= searchRadius; radius++) {
            for (int dy = 0; dy <= 4; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (radius != 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                        int y = baseY + dy;
                        if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;

                        Location candidate = new Location(world, baseX + dx, y, baseZ + dz);
                        if (isSafeChestSpot(candidate)) return candidate;
                    }
                }
            }

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius != 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    Location surface = findSurfaceBelow(world, baseX + dx, baseY, baseZ + dz, verticalDown);
                    if (surface != null && isSafeChestSpot(surface)) {
                        return surface;
                    }
                }
            }
        }

        return createEmergencyPlatform(origin);
    }

    private Location findSurfaceBelow(World world, int x, int startY, int z, int maxDown) {
        int minY = Math.max(world.getMinHeight() + 1, startY - maxDown);
        int maxY = Math.min(world.getMaxHeight() - 1, startY);

        for (int y = maxY; y >= minY; y--) {
            Location candidate = new Location(world, x, y, z);
            if (isSafeChestSpot(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private Location createEmergencyPlatform(Location origin) {
        if (origin == null || origin.getWorld() == null) return origin;
        if (!plugin.getConfig().getBoolean("chest.placement.emergency-platform-enabled", true)) return origin;

        World world = origin.getWorld();
        int searchRadius = Math.max(1, plugin.getConfig().getInt("chest.placement.search-radius", 8));
        int y = Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 1, origin.getBlockY()));
        Material platformMaterial = parsePlatformMaterial();

        for (int radius = 0; radius <= searchRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius != 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    Location candidate = new Location(world, origin.getBlockX() + dx, y, origin.getBlockZ() + dz);
                    Location support = candidate.clone().subtract(0, 1, 0);
                    if (!isReplaceableAir(candidate) || !isReplaceableAir(support)) continue;
                    if (getDeathChestByLocation(candidate) != null) continue;

                    support.getBlock().setType(platformMaterial);
                    return candidate;
                }
            }
        }

        return origin;
    }

    private Material parsePlatformMaterial() {
        String materialName = plugin.getConfig().getString("chest.placement.emergency-platform-material", "OBSIDIAN");
        Material material = Material.matchMaterial(materialName == null ? "OBSIDIAN" : materialName);
        if (material == null || !material.isBlock() || !material.isSolid()) {
            plugin.getLogger().warning("Некорректный material для emergency-platform: " + materialName + ". Используется OBSIDIAN.");
            return Material.OBSIDIAN;
        }
        return material;
    }

    private boolean isSafeChestSpot(Location location) {
        if (location == null || location.getWorld() == null) return false;
        if (location.getBlockY() <= location.getWorld().getMinHeight()
                || location.getBlockY() >= location.getWorld().getMaxHeight()) return false;
        if (!isReplaceableAir(location)) return false;
        if (!hasSolidSupport(location)) return false;
        return getDeathChestByLocation(location) == null;
    }

    private boolean isReplaceableAir(Location location) {
        Material type = location.getBlock().getType();
        return type.isAir() || type == Material.CAVE_AIR || type == Material.VOID_AIR;
    }

    private boolean hasSolidSupport(Location location) {
        Material belowType = location.clone().subtract(0, 1, 0).getBlock().getType();
        return belowType.isSolid() && belowType != Material.CHEST && belowType != Material.TRAPPED_CHEST;
    }

    public void removeChestBlock(DeathChest deathChest) {
        Location loc = deathChest.getLocation();
        if (loc == null) return;

        loc.getBlock().setType(Material.AIR);
        // PDC тег автоматически удаляется при смене типа блока
    }

    // ─────────────────────────────────────────────
    // Сохранение / Загрузка YAML
    // ─────────────────────────────────────────────

    public void saveToYaml() {
        if (pendingSaveTask != null) {
            pendingSaveTask.cancel();
            pendingSaveTask = null;
        }
        YamlConfiguration config = new YamlConfiguration();

        for (DeathChest dc : deathChests.values()) {
            String path = "deathchests." + dc.getId().toString();

            config.set(path + ".owner-uuid", dc.getOwnerUUID().toString());
            config.set(path + ".owner-name", dc.getOwnerName());
            config.set(path + ".world", dc.getWorldName());
            config.set(path + ".x", dc.getX());
            config.set(path + ".y", dc.getY());
            config.set(path + ".z", dc.getZ());
            config.set(path + ".created-at", dc.getCreatedAt());
            config.set(path + ".opened", dc.isOpened());
            config.set(path + ".opened-at", dc.getOpenedAt());
            config.set(path + ".exp-level", dc.getExpLevel());
            config.set(path + ".exp-progress", (double) dc.getExpProgress());
            config.set(path + ".stored-experience", dc.getStoredExperience());

            // Сериализация содержимого
            YamlConfiguration itemConfig = new YamlConfiguration();
            ItemStack[] contents = dc.getContents();
            if (contents != null) {
                for (int i = 0; i < contents.length; i++) {
                    if (contents[i] != null && !contents[i].isEmpty()) {
                        itemConfig.set("slot-" + i, contents[i]);
                    }
                }
            }
            config.set(path + ".contents", itemConfig.saveToString());
        }

        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить deathchests.yml: " + e.getMessage());
        }
    }

    public void requestSave() {
        if (pendingSaveTask != null) return;
        pendingSaveTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingSaveTask = null;
            saveToYaml();
        }, 100L);
    }

    public void flushSave() {
        saveToYaml();
    }

    public void loadFromYaml(DeathChestListener listener) {
        if (!dataFile.exists()) return;

        // Останавливаем все старые таймеры перед перезагрузкой
        listener.stopAllHologramTimers();

        // Удаляем все старые голограммы
        if (hologramManager != null) {
            hologramManager.removeAll();
        }

        // Очищаем текущие данные
        deathChests.clear();
        chestsByLocation.clear();
        lockedDeathChests.clear();

        int expireTime = plugin.getConfig().getInt("expire-time", 300);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = config.getConfigurationSection("deathchests");
        if (section == null) return;

        List<UUID> toRemove = new ArrayList<>();

        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String path = "deathchests." + key;

                UUID ownerUUID = UUID.fromString(config.getString(path + ".owner-uuid"));
                String ownerName = config.getString(path + ".owner-name", "Unknown");
                String worldName = config.getString(path + ".world");
                int x = config.getInt(path + ".x");
                int y = config.getInt(path + ".y");
                int z = config.getInt(path + ".z");
                long createdAt = config.getLong(path + ".created-at");
                boolean opened = config.getBoolean(path + ".opened");
                long openedAt = config.getLong(path + ".opened-at");
                int expLevel = config.getInt(path + ".exp-level", 0);
                float expProgress = (float) config.getDouble(path + ".exp-progress", 0.0);
                int storedExperience = config.contains(path + ".stored-experience")
                        ? config.getInt(path + ".stored-experience", 0)
                        : DeathChest.calculateTotalExperience(expLevel, expProgress);

                // Десериализация содержимого
                ItemStack[] contents = new ItemStack[54];
                String serialized = config.getString(path + ".contents");
                if (serialized != null && !serialized.isEmpty()) {
                    YamlConfiguration itemConfig = new YamlConfiguration();
                    itemConfig.loadFromString(serialized);
                    for (int i = 0; i < 54; i++) {
                        contents[i] = itemConfig.getItemStack("slot-" + i);
                    }
                }

                DeathChest dc = new DeathChest(id, ownerUUID, ownerName, worldName,
                        x, y, z, contents, createdAt, opened, openedAt, expLevel, expProgress, storedExperience);

                // Проверяем максимальный возраст деасчеста (3 дня по умолчанию)
                long maxAge = plugin.getConfig().getLong("max-age", 259200);
                long age = (System.currentTimeMillis() - createdAt) / 1000;
                if (age > maxAge) {
                    // Слишком старый — дропаем вещи и удаляем
                    World world = Bukkit.getWorld(worldName);
                    if (world != null) {
                        Location loc = new Location(world, x, y, z);
                        if (contents != null) {
                            for (ItemStack item : contents) {
                                if (item != null && !item.isEmpty()) {
                                    world.dropItemNaturally(loc, item);
                                }
                            }
                        }
                        loc.getBlock().setType(Material.AIR);
                    }
                    toRemove.add(id);
                    continue;
                }

                // Если деасчест был открыт — проверяем, не истекло ли время
                if (opened && openedAt > 0) {
                    long elapsed = (System.currentTimeMillis() - openedAt) / 1000;
                    long remaining = expireTime - elapsed;

                    if (remaining <= 0) {
                        // Время истекло — дропаем вещи и удаляем
                        World world = Bukkit.getWorld(worldName);
                        if (world != null) {
                            Location loc = new Location(world, x, y, z);
                            if (contents != null) {
                                for (ItemStack item : contents) {
                                    if (item != null && !item.isEmpty()) {
                                        world.dropItemNaturally(loc, item);
                                    }
                                }
                            }
                            loc.getBlock().setType(Material.AIR);
                        }
                        toRemove.add(id);
                        continue;
                    } else {
                        // Время ещё есть — перезапускаем таймеры
                        addDeathChest(dc);
                        World world = Bukkit.getWorld(worldName);
                        if (world != null) {
                            Location loc = new Location(world, x, y, z);
                            if (loc.getBlock().getType() == Material.CHEST || loc.getBlock().getType() == Material.TRAPPED_CHEST) {
                                setBlockTag(loc, id);
                            } else {
                                placeChestBlock(dc);
                            }
                        }
                        // Восстанавливаем голограмму (всегда, независимо от онлайна игрока)
                        listener.getHologramManager().createHologram(id, dc.getLocation(), ownerName);
                        // Запускаем таймеры с оставшимся временем
                        listener.startExpireTimer(dc, (int) remaining);
                        listener.startHologramTimer(dc);
                        continue;
                    }
                }

                // Если не был открыт — просто восстанавливаем
                addDeathChest(dc);

                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    Location loc = new Location(world, x, y, z);
                    if (loc.getBlock().getType() == Material.CHEST || loc.getBlock().getType() == Material.TRAPPED_CHEST) {
                        setBlockTag(loc, id);
                    } else {
                        placeChestBlock(dc);
                    }
                }

                // Восстанавливаем голограмму (всегда)
                listener.getHologramManager().createHologram(id, dc.getLocation(), ownerName);

            } catch (Exception e) {
                plugin.getLogger().warning("Не удалось загрузить деасчест " + key + ": " + e.getMessage());
            }
        }

        // Удаляем истекшие деасчесты
        for (UUID id : toRemove) {
            deathChests.remove(id);
        }

        // Сохраняем изменения (удаляем истекшие)
        if (!toRemove.isEmpty()) {
            saveToYaml();
        }

        plugin.getLogger().info("Загружено " + deathChests.size() + " деасчестов.");
    }

    private record LocationKey(String worldName, int x, int y, int z) {
        static LocationKey from(Location location) {
            return new LocationKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        static LocationKey from(DeathChest deathChest) {
            return new LocationKey(deathChest.getWorldName(), deathChest.getX(), deathChest.getY(), deathChest.getZ());
        }
    }
}
