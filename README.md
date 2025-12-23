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

## 🛠️ 实用小功能

### 快捷传送

非OP玩家传送功能，无需管理员权限即可传送到其他玩家位置。

#### 命令说明

```bash
/gu tpf <玩家名>     # 传送到指定玩家位置
```

#### 配置启用

在 `config/gugugu/config.json` 中启用：

```json
{
  "enableTeleport": true
}
```

**注意**：管理员（权限等级2）无需开启此选项即可使用。

---

### 自杀命令

快捷自杀命令，方便玩家在困境中快速重生。

#### 命令说明

```bash
/gu killme          # 立即死亡并重生
```

**注意**：此命令所有玩家均可使用，无需特殊权限。

---

## 🎯 核心模块

### 1. 假人玩家系统

参考知名 Mod Carpet 开发设计的假人功能，提供真实玩家的一系列功能。

#### 命令说明

```bash
# 生命周期管理
/gu fakeplayer spawn <名称>                                    # 在当前位置生成假人
/gu fakeplayer spawn <名称> <游戏模式>                         # 指定游戏模式生成假人
/gu fakeplayer spawn <名称> <游戏模式> <坐标>                  # 在指定位置生成假人
/gu fakeplayer kill <假人>                                     # 移除假人

# 基础行为控制
/gu fakeplayer action <假人> attack [once] [interval]          # 攻击
/gu fakeplayer action <假人> use [once] [interval]             # 使用/右键
/gu fakeplayer action <假人> dig [once]                        # 挖掘
/gu fakeplayer action <假人> jump [once] [interval]            # 跳跃
/gu fakeplayer action <假人> drop <槽位> <丢弃全部>            # 丢弃物品

# 移动控制
/gu fakeplayer action <假人> move forward                      # 向前移动
/gu fakeplayer action <假人> move backward                     # 向后移动
/gu fakeplayer action <假人> move left                         # 向左移动
/gu fakeplayer action <假人> move right                        # 向右移动
/gu fakeplayer action <假人> move stop                         # 停止移动
/gu fakeplayer action <假人> move <前进值> <横移值>            # 自定义移动
/gu fakeplayer action <假人> sneak <true|false>                # 潜行
/gu fakeplayer action <假人> sprint <true|false>               # 疾跑

# 视角控制
/gu fakeplayer action <假人> look at <yaw> <pitch>             # 看向绝对角度
/gu fakeplayer action <假人> look turn <yaw> <pitch>           # 相对转动视角
/gu fakeplayer action <假人> look pos <坐标>                   # 看向坐标位置
/gu fakeplayer action <假人> look direction <方向>             # 看向方向(north/south/east/west/up/down)
/gu fakeplayer action <假人> look entity <目标实体>            # 追踪实体
/gu fakeplayer action <假人> look crosshair                    # 追踪准星位置

# 物品和骑乘
/gu fakeplayer action <假人> hotbar <槽位>                     # 选择快捷栏槽位
/gu fakeplayer action <假人> swap                              # 交换主副手
/gu fakeplayer action <假人> useitem [手部] [持续时间]         # 持续使用物品
/gu fakeplayer action <假人> mount [onlyRideables]             # 骑乘
/gu fakeplayer action <假人> dismount                          # 下马

# 行为停止
/gu fakeplayer action <假人> stop                              # 停止当前行为
/gu fakeplayer action <假人> stopall                           # 停止所有行为

# 配置管理
/gu fakeplayer config autologin add <名称>                     # 添加自动登录
/gu fakeplayer config autologin remove <名称>                  # 移除自动登录
/gu fakeplayer config autologin list                           # 列出自动登录列表
/gu fakeplayer config reload                                   # 重载配置
```

#### 配置文件

文件位置：`config/gugugu/fakeplayer_config.json`

```json
{
  "commandLevel": 4,                                // 命令权限等级（0-4）
  "allowOpenInventory": false,                      // 允许右键打开假人背包
  "allowInventoryInteraction": true,                // 允许非管理员与假人背包互动
  "fakePlayerNamePrefix": "",                       // 假人名称前缀
  "fakePlayerNameSuffix": "",                       // 假人名称后缀
  "persisted": [],                                  // 持久化假人信息（自动管理）
  "autoLoginNames": [],                             // 自动登录的假人名称列表
  "allowFakeServerGamePacketListenerImpl": true     // 允许伪造ServerGamePacketListenerImpl
}
```

---

### 2. 备份与回档系统

高级备份系统，支持增量和全量备份策略。

#### 功能特性

- **增量备份** - 仅备份变更的区块
- **全量备份** - 完整世界备份
- **手动备份** - 支持命名备份，便于管理
- **热回档** - 无需重启服务器即可恢复区块
- **自动调度** - 自动化备份调度

#### 命令说明

```bash
# 创建备份
/gu backup incremental                                         # 增量备份
/gu backup full                                                # 全量备份
/gu backup manual create <名称> [force]                        # 创建命名备份

# 列出与删除
/gu backup list                                                # 列出全量备份
/gu backup manual list                                         # 列出手动备份
/gu backup delete <名称>                                       # 删除全量备份
/gu backup manual delete <名称>                                # 删除手动备份

# 回档操作
/gu backup rollback here [updateEntities]                      # 回档当前区块
/gu backup rollback here from inc [updateEntities]             # 从增量备份回档
/gu backup rollback here from full [备份名] [updateEntities]   # 从全量备份回档
/gu backup rollback here from manual <备份名> [updateEntities] # 从手动备份回档
/gu backup rollback <坐标1> <坐标2> [updateEntities]           # 回档区域
/gu backup rollback <坐标1> <坐标2> from inc [updateEntities]  # 从增量备份回档区域
/gu backup rollback <坐标1> <坐标2> from full [备份名] [updateEntities] # 从全量备份回档区域

# 配置管理
/gu backup config reload                                       # 重载配置
/gu backup config show                                         # 显示配置
/gu backup config set autoBackup <true|false>                  # 设置自动备份
/gu backup config set autoBackupMinutes <分钟>                 # 设置备份间隔
/gu backup config set keepFull <数量>                          # 设置保留备份数
/gu backup config set hotRollbackSource <inc|full>             # 设置默认回档源

# 调度器管理
/gu backup scheduler status                                    # 查看调度器状态
/gu backup scheduler start                                     # 启动调度器
/gu backup scheduler stop                                      # 停止调度器
/gu backup scheduler restart                                   # 重启调度器
```

#### 配置文件

文件位置：`GBackups/<世界名>/gbackup.json`

```json
{
  "autoBackupMinutes": 60,              // 自动备份间隔（分钟）
  "enableAutoBackup": false,            // 启用自动备份
  "autoBackupWithIncremental": false,   // 自动备份时同时执行增量备份
  "keepFull": 3,                        // 保留的全量备份数量
  "hotRollbackSource": "full",         // 默认热回档数据源（inc/full）
  "commandWhitelist": []                // 命令白名单（玩家名）
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
/gu chatEvent
```

---

### 4. 白名单管理

增强的白名单系统，支持离线模式和密码绑定验证。

#### 功能特性

- **UUID 检查绕过** - 支持离线模式服务器
- **安全验证** - UUID 不匹配时的二次认证
- **密码绑定** - 基于密码的账号绑定系统
- **IP 白名单** - 记忆已验证的 IP 地址
- **失败保护** - 防止暴力破解的锁定机制

#### 工作原理

当启用 `whiteListDisableUidCheck` 时：
1. 玩家可以仅通过名称匹配加入（无 UUID 检查）
2. 如果 UUID 与白名单不匹配，需要二次验证
3. 玩家必须输入白名单中正确的 UUID 来绑定账号
4. 绑定后，使用白名单玩家的数据作为主要数据

#### 密码绑定系统

当启用 `enablePasswordAuth` 时，提供更安全的账号绑定方式：

**设置密码**：
```bash
/gu password set <密码>          # 为当前账号设置密码
```

**使用流程**：
1. 白名单玩家首次登录时设置密码
2. 其他玩家使用该名称登录时，需要输入正确密码
3. 密码验证成功后，IP 地址将被记录到白名单
4. 在白名单期内（默认60分钟），该 IP 无需再次验证
5. 密码错误达到上限（默认3次）后，账号将被锁定（默认10分钟）

**密码管理命令**：
```bash
/gu password set <密码>                     # 设置密码
/gu password change <旧密码> <新密码>        # 修改密码
/gu password check                          # 检查密码状态
/gu password remove <玩家>                  # 移除玩家密码（管理员）
/gu password clear <玩家>                   # 清除失败尝试记录（管理员）
```

**密码要求**：
- 长度至少 6 个字符
- 建议包含大小写字母、数字和特殊字符
- 系统会显示密码强度评估

#### 详细说明

众所周知，Minecraft 的离线服务器生成白名单时总是会造成错误的 UUID 生成，而我们的 `whiteListDisableUidCheck` 选项可以实现对进入服务器的玩家只进行名称匹配的功能。

但是禁用了 UUID 检查就无异于掩耳盗铃，别人可以直接用其他人的账号进入服务器。为此我们提供了两种安全验证方式：

**方式一：简单安全检查** (`enableSimpleSecurity`)

假设白名单中有 `[玩家名：a, UUID：123]`，但玩家 `a` 的真实 UUID 不是 `123`：
- 玩家 `a` 仍可进入服务器
- 但会要求进行**二次验证**
- 必须在聊天栏输入正确的白名单 UUID 进行绑定
- 绑定后，使用白名单内玩家的数据作为主要游戏数据

**方式二：密码绑定验证** (`enablePasswordAuth`)

更安全且用户友好的验证方式：
- 白名单玩家设置密码后，账号受密码保护
- 其他玩家使用该名称登录需要输入正确密码
- 支持 IP 白名单，验证过的 IP 在一定时间内无需重复验证
- 具有防暴力破解机制，多次失败后自动锁定

#### 配置文件

文件位置：`config/gugugu/whitelist.json`

```json
{
  "bindMap": {},            // UUID绑定映射（自动管理）
  "passwordHashes": {},     // 密码哈希存储（自动管理）
  "ipWhitelist": {},        // IP白名单缓存（自动管理）
  "failedAttempts": {}      // 失败尝试记录（自动管理）
}
```

**注意**：白名单功能的开关在主配置文件 `config/gugugu/config.json` 中：
```json
{
  "whiteListDisableUidCheck": true,  // 禁用UUID检查
  "enableSimpleSecurity": true       // 启用简单安全验证
}
```

---

### 5. 性能监控

实时服务器性能诊断。

#### 命令说明

```bash
/gu showstats                      # 显示服务器性能统计（第1页）
/gu showstats <页码>               # 显示指定页的维度信息
/gu showstats <页码> <维度名>      # 显示指定维度的详细信息
```

显示内容：
- **服务器信息**：版本、运行时间、玩家数、种子
- **性能指标**：TPS、MSPT（平均、中位数、95分位）、内存使用
- **维度详情**（分页显示）：
  - 玩家数量
  - 区块统计（已加载/活跃区块）
  - 实体统计（怪物、动物、水生生物）
  - 支持点击维度名查看详情

---

## ⚙️ 配置

### 主配置文件

文件位置：`config/gugugu/config.json`

```json
{
  "enableFakePlayer": false,               // 启用假人功能
  "enableMessageHandler": false,           // 启用聊天消息处理
  "disabledMessageHandlers": ["teleport"], // 禁用的消息处理器列表
  "enableTeleport": false,                 // 启用非OP传送命令(/gu tpf)
  "whiteListDisableUidCheck": false,       // 禁用白名单UUID检查
  "enableSimpleSecurity": true,            // 启用简单安全验证
  "enableBackup": false,                   // 启用备份功能
  "enablePasswordAuth": false,             // 启用密码验证系统
  "maxPasswordAttempts": 3,                // 密码验证失败最大尝试次数
  "passwordIpWhitelistMinutes": 60,        // IP白名单记忆时长（分钟）
  "passwordLockoutMinutes": 10             // 密码错误锁定时间（分钟）
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
/gu chatEvent             # 查看聊天处理器信息
/gu showstats [页码]      # 服务器状态
/gu tpf <玩家>            # 传送到玩家（需启用enableTeleport）
/gu password <...>        # 密码管理（需启用enablePasswordAuth）
/gu killme                # 自杀命令
```

### 密码管理命令

```bash
/gu password set <密码>                    # 设置密码
/gu password change <旧密码> <新密码>      # 修改密码
/gu password check                         # 检查密码状态
/gu password remove <玩家>                 # 移除玩家密码（管理员）
/gu password clear <玩家>                  # 清除失败尝试记录（管理员）
```

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