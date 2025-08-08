package dev.treehouse.myhome;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Storage {
    private final MyHomePlugin plugin;
    private final File file;
    private YamlConfiguration yml;

    private final Map<UUID, Map<String, Location>> homes = new HashMap<>();
    private final Map<UUID, Set<String>> invited = new HashMap<>();
    private final Set<String> publicHomes = new HashSet<>();
    private final Map<UUID, Integer> homeAmt = new HashMap<>();

    public Storage(MyHomePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) { throw new RuntimeException(e); }
        }
        yml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection homesSec = yml.getConfigurationSection("homes");
        if (homesSec != null) {
            for (String uuidStr : homesSec.getKeys(false)) {
                UUID u = UUID.fromString(uuidStr);
                Map<String, Location> map = new HashMap<>();
                ConfigurationSection per = homesSec.getConfigurationSection(uuidStr);
                for (String name : per.getKeys(false)) {
                    map.put(name, readLocation(per.getConfigurationSection(name)));
                }
                homes.put(u, map);
            }
        }

        ConfigurationSection invSec = yml.getConfigurationSection("invited");
        if (invSec != null) {
            for (String uuidStr : invSec.getKeys(false)) {
                UUID u = UUID.fromString(uuidStr);
                Set<String> set = new HashSet<>(invSec.getStringList(uuidStr));
                invited.put(u, set);
            }
        }

        publicHomes.addAll(yml.getStringList("public"));

        ConfigurationSection amtSec = yml.getConfigurationSection("homeAmt");
        if (amtSec != null) {
            for (String uuidStr : amtSec.getKeys(false)) {
                homeAmt.put(UUID.fromString(uuidStr), amtSec.getInt(uuidStr, 0));
            }
        }
    }

    public void save() {
        YamlConfiguration out = new YamlConfiguration();

        for (var e : homes.entrySet()) {
            String u = e.getKey().toString();
            for (var h : e.getValue().entrySet()) {
                writeLocation(out.createSection("homes."+u+"."+h.getKey()), h.getValue());
            }
        }

        for (var e : invited.entrySet()) {
            out.set("invited."+e.getKey(), new ArrayList<>(e.getValue()));
        }

        out.set("public", new ArrayList<>(publicHomes));

        for (var e : homeAmt.entrySet()) {
            out.set("homeAmt."+e.getKey(), e.getValue());
        }

        try { out.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    private Location readLocation(ConfigurationSection s) {
        if (s == null) return null;
        var w = Bukkit.getWorld(s.getString("world"));
        return new Location(
                w,
                s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float)s.getDouble("yaw"), (float)s.getDouble("pitch")
        );
    }
    private void writeLocation(ConfigurationSection s, Location l) {
        s.set("world", l.getWorld().getName());
        s.set("x", l.getX()); s.set("y", l.getY()); s.set("z", l.getZ());
        s.set("yaw", l.getYaw()); s.set("pitch", l.getPitch());
    }

    public Map<String, Location> getHomes(UUID u) {
        return homes.computeIfAbsent(u, k -> new HashMap<>());
    }
    public boolean hasHome(UUID u, String name) { return getHomes(u).containsKey(name); }
    public Location getHome(UUID u, String name) { return getHomes(u).get(name); }
    public void setHome(UUID u, String name, Location loc) { getHomes(u).put(name, loc); }
    public void deleteHome(UUID u, String name) { getHomes(u).remove(name); }
    public List<String> getHomeNames(UUID u) { return new ArrayList<>(getHomes(u).keySet()); }
    public Set<String> getPublicHomes() { return publicHomes; }
    public boolean isPublic(UUID owner, String name) { return publicHomes.contains(owner+":"+name); }
    public void makePublic(UUID owner, String name) { publicHomes.add(owner+":"+name); }
    public void makePrivate(UUID owner, String name) { publicHomes.remove(owner+":"+name); }
    public Set<String> getInvited(UUID target) { return invited.computeIfAbsent(target, k -> new HashSet<>()); }
    public boolean isInvited(UUID target, UUID owner, String name) { return getInvited(target).contains(owner+":"+name); }
    public void invite(UUID target, UUID owner, String name) { getInvited(target).add(owner+":"+name); }
    public void uninvite(UUID target, UUID owner, String name) { getInvited(target).remove(owner+":"+name); }
    public void clearAllOf(UUID target) {
        var names = getHomeNames(target);
        homes.remove(target);
        for (String nm : names) {
            publicHomes.remove(target+":"+nm);
            for (UUID t : invited.keySet()) {
                invited.get(t).remove(target+":"+nm);
            }
        }
        homeAmt.put(target, 0);
    }
    public int getHomeAmt(UUID u) { return homeAmt.getOrDefault(u, 0); }
    public void setHomeAmt(UUID u, int v) { homeAmt.put(u, Math.max(v, 0)); }
    public void addHomeAmt(UUID u, int delta) { setHomeAmt(u, getHomeAmt(u) + delta); }

    public static Optional<UUID> uuidFromNameExact(String name) {
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        if (op != null && op.getUniqueId()!=null) return Optional.of(op.getUniqueId());
        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
            if (p.getName() != null && p.getName().equalsIgnoreCase(name)) return Optional.of(p.getUniqueId());
        }
        return Optional.empty();
    }

    public static String nameOf(UUID u) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(u);
        return p.getName() != null ? p.getName() : u.toString();
    }

    public List<String> offlineNames() {
        return Arrays.stream(Bukkit.getOfflinePlayers())
                .map(op -> op.getName() == null ? op.getUniqueId().toString() : op.getName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }
}
