# GuGuGu Mod

[中文](README.md) | **English**

> A powerful Minecraft server management mod providing fake players, backups, chat enhancements, and more.

[![Version](https://img.shields.io/badge/Version-1.0.0.0-red.svg)](https://github.com)

---

## 📋 Table of Contents

- [Features](#-features)
- [Core Modules](#-core-modules)
  - [Fake Player System](#1-fake-player-system)
  - [Backup & Restore System](#2-backup--restore-system)
  - [Chat Enhancement](#3-chat-enhancement)
  - [Whitelist Management](#4-whitelist-management)
  - [Status Monitoring](#5-status-monitoring)
- [Configuration](#-configuration)
- [Commands](#-commands)
- [License](#-license)

---

## ✨ Features

GuGuGu Mod provides comprehensive server management tools to enhance both player experience and server administration:

- **🤖 Fake Player System** - Create and control fake players with realistic behavior
- **💾 Advanced Backup System** - Full/incremental backups with hot rollback support
- **💬 Rich Chat Processing** - @mentions, clickable links, item display, map waypoints
- **🔐 Whitelist Security** - Enhanced whitelist with offline mode support
- **📊 Performance Monitoring** - Real-time server stats and diagnostics
- **🎮 Player Teleportation** - Non-OP player teleportation commands
- **🔄 Hot Reload** - Configuration hot reload without server restart

---

## 🎯 Core Modules

### 1. Fake Player System

Inspired by Carpet Mod, providing realistic fake player functionality.

#### Commands

```bash
# Create fake player
/fp create <name>
/fp create <name> <x> <y> <z>
/fp create <name> <x> <y> <z> <yaw> <pitch>
/fp create <name> <x> <y> <z> in <dimension>

# Open fake player inventory (admin only)
/fp open <name> [true|false]
/fp open <name> viewer <player> [true|false]

# Auto-login management
/fp autoLogin enable <name>
/fp autoLogin disable <name>
/fp autoLogin list

# Control actions
/fp control <name> [use|attack|jump|drop|stopAll|kill] [interval|continue]
```

#### Configuration

File: `config/gugugu/fakeplayer_config.json`

```json
{
  "commandLevel": 0,
  "allowOpenInventory": true,
  "allowInventoryInteraction": true,
  "fakePlayerNamePrefix": "",
  "fakePlayerNameSuffix": "",
  "persisted": [],
  "autoLoginNames": [],
  "allowFakeServerGamePacketListenerImpl": true
}
```

---

### 2. Backup & Restore System

Advanced backup system with incremental and full backup strategies.

#### Features

- **Incremental Backup** - Only backs up changed chunks
- **Full Backup** - Complete world backup
- **Hot Rollback** - Restore chunks without server restart
- **Auto Scheduling** - Automated backup scheduling
- **Compression** - Optional compression for storage efficiency

#### Commands

```bash
# Create backups
/gbackup inc                           # Incremental backup
/gbackup full                          # Full backup

# Single chunk rollback
/gbackup rollback [hot inc|hot full] <x> <z>

# Area rollback
/gbackup rollback area <x1> <z1> <x2> <z2> [hot inc|hot full]

# Rollback player's current chunk
/gbackup rollback player [hot inc|hot full]
```

#### Configuration

File: `GBackups/<worldname>/gbackup.json`

```json
{
  "autoBackupInterval": 3600,
  "enableCompression": true,
  "maxBackupCount": 10,
  "backupCommandWhitelist": []
}
```

---

### 3. Chat Enhancement

Rich text processing for chat messages with multiple processors.

#### Processors

- **@mention** - `@<player>` or `@all` mentions
- **link** - Auto-convert HTTP links to clickable
- **component** - Combine special formats (always enabled)
- **mention_notify** - Sound and visual notifications
- **teleport** - Click player name to teleport
- **hand_item** - Display held items: `[i]` main hand, `[io]` off hand
- **xaero_map_util** - Xaero's Minimap waypoint integration
- **journey_map_util** - JourneyMap waypoint integration

#### Disable Processors

In `config/gugugu/config.json`, use `disabledMessageHandlers` array:

```json
{
  "disabledMessageHandlers": ["teleport", "hand_item"]
}
```

#### View Examples

```bash
/chat components
```

---

### 4. Whitelist Management

Enhanced whitelist system with offline mode support.

#### Features

- **UUID Check Bypass** - Support for offline mode servers
- **Security Verification** - Secondary authentication for mismatched UUIDs
- **Password Protection** - Optional password-based authentication

#### How It Works

When `whiteListDisableUidCheck` is enabled:
1. Players can join with name-only matching (no UUID check)
2. If UUID doesn't match whitelist, secondary verification is required
3. Player must enter correct UUID from whitelist to bind account
4. After binding, uses whitelist player's data as primary

#### Configuration

File: `config/gugugu/whitelist.json`

```json
{
  "whiteListDisableUidCheck": true,
  "enableSimpleSecurity": true
}
```

---

### 5. Status Monitoring

Real-time server performance diagnostics.

#### Commands

```bash
/showstats              # Display server performance stats
```

Displays:
- TPS (Ticks Per Second)
- Memory usage
- Entity count
- Chunk statistics
- Dimension information

---

## ⚙️ Configuration

### Main Configuration

File: `config/gugugu/config.json`

```json
{
  "enableFakePlayer": true,
  "enableMessageHandler": true,
  "enableTeleport": true,
  "enableBackup": true,
  "whiteListDisableUidCheck": false,
  "enableSimpleSecurity": true,
  "disabledMessageHandlers": []
}
```

### Configuration Features

- **Hot Reload** - All configs support hot reload without restart
- **File Watching** - Automatic detection of config file changes
- **Validation** - Built-in validation for configuration values

---

## 📝 Commands

### Main Command

All commands are accessible through `/gugugu` or `/gu`:

```bash
/gu fakeplayer <...>      # Fake player management
/gu backup <...>          # Backup operations
/gu chat <...>            # Chat settings
/gu status                # Server status
/gu password <...>        # Password management
```

### Aliases

- `/fp` → `/gu fakeplayer`
- `/gbackup` → `/gu backup`
- `/showstats` → `/gu status`
- `/tpf <player>` → Teleport to player

---

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

---

## 🔗 Links

- **Issues**: [Report bugs](https://github.com/issues)
- **Discussions**: [Community discussions](https://github.com/discussions)
- **Wiki**: [Documentation](https://github.com/wiki)

---

## 🙏 Acknowledgments

- Inspired by [Carpet Mod](https://github.com/gnembon/fabric-carpet) for fake player implementation
- Thanks to the NeoForge community for support
- Special thanks to all contributors
