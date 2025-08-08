package dev.treehouse.myhome;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Storage {
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yml;

    public Storage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException("Could not create data.yml", e);
            }
        }
        this.yml = YamlConfiguration.loadConfiguration(file);
    }

    /* ========= basic io ========= */

    private void save() {
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save data.yml");
            e.printStackTrace();
        }
    }

    /* ========= schema helpers =========
       players.<uuid>.homes.<name>:
          world: <string>
          x: <double>
          y: <double>
          z: <double>
       players.<uuid>.public: [name, name2, ...]
       invited.<targetUuid>: ["<ownerUuid>:<homeName>", ...]
    */

    private String homesBase(UUID u) { return "players." + u + ".homes"; }
    private String publicBase(UUID u) { return "players." + u + ".public"; }
    private String invitedBase(UUID target) { return "invited." + target; }

    /* ========= homes ========= */

    public void setHome(UUID owner, String name, Location loc) {
        String base = homesBase(owner) + "." + name;
        yml.set(base + ".world", loc.getWorld().getName());
        yml.set(base + ".x", loc.getX());
        yml.set(base + ".y", loc.getY());
        yml.set(base + ".z", loc.getZ());
        save();
    }

    public boolean hasHome(UUID owner, String name) {
        return yml.contains(homesBase(owner) + "." + name);
    }

    public Location getHome(UUID owner, String name) {
        String base = homesBase(owner) + "." + name;
        if (!yml.contains(base)) return null;
        var world = Bukkit.getWorld(yml.getString(base + ".world", ""));
        if (world == null) return null;
        double x = yml.getDouble(base + ".x");
        double y = yml.getDouble(base + ".y");
        double z = yml.getDouble(base + ".z");
        return new Location(world, x, y, z);
    }

    public void deleteHome(UUID owner, String name) {
        // remove from homes
        yml.set(homesBase(owner) + "." + name, null);
        // remove from public list if present
        List<String> pub = new ArrayList<>(getPublicList(owner));
        if (pub.remove(name)) yml.set(publicBase(owner), pub);
        // remove all invitations
        for (String key : yml.getConfigurationSection("invited") == null
                ? List.<String>of() : yml.getConfigurationSection("invited").getKeys(false)) {
            UUID target = UUID.fromString(key);
            List<String> inv = new ArrayList<>(getInvited(target));
            inv.remove(owner + ":" + name);
            yml.set(invitedBase(target), inv);
        }
        save();
    }

    public List<String> getHomeNames(UUID owner) {
        var sec = yml.getConfigurationSection(homesBase(owner));
        if (sec == null) return List.of();
        return sec.getKeys(false).stream().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
    }

    /* ========= public/private ========= */

    public boolean isPublic(UUID owner, String name) {
        return getPublicList(owner).contains(name);
    }

    public void makePublic(UUID owner, String name) {
        List<String> list = new ArrayList<>(getPublicList(owner));
        if (!list.contains(name)) list.add(name);
        yml.set(publicBase(owner), list);
        save();
    }

    public void makePrivate(UUID owner, String name) {
        List<String> list = new ArrayList<>(getPublicList(owner));
        if (list.remove(name)) yml.set(publicBase(owner), list);
        save();
    }

    private List<String> getPublicList(UUID owner) {
        return new ArrayList<>(yml.getStringList(publicBase(owner)));
    }

    public List<String> getPublicHomes() {
        var playersSec = yml.getConfigurationSection("players");
        if (playersSec == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String uStr : playersSec.getKeys(false)) {
            UUID u = UUID.fromString(uStr);
            for (String name : getPublicList(u)) {
                out.add(u + ":" + name); // UUID:name
            }
        }
        return out;
    }

    /* ========= invites ========= */

    public void invite(UUID target, UUID owner, String name) {
        List<String> list = new ArrayList<>(getInvited(target));
        String token = owner + ":" + name;
        if (!list.contains(token)) list.add(token);
        yml.set(invitedBase(target), list);
        save();
    }

    public void uninvite(UUID target, UUID owner, String name) {
        List<String> list = new ArrayList<>(getInvited(target));
        if (list.remove(owner + ":" + name)) {
            yml.set(invitedBase(target), list);
            save();
        }
    }

    public boolean isInvited(UUID target, UUID owner, String name) {
        return getInvited(target).contains(owner + ":" + name);
    }

    public List<String> getInvited(UUID target) {
        return new ArrayList<>(yml.getStringList(invitedBase(target)));
    }

    /* ========= admin / misc ========= */

    public void clearAllOf(UUID user) {
        yml.set("players." + user, null);
        // remove any invites pointing to this user
        var sec = yml.getConfigurationSection("invited");
        if (sec != null) {
            for (String k : new ArrayList<>(sec.getKeys(false))) {
                UUID target = UUID.fromString(k);
                List<String> inv = new ArrayList<>(getInvited(target));
                inv.removeIf(s -> s.startsWith(user.toString() + ":"));
                yml.set(invitedBase(target), inv);
            }
        }
        save();
    }

    /* ========= helpers from names ========= */

    public static Optional<UUID> uuidFromNameExact(String name) {
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        if (op != null) return Optional.ofNullable(op.getUniqueId());
        // fallback search (slow)
        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
            if (p.getName() != null && p.getName().equalsIgnoreCase(name)) return Optional.of(p.getUniqueId());
        }
        return Optional.empty();
    }

    public static String nameOf(UUID id) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(id);
        return p.getName() != null ? p.getName() : id.toString();
    }

    /* ===== derived counts (no more homeAmt) ===== */

    public int privateCount(UUID owner) {
        int total = getHomeNames(owner).size();
        int pub = (int) getPublicList(owner).stream().filter(n -> hasHome(owner, n)).count();
        return total - pub;
    }
}
