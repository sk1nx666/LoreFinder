package com.lorefinder;

import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class LoreFinderAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("LoreFinder++");

    @Override
    public void onInitialize() {
        LOG.info("Initializing LoreFinder++");
        Modules.get().add(new SignFinder());
        Modules.get().add(new NamedEntityFinder());
        Modules.get().add(new AncientBuildsFinder());
        Modules.get().add(new IllegalsFinder());
        Modules.get().add(new LowMapIDFinder());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.lorefinder";
    }
}
