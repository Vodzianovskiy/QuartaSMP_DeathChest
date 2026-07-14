package eu._Nightwarden.quartaSMP_DeathChest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;
import java.util.Random;

public class ParticleManager {

    private final Plugin plugin;
    private final DeathChestManager deathChestManager;
    private boolean enabled;
    private Particle particleType;
    private int interval;
    private double visibleDistanceSquared;
    private BukkitRunnable task;
    private final Random random = new Random();

    public ParticleManager(Plugin plugin, DeathChestManager deathChestManager) {
        this.plugin = plugin;
        this.deathChestManager = deathChestManager;
    }

    /**
     * Загружает настройки из конфига.
     */
    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean("chest.particles.enabled", true);
        interval = config.getInt("chest.particles.interval", 40);
        visibleDistanceSquared = Math.pow(config.getDouble("chest.particles.visible-distance", 32.0), 2);

        String typeName = config.getString("chest.particles.type", "SOUL_FIRE_FLAME");
        try {
            particleType = Particle.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Неизвестный тип частицы: " + typeName + ". Используется SOUL_FIRE_FLAME.");
            particleType = Particle.SOUL_FIRE_FLAME;
        }
    }

    /**
     * Запускает таск спавна частиц.
     */
    public void start() {
        if (task != null) {
            task.cancel();
        }

        if (!enabled) return;

        task = new BukkitRunnable() {
            @Override
            public void run() {
                Collection<DeathChest> chests = deathChestManager.getAllDeathChests();
                for (DeathChest dc : chests) {
                    Location loc = dc.getLocation();
                    if (loc == null) continue;

                    World world = loc.getWorld();
                    if (world == null) continue;
                    if (!loc.getChunk().isLoaded()) continue;

                    boolean hasNearbyPlayer = false;
                    for (Player player : world.getPlayers()) {
                        if (player.getLocation().distanceSquared(loc) <= visibleDistanceSquared) {
                            hasNearbyPlayer = true;
                            break;
                        }
                    }
                    if (!hasNearbyPlayer) continue;

                    // Спавним частицы вокруг сундука (для всех)
                    double x = loc.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.2;
                    double y = loc.getY() + 0.2 + random.nextDouble() * 0.8;
                    double z = loc.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.2;

                    world.spawnParticle(
                            particleType,
                            x, y, z,
                            1,    // count
                            0,    // offsetX
                            0,    // offsetY
                            0,    // offsetZ
                            0     // speed
                    );

                    // Дополнительные частицы для владельца (подсветка)
                    Player owner = Bukkit.getPlayer(dc.getOwnerUUID());
                    if (owner != null && owner.isOnline()
                            && owner.getWorld().equals(world)
                            && owner.getLocation().distanceSquared(loc) <= visibleDistanceSquared) {
                        // Спавним больше частиц вокруг сундука только для владельца
                        for (int i = 0; i < 3; i++) {
                            double px = loc.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.5;
                            double py = loc.getY() + 0.5 + (random.nextDouble() - 0.5) * 1.0;
                            double pz = loc.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.5;

                            owner.spawnParticle(
                                    Particle.END_ROD,
                                    px, py, pz,
                                    1, 0, 0, 0, 0
                            );
                        }
                    }
                }
            }
        };

        task.runTaskTimer(plugin, 0L, interval);
    }

    /**
     * Останавливает таск частиц.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
