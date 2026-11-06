package dev.limucc.animatedgui.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.limucc.animatedgui.client.anim.ScreenAnimController;
import net.minecraft.client.renderer.GameRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fades the menu background blur with the screen: the radius the game renderer is about to apply is scaled by the
 * factor the screen published while it queued its blur ({@link ScreenAnimController#blurFactor}). The factor is
 * consumed here, so a frame without a menu blur always runs at full strength. Cosmetic only, hence {@code require = 0}.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @ModifyExpressionValue(
            method = "render",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;menuBackgroundBlurriness:I",
                    opcode = Opcodes.GETFIELD),
            require = 0)
    private int animatedgui$scaleBlur(int radius) {
        float f = ScreenAnimController.blurFactor;
        ScreenAnimController.blurFactor = 1.0f;
        if (f >= 1.0f || radius <= 0) return radius;
        ScreenAnimController.blurScaled++;
        return Math.round(radius * f);
    }
}
