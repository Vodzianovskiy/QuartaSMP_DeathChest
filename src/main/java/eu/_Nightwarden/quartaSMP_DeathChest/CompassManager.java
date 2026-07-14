package eu._Nightwarden.quartaSMP_DeathChest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CompassManager {

    private final Plugin plugin;
    private final DeathChestManager manager;
    private final Map<UUID, UUID> compassMap = new ConcurrentHashMap<>(); // playerUUID -> deathChestId
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final NamespacedKey deathCompassKey;

    // Карта для подтверждения удаления: playerUUID -> deathChestId (ожидает подтверждения)
    private final Map<UUID, UUID> pendingDelete = new ConcurrentHashMap<>();

    // Карта: playerUUID -> true (открыто GUI выбора сундука)
    private final Map<UUID, Boolean> selectionGUIOpen = new ConcurrentHashMap<>();

    public CompassManager(Plugin plugin, DeathChestManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.deathCompassKey = new NamespacedKey(plugin, "death_compass");
    }

    /**
     * Выдаёт игроку компас, указывающий на деасчест.
     */
    public void giveCompass(Player player, DeathChest deathChest) {
        Location targetLoc = deathChest.getLocation();
        if (targetLoc == null) return;

        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();

        // Устанавливаем лодстоун без физического блока
        meta.setLodestone(targetLoc);
        meta.setLodestoneTracked(false);

        // Кастомное название
        Component displayName = miniMessage.deserialize(
                "<i:false><gradient:#FF4500:#FFD700>☠ Компас смерти</gradient>"
        );
        meta.displayName(displayName);

        // Лор
        List<Component> lore = new ArrayList<>();
        lore.add(miniMessage.deserialize(
                "<i:false><gray>Ведёт к твоему деасчесту</gray>"
        ));
        lore.add(miniMessage.deserialize(
                "<i:false><gray>на <yellow>" + targetLoc.getBlockX() + ", " +
                        targetLoc.getBlockY() + ", " + targetLoc.getBlockZ() + "</yellow></gray>"
        ));
        lore.add(miniMessage.deserialize(
                "<i:false><gray>Предметов: <yellow>" + countItems(deathChest.getContents()) + "</yellow></gray>"
        ));
        lore.add(miniMessage.deserialize(
                "<i:false><red>ПКМ — выбрать сундук</red>"
        ));
        lore.add(miniMessage.deserialize(
                "<i:false><red>Нельзя выбросить или переместить</red>"
        ));
        meta.lore(lore);

        // Добавляем PDC тег для надёжной идентификации
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(deathCompassKey, PersistentDataType.BOOLEAN, true);
        compass.setItemMeta(meta);

        // Добавляем в инвентарь игрока
        player.getInventory().addItem(compass);
        compassMap.put(player.getUniqueId(), deathChest.getId());
    }

    /**
     * Открывает GUI со списком всех деасчестов игрока для выбора цели.
     */
    public void openChestSelectionGUI(Player player) {
        List<DeathChest> chests = manager.getDeathChestsByOwner(player.getUniqueId());
        if (chests.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(
                    plugin.getConfig().getString("messages.no-chests", "<yellow>У тебя нет активных деасчестов.</yellow>")));
            return;
        }

        // Размер инвентаря: кратно 9, минимум 9, макс 54
        int size = Math.min(Math.max(((chests.size() + 1) / 9 + 1) * 9, 9), 54);
        Inventory gui = Bukkit.createInventory(null, size,
                miniMessage.deserialize("<i:false><gradient:#FF4500:#FFD700>☠ Выбери деасчест</gradient>"));

        int slot = 0;
        for (DeathChest dc : chests) {
            if (slot >= size) break;

            ItemStack icon = new ItemStack(Material.CHEST);
            ItemMeta meta = icon.getItemMeta();

            Location loc = dc.getLocation();
            int itemCount = countItems(dc.getContents());

            meta.displayName(miniMessage.deserialize(
                    "<i:false><gold>☠ Смерть #" + dc.getId().toString().substring(0, 8) + "</gold>"
            ));

            List<Component> lore = new ArrayList<>();
            lore.add(miniMessage.deserialize("<i:false><gray>Координаты: <yellow>" +
                    loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "</yellow></gray>"));
            lore.add(miniMessage.deserialize("<i:false><gray>Мир: <yellow>" + loc.getWorld().getName() + "</yellow></gray>"));
            lore.add(miniMessage.deserialize("<i:false><gray>Предметов: <yellow>" + itemCount + "</yellow></gray>"));
            lore.add(miniMessage.deserialize(""));
            lore.add(miniMessage.deserialize("<i:false><green>ЛКМ — посмотреть (нельзя забрать)</green>"));
            lore.add(miniMessage.deserialize("<i:false><green>ПКМ — выбрать целью</green>"));
            lore.add(miniMessage.deserialize("<i:false><red>Shift+ПКМ — удалить</red>"));
            meta.lore(lore);

            icon.setItemMeta(meta);

            gui.setItem(slot, icon);
            slot++;
        }

        // Заполняем пустые слоты стеклом
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(miniMessage.deserialize("<i:false> "));
        filler.setItemMeta(fillerMeta);
        for (int i = slot; i < size; i++) {
            gui.setItem(i, filler);
        }

        // Кнопка удаления компаса (в последнем доступном слоте)
        int deleteSlot = size - 1;
        ItemStack deleteItem = new ItemStack(Material.BARRIER);
        ItemMeta deleteMeta = deleteItem.getItemMeta();
        deleteMeta.displayName(miniMessage.deserialize("<i:false><red>🗑 Удалить компас</red>"));
        List<Component> deleteLore = new ArrayList<>();
        deleteLore.add(miniMessage.deserialize("<i:false><gray>Нажми, чтобы удалить компас смерти</gray>"));
        deleteMeta.lore(deleteLore);
        deleteItem.setItemMeta(deleteMeta);
        gui.setItem(deleteSlot, deleteItem);

        // Устанавливаем флаг открытого GUI
        setSelectionGUIOpen(player, true);
        player.openInventory(gui);
    }

    /**
     * Получает ID деасчеста из слота GUI по иконке.
     */
    public UUID getChestIdFromIcon(ItemStack icon) {
        if (icon == null || !icon.hasItemMeta() || !icon.getItemMeta().hasDisplayName()) return null;
        String displayName = MiniMessage.miniMessage().serialize(icon.getItemMeta().displayName());
        // Парсим ID из названия: "☠ Смерть #XXXXXXXX"
        if (displayName.contains("#")) {
            String idStr = displayName.substring(displayName.indexOf("#") + 1);
            // Ищем полный UUID в списке деасчестов игрока
            // (в названии только первые 8 символов, но нам хватит для поиска)
            return null; // Будем искать через manager
        }
        return null;
    }

    /**
     * Проверяет, является ли предмет компасом смерти (по PDC тегу).
     */
    public boolean isDeathCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(deathCompassKey, PersistentDataType.BOOLEAN);
    }

    /**
     * Перенаправляет компас игрока на указанный деасчест.
     */
    public void redirectCompass(Player player, DeathChest deathChest) {
        // Удаляем старый компас
        removeCompass(player);
        // Выдаём новый
        giveCompass(player, deathChest);
    }

    /**
     * Удаляет компас у игрока.
     */
    public void removeCompass(Player player) {
        compassMap.remove(player.getUniqueId());
        pendingDelete.remove(player.getUniqueId());

        // Удаляем все компасы смерти из инвентаря
        for (ItemStack item : player.getInventory().getContents()) {
            if (isDeathCompass(item)) {
                item.setAmount(0);
            }
        }
    }

    /**
     * Удаляет компас у игрока по UUID деасчеста.
     */
    public void removeCompassByDeathChestId(UUID deathChestId) {
        UUID playerUUID = null;
        for (Map.Entry<UUID, UUID> entry : compassMap.entrySet()) {
            if (entry.getValue().equals(deathChestId)) {
                playerUUID = entry.getKey();
                break;
            }
        }

        if (playerUUID != null) {
            compassMap.remove(playerUUID);
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                removeCompass(player);
            }
        }
    }

    /**
     * Проверяет, есть ли у игрока активный компас к деасчесту.
     */
    public boolean hasCompass(Player player) {
        return compassMap.containsKey(player.getUniqueId());
    }

    /**
     * Обновляет направление компаса (если нужно при телепортации и т.д.).
     */
    public void updateCompass(Player player, DeathChest deathChest) {
        removeCompass(player);
        giveCompass(player, deathChest);
    }

    /**
     * Подсчитывает количество непустых предметов в массиве.
     */
    private int countItems(ItemStack[] items) {
        if (items == null) return 0;
        int count = 0;
        for (ItemStack item : items) {
            if (item != null && !item.isEmpty()) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * Возвращает ID деасчеста, на который указывает компас игрока.
     */
    public UUID getTargetChestId(Player player) {
        return compassMap.get(player.getUniqueId());
    }

    /**
     * Возвращает карту ожидающих подтверждение удалений.
     */
    public Map<UUID, UUID> getPendingDelete() {
        return pendingDelete;
    }

    /**
     * Проверяет, открыто ли у игрока GUI выбора сундука.
     */
    public boolean isSelectionGUIOpen(Player player) {
        return selectionGUIOpen.containsKey(player.getUniqueId());
    }

    /**
     * Устанавливает флаг открытого GUI выбора сундука.
     */
    public void setSelectionGUIOpen(Player player, boolean open) {
        if (open) {
            selectionGUIOpen.put(player.getUniqueId(), true);
        } else {
            selectionGUIOpen.remove(player.getUniqueId());
            pendingDelete.remove(player.getUniqueId());
        }
    }
}
