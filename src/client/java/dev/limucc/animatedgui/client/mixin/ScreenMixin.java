package dev.limucc.animatedgui.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.limucc.animatedgui.AnimatedGui;
import dev.limucc.animatedgui.client.anim.GfxAccess;
import dev.limucc.animatedgui.client.anim.InventoryShiftProvider;
import dev.limucc.animatedgui.client.anim.MenuTransition;
import dev.limucc.animatedgui.client.anim.ScreenAnimController;
import dev.limucc.animatedgui.client.anim.ScreenStyle;
import dev.limucc.animatedgui.client.config.AnimConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Open/close screen animation. The whole menu — its background panel AND its contents — animates as one unit,
 * while the screen-dim stays put.
 *
 * <p>The trick: a container screen draws its dim first (via {@code super.extractBackground}) and then its
 * panel PNG. So we push the eased pose transform at the <em>tail</em> of {@code Screen.extractBackground}
 * (after the dim, before the panel) and pop it right after the content render. Because the GUI pipeline
 * snapshots the pose per draw and {@code nextStratum()} doesn't touch it, that single push cleanly covers the
 * panel, slots, items, labels and buttons — everything except the dim and tooltips.
 *
 * <p>Menu→menu navigation reuses the same single push: the outgoing menu plays the chosen {@link MenuTransition}
 * exit (over its close timeline), then — after the deferred swap — the incoming menu plays the matching enter
 * (over its open timeline). Only one screen ever renders, over a backdrop both draw identically, so the panel
 * hands off without a flash and there's nothing fragile to crash.
 *
 * <p>Input stays truthful while a transform is on screen: the transform is published to
 * {@link ScreenAnimController} and the mouse is mapped through its inverse (see {@code MouseHandlerMixin}), and
 * tooltips are dropped mid-animation (they would hang off a moving slot).
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Unique private boolean animatedgui$pushed;
    /** The screen is mid open/close/transition (as opposed to merely shifted aside by the recipe book). */
    @Unique private boolean animatedgui$animating;
    @Unique private static boolean animatedgui$warnedTextureView;

    /**
     * The dim behind an inventory-style screen follows the screen's open/close progress: while it is drawn, the
     * opacity funnel in {@code GuiGraphicsExtractorMixin} (fill / gradient) scales it by the backdrop factor.
     */
    @Inject(method = "extractTransparentBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"))
    private void animatedgui$dimIn(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        ScreenAnimController.fadeAlpha = ScreenAnimController.backdropFactor((Screen) (Object) this);
    }

    @Inject(method = "extractTransparentBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("RETURN"))
    private void animatedgui$dimOut(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        ScreenAnimController.fadeAlpha = 1.0f;
    }

    /** The blurred menu background: tell the game renderer how strong this frame's blur should be. */
    @Inject(method = "extractBlurredBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"))
    private void animatedgui$blurFactor(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        ScreenAnimController.blurFactor = ScreenAnimController.backdropFactor((Screen) (Object) this);
    }

    /** Set when the transform was pushed from inside {@code Screen.extractBackground} this frame. */
    @Unique private boolean animatedgui$applied;

    @Inject(
            method = "extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("RETURN"))
    private void animatedgui$pushAfterDim(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        animatedgui$applied = true;
        animatedgui$apply(graphics);
    }

    /**
     * Some screens (the pause menu) override {@code extractBackground} without calling {@code super}, so the hook
     * above never fires for them. This wrapper notices that and pushes the transform right after their background
     * instead — their dim/blur stays put and everything drawn afterwards animates, same as for the rest.
     */
    @WrapOperation(
            method = "extractRenderStateWithTooltipAndSubtitles",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void animatedgui$backgroundFallback(
            Screen self, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, Operation<Void> original) {
        animatedgui$applied = false;
        original.call(self, graphics, mouseX, mouseY, a);
        if (!animatedgui$applied) {
            animatedgui$applied = true;
            animatedgui$apply(graphics);
        }
    }

    @Unique
    private void animatedgui$apply(GuiGraphicsExtractor graphics) {
        Screen me = (Screen) (Object) this;
        ScreenAnimController.fadeAlpha = 1.0f;
        ScreenAnimController.beginFrame(me); // once per rendered frame: advances the open clock
        animatedgui$animating = false;
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        Matrix3x2f m = ScreenAnimController.scratch;

        // Menu→menu EXIT: the outgoing menu slides off / shrinks / fades per the chosen transition, over its
        // close timeline (te runs 0→1 as it leaves).
        MenuTransition exit = ScreenAnimController.exitTransition(me);
        if (exit != null) {
            AnimConfig.ScreenFeature cfg = ScreenAnimController.configFor(me);   // menuClose
            float te = cfg.effEasing().apply(1.0f - ScreenAnimController.rawProgress(me));
            m.identity();
            exit.applyExit(m, te, w, h);
            animatedgui$push(graphics, me, m);
            ScreenAnimController.fadeAlpha = exit.exitAlpha(te);
            return;
        }

        // Menu→menu ENTER: the incoming menu slides in / grows / fades up, over its open timeline (tn 0→1).
        MenuTransition enter = ScreenAnimController.enterTransition(me);
        if (enter != null && ScreenAnimController.rawProgress(me) < 1.0f) {
            AnimConfig.ScreenFeature cfg = ScreenAnimController.configFor(me);   // menuOpen
            float tn = cfg.effEasing().apply(ScreenAnimController.rawProgress(me));
            m.identity();
            enter.applyEnter(m, tn, w, h);
            animatedgui$push(graphics, me, m);
            ScreenAnimController.fadeAlpha = enter.enterAlpha(tn);
            return;
        }

        float raw = ScreenAnimController.rawProgress(me);
        // Recipe-book screens also slide the whole inventory (panel included) aside as the book opens/closes.
        float invShift = (me instanceof InventoryShiftProvider isp) ? isp.animatedgui$screenInvShift() : 0.0f;
        boolean animating = raw < 1.0f;
        if (!animating && Math.abs(invShift) < 0.01f) {
            animatedgui$pushed = false;
            ScreenAnimController.transformActive = false;
            ScreenAnimController.clearTransform();
            return;
        }
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        if (animating) {
            AnimConfig.ScreenFeature cfg = ScreenAnimController.configFor(me);
            float p = cfg.effEasing().apply(raw);
            ScreenStyle style = cfg.effStyle();
            m.identity();
            style.apply(m, p, w, h);
            pose.mul(m);
            ScreenAnimController.publishTransform(me, m);
            animatedgui$animating = true;
            if (style.fades()) {
                ScreenAnimController.fadeAlpha = Math.max(0.0f, Math.min(1.0f, p));
            }
        } else {
            ScreenAnimController.clearTransform();
        }
        if (Math.abs(invShift) >= 0.01f) {
            pose.translate(invShift, 0.0f);
        }
        animatedgui$pushed = true;
        ScreenAnimController.transformActive = true;
    }

    /** Push {@code m} (the already-built menu-transition transform) and publish it for mouse mapping. */
    @Unique
    private void animatedgui$push(GuiGraphicsExtractor graphics, Screen me, Matrix3x2f m) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.mul(m);
        ScreenAnimController.publishTransform(me, m);
        animatedgui$pushed = true;
        animatedgui$animating = true;
        ScreenAnimController.transformActive = true;
    }

    @WrapOperation(
            method = "extractRenderStateWithTooltipAndSubtitles",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void animatedgui$popAfterContent(
            Screen self, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, Operation<Void> original) {
        try {
            original.call(self, graphics, mouseX, mouseY, a);
        } catch (RuntimeException e) {
            // Stability net: in heavily-modded GUI pipelines (ModMenu icons, OptiGUI live texture swaps,
            // ImmediatelyFast batching) a texture's GPU view can be momentarily unready — typically for a single
            // frame right after a resource reload. That throws deep in the screen's own render, with our wrapper
            // merely on the stack. Rather than let a one-frame texture race become a hard crash, skip this frame's
            // content; it draws fine on the next. Anything that isn't that known-transient case still propagates.
            if (!animatedgui$isTransientTextureView(e)) throw e;
            if (!animatedgui$warnedTextureView) {
                animatedgui$warnedTextureView = true;
                AnimatedGui.LOGGER.warn("[Animated GUI] Skipped a frame whose GUI texture view wasn't ready yet "
                        + "(typically a ModMenu/OptiGUI icon right after a resource reload). Suppressing further "
                        + "warnings.", e);
            }
        } finally {
            if (animatedgui$pushed) {
                graphics.pose().popMatrix();
                animatedgui$pushed = false;
            }
            if (animatedgui$animating) {
                // A tooltip queued mid-animation would be drawn at the (mapped) mouse position, not under the
                // real cursor, and would hang off a moving slot. It's back the frame the animation settles.
                ((GfxAccess) (Object) graphics).animatedgui$clearTooltip();
                animatedgui$animating = false;
            }
            ScreenAnimController.transformActive = false;
            ScreenAnimController.fadeAlpha = 1.0f;
        }
    }

    /** True for the transient "texture view doesn't exist yet" condition (and only that). */
    @Unique
    private static boolean animatedgui$isTransientTextureView(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof IllegalStateException) {
                String m = c.getMessage();
                if (m != null && m.contains("Texture view")) return true;
            }
            if (c.getCause() == c) break;
        }
        return false;
    }
}
