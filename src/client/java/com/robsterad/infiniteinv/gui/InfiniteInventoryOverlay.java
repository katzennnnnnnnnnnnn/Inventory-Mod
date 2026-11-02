package com.robsterad.infiniteinv.gui;

import com.robsterad.infiniteinv.network.ExtractItemPayload;
import com.robsterad.infiniteinv.network.SyncInventoryPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InfiniteInventoryOverlay {

    private static EditBox searchBox;
    private static Button sortButton, prevPageBtn, nextPageBtn, tooltipBtn, collapseBtn;
    private static Button widthMinusBtn, widthPlusBtn, allBtn;
    public static List<SyncInventoryPayload.NetworkItemData> cachedItems = new ArrayList<>();

    public static boolean panelVisible = true;

    private enum SortMode {
        RECENT("N",    "Newest"),
        DESCENDING("▼", "Count: High→Low"),
        ASCENDING("▲",  "Count: Low→High"),
        ALPHA_ASC("A↑", "Name: A→Z"),
        ALPHA_DESC("A↓", "Name: Z→A");

        final String shortLabel;
        final String hoverName;

        SortMode(String shortLabel, String hoverName) {
            this.shortLabel = shortLabel;
            this.hoverName  = hoverName;
        }
    }

    private static SortMode currentSort = SortMode.RECENT;
    private static int currentPage  = 0;
    private static int itemsPerPage = 1;
    private static boolean showTooltips = false;

    /** true = a click on an item takes ALL of it (as much as fits), false = one stack. */
    private static boolean extractAllMode = false;

    private static final int COLLAPSE_W = 20;

    // ---- adjustable panel width (percent of the original width), saved in the config folder ----
    private static final int MIN_WIDTH_PERCENT  = 30;
    private static final int WIDTH_PERCENT_STEP = 10;
    private static final int MIN_PANEL_PX       = 120;
    private static final String CONFIG_FILE     = "infinite-inventory-panel.txt";
    private static int widthPercent = loadWidthPercent();

    // ---- layout of the currently open inventory screen ----
    private static Minecraft mc;
    private static int sw, sh;       // scaled screen size
    private static int startX;       // left edge of the panel
    private static int panelW;       // current panel width

    private static int loadWidthPercent() {
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
            if (Files.exists(p)) {
                int v = Integer.parseInt(Files.readString(p).trim());
                return Math.max(MIN_WIDTH_PERCENT, Math.min(100, v));
            }
        } catch (Exception ignored) {
        }
        return 100;
    }

    private static void saveWidthPercent() {
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
            Files.writeString(p, String.valueOf(widthPercent));
        } catch (Exception ignored) {
        }
    }

    private static void changeWidth(int deltaPercent) {
        int newValue = Math.max(MIN_WIDTH_PERCENT, Math.min(100, widthPercent + deltaPercent));
        if (newValue == widthPercent) return;
        widthPercent = newValue;
        saveWidthPercent();
        computeLayout();
        buildWidgets();
    }

    /** The panel stays glued to the right screen edge; only its left edge moves. */
    private static void computeLayout() {
        int fullStartX = (sw / 2) + 93;
        int fullWidth  = sw - fullStartX - 5;
        panelW = Math.max(Math.min(fullWidth, MIN_PANEL_PX), fullWidth * widthPercent / 100);
        startX = sw - 5 - panelW;
    }

    /** (Re)creates all widgets for the current startX / panelW. */
    private static void buildWidgets() {
        String oldSearch = searchBox != null ? searchBox.getValue() : "";
        boolean wasFocused = searchBox != null && searchBox.isFocused();

        int bottomY   = sh - 25;
        int controlY  = bottomY - 16;
        int sortW = 20;
        int toolW = 36;

        int searchW = panelW - sortW - toolW - COLLAPSE_W - 6;

        searchBox = new EditBox(mc.font, startX, bottomY, searchW, 14, Component.literal(""));
        searchBox.setHint(Component.literal("Search..."));
        searchBox.setValue(oldSearch);
        searchBox.setFocused(wasFocused);

        sortButton  = Button.builder(Component.literal(currentSort.shortLabel), b -> {})
                .pos(startX + searchW + 2, bottomY).size(sortW, 14).build();
        tooltipBtn  = Button.builder(Component.literal(showTooltips ? "T:ON" : "T:OFF"), b -> {})
                .pos(startX + searchW + sortW + 4, bottomY).size(toolW, 14).build();
        collapseBtn = Button.builder(Component.literal(panelVisible ? "◀" : "▶"), b -> {})
                .pos(startX + searchW + sortW + toolW + 6, bottomY).size(COLLAPSE_W, 14).build();

        prevPageBtn = Button.builder(Component.literal("<"), b -> {})
                .pos(startX, 10).size(20, 14).build();
        nextPageBtn = Button.builder(Component.literal(">"), b -> {})
                .pos(startX + panelW - 20, 10).size(20, 14).build();

        // control row above the search row: [ALL] [-] [+], aligned to the right edge
        // so the buttons stay where they are while the width changes
        int plusX  = startX + panelW - 14;
        int minusX = plusX - 16;
        int allX   = minusX - 48;
        widthMinusBtn = Button.builder(Component.literal("-"), b -> {})
                .pos(minusX, controlY).size(14, 14).build();
        widthPlusBtn  = Button.builder(Component.literal("+"), b -> {})
                .pos(plusX, controlY).size(14, 14).build();
        allBtn        = Button.builder(Component.literal(extractAllMode ? "ALL:ON" : "ALL:OFF"), b -> {})
                .pos(allX, controlY).size(46, 14).build();
    }

    private static int visibleRows() {
        return Math.max(1, (sh - 72) / 18);
    }

    public static boolean isSearchFocused() {
        return searchBox != null && searchBox.isFocused();
    }

    public static boolean onCharTyped(CharacterEvent event) {
        if (searchBox != null && searchBox.isFocused()) {
            searchBox.charTyped(event);
            currentPage = 0;
            return true;
        }
        return false;
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof InventoryScreen)) return;

            int fullWidth = scaledWidth - ((scaledWidth / 2) + 93) - 5;
            if (fullWidth < 50) return;

            mc = client;
            sw = scaledWidth;
            sh = scaledHeight;
            computeLayout();
            buildWidgets();

            // MOUSE CLICK
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                double mx = event.x();
                double my = event.y();
                int btn = event.button();
                if (btn != 0) return true;

                if (collapseBtn.isMouseOver(mx, my)) {
                    panelVisible = !panelVisible;
                    collapseBtn.setMessage(Component.literal(panelVisible ? "◀" : "▶"));
                    return false;
                }

                if (!panelVisible) return true;

                if (searchBox.isMouseOver(mx, my)) {
                    searchBox.setFocused(true);
                    searchBox.mouseClicked(event, false);
                    return false;
                }
                searchBox.setFocused(false);

                if (sortButton.isMouseOver(mx, my)) {
                    currentSort = SortMode.values()[(currentSort.ordinal() + 1) % SortMode.values().length];
                    sortButton.setMessage(Component.literal(currentSort.shortLabel));
                    currentPage = 0;
                    return false;
                }

                if (tooltipBtn.isMouseOver(mx, my)) {
                    showTooltips = !showTooltips;
                    tooltipBtn.setMessage(Component.literal(showTooltips ? "T:ON" : "T:OFF"));
                    return false;
                }

                if (widthMinusBtn.isMouseOver(mx, my)) {
                    changeWidth(-WIDTH_PERCENT_STEP);
                    return false;
                }
                if (widthPlusBtn.isMouseOver(mx, my)) {
                    changeWidth(WIDTH_PERCENT_STEP);
                    return false;
                }

                if (allBtn.isMouseOver(mx, my)) {
                    extractAllMode = !extractAllMode;
                    allBtn.setMessage(Component.literal(extractAllMode ? "ALL:ON" : "ALL:OFF"));
                    return false;
                }

                if (prevPageBtn.isMouseOver(mx, my)) {
                    if (currentPage > 0) currentPage--;
                    return false;
                }
                if (nextPageBtn.isMouseOver(mx, my)) {
                    currentPage++;
                    return false;
                }

                List<SyncInventoryPayload.NetworkItemData> list = getProcessedItems();
                int columns = Math.max(1, (panelW - 8) / 18);
                for (int i = 0; i < list.size(); i++) {
                    int x = startX + 4 + (i % columns) * 18;
                    int y = 28 + (i / columns) * 18;
                    if (mx >= x && mx < x + 18 && my >= y && my < y + 18) {
                        ClientPlayNetworking.send(new ExtractItemPayload(list.get(i).stack(), extractAllMode));
                        return false;
                    }
                }

                return true;
            });

            // KEYBOARD
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
                if (!panelVisible) return true;
                if (searchBox.isFocused()) {
                    if (event.key() == GLFW.GLFW_KEY_E || event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                        searchBox.keyPressed(event);
                        return false;
                    }
                }
                return true;
            });

            // RENDER
            ScreenEvents.afterExtract(screen).register((s, ctx, mx, my, delta) -> {

                collapseBtn.extractRenderState(ctx, mx, my, delta);

                if (!panelVisible) return;

                ctx.fill(startX, 8, startX + panelW, sh - 8, 0x88222222);

                searchBox.extractRenderState(ctx, mx, my, delta);
                sortButton.extractRenderState(ctx, mx, my, delta);
                tooltipBtn.extractRenderState(ctx, mx, my, delta);
                prevPageBtn.extractRenderState(ctx, mx, my, delta);
                nextPageBtn.extractRenderState(ctx, mx, my, delta);
                widthMinusBtn.extractRenderState(ctx, mx, my, delta);
                widthPlusBtn.extractRenderState(ctx, mx, my, delta);
                allBtn.extractRenderState(ctx, mx, my, delta);

                // PAGING
                List<SyncInventoryPayload.NetworkItemData> all = getSortedFilteredAll();
                int columns    = Math.max(1, (panelW - 8) / 18);
                int rows       = visibleRows();
                itemsPerPage   = Math.max(1, columns * rows);
                int totalPages = Math.max(1, (int) Math.ceil((double) all.size() / itemsPerPage));
                currentPage    = Math.min(currentPage, Math.max(0, totalPages - 1));

                ctx.centeredText(client.font,
                        (currentPage + 1) + "/" + totalPages,
                        startX + panelW / 2, 13, -1);

                List<SyncInventoryPayload.NetworkItemData> page = getProcessedItems();
                ItemStack hoveredItem = null;

                for (int i = 0; i < page.size(); i++) {
                    int ix = startX + 4 + (i % columns) * 18;
                    int iy = 28 + (i / columns) * 18;

                    ctx.item(page.get(i).stack(), ix, iy);

                    if (page.get(i).count() > 0) {
                        String countText = formatCount(page.get(i).count());
                        float scale = 0.65f;
                        int textW   = client.font.width(countText);
                        int textH   = client.font.lineHeight;

                        float anchorX = ix + 16f;
                        float anchorY = iy + 16f;

                        ctx.pose().pushMatrix();
                        ctx.pose().translate(anchorX, anchorY);
                        ctx.pose().scale(scale, scale);
                        ctx.text(client.font, countText, -textW, -textH, -1);
                        ctx.pose().popMatrix();
                    }

                    if (showTooltips && mx >= ix && mx < ix + 18 && my >= iy && my < iy + 18) {
                        hoveredItem = page.get(i).stack();
                    }
                }

                if (sortButton.isMouseOver(mx, my))
                    ctx.setTooltipForNextFrame(client.font, Component.literal("Sort: " + currentSort.hoverName), mx, my);
                if (tooltipBtn.isMouseOver(mx, my))
                    ctx.setTooltipForNextFrame(client.font, Component.literal("Item Tooltips"), mx, my);
                if (collapseBtn.isMouseOver(mx, my))
                    ctx.setTooltipForNextFrame(client.font, Component.literal(panelVisible ? "Hide panel" : "Show panel"), mx, my);
                if (widthMinusBtn.isMouseOver(mx, my))
                    ctx.setTooltipForNextFrame(client.font, Component.literal("Narrower panel (" + widthPercent + "%)"), mx, my);
                if (widthPlusBtn.isMouseOver(mx, my))
                    ctx.setTooltipForNextFrame(client.font, Component.literal("Wider panel (" + widthPercent + "%)"), mx, my);
                if (allBtn.isMouseOver(mx, my))
                    ctx.setTooltipForNextFrame(client.font,
                            Component.literal(extractAllMode
                                    ? "Click on an item: take ALL of it (as much as fits)"
                                    : "Click on an item: take one stack"), mx, my);

                if (hoveredItem != null) {
                    ctx.setTooltipForNextFrame(client.font, hoveredItem, mx, my);
                    ctx.extractDeferredElements(mx, my, delta);
                }
            });
        });
    }

    private static List<SyncInventoryPayload.NetworkItemData> getSortedFilteredAll() {
        String q = searchBox.getValue().toLowerCase(Locale.ROOT);
        List<SyncInventoryPayload.NetworkItemData> list = new ArrayList<>();
        for (SyncInventoryPayload.NetworkItemData data : cachedItems) {
            if (matchesQuery(data.stack(), q))
                list.add(data);
        }

        list.sort((a, b) -> {
            switch (currentSort) {
                case DESCENDING: {
                    int c = Long.compare(b.count(), a.count());
                    return c != 0 ? c : a.stack().getHoverName().getString().compareToIgnoreCase(b.stack().getHoverName().getString());
                }
                case ASCENDING: {
                    int c = Long.compare(a.count(), b.count());
                    return c != 0 ? c : a.stack().getHoverName().getString().compareToIgnoreCase(b.stack().getHoverName().getString());
                }
                case ALPHA_ASC:
                    return a.stack().getHoverName().getString().compareToIgnoreCase(b.stack().getHoverName().getString());
                case ALPHA_DESC:
                    return b.stack().getHoverName().getString().compareToIgnoreCase(a.stack().getHoverName().getString());
                default: { // RECENT
                    int c = Long.compare(b.timestamp(), a.timestamp());
                    return c != 0 ? c : a.stack().getHoverName().getString().compareToIgnoreCase(b.stack().getHoverName().getString());
                }
            }
        });

        return list;
    }

    private static List<SyncInventoryPayload.NetworkItemData> getProcessedItems() {
        List<SyncInventoryPayload.NetworkItemData> all = getSortedFilteredAll();
        int columns  = Math.max(1, (panelW - 8) / 18);
        int rows     = visibleRows();
        itemsPerPage = Math.max(1, columns * rows);
        int start = currentPage * itemsPerPage;
        if (start >= all.size()) return new ArrayList<>();
        return all.subList(start, Math.min(start + itemsPerPage, all.size()));
    }

    private static boolean matchesQuery(ItemStack stack, String q) {
        if (q.isEmpty()) return true;
        if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (stack.getItem().getName(stack).getString().toLowerCase(Locale.ROOT).contains(q)) return true;
        // enchanted books store enchantments under STORED_ENCHANTMENTS; enchanted gear uses ENCHANTMENTS
        if (enchantmentMatches(stack.get(DataComponents.STORED_ENCHANTMENTS), q)) return true;
        if (enchantmentMatches(stack.get(DataComponents.ENCHANTMENTS), q)) return true;
        return false;
    }

    private static boolean enchantmentMatches(ItemEnchantments enchants, String q) {
        if (enchants == null) return false;
        for (var entry : enchants.entrySet()) {
            if (entry.getKey().value().description().getString().toLowerCase(Locale.ROOT).contains(q))
                return true;
        }
        return false;
    }

    private static String formatCount(long count) {
        if (count >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0);
        if (count >= 1_000)     return String.format(Locale.ROOT, "%.1fk", count / 1_000.0);
        return String.valueOf(count);
    }
}
