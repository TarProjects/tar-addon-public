package org.tarclient.addon.modules;

import it.unimi.dsi.fastutil.shorts.Short2LongRBTreeMap;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.tarclient.addon.TarAddon;
import org.tarclient.addon.TarModule;


public class TestFly extends TarModule {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> base = sgGeneral.add(new DoubleSetting.Builder()
        .name("base")
        .description("base")
        .defaultValue(-1)
        .build()
    );

    private final Setting<Double> exp = sgGeneral.add(new DoubleSetting.Builder()
        .name("exp")
        .description("exp b10")
        .defaultValue(1)
        .build()
    );

    private final Setting<Double> offset = sgGeneral.add(new DoubleSetting.Builder()
        .name("offset")
        .description("offset")
        .defaultValue(0)
        .build()
    );


    public TestFly() {
        super(TarAddon.CATEGORY, "test", "");
    }

    int counter = 0;
    int stage = 0;

    @Override
    public void onActivate() {
        counter = 0;
        stage = 0;
    }

    @EventHandler
    private void onTickPre(final TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;

        if (mc.player.age % 2 == 0) return;

        if (stage == 0) {
            if (counter >= 36) {
                stage++;
                counter = 0;
                return;
            }

            InvUtils.drop().slot(SlotUtils.HOTBAR_START + counter);
            counter++;
        }
        if (stage == 1) {
            if (counter >= 4) {
                stage++;
                counter = 0;
                return;
            }
            InvUtils.drop().slot(SlotUtils.ARMOR_START + counter);
            counter++;
        }

        if (stage == 2) {
            InvUtils.drop().slot(SlotUtils.OFFHAND);
            this.toggle();
        }
    }

}
