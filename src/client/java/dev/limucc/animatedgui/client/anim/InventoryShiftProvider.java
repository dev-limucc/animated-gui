package dev.limucc.animatedgui.client.anim;

/**
 * Duck-typed onto recipe-book screens so the generic screen mixin can ask how far to slide the whole inventory
 * (panel texture included) aside while the recipe book opens/closes.
 */
public interface InventoryShiftProvider {

    /** Horizontal pixels to offset the inventory (panel + contents) right now; 0 when settled. */
    float animatedgui$screenInvShift();
}
