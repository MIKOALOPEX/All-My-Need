package com.mikoalopex.allmyneed;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AllMyNeedConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue REGISTER_TAB_AS_FIRST;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        REGISTER_TAB_AS_FIRST = builder
                .comment("Whether to place the All My Need creative tab before the first vanilla creative tab.")
                .translation("config.allmyneed.register_tab_as_first")
                .define("registerTabAsFirst", false);
        SPEC = builder.build();
    }

    private AllMyNeedConfig() {
    }

    public static boolean registerTabAsFirst() {
        return REGISTER_TAB_AS_FIRST.get();
    }
}
