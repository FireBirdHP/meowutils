package meow.utils;

import meow.utils.modules.*;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class Utils extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("MeowUtils");

    @Override
    public void onInitialize() {
        LOG.info("MeowUtils starting | UwU");

        // Modules
        Modules.get().add(new TPLoot());
        Modules.get().add(new LeaveFinder());
        Modules.get().add(new PathFly());
        Modules.get().add(new GrimSpeed());
        Modules.get().add(new TPLootLite());
        Modules.get().add(new ShulkerJump());
        Modules.get().add(new NoClip());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "meow.utils";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("MeteorDevelopment", "meteor-addon-template");
    }
}
