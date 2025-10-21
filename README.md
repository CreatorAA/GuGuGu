# GuGuGu Mod

**中文** | [English](README_EN.md)

> 强大的 Minecraft 服务器管理模组，提供假人玩家、备份系统、聊天增强等功能。

[![Version](https://img.shields.io/badge/Version-1.0.0.0-red.svg)](https://github.com)

---

## 📋 目录

- [功能特性](#-功能特性)
- [核心模块](#-核心模块)
  - [假人玩家系统](#1-假人玩家系统)
  - [备份与回档系统](#2-备份与回档系统)
  - [聊天增强](#3-聊天增强)
  - [白名单管理](#4-白名单管理)
  - [性能监控](#5-性能监控)
- [配置](#-配置)
- [命令](#-命令)
- [许可证](#-许可证)

---

## ✨ 功能特性

GuGuGu 模组提供全面的服务器管理工具，增强玩家体验和服务器管理能力：

- **🤖 假人玩家系统** - 创建和控制具有真实行为的假人玩家
- **💾 高级备份系统** - 支持全量/增量备份与热回档
- **💬 丰富聊天处理** - @提醒、可点击链接、物品展示、地图路径点
- **🔐 白名单安全** - 增强的白名单系统，支持离线模式
- **📊 性能监控** - 实时服务器统计和诊断
- **🎮 玩家传送** - 非 OP 玩家传送命令
- **🔄 热重载** - 配置文件热重载，无需重启服务器

---

## 🎯 核心模块

### 1. 假人玩家系统

参考知名 Mod Carpet 开发设计的假人功能，提供真实玩家的一系列功能。

#### 命令说明

配置文件位置：`config/gugugu/fakeplayer_config.json`

```json

{
  "commandLevel": 0,                                // 命令执行需要的权限级别
  "allowOpenInventory": true,                       // 是否允许玩家右键打开假人背包
  "allowInventoryInteraction": true,                // 是否允许非管理员与FakePlayer的背包的物品互动
  "fakePlayerNamePrefix": "",                       // FakePlayer玩家名称前缀、后缀
  "fakePlayerNameSuffix": "",
  "persisted": [],                                  // 持久化假人位置信息，无需修改
  "autoLoginNames": [],                             // 当服务器重启或进入存档后，需要自动进入游戏的假人名称列表
  "allowFakeServerGamePacketListenerImpl": true     // 是否允许屏蔽forge/neoforge事件系统对假人进入游戏时的感知（设置false可能导致在部分情况下，提示假人缺少指定mod所以拒绝进入服务器）
}

```

```bash
# 创建假人
/fp create <玩家名>
/fp create <玩家名> <x> <y> <z>
/fp create <玩家名> <x> <y> <z> <yaw> <pitch>
/fp create <玩家名> <x> <y> <z> in <dimension>

# 打开假人背包（仅管理员）
/fp open <假人名> [true|false]
/fp open <假人名> viewer <玩家名> [true|false]

# 自动登录管理
/fp autoLogin enable <假人名>
/fp autoLogin disable <假人名>
/fp autoLogin list

# 控制假人行为
/fp control <假人名> [use|attack|jump|drop|stopAll|kill] [interval|continue]
```

#### 配置文件

文件位置：`config/gugugu/fakeplayer_config.json`

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

### 2. 备份与回档系统

高级备份系统，支持增量和全量备份策略。

#### 功能特性

- **增量备份** - 仅备份变更的区块
- **全量备份** - 完整世界备份
- **热回档** - 无需重启服务器即可恢复区块
- **自动调度** - 自动化备份调度
- **压缩选项** - 可选压缩以节省存储空间

#### 命令说明

```bash
# 创建备份
/gbackup inc                                    # 增量备份
/gbackup full                                   # 全量备份

# 单区块回档
/gbackup rollback [hot inc|hot full] <x> <z>

# 区域回档
/gbackup rollback area <x1> <z1> <x2> <z2> [hot inc|hot full]

# 回档玩家所在区块
/gbackup rollback player [hot inc|hot full]
```

#### 配置文件

文件位置：`GBackups/<世界名>/gbackup.json`

```json
{
  "autoBackupInterval": 3600,
  "enableCompression": true,
  "maxBackupCount": 10,
  "backupCommandWhitelist": []
}
```

---

### 3. 聊天增强

丰富的聊天消息处理，支持多种处理器。

#### 消息处理器

- **@mention** - `@<玩家>` 或 `@all` 提醒
- **link** - 自动将 HTTP 链接转换为可点击
- **component** - 组合特殊格式（始终启用）
- **mention_notify** - 声音和视觉通知
- **teleport** - 点击玩家名传送
- **hand_item** - 展示持有物品：`[i]` 主手、`[io]` 副手
- **xaero_map_util** - Xaero 小地图路径点集成
- **journey_map_util** - JourneyMap 路径点集成

#### 禁用处理器

在 `config/gugugu/config.json` 中使用 `disabledMessageHandlers` 数组：

```json
{
  "disabledMessageHandlers": ["teleport", "hand_item"]
}
```

#### 查看示例

```bash
/chat components
```

---

### 4. 白名单管理

增强的白名单系统，支持离线模式。

#### 功能特性

- **UUID 检查绕过** - 支持离线模式服务器
- **安全验证** - UUID 不匹配时的二次认证
- **密码保护** - 可选的基于密码的认证

#### 工作原理

当启用 `whiteListDisableUidCheck` 时：
1. 玩家可以仅通过名称匹配加入（无 UUID 检查）
2. 如果 UUID 与白名单不匹配，需要二次验证
3. 玩家必须输入白名单中正确的 UUID 来绑定账号
4. 绑定后，使用白名单玩家的数据作为主要数据

#### 详细说明

众所周知，Minecraft 的离线服务器生成白名单时总是会造成错误的 UUID 生成，而我们的 `whiteListDisableUidCheck` 选项可以实现对进入服务器的玩家只进行名称匹配的功能。

但是禁用了 UUID 检查就无异于掩耳盗铃，别人可以直接用其他人的账号进入服务器。为此我们提供了额外的选项，即默认启用的简易安全检查 `enableSimpleSecurity`。

**安全检查机制**：

假设白名单中有 `[玩家名：a, UUID：123]`，但玩家 `a` 的真实 UUID 不是 `123`：
- 玩家 `a` 仍可进入服务器
- 但会要求进行**二次验证**
- 必须在聊天栏输入正确的白名单 UUID 进行绑定
- 绑定后，使用白名单内玩家的数据作为主要游戏数据

#### 配置文件

文件位置：`config/gugugu/whitelist.json`

```json
{
  "whiteListDisableUidCheck": true,
  "enableSimpleSecurity": true
}
```

---

### 5. 性能监控

实时服务器性能诊断。

#### 命令说明

```bash
/showstats              # 显示服务器性能统计
```

显示内容：
- TPS（每秒 Tick 数）
- 内存使用情况
- 实体数量
- 区块统计
- 维度信息

---

## ⚙️ 配置

### 主配置文件

文件位置：`config/gugugu/config.json`

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

### 配置特性

- **热重载** - 所有配置支持热重载，无需重启
- **文件监控** - 自动检测配置文件更改
- **验证** - 内置配置值验证

---

## 📝 命令

### 主命令

所有命令通过 `/gugugu` 或 `/gu` 访问：

```bash
/gu fakeplayer <...>      # 假人玩家管理
/gu backup <...>          # 备份操作
/gu chat <...>            # 聊天设置
/gu status                # 服务器状态
/gu password <...>        # 密码管理
```

### 命令别名

- `/fp` → `/gu fakeplayer`
- `/gbackup` → `/gu backup`
- `/showstats` → `/gu status`
- `/tpf <玩家>` → 传送到玩家

---

## 📄 许可证

本项目采用 MIT 许可证 - 详见 LICENSE 文件。

---

## 🔗 链接

- **问题反馈**：[提交 Bug](https://github.com/issues)
- **讨论**：[社区讨论](https://github.com/discussions)
- **Wiki**：[文档](https://github.com/wiki)

---

## 🙏 致谢

- 假人玩家实现参考了 [Carpet Mod](https://github.com/gnembon/fabric-carpet)
- 感谢 NeoForge 社区的支持
- 特别感谢所有贡献者