package dev.limucc.animatedgui.client.anim;

/** Duck interface mixed into {@code GuiGraphicsExtractor}: small private-state hooks the screen mixin needs. */
public interface GfxAccess {
    /** Drop the tooltip a slot/widget queued this frame (used while a screen is mid-animation). */
    void animatedgui$clearTooltip();
}
