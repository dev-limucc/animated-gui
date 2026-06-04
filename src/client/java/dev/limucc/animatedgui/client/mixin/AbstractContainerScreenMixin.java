package dev.limucc.animatedgui.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.limucc.animatedgui.client.anim.FlyAnim;
import dev.limucc.animatedgui.client.config.AnimConfig;
import dev.limucc.animatedgui.client.config.AnimConfigManager;
import net.minecraft.util.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Glides item stacks between slots instead of teleporting them. Once per frame we diff every slot against last
 * frame: a slot whose stack shrank/emptied is a "source", one that grew/filled is a "destination". We pair a
 * destination with a same-item source and remember where the item came from, then offset that slot's icon back
 * toward the source and let it ease home.
 *
 * <p>Best-effort by design (shift-clicks, stacking and crafting all read as moves, which looks natural). The
 * creative screen is skipped because its grid contents are reshuffled on scroll, which isn't a real "move".
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Shadow @Final protected AbstractContainerMenu menu;

    @Unique private static final int[] ZERO = {0, 0};
    @Unique private final Map<Slot, ItemStack> animatedgui$prev = new IdentityHashMap<>();
    @Unique private final Map<Slot, FlyAnim> animatedgui$flying = new IdentityHashMap<>();

    @Inject(method = "extractSlots(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V", at = @At("HEAD"))
    private void animatedgui$diffSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        AnimConfig.Feature cfg = AnimConfigManager.get().items;
        if (!cfg.on() || (Object) this instanceof CreativeModeInventoryScreen) {
            if (!animatedgui$flying.isEmpty()) animatedgui$flying.clear();
            return;
        }

        long now = Util.getMillis();
        List<Slot> slots = this.menu.slots;

        // Slots that lost their item this frame become candidate sources for a matching destination.
        List<Slot> sources = new ArrayList<>();
        for (Slot slot : slots) {
            ItemStack prev = animatedgui$prev.get(slot);
            if (prev == null || prev.isEmpty()) continue;
            ItemStack cur = slot.getItem();
            if (cur.isEmpty() || cur.getItem() != prev.getItem() || cur.getCount() < prev.getCount()) {
                sources.add(slot);
            }
        }

        for (Slot slot : slots) {
            ItemStack prev = animatedgui$prev.get(slot);
            ItemStack cur = slot.getItem();
            if (prev == null || cur.isEmpty()) continue;
            boolean arrived = prev.isEmpty() || cur.getItem() != prev.getItem() || cur.getCount() > prev.getCount();
            if (!arrived) continue;

            Slot match = null;
            for (int i = 0; i < sources.size(); i++) {
                ItemStack srcPrev = animatedgui$prev.get(sources.get(i));
                if (srcPrev != null && srcPrev.getItem() == cur.getItem()) {
                    match = sources.remove(i);
                    break;
                }
            }
            if (match != null) {
                animatedgui$flying.put(slot, new FlyAnim(match.x, match.y, now, cfg.durationMs, cfg.easing));
            }
        }

        // Snapshot current contents for next frame's diff.
        for (Slot slot : slots) {
            ItemStack cur = slot.getItem();
            animatedgui$prev.put(slot, cur.isEmpty() ? ItemStack.EMPTY : cur.copy());
        }
    }

    @WrapOperation(
            method = "extractSlot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;III)V"))
    private void animatedgui$flyItem(
            GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int seed,
            Operation<Void> original, @Local(argsOnly = true) Slot slot) {
        int[] off = animatedgui$offset(slot);
        original.call(graphics, stack, x + off[0], y + off[1], seed);
    }

    @WrapOperation(
            method = "extractSlot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fakeItem(Lnet/minecraft/world/item/ItemStack;III)V"))
    private void animatedgui$flyFakeItem(
            GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int seed,
            Operation<Void> original, @Local(argsOnly = true) Slot slot) {
        int[] off = animatedgui$offset(slot);
        original.call(graphics, stack, x + off[0], y + off[1], seed);
    }

    @WrapOperation(
            method = "extractSlot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private void animatedgui$flyDecorations(
            GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y, String count,
            Operation<Void> original, @Local(argsOnly = true) Slot slot) {
        int[] off = animatedgui$offset(slot);
        original.call(graphics, font, stack, x + off[0], y + off[1], count);
    }

    /** Pixel offset {dx,dy} to draw {@code slot}'s item at right now (back toward its source, easing to 0). */
    @Unique
    private int[] animatedgui$offset(Slot slot) {
        FlyAnim fa = animatedgui$flying.get(slot);
        if (fa == null) return ZERO;
        long now = Util.getMillis();
        float k = fa.remaining(now);
        if (k <= 0.0f) {
            animatedgui$flying.remove(slot);
            return ZERO;
        }
        return new int[]{Math.round((fa.fromX - slot.x) * k), Math.round((fa.fromY - slot.y) * k)};
    }
}
