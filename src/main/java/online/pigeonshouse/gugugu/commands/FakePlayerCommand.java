package online.pigeonshouse.gugugu.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.AngleArgument;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.commands.arguments.SlotArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import online.pigeonshouse.gugugu.GuGuGu;
import online.pigeonshouse.gugugu.fakeplayer.RIFakeServerPlayer;
import online.pigeonshouse.gugugu.fakeplayer.RIFakeServerPlayerFactory;
import online.pigeonshouse.gugugu.fakeplayer.config.FakePlayerConfig;
import online.pigeonshouse.gugugu.fakeplayer.control.BehaviorController;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

/**
 * 假人命令
 * <p>
 * 提供假人的创建、控制、行为管理等功能
 */
public class FakePlayerCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("fakeplayer")
                .requires(source -> source.hasPermission(GuGuGu.getINSTANCE().getFakePlayerConfig().getCommandLevel()))
                // 无参数时显示help
                .executes(FakePlayerCommand::showHelp)
                // help - 帮助信息
                .then(Commands.literal("help")
                        .executes(FakePlayerCommand::showHelp)
                )
                // spawn - 召唤假人（支持多种参数组合）
                .then(Commands.literal("spawn")
                        .then(Commands.argument("name", StringArgumentType.string())
                                // spawn <名称>
                                .executes(ctx -> spawnFakePlayer(ctx))
                                // spawn <名称> <坐标>
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(ctx -> spawnFakePlayer(ctx))
                                        // spawn <名称> <坐标> <朝向>
                                        .then(Commands.argument("yaw", AngleArgument.angle())
                                                .then(Commands.argument("pitch", AngleArgument.angle())
                                                        .executes(ctx -> spawnFakePlayer(ctx))
                                                        // spawn <名称> <坐标> <朝向> in <维度>
                                                        .then(Commands.literal("in")
                                                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                                                        .executes(ctx -> spawnFakePlayer(ctx))
                                                                        // spawn <名称> <坐标> <朝向> in <维度> mode <游戏模式>
                                                                        .then(Commands.literal("mode")
                                                                                .requires(r -> r.hasPermission(4))
                                                                                .then(Commands.argument("gamemode", GameModeArgument.gameMode())
                                                                                        .executes(ctx -> spawnFakePlayer(ctx))
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                        // spawn <名称> <坐标> in <维度>
                                        .then(Commands.literal("in")
                                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                                        .executes(ctx -> spawnFakePlayer(ctx))
                                                        // spawn <名称> <坐标> in <维度> mode <游戏模式>
                                                        .then(Commands.literal("mode")
                                                                .requires(r -> r.hasPermission(4))
                                                                .then(Commands.argument("gamemode", GameModeArgument.gameMode())
                                                                        .executes(ctx -> spawnFakePlayer(ctx))
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
                // kill - 移除假人
                .then(Commands.literal("kill")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(FakePlayerCommand::killFakePlayer)
                        )
                )
                // action - 行为控制
                .then(Commands.literal("action")
                        .then(Commands.argument("player", EntityArgument.player())
                                // attack - 攻击
                                .then(Commands.literal("attack")
                                        .executes(ctx -> setAction(ctx, "attack", false, 0))
                                        .then(Commands.argument("once", BoolArgumentType.bool())
                                                .executes(ctx -> setAction(ctx, "attack", BoolArgumentType.getBool(ctx, "once"), 0))
                                                .then(Commands.argument("interval", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> setAction(ctx, "attack",
                                                                BoolArgumentType.getBool(ctx, "once"),
                                                                IntegerArgumentType.getInteger(ctx, "interval")))
                                                )
                                        )
                                )
                                // use - 使用/右键
                                .then(Commands.literal("use")
                                        .executes(ctx -> setAction(ctx, "use", false, 0))
                                        .then(Commands.argument("once", BoolArgumentType.bool())
                                                .executes(ctx -> setAction(ctx, "use", BoolArgumentType.getBool(ctx, "once"), 0))
                                                .then(Commands.argument("interval", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> setAction(ctx, "use",
                                                                BoolArgumentType.getBool(ctx, "once"),
                                                                IntegerArgumentType.getInteger(ctx, "interval")))
                                                )
                                        )
                                )
                                // dig - 挖掘
                                .then(Commands.literal("dig")
                                        .executes(ctx -> setAction(ctx, "dig", false, 0))
                                        .then(Commands.argument("once", BoolArgumentType.bool())
                                                .executes(ctx -> setAction(ctx, "dig", BoolArgumentType.getBool(ctx, "once"), 0))
                                        )
                                )
                                // jump - 跳跃
                                .then(Commands.literal("jump")
                                        .executes(ctx -> setAction(ctx, "jump", false, 0))
                                        .then(Commands.argument("once", BoolArgumentType.bool())
                                                .executes(ctx -> setAction(ctx, "jump", BoolArgumentType.getBool(ctx, "once"), 0))
                                                .then(Commands.argument("interval", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> setAction(ctx, "jump",
                                                                BoolArgumentType.getBool(ctx, "once"),
                                                                IntegerArgumentType.getInteger(ctx, "interval")))
                                                )
                                        )
                                )
                                // drop - 丢弃物品
                                .then(Commands.literal("drop")
                                        .executes(ctx -> setAction(ctx, "drop", false, 0))
                                        .then(Commands.argument("slot", SlotArgument.slot())
                                                .then(Commands.argument("dropAll", BoolArgumentType.bool())
                                                        .executes(FakePlayerCommand::dropItem)
                                                )
                                        )
                                )
                                // sneak - 潜行
                                .then(Commands.literal("sneak")
                                        .then(Commands.argument("enable", BoolArgumentType.bool())
                                                .executes(FakePlayerCommand::sneak)
                                        )
                                )
                                // sprint - 疾跑
                                .then(Commands.literal("sprint")
                                        .then(Commands.argument("enable", BoolArgumentType.bool())
                                                .executes(FakePlayerCommand::sprint)
                                        )
                                )
                                // move - 移动
                                .then(Commands.literal("move")
                                        .then(Commands.literal("forward")
                                                .executes(ctx -> setMove(ctx, "forward"))
                                        )
                                        .then(Commands.literal("backward")
                                                .executes(ctx -> setMove(ctx, "backward"))
                                        )
                                        .then(Commands.literal("left")
                                                .executes(ctx -> setMove(ctx, "left"))
                                        )
                                        .then(Commands.literal("right")
                                                .executes(ctx -> setMove(ctx, "right"))
                                        )
                                        .then(Commands.literal("stop")
                                                .executes(ctx -> setMove(ctx, "stop"))
                                        )
                                        .then(Commands.argument("forward", FloatArgumentType.floatArg(-1.0f, 1.0f))
                                                .then(Commands.argument("strafing", FloatArgumentType.floatArg(-1.0f, 1.0f))
                                                        .executes(FakePlayerCommand::moveCustom)
                                                )
                                        )
                                )
                                // look - 视角控制
                                .then(Commands.literal("look")
                                        // 看向绝对角度
                                        .then(Commands.literal("at")
                                                .then(Commands.argument("yaw", AngleArgument.angle())
                                                        .then(Commands.argument("pitch", AngleArgument.angle())
                                                                .executes(FakePlayerCommand::lookAbsolute)
                                                        )
                                                )
                                        )
                                        // 相对转动
                                        .then(Commands.literal("turn")
                                                .then(Commands.argument("yaw", AngleArgument.angle())
                                                        .then(Commands.argument("pitch", AngleArgument.angle())
                                                                .executes(FakePlayerCommand::lookRelative)
                                                        )
                                                )
                                        )
                                        // 看向位置
                                        .then(Commands.literal("pos")
                                                .then(Commands.argument("position", Vec3Argument.vec3())
                                                        .executes(FakePlayerCommand::lookAtPosition)
                                                )
                                        )
                                        // 看向方向
                                        .then(Commands.literal("direction")
                                                .then(Commands.literal("north")
                                                        .executes(ctx -> lookAtDirection(ctx, Direction.NORTH))
                                                )
                                                .then(Commands.literal("south")
                                                        .executes(ctx -> lookAtDirection(ctx, Direction.SOUTH))
                                                )
                                                .then(Commands.literal("east")
                                                        .executes(ctx -> lookAtDirection(ctx, Direction.EAST))
                                                )
                                                .then(Commands.literal("west")
                                                        .executes(ctx -> lookAtDirection(ctx, Direction.WEST))
                                                )
                                                .then(Commands.literal("up")
                                                        .executes(ctx -> lookAtDirection(ctx, Direction.UP))
                                                )
                                                .then(Commands.literal("down")
                                                        .executes(ctx -> lookAtDirection(ctx, Direction.DOWN))
                                                )
                                        )
                                        // 追踪实体
                                        .then(Commands.literal("entity")
                                                .then(Commands.argument("target", EntityArgument.entity())
                                                        .executes(FakePlayerCommand::trackEntity)
                                                )
                                        )
                                        // 追踪准星位置
                                        .then(Commands.literal("crosshair")
                                                .executes(FakePlayerCommand::trackCrosshair)
                                        )
                                )
                                // mount - 骑乘
                                .then(Commands.literal("mount")
                                        .executes(ctx -> mount(ctx, true))
                                        .then(Commands.argument("onlyRideables", BoolArgumentType.bool())
                                                .executes(ctx -> mount(ctx, BoolArgumentType.getBool(ctx, "onlyRideables")))
                                        )
                                )
                                // dismount - 下马
                                .then(Commands.literal("dismount")
                                        .executes(FakePlayerCommand::dismount)
                                )
                                // hotbar - 快捷栏
                                .then(Commands.literal("hotbar")
                                        .then(Commands.argument("slot", SlotArgument.slot())
                                                .executes(FakePlayerCommand::selectHotbar)
                                        )
                                )
                                // swap - 交换主副手
                                .then(Commands.literal("swap")
                                        .executes(FakePlayerCommand::swapHands)
                                )
                                // useitem - 持续使用物品
                                .then(Commands.literal("useitem")
                                        .executes(ctx -> useItem(ctx, InteractionHand.MAIN_HAND, -1))
                                        .then(Commands.argument("hand", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    builder.suggest("mainhand");
                                                    builder.suggest("offhand");
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> useItem(ctx, parseHand(StringArgumentType.getString(ctx, "hand")), -1))
                                                .then(Commands.argument("maxDuration", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> useItem(ctx,
                                                                parseHand(StringArgumentType.getString(ctx, "hand")),
                                                                IntegerArgumentType.getInteger(ctx, "maxDuration")))
                                                )
                                        )
                                )
                                // stop - 停止行为
                                .then(Commands.literal("stop")
                                        .executes(FakePlayerCommand::stopAction)
                                )
                                // stopall - 停止所有行为
                                .then(Commands.literal("stopall")
                                        .executes(FakePlayerCommand::stopAllActions)
                                )
                        )
                )
                // config - 配置管理
                .then(Commands.literal("config")
                        // autologin - 自动登录管理
                        .then(Commands.literal("autologin")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(FakePlayerCommand::addAutoLogin)
                                        )
                                )
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(FakePlayerCommand::removeAutoLogin)
                                        )
                                )
                                .then(Commands.literal("list")
                                        .executes(FakePlayerCommand::listAutoLogin)
                                )
                        )
                        // reload - 重载配置
                        .then(Commands.literal("reload")
                                .executes(FakePlayerCommand::reloadConfig)
                        )
                );
    }

    // ==================== 帮助信息 ====================

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.title"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.empty"), false);

        // 假人管理
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.management"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.spawn"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.kill"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.empty"), false);

        // 基础行为
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.basic_actions"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.attack"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.use"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.dig"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.jump"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.drop"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.empty"), false);

        // 移动和状态
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.movement"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.move"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.sneak"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.sprint"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.empty"), false);

        // 视角控制
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.look"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.look_at"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.look_turn"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.look_pos"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.look_direction"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.look_entity"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.look_crosshair"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.empty"), false);

        // 物品和骑乘
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.items"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.hotbar"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.swap"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.useitem"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.mount"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.dismount"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.empty"), false);

        // 行为控制
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.control"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.stop"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.stopall"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.empty"), false);

        // 配置管理
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.config"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.config_autologin_add"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.config_autologin_remove"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.config_autologin_list"), false);
        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.config_reload"), false);

        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.help.footer"), false);

        return 1;
    }

    // ==================== 假人生命周期 ====================

    private static int spawnFakePlayer(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        MinecraftServer server = source.getServer();

        Vec3 pos;
        try {
            pos = Vec3Argument.getVec3(ctx, "pos");
        } catch (IllegalArgumentException e) {
            try {
                ServerPlayer executor = source.getPlayerOrException();
                pos = executor.position();
            } catch (Exception ex) {
                pos = new Vec3(0, 64, 0);
            }
        }

        float yaw = 0;
        float pitch = 0;
        try {
            yaw = AngleArgument.getAngle(ctx, "yaw");
            pitch = AngleArgument.getAngle(ctx, "pitch");
        } catch (IllegalArgumentException e) {
            // 无朝向参数，使用默认值或命令执行者朝向
            try {
                ServerPlayer executor = source.getPlayerOrException();
                yaw = executor.getYRot();
                pitch = executor.getXRot();
            } catch (Exception ex) {
                // 使用默认值 0, 0
            }
        }

        ServerLevel level;
        try {
            level = DimensionArgument.getDimension(ctx, "dimension");
        } catch (IllegalArgumentException | CommandSyntaxException e) {
            // 无维度参数，使用命令执行者所在维度
            level = source.getLevel();
        }

        GameType gameMode;
        try {
            gameMode = GameModeArgument.getGameMode(ctx, "gamemode");
        } catch (IllegalArgumentException | CommandSyntaxException e) {
            // 无游戏模式参数，使用默认值
            gameMode = GameType.SURVIVAL;
        }

        Vec3 finalPos = pos;
        ServerLevel finalLevel = level;
        GameType finalGameMode = gameMode;
        float finalYaw = yaw;
        float finalPitch = pitch;

        RIFakeServerPlayerFactory.createFakeServerPlayer(
                server, name, finalLevel, finalGameMode,
                finalPos.x, finalPos.y, finalPos.z, finalYaw, finalPitch
        ).thenAccept(fakePlayer -> {
            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.spawn.success", name)
                    .withStyle(ChatFormatting.GREEN), true);
        });

        return 1;
    }

    private static int killFakePlayer(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");

            if (!(player instanceof RIFakeServerPlayer fakePlayer)) {
                source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.not_fake")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            String name = fakePlayer.getName().getString();
            fakePlayer.disconnect(Component.literal("Killed by command"));

            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.kill.success", name)
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    // ==================== 行为控制 ====================

    private static BehaviorController getController(ServerPlayer player) {
        return GuGuGu.getINSTANCE().getFakePlayerManager()
                .getOrCreateController(player);
    }

    private static int setAction(CommandContext<CommandSourceStack> ctx, String action, boolean once, int interval) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            BehaviorController controller = getController(player);

            switch (action) {
                case "attack" -> {
                    if (interval > 0) {
                        controller.attack(once, interval);
                    } else {
                        controller.attack(once);
                    }
                }
                case "use" -> {
                    if (interval > 0) {
                        controller.use(once, interval, InteractionHand.MAIN_HAND);
                    } else {
                        controller.use(once);
                    }
                }
                case "dig" -> controller.dig(once);
                case "jump" -> {
                    if (interval > 0) {
                        controller.jump(once, interval);
                    } else {
                        controller.jump(once);
                    }
                }
            }

            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.action.set",
                    player.getName().getString(), action).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int dropItem(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            BehaviorController controller = getController(player);

            int slot = SlotArgument.getSlot(ctx, "slot");
            boolean dropAll = BoolArgumentType.getBool(ctx, "dropAll");

            controller.drop(slot, dropAll);

            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.action.drop",
                    player.getName().getString(), slot).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int sneak(CommandContext<CommandSourceStack> ctx) {
        return toggleState(ctx, "sneak", BoolArgumentType.getBool(ctx, "enable"));
    }

    private static int sprint(CommandContext<CommandSourceStack> ctx) {
        return toggleState(ctx, "sprint", BoolArgumentType.getBool(ctx, "enable"));
    }

    private static int toggleState(CommandContext<CommandSourceStack> ctx, String action, boolean enable) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            BehaviorController controller = getController(player);

            if (action.equals("sneak")) {
                controller.sneak(enable);
            } else if (action.equals("sprint")) {
                controller.sprint(enable);
            }

            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.action." + action,
                    player.getName().getString(), enable).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setMove(CommandContext<CommandSourceStack> ctx, String direction) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            BehaviorController controller = getController(player);

            switch (direction) {
                case "forward" -> controller.moveForward();
                case "backward" -> controller.moveBackward();
                case "left" -> controller.moveLeft();
                case "right" -> controller.moveRight();
                case "stop" -> controller.stopMove();
            }

            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.action.move",
                    player.getName().getString(), direction).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int moveCustom(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            BehaviorController controller = getController(player);

            float forward = FloatArgumentType.getFloat(ctx, "forward");
            float strafing = FloatArgumentType.getFloat(ctx, "strafing");

            controller.move(forward, strafing);

            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.action.move.custom",
                    player.getName().getString(), forward, strafing).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int lookAbsolute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            BehaviorController controller = getController(player);

            float yaw = AngleArgument.getAngle(ctx, "yaw");
            float pitch = AngleArgument.getAngle(ctx, "pitch");

            controller.look(yaw, pitch);

            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.action.look.absolute",
                    player.getName().getString(), yaw, pitch).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int lookRelative(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            BehaviorController controller = getController(player);

            float yaw = AngleArgument.getAngle(ctx, "yaw");
            float pitch = AngleArgument.getAngle(ctx, "pitch");

            controller.turn(yaw, pitch);

            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.action.look.relative",
                    player.getName().getString(), yaw, pitch).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int lookAtPosition(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            BehaviorController controller = getController(player);

            Vec3 position = Vec3Argument.getVec3(ctx, "position");
            controller.lookAt(position);

            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.action.look.position",
                    player.getName().getString()).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int lookAtDirection(CommandContext<CommandSourceStack> ctx, Direction direction) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            BehaviorController controller = getController(player);

            controller.lookAt(direction);

            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.action.look.direction",
                    player.getName().getString(), direction.getName()).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int trackEntity(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            BehaviorController controller = getController(player);

            Entity target = EntityArgument.getEntity(ctx, "target");
            controller.trackEntity(target);

            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.action.look.track_entity",
                    player.getName().getString(), target.getName().getString()).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int trackCrosshair(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            BehaviorController controller = getController(player);

            controller.trackCrosshair();

            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.action.look.track_crosshair",
                    player.getName().getString()).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int mount(CommandContext<CommandSourceStack> ctx, boolean onlyRideables) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            BehaviorController controller = getController(player);

            controller.mount(onlyRideables);

            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.action.mount",
                    player.getName().getString()).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int dismount(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            BehaviorController controller = getController(player);

            controller.dismount();

            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.action.dismount",
                    player.getName().getString()).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int selectHotbar(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            BehaviorController controller = getController(player);

            int slot = SlotArgument.getSlot(ctx, "slot");
            controller.selectHotbar(slot);

            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.action.hotbar",
                    player.getName().getString(), slot).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int swapHands(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            BehaviorController controller = getController(player);

            controller.swapHands();

            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.action.swap",
                    player.getName().getString()).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int useItem(CommandContext<CommandSourceStack> ctx, InteractionHand hand, int maxDuration) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            BehaviorController controller = getController(player);

            if (maxDuration > 0) {
                controller.useItem(hand, maxDuration);
            } else {
                controller.useItem();
            }

            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.action.useitem",
                    player.getName().getString()).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int stopAction(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            BehaviorController controller = getController(player);

            controller.stop();

            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.action.stop",
                    player.getName().getString()).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int stopAllActions(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            BehaviorController controller = getController(player);

            controller.stopAll();

            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.action.stopall",
                    player.getName().getString()).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    // ==================== 配置管理 ====================

    private static int addAutoLogin(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        FakePlayerConfig config = GuGuGu.getINSTANCE().getFakePlayerConfig();

        config.getAutoLoginNames().add(name);
        config.save();

        source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.config.autologin.add", name)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removeAutoLogin(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        FakePlayerConfig config = GuGuGu.getINSTANCE().getFakePlayerConfig();

        if (config.getAutoLoginNames().remove(name)) {
            config.save();
            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.config.autologin.remove", name)
                    .withStyle(ChatFormatting.GREEN), true);
        } else {
            source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.config.autologin.not_found", name)
                    .withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    private static int listAutoLogin(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        FakePlayerConfig config = GuGuGu.getINSTANCE().getFakePlayerConfig();

        if (config.getAutoLoginNames().isEmpty()) {
            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.config.autologin.empty")
                    .withStyle(ChatFormatting.YELLOW), false);
        } else {
            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.config.autologin.list")
                    .withStyle(ChatFormatting.GOLD), false);
            for (String name : config.getAutoLoginNames()) {
                source.sendSuccess(() -> Component.literal("  - " + name)
                        .withStyle(ChatFormatting.WHITE), false);
            }
        }
        return 1;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            GuGuGu.getINSTANCE().getFakePlayerConfig().load();
            source.sendSuccess(() -> MinecraftUtil.translate("gugugu.fakeplayer.config.reload")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(MinecraftUtil.translate("gugugu.fakeplayer.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    // ==================== 辅助方法 ====================

    private static InteractionHand parseHand(String handStr) {
        return handStr.equalsIgnoreCase("offhand") ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }
}
