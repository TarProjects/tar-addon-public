package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.tarclient.addon.modules.NoLerp;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(EntityRenderManager.class)
public class EntityRenderManagerMixin {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(
        method = "getAndUpdateRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;getAndUpdateRenderState(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/client/render/entity/state/EntityRenderState;")
    )
    private EntityRenderState modifyTickProgress(EntityRenderer instance, Entity entity, float tickProgress) {
        NoLerp noLerp = Modules.get().get(NoLerp.class);
        if (noLerp == null || !noLerp.isActive()) return instance.getAndUpdateRenderState(entity, tickProgress);
        if (entity == mc.player && !noLerp.self.get()) return instance.getAndUpdateRenderState(entity, tickProgress);
        if (noLerp.checkDistanceMoved.get() && entity.getEntityPos().squaredDistanceTo(entity.lastX, entity.lastY, entity.lastZ) < noLerp.distanceToMove.get() * noLerp.distanceToMove.get()) return instance.getAndUpdateRenderState(entity, tickProgress);

        tickProgress = switch (noLerp.lerpMode.get()) {
            case Normal -> tickProgress;
            case Fast -> (float) Math.pow(tickProgress, 0.2);
            case Instant -> 1.0f;
        };

        return instance.getAndUpdateRenderState(entity, tickProgress);
    }
}
