# MyHome

**Author:** Lumnator97  
**Version:** 1.0.0-dev  
A fully featured Minecraft home management plugin for Paper/Spigot servers, designed for simplicity and flexibility.  
Supports **private homes**, **public homes**, **invitations**, clickable teleport links, and stores exact player yaw/pitch for perfect facing when teleporting.

---

## 📦 Features

- **Set unlimited homes** (or limit them via `config.yml`)
- **Private or public homes** – share with everyone or keep them to yourself
- **Invite specific players** to private homes
- **Clickable teleport in chat** when listing homes
- **Yaw & pitch saving** – you'll face the same way when returning
- **Simple, clean formatting** using Minecraft `&` color codes
- **All data stored in `data.yml`** (easy to edit if needed)
- Configurable **prefix**, **max homes**, and more

---

## ⚙️ How It Works

1. **Set a home** anywhere using `/sethome <name>`.
2. **Teleport** back with `/home <name>` or click its name in `/homes`.
3. **Make public** with `/publichome <name>` so everyone can visit.
4. **Invite** specific players with `/invitehome <player> <name>`.
5. **List** your own homes, public homes, or invitations with `/homes`, `/publichomes`, or `/invitedhomes`.
6. **Delete** any home you no longer need with `/delhome <name>`.

---

## 📜 Commands

| Command | Description |
|---------|-------------|
| `/sethome <name>` | Set a home at your current location. |
| `/home <name>` | Teleport to a saved home. |
| `/homes` | List your homes (click to teleport). |
| `/delhome <name>` | Delete a home you own. |
| `/publichome <name>` | Make one of your homes public. |
| `/privatehome <name>` | Make a public home private again. |
| `/publichomes` | List all public homes on the server. |
| `/invitehome <player> <name>` | Invite a player to a private home. |
| `/uninvitehome <player> <name>` | Remove a player’s invite to a home. |
| `/invitedhomes` | List homes you’ve been invited to. |
| `/clearhomes` | **Admin:** Clear all of a player's homes/invites. |

---

## 🛠️ Configuration

Located in `plugins/MyHome/config.yml`:

```yaml
# Chat prefix for all plugin messages
prefix: "&7[&bMyHome&7] "

# Maximum number of private homes a player can have
# (set to -1 for unlimited)
max-homes: 5

# Should teleport use exact yaw/pitch saved with the home?
use-precise-facing: true
