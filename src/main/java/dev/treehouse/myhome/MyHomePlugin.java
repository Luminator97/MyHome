package dev.treehouse.myhome;

import org.bukkit.plugin.java.JavaPlugin;

public class MyHomePlugin extends JavaPlugin {

    private Storage storage;
    private Text text;

    @Override
    public void onEnable() {
        // config with &-colors
        saveDefaultConfig();

        // data store (loads data.yml automatically)
        storage = new Storage(this);

        // chat prefix helper
        String prefix = getConfig().getString("chatPrefix", "&a&lHome&r &8≫ &7");
        text = new Text(prefix);

        // command executor + tab complete
        HomeCommand cmd = new HomeCommand(this, storage, text);
        getCommand("home").setExecutor(cmd);
        getCommand("home").setTabCompleter(cmd);

        getLogger().info("MyHome enabled.");
    }

    @Override
    public void onDisable() {
        // No explicit save needed; Storage saves on every mutation.
        getLogger().info("MyHome disabled.");
    }
}
