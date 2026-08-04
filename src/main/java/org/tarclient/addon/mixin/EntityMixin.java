package org.tarclient.addon.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.tarclient.addon.events.ModifyRotationCameraPosEvent;
import org.tarclient.addon.utils.MioUtils;
import org.tarclient.addon.utils.StackUtils;

import java.util.Optional;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @ModifyReturnValue(method = "getCameraPosVec", at = @At("RETURN"))
    private Vec3d onGetCameraPosVec(Vec3d original, float tickProgress) {
        if ((Object) this instanceof PlayerEntity entity) {
            if (entity == mc.player) {
                // very hot event, there is 0 way to accurately see if mio is the caller
                // this WILL break some stuff, but it's a small risk due to the small use of the event
                if (MioUtils.mioCompatibility.expensiveRotationCaller.get()) {
                    Optional<StackWalker.StackFrame> caller = StackUtils.getNthCaller(3);
                    // no caller or caller not mio, return original
                    if (caller.isEmpty() || !caller.get().getClassName().startsWith("me.mioclient.")) return original;
                }

                // remove the eye pos in order to spoof it back again later on
                ((IVec3d) original).meteor$setY(original.getY() - entity.getStandingEyeHeight());
                MeteorClient.EVENT_BUS.post(ModifyRotationCameraPosEvent.get(original));
                // add it back, if no modification was made the original should be identical
                ((IVec3d) original).meteor$setY(original.getY() + entity.getStandingEyeHeight());
            }
        }
        return original;
    }
}
