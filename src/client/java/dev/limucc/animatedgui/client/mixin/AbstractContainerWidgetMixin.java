package dev.limucc.animatedgui.client.mixin;

import dev.limucc.animatedgui.client.anim.ListScrollHit;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hit-testing for smooth-scrolling lists: {@code AbstractSelectionList} does not declare {@code mouseClicked}
 * itself (its rows are child widgets handled here), so this is the hook where a click can be made to agree with the
 * smoothed scroll position that is actually drawn. Anything that isn't a selection list is untouched.
 */
@Mixin(AbstractContainerWidget.class)
public abstract class AbstractContainerWidgetMixin {

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"))
    private void animatedgui$hitBegin(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ListScrollHit hit) hit.animatedgui$hitBegin();
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("RETURN"))
    private void animatedgui$hitEnd(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ListScrollHit hit) hit.animatedgui$hitEnd();
    }
}
