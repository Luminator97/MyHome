package dev.treehouse.myhome;

import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
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

    private final int PAGE_SIZE = 8;

    public HomeCommand(MyHomePlugin plugin, Storage storage, Text t) {
        this.plugin = plugin;
        this.storage = storage;
        this.t = t;
    }

    private boolean isAdmin(CommandSender s) {
        return s.hasPermission("MyHome.admin");
    }

    private int maxHomes() { return plugin.getConfig().getInt("maxHomes", 8); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            t.send(sender, "<red>Players only.");
            return true;
        }
        UUID u = p.getUniqueId();

        if (storage.getHomeAmt(u) < 0) storage.setHomeAmt(u, 0);

        if (args.length == 0) {
            return listMineDefault(p);
        }

        String joined = String.join(" ", args);

        if (joined.contains(":") && args.length == 1) {
            String[] split = joined.split(":", 2);
            String ownerName = split[0];
            String homeName = split[1];

            Optional<UUID> ou = Storage.uuidFromNameExact(ownerName);
            if (ou.isEmpty()) {
                t.send(p, "<light_red>That player has never joined.");
                return true;
            }
            UUID owner = ou.get();

            if (storage.isPublic(owner, homeName) || storage.isInvited(u, owner, homeName)) {
                Location loc = storage.getHome(owner, homeName);
                if (loc == null) {
                    t.send(p, "<light_red>That home doesn’t exist anymore.");
                    return true;
                }
                p.teleportAsync(loc);
                t.send(p, "You have been teleported!");
            } else {
                t.send(p, "That home is not public, or you were not invited");
            }
            return true;
        }

        if (args.length >= 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);

            switch (sub) {
                case "set":
                case "create":
                case "add": {
                    if (args.length < 2) {
                        t.send(p, "<yellow>Usage:</yellow> /home set <name> [--override]");
                        return true;
                    }
                    String name = args[1].toLowerCase(Locale.ROOT);
                    if (isReserved(name)) {
                        t.send(p, "You can not set a home with the name "" + name + """);
                        return true;
                    }
                    boolean override = args.length >= 3 && args[2].equalsIgnoreCase("--override");

                    if (!override && storage.hasHome(u, name)) {
                        t.send(p, "You already have a home named "" + name + "". Use "--override" to reset it to your current location!");
                        return true;
                    }

                    if (!isAdmin(p) && !override && !storage.hasHome(u, name)) {
                        if (storage.getHomeAmt(u) >= maxHomes()) {
                            t.send(p, "You cannot add homes until you delete 1 or more! There is a limit of <light_red>" + maxHomes() + "</light_red>");
                            return true;
                        }
                        storage.addHomeAmt(u, 1);
                    }

                    storage.setHome(u, name, p.getLocation());
                    t.send(p, override
                            ? "Reset home "" + name + "" to your current location!"
                            : "Set home "" + name + "" to your current location!");
                    return true;
                }

                case "delete":
                case "del":
                case "remove":
                case "rem": {
                    if (args.length < 2) {
                        t.send(p, "<yellow>Usage:</yellow> /home delete <name>");
                        return true;
                    }
                    String name = args[1].toLowerCase(Locale.ROOT);
                    if (!storage.hasHome(u, name)) {
                        t.send(p, "You do not have a home named "" + name + """);
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
                    t.send(p, "Your home "" + name + "" has been deleted!");
                    return true;
                }

                case "invite":
                case "inv": {
                    if (args.length < 3) {
                        t.send(p, "<yellow>Usage:</yellow> /home invite <home> <player>");
                        return true;
                    }
                    String name = args[1].toLowerCase(Locale.ROOT);
                    String targetName = args[2];

                    if (!storage.hasHome(u, name)) {
                        t.send(p, "Cannot invite " + targetName + " to "" + name + "" because it does not exist");
                        return true;
                    }
                    if (storage.isPublic(u, name)) {
                        t.send(p, "You cannot add anyone to " + name + " because it is public!");
                        return true;
                    }

                    Optional<UUID> tu = Storage.uuidFromNameExact(targetName);
                    if (tu.isEmpty()) {
                        t.send(p, "<light_red>That player has never joined.");
                        return true;
                    }

                    UUID target = tu.get();
                    if (storage.isInvited(target, u, name)) {
                        t.send(p, "You cannot invite " + Storage.nameOf(target) + " to "" + name + "" because they are already invited!");
                        return true;
                    }
                    if (storage.getHomeAmt(u) <= 0) {
                        t.send(p, "You have <light_red>" + storage.getHomeAmt(u) + "</light_red> homes to invite " + targetName + " to!");
                        return true;
                    }

                    storage.invite(target, u, name);
                    t.send(p, "You have added " + Storage.nameOf(target) + " to "" + name + """);
                    Player tp = Bukkit.getPlayer(target);
                    if (tp != null) {
                        t.send(tp, "You have been added to " + p.getName() + "'s home "" + name + "", use "/home " + p.getName() + ":" + name + "" to get there!");
                    }
                    return true;
                }

                case "uninvite":
                case "uninv": {
                    if (args.length < 3) {
                        t.send(p, "<yellow>Usage:</yellow> /home uninvite <home> <player>");
                        return true;
                    }
                    String name = args[1].toLowerCase(Locale.ROOT);
                    String targetName = args[2];

                    if (!storage.hasHome(u, name)) {
                        t.send(p, "Cannot uninvite " + targetName + " to "" + name + "" because it does not exist!");
                        return true;
                    }
                    if (storage.isPublic(u, name)) {
                        t.send(p, "Cannot uninvite " + targetName + " from "" + name + "" because it is Public!");
                        return true;
                    }

                    Optional<UUID> tu = Storage.uuidFromNameExact(targetName);
                    if (tu.isEmpty()) {
                        t.send(p, "<light_red>That player has never joined.");
                        return true;
                    }
                    UUID target = tu.get();
                    if (!storage.isInvited(target, u, name)) {
                        t.send(p, "Cannot uninvite " + Storage.nameOf(target) + " from "" + name + "" because they are not invited!");
                        return true;
                    }
                    storage.uninvite(target, u, name);
                    t.send(p, "You have uninvited " + Storage.nameOf(target) + " from "" + name + ""!");
                    return true;
                }

                case "public":
                case "pub": {
                    if (args.length < 2) {
                        t.send(p, "You need to specify a home!");
                        return true;
                    }
                    String name = args[1].toLowerCase(Locale.ROOT);
                    if (!storage.hasHome(u, name)) {
                        t.send(p, "You do not have a home named "" + name + """);
                        return true;
                    }
                    if (storage.isPublic(u, name)) {
                        t.send(p, "Cannot make "" + name + "" public because it already is!");
                        return true;
                    }
                    storage.makePublic(u, name);
                    t.send(p, "You have set "" + name + "" to Public!");
                    if (!isAdmin(p)) {
                        storage.addHomeAmt(u, -1);
                    }
                    return true;
                }

                case "private":
                case "priv": {
                    if (args.length < 2) {
                        t.send(p, "You need to specify a home!");
                        return true;
                    }
                    String name = args[1].toLowerCase(Locale.ROOT);
                    if (!storage.isPublic(u, name)) {
                        t.send(p, "Cannot privatize "" + name + "" because it already is!");
                        return true;
                    }
                    if (!isAdmin(p)) {
                        if (storage.getHomeAmt(u) >= maxHomes()) {
                            t.send(p, "Cannot privatize "" + name + "" because you are at your max amount of homes! Remove one or delete this one");
                            return true;
                        }
                        storage.addHomeAmt(u, 1);
                    }
                    storage.makePrivate(u, name);
                    t.send(p, "You have made "" + name + "" private!");
                    return true;
                }

                case "list": {
                    if (args.length < 2) {
                        t.send(p, "You need to specify if you want to list your homes, homes you are invited to, or all public homes");
                        return true;
                    }
                    String which = args[1].toLowerCase(Locale.ROOT);
                    int page = (args.length >= 3) ? parseIntSafe(args[2], 1) : 1;
                    switch (which) {
                        case "mine":
                            if (isAdmin(p)) {
                                listMineAdmin(p, page);
                            } else {
                                listMineDefault(p);
                            }
                            return true;
                        case "invited":
                            listInvited(p, page);
                            return true;
                        case "public":
                            listPublic(p, page);
                            return true;
                        default:
                            t.send(p, "Unknown list type. Use <yellow>mine</yellow>, <yellow>public</yellow>, or <yellow>invited</yellow>.");
                            return true;
                    }
                }

                case "clear": {
                    if (args.length >= 2 && isAdmin(p)) {
                        String target = args[1];
                        Optional<UUID> ou = Storage.uuidFromNameExact(target);
                        if (ou.isEmpty()) {
                            t.send(p, "<light_red>That player has never joined.");
                            return true;
                        }
                        storage.clearAllOf(ou.get());
                        t.send(p, "You have cleared the homes of " + target + "!");
                        return true;
                    } else {
                        if (!isAdmin(p)) {
                            storage.clearAllOf(u);
                            t.send(p, "You have cleared your homes!");
                            return true;
                        }
                        storage.clearAllOf(u);
                        t.send(p, "You have cleared your homes!");
                        return true;
                    }
                }

                case "reload": {
                    if (!isAdmin(p)) { t.send(p, "<red>No permission."); return true; }
                    plugin.reloadConfig();
                    t.send(p, "<green>Config reloaded.");
                    return true;
                }

                case "bed": {
                    var bed = p.getBedSpawnLocation();
                    if (bed != null) {
                        p.teleportAsync(bed);
                        t.send(p, "You have been teleported!");
                    } else {
                        t.send(p, "Your bed is missing or obstructed!");
                    }
                    return true;
                }
            }

            String name = args[0].toLowerCase(Locale.ROOT);
            if (storage.hasHome(u, name)) {
                Location loc = storage.getHome(u, name);
                if (loc == null) {
                    t.send(p, "<light_red>That home doesn’t exist anymore.");
                    return true;
                }
                p.teleportAsync(loc);
                t.send(p, "You have been teleported!");
                return true;
            }
            t.send(p, "<yellow>Unknown subcommand or home name.</yellow>");
            return true;
        }

        return true;
    }

    private boolean listMineDefault(Player p) {
        UUID u = p.getUniqueId();
        List<String> names = storage.getHomeNames(u).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        int total = names.size();
        if (total == 0) {
            t.send(p, "<yellow>No homes available!</yellow>");
            return true;
        }
        t.send(p, "&e--- My Homes (" + storage.getHomeAmt(u) + "/" + maxHomes() + ") ---".replace("&","§"));
        int i = 1;
        for (String nm : names.stream().limit(maxHomes()).toList()) {
            boolean pub = storage.isPublic(u, nm);
            String state = pub ? "<light_blue><bold>Public</bold></light_blue>" : "<light_red><bold>Private</bold></light_red>";
            var line = t.comp(state + " <gray>|</gray> <light_green><underlined>" + nm + "</underlined>")
                    .clickEvent(ClickEvent.runCommand("/home " + nm));
            p.sendMessage(line);
            i++;
        }
        return true;
    }

    private void listMineAdmin(Player p, int page) {
        UUID u = p.getUniqueId();
        List<String> names = storage.getHomeNames(u).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        paginateStatusList(p, "My Homes", names, page, (nm) -> {
            boolean pub = storage.isPublic(u, nm);
            String state = pub ? "<light_blue><bold>Public</bold></light_blue>" : "<light_red><bold>Private</bold></light_red>";
            return t.comp(state + " <gray>|</gray> <light_green><underlined>" + nm + "</underlined>")
                    .clickEvent(ClickEvent.runCommand("/home " + nm));
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
            return t.comp("<light_green><underlined>" + ownerName + " - " + homeName + "</underlined>")
                    .clickEvent(ClickEvent.runCommand("/home " + ownerName + ":" + homeName));
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
            return t.comp("<light_green><underlined>" + ownerName + " - " + homeName + "</underlined>")
                    .clickEvent(ClickEvent.runCommand("/home " + ownerName + ":" + homeName));
        }, (pg) -> "/home list public " + pg);
    }

    private interface LineBuilder { net.kyori.adventure.text.Component build(String entry); }
    private interface PageCmd { String cmd(int page); }

    private void paginateStatusList(Player p, String title, List<String> entries, int page, LineBuilder builder, PageCmd pageCmd) {
        int total = entries.size();
        if (total == 0) { t.send(p, "<yellow>No " + title.toLowerCase(Locale.ROOT) + " available!</yellow>"); return; }
        int totalPages = (int)Math.ceil(total / (double)PAGE_SIZE);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int start = (page-1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, total);

        t.send(p, "&e--- " + title + " (Page " + page + "/" + totalPages + ") ---".replace("&","§"));
        int lineNum = start + 1;
        for (int i = start; i < end; i++) {
            var comp = builder.build(entries.get(i));
            p.sendMessage(t.comp("<green>" + lineNum + ". </green>").append(comp));
            lineNum++;
        }

        List<net.kyori.adventure.text.Component> nav = new ArrayList<>();
        if (page > 1) {
            nav.add(t.comp("<aqua>[Back]").clickEvent(ClickEvent.runCommand(pageCmd.cmd(page-1))));
        }
        if (page < totalPages) {
            if (!nav.isEmpty()) nav.add(t.comp(" <gray>|</gray> "));
            nav.add(t.comp("<aqua>[Next]").clickEvent(ClickEvent.runCommand(pageCmd.cmd(page+1))));
        }
        if (!nav.isEmpty()) {
            var line = nav.get(0);
            for (int i=1;i<nav.size();i++) line = line.append(nav.get(i));
            p.sendMessage(line);
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
