package eu._Nightwarden.quartaSMP_DeathChest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class DeathChest {

    private final UUID id;
    private final UUID ownerUUID;
    private final String ownerName;
    private final String worldName;
    private final int x, y, z;
    private ItemStack[] contents;
    private final long createdAt;
    private boolean opened;
    private long openedAt;
    private int expLevel;
    private float expProgress;
    private int storedExperience;
    private transient BukkitTask expireTask;

    public DeathChest(UUID id, UUID ownerUUID, String ownerName, String worldName,
                      int x, int y, int z, ItemStack[] contents, long createdAt,
                      boolean opened, long openedAt, int expLevel, float expProgress) {
        this(id, ownerUUID, ownerName, worldName, x, y, z, contents, createdAt,
                opened, openedAt, expLevel, expProgress, calculateTotalExperience(expLevel, expProgress));
    }

    public DeathChest(UUID id, UUID ownerUUID, String ownerName, String worldName,
                      int x, int y, int z, ItemStack[] contents, long createdAt,
                      boolean opened, long openedAt, int expLevel, float expProgress, int storedExperience) {
        this.id = id;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.contents = contents;
        this.createdAt = createdAt;
        this.opened = opened;
        this.openedAt = openedAt;
        this.expLevel = expLevel;
        this.expProgress = expProgress;
        this.storedExperience = Math.max(0, storedExperience);
    }

    // --- Getters ---

    public UUID getId() {
        return id;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public Location getLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }

    public ItemStack[] getContents() {
        return contents;
    }

    public void setContents(ItemStack[] contents) {
        this.contents = contents;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean isOpened() {
        return opened;
    }

    public void setOpened(boolean opened) {
        this.opened = opened;
    }

    public long getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(long openedAt) {
        this.openedAt = openedAt;
    }

    public BukkitTask getExpireTask() {
        return expireTask;
    }

    public void setExpireTask(BukkitTask expireTask) {
        this.expireTask = expireTask;
    }

    public int getExpLevel() {
        return expLevel;
    }

    public void setExpLevel(int expLevel) {
        this.expLevel = expLevel;
    }

    public float getExpProgress() {
        return expProgress;
    }

    public void setExpProgress(float expProgress) {
        this.expProgress = expProgress;
    }

    public int getStoredExperience() {
        return storedExperience;
    }

    public void setStoredExperience(int storedExperience) {
        this.storedExperience = Math.max(0, storedExperience);
    }

    /**
     * Проверяет, пуст ли инвентарь деасчеста.
     */
    public boolean isEmpty() {
        if (contents == null) return true;
        for (ItemStack item : contents) {
            if (item != null && !item.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Создаёт новый деасчест с пустым инвентарём (54 слота).
     */
    public static DeathChest createNew(UUID ownerUUID, String ownerName, Location location,
                                       int expLevel, float expProgress) {
        return createNew(ownerUUID, ownerName, location, expLevel, expProgress,
                calculateTotalExperience(expLevel, expProgress));
    }

    public static DeathChest createNew(UUID ownerUUID, String ownerName, Location location,
                                       int expLevel, float expProgress, int storedExperience) {
        UUID id = UUID.randomUUID();
        ItemStack[] emptyContents = new ItemStack[54];
        String worldName = location.getWorld().getName();
        int bx = location.getBlockX();
        int by = location.getBlockY();
        int bz = location.getBlockZ();
        long now = System.currentTimeMillis();
        return new DeathChest(
                id, ownerUUID, ownerName, worldName,
                bx, by, bz, emptyContents, now,
                false, 0L, expLevel, expProgress, storedExperience
        );
    }

    public static int calculateTotalExperience(int level, float progress) {
        level = Math.max(0, level);
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        return getExperienceAtLevel(level) + Math.round(getExperienceToNextLevel(level) * progress);
    }

    private static int getExperienceAtLevel(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            return (int) Math.floor(2.5 * level * level - 40.5 * level + 360);
        }
        return (int) Math.floor(4.5 * level * level - 162.5 * level + 2220);
    }

    private static int getExperienceToNextLevel(int level) {
        if (level <= 15) return 2 * level + 7;
        if (level <= 30) return 5 * level - 38;
        return 9 * level - 158;
    }
}
