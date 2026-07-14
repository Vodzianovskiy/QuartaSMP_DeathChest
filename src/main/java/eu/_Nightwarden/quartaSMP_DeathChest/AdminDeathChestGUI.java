package eu._Nightwarden.quartaSMP_DeathChest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AdminDeathChestGUI implements Listener {

    private static final int GUI_SIZE = 54;

    private final Plugin plugin;
    private final DeathChestManager manager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, Map<Integer, UUID>> adminListSlots = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> adminViewingChest = new ConcurrentHashMap<>();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public AdminDeathChestGUI(Plugin plugin, DeathChestManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void openAdminList(Player admin) {
        if (!admin.hasPermission("quartasmp.deathchest.admin")) {
            admin.sendMessage(miniMessage.deserialize(plugin.getConfig().getString("messages.no-permission",
                    "<red>⛔ У тебя нет прав на эту команду.</red>")));
            return;
        }

        List<DeathChest> chests = manager.getAllDeathChests().stream()
                .sorted(Comparator.comparingLong(DeathChest::getCreatedAt).reversed())
                .limit(GUI_SIZE)
                .toList();

        Inventory gui = Bukkit.createInventory(null, GUI_SIZE,
                miniMessage.deserialize("<i:false><dark_red>☠ Админ: смерти игроков</dark_red>"));

        Map<Integer, UUID> slotMap = new HashMap<>();
        int slot = 0;
        for (DeathChest dc : chests) {
            gui.setItem(slot, createDeathIcon(dc));
            slotMap.put(slot, dc.getId());
            slot++;
        }

        if (chests.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            meta.displayName(miniMessage.deserialize("<i:false><yellow>Активных death chest нет</yellow>"));
            empty.setItemMeta(meta);
            gui.setItem(22, empty);
        }

        adminListSlots.put(admin.getUniqueId(), slotMap);
        admin.openInventory(gui);
    }

    public void openReadOnlyChest(Player admin, DeathChest deathChest) {
        if (deathChest == null) {
            admin.sendMessage(miniMessage.deserialize(plugin.getConfig().getString("messages.chest-not-found",
                    "<red>❌ Деасчест не найден.</red>")));
            return;
        }

        String titleStr = plugin.getConfig().getString("chest.inventory-title",
                "<gradient:#FF4500:#FFD700>💀 Смерть {player}</gradient>");
        titleStr = titleStr.replace("{player}", deathChest.getOwnerName());

        Inventory view = Bukkit.createInventory(null, 54, miniMessage.deserialize(titleStr));
        ItemStack[] contents = deathChest.getContents();
        if (contents != null) {
            for (int i = 0; i < Math.min(contents.length, 54); i++) {
                if (contents[i] != null && !contents[i].isEmpty()) {
                    view.setItem(i, contents[i].clone());
                }
            }
        }

        adminViewingChest.put(admin.getUniqueId(), deathChest.getId());
        admin.openInventory(view);
    }

    public boolean isAdminGuiOpen(Player player) {
        return adminListSlots.containsKey(player.getUniqueId()) || adminViewingChest.containsKey(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        UUID playerId = player.getUniqueId();

        if (adminViewingChest.containsKey(playerId)) {
            event.setCancelled(true);
            return;
        }

        Map<Integer, UUID> slotMap = adminListSlots.get(playerId);
        if (slotMap == null) return;

        event.setCancelled(true);
        UUID chestId = slotMap.get(event.getRawSlot());
        if (chestId == null) return;

        DeathChest deathChest = manager.getDeathChest(chestId);
        if (deathChest == null) {
            player.sendMessage(miniMessage.deserialize(plugin.getConfig().getString("messages.chest-not-found",
                    "<red>❌ Деасчест не найден.</red>")));
            openAdminList(player);
            return;
        }

        if (event.isRightClick()) {
            Location loc = deathChest.getLocation();
            if (loc == null) {
                player.sendMessage(miniMessage.deserialize("<i:false><red>❌ Мир этого сундука не загружен.</red>"));
                return;
            }
            player.closeInventory();
            player.teleport(loc.clone().add(0.5, 1.0, 0.5));
            player.sendMessage(miniMessage.deserialize("<i:false><green>✅ Телепорт к death chest игрока <yellow>"
                    + deathChest.getOwnerName() + "</yellow></green>"));
            return;
        }

        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> openReadOnlyChest(player, deathChest));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID playerId = player.getUniqueId();
        adminListSlots.remove(playerId);
        adminViewingChest.remove(playerId);
    }

    private ItemStack createDeathIcon(DeathChest deathChest) {
        ItemStack icon = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) icon.getItemMeta();

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(deathChest.getOwnerUUID());
        meta.setOwningPlayer(offlinePlayer);
        meta.displayName(miniMessage.deserialize("<i:false><gold>☠ " + deathChest.getOwnerName()
                + " <gray>#" + deathChest.getId().toString().substring(0, 8) + "</gray></gold>"));

        Location loc = deathChest.getLocation();
        int itemCount = countItems(deathChest.getContents());
        List<Component> lore = new ArrayList<>();
        lore.add(miniMessage.deserialize("<i:false><gray>UUID: <white>" + deathChest.getOwnerUUID() + "</white></gray>"));
        lore.add(miniMessage.deserialize("<i:false><gray>Мир: <yellow>" + deathChest.getWorldName() + "</yellow></gray>"));
        lore.add(miniMessage.deserialize("<i:false><gray>Координаты: <yellow>" + deathChest.getX() + ", "
                + deathChest.getY() + ", " + deathChest.getZ() + "</yellow></gray>"));
        lore.add(miniMessage.deserialize("<i:false><gray>Предметов: <yellow>" + itemCount + "</yellow></gray>"));
        lore.add(miniMessage.deserialize("<i:false><gray>Создан: <white>"
                + dateFormatter.format(Instant.ofEpochMilli(deathChest.getCreatedAt())) + "</white></gray>"));
        lore.add(miniMessage.deserialize("<i:false><gray>Статус: "
                + (deathChest.isOpened() ? "<red>открыт</red>" : "<green>не открыт</green>") + "</gray>"));
        if (loc == null) {
            lore.add(miniMessage.deserialize("<i:false><red>Мир не загружен</red>"));
        }
        lore.add(miniMessage.deserialize(""));
        lore.add(miniMessage.deserialize("<i:false><green>ЛКМ — посмотреть содержимое</green>"));
        lore.add(miniMessage.deserialize("<i:false><yellow>ПКМ — телепорт к сундуку</yellow>"));

        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

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
}