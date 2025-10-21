package online.pigeonshouse.gugugu.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.commands.WhitelistCommand;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.server.players.UserWhiteListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Collection;
import java.util.Iterator;

@Mixin(WhitelistCommand.class)
public class WhitelistCommandMixin {
    @Inject(method = "addPlayers", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/UserWhiteList;add(Lnet/minecraft/server/players/StoredUserEntry;)V"),
            locals = LocalCapture.CAPTURE_FAILSOFT
    )
    private static void addPlayer(CommandSourceStack source, Collection<GameProfile> players, CallbackInfoReturnable<Integer> cir, UserWhiteList userwhitelist, int i, Iterator var4, GameProfile gameprofile, UserWhiteListEntry userwhitelistentry) {
        MutableComponent message = Component.literal(gameprofile.getName())
                .append(": ")
                .append(Component.literal(gameprofile.getId().toString()))
                .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, gameprofile.getId().toString())));

        source.sendSystemMessage(message);
    }
}
