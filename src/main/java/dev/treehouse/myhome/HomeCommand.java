package dev.treehouse.myhome;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

    private boolean isAdmin(CommandSender s) { return s.hasPermission("MyHome.admin"); }
    private int maxHomes() { return plugin.getConfig().getInt("maxHomes", 8); }
    private int pageSize() { return Math.max(1, plugin.getConfig().getInt("pageSize", 8)); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { t.send(sender, "&cPlayers only."); return true; }
        UUID u = p.getUniqueId();
        if (storage.getHomeAmt(u) < 0) storage.setHomeAmt(u, 0);

        if (args.length == 0) return listMineDefault(p);

        String joined = String.join(" ", args);

        // /home player:name
        if (joined.contains(":") && args.length == 1) {
            String[] split = joined.split(":", 2);
            String ownerName = split[0], homeName = split[1];
            Optional<UUID> ou = Storage.uuidFromNameExact(ownerName);
            if (ou.isEmpty()) { t.send(p, "&cThat player has never joined."); return true; }
            UUID owner = ou.get();

            if (storage.isPublic(owner, homeName) || storage.isInvited(u, owner, homeName)) {
                Location loc = storage.getHome(owner, homeName);
                if (loc == null) { t.send(p, "&cThat home doesn’t exist anymore."); return true; }
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
                if (args.length < 2) { t.send(p, "&eUsage:&r /home set <name> [--override]"); return true; }
                String name = args[1].toLowerCase(Locale.ROOT);
                if (isReserved(name)) { t.send(p, "&7You can not set a home with the name &f\"" + name + "\""); return true; }
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
                t.send(p, override ? "&7Reset home &f\"" + name + "\" &7to your current location!"
                                   : "&7Set home &f\"" + name + "\" &7to your current location!");
                return true;
            }

            case "delete":
            case "del":
            case "remove":
            case "rem": {
                if (args.length < 2) { t.send(p, "&eUsage:&r /home delete <name>"); return true; }
                String name = args[1].toLowerCase(Locale.ROOT);
                if (!storage.hasHome(u, name)) { t.send(p, "&7You do not have a home named &f\"" + name + "\""); return true; }
                boolean wasPublic = storage.isPublic(u, name);
                storage.deleteHome(u, name);
                if (wasPublic) storage.makePrivate(u, name); else storage.addHomeAmt(u, -1);
                for (OfflinePlayer op : Bukkit.getOfflinePlayers()) storage.uninvite(op.getUniqueId(), u, name);
                t.send(p, "&7Your home &f\"" + name + "\" &7has been deleted!");
                return true;
            }

            case "invite":
            case "inv": {
                if (args.length < 3) { t.send(p, "&eUsage:&r /home invite <home> <player>"); return true; }
                String name = args[1].toLowerCase(Locale.ROOT), targetName = args[2];
                if (!storage.hasHome(u, name)) { t.send(p, "&7Cannot invite &f" + targetName + " &7to &f\"" + name + "\" &7because it does not exist"); return true; }
                if (storage.isPublic(u, name)) { t.send(p, "&7You cannot add anyone to &f" + name + " &7because it is public!"); return true; }

                Optional<UUID> tu = Storage.uuidFromNameExact(targetName);
                if (tu.isEmpty()) { t.send(p, "&cThat player has never joined."); return true; }
                UUID target = tu.get();

                if (storage.isInvited(target, u, name)) {
                    t.send(p, "&7You cannot invite &f" + Storage.nameOf(target) + " &7to &f\"" + name + "\" &7because they are already invited!");
                    return true;
                }
                if (storage.getHomeAmt(u) <= 0) { t.send(p, "&7You have &c" + storage.getHomeAmt(u) + "&7 homes to invite &f" + targetName + " &7to!"); return true; }

                storage.invite(target, u, name);
                t.send(p, "&7You have added &f" + Storage.nameOf(target) + " &7to &f\"" + name + "\"");
                Player tp = Bukkit.getPlayer(target);
                if (tp != null) t.send(tp, "&7You have been added to &f" + p.getName() + "&7's home &f\"" + name + "\"&7, use &f/home " + p.getName() + ":" + name + " &7to get there!");
                return true;
            }

            case "uninvite":
            case "uninv": {
                if (args.length < 3) { t.send(p, "&eUsage:&r /home uninvite <home> <player>"); return true; }
                String name = args[1].toLowerCase(Locale.ROOT), targetName = args[2];
                if (!storage.hasHome(u, name)) { t.send(p, "&7Cannot uninvite &f" + targetName + " &7to &f\"" + name + "\" &7because it does not exist!"); return true; }
                if (storage.isPublic(u, name)) { t.send(p, "&7Cannot uninvite &f" + targetName + " &7from &f\"" + name + "\" &7because it is Public!"); return true; }

                Optional<UUID> tu = Storage.uuidFromNameExact(targetName);
                if (tu.isEmpty()) { t.send(p, "&cThat player has never joined."); return true; }
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
                if (args.length < 2) { t.send(p, "&7You need to specify a home!"); return true; }
                String name = args[1].toLowerCase(Locale.ROOT);
                if (!storage.hasHome(u, name)) { t.send(p, "&7You do not have a home named &f\"" + name + "\""); return true; }
                if (storage.isPublic(u, name)) { t.send(p, "&7Cannot make &f\"" + name + "\" &7public because it already is!"); return true; }
                storage.makePublic(u, name);
                t.send(p, "&7You have set &f\"" + name + "\" &7to Public!");
                if (!isAdmin(p)) storage.addHomeAmt(u, -1);
                return true;
            }

            case "private":
            case "priv": {
                if (args.length < 2) { t.send(p, "&7You need to specify a home!"); return true; }
                String name = args[1].toLowerCase(Locale.ROOT);
                if (!storage.isPublic(u, name)) { t.send(p, "&7Cannot privatize &f\"" + name + "\" &7because it already is!"); return true; }
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
                if (args.length < 2) { t.send(p, "&7You need to specify if you want to list your homes, homes you are invited to, or all public homes"); return true; }
                String which = args[1].toLowerCase(Locale.ROOT);
                int page = (args.length >= 3) ? parseIntSafe(args[2], 1) : 1;
                switch (which) {
                    case "mine":    if (isAdmin(p)) listMineAdmin(p, page); else listMineDefault(p); return true;
                    case "invited": listInvited(p, page); return true;
                    case "public":  listPublic(p, page);  return true;
                    default: t.send(p, "&7Unknown list type. Use &emine&7, &epublic&7, or &einvited&7."); return true;
                }
            }

            case "clear": {
                if (args.length >= 2 && isAdmin(p)) {
                    String target = args[1];
                    Optional<UUID> ou = Storage.uuidFromNameExact(target);
                    if (ou.isEmpty()) { t.send(p, "&cThat player has never joined."); return true; }
                    storage.clearAllOf(ou.get());
                    t.send(p, "&7You have cleared the homes of &f" + target + "&7!");
                    return true;
                } else {
                    storage.clearAllOf(u);
                    t.send(p, "&7You have cleared your homes!");
                    return true;
                }
            }

            case "reload": { if (!isAdmin(p)) { t.send(p, "&cNo permission."); return true; } plugin.reloadConfig(); t.send(p, "&aConfig reloaded."); return true; }
            case "bed": {
                var bed = p.getBedSpawnLocation();
                if (bed != null) { p.teleportAsync(bed); t.send(p, "&7You have been teleported!"); }
                else t.send(p, "&7Your bed is missing or obstructed!");
                return true;
            }
        }

        // Fallback: teleport to own home by name
        String name = args[0].toLowerCase(Locale.ROOT);
        if (storage.hasHome(u, name)) {
            Location loc = storage.getHome(u, name);
            if (loc == null) { t.send(p, "&cThat home doesn’t exist anymore."); return true; }
            p.teleportAsync(loc);
            t.send(p, "&7You have been teleported!");
            return true;
        }
        t.send(p, "&eUnknown subcommand or home name.");
        return true;
    }

    /* =======================
       LIST RENDERING (Clickable, no prefix)
       ======================= */

    private boolean listMineDefault(Player p) {
        UUID u = p.getUniqueId();
        List<String> names = storage.getHomeNames(u).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        if (names.isEmpty()) { p.sendMessage(Component.text("--- My Homes ---", NamedTextColor.YELLOW)); p.sendMessage(Component.text("No homes available!", NamedTextColor.YELLOW)); return true; }

        paginateComponents(
                p,
                Component.text("--- My Homes ", NamedTextColor.YELLOW)
                        .append(Component.text("(Page ", NamedTextColor.YELLOW))
                        .append(Component.text("?", NamedTextColor.YELLOW)) // placeholder, replaced inside paginateComponents
                        .append(Component.text("/", NamedTextColor.YELLOW))
                        .append(Component.text("?", NamedTextColor.YELLOW))
                        .append(Component.text(") ---", NamedTextColor.YELLOW)),
                names,
                (nm, ownerName) -> {
                    boolean pub = storage.isPublic(u, nm);
                    Component status = Component.text(pub ? "Public" : "Private",
                            pub ? NamedTextColor.AQUA : NamedTextColor.RED).decorate(TextDecoration.BOLD);
                    Component bar = Component.text(" | ", NamedTextColor.WHITE);
                    Component clickable = clickableHome(null, nm); // /home <nm>
                    return status.append(bar).append(clickable);
                },
                pageSize(),
                (page) -> "/home list mine " + page
        );
        return true;
    }

    private void listMineAdmin(Player p, int page) {
        UUID u = p.getUniqueId();
        List<String> names = storage.getHomeNames(u).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();

        paginateComponents(
                p,
                Component.text("--- My Homes (Page ?/?) ---", NamedTextColor.YELLOW),
                names,
                (nm, ownerName) -> {
                    boolean pub = storage.isPublic(u, nm);
                    Component status = Component.text(pub ? "Public" : "Private",
                            pub ? NamedTextColor.AQUA : NamedTextColor.RED).decorate(TextDecoration.BOLD);
                    Component bar = Component.text(" | ", NamedTextColor.WHITE);
                    Component clickable = clickableHome(null, nm); // /home <nm>
                    return status.append(bar).append(clickable);
                },
                pageSize(),
                (pg) -> "/home list mine " + pg,
                page
        );
    }

    private void listInvited(Player p, int page) {
        UUID u = p.getUniqueId();
        List<String> entries = new ArrayList<>(storage.getInvited(u));
        entries.sort(String.CASE_INSENSITIVE_ORDER);

        paginateComponents(
                p,
                Component.text("--- My Invited Homes (Page ?/?) ---", NamedTextColor.YELLOW),
                entries,
                (entry, ownerNameIgnored) -> {
                    String[] sp = entry.split(":",2);
                    UUID owner = UUID.fromString(sp[0]);
                    String ownerName = Storage.nameOf(owner);
                    String homeName = sp[1];

                    Component left = Component.text("[", NamedTextColor.GREEN)
                            .append(Component.text(ownerName, NamedTextColor.AQUA, TextDecoration.BOLD))
                            .append(Component.text(" - ", NamedTextColor.GRAY));

                    Component nameClickable = Component.text(homeName, NamedTextColor.YELLOW, TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.runCommand("/home " + ownerName + ":" + homeName));

                    Component right = Component.text("]", NamedTextColor.GREEN);
                    return left.append(nameClickable).append(right);
                },
                pageSize(),
                (pg) -> "/home list invited " + pg,
                page
        );
    }

    private void listPublic(Player p, int page) {
        List<String> entries = new ArrayList<>(storage.getPublicHomes());
        entries.sort(String.CASE_INSENSITIVE_ORDER);

        paginateComponents(
                p,
                Component.text("--- Public Homes (Page ?/?) ---", NamedTextColor.YELLOW),
                entries,
                (entry, ownerNameIgnored) -> {
                    String[] sp = entry.split(":",2);
                    UUID owner = UUID.fromString(sp[0]);
                    String ownerName = Storage.nameOf(owner);
                    String homeName = sp[1];

                    Component left = Component.text("[", NamedTextColor.GREEN)
                            .append(Component.text(ownerName, NamedTextColor.AQUA, TextDecoration.BOLD))
                            .append(Component.text(" - ", NamedTextColor.GRAY));

                    Component nameClickable = Component.text(homeName, NamedTextColor.YELLOW, TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.runCommand("/home " + ownerName + ":" + homeName));

                    Component right = Component.text("]", NamedTextColor.GREEN);
                    return left.append(nameClickable).append(right);
                },
                pageSize(),
                (pg) -> "/home list public " + pg,
                page
        );
    }

    /* ===== Component helpers ===== */

    private Component clickableHome(String ownerNameOrNull, String homeName) {
        // for your own homes: /home <home>
        // for others:        /home owner:home
        String cmd = ownerNameOrNull == null ? "/home " + homeName : "/home " + ownerNameOrNull + ":" + homeName;
        return Component.text("[", NamedTextColor.GREEN)
                .append(Component.text(homeName, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand(cmd)))
                .append(Component.text("]", NamedTextColor.GREEN));
    }

    @FunctionalInterface
    private interface LineBuilder { Component build(String entry, String ownerNameIfAny); }

    @FunctionalInterface
    private interface PageCmd { String cmd(int page); }

    private void paginateComponents(
            Player p,
            Component headerTemplate,
            List<String> entries,
            LineBuilder lineBuilder,
            int perPage,
            PageCmd pageCmd
    ) { paginateComponents(p, headerTemplate, entries, lineBuilder, perPage, pageCmd, 1); }

    private void paginateComponents(
            Player p,
            Component headerTemplate,
            List<String> entries,
            LineBuilder lineBuilder,
            int perPage,
            PageCmd pageCmd,
            int page
    ) {
        int total = entries.size();
        if (total == 0) {
            p.sendMessage(headerTemplate.color(NamedTextColor.YELLOW));
            p.sendMessage(Component.text("No entries available!", NamedTextColor.YELLOW));
            return;
        }
        int totalPages = (int)Math.ceil(total / (double)perPage);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int start = (page-1) * perPage;
        int end = Math.min(start + perPage, total);

        // Header without prefix
        Component header = Component.text("--- ", NamedTextColor.YELLOW)
                .append(Component.text(headerTitleFromTemplate(headerTemplate), NamedTextColor.YELLOW))
                .append(Component.text(" (Page " + page + "/" + totalPages + ") ---", NamedTextColor.YELLOW));
        p.sendMessage(header);

        int lineNum = start + 1;
        for (int i = start; i < end; i++) {
            String entry = entries.get(i);
            Component number = Component.text(lineNum + ". ", NamedTextColor.GREEN);
            Component payload = lineBuilder.build(entry, null);
            p.sendMessage(number.append(payload));
            lineNum++;
        }

        // Footer nav (clickable)
        if (totalPages > 1) {
            List<Component> parts = new ArrayList<>();
            if (page > 1) {
                parts.add(Component.text("[Back]", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand(pageCmd.cmd(page - 1))));
            }
            if (page < totalPages) {
                if (!parts.isEmpty()) parts.add(Component.text(" | ", NamedTextColor.GRAY));
                parts.add(Component.text("[Next]", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand(pageCmd.cmd(page + 1))));
            }
            if (!parts.isEmpty()) {
                Component nav = parts.get(0);
                for (int i = 1; i < parts.size(); i++) nav = nav.append(parts.get(i));
                p.sendMessage(nav);
            }
        }
    }

    private String headerTitleFromTemplate(Component headerTemplate) {
        // crude title extractor; we pass explicit templates above so this is fine
        return switch (headerTemplate.toString()) {
            default -> "List";
        };
    }

    /* ===== rest of file: tab-complete, utils ===== */

    private boolean isReserved(String s) {
        return Set.of("set","create","add","delete","del","rem","remove",
                "public","pub","private","priv","invite","inv","uninvite","uninv",
                "mine","list","clear","reload","bed").contains(s.toLowerCase(Locale.ROOT));
    }

    private int parseIntSafe(String s, int def) { try { return Integer.parseInt(s); } catch (Exception e) { return def; } }

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
            if (a1.equals("list")) return prefixFilter(List.of("mine","public","invited"), args[1]);
            if (a1.equals("clear") && isAdmin(p)) return prefixFilter(storage.offlineNames(), args[1]);
            if (Set.of("invite","inv","uninvite","uninv").contains(a1)) return prefixFilter(myNames, args[1]);
        }

        if (args.length == 3) {
            String a1 = args[0].toLowerCase(Locale.ROOT);
            if (Set.of("invite","inv","uninvite","uninv").contains(a1)) return prefixFilter(storage.offlineNames(), args[2]);
            if (Set.of("set","create","add").contains(a1)) return prefixFilter(List.of("--override"), args[2]);
        }

        return List.of();
    }

    private List<String> prefixFilter(Collection<String> base, String token) {
        String tkn = token.toLowerCase(Locale.ROOT);
        return base.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(tkn)).limit(50).collect(Collectors.toList());
    }
}
