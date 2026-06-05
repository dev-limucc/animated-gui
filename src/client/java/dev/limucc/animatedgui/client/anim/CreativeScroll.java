package dev.limucc.animatedgui.client.anim;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Shared state for the creative inventory's smooth pixel-slide, published each frame by the creative-screen
 * mixin and read by the container-screen mixin (which owns the actual slot rendering). Lets the grid items be
 * nudged by a sub-row pixel amount and an extra "incoming" row be drawn, so the grid truly slides instead of
 * paging a whole row at a time.
 *
 * <p>Coordinates are relative to the screen's {@code (leftPos, topPos)} — the space slots are drawn in.
 */
public final class CreativeScroll {

    private CreativeScroll() {}

    /** Grid geometry: 9 columns × 5 rows of 18px cells, with the top-left cell at (9, 18). */
    public static final int GRID_LEFT = 9;
    public static final int GRID_TOP = 18;
    public static final int GRID_RIGHT = 9 + 9 * 18;
    public static final int GRID_BOTTOM = 18 + 5 * 18;
    public static final int CELL = 18;
    public static final int ROWS = 5;

    /** True only while a slide is in progress (otherwise the menu sits on an exact row and we don't interfere). */
    public static boolean active;
    /** Pixels the grid is shifted upward (0..17): the fractional part of the row position. */
    public static int pixelOffset;
    /** Top item-row currently loaded into the 45 grid slots. */
    public static int floorRow;
    /** Total scrollable rows. */
    public static int rowCount;
    /**
     * Identity of the creative grid container, so the container mixin can tell grid slots (which reshuffle on
     * scroll) from the stable player-inventory slots. Set once and kept — it's a shared static, always the same.
     */
    public static Object container;
    /** The full item list for the selected tab (to draw the extra incoming row). */
    public static List<ItemStack> items;
    /** The selected tab's background texture — re-blitted as a sliding strip so the grid cells slide too. */
    public static Identifier gridTexture;

    public static void clear() {
        active = false;
        items = null;
        gridTexture = null;
    }

    /** The item at item-row {@code row}, column {@code col}, or EMPTY if out of range. */
    public static ItemStack itemAt(int row, int col) {
        if (items == null || row < 0) return ItemStack.EMPTY;
        int idx = row * 9 + col;
        return (idx >= 0 && idx < items.size()) ? items.get(idx) : ItemStack.EMPTY;
    }
}
