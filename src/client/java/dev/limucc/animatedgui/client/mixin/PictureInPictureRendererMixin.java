package dev.limucc.animatedgui.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.limucc.animatedgui.client.anim.AlphaHolder;
import dev.limucc.animatedgui.client.anim.PipCapture;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.OversizedItemRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix3x2fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Blits a picture-in-picture texture (the inventory player model) through the pose it was submitted with and
 * fades it with the screen. The PiP keeps its own bounds, so its texture is never resized or re-allocated while
 * the panel animates — no per-frame realloc, no 1px jitter from rounding bounds.
 */
@Mixin(PictureInPictureRenderer.class)
public abstract class PictureInPictureRendererMixin {

    @WrapOperation(
            method = "blitTexture",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;pose()Lorg/joml/Matrix3x2fc;"))
    private Matrix3x2fc animatedgui$capturedPose(PictureInPictureRenderState state, Operation<Matrix3x2fc> original) {
        int i = PipCapture.find(state);
        return i >= 0 ? PipCapture.pose(i) : original.call(state);
    }

    @ModifyConstant(method = "blitTexture", constant = @Constant(intValue = -1))
    private int animatedgui$fadeBlit(int color, @Local(argsOnly = true) PictureInPictureRenderState state) {
        float a = 1.0f;
        int i = PipCapture.find(state);
        if (i >= 0) {
            a = PipCapture.alpha(i);
        } else if (state instanceof OversizedItemRenderState big) {
            // an oversized item model is a PiP too; its opacity travels on the item state it was built from
            a = ((AlphaHolder) (Object) big.guiItemRenderState()).animatedgui$alpha();
        }
        return a >= 1.0f ? color : PipCapture.premultipliedTint(a);
    }
}
