package dev.limucc.animatedgui.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.limucc.animatedgui.AnimatedGui;
import dev.limucc.animatedgui.client.anim.InventoryShiftProvider;
import dev.limucc.animatedgui.client.anim.MenuTransition;
import dev.limucc.animatedgui.client.anim.ScreenAnimController;
import dev.limucc.animatedgui.client.config.AnimConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
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
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Unique private boolean animatedgui$pushed;
    @Unique private static boolean animatedgui$warnedTextureView;

    @Inject(
            method = "extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("RETURN"))
    private void animatedgui$pushAfterDim(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        Screen me = (Screen) (Object) this;
        ScreenAnimController.fadeAlpha = 1.0f;
        int w = graphics.guiWidth(), h = graphics.guiHeight();

        // Menu→menu EXIT: the outgoing menu slides off / shrinks / fades per the chosen transition, over its
        // close timeline (te runs 0→1 as it leaves).
        MenuTransition exit = ScreenAnimController.exitTransition(me);
        if (exit != null) {
            AnimConfig.ScreenFeature cfg = ScreenAnimController.configFor(me);   // menuClose
            float te = cfg.easing.apply(1.0f - ScreenAnimController.rawProgress(me));
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            exit.applyExit(pose, te, w, h);
            ScreenAnimController.fadeAlpha = exit.exitAlpha(te);
            animatedgui$pushed = true;
            ScreenAnimController.transformActive = true;
            return;
        }

        // Menu→menu ENTER: the incoming menu slides in / grows / fades up, over its open timeline (tn 0→1).
        MenuTransition enter = ScreenAnimController.enterTransition(me);
        if (enter != null && ScreenAnimController.rawProgress(me) < 1.0f) {
            AnimConfig.ScreenFeature cfg = ScreenAnimController.configFor(me);   // menuOpen
            float tn = cfg.easing.apply(ScreenAnimController.rawProgress(me));
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            enter.applyEnter(pose, tn, w, h);
            ScreenAnimController.fadeAlpha = enter.enterAlpha(tn);
            animatedgui$pushed = true;
            ScreenAnimController.transformActive = true;
            return;
        }

        float raw = ScreenAnimController.rawProgress(me);
        // Recipe-book screens also slide the whole inventory (panel included) aside as the book opens/closes.
        float invShift = (me instanceof InventoryShiftProvider isp) ? isp.animatedgui$screenInvShift() : 0.0f;
        boolean animating = raw < 1.0f;
        if (!animating && Math.abs(invShift) < 0.01f) {
            animatedgui$pushed = false;
            ScreenAnimController.transformActive = false;
            return;
        }
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        if (animating) {
            AnimConfig.ScreenFeature cfg = ScreenAnimController.configFor(me);
            float p = cfg.easing.apply(raw);
            cfg.style.apply(pose, p, w, h);
            if (cfg.style.fades()) {
                ScreenAnimController.fadeAlpha = Math.max(0.0f, Math.min(1.0f, p));
            }
        }
        if (Math.abs(invShift) >= 0.01f) {
            pose.translate(invShift, 0.0f);
        }
        animatedgui$pushed = true;
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
