package dev.treehouse.myhome;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class HomeCommand implements TabExecutor {
    private final MyHomePlugin plugin;
    private final Storage storage;
    private final Text t;

    public HomeCommand(MyHomePlugin plugin, Storage storage, Text t) {
        this.plugin = plugin;
        this.storage = storage;
        this.t = t;
    }

    private boolean isAdmin(CommandSender s) {
        return s.hasPermission("MyHome.admin");
    }

    private int maxHomes() { return plugin.getConfig().getInt("maxHomes", 8); }
    private int pageSize() { return Math.max(1, plugin.getConfig().getInt("pageSize", 8)); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            t.send(sender, "&cPlayers only.");
            return true;
        }
        UUID u = p.getUniqueId();

        if (storage.getHomeAmt(u) < 0) storage.setHomeAmt(u, 0);

        if (args.length == 0) {
            return listMineDefault(p);
        }

        String joined = String.join(" ", args);

        // /home player:name
        if (joined.contains(":") && args.length == 1) {
            String[] split = joined.split(":", 2);
            String ownerName = split[0];
            String homeName = split[1];

            Optional<UUID> ou = Storage.uuidFromNameExact(ownerName);
            if (ou.isEmpty()) {
                t.send(p, "&cThat player has never joined.");
                return true;
            }
            UUID owner = ou.get();

            if (storage.isPublic(owner, homeName) || storage.isInvited(u, owner, homeName)) {
                Location loc = storage.getHome(owner, homeName);
                if (loc == null) {
                    t.send(p, "&cThat home doesn’t exist anymore.");
                    return true;
                }
                p.teleportAsync(loc);
                t.send(p, "&7You have been teleported!");
            } else {
                t.send(p, "&7That home is not public, or you were not invited");
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "set":
            case "create":
            case "add": {
                if (args.length < 2) {
                    t.send(p, "&eUsage:&r /home set <name> [--override]");
                    return true;
                }
                String name = args[1].toLowerCase(Locale.ROOT);
                if (isReserved(name)) {
                    t.send(p, "&7You can not set a home with the name &f\"" + name + "\"");
                    return true;
                }
                boolean override = args.length >= 3 && args[2].equalsIgnoreCase("--override");

                if (!override && storage.hasHome(u, name)) {
                    t.send(p, "&7You already have a home named &f\"" + name + "\"&7. Use &f--override&7 to reset it to your current location!");
                    return true;
                }

                if (!isAdmin(p) && !override && !storage.hasHome(u, name)) {
                    if (storage.getHomeAmt(u) >= maxHomes()) {
                        t.send(p, "&7You cannot add homes until you delete 1 or more! There is a limit of &c" + maxHomes());
                        return true;
                    }
                    storage.addHomeAmt(u, 1);
                }

                storage.setHome(u, name, p.getLocation());
                if (override) {
                    t.send(p, "&7Reset home &f\"" + name + "\" &7to your current location!");
                } else {
                    t.send(p, "&7Set home &f\"" + name + "\" &7to your current location!");
                }
                return true;
            }

            case "delete":
            case "del":
            case "remove":
            case "rem": {
                if (args.length < 2) {
                    t.send(p, "&eUsage:&r /home delete <name>");
                    return true;
                }
                String name = args[1].toLowerCase(Locale.ROOT);
                if (!storage.hasHome(u, name)) {
                    t.send(p, "&7You do not have a home named &f\"" + name + "\"");
                    return true;
                }
                boolean wasPublic = storage.isPublic(u, name);
                storage.deleteHome(u, name);
                if (wasPublic) {
                    storage.makePrivate(u, name);
                } else {
                    storage.addHomeAmt(u, -1);
                }
                for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                    storage.uninvite(op.getUniqueId(), u, name);
                }
                t.send(p, "&7Your home &f\"" + name + "\" &7has been deleted!");
                return true;
            }

            case "invite":
            case "inv": {
                if (args.length < 3) {
                    t.send(p, "&eUsage:&r /home invite <home> <player>");
                    return true;
                }
                String name = args[1].toLowerCase(Locale.ROOT);
                String targetName = args[2];

                if (!storage.hasHome(u, name)) {
                    t.send(p, "&7Cannot invite &f" + targetName + " &7to &f\"" + name + "\" &7because it does not exist");
                    return true;
                }
                if (storage.isPublic(u, name)) {
                    t.send(p, "&7You cannot add anyone to &f" + name + " &7because it is public!");
                    return true;
                }

                Optional<UUID> tu = Storage.uuidFromNameExact(targetName);
                if (tu.isEmpty()) {
                    t.send(p, "&cThat player has never joined.");
                    return true;
                }

                UUID target = tu.get();
                if (storage.isInvited(target, u, name)) {
                    t.send(p, "&7You cannot invite &f" + Storage.nameOf(target) + " &7to &f\"" + name + "\" &7because they are already invited!");
                    return true;
                }
                if (storage.getHomeAmt(u) <= 0) {
                    t.send(p, "&7You have &c" + storage.getHomeAmt(u) + "&7 homes to invite &f" + targetName + " &7to!");
                    return true;
                }

                storage.invite(target, u, name);
                t.send(p, "&7You have added &f" + Storage.nameOf(target) + " &7to &f\"" + name + "\"");
                Player tp = Bukkit.getPlayer(target);
                if (tp != null) {
                    t.send(tp, "&7You have been added to &f" + p.getName() + "&7's home &f\"" + name + "\"&7, use &f/home " + p.getName() + ":" + name + " &7to get there!");
                }
                return true;
            }

            case "uninvite":
            case "uninv": {
                if (args.length < 3) {
                    t.send(p, "&eUsage:&r /home uninvite <home> <player>");
                    return true;
                }
                String name = args[1].toLowerCase(Locale.ROOT);
                String targetName = args[2];

                if (!storage.hasHome(u, name)) {
                    t.send(p, "&7Cannot uninvite &f" + targetName + " &7to &f\"" + name + "\" &7because it does not exist!");
                    return true;
                }
                if (storage.isPublic(u, name)) {
                    t.send(p, "&7Cannot uninvite &f" + targetName + " &7from &f\"" + name + "\" &7because it is Public!");
                    return true;
                }

                Optional<UUID> tu = Storage.uuidFromNameExact(targetName);
                if (tu.isEmpty()) {
                    t.send(p, "&cThat player has never joined.");
                    return true;
                }
                UUID target = tu.get();
                if (!storage.isInvited(target, u, name)) {
                    t.send(p, "&7Cannot uninvite &f" + Storage.nameOf(target) + " &7from &f\"" + name + "\" &7because they are not invited!");
                    return true;
                }
                storage.uninvite(target, u, name);
                t.send(p, "&7You have uninvited &f" + Storage.nameOf(target) + " &7from &f\"" + name + "\"&7!");
                return true;
            }

            case "public":
            case "pub": {
                if (args.length < 2) {
                    t.send(p, "&7You need to specify a home!");
                    return true;
                }
                String name = args[1].toLowerCase(Locale.ROOT);
                if (!storage.hasHome(u, name)) {
                    t.send(p, "&7You do not have a home named &f\"" + name + "\"");
                    return true;
                }
                if (storage.isPublic(u, name)) {
                    t.send(p, "&7Cannot make &f\"" + name + "\" &7public because it already is!");
                    return true;
                }
                storage.makePublic(u, name);
                t.send(p, "&7You have set &f\"" + name + "\" &7to Public!");
                if (!isAdmin(p)) storage.addHomeAmt(u, -1);
                return true;
            }

            case "private":
            case "priv": {
                if (args.length < 2) {
                    t.send(p, "&7You need to specify a home!");
                    return true;
                }
                String name = args[1].toLowerCase(Locale.ROOT);
                if (!storage.isPublic(u, name)) {
                    t.send(p, "&7Cannot privatize &f\"" + name + "\" &7because it already is!");
                    return true;
                }
                if (!isAdmin(p)) {
                    if (storage.getHomeAmt(u) >= maxHomes()) {
                        t.send(p, "&7Cannot privatize &f\"" + name + "\" &7because you are at your max amount of homes! Remove one or delete this one");
                        return true;
                    }
                    storage.addHomeAmt(u, 1);
                }
                storage.makePrivate(u, name);
                t.send(p, "&7You have made &f\"" + name + "\" &7private!");
                return true;
            }

            case "list": {
                if (args.length < 2) {
                    t.send(p, "&7You need to specify if you want to list your homes, homes you are invited to, or all public homes");
                    return true;
                }
                String which = args[1].toLowerCase(Locale.ROOT);
                int page = (args.length >= 3) ? parseIntSafe(args[2], 1) : 1;
                switch (which) {
                    case "mine":
                        if (isAdmin(p)) listMineAdmin(p, page); else listMineDefault(p);
                        return true;
                    case "invited":
                        listInvited(p, page);
                        return true;
                    case "public":
                        listPublic(p, page);
                        return true;
                    default:
                        t.send(p, "&7Unknown list type. Use &emine&7, &epublic&7, or &einvited&7.");
                        return true;
                }
            }

            case "clear": {
                if (args.length >= 2 && isAdmin(p)) {
                    String target = args[1];
                    Optional<UUID> ou = Storage.uuidFromNameExact(target);
                    if (ou.isEmpty()) {
                        t.send(p, "&cThat player has never joined.");
                        return true;
                    }
                    storage.clearAllOf(ou.get());
                    t.send(p, "&7You have cleared the homes of &f" + target + "&7!");
                    return true;
                } else {
                    storage.clearAllOf(u);
                    t.send(p, "&7You have cleared your homes!");
                    return true;
                }
            }

            case "reload": {
                if (!isAdmin(p)) { t.send(p, "&cNo permission."); return true; }
                plugin.reloadConfig();
                t.send(p, "&aConfig reloaded.");
                return true;
            }

            case "bed": {
                var bed = p.getBedSpawnLocation();
                if (bed != null) {
                    p.teleportAsync(bed);
                    t.send(p, "&7You have been teleported!");
                } else {
                    t.send(p, "&7Your bed is missing or obstructed!");
                }
                return true;
            }
        }

        // Fallback: teleport to own home by name
        String name = args[0].toLowerCase(Locale.ROOT);
        if (storage.hasHome(u, name)) {
            Location loc = storage.getHome(u, name);
            if (loc == null) {
                t.send(p, "&cThat home doesn’t exist anymore.");
                return true;
            }
            p.teleportAsync(loc);
            t.send(p, "&7You have been teleported!");
            return true;
        }
        t.send(p, "&eUnknown subcommand or home name.");
        return true;
    }

    private boolean listMineDefault(Player p) {
        UUID u = p.getUniqueId();
        List<String> names = storage.getHomeNames(u).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        if (names.isEmpty()) {
            t.send(p, "&eNo homes available!");
            return true;
        }
        t.send(p, "&e--- My Homes (" + storage.getHomeAmt(u) + "/" + maxHomes() + ") ---");
        for (String nm : names.stream().limit(maxHomes()).toList()) {
            boolean pub = storage.isPublic(u, nm);
            String state = pub ? ChatColor.AQUA + "Public" : ChatColor.RED + "Private";
            p.sendMessage(t.prefixColored() + ChatColor.GREEN + state + ChatColor.RESET + " | " + ChatColor.GREEN + nm);
        }
        return true;
    }

    private void listMineAdmin(Player p, int page) {
        UUID u = p.getUniqueId();
        List<String> names = storage.getHomeNames(u).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        paginateStatusList(p, "My Homes", names, page, (nm) -> {
            boolean pub = storage.isPublic(u, nm);
            String state = pub ? ChatColor.AQUA + "Public" : ChatColor.RED + "Private";
            return ChatColor.GREEN + state + ChatColor.RESET + " | " + ChatColor.GREEN + nm;
        }, (pg) -> "/home list mine " + pg);
    }

    private void listInvited(Player p, int page) {
        UUID u = p.getUniqueId();
        List<String> entries = new ArrayList<>(storage.getInvited(u));
        entries.sort(String.CASE_INSENSITIVE_ORDER);
        paginateStatusList(p, "My Invited Homes", entries, page, (entry) -> {
            String[] sp = entry.split(":",2);
            UUID owner = UUID.fromString(sp[0]);
            String ownerName = Storage.nameOf(owner);
            String homeName = sp[1];
            return ChatColor.GREEN + ownerName + ChatColor.GRAY + " - " + ChatColor.YELLOW + homeName;
        }, (pg) -> "/home list invited " + pg);
    }

    private void listPublic(Player p, int page) {
        List<String> entries = new ArrayList<>(storage.getPublicHomes());
        entries.sort(String.CASE_INSENSITIVE_ORDER);
        paginateStatusList(p, "Public Homes", entries, page, (entry) -> {
            String[] sp = entry.split(":",2);
            UUID owner = UUID.fromString(sp[0]);
            String ownerName = Storage.nameOf(owner);
            String homeName = sp[1];
            return ChatColor.GREEN + ownerName + ChatColor.GRAY + " - " + ChatColor.YELLOW + homeName;
        }, (pg) -> "/home list public " + pg);
    }

    private interface LineBuilder { String build(String entry); }
    private interface PageCmd { String cmd(int page); }

    private void paginateStatusList(Player p, String title, List<String> entries, int page, LineBuilder builder, PageCmd pageCmd) {
        int total = entries.size();
        if (total == 0) { t.send(p, "&eNo " + title.toLowerCase(Locale.ROOT) + " available!"); return; }
        int perPage = pageSize();
        int totalPages = (int)Math.ceil(total / (double)perPage);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int start = (page-1) * perPage;
        int end = Math.min(start + perPage, total);

        t.send(p, "&e--- " + title + " (Page " + page + "/" + totalPages + ") ---");
        int lineNum = start + 1;
        for (int i = start; i < end; i++) {
            String line = builder.build(entries.get(i));
            p.sendMessage(t.prefixColored() + ChatColor.GREEN + lineNum + ". " + ChatColor.RESET + line);
            lineNum++;
        }

        // simple nav hint (not clickable)
        if (totalPages > 1) {
            String nav = "";
            if (page > 1) nav += ChatColor.AQUA + "[Back: " + pageCmd.cmd(page-1) + "]";
            if (page < totalPages) {
                if (!nav.isEmpty()) nav += ChatColor.GRAY + " | ";
                nav += ChatColor.AQUA + "[Next: " + pageCmd.cmd(page+1) + "]";
            }
            if (!nav.isEmpty()) p.sendMessage(t.prefixColored() + nav);
        }
    }

    private boolean isReserved(String s) {
        return Set.of("set","create","add","delete","del","rem","remove",
                "public","pub","private","priv","invite","inv","uninvite","uninv",
                "mine","list","clear","reload","bed").contains(s.toLowerCase(Locale.ROOT));
    }

    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player p)) return List.of();
        UUID u = p.getUniqueId();
        List<String> myNames = storage.getHomeNames(u).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        boolean hasBed = p.getBedSpawnLocation() != null;

        if (args.length == 1) {
            Set<String> first = new LinkedHashSet<>();
            first.addAll(myNames);
            if (hasBed) first.add("bed");
            first.addAll(Set.of("set","create","add","delete","del","rem","remove","invite","inv","uninvite","uninv",
                    "list","pub","public","priv","private","clear","reload"));
            return prefixFilter(first, args[0]);
        }

        if (args.length == 2) {
            String a1 = args[0].toLowerCase(Locale.ROOT);
            if (Set.of("delete","del","rem","remove","pub","public","priv","private","set","create","add").contains(a1)) {
                return prefixFilter(myNames, args[1]);
            }
            if (a1.equals("list")) {
                return prefixFilter(List.of("mine","public","invited"), args[1]);
            }
            if (a1.equals("clear") && isAdmin(p)) {
                return prefixFilter(storage.offlineNames(), args[1]);
            }
            if (Set.of("invite","inv","uninvite","uninv").contains(a1)) {
                return prefixFilter(myNames, args[1]);
            }
        }

        if (args.length == 3) {
            String a1 = args[0].toLowerCase(Locale.ROOT);
            if (Set.of("invite","inv","uninvite","uninv").contains(a1)) {
                return prefixFilter(storage.offlineNames(), args[2]);
            }
            if (Set.of("set","create","add").contains(a1)) {
                return prefixFilter(List.of("--override"), args[2]);
            }
        }

        return List.of();
    }

    private List<String> prefixFilter(Collection<String> base, String token) {
        String t = token.toLowerCase(Locale.ROOT);
        return base.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(t)).limit(50).collect(Collectors.toList());
    }
}
