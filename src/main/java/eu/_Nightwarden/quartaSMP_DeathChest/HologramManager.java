package eu._Nightwarden.quartaSMP_DeathChest;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HologramManager {

    private final Plugin plugin;
    private final Map<UUID, Hologram> holograms = new ConcurrentHashMap<>();
    private boolean decentHologramsAvailable = false;

    public HologramManager(Plugin plugin) {
        this.plugin = plugin;
        try {
            Class.forName("eu.decentsoftware.holograms.api.DHAPI");
            decentHologramsAvailable = true;
            plugin.getLogger().info("DecentHolograms найден! Голограммы будут использоваться.");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("DecentHolograms не найден. Голограммы отключены.");
        }
    }

    public boolean isAvailable() {
        return decentHologramsAvailable;
    }

    /**
     * Создаёт голограмму над деасчестом.
     * Строки читаются из config.yml -> hologram.lines
     * {player} заменяется на имя владельца
     */
    public void createHologram(UUID deathChestId, Location location, String ownerName) {
        if (!decentHologramsAvailable) return;

        double offsetY = plugin.getConfig().getDouble("hologram.offset-y", 2.5);
        Location holoLoc = location.clone().add(0.5, offsetY, 0.5);

        Hologram hologram = DHAPI.createHologram(
                "dc_" + deathChestId.toString().substring(0, 8),
                holoLoc
        );

        // Читаем строки из конфига
        List<String> lines = plugin.getConfig().getStringList("hologram.lines");
        if (lines.isEmpty()) {
            lines = List.of(
                    "&r&c&l☠ &4&lDEATH CHEST &c&l☠",
                    "&r&7Владелец: &f" + ownerName,
                    "&r&8⏱ Ожидает открытия..."
            );
        }

        for (String line : lines) {
            line = line.replace("{player}", ownerName);
            DHAPI.addHologramLine(hologram, line);
        }

        holograms.put(deathChestId, hologram);
    }

    /**
     * Возвращает индекс последней строки голограммы.
     */
    private int getLastLineIndex(Hologram hologram) {
        return hologram.getPage(0).getLines().size() - 1;
    }

    /**
     * Заменяет последнюю строку голограммы на таймер.
     */
    public void setTimerLine(UUID deathChestId, long remainingSeconds) {
        if (!decentHologramsAvailable) return;

        Hologram hologram = holograms.get(deathChestId);
        if (hologram == null) {
            plugin.getLogger().warning("HologramManager: голограмма " + deathChestId + " не найдена");
            return;
        }

        String timeStr = formatTime(remainingSeconds);
        String timerLine = plugin.getConfig().getString("hologram.timer-line",
                "&r&e⏱ Осталось: &f{time}");
        timerLine = timerLine.replace("{time}", timeStr);

        // Заменяем последнюю строку
        int lastIndex = getLastLineIndex(hologram);
        if (lastIndex >= 0) {
            DHAPI.setHologramLine(hologram, lastIndex, timerLine);
        }
    }

    /**
     * Заменяет последнюю строку голограммы на "Истекает...".
     */
    public void setExpired(UUID deathChestId) {
        if (!decentHologramsAvailable) return;

        Hologram hologram = holograms.get(deathChestId);
        if (hologram == null) return;

        String expiredLine = plugin.getConfig().getString("hologram.expired-line",
                "&r&c⏱ Истекает...");

        // Заменяем последнюю строку
        int lastIndex = getLastLineIndex(hologram);
        if (lastIndex >= 0) {
            DHAPI.setHologramLine(hologram, lastIndex, expiredLine);
        }
    }

    /**
     * Форматирует секунды в MM:SS.
     */
    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }

    /**
     * Удаляет голограмму.
     */
    public void removeHologram(UUID deathChestId) {
        if (!decentHologramsAvailable) return;

        Hologram hologram = holograms.remove(deathChestId);
        if (hologram != null) {
            hologram.delete();
        }
    }

    /**
     * Удаляет все голограммы.
     */
    public void removeAll() {
        if (!decentHologramsAvailable) return;

        for (Hologram hologram : holograms.values()) {
            hologram.delete();
        }
        holograms.clear();
    }
}
