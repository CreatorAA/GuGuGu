package online.pigeonshouse.gugugu.chat;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import online.pigeonshouse.gugugu.chat.processors.ComponentRenderer;
import online.pigeonshouse.gugugu.chat.processors.map.JourneyMapUtilProcessor;
import online.pigeonshouse.gugugu.chat.processors.MentionNotifier;
import online.pigeonshouse.gugugu.chat.processors.map.XaeroMapUtilProcessor;
import online.pigeonshouse.gugugu.chat.processors.parser.HandItemParser;
import online.pigeonshouse.gugugu.chat.processors.parser.LinkParser;
import online.pigeonshouse.gugugu.chat.processors.parser.MentionParser;
import online.pigeonshouse.gugugu.chat.processors.parser.TeleportRequestParser;
import online.pigeonshouse.gugugu.event.EventCallback;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

@Slf4j
public class ChatEventHandler implements EventCallback<MinecraftServerEvents.PlayerChatEvent> {
    @Getter
    private final MessagePipeline pipeline;

    public ChatEventHandler() {
        this.pipeline = new MessagePipeline()
                .registerFinalProcessor(new ComponentRenderer(), buildComponentRendererInfo())
                .registerProcessor(new MentionParser(), buildMentionParserInfo())
                .registerProcessor(new LinkParser(), buildLinkParserInfo())
                .registerProcessor(new MentionNotifier(), buildMentionNotifierInfo())
                .registerProcessor(new TeleportRequestParser(), buildTeleportRequestProcessorInfo())
                .registerProcessor(new XaeroMapUtilProcessor(), buildXaeroMapUtilProcessorInfo())
                .registerProcessor(new JourneyMapUtilProcessor(), buildJourneyMapUtilProcessorInfo())
                .registerProcessor(new HandItemParser(), buildHandItemParserInfo());
    }

    private static MessageProcessorInfo buildMentionParserInfo() {
        Component description = MinecraftUtil.translate("gugugu.processor.mention.description");

        return MessageProcessorInfo.of("mention", description)
                .addExample(new MessageProcessorInfo.Example("@all 兄弟们我挖到钻石辣！！！",
                        MinecraftUtil.translate("gugugu.processor.mention.example1")))
                .addExample(new MessageProcessorInfo.Example("@Steven 你昨天是不是偷偷拿了@Alice 的巧克力",
                        MinecraftUtil.translate("gugugu.processor.mention.example2")))
                .addExample(new MessageProcessorInfo.Example("@all 这俩人有问题：@Alice @Steven",
                        MinecraftUtil.translate("gugugu.processor.mention.example3")));
    }

    private static MessageProcessorInfo buildLinkParserInfo() {
        Component description = MinecraftUtil.translate("gugugu.processor.link.description");

        return MessageProcessorInfo.of("link", description)
                .addExample(new MessageProcessorInfo.Example("https://www.baidu.com",
                        MinecraftUtil.translate("gugugu.processor.link.example1")));
    }

    private static MessageProcessorInfo buildComponentRendererInfo() {
        Component description = MinecraftUtil.translate("gugugu.processor.component.description");

        return MessageProcessorInfo.of("component", description);
    }

    private static MessageProcessorInfo buildMentionNotifierInfo() {
        Component description = MinecraftUtil.translate("gugugu.processor.mention_notify.description");

        return MessageProcessorInfo.of("mention_notify", description)
                .addExample(new MessageProcessorInfo.Example("@all 兄弟们我挖到钻石辣！！！",
                        MinecraftUtil.translate("gugugu.processor.mention_notify.example1")));
    }

    private static MessageProcessorInfo buildTeleportRequestProcessorInfo() {
        Component description = MinecraftUtil.translate("gugugu.processor.teleport.description");

        return MessageProcessorInfo.of("teleport", description)
                .addExample(new MessageProcessorInfo.Example("Steven",
                        MinecraftUtil.translate("gugugu.processor.teleport.example1")));
    }

    private static MessageProcessorInfo buildHandItemParserInfo() {
        Component description = MinecraftUtil.translate("gugugu.processor.hand_item.description");

        return MessageProcessorInfo.of("hand_item", description)
                .addExample(new MessageProcessorInfo.Example("兄弟们看看我的装备[i][io]，我的护甲[it][ij][ik][ix]",
                        MinecraftUtil.translate("gugugu.processor.hand_item.example1")));
    }

    private static MessageProcessorInfo buildXaeroMapUtilProcessorInfo() {
        Component description = MinecraftUtil.translate("gugugu.processor.xaero_map_util.description");

        return MessageProcessorInfo.of("xaero_map_util", description)
                .addExample(new MessageProcessorInfo.Example("Steven",
                        MinecraftUtil.translate("gugugu.processor.xaero_map_util.example1")))
                .addExample(new MessageProcessorInfo.Example("[10, 60, 30]",
                        MinecraftUtil.translate("gugugu.processor.xaero_map_util.example2")));
    }

    private static MessageProcessorInfo buildJourneyMapUtilProcessorInfo() {
        Component description = MinecraftUtil.translate("gugugu.processor.journey_map_util.description");

        return MessageProcessorInfo.of("journey_map_util", description)
                .addExample(new MessageProcessorInfo.Example("Steven",
                        MinecraftUtil.translate("gugugu.processor.journey_map_util.example1")))
                .addExample(new MessageProcessorInfo.Example("[10, 60, 30]",
                        MinecraftUtil.translate("gugugu.processor.journey_map_util.example2")));
    }

    @Override
    public void onEvent(MinecraftServerEvents.PlayerChatEvent event) {
        Component newComponent = pipeline.processMessage(
                event.getPlayer(),
                event.getOriginalComponent().getString()
        );
        event.setComponent(newComponent);
    }
}