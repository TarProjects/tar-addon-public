package org.tarclient.addon.modules;

import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;

public class CornerClip extends TarModule {
    public CornerClip() {
        super(TarAddon.CATEGORY, "corner-clip", "Allows you to go slightly inside a block to prevent crystals from being placed");
    }

    @Override
    public void onActivate() {
        if (!Utils.canUpdate()) {
            toggle();
            return;
        }
        Vec3d vec3d = mc.player.getBlockPos().toCenterPos();

        boolean flagX = (vec3d.x - mc.player.getX()) > 0;
        boolean flagZ = (vec3d.z - mc.player.getZ()) > 0;

        double x = vec3d.x + 0.20000000009497754 * (flagX ? -1 : 1);
        double z = vec3d.z + 0.2000000000949811 * (flagZ ? -1 : 1);

        mc.player.setVelocity(0, mc.player.getVelocity().getY(), 0);
        mc.player.setPosition(x, mc.player.getY(), z);
        // Update since otherwise position will only be updated after player turns or moves/20 ticks with no movement...
        sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.isOnGround(), mc.player.horizontalCollision));
        toggle();
    }
}
