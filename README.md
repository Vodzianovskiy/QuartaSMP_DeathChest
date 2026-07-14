# 💀 QuartaSMP DeathChest

**QuartaSMP DeathChest** is a polished Paper plugin that protects players from losing their items after death.  
When a player dies, their drops are safely stored inside a personal death chest, marked with particles, holograms, and an optional death compass for easy navigation.

Designed for survival servers, SMP projects, and custom Paper networks, the plugin gives players a clean recovery experience while still giving administrators full control over death chests.

---

## ✨ Features

💀 **Automatic Death Chests** — player drops are captured on death and stored inside a protected personal chest  
📦 **Safe Item Storage** — prevents item loss from lava, void deaths, explosions, despawn timers, or chaotic death locations  
🧭 **Death Compass Support** — players can be guided back to their selected death chest using a compass  
📋 **Personal Chest List** — players can view their active death chests and interact with clickable entries  
🚀 **Teleport Command** — admins can teleport directly to a death chest for moderation or support  
🛡️ **Owner Protection** — players cannot open or steal from another player's death chest  
⏳ **Configurable Expiration** — chest lifetime after first opening and maximum age from creation are fully configurable  
🌋 **Safe Placement System** — searches for a valid nearby location instead of placing chests in unsafe blocks  
🧱 **Emergency Platform** — if a player dies in the void or another unsafe area, the plugin can create an obsidian fallback platform  
✨ **Particle Effects** — configurable particle aura around death chests for visibility  
🔮 **Hologram Support** — optional DecentHolograms integration displays owner and timer information above the chest  
🎨 **MiniMessage Formatting** — inventory titles and chat messages support modern MiniMessage formatting, gradients, colors, and icons  
🖱️ **Clickable Messages** — chest list entries can run commands directly from chat  
🧹 **Admin Cleanup Tools** — administrators can remove player death chests or clear broken/expired entries  
🔄 **Config Reload** — reload configuration without restarting the server  
⚙️ **Highly Configurable** — chest title, messages, timers, particles, holograms, placement behavior, and permissions can be customized  
🧩 **Soft Dependency Friendly** — DecentHolograms is optional; the plugin works even if holograms are not installed  
🚀 **Paper 1.21.11 Ready** — built against the modern Paper API and Java 21

---

## 🔧 Commands

| Command | Description | Permission |
|---|---|---|
| `/dc list` | Show your active death chests | `quartasmp.deathchest.use` |
| `/dc admin` | Open the admin death chest interface | `quartasmp.deathchest.admin` |
| `/dc view <player/id>` | View a death chest or player's chests | `quartasmp.deathchest.admin` |
| `/dc tp <id>` | Teleport to a death chest | `quartasmp.deathchest.admin` |
| `/dc clear <player>` | Clear death chests for a player | `quartasmp.deathchest.admin` |
| `/dc reload` | Reload the plugin configuration | `quartasmp.deathchest.admin` |
| `/deathchest` | Alias for `/dc` | Depends on subcommand |

> Exact command behavior may depend on the current plugin implementation and configuration.

---

## 🔐 Permissions

| Permission | Description | Default |
|---|---|---|
| `quartasmp.deathchest.use` | Allows players to use death chest features | `true` |
| `quartasmp.deathchest.admin` | Allows access to administrative death chest commands | `OP` |

---

## ⚙️ Configuration

The plugin is configured through `config.yml`.

| Section | Description |
|---|---|
| `chest.inventory-title` | Inventory title shown when opening a death chest, using MiniMessage |
| `chest.particles` | Particle type, interval, and visibility distance around death chests |
| `chest.placement` | Safe location search radius, vertical search depth, and emergency platform settings |
| `hologram` | DecentHolograms text, Y offset, timer line, and expired line |
| `expire-time` | Time in seconds before a chest expires after first opening |
| `max-age` | Maximum lifetime of a death chest from creation time |
| `messages` | All player/admin messages using MiniMessage formatting |

### Example configuration highlights

```yml
expire-time: 300      # 5 minutes after first opening
max-age: 259200      # 3 days from creation

chest:
  inventory-title: "<i:false><gradient:#FF4500:#FFD700>💀 Death of {player}</gradient>"
  particles:
    enabled: true
    type: "SOUL_FIRE_FLAME"
    interval: 40
    visible-distance: 32.0
```

---

## 📦 Dependencies

| Plugin / Tool | Type | Purpose |
|---|---|---|
| Paper `1.21.11` | Required | Server API |
| Java `21` | Required | Runtime and compilation |
| Maven | Required for building | Project build system |
| DecentHolograms | Optional / Soft Depend | Floating holograms above death chests |

---

## 🚀 Installation

1. Download or build the plugin `.jar` file.
2. Place it into your server's `plugins/` folder.
3. Install **DecentHolograms** if you want hologram support.
4. Restart the server.
5. Edit `plugins/QuartaSMP_DeathChest/config.yml` to match your server style.
6. Use `/dc reload` after configuration changes if supported.

---

## 🛠️ Building from Source

Requirements:

- Java 21
- Maven
- Paper API access through Maven repositories

Build command:

```bash
mvn clean package
```

The compiled plugin will be located in:

```text
target/quartasmp_deathchest-1.0-SNAPSHOT.jar
```

---

## 🗂️ Project Structure

```text
src/main/java/eu/_Nightwarden/quartaSMP_DeathChest/
├── QuartaSMP_DeathChest.java      # Main plugin class
├── DeathChest.java                # Death chest data/model logic
├── DeathChestManager.java         # Core death chest management
├── DeathChestListener.java        # Player death and interaction listeners
├── DeathChestCommand.java         # /dc command handling
├── AdminDeathChestGUI.java        # Admin GUI interface
├── CompassManager.java            # Death compass behavior
├── HologramManager.java           # Optional hologram integration
└── ParticleManager.java           # Particle display logic

src/main/resources/
├── plugin.yml                     # Paper plugin metadata
└── config.yml                     # Default plugin configuration
```

---

## 🎨 Message Formatting

Messages use **MiniMessage**, allowing modern formatting such as:

- HEX colors
- gradients
- bold, italic, underline, strikethrough
- clickable commands
- icons and emojis

Example:

```yml
messages:
  chest-created: "<i:false><green>💀 Your death chest appeared at <yellow><x>, <y>, <z></yellow></green>"
```

---

## 🧑‍💻 Server Admin Notes

- Keep `target/` and local test server folders out of Git.
- Do not commit generated `.jar` files unless you intentionally publish releases through the repository.
- Use permissions to separate normal player features from admin tools.
- If DecentHolograms is missing, hologram features are skipped while the core plugin remains usable.

---

## 📄 License

No license has been specified yet.  
Add a `LICENSE` file if you want to define how others may use, modify, or redistribute this project.
