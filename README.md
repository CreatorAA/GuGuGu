# GuGuGu Mod

`(0.1.0.1)`版本规则：发生结构性变化的版本号.额外功能增删的版本号.发生方法/类变动的版本号.小修改的版本号

本MOD为游戏提供多样化的辅助工具，显著改善玩家游戏体验，同时允许服务器管理员灵活地控制和配置相关功能。

截止至`(0.2.*)`我们提供的功能：`假人玩家`、`行为控制`、`备份`、`热回档`、`聊天消息组件`、`服务器状态查询`、`白名单禁用uuid检查`、`白名单禁用检查后的安全管理`等多种功能。

---

## 功能详细介绍

### 一、假人玩家功能（FakePlayer）

参考知名Mod Carpet 开发设计的假人功能，提供真实玩家的一系列功能。

#### 配置文件介绍

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

#### 假人指令详解（`/rifakeplayer` 或 `/fp`）

* **创建假人**

  ```
  /fp create 玩家名
  /fp create 玩家名 x y z
  /fp create 玩家名 x y z yaw pitch
  /fp create 玩家名 x y z in dimension
  ```

* **打开假人背包（此子命令只支持管理员权限的玩家执行）**

  ```
  /fp open 假人名 [true|false]                    // true为允许玩家与假人背包互动，false为不允许
  /fp open 假人名 viewer 玩家名 [true|false]       // viewer为某个玩家打开指定假人的背包视图
  ```

* **自动登录设置**

  ```
  /fp autoLogin enable 假人名
  /fp autoLogin disable 假人名
  /fp autoLogin list
  ```

* **控制假人动作**

  ```
  /fp control 假人名 [use|attack|jump|drop|stopAll|kill] [interval|continue]          // 不同动作可以组合使用
  ```

---

### 二、玩家消息处理功能（/ChatEvent components查看具体示例）

支持丰富的聊天消息富文本处理，包含以下消息处理器：

* **mention**：`@{玩家}`或`@all`，强调提示特定玩家。
* **link**：自动将有效的HTTP链接转换为可点击链接。
* **component**：自动组合消息中的特殊格式（不可禁用）。
* **mention\_notify**：为被提醒玩家提供额外的音效和屏幕提示。
* **teleport**：单独输入玩家名称实现点击传送。
* **hand\_item**：支持在聊天中展示玩家持有物品，示例：`[i]`主手、`[io]`副手。
* **xaero\_map\_util**：支持 Xaero 地图路径点点击添加。
* **journey\_map\_util**：支持 JourneyMap 路径点点击添加。

#### 禁用组件方法

在`config/gugugu/config.json`中，通过`disabledMessageHandlers`字段指定禁用的组件。

---

### 三、服务器性能诊断（ShowStats）

* 使用指令 `/showstats`，快速获取当前服务器性能数据。

---

### 四、玩家间传送（TPF）

* 允许非 OP 玩家快速传送到其他玩家。

  ```
  /tpf 目标玩家名称
  ```

---

### 五、服务器备份与区域热回档（GBackup）

提供高效的手动、定时备份与热回档。

#### 指令详解

* **增量备份**：

  ```
  /gbackup inc
  ```

* **全量备份**：

  ```
  /gbackup full
  ```

* **热回档**（单区块）：

  ```
  /gbackup rollback [hot inc|hot full] [坐标]
  ```

* **区域热回档**：

  ```
  /gbackup rollback area 坐标1 坐标2 [hot inc|hot full]
  ```

#### 配置说明

配置文件：`GBackups/world/gbackup.json`

* 自动备份时间、是否启用压缩、保留备份数量
* 热回档数据源设置（增量或全量）
* 备份指令白名单

---

### 六、禁用白名单uuid检查后的简易安全检查

众所周知，Minecraft的离线服务器生成白名单时总是会造成错误的UUID生成，而我们的`whiteListDisableUidCheck`选项就可以做到对进入服务器的玩家只进行名称匹配的功能。

但是禁用了UUID检查就无异于是掩耳盗铃，别人直接就拿着其他人的账号就进服务器了，为此我们提供了额外的选项，即默认启用的简易安全检查`enableSimpleSecurity`。

安全检查的作用是防止有人浑水摸鱼，恶意破坏服务器安全，以下是触发安全检查的情况：

假设我们添加了一个白名单`[玩家：a, uuid：123]`, 但是玩家`a`的真实`uuid`不是`123`，那么玩家`a`虽然仍能正常进入服务器，但是会要求进行`二次验证`，必须在聊天栏输入正确的`白名单内的对应名称相同玩家的uuid`进行绑定，否则无法正常游玩。

绑定之后，会将两个玩家视为一人，使用白名单内玩家的数据作为主要游戏数据。

---

## 核心配置总览

位置：`config/gugugu/config.json`

* 假人功能启用开关（`enableFakePlayer`）
* 消息处理启用开关（`enableMessageHandler`）
* 玩家传送功能开关（`enableTeleport`）
* 备份功能开关（`enableBackup`）
* 白名单UID检查禁用开关（`whiteListDisableUidCheck`）
* 白名单UID检查禁用后，玩家进入游戏时的二次验证开关（`enableSimpleSecurity`）