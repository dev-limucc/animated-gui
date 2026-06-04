package dev.limucc.animatedgui.client.mixin;

import dev.limucc.animatedgui.client.anim.Tween;
import dev.limucc.animatedgui.client.config.AnimConfig;
import dev.limucc.animatedgui.client.config.AnimConfigManager;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Slides the hotbar selection box between slots instead of teleporting it. Vanilla draws the selection sprite
 * at {@code screenCenter - 92 + selectedSlot*20}; we recover the per-slot base from that x and re-place it
 * using an eased "animated slot" that glides toward the real one. Wrapping (8↔0) snaps rather than sweeping
 * the box across the whole bar.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Shadow @Final private Minecraft minecraft;

    @Unique private final Tween animatedgui$selector = new Tween();
    @Unique private boolean animatedgui$selectorInit;

    @ModifyArg(
            method = "extractItemHotbar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
                    ordinal = 1),
            index = 2)
    private int animatedgui$slideSelection(int x) {
        AnimConfig.Feature cfg = AnimConfigManager.get().hotbar;
        if (!cfg.on() || this.minecraft.player == null) return x;

        int sel = this.minecraft.player.getInventory().getSelectedSlot();
        long now = Util.getMillis();

        if (!animatedgui$selectorInit) {
            animatedgui$selector.snap(sel);
            animatedgui$selectorInit = true;
        } else if (Math.abs(sel - animatedgui$selector.current()) > 4.5f) {
            animatedgui$selector.snap(sel); // wrap-around (e.g. 8 -> 0): don't sweep the whole bar
        }

        animatedgui$selector.retarget(sel, now, cfg.durationMs, cfg.easing);
        float animSlot = animatedgui$selector.update(now);
        return Math.round(x + (animSlot - sel) * 20.0f);
    }
}
