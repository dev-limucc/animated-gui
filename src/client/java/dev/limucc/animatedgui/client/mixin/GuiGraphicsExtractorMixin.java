package dev.limucc.animatedgui.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.limucc.animatedgui.client.anim.ScreenAnimController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Matrix3x2fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Two render-side jobs for screen animations:
 *
 * <ul>
 *   <li><b>Entity follow</b> — {@code entity()} submits the inventory's 3D player model with absolute bounds and
 *       its own scale, ignoring the 2D pose; while a transform is active we run those through the pose so the
 *       model shrinks/slides with the panel (also fixes the creative open "top-left flash").</li>
 *   <li><b>Opacity</b> — for the FADE style we tint every fill / blit / text by {@code fadeAlpha} just before it
 *       is submitted, by multiplying the alpha of the colour each draw funnels through. Gated on
 *       {@code fadeAlpha < 1}, so it's a no-op for the entire game except the fading menu's own draws.</li>
 * </ul>
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {

    @Shadow @Final private Matrix3x2fStack pose;

    @WrapMethod(method = "entity")
    private void animatedgui$transformEntity(
            EntityRenderState renderState, float scale, Vector3f translation, Quaternionf rotation,
            Quaternionf overrideCameraAngle, int x0, int y0, int x1, int y1, Operation<Void> original) {
        if (!ScreenAnimController.transformActive) {
            original.call(renderState, scale, translation, rotation, overrideCameraAngle, x0, y0, x1, y1);
            return;
        }
        Matrix3x2fStack m = this.pose;
        int nx0 = Math.round(m.m00 * x0 + m.m10 * y0 + m.m20);
        int ny0 = Math.round(m.m01 * x0 + m.m11 * y0 + m.m21);
        int nx1 = Math.round(m.m00 * x1 + m.m10 * y1 + m.m20);
        int ny1 = Math.round(m.m01 * x1 + m.m11 * y1 + m.m21);
        float nscale = scale * m.m00; // m00 is the x-scale (1 for pure slides)
        original.call(renderState, nscale, translation, rotation, overrideCameraAngle, nx0, ny0, nx1, ny1);
    }

    // ── opacity: tint the colour every draw type funnels through ──────────────────

    @ModifyVariable(
            method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int animatedgui$fadeText(int color) {
        return animatedgui$fade(color);
    }

    @ModifyVariable(method = "innerFill", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int animatedgui$fadeFillPrimary(int color) {
        return animatedgui$fade(color);
    }

    @ModifyVariable(method = "innerFill", at = @At("HEAD"), argsOnly = true)
    private Integer animatedgui$fadeFillSecondary(Integer color) {
        return color == null ? null : animatedgui$fade(color);
    }

    @ModifyVariable(
            method = "innerBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/textures/GpuSampler;IIIIFFFFI)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int animatedgui$fadeBlit(int color) {
        return animatedgui$fade(color);
    }

    @ModifyVariable(method = "innerTiledBlit", at = @At("HEAD"), argsOnly = true, ordinal = 6)
    private int animatedgui$fadeTiled(int color) {
        return animatedgui$fade(color);
    }

    @Unique
    private static int animatedgui$fade(int color) {
        float a = ScreenAnimController.fadeAlpha;
        if (a >= 1.0f) return color;
        int alpha = Math.round(((color >>> 24) & 0xFF) * a);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
