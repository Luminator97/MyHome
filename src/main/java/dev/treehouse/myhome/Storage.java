package dev.treehouse.myhome;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Storage {

    private final File file;
    private final FileConfiguration yml;

    public Storage(File dataFolder) {
        this.file = new File(dataFolder, "data.yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.yml = YamlConfiguration.loadConfiguration(file);
    }

    private String homesBase(UUID owner) {
        return "homes." + owner.toString();
    }

    public void setHome(UUID owner, String name, Location loc) {
        String base = homesBase(owner) + "." + name;
        yml.set(base + ".world", loc.getWorld().getName());
        yml.set(base + ".x", loc.getX());
        yml.set(base + ".y", loc.getY());
        yml.set(base + ".z", loc.getZ());
        yml.set(base + ".yaw", loc.getYaw());
        yml.set(base + ".pitch", loc.getPitch());
        save();
    }

    public Location getHome(UUID owner, String name) {
        String base = homesBase(owner) + "." + name;
        if (!yml.contains(base)) return null;

        var world = Bukkit.getWorld(yml.getString(base + ".world", ""));
        if (world == null) return null;

        double x = yml.getDouble(base + ".x");
        double y = yml.getDouble(base + ".y");
        double z = yml.getDouble(base + ".z");
        float yaw = (float) yml.getDouble(base + ".yaw", 0.0);
        float pitch = (float) yml.getDouble(base + ".pitch", 0.0);

        return new Location(world, x, y, z, yaw, pitch);
    }

    public boolean deleteHome(UUID owner, String name) {
        String base = homesBase(owner) + "." + name;
        if (yml.contains(base)) {
            yml.set(base, null);
            save();
            return true;
        }
        return false;
    }

    public Set<String> listHomes(UUID owner) {
        String base = homesBase(owner);
        if (!yml.contains(base)) return new HashSet<>();
        return yml.getConfigurationSection(base).getKeys(false);
    }

    public void save() {
        try {
            yml.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
