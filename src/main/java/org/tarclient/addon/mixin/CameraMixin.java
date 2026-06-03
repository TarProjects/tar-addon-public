package org.tarclient.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.tarclient.addon.modules.SpectatorCamera;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPos(Vec3d pos);

    @Inject(method = "update", at = @At("RETURN"))
    private void onUpdate(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        SpectatorCamera spectatorCam = Modules.get().get(SpectatorCamera.class);
        if (spectatorCam != null && spectatorCam.isActive() && mc.player != null) {
            Camera camera = (Camera) (Object) this;
            double y = mc.player.getLerpedPos(tickDelta).getY() + mc.player.getEyeHeight(mc.player.getPose()) + spectatorCam.getOffset(tickDelta);
            setPos(new Vec3d(camera.getCameraPos().x, y, camera.getCameraPos().z));
        }
    }

}
