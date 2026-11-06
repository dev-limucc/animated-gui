package dev.limucc.animatedgui.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.limucc.animatedgui.client.anim.GfxAccess;
import dev.limucc.animatedgui.client.anim.PipCapture;
import dev.limucc.animatedgui.client.anim.ScreenAnimController;
import dev.limucc.animatedgui.client.compat.GfxCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import org.joml.Matrix3x2fStack;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Render-side jobs for screen animations:
 *
 * <ul>
 *   <li><b>Player model</b> — the inventory's 3D player model is a picture-in-picture that ignores the 2D pose.
 *       While a screen transform is active we record the pose + opacity it was submitted with and hand the state
 *       <em>transformed bounds</em> (so the GUI layering still puts it above the moving panel); the model's own
 *       bounds, and therefore its render texture, never change. {@code PictureInPictureRendererMixin} then blits
 *       it through that pose, tinted by the fade.</li>
 *   <li><b>Opacity</b> — for the FADE styles we tint every fill / blit / text by {@code fadeAlpha} just before it
 *       is submitted, by multiplying the alpha of the colour each draw funnels through. Gated on
 *       {@code fadeAlpha < 1}, so it's a no-op for the entire game except the fading menu's own draws. Items and
 *       the player model are tinted later, where their textures are blitted (see the two sibling mixins).</li>
 *   <li><b>Tooltip drop</b> — lets the screen mixin discard the tooltip a slot queued mid-animation.</li>
 * </ul>
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin implements GfxAccess {

    @Shadow @Final private Matrix3x2fStack pose;
    @Shadow private Runnable deferredTooltip;

    @Override
    public void animatedgui$clearTooltip() {
        this.deferredTooltip = null;
    }

    @WrapOperation(
            method = "entity",
            at = @At(value = "NEW", target = "net/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState"))
    private GuiEntityRenderState animatedgui$captureEntity(
            EntityRenderState renderState, Vector3fc translation, Quaternionfc rotation,
            Quaternionfc overrideCameraAngle, int x0, int y0, int x1, int y1, float scale,
            ScreenRectangle scissor, Operation<GuiEntityRenderState> original) {
        if (!ScreenAnimController.transformActive) {
            return original.call(renderState, translation, rotation, overrideCameraAngle, x0, y0, x1, y1, scale, scissor);
        }
        // Layering bounds follow the pose; the model's x0..y1 (= its render-texture size) stay exactly as submitted.
        ScreenRectangle moved = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(this.pose);
        ScreenRectangle bounds = scissor != null ? scissor.intersection(moved) : moved;
        GuiEntityRenderState state = new GuiEntityRenderState(
                renderState, translation, rotation, overrideCameraAngle, x0, y0, x1, y1, scale, scissor, bounds);
        PipCapture.put(state, this.pose, ScreenAnimController.fadeAlpha);
        return state;
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

    @ModifyVariable(method = GfxCompat.INNER_BLIT, at = @At("HEAD"), argsOnly = true, ordinal = 4)
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
