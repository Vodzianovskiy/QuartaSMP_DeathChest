package eu._Nightwarden.quartaSMP_DeathChest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DeathChestListener implements Listener {

    private final Plugin plugin;
    private final DeathChestManager manager;
    private final HologramManager hologramManager;
    private final CompassManager compassManager;
    private final MiniMessage miniMessage;

    // Карта: deathChestId -> BukkitTask (задача обновления таймера в голограмме)
    private final Map<UUID, BukkitTask> timerTasks = new ConcurrentHashMap<>();

    // Карта: playerUUID -> deathChestId (какой деасчест открыт у игрока)
    private final Map<UUID, UUID> playerOpenChests = new ConcurrentHashMap<>();

    // Карта: playerUUID -> deathChestId (read-only просмотр через GUI компаса)
    private final Map<UUID, UUID> playerViewingChests = new ConcurrentHashMap<>();

    public DeathChestListener(Plugin plugin, DeathChestManager manager,
                              HologramManager hologramManager, CompassManager compassManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.hologramManager = hologramManager;
        this.compassManager = compassManager;
        this.miniMessage = MiniMessage.miniMessage();
    }

    // ─────────────────────────────────────────────
    // СМЕРТЬ ИГРОКА — создание деасчеста
    // ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        Location deathLocation = player.getLocation();
        Location chestLocation = manager.findSafeChestLocation(deathLocation);
        int storedExperience = DeathChest.calculateTotalExperience(player.getLevel(), player.getExp());

        // Vanilla XP orbs полностью отключаем: убийца/любой игрок рядом не должен получать опыт умершего.
        event.setDroppedExp(0);

        // Удаляем компас смерти из инвентаря перед сбором вещей
        compassManager.removeCompass(player);

        // Собираем все вещи игрока
        List<ItemStack> allItems = new ArrayList<>();

        // getStorageContents() — только 36 слотов хранения (без брони и оффхенда)
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && !item.isEmpty()) {
                allItems.add(item.clone());
            }
        }

        // Броня — отдельно, чтобы не дублировалась (getContents() включает броню)
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && !item.isEmpty()) {
                allItems.add(item.clone());
            }
        }

        // Оффхенд — отдельно
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && !offhand.isEmpty()) {
            allItems.add(offhand.clone());
        }

        if (allItems.isEmpty() && storedExperience <= 0) return;

        event.getDrops().clear();
        event.setKeepInventory(false);

        DeathChest deathChest = DeathChest.createNew(
                player.getUniqueId(),
                player.getName(),
                chestLocation,
                player.getLevel(),
                player.getExp(),
                storedExperience
        );

        ItemStack[] contents = new ItemStack[54];
        for (int i = 0; i < Math.min(allItems.size(), 54); i++) {
            contents[i] = allItems.get(i);
        }
        if (allItems.size() > 54 && chestLocation.getWorld() != null) {
            for (int i = 54; i < allItems.size(); i++) {
                chestLocation.getWorld().dropItemNaturally(chestLocation, allItems.get(i));
            }
        }
        deathChest.setContents(contents);

        if (!manager.placeChestBlock(deathChest)) {
            if (chestLocation.getWorld() != null) {
                for (ItemStack item : contents) {
                    if (item != null && !item.isEmpty()) {
                        chestLocation.getWorld().dropItemNaturally(chestLocation, item);
                    }
                }
            }
            plugin.getLogger().warning("Не удалось безопасно поставить death chest для " + player.getName()
                    + " на " + chestLocation + ". Предметы сброшены без создания сундука.");
            return;
        }

        manager.addDeathChest(deathChest);
        hologramManager.createHologram(deathChest.getId(), chestLocation, player.getName());
        manager.requestSave();

        String msg = plugin.getConfig().getString("messages.chest-created",
                "<green>💀 Твой деасчест появился на <yellow>{x}, {y}, {z}</yellow></green>");
        Component component = miniMessage.deserialize(msg,
                Placeholder.unparsed("x", String.valueOf(chestLocation.getBlockX())),
                Placeholder.unparsed("y", String.valueOf(chestLocation.getBlockY())),
                Placeholder.unparsed("z", String.valueOf(chestLocation.getBlockZ())));
        player.sendMessage(component);
    }

    // ─────────────────────────────────────────────
    // РЕСПАВН — выдача компаса
    // ─────────────────────────────────────────────

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        new BukkitRunnable() {
            @Override
            public void run() {
                List<DeathChest> chests = manager.getDeathChestsByOwner(player.getUniqueId());
                if (!chests.isEmpty()) {
                    // Выдаём компас на самый ценный сундук (с наибольшим количеством предметов)
                    DeathChest mostValuable = chests.stream()
                            .filter(dc -> !dc.isEmpty())
                            .max(Comparator.comparingInt(dc -> countItems(dc.getContents())))
                            .orElse(chests.get(chests.size() - 1));
                    compassManager.giveCompass(player, mostValuable);
                }
            }
        }.runTaskLater(plugin, 10L);
    }

    // ─────────────────────────────────────────────
    // ВЗАИМОДЕЙСТВИЕ С БЛОКОМ (ПКМ) И КОМПАСОМ
    // ─────────────────────────────────────────────

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();

        // Если в руке компас смерти — открываем GUI выбора сундука
        // Проверяем action: RIGHT_CLICK_AIR или RIGHT_CLICK_BLOCK
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR ||
            event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (compassManager.isDeathCompass(item)) {
                event.setCancelled(true);
                compassManager.openChestSelectionGUI(player);
                return;
            }
        }

        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.CHEST
                && event.getClickedBlock().getType() != Material.TRAPPED_CHEST) return;

        Location loc = event.getClickedBlock().getLocation();

        UUID chestId = manager.getBlockTag(loc);
        if (chestId == null) return;

        event.setCancelled(true);

        DeathChest deathChest = manager.getDeathChest(chestId);
        if (deathChest == null) return;

        boolean isOwner = deathChest.getOwnerUUID().equals(player.getUniqueId());
        boolean isAdmin = player.hasPermission("quartasmp.deathchest.admin");

        if (!isOwner && !isAdmin) {
            String msg = plugin.getConfig().getString("messages.not-your-chest",
                    "<red>❌ Это не твой деасчест!</red>");
            player.sendActionBar(miniMessage.deserialize(msg));
            return;
        }

        // Если игрок не владелец (админ в режиме наблюдения) — открываем read-only просмотр
        // чтобы не сломать инвентарь владельца и не создать дупликат баг
        if (!isOwner && isAdmin) {
            String titleStr = plugin.getConfig().getString("chest.inventory-title",
                    "<gradient:#FF4500:#FFD700>💀 Смерть {player}</gradient>");
            titleStr = titleStr.replace("{player}", deathChest.getOwnerName());
            Component title = miniMessage.deserialize(titleStr);

            Inventory viewInv = Bukkit.createInventory(null, 54, title);
            ItemStack[] contents = deathChest.getContents();
            if (contents != null) {
                for (int i = 0; i < Math.min(contents.length, 54); i++) {
                    if (contents[i] != null) {
                        viewInv.setItem(i, contents[i].clone());
                    }
                }
            }

            player.openInventory(viewInv);
            // Запоминаем, что это read-only просмотр (не сохранять изменения при закрытии)
            playerViewingChests.put(player.getUniqueId(), deathChest.getId());
            return;
        }

        // Открываем виртуальный инвентарь (54 слота) — только для владельца
        if (!manager.tryLockDeathChest(deathChest.getId())) {
            player.sendActionBar(miniMessage.deserialize("<i:false><red>❌ Этот деасчест уже открыт. Закрой предыдущее окно.</red>"));
            return;
        }

        String titleStr = plugin.getConfig().getString("chest.inventory-title",
                "<gradient:#FF4500:#FFD700>💀 Смерть {player}</gradient>");
        titleStr = titleStr.replace("{player}", deathChest.getOwnerName());
        Component title = miniMessage.deserialize(titleStr);

        Inventory inv = Bukkit.createInventory(null, 54, title);

        ItemStack[] contents = deathChest.getContents();
        if (contents != null) {
            for (int i = 0; i < Math.min(contents.length, 54); i++) {
                if (contents[i] != null) {
                    inv.setItem(i, contents[i].clone());
                }
            }
        }

        player.openInventory(inv);
        manager.getOpenInventories().put(player.getUniqueId(), inv);
        playerOpenChests.put(player.getUniqueId(), deathChest.getId());

        // Если владелец открыл сундук — запускаем таймер и обновляем голограмму
        if (isOwner) {
            if (!deathChest.isOpened()) {
                deathChest.setOpened(true);
                deathChest.setOpenedAt(System.currentTimeMillis());
                startExpireTimer(deathChest);
                manager.requestSave();
            }

            // Заменяем "Ожидает открытия" на таймер
            long elapsed = (System.currentTimeMillis() - deathChest.getOpenedAt()) / 1000;
            int expireTime = plugin.getConfig().getInt("expire-time", 300);
            long remaining = expireTime - elapsed;
            if (remaining > 0) {
                hologramManager.setTimerLine(deathChest.getId(), remaining);
            }

            // Запускаем периодическое обновление таймера в голограмме (предварительно останавливаем старый)
            stopHologramTimer(deathChest.getId());
            startHologramTimer(deathChest);
        }

        // Компас НЕ удаляем — он остаётся, пока есть хотя бы 1 сундук
        // Сундук НЕ удаляем — игрок сам решает, когда забрать вещи
    }

    // ─────────────────────────────────────────────
    // ЗАКРЫТИЕ ИНВЕНТАРЯ
    // ─────────────────────────────────────────────

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory inv = event.getInventory();
        UUID playerUUID = player.getUniqueId();

        // Если закрыто GUI выбора сундука — снимаем флаг
        if (compassManager.isSelectionGUIOpen(player)) {
            compassManager.setSelectionGUIOpen(player, false);
            return;
        }

        // Если это read-only просмотр через компас — просто убираем из карты, НЕ сохраняем изменения
        if (playerViewingChests.containsKey(playerUUID)) {
            playerViewingChests.remove(playerUUID);
            return;
        }

        Inventory trackedInv = manager.getOpenInventories().get(playerUUID);
        if (trackedInv == null || !trackedInv.equals(inv)) return;

        manager.getOpenInventories().remove(playerUUID);

        // Получаем ID деасчеста из нашей карты (а не по владельцу!)
        UUID chestId = playerOpenChests.remove(playerUUID);
        if (chestId == null) return;

        DeathChest deathChest = manager.getDeathChest(chestId);
        if (deathChest == null) {
            manager.unlockDeathChest(chestId);
            return;
        }

        boolean isOwner = deathChest.getOwnerUUID().equals(playerUUID);

        // Сохраняем содержимое обратно
        ItemStack[] newContents = new ItemStack[54];

        for (int i = 0; i < 54; i++) {
            ItemStack item = inv.getItem(i);
            newContents[i] = (item != null && !item.isEmpty()) ? item.clone() : null;
        }
        deathChest.setContents(newContents);
        manager.unlockDeathChest(chestId);
        manager.requestSave();

        // Если владелец закрыл пустой инвентарь — удаляем деасчест и возвращаем опыт
        if (isOwner && deathChest.isEmpty()) {
            removeDeathChest(deathChest, player);
        }
    }

    // ─────────────────────────────────────────────
    // ЗАЩИТА КОМПАСА ОТ ВЫБРАСЫВАНИЯ
    // ─────────────────────────────────────────────

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (compassManager.isDeathCompass(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            String msg = plugin.getConfig().getString("messages.cant-drop-compass",
                    "<red>❌ Нельзя выбросить компас смерти!</red>");
            event.getPlayer().sendActionBar(miniMessage.deserialize(msg));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Проверяем, не открыто ли GUI выбора сундука
        if (compassManager.isSelectionGUIOpen(player)) {
            event.setCancelled(true);
            handleChestSelectionClick(player, event);
            return;
        }

        // Если это read-only просмотр через компас — блокируем любые клики
        if (playerViewingChests.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Запрещаем ЛЮБОЕ взаимодействие с компасом смерти
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (compassManager.isDeathCompass(current) || compassManager.isDeathCompass(cursor)) {
            event.setCancelled(true);
            String msg = plugin.getConfig().getString("messages.cant-move-compass",
                    "<red>❌ Нельзя переместить компас смерти!</red>");
            player.sendActionBar(miniMessage.deserialize(msg));
        }
    }

    /**
     * Обрабатывает клики в GUI выбора деасчеста.
     */
    private void handleChestSelectionClick(Player player, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) return;

        String displayName = MiniMessage.miniMessage().serialize(clicked.getItemMeta().displayName());

        // Кнопка удаления компаса
        if (displayName.contains("🗑 Удалить компас")) {
            compassManager.removeCompass(player);
            player.closeInventory();
            String msg = plugin.getConfig().getString("messages.compass-deleted",
                    "<red>🗑 Компас смерти удалён из инвентаря.</red>");
            player.sendMessage(miniMessage.deserialize(msg));
            return;
        }

        if (displayName.contains("Смерть #")) {
            // Извлекаем первые 8 символов UUID из названия
            String shortId = displayName.substring(displayName.indexOf("#") + 1);

            // Ищем деасчест по первым 8 символам UUID
            List<DeathChest> chests = manager.getDeathChestsByOwner(player.getUniqueId());
            DeathChest target = null;
            for (DeathChest dc : chests) {
                if (dc.getId().toString().startsWith(shortId)) {
                    target = dc;
                    break;
                }
            }

            if (target == null) return;

            if (event.isLeftClick()) {
                // ЛКМ — просмотр содержимого (ТОЛЬКО ДЛЯ ЧТЕНИЯ, нельзя забрать)
                String titleStr = plugin.getConfig().getString("chest.inventory-title",
                        "<gradient:#FF4500:#FFD700>💀 Смерть {player}</gradient>");
                titleStr = titleStr.replace("{player}", target.getOwnerName());
                Component title = miniMessage.deserialize(titleStr);

                Inventory viewInv = Bukkit.createInventory(null, 54, title);
                ItemStack[] contents = target.getContents();
                if (contents != null) {
                    for (int i = 0; i < Math.min(contents.length, 54); i++) {
                        if (contents[i] != null) {
                            viewInv.setItem(i, contents[i].clone());
                        }
                    }
                }

                player.openInventory(viewInv);
                // Запоминаем, что это read-only просмотр (не сохранять изменения при закрытии)
                playerViewingChests.put(player.getUniqueId(), target.getId());

                // Не запускаем таймер — это просто просмотр
                // Компас не удаляем — игрок ещё не забрал вещи
            } else if (event.isRightClick() && !event.isShiftClick()) {
                // ПКМ — выбрать целью
                compassManager.redirectCompass(player, target);
                player.closeInventory();
                String msg = plugin.getConfig().getString("messages.compass-selected",
                        "<green>✅ Компас перенаправлен на сундук #<yellow>{id}</yellow></green>");
                msg = msg.replace("{id}", shortId);
                player.sendMessage(miniMessage.deserialize(msg));
            } else if (event.isRightClick() && event.isShiftClick()) {
                // Shift+ПКМ — удалить (с подтверждением)
                UUID pendingId = compassManager.getPendingDelete().get(player.getUniqueId());
                if (pendingId != null && pendingId.equals(target.getId())) {
                    // Подтверждение — удаляем
                    compassManager.getPendingDelete().remove(player.getUniqueId());
                    forceRemoveDeathChest(target);
                    player.closeInventory();
                    String msg = plugin.getConfig().getString("messages.chest-deleted",
                            "<red>🗑 Деасчест #<yellow>{id}</yellow> удалён!</red>");
                    msg = msg.replace("{id}", shortId);
                    player.sendMessage(miniMessage.deserialize(msg));
                } else {
                    // Первый клик — запрашиваем подтверждение
                    compassManager.getPendingDelete().put(player.getUniqueId(), target.getId());
                    String msg = plugin.getConfig().getString("messages.confirm-delete",
                            "<red>⚠ Нажми ПКМ ещё раз для подтверждения удаления!</red>");
                    player.sendActionBar(miniMessage.deserialize(msg));
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        // Проверяем, не пытаются ли перетащить компас смерти
        for (Map.Entry<Integer, ItemStack> entry : event.getNewItems().entrySet()) {
            if (compassManager.isDeathCompass(entry.getValue())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ─────────────────────────────────────────────
    // РАЗРУШЕНИЕ БЛОКА
    // ─────────────────────────────────────────────

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) return;

        Player player = event.getPlayer();
        UUID chestId = manager.getBlockTag(block.getLocation());
        if (chestId != null) {
            // Админ может сломать — удаляем деасчест программно
            if (player.hasPermission("quartasmp.deathchest.admin")) {
                DeathChest dc = manager.getDeathChest(chestId);
                if (dc != null) {
                    forceRemoveDeathChest(dc);
                }
                // Не отменяем событие — блок сломается
            } else {
                event.setCancelled(true);
            }
        }
    }

    // ─────────────────────────────────────────────
    // ВЗРЫВЫ
    // ─────────────────────────────────────────────

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block ->
                (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST)
                        && manager.getBlockTag(block.getLocation()) != null
        );
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block ->
                (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST)
                        && manager.getBlockTag(block.getLocation()) != null
        );
    }

    // ─────────────────────────────────────────────
    // ПОРШНИ
    // ─────────────────────────────────────────────

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if ((block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST)
                    && manager.getBlockTag(block.getLocation()) != null) {
                event.setCancelled(true);
                return;
            }
        }
        Block target = event.getBlock().getRelative(event.getDirection());
        if ((target.getType() == Material.CHEST || target.getType() == Material.TRAPPED_CHEST)
                && manager.getBlockTag(target.getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if ((block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST)
                    && manager.getBlockTag(block.getLocation()) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ─────────────────────────────────────────────
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ─────────────────────────────────────────────

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
     * Возвращает самый ценный деасчест игрока (с наибольшим количеством предметов).
     */
    private DeathChest getMostValuableChest(Player player) {
        List<DeathChest> chests = manager.getDeathChestsByOwner(player.getUniqueId());
        return chests.stream()
                .filter(dc -> !dc.isEmpty())
                .max(Comparator.comparingInt(dc -> countItems(dc.getContents())))
                .orElse(null);
    }

    /**
     * Запускает таймер на удаление деасчеста после первого открытия.
     * Публичный — для перезапуска после перезагрузки.
     */
    public void startExpireTimer(DeathChest deathChest) {
        startExpireTimer(deathChest, plugin.getConfig().getInt("expire-time", 300));
    }

    /**
     * Запускает таймер с указанным оставшимся временем (в секундах).
     */
    public void startExpireTimer(DeathChest deathChest, int remainingSeconds) {
        int expireTime = remainingSeconds;

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (manager.getDeathChest(deathChest.getId()) == null) return;

                // Закрываем инвентарь игроку, если он открыт
                Player owner = Bukkit.getPlayer(deathChest.getOwnerUUID());
                if (owner != null && owner.isOnline()) {
                    Inventory openInv = manager.getOpenInventories().get(owner.getUniqueId());
                    if (openInv != null) {
                        ItemStack[] contents = new ItemStack[54];
                        for (int i = 0; i < 54; i++) {
                            ItemStack item = openInv.getItem(i);
                            contents[i] = (item != null && !item.isEmpty()) ? item.clone() : null;
                        }
                        deathChest.setContents(contents);
                        owner.closeInventory();
                    }
                }

                // Останавливаем задачу обновления таймера голограммы
                stopHologramTimer(deathChest.getId());

                // Дропаем вещи на землю
                Location loc = deathChest.getLocation();
                if (loc != null) {
                    World world = loc.getWorld();
                    if (world != null) {
                        ItemStack[] contents = deathChest.getContents();
                        if (contents != null) {
                            for (ItemStack item : contents) {
                                if (item != null && !item.isEmpty()) {
                                    world.dropItemNaturally(loc, item);
                                }
                            }
                        }
                    }
                }

                // Удаляем деасчест
                manager.removeChestBlock(deathChest);
                hologramManager.removeHologram(deathChest.getId());
                compassManager.removeCompassByDeathChestId(deathChest.getId());
                manager.removeDeathChest(deathChest.getId());
                manager.requestSave();

                if (owner != null && owner.isOnline()) {
                    String msg = plugin.getConfig().getString("messages.chest-expired",
                            "<red>⏰ Время деасчеста истекло! Вещи выпали на землю.</red>");
                    owner.sendMessage(miniMessage.deserialize(msg));
                }
            }
        }.runTaskLater(plugin, expireTime * 20L);

        deathChest.setExpireTask(task);
    }

    /**
     * Запускает периодическое обновление таймера в голограмме (каждую секунду).
     * Публичный — для перезапуска после перезагрузки.
     */
    public void startHologramTimer(DeathChest deathChest) {
        int expireTime = plugin.getConfig().getInt("expire-time", 300);
        long startTime = deathChest.getOpenedAt();

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                // Если деасчест уже удалён — останавливаем
                if (manager.getDeathChest(deathChest.getId()) == null) {
                    this.cancel();
                    timerTasks.remove(deathChest.getId());
                    return;
                }

                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                long remaining = expireTime - elapsed;

                if (remaining <= 0) {
                    hologramManager.setExpired(deathChest.getId());
                    this.cancel();
                    timerTasks.remove(deathChest.getId());
                } else {
                    hologramManager.setTimerLine(deathChest.getId(), remaining);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // сразу и каждые 20 тиков (1 сек)

        timerTasks.put(deathChest.getId(), task);
    }

    /**
     * Останавливает задачу обновления таймера голограммы.
     */
    private void stopHologramTimer(UUID deathChestId) {
        BukkitTask task = timerTasks.remove(deathChestId);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Останавливает все задачи обновления таймеров голограмм.
     * Используется при перезагрузке.
     */
    public void stopAllHologramTimers() {
        for (BukkitTask task : timerTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        timerTasks.clear();
    }

    /**
     * Удаляет деасчест (когда игрок забрал все вещи).
     * Возвращает сохранённый опыт игроку.
     * Если остались другие сундуки — компас перенаправляется, а не удаляется.
     */
    private void removeDeathChest(DeathChest deathChest, Player player) {
        if (deathChest.getExpireTask() != null) {
            deathChest.getExpireTask().cancel();
        }
        stopHologramTimer(deathChest.getId());

        // Возвращаем точное количество сохранённого опыта только владельцу и только при полном сборе сундука.
        if (deathChest.getStoredExperience() > 0) {
            player.giveExp(deathChest.getStoredExperience());
        }

        manager.removeChestBlock(deathChest);
        hologramManager.removeHologram(deathChest.getId());

        // Проверяем, остались ли другие сундуки
        List<DeathChest> remaining = manager.getDeathChestsByOwner(player.getUniqueId());
        remaining.removeIf(dc -> dc.getId().equals(deathChest.getId()));

        if (remaining.isEmpty()) {
            // Нет больше сундуков — удаляем компас
            compassManager.removeCompass(player);
        } else {
            // Есть другие сундуки — перенаправляем компас на самый ценный
            DeathChest mostValuable = remaining.stream()
                    .filter(dc -> !dc.isEmpty())
                    .max(Comparator.comparingInt(dc -> countItems(dc.getContents())))
                    .orElse(remaining.get(0));
            compassManager.redirectCompass(player, mostValuable);
        }

        manager.removeDeathChest(deathChest.getId());
        manager.requestSave();

        String msg = plugin.getConfig().getString("messages.chest-collected",
                "<green>✅ Ты забрал все вещи из деасчеста!</green>");
        player.sendMessage(miniMessage.deserialize(msg));
    }

    /**
     * Возвращает HologramManager (нужен для DeathChestManager.loadFromYaml).
     */
    public HologramManager getHologramManager() {
        return hologramManager;
    }

    /**
     * Публичный метод для удаления деасчеста (из команд).
     */
    public void forceRemoveDeathChest(DeathChest deathChest) {
        Player owner = Bukkit.getPlayer(deathChest.getOwnerUUID());
        if (owner != null && owner.isOnline()) {
            Inventory openInv = manager.getOpenInventories().get(owner.getUniqueId());
            if (openInv != null) {
                owner.closeInventory();
            }
        }

        if (deathChest.getExpireTask() != null) {
            deathChest.getExpireTask().cancel();
        }
        stopHologramTimer(deathChest.getId());

        manager.removeChestBlock(deathChest);
        hologramManager.removeHologram(deathChest.getId());

        // Удаляем компас только если у игрока больше нет других сундуков
        if (owner != null && owner.isOnline()) {
            List<DeathChest> remaining = manager.getDeathChestsByOwner(deathChest.getOwnerUUID());
            // Убираем текущий из списка (он ещё не удалён из manager)
            remaining.removeIf(dc -> dc.getId().equals(deathChest.getId()));

            if (remaining.isEmpty()) {
                compassManager.removeCompass(owner);
            } else {
                // Перенаправляем компас на самый ценный из оставшихся
                DeathChest mostValuable = remaining.stream()
                        .filter(dc -> !dc.isEmpty())
                        .max(Comparator.comparingInt(dc -> countItems(dc.getContents())))
                        .orElse(remaining.get(0));
                compassManager.redirectCompass(owner, mostValuable);
            }
        } else {
            compassManager.removeCompassByDeathChestId(deathChest.getId());
        }

        manager.removeDeathChest(deathChest.getId());
        manager.requestSave();
    }
}
