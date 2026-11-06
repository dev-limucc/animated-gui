package dev.limucc.animatedgui.client.mixin;

import dev.limucc.animatedgui.client.anim.ListScrollHit;
import dev.limucc.animatedgui.client.anim.ScreenAnimController;
import dev.limucc.animatedgui.client.config.AnimConfig;
import dev.limucc.animatedgui.client.config.AnimConfigManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Generic smooth scrolling for <em>every</em> vanilla-list screen — no mod is named or predicted. Any screen
 * built on {@link AbstractSelectionList} (the options menu, Mod Menu's list, Cloth Config, and the countless
 * mod settings screens that reuse it) inherits an eased scroll instead of snapping row-by-row.
 *
 * <p>The list still tracks its true scroll position in {@code AbstractScrollArea.scrollAmount} (vanilla updates
 * it on wheel / drag). We keep a separate <em>display</em> value that chases the target with frame-rate-independent
 * exponential smoothing, swap it in just for the render-state extraction, then restore the true value — so input,
 * clamping and the scrollbar math stay exactly vanilla while only the on-screen position is smoothed.
 *
 * <p>A click, however, must land on the row you <em>see</em>, which is the displayed position, not the target:
 * {@link ListScrollHit} lets the container-widget mixin swap the displayed position in for the click as well.
 */
@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin implements ListScrollHit {

    @Unique private double animatedgui$display;
    @Unique private long animatedgui$lastMs;
    @Unique private boolean animatedgui$init;
    @Unique private double animatedgui$saved;
    @Unique private boolean animatedgui$swapped;
    @Unique private double animatedgui$hitSaved;
    @Unique private double animatedgui$hitShown;
    @Unique private boolean animatedgui$hitSwapped;

    @Unique
    private static boolean animatedgui$enabled() {
        return AnimConfigManager.get().listScroll.on() && !ScreenAnimController.deniedCurrent();
    }

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"))
    private void animatedgui$smoothBegin(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        animatedgui$swapped = false;
        if (!animatedgui$enabled()) {
            animatedgui$init = false; // re-anchor on the current position when smoothing resumes
            return;
        }
        AnimConfig.Feature f = AnimConfigManager.get().listScroll;

        AbstractScrollArea sa = (AbstractScrollArea) (Object) this;
        double target = sa.scrollAmount();
        long now = Util.getMillis();
        if (!animatedgui$init) {
            animatedgui$display = target;
            animatedgui$lastMs = now;
            animatedgui$init = true;
        }
        double dt = Math.max(0.0, Math.min(100.0, now - animatedgui$lastMs));
        animatedgui$lastMs = now;

        double tau = Math.max(20.0, f.durationMs);
        double alpha = 1.0 - Math.exp(-dt / tau);
        animatedgui$display += (target - animatedgui$display) * alpha;
        if (Math.abs(target - animatedgui$display) < 0.5) animatedgui$display = target; // settle, no endless chase

        animatedgui$saved = target;
        sa.setScrollAmount(animatedgui$display); // render extraction reads the smoothed position
        animatedgui$swapped = true;
    }

    @Inject(method = "extractWidgetRenderState", at = @At("RETURN"))
    private void animatedgui$smoothEnd(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!animatedgui$swapped) return;
        ((AbstractScrollArea) (Object) this).setScrollAmount(animatedgui$saved); // restore the true target
        animatedgui$swapped = false;
    }

    @Override
    public void animatedgui$hitBegin() {
        animatedgui$hitSwapped = false;
        if (!animatedgui$init || !animatedgui$enabled()) return;
        AbstractScrollArea sa = (AbstractScrollArea) (Object) this;
        double target = sa.scrollAmount();
        if (Math.abs(animatedgui$display - target) < 0.5) return; // settled: shown == real, nothing to map
        animatedgui$hitSaved = target;
        sa.setScrollAmount(animatedgui$display);
        animatedgui$hitShown = sa.scrollAmount();
        animatedgui$hitSwapped = true;
    }

    @Override
    public void animatedgui$hitEnd() {
        if (!animatedgui$hitSwapped) return;
        animatedgui$hitSwapped = false;
        AbstractScrollArea sa = (AbstractScrollArea) (Object) this;
        // Only put the target back if the click left the scroll alone; a scrollbar grab sets its own position.
        if (sa.scrollAmount() == animatedgui$hitShown) sa.setScrollAmount(animatedgui$hitSaved);
    }
}
