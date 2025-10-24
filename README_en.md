# GuGuGu Mod

`0.1.0.1` version rule: Major structural changes version.Extra features addition/removal version.Method/class changes version.Minor modifications version.

This mod provides diverse auxiliary tools for the game, significantly improving player experience, while allowing server administrators to flexibly control and configure related features.

Up to `0.2.*`, we provide: `Fake Player`, `Behavior Control`, `Backup`, `Hot Rollback`, `Chat Message Components`, `Server Status Query`, `Whitelist UUID Check Disable`, `Security Management after Whitelist Check Disable` and various other features.

---

## Detailed Feature Introduction

### 1. Fake Player Functionality

Reference the well-known Carpet mod to develop fake player functionality, providing a series of features similar to real players.

#### Configuration File Introduction

Configuration file location: `config/gugugu/fakeplayer_config.json`

```json
{
  "commandLevel": 0,                                // Required permission level for command execution
  "allowOpenInventory": true,                       // Whether players can right-click to open fake player inventory
  "allowInventoryInteraction": true,                // Whether non-admins can interact with items in FakePlayer inventory
  "fakePlayerNamePrefix": "",                       // FakePlayer name prefix and suffix
  "fakePlayerNameSuffix": "",
  "persisted": [],                                  // Persistent fake player location information, no manual modification needed
  "autoLoginNames": [],                             // List of fake player names that should automatically log in when server restarts or loads world
  "allowFakeServerGamePacketListenerImpl": true     // Whether to block forge/neoforge event system from detecting fake player login (setting false may cause fake players to be rejected due to missing mods in some cases)
}
```

#### Fake Player Commands Detailed (`/rifakeplayer` or `/fp`)

* **Create Fake Player**

  ```
  /fp create playername
  /fp create playername x y z
  /fp create playername x y z yaw pitch
  /fp create playername x y z in dimension
  ```

* **Open Fake Player Inventory** (This subcommand only supports players with admin permissions)

  ```
  /fp open fakename [true|false]                    // true allows players to interact with fake player inventory, false disallows
  /fp open fakename viewer playername [true|false]  // Opens specified fake player inventory view for a specific player
  ```

* **Auto Login Settings**

  ```
  /fp autoLogin enable fakename
  /fp autoLogin disable fakename
  /fp autoLogin list
  ```

* **Control Fake Player Actions**

  ```
  /fp control fakename [use|attack|jump|drop|stopAll|kill] [interval|continue]          // Different actions can be combined
  ```

---

### 2. Player Message Processing Functionality (`/chatEvent components` to view specific examples)

Supports rich chat message text processing, including the following message processors:

* **mention**: `@{player}` or `@all` to highlight specific players
* **link**: Automatically converts valid HTTP links to clickable links
* **component**: Automatically combines special formats in messages (cannot be disabled)
* **mention_notify**: Provides additional sound effects and screen notifications for mentioned players
* **teleport**: Click to teleport when only entering player name
* **hand_item**: Supports displaying player-held items in chat, examples: `[i]` main hand, `[io]` off hand
* **xaero_map_util**: Supports Xaero map waypoint click-to-add
* **journey_map_util**: Supports JourneyMap waypoint click-to-add

#### Disabling Components Method

Specify disabled components in the `disabledMessageHandlers` field in `config/gugugu/config.json`.

---

### 3. Server Performance Diagnostics (ShowStats)

* Use command `/showstats` to quickly get current server performance data

---

### 4. Player-to-Player Teleportation (TPF)

* Allows non-OP players to quickly teleport to other players

  ```
  /tpf targetplayername
  ```

---

### 5. Server Backup and Hot Area Rollback (GBackup)

Provides efficient manual and scheduled backup with hot rollback functionality.

#### Command Details

* **Incremental Backup**:

  ```
  /gbackup inc
  ```

* **Full Backup**:

  ```
  /gbackup full
  ```

* **Hot Rollback** (Single Chunk):

  ```
  /gbackup rollback [hot inc|hot full] [coordinates]
  ```

* **Area Hot Rollback**:

  ```
  /gbackup rollback area coordinate1 coordinate2 [hot inc|hot full]
  ```

#### Configuration Explanation

Configuration file: `GBackups/world/gbackup.json`

* Automatic backup timing, compression enablement, backup retention count
* Hot rollback data source settings (incremental or full)
* Backup command whitelist

---

### 6. Simple Security Check After Disabling Whitelist UUID Check

As known, Minecraft offline servers often generate incorrect UUIDs when creating whitelists. Our `whiteListDisableUidCheck` option enables name-only matching for players joining the server.

However, disabling UUID checks is like burying one's head in the sand - others can directly enter the server using other people's accounts. Therefore, we provide an additional option: the default-enabled simple security check `enableSimpleSecurity`.

The security check prevents malicious actors from exploiting server security vulnerabilities. Here are scenarios that trigger security checks:

Assume we add a whitelist `[Player: a, uuid: 123]`, but player `a`'s actual `uuid` is not `123`. Although player `a` can still enter the server normally, they will be required for `secondary verification` - they must enter the correct `uuid of the player with the same name in the whitelist` in the chat bar for binding, otherwise they cannot play normally.

After binding, the two players are considered the same person, using the whitelisted player's data as the primary game data.

---

## Core Configuration Overview

Location: `config/gugugu/config.json`

* Fake player functionality enable switch (`enableFakePlayer`)
* Message processing enable switch (`enableMessageHandler`)
* Player teleportation functionality switch (`enableTeleport`)
* Backup functionality switch (`enableBackup`)
* Whitelist UID check disable switch (`whiteListDisableUidCheck`)
* Secondary verification switch when players join the game after disabling whitelist UID check (`enableSimpleSecurity`)