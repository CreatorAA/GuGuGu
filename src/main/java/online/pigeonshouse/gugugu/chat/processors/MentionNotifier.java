package online.pigeonshouse.gugugu.chat.processors;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import online.pigeonshouse.gugugu.chat.MessageContext;
import online.pigeonshouse.gugugu.chat.MessageProcessor;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

import java.util.Set;

public class MentionNotifier implements MessageProcessor {
    @Override
    public int getPriority() {
        return LOW;
    }

    @Override
    public void process(MessageContext context) {
        Set<ServerPlayer> mentioned = context.getMentionedPlayers();
        mentioned.remove(context.getSender());

        Component notification = Component.literal(context.getSender().getName().getString())
                .append(" ")
                .append(MinecraftUtil.translate("chat.gugugu.mention"));

        mentioned.forEach(player -> {
                    player.sendSystemMessage(notification, true);
                    player.playSound(SoundEvents.ANVIL_LAND, 1.0f, 1.0f);
                }
        );
    }

    @Override
    public String[] testProcessors() {
        return new String[]{"mention"};
    }
}