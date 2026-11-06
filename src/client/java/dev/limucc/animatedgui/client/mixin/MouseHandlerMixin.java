package dev.limucc.animatedgui.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.limucc.animatedgui.client.anim.ScreenAnimController;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Input correctness while a screen is mid-animation: what you click must be what you see.
 *
 * <ul>
 *   <li>Every screen mouse position (hover highlights, clicks, drags, wheel) flows through
 *       {@code getScaledXPos/getScaledYPos}. While a screen is drawn through an open/close transform we map the
 *       position through that transform's inverse, so slots/buttons react where they appear, not where they would
 *       sit un-animated. Outside an animation this is a single null check.</li>
 *   <li>A screen that is animating <em>out</em> swallows new clicks: it is already gone, and a click on a shrinking
 *       inventory must not pick items up or press a button. Keys still work, so Esc/E again skips the animation.</li>
 * </ul>
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @ModifyReturnValue(method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("RETURN"))
    private double animatedgui$mapX(double x) {
        return ScreenAnimController.compensating() ? ScreenAnimController.compX(x) : x;
    }

    @ModifyReturnValue(method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("RETURN"))
    private double animatedgui$mapY(double y) {
        return ScreenAnimController.compensating() ? ScreenAnimController.compY(y) : y;
    }

    @WrapOperation(
            method = "onButton",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"))
    private boolean animatedgui$ignoreClickWhileClosing(
            Screen screen, MouseButtonEvent event, boolean doubleClick, Operation<Boolean> original) {
        if (ScreenAnimController.isClosing(screen)) return true;
        return original.call(screen, event, doubleClick);
    }
}
