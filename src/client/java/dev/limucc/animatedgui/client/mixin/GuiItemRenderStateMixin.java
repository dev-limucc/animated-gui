package dev.limucc.animatedgui.client.mixin;

import dev.limucc.animatedgui.client.anim.AlphaHolder;
import dev.limucc.animatedgui.client.anim.ScreenAnimController;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Remembers the opacity an item icon was submitted with. Items are rendered into an atlas and blitted much later,
 * long after the screen's fade has been reset, so the fade has to travel with the item state itself. The field
 * initialiser runs in the constructor, i.e. exactly while the fading screen is extracting its slots.
 */
@Mixin(GuiItemRenderState.class)
public abstract class GuiItemRenderStateMixin implements AlphaHolder {

    @Unique private float animatedgui$alpha = ScreenAnimController.fadeAlpha;

    @Override
    public float animatedgui$alpha() {
        return this.animatedgui$alpha;
    }
}
