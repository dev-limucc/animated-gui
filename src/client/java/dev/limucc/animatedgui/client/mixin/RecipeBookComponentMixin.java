package dev.limucc.animatedgui.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.limucc.animatedgui.client.anim.RecipeBookOpenness;
import dev.limucc.animatedgui.client.anim.Tween;
import dev.limucc.animatedgui.client.config.AnimConfig;
import dev.limucc.animatedgui.client.config.AnimConfigManager;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Recipe book animation + crash guard.
 *
 * <ul>
 *   <li><b>Crash guard</b> — the component grabs {@code player.getRecipeBook()} in its constructor; if a
 *       container opens before the recipe book has synced that's null and the next tick NPEs. Skip the tick
 *       until it has a book.</li>
 *   <li><b>Open/close slide</b> — an eased {@code openness} (0..1) drives the whole transition: the book panel
 *       slides in from / out to the left, and the inventory slides aside to keep room (via {@link RecipeBookOpenness},
 *       read by the screen mixin). To slide the panel <em>out</em> on close we force it to keep rendering while
 *       {@code openness} is still &gt; 0, even though vanilla has already flagged it invisible.</li>
 * </ul>
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin implements RecipeBookOpenness {

    @Shadow private ClientRecipeBook book;
    @Shadow private boolean widthTooNarrow;

    @Shadow public abstract boolean isVisible();

    @Unique private final Tween animatedgui$open = new Tween();
    @Unique private boolean animatedgui$openInit;
    @Unique private float animatedgui$openness;
    @Unique private boolean animatedgui$pushed;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void animatedgui$skipTickWhenUnsynced(CallbackInfo ci) {
        if (this.book == null) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void animatedgui$drive(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        animatedgui$pushed = false;
        float target = this.isVisible() ? 1.0f : 0.0f;
        AnimConfig.ScreenFeature cfg = AnimConfigManager.get().screenOpen;
        long now = Util.getMillis();

        if (!cfg.on()) {
            animatedgui$open.snap(target);
            animatedgui$openness = target;
            animatedgui$openInit = true;
            return;
        }
        if (!animatedgui$openInit) {
            animatedgui$open.snap(target);
            animatedgui$openInit = true;
        }
        animatedgui$open.retarget(target, now, cfg.durationMs, cfg.easing);
        animatedgui$openness = animatedgui$open.update(now);

        if (animatedgui$openness > 0.001f && animatedgui$openness < 0.999f) {
            // The screen mixin shifts the whole inventory (including this book) by invShift; cancel it here so
            // only the inventory moves, then slide the book itself in from / out to the left.
            float cancel = -this.animatedgui$invShift();
            graphics.pose().pushMatrix();
            graphics.pose().translate(cancel - (1.0f - animatedgui$openness) * 180.0f, 0.0f);
            animatedgui$pushed = true;
        }
    }

    @ModifyExpressionValue(
            method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;isVisible()Z"))
    private boolean animatedgui$keepRenderingWhileClosing(boolean original) {
        return original || animatedgui$openness > 0.001f;
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void animatedgui$endSlide(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (animatedgui$pushed) {
            graphics.pose().popMatrix();
            animatedgui$pushed = false;
        }
    }

    @Override
    public float animatedgui$invShift() {
        if (this.widthTooNarrow) return 0.0f;
        float actual = this.isVisible() ? 1.0f : 0.0f;
        return (animatedgui$openness - actual) * 77.0f;
    }
}
