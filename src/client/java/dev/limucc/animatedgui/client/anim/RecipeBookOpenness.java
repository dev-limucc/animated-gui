package dev.limucc.animatedgui.client.anim;

/**
 * Duck-typed onto the recipe book component by its mixin so the screen mixin can read how far the book is
 * "open" (eased), and therefore how far to slide the inventory aside to keep the two in sync.
 */
public interface RecipeBookOpenness {

    /** Horizontal pixels to offset the inventory content right now (0 when settled). */
    float animatedgui$invShift();
}
