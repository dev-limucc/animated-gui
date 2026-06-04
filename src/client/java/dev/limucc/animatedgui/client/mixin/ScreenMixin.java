package dev.limucc.animatedgui.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.limucc.animatedgui.client.anim.ScreenAnimController;
import dev.limucc.animatedgui.client.config.AnimConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * Open/close screen animation. We start the "open" timer when a screen is added, and wrap the screen's own
 * content render with a pose transform (scale / slide, eased) driven by {@link ScreenAnimController}. The
 * background dim and tooltips are drawn outside this wrap, so only the menu body moves — which reads cleanly.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "added", at = @At("TAIL"))
    private void animatedgui$onAdded(CallbackInfo ci) {
        ScreenAnimController.onScreenAdded((Screen) (Object) this);
    }

    @WrapOperation(
            method = "extractRenderStateWithTooltipAndSubtitles",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void animatedgui$animateScreen(
            Screen self, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, Operation<Void> original) {
        Screen me = (Screen) (Object) this;
        float raw = ScreenAnimController.rawProgress(me);
        if (raw >= 1.0f) {
            original.call(self, graphics, mouseX, mouseY, a);
            return;
        }
        AnimConfig.ScreenFeature cfg = ScreenAnimController.configFor(me);
        float p = cfg.easing.apply(raw);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        cfg.style.apply(pose, p, graphics.guiWidth(), graphics.guiHeight());
        original.call(self, graphics, mouseX, mouseY, a);
        pose.popMatrix();
    }
}
