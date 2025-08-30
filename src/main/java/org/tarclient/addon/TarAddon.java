package org.tarclient.addon;

import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.PreInit;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.MeteorStarscript;
import meteordevelopment.starscript.value.Value;
import meteordevelopment.starscript.value.ValueMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.tarclient.addon.modules.*;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class TarAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("Tar-Addon");

    @PreInit
    public static void preInit() {
        // Add more stuff?
        MeteorStarscript.ss.set("enemy", new ValueMap()
            // what the fuck intellij im literally checking that its not null and ur telling it throws nullpointerex?
            .set("_toString", () -> Value.string(closestPlayer() != null ? closestPlayer().getName().getString() : "None"))
            .set("health", () -> Value.number(closestPlayer() != null ? closestPlayer().getHealth() : 0))
        );
    }

    // Rewrite?
    private static PlayerEntity closestPlayer() {
        if (Utils.canUpdate()) {
            double distance = Double.MAX_VALUE;
            PlayerEntity player = null;
            for (Entity e : mc.world.getEntities()) {
                if (e == mc.player) continue;
                if (e instanceof PlayerEntity entity) {
                    double d = mc.player.distanceTo(entity);
                    if (d < distance) {
                        distance = d;
                        player = entity;
                    }
                }
            }
            return player;
        }
        return null;
    }

    // TODO: remove all Utils.canUpdate since events arent called if player is null anyways.... stupid me
    @Override
    public void onInitialize() {
        Modules.get().add(new AutoEZ());
        Modules.get().add(new AutoKit());
        Modules.get().add(new AutoSpectator());
        Modules.get().add(new BetterRichPresence());
        Modules.get().add(new PearlPhase());
        Modules.get().add(new SelfFill());
        Modules.get().add(new PhaseFix());
        Modules.get().add(new CornerClip());
        Modules.get().add(new ChorusESP());
        Modules.get().add(new ChinaExploit());

    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "org.tarclient.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("TarClient", "tar-addon-public");
    }
}
