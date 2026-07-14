package eu._Nightwarden.quartaSMP_DeathChest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class DeathChestCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final DeathChestManager manager;
    private final DeathChestListener listener;
    private final AdminDeathChestGUI adminGui;
    private final MiniMessage miniMessage;

    public DeathChestCommand(Plugin plugin, DeathChestManager manager, DeathChestListener listener,
                             AdminDeathChestGUI adminGui) {
        this.plugin = plugin;
        this.manager = manager;
        this.listener = listener;
        this.adminGui = adminGui;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            sendMessage(sender, "<i:false><red>Использование: /dc <list|tp|clear|reload></red>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> handleList(sender);
            case "tp" -> handleTp(sender, args);
            case "admin", "deaths" -> handleAdmin(sender);
            case "view" -> handleView(sender, args);
            case "clear" -> handleClear(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendMessage(sender, "<i:false><red>Неизвестная подкоманда. Используй: list, admin, view, tp, clear, reload</red>");
        }

        return true;
    }

    private void handleAdmin(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "<red>Только для игроков.</red>");
            return;
        }
        if (!player.hasPermission("quartasmp.deathchest.admin")) {
            sendMessage(sender, plugin.getConfig().getString("messages.no-permission",
                    "<red>⛔ У тебя нет прав на эту команду.</red>"));
            return;
        }
        adminGui.openAdminList(player);
    }

    private void handleView(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "<red>Только для игроков.</red>");
            return;
        }
        if (!player.hasPermission("quartasmp.deathchest.admin")) {
            sendMessage(sender, plugin.getConfig().getString("messages.no-permission",
                    "<red>⛔ У тебя нет прав на эту команду.</red>"));
            return;
        }
        if (args.length < 2) {
            sendMessage(sender, "<red>Использование: /dc view <id></red>");
            return;
        }
        DeathChest target = findByPartialId(args[1]);
        adminGui.openReadOnlyChest(player, target);
    }

    // ─────────────────────────────────────────────
    // /dc list
    // ─────────────────────────────────────────────

    private void handleList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "<red>Только для игроков.</red>");
            return;
        }

        List<DeathChest> chests = manager.getDeathChestsByOwner(player.getUniqueId());
        if (chests.isEmpty()) {
            sendMessage(sender, plugin.getConfig().getString("messages.no-chests",
                    "<yellow>У тебя нет активных деасчестов.</yellow>"));
            return;
        }

        sendMessage(sender, plugin.getConfig().getString("messages.chest-list-header",
                "<gold>📋 Твои деасчесты:</gold>"));

        for (DeathChest dc : chests) {
            String entry = plugin.getConfig().getString("messages.chest-list-entry",
                    "<gold>• <click:run_command:/dc tp {id}><white>{id}</white></click> — <gray>{x}, {y}, {z} ({world})</gray></gold>");
            Component component = miniMessage.deserialize(entry,
                    Placeholder.unparsed("id", dc.getId().toString().substring(0, 8) + "..."),
                    Placeholder.unparsed("x", String.valueOf(dc.getX())),
                    Placeholder.unparsed("y", String.valueOf(dc.getY())),
                    Placeholder.unparsed("z", String.valueOf(dc.getZ())),
                    Placeholder.unparsed("world", dc.getWorldName()));
            player.sendMessage(component);
        }
    }

    // ─────────────────────────────────────────────
    // /dc tp <id>
    // ─────────────────────────────────────────────

    private void handleTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "<red>Только для игроков.</red>");
            return;
        }

        if (!player.hasPermission("quartasmp.deathchest.admin")) {
            sendMessage(sender, plugin.getConfig().getString("messages.no-permission",
                    "<red>⛔ У тебя нет прав на эту команду.</red>"));
            return;
        }

        if (args.length < 2) {
            sendMessage(sender, "<red>Использование: /dc tp <id></red>");
            return;
        }

        DeathChest target = findByPartialId(args[1]);

        if (target == null) {
            sendMessage(sender, plugin.getConfig().getString("messages.chest-not-found",
                    "<red>❌ Деасчест не найден.</red>"));
            return;
        }

        Location loc = target.getLocation();
        if (loc == null) {
            sendMessage(sender, "<red>❌ Мир деасчеста не найден.</red>");
            return;
        }

        player.teleport(loc.add(0.5, 1, 0.5));

        String msg = plugin.getConfig().getString("messages.teleported",
                "<green>✅ Телепортирован к деасчесту <yellow>{id}</yellow></green>");
        sendMessage(sender, msg.replace("{id}", target.getId().toString().substring(0, 8) + "..."));
    }

    // ─────────────────────────────────────────────
    // /dc clear <ник>
    // ─────────────────────────────────────────────

    private void handleClear(CommandSender sender, String[] args) {
        if (!sender.hasPermission("quartasmp.deathchest.admin")) {
            sendMessage(sender, plugin.getConfig().getString("messages.no-permission",
                    "<red>⛔ У тебя нет прав на эту команду.</red>"));
            return;
        }

        if (args.length < 2) {
            sendMessage(sender, "<red>Использование: /dc clear <ник></red>");
            return;
        }

        String playerName = args[1];
        var offlinePlayer = Bukkit.getOfflinePlayerIfCached(playerName);
        if (offlinePlayer == null) {
            sendMessage(sender, plugin.getConfig().getString("messages.player-not-found",
                    "<red>❌ Игрок не найден.</red>"));
            return;
        }

        UUID targetUUID = offlinePlayer.getUniqueId();
        List<DeathChest> chests = manager.getDeathChestsByOwner(targetUUID);

        if (chests.isEmpty()) {
            sendMessage(sender, "<yellow>У игрока " + playerName + " нет активных деасчестов.</yellow>");
            return;
        }

        for (DeathChest dc : new ArrayList<>(chests)) {
            listener.forceRemoveDeathChest(dc);
        }

        String msg = plugin.getConfig().getString("messages.admin-cleared",
                "<green>✅ Деасчесты игрока <yellow>{player}</yellow> удалены.</green>");
        sendMessage(sender, msg.replace("{player}", playerName));
    }

    // ─────────────────────────────────────────────
    // /dc reload
    // ─────────────────────────────────────────────

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("quartasmp.deathchest.admin")) {
            sendMessage(sender, plugin.getConfig().getString("messages.no-permission",
                    "<red>⛔ У тебя нет прав на эту команду.</red>"));
            return;
        }

        plugin.reloadConfig();

        String msg = plugin.getConfig().getString("messages.config-reloaded",
                "<green>✅ Конфиг перезагружен.</green>");
        sendMessage(sender, msg);
    }

    // ─────────────────────────────────────────────
    // TabCompleter
    // ─────────────────────────────────────────────

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("list");
            if (sender.hasPermission("quartasmp.deathchest.admin")) {
                completions.add("tp");
                completions.add("admin");
                completions.add("deaths");
                completions.add("view");
                completions.add("clear");
                completions.add("reload");
            }
            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("clear")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("view"))) {
            return manager.getAllDeathChests().stream()
                    .map(dc -> dc.getId().toString().substring(0, 8))
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    // ─────────────────────────────────────────────
    // Утилиты
    // ─────────────────────────────────────────────

    private void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(miniMessage.deserialize(message));
    }

    private DeathChest findByPartialId(String partialId) {
        String searchId = partialId.toLowerCase();
        for (DeathChest dc : manager.getAllDeathChests()) {
            String fullId = dc.getId().toString().toLowerCase();
            if (fullId.startsWith(searchId) || fullId.substring(0, 8).equalsIgnoreCase(searchId)) {
                return dc;
            }
        }
        return null;
    }
}
