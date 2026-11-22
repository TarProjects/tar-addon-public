package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.tarclient.addon.modules.FakeFly;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("RETURN"))
    private void disableGlidingRendering(PlayerLikeEntity playerLikeEntity, PlayerEntityRenderState renderState, float tickDelta, CallbackInfo ci) {
        FakeFly fakeFly = Modules.get().get(FakeFly.class);
        if (fakeFly != null && fakeFly.isActive()) {
            // fakefly disable gliding
            renderState.isGliding = false;
            renderState.glidingTicks = 0.0F;
            renderState.applyFlyingRotation = false;
            renderState.flyingRotation = 0.0F;
        }

    }
}
