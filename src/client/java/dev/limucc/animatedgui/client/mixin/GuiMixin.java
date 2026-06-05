package dev.limucc.animatedgui.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.limucc.animatedgui.client.anim.Tween;
import dev.limucc.animatedgui.client.config.AnimConfig;
import dev.limucc.animatedgui.client.config.AnimConfigManager;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Slides the hotbar selection box between slots instead of teleporting it, with an optional motion-blur trail.
 * We recover the per-slot base from the sprite's x, re-place it using an eased "animated slot", and (when the
 * trail is enabled) draw a few fading ghost copies along the path it's travelling. Wrapping (8↔0) snaps rather
 * than sweeping the whole bar.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Shadow @Final private Minecraft minecraft;

    @Unique private final Tween animatedgui$selector = new Tween();
    @Unique private boolean animatedgui$selectorInit;

    @WrapOperation(
            method = "extractItemHotbar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
                    ordinal = 1))
    private void animatedgui$slideSelection(
            GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h,
            Operation<Void> original) {
        AnimConfig.Feature cfg = AnimConfigManager.get().hotbar;
        if (!cfg.on() || this.minecraft.player == null) {
            original.call(graphics, pipeline, sprite, x, y, w, h);
            return;
        }

        int sel = this.minecraft.player.getInventory().getSelectedSlot();
        long now = Util.getMillis();

        if (!animatedgui$selectorInit) {
            animatedgui$selector.snap(sel);
            animatedgui$selectorInit = true;
        } else if (Math.abs(sel - animatedgui$selector.current()) > 4.5f) {
            animatedgui$selector.snap(sel); // wrap-around (8 -> 0): don't sweep across the whole bar
        }

        animatedgui$selector.retarget(sel, now, cfg.durationMs, cfg.easing);
        float animSlot = animatedgui$selector.update(now);

        // Optional motion-blur trail: fading ghosts strung along the path from where the move started.
        if (AnimConfigManager.get().hotbarTrail && animatedgui$selector.isActive()) {
            float startSlot = animatedgui$selector.start();
            int ghosts = 4;
            for (int i = 1; i <= ghosts; i++) {
                float t = i / (float) (ghosts + 1);
                float ghostSlot = startSlot + (animSlot - startSlot) * t;
                int gx = Math.round(x + (ghostSlot - sel) * 20.0f);
                graphics.blitSprite(pipeline, sprite, gx, y, w, h, 0.22f * (1.0f - t));
            }
        }

        int realX = Math.round(x + (animSlot - sel) * 20.0f);
        original.call(graphics, pipeline, sprite, realX, y, w, h);
    }
}
