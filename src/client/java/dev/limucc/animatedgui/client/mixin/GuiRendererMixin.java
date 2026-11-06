package dev.limucc.animatedgui.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.limucc.animatedgui.client.anim.AlphaHolder;
import dev.limucc.animatedgui.client.anim.PipCapture;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Makes item icons fade with a FADE-style screen. Slot items are drawn from a texture atlas through a
 * premultiplied-alpha pipeline with a plain white vertex colour; scaling that colour (all four channels, because the
 * texture is premultiplied) fades the icon. The opacity comes from {@link AlphaHolder} on the item's render state.
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {

    @ModifyConstant(method = "submitBlitFromItemAtlas", constant = @Constant(intValue = -1))
    private int animatedgui$fadeItem(int color, @Local(argsOnly = true) GuiItemRenderState itemState) {
        float a = ((AlphaHolder) (Object) itemState).animatedgui$alpha();
        return a >= 1.0f ? color : PipCapture.premultipliedTint(a);
    }
}
