package eu._Nightwarden.quartaSMP_DeathChest;

import org.bukkit.plugin.java.JavaPlugin;

public final class QuartaSMP_DeathChest extends JavaPlugin {

    private DeathChestManager deathChestManager;
    private HologramManager hologramManager;
    private CompassManager compassManager;
    private ParticleManager particleManager;
    private DeathChestListener deathChestListener;
    private DeathChestCommand deathChestCommand;
    private AdminDeathChestGUI adminDeathChestGUI;

    @Override
    public void onEnable() {
        // Сохраняем конфиг по умолчанию
        saveDefaultConfig();

        // Инициализация менеджеров
        this.deathChestManager = new DeathChestManager(this);
        this.hologramManager = new HologramManager(this);
        this.deathChestManager.setHologramManager(hologramManager);
        this.compassManager = new CompassManager(this, deathChestManager);
        this.particleManager = new ParticleManager(this, deathChestManager);

        // Загружаем настройки частиц
        particleManager.loadConfig();

        // Инициализация слушателя и команд
        this.deathChestListener = new DeathChestListener(this, deathChestManager,
                hologramManager, compassManager);
        this.adminDeathChestGUI = new AdminDeathChestGUI(this, deathChestManager);
        this.deathChestCommand = new DeathChestCommand(this, deathChestManager, deathChestListener, adminDeathChestGUI);

        // Загружаем деасчесты из YAML (с восстановлением таймеров)
        deathChestManager.loadFromYaml(deathChestListener);

        // Регистрация событий
        getServer().getPluginManager().registerEvents(deathChestListener, this);
        getServer().getPluginManager().registerEvents(adminDeathChestGUI, this);

        // Регистрация команды
        var command = getCommand("dc");
        if (command != null) {
            command.setExecutor(deathChestCommand);
            command.setTabCompleter(deathChestCommand);
        }

        // Запускаем частицы
        particleManager.start();

        getLogger().info("QuartaSMP_DeathChest включён!");
    }

    @Override
    public void onDisable() {
        // Останавливаем частицы
        if (particleManager != null) {
            particleManager.stop();
        }

        // Удаляем все голограммы
        if (hologramManager != null) {
            hologramManager.removeAll();
        }

        // Сохраняем все деасчесты
        if (deathChestManager != null) {
            deathChestManager.flushSave();
        }

        getLogger().info("QuartaSMP_DeathChest выключен.");
    }

    public DeathChestManager getDeathChestManager() {
        return deathChestManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public CompassManager getCompassManager() {
        return compassManager;
    }

    public ParticleManager getParticleManager() {
        return particleManager;
    }

    public AdminDeathChestGUI getAdminDeathChestGUI() {
        return adminDeathChestGUI;
    }
}
