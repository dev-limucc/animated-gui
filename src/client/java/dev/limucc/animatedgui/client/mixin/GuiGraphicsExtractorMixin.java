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

/**
 * Makes the inventory's 3D player model follow a screen open/close animation. {@code entity()} submits a
 * picture-in-picture with absolute bounds and its own scale, ignoring the 2D pose — so without this the panel
 * would scale while the model popped in at full size at the panel's corner (reads as a top-left glitch on open).
 * While a screen transform is active we run the entity's bounds and scale through the same pose so it shrinks,
 * grows and slides together with everything else.
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
}
