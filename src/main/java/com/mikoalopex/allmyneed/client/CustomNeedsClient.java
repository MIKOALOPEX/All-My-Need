package com.mikoalopex.allmyneed.client;

import com.mikoalopex.allmyneed.AllMyNeed;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = AllMyNeed.MOD_ID, value = Dist.CLIENT)
public final class CustomNeedsClient {
    private static final int PAGE_BUTTON_WIDTH = 24;
    private static final int VISIBLE_PAGE_BUTTONS = 7;
    private static final int PAGE_BUTTON_HEIGHT = 16;
    private static final int PAGE_BUTTON_GAP = 2;
    private static final int PAGE_LIST_HEIGHT = VISIBLE_PAGE_BUTTONS * (PAGE_BUTTON_HEIGHT + PAGE_BUTTON_GAP) - PAGE_BUTTON_GAP;
    private static final int PAGE_SCROLLBAR_WIDTH = 8;
    private static final int PAGE_PANEL_LEFT_OFFSET = 40;
    private static final int PAGE_CONTROL_WIDTH = PAGE_BUTTON_WIDTH + 4 + PAGE_SCROLLBAR_WIDTH;
    private static int page;
    private static int pageScrollOffset;
    private static boolean draggingPageScrollbar;
    private static boolean suppressNextEditRelease;
    private static boolean editMode;
    private static CreativeModeInventoryScreen activeScreen;
    private static Button saveButton;
    private static Button editButton;
    private static Button deletePageButton;
    private static Button movePageUpButton;
    private static EditBox pageNameBox;
    private static Button[] pageButtons = new Button[0];
    private static boolean updatingPageNameBox;
    private static boolean wasOurTabSelected;

    private CustomNeedsClient() {
    }

    @SubscribeEvent
    public static void onScreenInitPre(ScreenEvent.Init.Pre event) {
        if (event.getScreen() instanceof CreativeModeInventoryScreen) {
            AllMyNeed.applyConfiguredTabPosition();
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) {
            return;
        }

        activeScreen = screen;
        page = 0;
        pageScrollOffset = 0;
        draggingPageScrollbar = false;
        suppressNextEditRelease = false;
        wasOurTabSelected = false;
        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        pageButtons = new Button[VISIBLE_PAGE_BUTTONS];
        for (int i = 0; i < pageButtons.length; i++) {
            final int buttonIndex = i;
            Button button = Button.builder(Component.literal(Integer.toString(i + 1)), ignored -> {
                        int entryIndex = pageScrollOffset + buttonIndex;
                        if (entryIndex == CustomNeedsStorage.pageCount()) {
                            CustomNeedsStorage.save();
                            page = CustomNeedsStorage.addPage();
                            CustomNeedsStorage.save();
                        } else if (entryIndex < CustomNeedsStorage.pageCount()) {
                            switchToPage(screen, entryIndex);
                        }
                        ensureSelectedPageVisible();
                        syncPageNameBox();
                        syncCreativeItems(screen);
                        updateButtons();
                    })
                    .bounds(pageButtonX(left), pageButtonY(top, i), PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable("gui.allmyneed.page", i + 1)))
                    .build();
            pageButtons[i] = button;
            event.addListener(button);
        }

        pageNameBox = new EditBox(Minecraft.getInstance().font, sideControlX(left), top + 19, sideControlWidth(), 18, Component.translatable("gui.allmyneed.page_name"));
        pageNameBox.setMaxLength(24);
        pageNameBox.setFilter(value -> value.codePointCount(0, value.length()) <= CustomNeedsStorage.MAX_PAGE_NAME_LENGTH);
        pageNameBox.setResponder(value -> {
            if (!updatingPageNameBox) {
                CustomNeedsStorage.setPageName(page, value);
                updateButtons();
            }
        });
        event.addListener(pageNameBox);

        deletePageButton = Button.builder(Component.translatable("gui.allmyneed.delete_page.short"), ignored -> {
                    CustomNeedsStorage.save();
                    page = CustomNeedsStorage.deletePage(page);
                    setPageScrollOffset(Math.min(pageScrollOffset, Math.max(0, page)));
                    ensureSelectedPageVisible();
                    syncPageNameBox();
                    syncCreativeItems(screen);
                    CustomNeedsStorage.save();
                    updateButtons();
                })
                .bounds(sideControlX(left), top + 43, sideControlWidth(), 18)
                .tooltip(Tooltip.create(Component.translatable("gui.allmyneed.delete_page.tooltip")))
                .build();
        movePageUpButton = Button.builder(Component.translatable("gui.allmyneed.move_page_up.short"), ignored -> {
                    CustomNeedsStorage.save();
                    page = CustomNeedsStorage.movePageUp(page);
                    ensureSelectedPageVisible();
                    syncPageNameBox();
                    syncCreativeItems(screen);
                    CustomNeedsStorage.save();
                    updateButtons();
                })
                .bounds(sideControlX(left), top + 63, sideControlWidth(), 18)
                .tooltip(Tooltip.create(Component.translatable("gui.allmyneed.move_page_up.tooltip")))
                .build();
        event.addListener(deletePageButton);
        event.addListener(movePageUpButton);

        saveButton = Button.builder(Component.translatable("gui.allmyneed.save"), ignored -> {
                    CustomNeedsStorage.save();
                    editMode = false;
                    updateButtons();
                })
                .bounds(sideControlX(left), top + 91, sideControlWidth(), 18)
                .tooltip(Tooltip.create(Component.translatable("gui.allmyneed.save.tooltip")))
                .build();
        editButton = Button.builder(Component.translatable("gui.allmyneed.edit"), ignored -> {
                    editMode = !editMode;
                    syncPageNameBox();
                    updateButtons();
                })
                .bounds(sideControlX(left), top + 111, sideControlWidth(), 18)
                .tooltip(Tooltip.create(Component.translatable("gui.allmyneed.edit.tooltip")))
                .build();
        event.addListener(saveButton);
        event.addListener(editButton);
        updateButtons();
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) {
            return;
        }

        activeScreen = screen;
        boolean ourTabSelected = isOurTabSelected();
        if (wasOurTabSelected && !ourTabSelected) {
            CustomNeedsStorage.save();
            editMode = false;
            draggingPageScrollbar = false;
            hideCustomWidgets();
        }
        wasOurTabSelected = ourTabSelected;
        updateButtons();
        if (!ourTabSelected) {
            return;
        }

        syncCreativeItems(screen);
        renderPageScrollbar(event.getGuiGraphics(), screen);
        renderEditOverlay(event.getGuiGraphics(), screen);
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen) || !isOurTabSelected()) {
            return;
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && isOverPageScrollbar(screen, event.getMouseX(), event.getMouseY())) {
            draggingPageScrollbar = true;
            updatePageScrollFromMouse(screen, event.getMouseY());
            event.setCanceled(true);
            return;
        }

        if (!editMode) {
            return;
        }

        Slot slot = screen.getSlotUnderMouse();
        if (!isCustomDisplaySlot(slot)) {
            return;
        }

        int button = event.getButton();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return;
        }

        ItemStack carried = screen.getMenu().getCarried();
        ItemStack originalCarried = carried.copy();
        int slotIndex = slot.getSlotIndex();
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || carried.isEmpty()) {
            CustomNeedsStorage.set(page, slotIndex, ItemStack.EMPTY);
        } else {
            CustomNeedsStorage.set(page, slotIndex, carried.copy());
        }
        syncCreativeItems(screen);
        screen.getMenu().setCarried(originalCarried);
        suppressNextEditRelease = true;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen) || !draggingPageScrollbar || !isOurTabSelected()) {
            return;
        }
        updatePageScrollFromMouse(screen, event.getMouseY());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (draggingPageScrollbar && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggingPageScrollbar = false;
            event.setCanceled(true);
            return;
        }
        if (suppressNextEditRelease && event.getScreen() instanceof CreativeModeInventoryScreen && isOurTabSelected()) {
            suppressNextEditRelease = false;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen) || !isOurTabSelected() || !editMode) {
            return;
        }
        if (pageNameBox != null && pageNameBox.isVisible() && pageNameBox.isFocused()) {
            pageNameBox.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers());
            event.setCanceled(true);
            return;
        }
        if (event.getKeyCode() == GLFW.GLFW_KEY_E || Minecraft.getInstance().options.keyInventory.matches(event.getKeyCode(), event.getScanCode())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCharacterTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen) || !isOurTabSelected() || !editMode) {
            return;
        }
        if (pageNameBox != null && pageNameBox.isVisible() && pageNameBox.isFocused()) {
            pageNameBox.charTyped(event.getCodePoint(), event.getModifiers());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen) || !isOurTabSelected()) {
            return;
        }
        if (!isOverPagePanel(screen, event.getMouseX(), event.getMouseY())) {
            return;
        }
        int direction = event.getScrollDeltaY() < 0.0D ? 1 : -1;
        setPageScrollOffset(pageScrollOffset + direction);
        updateButtons();
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof CreativeModeInventoryScreen && wasOurTabSelected) {
            CustomNeedsStorage.save();
            wasOurTabSelected = false;
        }
    }

    private static void syncCreativeItems(CreativeModeInventoryScreen screen) {
        if (!isOurTabSelected()) {
            return;
        }
        screen.getMenu().items.clear();
        screen.getMenu().items.addAll(CustomNeedsStorage.pageItems(page));
        screen.getMenu().scrollTo(0.0F);
    }

    private static void renderPageScrollbar(GuiGraphics graphics, CreativeModeInventoryScreen screen) {
        if (maxPageScrollOffset() <= 0) {
            return;
        }

        int x = pageScrollbarX(screen.getGuiLeft());
        int y = pagePanelY(screen.getGuiTop());
        graphics.fill(x, y, x + PAGE_SCROLLBAR_WIDTH, y + PAGE_LIST_HEIGHT, 0xAA202020);
        graphics.fill(x + 1, y + 1, x + PAGE_SCROLLBAR_WIDTH - 1, y + PAGE_LIST_HEIGHT - 1, 0xFF7A7A7A);

        int thumbHeight = Math.max(14, PAGE_LIST_HEIGHT * VISIBLE_PAGE_BUTTONS / virtualPageEntryCount());
        int travel = PAGE_LIST_HEIGHT - thumbHeight;
        int thumbY = y + (maxPageScrollOffset() == 0 ? 0 : pageScrollOffset * travel / maxPageScrollOffset());
        graphics.fill(x + 1, thumbY + 1, x + PAGE_SCROLLBAR_WIDTH - 1, thumbY + thumbHeight - 1, 0xFFE6E6E6);
    }

    private static void renderEditOverlay(GuiGraphics graphics, CreativeModeInventoryScreen screen) {
        if (!editMode) {
            return;
        }
        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        graphics.fill(left + 7, top + 16, left + 172, top + 109, 0x3300AAFF);
    }

    private static void switchToPage(CreativeModeInventoryScreen screen, int targetPage) {
        if (targetPage == page) {
            return;
        }
        CustomNeedsStorage.save();
        page = targetPage;
        syncPageNameBox();
        syncCreativeItems(screen);
        CustomNeedsStorage.save();
    }

    private static void updateButtons() {
        boolean visible = activeScreen != null && isOurTabSelected();
        if (page >= CustomNeedsStorage.pageCount()) {
            page = Math.max(0, CustomNeedsStorage.pageCount() - 1);
        }
        setPageScrollOffset(pageScrollOffset);
        for (int i = 0; i < pageButtons.length; i++) {
            Button button = pageButtons[i];
            if (button != null) {
                int entryIndex = pageScrollOffset + i;
                boolean plus = entryIndex == CustomNeedsStorage.pageCount();
                button.visible = visible && entryIndex < virtualPageEntryCount();
                button.active = button.visible && (plus || entryIndex != page);
                button.setMessage(Component.literal(plus ? "+" : CustomNeedsStorage.pageName(entryIndex)));
                button.setTooltip(Tooltip.create(plus
                        ? Component.translatable("gui.allmyneed.add_page")
                        : Component.translatable("gui.allmyneed.page_named", CustomNeedsStorage.pageName(entryIndex))));
            }
        }
        if (pageNameBox != null) {
            pageNameBox.setVisible(visible && editMode);
            pageNameBox.active = visible && editMode;
            if (visible && editMode && !pageNameBox.isFocused()) {
                syncPageNameBox();
            }
        }
        if (deletePageButton != null) {
            deletePageButton.visible = visible && editMode;
            deletePageButton.active = deletePageButton.visible && CustomNeedsStorage.pageCount() > 1;
        }
        if (movePageUpButton != null) {
            movePageUpButton.visible = visible && editMode;
            movePageUpButton.active = movePageUpButton.visible && page > 0;
        }
        if (saveButton != null) {
            saveButton.visible = visible;
            saveButton.active = visible && editMode;
        }
        if (editButton != null) {
            editButton.visible = visible;
            editButton.setMessage(Component.translatable(editMode ? "gui.allmyneed.done" : "gui.allmyneed.edit"));
        }
    }

    private static void hideCustomWidgets() {
        for (Button button : pageButtons) {
            if (button != null) {
                button.visible = false;
            }
        }
        if (pageNameBox != null) {
            pageNameBox.setVisible(false);
            pageNameBox.setFocused(false);
        }
        if (deletePageButton != null) {
            deletePageButton.visible = false;
        }
        if (movePageUpButton != null) {
            movePageUpButton.visible = false;
        }
        if (saveButton != null) {
            saveButton.visible = false;
        }
        if (editButton != null) {
            editButton.visible = false;
        }
    }

    private static boolean isCustomDisplaySlot(Slot slot) {
        return slot != null && slot.index >= 0 && slot.index < CustomNeedsStorage.SLOTS_PER_PAGE && slot.getSlotIndex() == slot.index;
    }

    private static void ensureSelectedPageVisible() {
        if (page < pageScrollOffset) {
            setPageScrollOffset(page);
        } else if (page >= pageScrollOffset + VISIBLE_PAGE_BUTTONS - 1) {
            setPageScrollOffset(page - VISIBLE_PAGE_BUTTONS + 2);
        } else {
            setPageScrollOffset(pageScrollOffset);
        }
    }

    private static void updatePageScrollFromMouse(CreativeModeInventoryScreen screen, double mouseY) {
        int y = pagePanelY(screen.getGuiTop());
        int thumbHeight = Math.max(14, PAGE_LIST_HEIGHT * VISIBLE_PAGE_BUTTONS / virtualPageEntryCount());
        int travel = Math.max(1, PAGE_LIST_HEIGHT - thumbHeight);
        double local = Math.max(0.0D, Math.min(travel, mouseY - y - thumbHeight / 2.0D));
        setPageScrollOffset((int)Math.round(local / travel * maxPageScrollOffset()));
        updateButtons();
    }

    private static void setPageScrollOffset(int offset) {
        pageScrollOffset = Math.max(0, Math.min(maxPageScrollOffset(), offset));
    }

    private static boolean isOverPageScrollbar(CreativeModeInventoryScreen screen, double mouseX, double mouseY) {
        int x = pageScrollbarX(screen.getGuiLeft());
        int y = pagePanelY(screen.getGuiTop());
        return mouseX >= x - 10 && mouseX < x + PAGE_SCROLLBAR_WIDTH + 10 && mouseY >= y - 4 && mouseY < y + PAGE_LIST_HEIGHT + 4;
    }

    private static boolean isOverPagePanel(CreativeModeInventoryScreen screen, double mouseX, double mouseY) {
        int x = pageButtonX(screen.getGuiLeft());
        int y = pagePanelY(screen.getGuiTop());
        return mouseX >= x && mouseX < x + PAGE_BUTTON_WIDTH + PAGE_SCROLLBAR_WIDTH + 8 && mouseY >= y && mouseY < y + PAGE_LIST_HEIGHT;
    }

    private static int maxPageScrollOffset() {
        return Math.max(0, virtualPageEntryCount() - VISIBLE_PAGE_BUTTONS);
    }

    private static int virtualPageEntryCount() {
        return CustomNeedsStorage.pageCount() + 1;
    }

    private static void syncPageNameBox() {
        if (pageNameBox == null) {
            return;
        }
        String name = CustomNeedsStorage.pageName(page);
        if (!name.equals(pageNameBox.getValue())) {
            updatingPageNameBox = true;
            pageNameBox.setValue(name);
            updatingPageNameBox = false;
        }
    }

    private static int pageButtonX(int left) {
        return left - PAGE_PANEL_LEFT_OFFSET;
    }

    private static int pageButtonY(int top, int visibleIndex) {
        return pagePanelY(top) + visibleIndex * (PAGE_BUTTON_HEIGHT + PAGE_BUTTON_GAP);
    }

    private static int pageScrollbarX(int left) {
        return pageButtonX(left) + PAGE_BUTTON_WIDTH + 4;
    }

    private static int pagePanelY(int top) {
        return top + 10;
    }

    private static int sideControlX(int left) {
        return left - 135;
    }

    private static int sideControlWidth() {
        return 88;
    }

    private static boolean isOurTabSelected() {
        CreativeModeTab selected = selectedTab();
        return selected != null && selected == AllMyNeed.CUSTOM_NEEDS_TAB.get();
    }

    private static CreativeModeTab selectedTab() {
        try {
            Field field = CreativeModeInventoryScreen.class.getDeclaredField("selectedTab");
            field.setAccessible(true);
            return (CreativeModeTab) field.get(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
