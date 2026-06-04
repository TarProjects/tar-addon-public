package org.tarclient.addon;

import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.PreInit;
import meteordevelopment.meteorclient.utils.misc.MeteorStarscript;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import org.meteordev.starscript.value.Value;
import org.meteordev.starscript.value.ValueMap;
import org.tarclient.addon.commands.*;
import org.tarclient.addon.modules.*;
import org.tarclient.addon.themes.TarSettingsTheme;
import org.tarclient.addon.utils.MioUtils;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class TarAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("Tar-Addon");

    private static final String BARITONE_MOD_ID = "baritone";

    @PreInit
    public static void preInit() {
        // Add more stuff?
        MeteorStarscript.ss.set("enemy", new ValueMap()
            // what the fuck intellij im literally checking that its not null and ur telling it throws nullpointerex?
            .set("_toString", () -> {
                PlayerEntity closest = closestPlayer();
                return Value.string(closest != null ? closest.getName().getString() : "None");
            })
            .set("health", () -> {
                PlayerEntity closest = closestPlayer();
                return Value.number(closest != null ? closest.getHealth() : 0);
            })
            .set("distance", () -> {
                PlayerEntity closest = closestPlayer();
                return Value.number(closest != null && mc.player != null ? mc.player.distanceTo(closest) : 0);
            })
        );
    }

    // Rewrite?
    private static PlayerEntity closestPlayer() {
        if (mc.player == null || mc.world == null) return null;

        double distance = Double.MAX_VALUE;
        PlayerEntity player = null;

        for (PlayerEntity e : mc.world.getPlayers()) {
            if (e == mc.player) continue;
            double d = mc.player.distanceTo(e);
            if (d < distance) {
                distance = d;
                player = e;
            }
        }

        return player;
    }

    // TODO: remove all Utils.canUpdate since events arent called if player is null anyways.... stupid me
    @Override
    public void onInitialize() {
        // Overwrites default theme, only for custom settings!
        GuiThemes.add(new TarSettingsTheme());

        Modules.get().add(new AntiPearl());
        Modules.get().add(new AutoCEV());
        Modules.get().add(new AutoDuelAccept());
        Modules.get().add(new AutoEZ());
        Modules.get().add(new AutoKit());
        Modules.get().add(new AutoSpectator());
        Modules.get().add(new BetterRichPresence());
        Modules.get().add(new BlinkESP());
        Modules.get().add(new BreakESP());
        Modules.get().add(new ChinaExploit());
        Modules.get().add(new ChorusESP());
        Modules.get().add(new CornerClip());
        Modules.get().add(new CrawlESP());
        Modules.get().add(new DropKit());
        Modules.get().add(new Flattener());
        Modules.get().add(new GridFiller());
        Modules.get().add(new HeadBurrow());
        Modules.get().add(new InfiniteNameTags());
        Modules.get().add(new InfiniteNameTagsTeleporter());
        Modules.get().add(new InventoryFixes());
        Modules.get().add(new ItemUse());
        Modules.get().add(new KitDeleter());
        Modules.get().add(new MioCompatibility());
        Modules.get().add(new ModifyESP());
        Modules.get().add(new NCPSpeed());
        Modules.get().add(new NoLerp());
        Modules.get().add(new PearlBoost());
        Modules.get().add(new PearlCancel());
        Modules.get().add(new PearlPhase());
        Modules.get().add(new PhaseFix());
        Modules.get().add(new PlaceObsidian());
        Modules.get().add(new RegearBot());
        Modules.get().add(new RespawnMessage());
        Modules.get().add(new SelfFill());
        Modules.get().add(new SpectatorCamera());
        Modules.get().add(new TestFly());
        Modules.get().add(new TickShift());
        Modules.get().add(new UUIDSpectator());
        Modules.get().add(new VirtualHotbar());

        // module requires/relies on baritone
        if (FabricLoader.getInstance().isModLoaded(BARITONE_MOD_ID)) {
            Modules.get().add(new AutoLobby());
        }

        Commands.add(new KitCommand());
        Commands.add(new ResetChangesCommand());
        Commands.add(new SetPoseCommand());
        Commands.add(new TPCommand());
        Commands.add(new UUIDCommand());

        MioUtils.mioCompatibility = Modules.get().get(MioCompatibility.class);
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
