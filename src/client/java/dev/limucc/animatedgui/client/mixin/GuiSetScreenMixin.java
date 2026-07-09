package dev.limucc.animatedgui.client.mixin;

import dev.limucc.animatedgui.client.anim.ScreenAnimController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks {@code Gui.setScreen} (screen management moved from {@code Minecraft} to {@code Gui} in 26.2)
 * so a close ({@code setScreen(null)} while in-game) can be deferred: the outgoing screen is kept up to
 * play its exit animation, and {@link ScreenAnimController} re-issues the real close once the animation
 * finishes.
 */
@Mixin(Gui.class)
public abstract class GuiSetScreenMixin {

    @Shadow
    private Screen screen;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void animatedgui$deferClose(Screen next, CallbackInfo ci) {
        if (ScreenAnimController.interceptClose(Minecraft.getInstance(), this.screen, next)) {
            ci.cancel();
        }
    }

    /**
     * The new screen has been installed (this only runs when the switch wasn't deferred) — start its open
     * animation. Done here rather than in {@code Screen.added()} because some screens override {@code added()}
     * without calling super, which would skip the open animation.
     */
    @Inject(method = "setScreen", at = @At("TAIL"))
    private void animatedgui$onScreenSet(Screen next, CallbackInfo ci) {
        ScreenAnimController.onScreenAdded(this.screen);
    }
}
