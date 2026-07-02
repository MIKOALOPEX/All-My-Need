package com.mikoalopex.allmyneed.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mikoalopex.allmyneed.AllMyNeed;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

public final class CustomNeedsStorage {
    public static final int DEFAULT_PAGE_COUNT = 2;
    public static final int MAX_PAGE_NAME_LENGTH = 8;
    public static final int SLOTS_PER_PAGE = 45;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve(AllMyNeed.MOD_ID).resolve("client_items.json");
    private static final List<Page> PAGES = new ArrayList<>();
    private static boolean loaded;

    private CustomNeedsStorage() {
    }

    public static List<ItemStack> pageItems(int page) {
        ensureLoaded();
        List<ItemStack> result = new ArrayList<>(SLOTS_PER_PAGE);
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            result.add(page(page).items.get(i).copy());
        }
        return result;
    }

    public static ItemStack get(int page, int slot) {
        ensureLoaded();
        return page(page).items.get(slotIndex(slot)).copy();
    }

    public static void set(int page, int slot, ItemStack stack) {
        ensureLoaded();
        page(page).items.set(slotIndex(slot), stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount()));
    }

    public static int pageCount() {
        ensureLoaded();
        return PAGES.size();
    }

    public static String pageName(int page) {
        ensureLoaded();
        return page(page).name;
    }

    public static void setPageName(int page, String name) {
        ensureLoaded();
        page(page).name = sanitizePageName(name, page + 1);
    }

    public static int addPage() {
        ensureLoaded();
        PAGES.add(new Page(Integer.toString(PAGES.size() + 1)));
        return PAGES.size() - 1;
    }

    public static int deletePage(int page) {
        ensureLoaded();
        if (PAGES.size() <= 1) {
            return 0;
        }
        PAGES.remove(Math.max(0, Math.min(PAGES.size() - 1, page)));
        return Math.max(0, Math.min(PAGES.size() - 1, page));
    }

    public static int movePageUp(int page) {
        ensureLoaded();
        if (page <= 0 || page >= PAGES.size()) {
            return Math.max(0, Math.min(PAGES.size() - 1, page));
        }
        Page current = PAGES.remove(page);
        PAGES.add(page - 1, current);
        return page - 1;
    }

    public static void save() {
        ensureLoaded();
        HolderLookup.Provider registries = registries();
        if (registries == null) {
            return;
        }

        JsonObject root = new JsonObject();
        root.addProperty("version", 2);
        root.addProperty("slots_per_page", SLOTS_PER_PAGE);
        JsonArray pages = new JsonArray();
        for (Page page : PAGES) {
            JsonObject pageJson = new JsonObject();
            pageJson.addProperty("name", page.name);
            JsonArray items = new JsonArray();
            for (int i = 0; i < page.items.size(); i++) {
                ItemStack stack = page.items.get(i);
                if (!stack.isEmpty()) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("slot", i);
                    ItemStack.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), stack)
                            .result()
                            .ifPresent(encoded -> entry.add("stack", encoded));
                    if (entry.has("stack")) {
                        items.add(entry);
                    }
                }
            }
            pageJson.add("items", items);
            pages.add(pageJson);
        }
        root.add("pages", pages);

        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }

        PAGES.clear();
        addDefaultPages();
        loaded = true;

        HolderLookup.Provider registries = registries();
        if (registries == null || !Files.isRegularFile(FILE)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(FILE)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("pages")) {
                loadPages(root.getAsJsonArray("pages"), registries);
                return;
            }

            JsonArray items = root.getAsJsonArray("items");
            if (items == null) {
                return;
            }
            for (JsonElement element : items) {
                JsonObject entry = element.getAsJsonObject();
                int slot = entry.get("slot").getAsInt();
                if (slot < 0 || slot >= PAGES.size() * SLOTS_PER_PAGE || !entry.has("stack")) {
                    continue;
                }
                ItemStack.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), entry.get("stack"))
                        .result()
                        .ifPresent(stack -> page(slot / SLOTS_PER_PAGE).items.set(slot % SLOTS_PER_PAGE, stack));
            }
        } catch (RuntimeException | IOException ignored) {
        }
    }

    private static HolderLookup.Provider registries() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? null : minecraft.level.registryAccess();
    }

    private static void loadPages(JsonArray pages, HolderLookup.Provider registries) {
        PAGES.clear();
        for (int i = 0; i < pages.size(); i++) {
            JsonObject pageJson = pages.get(i).getAsJsonObject();
            Page page = new Page(sanitizePageName(pageJson.has("name") ? pageJson.get("name").getAsString() : "", i + 1));
            JsonArray items = pageJson.getAsJsonArray("items");
            if (items != null) {
                for (JsonElement element : items) {
                    JsonObject entry = element.getAsJsonObject();
                    int slot = entry.get("slot").getAsInt();
                    if (slot < 0 || slot >= SLOTS_PER_PAGE || !entry.has("stack")) {
                        continue;
                    }
                    ItemStack.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), entry.get("stack"))
                            .result()
                            .ifPresent(stack -> page.items.set(slot, stack));
                }
            }
            PAGES.add(page);
        }
        if (PAGES.isEmpty()) {
            addDefaultPages();
        }
    }

    private static void addDefaultPages() {
        for (int i = 0; i < DEFAULT_PAGE_COUNT; i++) {
            PAGES.add(new Page(Integer.toString(i + 1)));
        }
    }

    private static Page page(int page) {
        return PAGES.get(Math.max(0, Math.min(PAGES.size() - 1, page)));
    }

    private static int slotIndex(int slot) {
        return Math.max(0, Math.min(SLOTS_PER_PAGE - 1, slot));
    }

    private static String sanitizePageName(String name, int fallbackNumber) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            trimmed = Integer.toString(fallbackNumber);
        }
        return trimmed.codePoints()
                .limit(MAX_PAGE_NAME_LENGTH)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private static final class Page {
        private String name;
        private final List<ItemStack> items = new ArrayList<>(SLOTS_PER_PAGE);

        private Page(String name) {
            this.name = name;
            for (int i = 0; i < SLOTS_PER_PAGE; i++) {
                items.add(ItemStack.EMPTY);
            }
        }
    }
}
