package dev.limucc.animatedgui.client.mixin;

import dev.limucc.animatedgui.client.anim.ScreenAnimController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks {@code setScreen} so a close ({@code setScreen(null)} while in-game) can be deferred: the outgoing
 * screen is kept up to play its exit animation, and {@link ScreenAnimController} re-issues the real close
 * once the animation finishes.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow
    public Screen screen;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void animatedgui$deferClose(Screen next, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (ScreenAnimController.interceptClose(mc, this.screen, next)) {
            ci.cancel();
        }
    }
}
