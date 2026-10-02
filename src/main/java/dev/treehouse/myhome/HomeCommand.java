package dev.treehouse.myhome;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class HomeCommand implements TabExecutor {

    private final MyHomePlugin plugin;
    private final Storage storage;
    private final Text t;

    /** Cache for offline-player display names (avoids blocking lookups in list rendering). */
    private final Map<UUID, String> nameCache = new HashMap<>();

    /** Pending two-step clear confirmations: sender UUID -> target (null = self) + timestamp. */
    private final Map<UUID, PendingClear> pendingClears = new HashMap<>();
    private record PendingClear(UUID target, long at) {}

    private static final long CLEAR_CONFIRM_WINDOW_MS = 60_000;

    public HomeCommand(MyHomePlugin plugin, Storage storage, Text t) {
        this.plugin = plugin;
        this.storage = storage;
        this.t = t;
    }

    private boolean isAdmin(CommandSender s) { return s.hasPermission("MyHome.admin"); }
    private int maxHomes() { return plugin.getConfig().getInt("maxHomes", 8); }
    private int pageSize() { return Math.max(1, plugin.getConfig().getInt("pageSize", 8)); }

    private String displayName(UUID id) {
        return nameCache.computeIfAbsent(id, Storage::nameOf);
    }

    /* Home names are used as YAML path segments and in owner:name tokens,
       so they must be path-safe: lowercase alphanumerics, dash, underscore. */
    private boolean isValidName(String s) {
        return s != null && s.matches("[a-z0-9_-]{1,32}");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            t.send(sender, "&cPlayers only.");
            return true;
        }
        UUID u = p.getUniqueId();

        // /home  -> default list
        if (args.length == 0) {
            return listMineDefault(p);
        }

        String joined = String.join(" ", args);

        // /home owner:name  (teleport to someone else's home if public or invited)
        if (args.length == 1 && joined.contains(":")) {
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
                    t.send(p, "&cThat home doesn't exist anymore.");
                    return true;
                }
                teleportSafe(p, loc);
                t.send(p, "&7You have been teleported!");
            } else {
                t.send(p, "&7That home is not public, or you were not invited");
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            /* =======================
               CREATE / SET
               ======================= */
            case "set":
            case "create":
            case "add": {
                if (args.length < 2) {
                    t.send(p, "&eUsage:&r /home set <name> [--override]");
                    return true;
                }
                String name = args[1].toLowerCase(Locale.ROOT);
                if (!isValidName(name)) {
                    t.send(p, "&7Home names can only use &fa-z&7, &f0-9&7, &f-&7 and &f_&7 (max 32 chars).");
                    return true;
                }
                if (isReserved(name)) {
                    t.send(p, "&7You can not set a home with the name &f\"" + name + "\"");
                    return true;
                }
                boolean override = args.length >= 3 && args[2].equalsIgnoreCase("--override");

                if (!override && storage.hasHome(u, name)) {
                    t.send(p, "&7You already have a home named &f\"" + name + "\"&7. Use &f--override&7 to reset it to your current location!");
                    return true;
                }

                // limit only counts PRIVATE homes
                if (!isAdmin(p) && !override && !storage.hasHome(u, name)) {
                    if (storage.privateCount(u) >= maxHomes()) {
                        t.send(p, "&7You cannot add homes until you delete or make one public! Limit: &c" + maxHomes());
                        return true;
                    }
                }

                storage.setHome(u, name, p.getLocation());
                t.send(p, override
                        ? "&7Reset home &f\"" + name + "\" &7to your current location!"
                        : "&7Set home &f\"" + name + "\" &7to your current location!");
                return true;
            }

            /* =======================
               RENAME
               ======================= */
            case "rename":
            case "ren": {
                if (args.length < 3) {
                    t.send(p, "&eUsage:&r /home rename <old> <new>");
                    return true;
                }
                String oldName = args[1].toLowerCase(Locale.ROOT);
                String newName = args[2].toLowerCase(Locale.ROOT);
                if (!isValidName(newName)) {
                    t.send(p, "&7Home names can only use &fa-z&7, &f0-9&7, &f-&7 and &f_&7 (max 32 chars).");
                    return true;
                }
                if (isReserved(newName)) {
                    t.send(p, "&7You can not rename a home to &f\"" + newName + "\"");
                    return true;
                }
                if (!storage.hasHome(u, oldName)) {
                    t.send(p, "&7You do not have a home named &f\"" + oldName + "\"");
                    return true;
                }
                if (storage.hasHome(u, newName)) {
                    t.send(p, "&7You already have a home named &f\"" + newName + "\"");
                    return true;
                }
                storage.renameHome(u, oldName, newName);
                t.send(p, "&7Renamed &f\"" + oldName + "\" &7to &f\"" + newName + "\"&7!");
                return true;
            }

            /* =======================
               DELETE
               ======================= */
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
                storage.deleteHome(u, name); // also removes from public + clears invites referencing it
                t.send(p, "&7Your home &f\"" + name + "\" &7has been deleted!");
                return true;
            }

            /* =======================
               INVITE / UNINVITE
               ======================= */
            case "invite":
            case "inv": {
                if (args.length < 3) {
                    t.send(p, "&eUsage:&r /home invite <home> <player>");
                    return true;
                }
                return doInvite(p, u, args[1].toLowerCase(Locale.ROOT), args[2], false, null);
            }

            case "uninvite":
            case "uninv": {
                if (args.length < 3) {
                    t.send(p, "&eUsage:&r /home uninvite <home> <player>");
                    return true;
                }
                return doUninvite(p, u, args[1].toLowerCase(Locale.ROOT), args[2], false, null);
            }

            /* =======================
               PUBLIC / PRIVATE
               ======================= */
            case "public":
            case "pub": {
                if (args.length < 2) {
                    t.send(p, "&7You need to specify a home!");
                    return true;
                }
                return doVisibility(p, u, args[1].toLowerCase(Locale.ROOT), true, false, null);
            }

            case "private":
            case "priv": {
                if (args.length < 2) {
                    t.send(p, "&7You need to specify a home!");
                    return true;
                }
                return doVisibility(p, u, args[1].toLowerCase(Locale.ROOT), false, false, null);
            }

            /* =======================
               ADMIN (manage another player's homes)
               ======================= */
            case "admin": {
                if (!isAdmin(p)) {
                    t.send(p, "&cNo permission.");
                    return true;
                }
                if (args.length < 3) {
                    t.send(p, "&eUsage:&r /home admin <player> <set|rename|delete|invite|uninvite|public|private|list|clear> [args]");
                    return true;
                }
                Optional<UUID> ou = Storage.uuidFromNameExact(args[1]);
                if (ou.isEmpty()) {
                    t.send(p, "&cThat player has never joined.");
                    return true;
                }
                UUID target = ou.get();
                String targetName = displayName(target);
                String action = args[2].toLowerCase(Locale.ROOT);
                String[] rest = Arrays.copyOfRange(args, 3, args.length);

                switch (action) {
                    case "set": {
                        if (rest.length < 1) {
                            t.send(p, "&eUsage:&r /home admin <player> set <name>");
                            return true;
                        }
                        String name = rest[0].toLowerCase(Locale.ROOT);
                        if (!isValidName(name) || isReserved(name)) {
                            t.send(p, "&7That is not a valid home name.");
                            return true;
                        }
                        storage.setHome(target, name, p.getLocation());
                        // keep invite/public cleanup consistent if overwriting
                        t.send(p, "&7Set home &f\"" + name + "\" &7for &f" + targetName + " &7at your location!");
                        return true;
                    }
                    case "rename": {
                        if (rest.length < 2) {
                            t.send(p, "&eUsage:&r /home admin <player> rename <old> <new>");
                            return true;
                        }
                        String oldName = rest[0].toLowerCase(Locale.ROOT);
                        String newName = rest[1].toLowerCase(Locale.ROOT);
                        if (!isValidName(newName) || isReserved(newName)) {
                            t.send(p, "&7That is not a valid home name.");
                            return true;
                        }
                        if (!storage.hasHome(target, oldName)) {
                            t.send(p, "&f" + targetName + " &7does not have a home named &f\"" + oldName + "\"");
                            return true;
                        }
                        if (storage.hasHome(target, newName)) {
                            t.send(p, "&f" + targetName + " &7already has a home named &f\"" + newName + "\"");
                            return true;
                        }
                        storage.renameHome(target, oldName, newName);
                        t.send(p, "&7Renamed &f" + targetName + "&7's home &f\"" + oldName + "\" &7to &f\"" + newName + "\"&7!");
                        return true;
                    }
                    case "delete":
                    case "del":
                    case "remove":
                    case "rem": {
                        if (rest.length < 1) {
                            t.send(p, "&eUsage:&r /home admin <player> delete <name>");
                            return true;
                        }
                        String name = rest[0].toLowerCase(Locale.ROOT);
                        if (!storage.hasHome(target, name)) {
                            t.send(p, "&f" + targetName + " &7does not have a home named &f\"" + name + "\"");
                            return true;
                        }
                        storage.deleteHome(target, name);
                        t.send(p, "&7Deleted &f" + targetName + "&7's home &f\"" + name + "\"&7!");
                        return true;
                    }
                    case "invite":
                    case "inv": {
                        if (rest.length < 2) {
                            t.send(p, "&eUsage:&r /home admin <player> invite <home> <target>");
                            return true;
                        }
                        return doInvite(p, target, rest[0].toLowerCase(Locale.ROOT), rest[1], true, targetName);
                    }
                    case "uninvite":
                    case "uninv": {
                        if (rest.length < 2) {
                            t.send(p, "&eUsage:&r /home admin <player> uninvite <home> <target>");
                            return true;
                        }
                        return doUninvite(p, target, rest[0].toLowerCase(Locale.ROOT), rest[1], true, targetName);
                    }
                    case "public":
                    case "pub": {
                        if (rest.length < 1) {
                            t.send(p, "&eUsage:&r /home admin <player> public <name>");
                            return true;
                        }
                        return doVisibility(p, target, rest[0].toLowerCase(Locale.ROOT), true, true, targetName);
                    }
                    case "private":
                    case "priv": {
                        if (rest.length < 1) {
                            t.send(p, "&eUsage:&r /home admin <player> private <name>");
                            return true;
                        }
                        return doVisibility(p, target, rest[0].toLowerCase(Locale.ROOT), false, true, targetName);
                    }
                    case "list": {
                        int page = (rest.length >= 1) ? parseIntSafe(rest[0], 1) : 1;
                        return listHomesOf(p, target, targetName, page);
                    }
                    case "clear": {
                        pendingClears.put(u, new PendingClear(target, System.currentTimeMillis()));
                        t.send(p, "&cThis will delete ALL homes of &f" + targetName + "&c. Type &f/home clear confirm &cto proceed.");
                        return true;
                    }
                    default:
                        t.send(p, "&7Unknown admin action. Use &eset&7, &erename&7, &edelete&7, &einvite&7, &euninvite&7, &epublic&7, &eprivate&7, &elist&7, or &eclear&7.");
                        return true;
                }
            }

            /* =======================
               LISTS
               ======================= */
            case "list": {
                if (args.length < 2) {
                    t.send(p, "&7You need to specify if you want to list your homes, homes you are invited to, or all public homes");
                    return true;
                }
                String which = args[1].toLowerCase(Locale.ROOT);
                int page = (args.length >= 3) ? parseIntSafe(args[2], 1) : 1;
                switch (which) {
                    case "mine":    if (isAdmin(p)) listMineAdmin(p, page); else listMineDefault(p); return true;
                    case "invited": listInvited(p, page); return true;
                    case "public":  listPublic(p, page);  return true;
                    default:
                        t.send(p, "&7Unknown list type. Use &emine&7, &epublic&7, or &einvited&7.");
                        return true;
                }
            }

            /* =======================
               HELP
               ======================= */
            case "help": {
                sendHelp(p);
                return true;
            }

            /* =======================
               OTHER
               ======================= */
            case "clear": {
                // /home clear confirm  -> execute a pending clear
                if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
                    PendingClear pc = pendingClears.remove(u);
                    if (pc == null || System.currentTimeMillis() - pc.at() > CLEAR_CONFIRM_WINDOW_MS) {
                        t.send(p, "&7Nothing to confirm. Use &f/home clear &7first.");
                        return true;
                    }
                    UUID target = pc.target();
                    if (target != null && !isAdmin(p)) {
                        t.send(p, "&cNo permission.");
                        return true;
                    }
                    UUID victim = (target != null) ? target : u;
                    storage.clearAllOf(victim);
                    nameCache.remove(victim);
                    if (target != null) {
                        t.send(p, "&7You have cleared the homes of &f" + displayName(target) + "&7!");
                    } else {
                        t.send(p, "&7You have cleared your homes!");
                    }
                    return true;
                }
                // /home clear <player>  (admin) -> arm a two-step confirm
                if (args.length >= 2) {
                    if (!isAdmin(p)) {
                        t.send(p, "&cNo permission.");
                        return true;
                    }
                    Optional<UUID> ou = Storage.uuidFromNameExact(args[1]);
                    if (ou.isEmpty()) {
                        t.send(p, "&cThat player has never joined.");
                        return true;
                    }
                    pendingClears.put(u, new PendingClear(ou.get(), System.currentTimeMillis()));
                    t.send(p, "&cThis will delete ALL homes of &f" + args[1] + "&c. Type &f/home clear confirm &cto proceed.");
                    return true;
                }
                // /home clear  (self) -> arm a two-step confirm
                pendingClears.put(u, new PendingClear(null, System.currentTimeMillis()));
                t.send(p, "&cThis will delete ALL of your homes. Type &f/home clear confirm &cto proceed.");
                return true;
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
                    teleportSafe(p, bed);
                    t.send(p, "&7You have been teleported!");
                } else {
                    t.send(p, "&7Your bed is missing or obstructed!");
                }
                return true;
            }
        }

        // Fallback: /home <name>  (teleport to own home)
        String name = args[0].toLowerCase(Locale.ROOT);
        if (storage.hasHome(u, name)) {
            Location loc = storage.getHome(u, name);
            if (loc == null) {
                t.send(p, "&cThat home doesn't exist anymore.");
                return true;
            }
            teleportSafe(p, loc);
            t.send(p, "&7You have been teleported!");
            return true;
        }
        t.send(p, "&eUnknown subcommand or home name. Try &f/home help&e.");
        return true;
    }

    /* ===================================================
       SHARED OPERATIONS (self + admin)
       =================================================== */

    private boolean doInvite(Player sender, UUID owner, String home, String targetName,
                             boolean adminView, String ownerLabel) {
        String who = adminView ? "&f" + ownerLabel + "&7's home &f\"" + home + "\"" : "&f\"" + home + "\"";
        if (!storage.hasHome(owner, home)) {
            t.send(sender, "&7Cannot invite &f" + targetName + " &7to " + who + " &7because it does not exist");
            return true;
        }
        if (storage.isPublic(owner, home)) {
            t.send(sender, "&7You cannot add anyone to " + who + " &7because it is public!");
            return true;
        }

        Optional<UUID> tu = Storage.uuidFromNameExact(targetName);
        if (tu.isEmpty()) {
            t.send(sender, "&cThat player has never joined.");
            return true;
        }
        UUID target = tu.get();

        if (storage.isInvited(target, owner, home)) {
            t.send(sender, "&7You cannot invite &f" + displayName(target) + " &7to " + who + " &7because they are already invited!");
            return true;
        }

        storage.invite(target, owner, home);
        t.send(sender, "&7You have added &f" + displayName(target) + " &7to " + who);
        Player tp = Bukkit.getPlayer(target);
        if (tp != null) {
            String ownerName = displayName(owner);
            t.send(tp, "&7You have been added to &f" + ownerName + "&7's home &f\"" + home + "\"&7, use &f/home " + ownerName + ":" + home + " &7to get there!");
        }
        return true;
    }

    private boolean doUninvite(Player sender, UUID owner, String home, String targetName,
                               boolean adminView, String ownerLabel) {
        String who = adminView ? "&f" + ownerLabel + "&7's home &f\"" + home + "\"" : "&f\"" + home + "\"";
        if (!storage.hasHome(owner, home)) {
            t.send(sender, "&7Cannot uninvite &f" + targetName + " &7from " + who + " &7because it does not exist!");
            return true;
        }
        if (storage.isPublic(owner, home)) {
            t.send(sender, "&7Cannot uninvite &f" + targetName + " &7from " + who + " &7because it is Public!");
            return true;
        }

        Optional<UUID> tu = Storage.uuidFromNameExact(targetName);
        if (tu.isEmpty()) {
            t.send(sender, "&cThat player has never joined.");
            return true;
        }
        UUID target = tu.get();

        if (!storage.isInvited(target, owner, home)) {
            t.send(sender, "&7Cannot uninvite &f" + displayName(target) + " &7from " + who + " &7because they are not invited!");
            return true;
        }

        storage.uninvite(target, owner, home);
        t.send(sender, "&7You have uninvited &f" + displayName(target) + " &7from " + who + "&7!");
        return true;
    }

    private boolean doVisibility(Player sender, UUID owner, String name, boolean makePublic,
                                 boolean adminView, String ownerLabel) {
        String who = adminView ? "&f" + ownerLabel + "&7's home &f\"" + name + "\"" : "&f\"" + name + "\"";
        if (!storage.hasHome(owner, name)) {
            t.send(sender, "&7" + (adminView ? ownerLabel + " does" : "You do") + " not have a home named " + who);
            return true;
        }
        if (makePublic) {
            if (storage.isPublic(owner, name)) {
                t.send(sender, "&7Cannot make " + who + " &7public because it already is!");
                return true;
            }
            storage.makePublic(owner, name);
            t.send(sender, "&7You have set " + who + " &7to Public!");
        } else {
            if (!storage.isPublic(owner, name)) {
                t.send(sender, "&7Cannot privatize " + who + " &7because it already is!");
                return true;
            }
            // privatizing increases the private count by 1; enforce the limit for non-admins on self
            if (!adminView && !isAdmin(sender) && storage.privateCount(owner) >= maxHomes()) {
                t.send(sender, "&7Cannot privatize " + who + " &7because you are at your max amount of homes! Remove one or make another public.");
                return true;
            }
            storage.makePrivate(owner, name);
            t.send(sender, "&7You have made " + who + " &7private!");
        }
        return true;
    }

    private void sendHelp(Player p) {
        t.send(p, "&a&lHome Help &8- &7all commands start with &f/home");
        t.send(p, "&f/home &7- list your homes (click to teleport)");
        t.send(p, "&f/home <name> &7- teleport to your home");
        t.send(p, "&f/home <owner>:<name> &7- teleport to a public/invited home");
        t.send(p, "&f/home set <name> [--override] &7- save your location");
        t.send(p, "&f/home rename <old> <new> &7- rename a home");
        t.send(p, "&f/home delete <name> &7- delete a home");
        t.send(p, "&f/home public|private <name> &7- toggle visibility");
        t.send(p, "&f/home invite|uninvite <home> <player>");
        t.send(p, "&f/home list <mine|public|invited> [page]");
        t.send(p, "&f/home bed &7- teleport to your bed");
        t.send(p, "&f/home clear &7- delete ALL your homes (asks first)");
        if (isAdmin(p)) {
            t.send(p, "&cAdmin: &f/home admin <player> <set|rename|delete|invite|uninvite|public|private|list|clear>");
            t.send(p, "&cAdmin: &f/home clear <player> &7/ &f/home reload");
        }
    }

    /* ===================================================
       SAFE TELEPORT
       =================================================== */

    /** Teleport, nudging to the nearest safe spot so players don't land inside blocks. */
    private void teleportSafe(Player p, Location loc) {
        Location dest = findSafeSpot(loc);
        p.teleportAsync(dest != null ? dest : loc);
    }

    private Location findSafeSpot(Location loc) {
        World w = loc.getWorld();
        if (w == null) return null;
        int x = loc.getBlockX(), z = loc.getBlockZ();
        int startY = loc.getBlockY();
        int minY = w.getMinHeight();
        int maxY = w.getMaxHeight() - 2;
        for (int d = 0; d <= 8; d++) {
            int up = startY + d;
            if (up <= maxY && isSafeSpot(w, x, up, z)) {
                return new Location(w, loc.getX(), up, loc.getZ(), loc.getYaw(), loc.getPitch());
            }
            int down = startY - d;
            if (d > 0 && down >= minY && isSafeSpot(w, x, down, z)) {
                return new Location(w, loc.getX(), down, loc.getZ(), loc.getYaw(), loc.getPitch());
            }
        }
        return null;
    }

    private boolean isSafeSpot(World w, int x, int y, int z) {
        return w.getBlockAt(x, y, z).isPassable()
                && w.getBlockAt(x, y + 1, z).isPassable()
                && w.getBlockAt(x, y - 1, z).getType().isSolid();
    }

    /* ===================================================
       LIST RENDERING (clickable, NO prefix spam)
       =================================================== */

    private boolean listMineDefault(Player p) {
        return listHomesOf(p, p.getUniqueId(), null, 1);
    }

    private void listMineAdmin(Player p, int page) {
        listHomesOf(p, p.getUniqueId(), null, page);
    }

    /** List one player's homes with public/private badges. ownerLabel null = "My Homes". */
    private boolean listHomesOf(Player viewer, UUID owner, String ownerLabel, int page) {
        List<String> names = storage.getHomeNames(owner).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        String title = (ownerLabel == null) ? "My Homes" : ownerLabel + "'s Homes";
        String pageBase = (ownerLabel == null) ? "/home list mine " : "/home admin " + ownerLabel + " list ";
        paginateComponents(
                viewer,
                title,
                names,
                (nm) -> {
                    boolean pub = storage.isPublic(owner, nm);
                    Component status = Component.text(pub ? "Public" : "Private",
                            pub ? NamedTextColor.AQUA : NamedTextColor.RED).decorate(TextDecoration.BOLD);
                    Component bar = Component.text(" | ", NamedTextColor.WHITE);
                    Component clickable = (ownerLabel == null)
                            ? bracketedName(nm, null)
                            : bracketedName(nm, ownerLabel);
                    return status.append(bar).append(clickable);
                },
                pageSize(),
                (pg) -> pageBase + pg,
                page
        );
        return true;
    }

    private void listInvited(Player p, int page) {
        UUID u = p.getUniqueId();
        List<String> entries = new ArrayList<>(storage.getInvited(u));
        entries.sort(String.CASE_INSENSITIVE_ORDER);

        paginateComponents(
                p,
                "My Invited Homes",
                entries,
                (entry) -> {
                    String[] sp = entry.split(":", 2);
                    UUID owner = UUID.fromString(sp[0]);
                    String ownerName = displayName(owner);
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
                "Public Homes",
                entries,
                (entry) -> {
                    String[] sp = entry.split(":", 2);
                    UUID owner = UUID.fromString(sp[0]);
                    String ownerName = displayName(owner);
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

    /* ===== paginate helpers ===== */

    @FunctionalInterface private interface RowBuilder { Component build(String entry); }
    @FunctionalInterface private interface PageCmd { String cmd(int page); }

    private void paginateComponents(Player p,
                                    String title,
                                    List<String> entries,
                                    RowBuilder rowBuilder,
                                    int perPage,
                                    PageCmd pageCmd,
                                    int page) {
        int total = entries.size();
        if (total == 0) {
            p.sendMessage(Component.text("--- " + title + " ---", NamedTextColor.YELLOW));
            p.sendMessage(Component.text("No " + title.toLowerCase(Locale.ROOT) + " available!", NamedTextColor.YELLOW));
            return;
        }
        int totalPages = (int) Math.ceil(total / (double) perPage);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, total);

        // header
        p.sendMessage(Component.text("--- " + title + " (Page " + page + "/" + totalPages + ") ---", NamedTextColor.YELLOW));

        // rows (no prefix)
        int lineNum = start + 1;
        for (int i = start; i < end; i++) {
            Component number = Component.text(lineNum + ". ", NamedTextColor.GREEN);
            p.sendMessage(number.append(rowBuilder.build(entries.get(i))));
            lineNum++;
        }

        // nav
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

    private Component bracketedName(String homeName, String ownerNameOrNull) {
        // If ownerNameOrNull == null → /home <homeName>
        // Else → /home owner:home
        String cmd = (ownerNameOrNull == null)
                ? "/home " + homeName
                : "/home " + ownerNameOrNull + ":" + homeName;

        return Component.text("[", NamedTextColor.GREEN)
                .append(Component.text(homeName, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand(cmd)))
                .append(Component.text("]", NamedTextColor.GREEN));
    }

    private boolean isReserved(String s) {
        return Set.of("set","create","add","delete","del","rem","remove",
                "rename","ren","public","pub","private","priv","invite","inv",
                "uninvite","uninv","mine","list","clear","confirm","reload",
                "bed","admin","help").contains(s.toLowerCase(Locale.ROOT));
    }

    private int parseIntSafe(String s, int def) { try { return Integer.parseInt(s); } catch (Exception e) { return def; } }

    /* =======================
       TAB COMPLETE
       ======================= */

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
            first.addAll(Set.of("set","create","add","rename","ren","delete","del","rem","remove",
                    "invite","inv","uninvite","uninv","list","pub","public","priv","private",
                    "clear","reload","help"));
            if (isAdmin(p)) first.add("admin");
            return prefixFilter(first, args[0]);
        }

        if (args.length == 2) {
            String a1 = args[0].toLowerCase(Locale.ROOT);
            if (Set.of("delete","del","rem","remove","pub","public","priv","private",
                    "set","create","add","rename","ren").contains(a1)) {
                return prefixFilter(myNames, args[1]);
            }
            if (a1.equals("list")) {
                return prefixFilter(List.of("mine","public","invited"), args[1]);
            }
            if (a1.equals("clear")) {
                List<String> opts = new ArrayList<>(List.of("confirm"));
                if (isAdmin(p)) opts.addAll(offlineNames());
                return prefixFilter(opts, args[1]);
            }
            if (Set.of("invite","inv","uninvite","uninv").contains(a1)) {
                return prefixFilter(myNames, args[1]);
            }
            if (a1.equals("admin") && isAdmin(p)) {
                return prefixFilter(offlineNames(), args[1]);
            }
        }

        if (args.length == 3) {
            String a1 = args[0].toLowerCase(Locale.ROOT);
            if (Set.of("invite","inv","uninvite","uninv").contains(a1)) {
                return prefixFilter(offlineNames(), args[2]);
            }
            if (Set.of("set","create","add").contains(a1)) {
                return prefixFilter(List.of("--override"), args[2]);
            }
            if (a1.equals("rename") || a1.equals("ren")) {
                return prefixFilter(myNames, args[2]);
            }
            if (a1.equals("admin") && isAdmin(p)) {
                return prefixFilter(List.of("set","rename","delete","invite","uninvite",
                        "public","private","list","clear"), args[2]);
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && isAdmin(p)) {
            String action = args[2].toLowerCase(Locale.ROOT);
            Optional<UUID> ou = Storage.uuidFromNameExact(args[1]);
            List<String> targetNames = ou.map(t -> storage.getHomeNames(t).stream()
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList()).orElse(List.of());
            if (Set.of("delete","rename","public","private","invite","uninvite","set").contains(action)) {
                return prefixFilter(targetNames, args[3]);
            }
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("admin") && isAdmin(p)) {
            String action = args[2].toLowerCase(Locale.ROOT);
            if (action.equals("rename")) {
                return prefixFilter(List.of(), args[4]); // new name is free text
            }
            if (action.equals("invite") || action.equals("uninvite")) {
                return prefixFilter(offlineNames(), args[4]);
            }
        }

        return List.of();
    }

    private List<String> offlineNames() {
        List<String> list = new ArrayList<>();
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null) list.add(op.getName());
        }
        list.sort(String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    private List<String> prefixFilter(Collection<String> base, String token) {
        String tkn = token.toLowerCase(Locale.ROOT);
        return base.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(tkn)).limit(50).collect(Collectors.toList());
    }
}
