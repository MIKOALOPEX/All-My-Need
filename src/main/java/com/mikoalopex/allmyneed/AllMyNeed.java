package com.mikoalopex.allmyneed;

import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(AllMyNeed.MOD_ID)
public class AllMyNeed {
    public static final String MOD_ID = "allmyneed";

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CUSTOM_NEEDS_TAB = CREATIVE_TABS.register(
            "custom_needs",
            () -> {
                CreativeModeTab.Builder builder = CreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.allmyneed.custom_needs"))
                        .icon(() -> new ItemStack(Items.CHEST))
                        .displayItems((parameters, output) -> output.accept(Items.BARRIER));
                return builder.build();
            }
    );

    public AllMyNeed(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, AllMyNeedConfig.SPEC);
        modEventBus.addListener(AllMyNeed::onCommonSetup);
        modEventBus.addListener(AllMyNeed::onConfigLoading);
        modEventBus.addListener(AllMyNeed::onConfigReloading);
        CREATIVE_TABS.register(modEventBus);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(AllMyNeed::applyConfiguredTabPosition);
    }

    private static void onConfigLoading(ModConfigEvent.Loading event) {
        if (isAllMyNeedConfig(event)) {
            applyConfiguredTabPosition();
        }
    }

    private static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (isAllMyNeedConfig(event)) {
            applyConfiguredTabPosition();
        }
    }

    private static boolean isAllMyNeedConfig(ModConfigEvent event) {
        return event.getConfig().getModId().equals(MOD_ID);
    }

    @SuppressWarnings("unchecked")
    public static void applyConfiguredTabPosition() {
        if (!AllMyNeedConfig.registerTabAsFirst() || !CUSTOM_NEEDS_TAB.isBound()) {
            return;
        }
        CreativeModeTab customTab = CUSTOM_NEEDS_TAB.get();
        if (BuiltInRegistries.CREATIVE_MODE_TAB.getKey(customTab) == null) {
            return;
        }
        try {
            Field sortedTabsField = CreativeModeTabRegistry.class.getDeclaredField("SORTED_TABS");
            sortedTabsField.setAccessible(true);
            List<CreativeModeTab> sortedTabs = (List<CreativeModeTab>) sortedTabsField.get(null);
            if (sortedTabs.remove(customTab)) {
                sortedTabs.add(0, customTab);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
