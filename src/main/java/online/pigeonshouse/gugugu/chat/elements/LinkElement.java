package online.pigeonshouse.gugugu.chat.elements;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import online.pigeonshouse.gugugu.chat.MessageElement;
import online.pigeonshouse.gugugu.utils.MinecraftUtil;

public class LinkElement implements MessageElement {
    private final String url;

    public LinkElement(String url) {
        this.url = url;
    }

    @Override
    public Component toComponent() {
        return Component.literal(url)
                .withStyle(ChatFormatting.AQUA)
                .withStyle(style -> style
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                MinecraftUtil.translate("gugugu.chatEvent.components.link.open", url)
                        ))
                );
    }
}