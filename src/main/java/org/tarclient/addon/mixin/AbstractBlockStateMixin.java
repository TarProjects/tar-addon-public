package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.block.AbstractBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.tarclient.addon.events.MioPauseSpeedmineEvent;
import org.tarclient.addon.utils.StackUtils;

import java.util.Optional;

@Mixin(AbstractBlock.AbstractBlockState.class)
public class AbstractBlockStateMixin {
    @Inject(method = "getHardness", at = @At("HEAD"), cancellable = true)
    private void onGetHardness(BlockView world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        // this is really stupid
        // mio calls getHardness on speedmine (almost) every tick with null worldview,
        // saves a ton of compute on stack
        if (world == null) {
            Optional<StackWalker.StackFrame> caller = StackUtils.getNthCaller(3);
            if (caller.isPresent() && caller.get().getClassName().startsWith("me.mioclient.")) {
                // we are in mioclient, post speedmine -> set hardness to 0
                if (MeteorClient.EVENT_BUS.post(MioPauseSpeedmineEvent.get()).isCancelled()) {
                    // 11h to mine, not gonna use -1 because it breaks swapping?
                    cir.setReturnValue(99999999999f);
                }
            }
        }
    }
}
