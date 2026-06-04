package dev.limucc.animatedgui.client.mixin;

import dev.limucc.animatedgui.client.anim.Easing;
import dev.limucc.animatedgui.client.config.AnimConfig;
import dev.limucc.animatedgui.client.config.AnimConfigManager;
import net.minecraft.util.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smooth chat. Vanilla shoves existing lines up by one line the instant a message arrives. We instead render
 * the whole chat shifted down by the just-added line(s) and ease that offset back to zero, so old lines glide
 * up and the new line rises in from below — instead of the hard pop.
 *
 * <p>The offset lives in "lines" and is converted to screen pixels using the same line height & scale vanilla
 * uses, then applied as a pose translation right after chat pushes its matrix (so it's cleanly popped).
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    @Shadow private double getScale() { throw new AssertionError(); }

    @Shadow private int getLineHeight() { throw new AssertionError(); }

    @Unique private float animatedgui$slideStart;
    @Unique private long animatedgui$slideStartMs;
    @Unique private int animatedgui$slideDurationMs = 1;
    @Unique private Easing animatedgui$slideEasing = Easing.EASE_OUT;

    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"))
    private void animatedgui$onNewMessage(GuiMessage message, CallbackInfo ci) {
        AnimConfig.Feature cfg = AnimConfigManager.get().chat;
        if (!cfg.on()) return;
        long now = Util.getMillis();
        float current = animatedgui$currentSlide(now);
        // Add a line to whatever is still sliding, capped so a burst of messages doesn't over-shift.
        animatedgui$slideStart = Math.min(4.0f, current + 1.0f);
        animatedgui$slideStartMs = now;
        animatedgui$slideDurationMs = Math.max(1, cfg.durationMs);
        animatedgui$slideEasing = cfg.easing;
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Matrix3x2fStack;pushMatrix()Lorg/joml/Matrix3x2fStack;",
                    shift = At.Shift.AFTER))
    private void animatedgui$slideChat(
            GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY,
            ChatComponent.DisplayMode displayMode, boolean changeCursorOnInsertions, CallbackInfo ci) {
        if (!AnimConfigManager.get().chat.on()) return;
        float lines = animatedgui$currentSlide(Util.getMillis());
        if (lines <= 1.0e-4f) return;
        float px = lines * getLineHeight() * (float) getScale();
        graphics.pose().translate(0.0f, px);
    }

    @Unique
    private float animatedgui$currentSlide(long now) {
        if (animatedgui$slideStart <= 0.0f) return 0.0f;
        float t = (now - animatedgui$slideStartMs) / (float) animatedgui$slideDurationMs;
        if (t >= 1.0f) return 0.0f;
        if (t < 0.0f) t = 0.0f;
        return animatedgui$slideStart * (1.0f - animatedgui$slideEasing.apply(t));
    }
}
