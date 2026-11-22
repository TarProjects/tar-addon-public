package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.misc.BookBot;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(BookBot.class)
public class BookBotMixin {
    @Inject(method = "createBook", at = @At("HEAD"))
    private void tar$onCreateBook(ArrayList<String> pages, ArrayList<RawFilteredPair<Text>> filteredPages, CallbackInfo ci) {
        if (mc.interactionManager == null || mc.player == null || mc.world == null) return;
        // send right click with fixed sequence, bypasses some BookBot detection
        ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).tar$sendSequencedPacket(mc.world, (sequence -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, mc.player.getYaw(), mc.player.getPitch())));
    }
}
