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

## 🛠️ Utility Features

### Quick Teleport

Non-OP player teleportation feature, allowing players to teleport to other players without admin permissions.

#### Commands

```bash
/gu tpf <player>     # Teleport to specified player
```

#### Enable Configuration

Enable in `config/gugugu/config.json`:

```json
{
  "enableTeleport": true
}
```

**Note**: Admins (permission level 2) can use this without enabling the option.

---

### Kill Self Command

Quick self-kill command for players to respawn quickly when stuck.

#### Commands

```bash
/gu killme          # Instant death and respawn
```

**Note**: This command is available to all players without special permissions.

---

## 🎯 Core Modules

### 1. Fake Player System

Inspired by Carpet Mod, providing realistic fake player functionality.

#### Commands

```bash
# Lifecycle Management
/gu fakeplayer spawn <name>                                    # Spawn fake player at current position
/gu fakeplayer spawn <name> <gamemode>                         # Spawn with specific gamemode
/gu fakeplayer spawn <name> <gamemode> <pos>                   # Spawn at specific position
/gu fakeplayer kill <fakeplayer>                               # Remove fake player

# Basic Actions
/gu fakeplayer action <player> attack [once] [interval]        # Attack
/gu fakeplayer action <player> use [once] [interval]           # Use/Right-click
/gu fakeplayer action <player> dig [once]                      # Dig
/gu fakeplayer action <player> jump [once] [interval]          # Jump
/gu fakeplayer action <player> drop <slot> <dropAll>           # Drop item

# Movement Control
/gu fakeplayer action <player> move forward                    # Move forward
/gu fakeplayer action <player> move backward                   # Move backward
/gu fakeplayer action <player> move left                       # Move left
/gu fakeplayer action <player> move right                      # Move right
/gu fakeplayer action <player> move stop                       # Stop moving
/gu fakeplayer action <player> move <forward> <strafing>       # Custom movement
/gu fakeplayer action <player> sneak <true|false>              # Sneak
/gu fakeplayer action <player> sprint <true|false>             # Sprint

# View Control
/gu fakeplayer action <player> look at <yaw> <pitch>           # Look at absolute angle
/gu fakeplayer action <player> look turn <yaw> <pitch>         # Relative turn
/gu fakeplayer action <player> look pos <position>             # Look at position
/gu fakeplayer action <player> look direction <dir>            # Look at direction(north/south/east/west/up/down)
/gu fakeplayer action <player> look entity <target>            # Track entity
/gu fakeplayer action <player> look crosshair                  # Track crosshair

# Item and Mount
/gu fakeplayer action <player> hotbar <slot>                   # Select hotbar slot
/gu fakeplayer action <player> swap                            # Swap hands
/gu fakeplayer action <player> useitem [hand] [maxDuration]    # Use item continuously
/gu fakeplayer action <player> mount [onlyRideables]           # Mount
/gu fakeplayer action <player> dismount                        # Dismount

# Stop Actions
/gu fakeplayer action <player> stop                            # Stop current action
/gu fakeplayer action <player> stopall                         # Stop all actions

# Configuration
/gu fakeplayer config autologin add <name>                     # Add auto-login
/gu fakeplayer config autologin remove <name>                  # Remove auto-login
/gu fakeplayer config autologin list                           # List auto-login
/gu fakeplayer config reload                                   # Reload config
```

#### Configuration

File: `config/gugugu/fakeplayer_config.json`

```json
{
  "commandLevel": 4,                                 // Command permission level (0-4)
  "allowOpenInventory": false,                       // Allow right-click to open inventory
  "allowInventoryInteraction": true,                 // Allow non-admin inventory interaction
  "fakePlayerNamePrefix": "",                        // Fake player name prefix
  "fakePlayerNameSuffix": "",                        // Fake player name suffix
  "persisted": [],                                   // Persisted fake player info (auto-managed)
  "autoLoginNames": [],                              // Auto-login fake player names
  "allowFakeServerGamePacketListenerImpl": true      // Allow fake ServerGamePacketListenerImpl
}
```

---

### 2. Backup & Restore System

Advanced backup system with incremental and full backup strategies.

#### Features

- **Incremental Backup** - Only backs up changed chunks
- **Full Backup** - Complete world backup
- **Manual Backup** - Named backups for easier management
- **Hot Rollback** - Restore chunks without server restart
- **Auto Scheduling** - Automated backup scheduling

#### Commands

```bash
# Create backups
/gu backup incremental                                         # Incremental backup
/gu backup full                                                # Full backup
/gu backup manual create <name> [force]                        # Create named backup

# List and Delete
/gu backup list                                                # List full backups
/gu backup manual list                                         # List manual backups
/gu backup delete <name>                                       # Delete full backup
/gu backup manual delete <name>                                # Delete manual backup

# Rollback Operations
/gu backup rollback here [updateEntities]                      # Rollback current chunk
/gu backup rollback here from inc [updateEntities]             # Rollback from incremental
/gu backup rollback here from full [backupName] [updateEntities] # Rollback from full backup
/gu backup rollback here from manual <backupName> [updateEntities] # Rollback from manual backup
/gu backup rollback <pos1> <pos2> [updateEntities]             # Rollback area
/gu backup rollback <pos1> <pos2> from inc [updateEntities]    # Rollback area from incremental
/gu backup rollback <pos1> <pos2> from full [backupName] [updateEntities] # Rollback area from full

# Configuration Management
/gu backup config reload                                       # Reload config
/gu backup config show                                         # Show config
/gu backup config set autoBackup <true|false>                  # Set auto backup
/gu backup config set autoBackupMinutes <minutes>              # Set backup interval
/gu backup config set keepFull <count>                         # Set kept backups
/gu backup config set hotRollbackSource <inc|full>             # Set default rollback source

# Scheduler Management
/gu backup scheduler status                                    # Show scheduler status
/gu backup scheduler start                                     # Start scheduler
/gu backup scheduler stop                                      # Stop scheduler
/gu backup scheduler restart                                   # Restart scheduler
```

#### Configuration

File: `GBackups/<worldname>/gbackup.json`

```json
{
  "autoBackupMinutes": 60,              // Auto backup interval (minutes)
  "enableAutoBackup": false,            // Enable auto backup
  "autoBackupWithIncremental": false,   // Include incremental with auto backup
  "keepFull": 3,                        // Number of full backups to keep
  "hotRollbackSource": "full",         // Default hot rollback source (inc/full)
  "commandWhitelist": []                // Command whitelist (player names)
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
/gu chatEvent
```

---

### 4. Whitelist Management

Enhanced whitelist system with offline mode support and password binding authentication.

#### Features

- **UUID Check Bypass** - Support for offline mode servers
- **Security Verification** - Secondary authentication for mismatched UUIDs
- **Password Binding** - Password-based account binding system
- **IP Whitelist** - Remember verified IP addresses
- **Failure Protection** - Lockout mechanism to prevent brute force attacks

#### How It Works

When `whiteListDisableUidCheck` is enabled:
1. Players can join with name-only matching (no UUID check)
2. If UUID doesn't match whitelist, secondary verification is required
3. Player must enter correct UUID from whitelist to bind account
4. After binding, uses whitelist player's data as primary

#### Password Binding System

When `enablePasswordAuth` is enabled, provides more secure account binding:

**Set Password**:
```bash
/gu password set <password>          # Set password for current account
```

**Usage Flow**:
1. Whitelist player sets password on first login
2. Other players using the same name must enter correct password
3. After successful verification, IP address is added to whitelist
4. Within whitelist period (default 60 minutes), the IP doesn't need re-verification
5. After reaching max failed attempts (default 3), account is locked (default 10 minutes)

**Password Management Commands**:
```bash
/gu password set <password>                    # Set password
/gu password change <oldPassword> <newPassword> # Change password
/gu password check                             # Check password status
/gu password remove <player>                   # Remove player password (admin)
/gu password clear <player>                    # Clear failed attempts (admin)
```

**Password Requirements**:
- Minimum 6 characters
- Recommended to include uppercase, lowercase, numbers, and special characters
- System displays password strength evaluation

#### Detailed Explanation

As we all know, Minecraft offline servers always generate incorrect UUIDs for whitelists. Our `whiteListDisableUidCheck` option enables name-only matching for joining players.

However, disabling UUID checks is like closing your eyes to the problem - others can directly use someone else's account to join the server. We provide two security verification methods:

**Method 1: Simple Security Check** (`enableSimpleSecurity`)

Suppose the whitelist has `[Name: a, UUID: 123]`, but player `a`'s real UUID is not `123`:
- Player `a` can still join the server
- But will be required to perform **secondary verification**
- Must enter the correct whitelist UUID in chat to bind account
- After binding, uses the whitelist player's data as primary game data

**Method 2: Password Binding Verification** (`enablePasswordAuth`)

More secure and user-friendly verification method:
- After whitelist player sets password, account is password-protected
- Other players using that name must enter correct password to login
- Supports IP whitelist - verified IPs don't need re-verification within time limit
- Has anti-brute-force mechanism - automatically locks after multiple failures

#### Configuration

File: `config/gugugu/whitelist.json`

```json
{
  "bindMap": {},            // UUID binding map (auto-managed)
  "passwordHashes": {},     // Password hash storage (auto-managed)
  "ipWhitelist": {},        // IP whitelist cache (auto-managed)
  "failedAttempts": {}      // Failed attempts record (auto-managed)
}
```

**Note**: Whitelist feature toggles are in main config `config/gugugu/config.json`:
```json
{
  "whiteListDisableUidCheck": true,  // Disable UUID check
  "enableSimpleSecurity": true       // Enable simple security verification
}
```

---

### 5. Status Monitoring

Real-time server performance diagnostics.

#### Commands

```bash
/gu showstats                      # Display server performance stats (page 1)
/gu showstats <page>               # Display dimension info at specified page
/gu showstats <page> <dimension>   # Display detailed info for specific dimension
```

Displays:
- **Server Info**: Version, uptime, player count, seed
- **Performance Metrics**: TPS, MSPT (average, median, 95th percentile), memory usage
- **Dimension Details** (paginated):
  - Player count
  - Chunk statistics (loaded/ticking chunks)
  - Entity statistics (monsters, creatures, water creatures)
  - Click dimension name to view details

---

## ⚙️ Configuration

### Main Configuration

File: `config/gugugu/config.json`

```json
{
  "enableFakePlayer": false,               // Enable fake player feature
  "enableMessageHandler": false,           // Enable chat message processing
  "disabledMessageHandlers": ["teleport"], // Disabled message handlers list
  "enableTeleport": false,                 // Enable non-OP teleport command (/gu tpf)
  "whiteListDisableUidCheck": false,       // Disable whitelist UUID check
  "enableSimpleSecurity": true,            // Enable simple security verification
  "enableBackup": false,                   // Enable backup feature
  "enablePasswordAuth": false,             // Enable password authentication system
  "maxPasswordAttempts": 3,                // Max password attempt failures
  "passwordIpWhitelistMinutes": 60,        // IP whitelist memory duration (minutes)
  "passwordLockoutMinutes": 10             // Password error lockout time (minutes)
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
/gu chatEvent             # View chat processor info
/gu showstats [page]      # Server status
/gu tpf <player>          # Teleport to player (requires enableTeleport)
/gu password <...>        # Password management (requires enablePasswordAuth)
/gu killme                # Kill self command
```

### Password Management Commands

```bash
/gu password set <password>                    # Set password
/gu password change <oldPassword> <newPassword> # Change password
/gu password check                             # Check password status
/gu password remove <player>                   # Remove player password (admin)
/gu password clear <player>                    # Clear failed attempts (admin)
```

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
