package org.tarclient.addon.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.block.AbstractBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.tarclient.addon.events.SpeedmineHardnessMultiplierEvent;
import org.tarclient.addon.utils.StackUtils;

import java.util.Optional;

@Mixin(AbstractBlock.AbstractBlockState.class)
public class AbstractBlockStateMixin {
    @ModifyReturnValue(method = "getHardness", at = @At("RETURN"))
    private float onGetHardness(float original, BlockView world, BlockPos pos) {
        // this is really stupid
        // mio calls getHardness on speedmine (almost) every tick with null worldview,
        // saves a ton of compute on stack
        if (world == null) {
            Optional<StackWalker.StackFrame> caller = StackUtils.getNthCaller(3);
            if (caller.isPresent() && caller.get().getClassName().startsWith("me.mioclient.")) {
                // we are in mioclient, post speedmine -> set hardness to 0
                SpeedmineHardnessMultiplierEvent event = MeteorClient.EVENT_BUS.post(SpeedmineHardnessMultiplierEvent.get());

                if (event.isCancelled()) {
                    // arbitary high number that works for now (pauses speedmine)
                    return 99999999999f;
                } else {
                    // multiply by multiplier
                    return original * event.multiplier;
                }
            }
        }
        return original;
    }
}
