package dev.limucc.animatedgui.client.mixin;

import dev.limucc.animatedgui.client.config.AnimConfig;
import dev.limucc.animatedgui.client.config.AnimConfigManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Slides the recipe grid when you page through it with the back/forward arrows, instead of the recipes swapping
 * instantly: the new page enters from the right (forward) or left (back). Tied to the menu-open animation toggle.
 */
@Mixin(RecipeBookPage.class)
public abstract class RecipeBookPageMixin {

    @Shadow private int currentPage;

    @Unique private int animatedgui$lastPage = -1;
    @Unique private long animatedgui$pageMs = -100000L;
    @Unique private int animatedgui$pageDir;
    @Unique private boolean animatedgui$pagePushed;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void animatedgui$pageSlideStart(
            GuiGraphicsExtractor graphics, int xo, int yo, int mouseX, int mouseY, float a, CallbackInfo ci) {
        animatedgui$pagePushed = false;
        if (this.currentPage != animatedgui$lastPage) {
            if (animatedgui$lastPage >= 0) {
                animatedgui$pageDir = Integer.signum(this.currentPage - animatedgui$lastPage);
                animatedgui$pageMs = Util.getMillis();
            }
            animatedgui$lastPage = this.currentPage;
        }
        AnimConfig.ScreenFeature cfg = AnimConfigManager.get().screenOpen;
        if (!cfg.on() || animatedgui$pageDir == 0) return;
        float t = (Util.getMillis() - animatedgui$pageMs) / (float) Math.max(1, cfg.durationMs);
        if (t < 0.0f || t >= 1.0f) return;
        float p = cfg.easing.apply(t);
        graphics.pose().pushMatrix();
        graphics.pose().translate(animatedgui$pageDir * (1.0f - p) * 120.0f, 0.0f);
        animatedgui$pagePushed = true;
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void animatedgui$pageSlideEnd(
            GuiGraphicsExtractor graphics, int xo, int yo, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (animatedgui$pagePushed) {
            graphics.pose().popMatrix();
            animatedgui$pagePushed = false;
        }
    }
}
