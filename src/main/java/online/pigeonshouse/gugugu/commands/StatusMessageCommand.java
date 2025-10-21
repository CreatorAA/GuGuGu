package online.pigeonshouse.gugugu.commands;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

import java.lang.management.ManagementFactory;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

public class StatusMessageCommand {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("#.#");

    public static void sendStats(ServerPlayer player, MinecraftServer server) {
        long[] tickTimes = server.getTickTimesNanos();
        TickStats tickStats = calculateTickStats(tickTimes);

        Runtime runtime = Runtime.getRuntime();
        long usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMem = runtime.maxMemory() / (1024 * 1024);
        long allocatedMem = runtime.totalMemory() / (1024 * 1024);
        double memUsagePercent = (double) usedMem / maxMem * 100;

        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        String uptimeStr = formatUptime(uptimeMs);

        int playerCount = server.getPlayerCount();
        int maxPlayers = server.getMaxPlayers();
        double playerPercent = (double) playerCount / maxPlayers * 100;

        String version = server.getServerVersion();
        long seed = server.overworld().getSeed();

        ServerLevel world = player.serverLevel();
        long ticks = world.getDayTime();
        long day = ticks / 24000 + 1;
        long tod = ticks % 24000;
        int h = (int) ((tod / 1000 + 6) % 24);
        int m = (int) ((tod % 1000) * 60 / 1000);
        String timeStr = String.format("Day %d | %02d:%02d", day, h, m);

        player.sendSystemMessage(Component.literal("━━━━━ ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("服务器状态").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(" ━━━━━").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));

        player.sendSystemMessage(Component.literal("版本: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(version).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" │ ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("运行: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(uptimeStr).withStyle(ChatFormatting.WHITE)));

        MutableComponent playerInfo = Component.literal("玩家: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(playerCount + "/" + maxPlayers)
                        .withStyle(playerCount >= maxPlayers * 0.9 ? ChatFormatting.RED :
                                playerCount >= maxPlayers * 0.7 ? ChatFormatting.YELLOW : ChatFormatting.GREEN))
                .append(Component.literal(" (" + PERCENT_FORMAT.format(playerPercent) + "%)")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" │ ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("时间: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(timeStr).withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(playerInfo);

        MutableComponent seedInfo = Component.literal("种子: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(seed)).withStyle(ChatFormatting.DARK_GREEN)
                        .withStyle(style -> style
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("点击复制种子").withStyle(ChatFormatting.YELLOW)))
                                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, String.valueOf(seed)))));
        player.sendSystemMessage(seedInfo);

        // ========== 性能指标 ==========
        player.sendSystemMessage(Component.literal("─────────────────").withStyle(ChatFormatting.DARK_GRAY));
        MutableComponent tpsInfo = Component.literal("TPS: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(DECIMAL_FORMAT.format(tickStats.tps))
                        .withStyle(tickStats.tps >= 19.5 ? ChatFormatting.GREEN :
                                tickStats.tps >= 18.0 ? ChatFormatting.YELLOW :
                                        tickStats.tps >= 15.0 ? ChatFormatting.GOLD : ChatFormatting.RED))
                .append(Component.literal("/20").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" (" + PERCENT_FORMAT.format(tickStats.tps / 20.0 * 100) + "%)")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Ticks Per Second\n\n").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                                .append(Component.literal("理想值: ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("20.0\n").withStyle(ChatFormatting.GREEN))
                                .append(Component.literal("当前值: ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(DECIMAL_FORMAT.format(tickStats.tps)).withStyle(
                                        tickStats.tps >= 19.5 ? ChatFormatting.GREEN : ChatFormatting.RED)))));

        MutableComponent msptInfo = Component.literal(" │ MSPT: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(DECIMAL_FORMAT.format(tickStats.avg))
                        .withStyle(tickStats.avg <= 50 ? ChatFormatting.GREEN :
                                tickStats.avg <= 100 ? ChatFormatting.YELLOW : ChatFormatting.RED))
                .append(Component.literal("/50ms").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" [" + DECIMAL_FORMAT.format(tickStats.min) + "-" +
                        DECIMAL_FORMAT.format(tickStats.max) + "]").withStyle(ChatFormatting.DARK_GRAY))
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Milliseconds Per Tick\n\n").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                                .append(Component.literal("理想值: ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("≤ 50.0ms\n\n").withStyle(ChatFormatting.GREEN))
                                .append(Component.literal("平均: ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(DECIMAL_FORMAT.format(tickStats.avg) + "ms\n").withStyle(ChatFormatting.WHITE))
                                .append(Component.literal("中位数: ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(DECIMAL_FORMAT.format(tickStats.median) + "ms\n").withStyle(ChatFormatting.WHITE))
                                .append(Component.literal("最小值: ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(DECIMAL_FORMAT.format(tickStats.min) + "ms\n").withStyle(ChatFormatting.GREEN))
                                .append(Component.literal("最大值: ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(DECIMAL_FORMAT.format(tickStats.max) + "ms\n").withStyle(ChatFormatting.RED))
                                .append(Component.literal("95分位: ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(DECIMAL_FORMAT.format(tickStats.percentile95) + "ms").withStyle(ChatFormatting.YELLOW)))));

        player.sendSystemMessage(tpsInfo.append(msptInfo));

        // MSPT 详细统计 - 紧凑显示
        player.sendSystemMessage(Component.literal("  Med: ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(DECIMAL_FORMAT.format(tickStats.median))
                        .withStyle(tickStats.median <= 50 ? ChatFormatting.GREEN : ChatFormatting.YELLOW))
                .append(Component.literal("ms │ 95%: ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(DECIMAL_FORMAT.format(tickStats.percentile95))
                        .withStyle(tickStats.percentile95 <= 50 ? ChatFormatting.GREEN :
                                tickStats.percentile95 <= 100 ? ChatFormatting.YELLOW : ChatFormatting.RED))
                .append(Component.literal("ms").withStyle(ChatFormatting.DARK_GRAY)));

        // 内存统计
        MutableComponent memInfo = Component.literal("内存: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(usedMem + "MB").withStyle(
                        memUsagePercent > 90 ? ChatFormatting.RED :
                                memUsagePercent > 70 ? ChatFormatting.YELLOW : ChatFormatting.GREEN))
                .append(Component.literal("/" + maxMem + "MB").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" (" + PERCENT_FORMAT.format(memUsagePercent) + "%)")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" │ 已分配: ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(allocatedMem + "MB").withStyle(ChatFormatting.GRAY))
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("内存使用情况\n\n").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                                .append(Component.literal("已用: ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(usedMem + "MB\n").withStyle(ChatFormatting.WHITE))
                                .append(Component.literal("已分配: ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(allocatedMem + "MB\n").withStyle(ChatFormatting.WHITE))
                                .append(Component.literal("最大: ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(maxMem + "MB\n").withStyle(ChatFormatting.WHITE))
                                .append(Component.literal("使用率: ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(PERCENT_FORMAT.format(memUsagePercent) + "%").withStyle(
                                        memUsagePercent > 90 ? ChatFormatting.RED : ChatFormatting.GREEN)))));
        player.sendSystemMessage(memInfo);

        // ========== 维度详情 ==========
        player.sendSystemMessage(Component.literal("─────────────────").withStyle(ChatFormatting.DARK_GRAY));

        List<ServerLevel> levels = MinecraftUtil.iterableToStream(server.getAllLevels()).toList();
        for (int i = 0; i < levels.size(); i++) {
            boolean isLast = i == levels.size() - 1;
            sendWorldInfo(player, levels.get(i), isLast);
        }

        player.sendSystemMessage(Component.literal("━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    private static void sendWorldInfo(ServerPlayer player, ServerLevel lvl, boolean isLast) {
        String dimensionName = getDimensionName(lvl);
        String dimensionKey = lvl.dimension().location().getPath();
        int playerCount = lvl.players().size();
        int chunks = lvl.getChunkSource().getLoadedChunksCount();
        int tickingChunks = lvl.getChunkSource().getTickingGenerated();

        // 获取可生成区块数量（用于计算生物上限）
        ServerChunkCache chunkSource = lvl.getChunkSource();
        int spawnableChunks = chunkSource.chunkMap.getDistanceManager().getNaturalSpawnChunkCount();

        // 获取各类生物的实际数量和上限
        NaturalSpawner.SpawnState spawnState = lvl.getChunkSource().getLastSpawnState();
        MobCategoryStats monsterStats = getMobCategoryStats(spawnState, MobCategory.MONSTER, spawnableChunks);
        MobCategoryStats creatureStats = getMobCategoryStats(spawnState, MobCategory.CREATURE, spawnableChunks);
        MobCategoryStats waterCreatureStats = getMobCategoryStats(spawnState, MobCategory.WATER_CREATURE, spawnableChunks);
        MobCategoryStats waterAmbientStats = getMobCategoryStats(spawnState, MobCategory.WATER_AMBIENT, spawnableChunks);
        MobCategoryStats ambientStats = getMobCategoryStats(spawnState, MobCategory.AMBIENT, spawnableChunks);

        // 获取总实体数
        int totalEntities = spawnState.getMobCategoryCounts().values().stream().mapToInt(Integer::intValue).sum();

        ChatFormatting entityColor = totalEntities > 1000 ? ChatFormatting.RED :
                totalEntities > 500 ? ChatFormatting.YELLOW : ChatFormatting.GREEN;

        ChatFormatting chunkColor = chunks > 5000 ? ChatFormatting.RED :
                chunks > 3000 ? ChatFormatting.YELLOW : ChatFormatting.GREEN;

        String prefix = isLast ? "└ " : "├ ";

        // 维度名称行 + 基础信息整合
        MutableComponent dimensionLine = Component.literal(prefix).withStyle(ChatFormatting.GRAY)
                .append(Component.literal(dimensionName).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal(" │ ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("玩家:").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(String.valueOf(playerCount))
                        .withStyle(playerCount > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                .append(Component.literal(" │ ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("区块:").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(String.valueOf(chunks)).withStyle(chunkColor))
                .append(Component.literal("/" + tickingChunks).withStyle(ChatFormatting.DARK_GRAY))
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("维度: " + dimensionKey + "\n\n").withStyle(ChatFormatting.YELLOW)
                                .append(Component.literal("已加载区块: ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(chunks + "\n").withStyle(ChatFormatting.WHITE))
                                .append(Component.literal("活跃区块: ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(tickingChunks + "\n").withStyle(ChatFormatting.WHITE))
                                .append(Component.literal("可生成区块: ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(String.valueOf(spawnableChunks)).withStyle(ChatFormatting.WHITE)))));

        player.sendSystemMessage(dimensionLine);

        String subPrefix = isLast ? "  " : "│ ";

        MutableComponent entityLine = Component.literal(subPrefix + "实体:").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(String.valueOf(totalEntities)).withStyle(entityColor))
                .append(Component.literal(" [").withStyle(ChatFormatting.DARK_GRAY));

        // 怪物
        if (monsterStats.cap > 0) {
            entityLine.append(Component.literal("怪:").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(monsterStats.count + "/" + monsterStats.cap).withStyle(monsterStats.color))
                    .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("怪物生成统计\n\n").withStyle(ChatFormatting.YELLOW)
                                    .append(Component.literal("当前: ").withStyle(ChatFormatting.GRAY))
                                    .append(Component.literal(monsterStats.count + "\n").withStyle(ChatFormatting.WHITE))
                                    .append(Component.literal("上限: ").withStyle(ChatFormatting.GRAY))
                                    .append(Component.literal(monsterStats.cap + "\n").withStyle(ChatFormatting.WHITE))
                                    .append(Component.literal("占用率: ").withStyle(ChatFormatting.GRAY))
                                    .append(Component.literal(PERCENT_FORMAT.format(monsterStats.percent) + "%\n").withStyle(monsterStats.color))
                                    .append(Component.literal("\n基础上限: 70 × ").withStyle(ChatFormatting.DARK_GRAY))
                                    .append(Component.literal(spawnableChunks + " / 289").withStyle(ChatFormatting.DARK_GRAY)))));
        }

        // 动物
        if (creatureStats.cap > 0) {
            entityLine.append(Component.literal(" 动:").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(creatureStats.count + "/" + creatureStats.cap).withStyle(creatureStats.color));
        }

        // 水生生物
        if (waterCreatureStats.cap > 0 || waterAmbientStats.cap > 0) {
            int waterTotal = waterCreatureStats.count + waterAmbientStats.count;
            int waterCap = waterCreatureStats.cap + waterAmbientStats.cap;
            double waterPercent = waterCap > 0 ? (double) waterTotal / waterCap * 100 : 0;
            ChatFormatting waterColor = waterPercent < 50 ? ChatFormatting.GREEN :
                    waterPercent < 80 ? ChatFormatting.YELLOW : ChatFormatting.GOLD;
            entityLine.append(Component.literal(" 水:").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(waterTotal + "/" + waterCap).withStyle(waterColor));
        }

        entityLine.append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
        player.sendSystemMessage(entityLine);
    }

    private static String getDimensionName(ServerLevel level) {
        String path = level.dimension().location().getPath();
        return switch (path) {
            case "overworld" -> "主世界";
            case "the_nether" -> "下界";
            case "the_end" -> "末地";
            default -> path;
        };
    }

    private static String formatUptime(long uptimeMs) {
        long days = uptimeMs / 86_400_000;
        long hours = (uptimeMs / 3_600_000) % 24;
        long minutes = (uptimeMs / 60_000) % 60;
        long seconds = (uptimeMs / 1000) % 60;

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    private static TickStats calculateTickStats(long[] tickTimesNanos) {
        if (tickTimesNanos == null || tickTimesNanos.length == 0) {
            return new TickStats(20.0, 0, 0, 0, 0, 0);
        }

        double[] tickTimeMs = new double[tickTimesNanos.length];
        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;

        for (int i = 0; i < tickTimesNanos.length; i++) {
            double ms = tickTimesNanos[i] / 1_000_000.0;
            tickTimeMs[i] = ms;
            sum += ms;
            min = Math.min(min, ms);
            max = Math.max(max, ms);
        }

        double avg = sum / tickTimesNanos.length;
        double tps = Math.min(1000.0 / avg, 20.0);
        Arrays.sort(tickTimeMs);

        double median;
        int midIndex = tickTimeMs.length / 2;
        if (tickTimeMs.length % 2 == 0) {
            median = (tickTimeMs[midIndex - 1] + tickTimeMs[midIndex]) / 2.0;
        } else {
            median = tickTimeMs[midIndex];
        }

        double percentile95 = calculatePercentile(tickTimeMs, 0.95);

        return new TickStats(tps, avg, median, min, max, percentile95);
    }

    private static double calculatePercentile(double[] sortedArray, double percentile) {
        if (sortedArray.length == 0) return 0;
        if (sortedArray.length == 1) return sortedArray[0];

        double pos = (sortedArray.length - 1) * percentile;
        int base = (int) Math.floor(pos);
        double rest = pos - base;

        if (base + 1 < sortedArray.length) {
            return sortedArray[base] + rest * (sortedArray[base + 1] - sortedArray[base]);
        } else {
            return sortedArray[base];
        }
    }

    private static MobCategoryStats getMobCategoryStats(NaturalSpawner.SpawnState spawnState, MobCategory category, int spawnableChunks) {
        if (spawnState == null) {
            return new MobCategoryStats(0, 0, 0, ChatFormatting.GRAY);
        }

        int count = spawnState.getMobCategoryCounts().getOrDefault(category, 0);
        int baseCap = category.getMaxInstancesPerChunk();
        int cap = baseCap * spawnableChunks / 289;
        double percent = cap > 0 ? (double) count / cap * 100 : 0;

        ChatFormatting color = percent < 50 ? ChatFormatting.GREEN :
                percent < 80 ? ChatFormatting.YELLOW :
                        percent < 100 ? ChatFormatting.GOLD : ChatFormatting.RED;

        return new MobCategoryStats(count, cap, percent, color);
    }

    private record MobCategoryStats(int count, int cap, double percent, ChatFormatting color) {
    }

    private record TickStats(double tps, double avg, double median, double min, double max, double percentile95) {
    }


}