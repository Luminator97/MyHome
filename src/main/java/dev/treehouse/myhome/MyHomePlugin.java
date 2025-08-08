package dev.treehouse.myhome;

import org.bukkit.plugin.java.JavaPlugin;

public final class MyHomePlugin extends JavaPlugin {
    private static MyHomePlugin instance;
    private Storage storage;
    private Text t;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        this.t = new Text(getConfig().getString("chatPrefix", "<green>[Home] </green>"));
        this.storage = new Storage(this);
        storage.load();

        var cmd = new HomeCommand(this, storage, t);
        getCommand("home").setExecutor(cmd);
        getCommand("home").setTabCompleter(cmd);

        getLogger().info("MyHome enabled.");
    }

    @Override
    public void onDisable() {
        storage.save();
    }

    public static MyHomePlugin inst() { return instance; }
    public Storage storage() { return storage; }
    public Text text() { return t; }
}
