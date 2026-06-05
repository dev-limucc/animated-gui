package dev.limucc.animatedgui.client.mixin;

import dev.limucc.animatedgui.client.anim.CreativeScroll;
import dev.limucc.animatedgui.client.anim.Tween;
import dev.limucc.animatedgui.client.config.AnimConfig;
import dev.limucc.animatedgui.client.config.AnimConfigManager;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.ItemPickerMenu;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Real pixel-smooth creative scrolling. Vanilla snaps the grid to whole rows, so a wheel notch "pages". We
 * instead ease a continuous row position, keep the menu loaded at the floor row, and publish the fractional
 * sub-row offset to {@link CreativeScroll}. The container mixin then nudges the grid items up by that many
 * pixels (clipped to the grid) and draws one extra incoming row at the bottom — so the items glide like a
 * scrolling list instead of jumping a row at a time. At rest we hand rendering straight back to vanilla so
 * clicks always line up.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {

    @Shadow private float scrollOffs;
    @Shadow private boolean scrolling;

    @Shadow private boolean canScroll() { throw new AssertionError(); }

    @Shadow @Final private static SimpleContainer CONTAINER;

    @Shadow private static CreativeModeTab selectedTab;

    @Unique private final Tween animatedgui$row = new Tween();
    @Unique private float animatedgui$lastScrollOffs = Float.NaN;
    @Unique private boolean animatedgui$settled = true;
    @Unique private boolean animatedgui$wasDragging;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("HEAD"))
    private void animatedgui$easeScroll(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        AnimConfig.Feature cfg = AnimConfigManager.get().creativeScroll;
        ItemPickerMenu menu = (ItemPickerMenu) ((AbstractContainerScreen<?>) (Object) this).getMenu();
        int rowCount = Math.max(0, animatedgui$ceilDiv(menu.items.size()) - CreativeScroll.ROWS);
        CreativeScroll.container = CONTAINER; // always, so grid slots can be identified even when not sliding

        if (!cfg.on() || !this.canScroll() || rowCount <= 0) {
            CreativeScroll.clear();
            animatedgui$row.snap(rowCount > 0 ? this.scrollOffs * rowCount : 0.0f);
            animatedgui$lastScrollOffs = this.scrollOffs;
            animatedgui$settled = true; // vanilla owns the scroll position here
            return;
        }

        long now = Util.getMillis();

        // Dragging the scrollbar: map its position straight to a continuous row so the grid slides 1:1 with the
        // cursor (no easing lag). The tween follows so releasing eases to snap onto the nearest row.
        if (this.scrolling) {
            float dragRow = Math.max(0.0f, Math.min(rowCount, this.scrollOffs * rowCount));
            animatedgui$row.snap(dragRow);
            animatedgui$lastScrollOffs = this.scrollOffs;
            animatedgui$settled = false;
            animatedgui$wasDragging = true;
            animatedgui$publishSlide(menu, dragRow, rowCount);
            return;
        }

        int targetRow = Math.max(0, Math.min(rowCount, Math.round(this.scrollOffs * rowCount)));

        // A fresh external scrollOffs (wheel / tab reset) — or releasing a drag — becomes the new easing target.
        boolean released = animatedgui$wasDragging;
        animatedgui$wasDragging = false;
        if (released || Float.isNaN(animatedgui$lastScrollOffs) || this.scrollOffs != animatedgui$lastScrollOffs) {
            animatedgui$row.retarget(targetRow, now, cfg.durationMs, cfg.easing);
        }
        animatedgui$lastScrollOffs = this.scrollOffs;

        float displayRow = Math.max(0.0f, Math.min(rowCount, animatedgui$row.update(now)));
        float frac = displayRow - (int) Math.floor(displayRow);

        if (frac < 0.004f && !animatedgui$row.isActive()) {
            // Settled on a row — snap to the final target once, then let vanilla render so hit-testing matches.
            if (!animatedgui$settled) {
                menu.scrollTo(this.scrollOffs);
                animatedgui$settled = true;
            }
            CreativeScroll.clear();
            return;
        }

        animatedgui$settled = false;
        animatedgui$publishSlide(menu, displayRow, rowCount);
    }

    /** Load the floor row and publish the sub-row pixel offset + extra-row data for the container mixin. */
    @Unique
    private void animatedgui$publishSlide(ItemPickerMenu menu, float displayRow, int rowCount) {
        int floorRow = Math.max(0, Math.min(rowCount, (int) Math.floor(displayRow)));
        float frac = displayRow - floorRow;
        menu.scrollTo(floorRow / (float) rowCount);
        CreativeScroll.active = true;
        CreativeScroll.pixelOffset = Math.round(frac * CreativeScroll.CELL);
        CreativeScroll.floorRow = floorRow;
        CreativeScroll.rowCount = rowCount;
        CreativeScroll.items = menu.items;
        CreativeScroll.gridTexture = selectedTab.getBackgroundTexture();
    }

    /** ceil(size / 9). */
    @Unique
    private static int animatedgui$ceilDiv(int size) {
        return (size + 8) / 9;
    }
}
