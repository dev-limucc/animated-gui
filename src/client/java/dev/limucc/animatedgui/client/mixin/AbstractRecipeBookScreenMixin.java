package dev.limucc.animatedgui.client.mixin;

import dev.limucc.animatedgui.client.anim.InventoryShiftProvider;
import dev.limucc.animatedgui.client.anim.RecipeBookOpenness;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Exposes the recipe book's eased "openness" shift to the screen mixin so the whole inventory — its background
 * panel and its contents together — glides aside when the recipe book opens/closes, instead of teleporting.
 * The screen mixin applies the shift to the entire inventory; the recipe book cancels it out for itself so only
 * the inventory moves.
 */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin implements InventoryShiftProvider {

    @Shadow @Final private RecipeBookComponent<?> recipeBookComponent;

    @Override
    public float animatedgui$screenInvShift() {
        return ((RecipeBookOpenness) (Object) this.recipeBookComponent).animatedgui$invShift();
    }
}
