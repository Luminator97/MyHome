# MyHome

**Author:** Luminator97  
**A clean, fast home system for Paper/Spigot.**  
Everything hangs off one command: `/home <action> [args]`.  
Supports private/public homes, invites, rename, clickable lists, precise yaw/pitch teleports,
safe landing (never drops you inside a block), and full admin management of other players' homes.
Saves to `data.yml` instantly on every change.

Home names may only use `a-z`, `0-9`, `-` and `_` (max 32 chars) — this keeps the data file
and the `owner:name` syntax unambiguous.

---

## Features

- `/home` with subcommands (one command to rule them all)
- Save + TP with exact yaw/pitch, with a safe-landing check
- Rename homes (public entries and invites follow the rename)
- Public vs private homes
- Per‑player invites to private homes
- Clickable lists: click a home name to teleport
- Color formatting with classic `&` codes
- `/home help` in-game command reference
- Two-step confirm on `/home clear` (no more accidental wipes)
- Simple config: prefix, limits, page size
- No janky counters; limits based on actual private homes
- Admin suite: manage any player's homes (`/home admin <player> ...`)

---

## Commands

_All commands start with **`/home`**._

### Teleport & Basics
- **`/home`** — List **your** homes (click to teleport).
- **`/home <name>`** — Teleport to your home named `<name>`.
- **`/home bed`** — Teleport to your bed (if set).
- **`/home <owner>:<name>`** — Teleport to someone else’s home if it’s **public** or you’re **invited**.
- **`/home help`** — Show the command reference in-game.

### Managing Homes
- **`/home set <name> [--override]`** — Create a home at your location (use `--override` to reset an existing one).
- **`/home rename <old> <new>`** — Rename one of your homes (public status and invites carry over).
- **`/home delete|del|remove|rem <name>`** — Delete one of your homes.
- **`/home public|pub <name>`** — Make one of your homes public.
- **`/home private|priv <name>`** — Make a public home private again.
  - Limits apply only to **private** homes. If you hit the limit, make one public or delete one.

### Invites
- **`/home invite|inv <home> <player>`** — Invite a player to a **private** home.
- **`/home uninvite|uninv <home> <player>`** — Remove a player’s invite.
- Invited players can use: **`/home list invited [page]`** — List homes you’re invited to.

### Admin
Requires the `MyHome.admin` permission. Manage any player's homes:
- **`/home admin <player> set <name>`** — Set a home for them at your location.
- **`/home admin <player> rename <old> <new>`**
- **`/home admin <player> delete <name>`**
- **`/home admin <player> invite|uninvite <home> <target>`**
- **`/home admin <player> public|private <name>`**
- **`/home admin <player> list [page]`**
- **`/home admin <player> clear`** — Wipe their homes (asks for confirm).

### Admin / Utility
- **`/home clear`** — Clear **your** homes (asks for `/home clear confirm` first — no accidental wipes).
- **`/home clear <player>`** — Clear someone else’s homes (admin only, also asks for confirm).
- **`/home reload`** — Reload config (admin only).

---

## Permissions

- **`MyHome.admin`**
  - Use `/home admin <player> ...`
  - Use `/home reload`
  - Use `/home clear <player>`
  - Bypass private‑home limit when toggling
  - See admin‑style paginated “mine” list

_No other permissions are required. Regular players can use everything else._

---

## Config (`config.yml`)

```yaml
# Message prefix (supports & color codes)
chatPrefix: "&a&lHome&r &8≫ &7"

# Max number of PRIVATE homes for non-admins.
# Public homes do not count toward the limit.
maxHomes: 8

# Rows per page for paged lists (public/invited/admin-mine)
pageSize: 8

```

---

## Building

```bash
./gradlew build
```

The jar lands in `build/libs/`. Tagged pushes (`v*`) automatically build and attach
a GitHub Release via the `release.yml` workflow.
