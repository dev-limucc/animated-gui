package dev.limucc.animatedgui.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.limucc.animatedgui.client.anim.InventoryShiftProvider;
import dev.limucc.animatedgui.client.anim.ScreenAnimController;
import dev.limucc.animatedgui.client.config.AnimConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Open/close screen animation. The whole menu — its background panel AND its contents — animates as one unit,
 * while the screen-dim stays put.
 *
 * <p>The trick: a container screen draws its dim first (via {@code super.extractBackground}) and then its
 * panel PNG. So we push the eased pose transform at the <em>tail</em> of {@code Screen.extractBackground}
 * (after the dim, before the panel) and pop it right after the content render. Because the GUI pipeline
 * snapshots the pose per draw and {@code nextStratum()} doesn't touch it, that single push cleanly covers the
 * panel, slots, items, labels and buttons — everything except the dim and tooltips.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Unique private boolean animatedgui$pushed;

    @Inject(method = "added", at = @At("TAIL"))
    private void animatedgui$onAdded(CallbackInfo ci) {
        ScreenAnimController.onScreenAdded((Screen) (Object) this);
    }

    @Inject(
            method = "extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("RETURN"))
    private void animatedgui$pushAfterDim(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        Screen me = (Screen) (Object) this;
        float raw = ScreenAnimController.rawProgress(me);
        // Recipe-book screens also slide the whole inventory (panel included) aside as the book opens/closes.
        float invShift = (me instanceof InventoryShiftProvider isp) ? isp.animatedgui$screenInvShift() : 0.0f;
        boolean animating = raw < 1.0f;
        if (!animating && Math.abs(invShift) < 0.01f) {
            animatedgui$pushed = false;
            ScreenAnimController.transformActive = false;
            return;
        }
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        if (animating) {
            AnimConfig.ScreenFeature cfg = ScreenAnimController.configFor(me);
            float p = cfg.easing.apply(raw);
            cfg.style.apply(pose, p, graphics.guiWidth(), graphics.guiHeight());
        }
        if (Math.abs(invShift) >= 0.01f) {
            pose.translate(invShift, 0.0f);
        }
        animatedgui$pushed = true;
        ScreenAnimController.transformActive = true;
    }

    @WrapOperation(
            method = "extractRenderStateWithTooltipAndSubtitles",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void animatedgui$popAfterContent(
            Screen self, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, Operation<Void> original) {
        original.call(self, graphics, mouseX, mouseY, a);
        if (animatedgui$pushed) {
            graphics.pose().popMatrix();
            animatedgui$pushed = false;
        }
        ScreenAnimController.transformActive = false;
    }
}
