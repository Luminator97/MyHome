# MyHome

**Author:** Luminator97  
**A clean, fast home system for Paper/Spigot.**  
Everything hangs off one command: `/home <action> [args]`.  
Supports private/public homes, invites, clickable lists, and precise yaw/pitch teleports. Saves to `data.yml` instantly on every change.

---

## Features

- `/home` with subcommands (one command to rule them all)
- Save + TP with exact yaw/pitch
- Public vs private homes
- Per‑player invites to private homes
- Clickable lists: click a home name to teleport
- Color formatting with classic `&` codes
- Simple config: prefix, limits, page size
- No janky counters; limits based on actual private homes

---

## Commands

_All commands start with **`/home`**._

### Teleport & Basics
- **`/home`** — List **your** homes (click to teleport).
- **`/home <name>`** — Teleport to your home named `<name>`.
- **`/home bed`** — Teleport to your bed (if set).
- **`/home <owner>:<name>`** — Teleport to someone else’s home if it’s **public** or you’re **invited**.

### Managing Homes
- **`/home set <name> [--override]`** — Create a home at your location (use `--override` to reset an existing one).
- **`/home delete|del|remove|rem <name>`** — Delete one of your homes.
- **`/home public|pub <name>`** — Make one of your homes public.
- **`/home private|priv <name>`** — Make a public home private again.
  - Limits apply only to **private** homes. If you hit the limit, make one public or delete one.

### Invites
- **`/home invite|inv <home> <player>`** — Invite a player to a **private** home.
- **`/home uninvite|uninv <home> <player>`** — Remove a player’s invite.
- Invited players can use: **`/home <owner>:<home>`**

### Lists (with pagination)
- **`/home list mine [page]`** — List your homes.  
  - If you’re **admin**, shows paged view; otherwise you get a compact list.
- **`/home list public [page]`** — List all public homes on the server.
- **`/home list invited [page]`** — List homes you’re invited to.

### Admin / Utility
- **`/home clear [player]`** — Clear **your** homes; with permission, clear someone else’s.
- **`/home reload`** — Reload config (admin only).

---

## Permissions

- **`MyHome.admin`**
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
