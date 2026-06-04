package dev.limucc.animatedgui.client.mixin;

import dev.limucc.animatedgui.client.anim.Tween;
import dev.limucc.animatedgui.client.config.AnimConfig;
import dev.limucc.animatedgui.client.config.AnimConfigManager;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.ItemPickerMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smooths the creative inventory's notoriously jumpy scroll. Vanilla snaps {@code scrollOffs} straight to the
 * target and rebuilds the grid at the rounded row, so a wheel notch "pages" a whole row at once. We keep a
 * separate display value that eases toward {@code scrollOffs} every frame and re-issue {@code scrollTo} with it,
 * so the rows cascade smoothly instead of teleporting. Scrollbar drags (and tab resets that write
 * {@code scrollOffs} directly) are detected and adopted instantly, so nothing fights the user.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {

    @Shadow private float scrollOffs;
    @Shadow private boolean scrolling;

    @Shadow private boolean canScroll() { throw new AssertionError(); }

    @Shadow @Final protected AbstractContainerMenu menu;

    @Unique private final Tween animatedgui$scroll = new Tween();
    @Unique private float animatedgui$lastTarget = Float.NaN;
    @Unique private float animatedgui$lastDisplay = Float.NaN;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("HEAD"))
    private void animatedgui$easeScroll(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        AnimConfig.Feature cfg = AnimConfigManager.get().creativeScroll;

        // Disabled, can't scroll, or actively dragging the bar -> stay glued to vanilla.
        if (!cfg.on() || !this.canScroll() || this.scrolling) {
            animatedgui$scroll.snap(this.scrollOffs);
            animatedgui$lastTarget = this.scrollOffs;
            animatedgui$lastDisplay = this.scrollOffs;
            return;
        }

        long now = Util.getMillis();

        // Any external write to scrollOffs (wheel, tab reset) becomes the new easing target.
        if (Float.isNaN(animatedgui$lastTarget) || this.scrollOffs != animatedgui$lastTarget) {
            animatedgui$scroll.retarget(this.scrollOffs, now, cfg.durationMs, cfg.easing);
            animatedgui$lastTarget = this.scrollOffs;
        }

        float display = animatedgui$scroll.update(now);
        if (Float.isNaN(animatedgui$lastDisplay) || Math.abs(display - animatedgui$lastDisplay) > 1.0e-4f) {
            ((ItemPickerMenu) this.menu).scrollTo(display);
            animatedgui$lastDisplay = display;
        }
    }
}
