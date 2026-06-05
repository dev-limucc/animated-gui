package dev.limucc.animatedgui.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.limucc.animatedgui.client.anim.CreativeScroll;
import dev.limucc.animatedgui.client.anim.FlyAnim;
import dev.limucc.animatedgui.client.anim.SlotSource;
import dev.limucc.animatedgui.client.config.AnimConfig;
import dev.limucc.animatedgui.client.config.AnimConfigManager;
import net.minecraft.util.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
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
 * Two jobs, both at the slot-render level:
 *
 * <ul>
 *   <li><b>Item glide</b> — diff the container each frame; a slot that loses an item is remembered as a
 *       "source" for a short window, and when a slot fills with that same item (even a few frames later, as
 *       happens with server-authoritative shift-clicks) the destination icon is drawn back at the source and
 *       eased home.</li>
 *   <li><b>Creative pixel-slide</b> — when {@link CreativeScroll} is active, the creative grid's items are
 *       nudged up by the sub-row pixel offset (clipped to the grid) and one extra incoming row is drawn at the
 *       bottom, turning the row-paging into a smooth slide.</li>
 * </ul>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Shadow @Final protected AbstractContainerMenu menu;

    @Unique private static final long SOURCE_WINDOW_MS = 400L;

    @Unique private final Map<Slot, ItemStack> animatedgui$prev = new IdentityHashMap<>();
    @Unique private final Map<Slot, FlyAnim> animatedgui$flying = new IdentityHashMap<>();
    @Unique private final List<SlotSource> animatedgui$recentSources = new ArrayList<>();

    @Inject(method = "extractSlots(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V", at = @At("HEAD"))
    private void animatedgui$diffSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        boolean creative = (Object) this instanceof CreativeModeInventoryScreen;

        // Slide the grid cells with the items: the cells are baked into the panel texture, so re-blit the grid
        // region of that texture as a tiled scrolling strip on top of the static one.
        if (creative && CreativeScroll.active && CreativeScroll.gridTexture != null) {
            animatedgui$drawSlidingGrid(graphics);
        }

        AnimConfig.Feature cfg = AnimConfigManager.get().items;
        if (!cfg.on()) {
            if (!animatedgui$flying.isEmpty()) animatedgui$flying.clear();
            if (!animatedgui$recentSources.isEmpty()) animatedgui$recentSources.clear();
            return;
        }

        long now = Util.getMillis();
        animatedgui$recentSources.removeIf(s -> now - s.time > SOURCE_WINDOW_MS);
        List<Slot> slots = this.menu.slots;

        // Pass 1: slots that lost (some of) an item become sources — but skip the creative grid, whose contents
        // reshuffle on scroll (that's not a real move; explicit shift-clicks are recorded in slotClicked).
        for (Slot slot : slots) {
            if (creative && slot.container == CreativeScroll.container) continue;
            ItemStack prev = animatedgui$prev.get(slot);
            if (prev == null || prev.isEmpty()) continue;
            ItemStack cur = slot.getItem();
            if (cur.isEmpty() || cur.getItem() != prev.getItem() || cur.getCount() < prev.getCount()) {
                animatedgui$recentSources.add(new SlotSource(prev.getItem(), slot.x, slot.y, now));
            }
        }

        // Pass 2: a slot that filled (clean move) or grew (stack merge) flies in from the nearest source.
        for (Slot slot : slots) {
            if (creative && slot.container == CreativeScroll.container) continue;
            ItemStack prev = animatedgui$prev.get(slot);
            ItemStack cur = slot.getItem();
            if (prev == null || cur.isEmpty()) continue;
            boolean cleanFill = prev.isEmpty();
            boolean stacked = !prev.isEmpty() && cur.getItem() == prev.getItem() && cur.getCount() > prev.getCount();
            if (!cleanFill && !stacked) continue; // ignore swaps / unchanged slots

            int bestIdx = -1;
            long bestDist = Long.MAX_VALUE;
            for (int i = 0; i < animatedgui$recentSources.size(); i++) {
                SlotSource s = animatedgui$recentSources.get(i);
                if (s.item != cur.getItem()) continue;
                long dx = s.x - slot.x, dy = s.y - slot.y;
                long dist = dx * dx + dy * dy;
                if (dist < bestDist && dist > 0) {
                    bestDist = dist;
                    bestIdx = i;
                }
            }
            if (bestIdx >= 0) {
                SlotSource s = animatedgui$recentSources.remove(bestIdx);
                animatedgui$flying.put(slot, new FlyAnim(s.x, s.y, now, cfg.durationMs, cfg.easing, !cleanFill));
            }
        }

        for (Slot slot : slots) {
            if (creative && slot.container == CreativeScroll.container) continue;
            ItemStack cur = slot.getItem();
            animatedgui$prev.put(slot, cur.isEmpty() ? ItemStack.EMPTY : cur.copy());
        }
    }

    /** Record the clicked slot as a fly source so shift-click (quick-move) animates — including the creative
     *  grid, whose items "leave" without the slot itself emptying. */
    @Inject(method = "slotClicked", at = @At("HEAD"))
    private void animatedgui$onQuickMove(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        if (!AnimConfigManager.get().items.on() || input != ContainerInput.QUICK_MOVE || slot == null) return;
        ItemStack stack = slot.getItem();
        if (!stack.isEmpty()) {
            animatedgui$recentSources.add(new SlotSource(stack.getItem(), slot.x, slot.y, Util.getMillis()));
        }
    }

    /** Re-blit the grid region of the tab texture as a vertical tile, scrolled by the sub-row pixel offset. */
    @Unique
    private void animatedgui$drawSlidingGrid(GuiGraphicsExtractor graphics) {
        int off = CreativeScroll.pixelOffset;
        int w = 9 * CreativeScroll.CELL;
        graphics.enableScissor(CreativeScroll.GRID_LEFT, CreativeScroll.GRID_TOP, CreativeScroll.GRID_RIGHT, CreativeScroll.GRID_BOTTOM);
        for (int k = 0; k <= CreativeScroll.ROWS; k++) {
            int y = CreativeScroll.GRID_TOP - off + k * CreativeScroll.CELL;
            graphics.blit(RenderPipelines.GUI_TEXTURED, CreativeScroll.gridTexture,
                    CreativeScroll.GRID_LEFT, y, (float) CreativeScroll.GRID_LEFT, (float) CreativeScroll.GRID_TOP,
                    w, CreativeScroll.CELL, 256, 256);
        }
        graphics.disableScissor();
    }

    @WrapOperation(
            method = "extractSlot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;III)V"))
    private void animatedgui$flyItem(
            GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int seed,
            Operation<Void> original, @Local(argsOnly = true) Slot slot) {
        if (animatedgui$creativeGrid(slot)) {
            graphics.enableScissor(CreativeScroll.GRID_LEFT, CreativeScroll.GRID_TOP, CreativeScroll.GRID_RIGHT, CreativeScroll.GRID_BOTTOM);
            original.call(graphics, stack, x, y - CreativeScroll.pixelOffset, seed);
            graphics.disableScissor();
            return;
        }
        FlyAnim fa = animatedgui$flyOf(slot);
        if (fa == null) {
            original.call(graphics, stack, x, y, seed);
            return;
        }
        float k = fa.remaining(Util.getMillis());
        int dx = Math.round((fa.fromX - slot.x) * k);
        int dy = Math.round((fa.fromY - slot.y) * k);
        if (fa.merge) {
            original.call(graphics, stack, x, y, seed);          // existing stack stays put
            graphics.item(stack, x + dx, y + dy, seed);          // incoming ghost glides in and merges
        } else {
            original.call(graphics, stack, x + dx, y + dy, seed);
        }
    }

    @WrapOperation(
            method = "extractSlot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fakeItem(Lnet/minecraft/world/item/ItemStack;III)V"))
    private void animatedgui$flyFakeItem(
            GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int seed,
            Operation<Void> original, @Local(argsOnly = true) Slot slot) {
        if (animatedgui$creativeGrid(slot)) {
            graphics.enableScissor(CreativeScroll.GRID_LEFT, CreativeScroll.GRID_TOP, CreativeScroll.GRID_RIGHT, CreativeScroll.GRID_BOTTOM);
            original.call(graphics, stack, x, y - CreativeScroll.pixelOffset, seed);
            graphics.disableScissor();
        } else {
            original.call(graphics, stack, x, y, seed); // recipe-ghost items don't fly
        }
    }

    @WrapOperation(
            method = "extractSlot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private void animatedgui$flyDecorations(
            GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y, String count,
            Operation<Void> original, @Local(argsOnly = true) Slot slot) {
        if (animatedgui$creativeGrid(slot)) {
            graphics.enableScissor(CreativeScroll.GRID_LEFT, CreativeScroll.GRID_TOP, CreativeScroll.GRID_RIGHT, CreativeScroll.GRID_BOTTOM);
            original.call(graphics, font, stack, x, y - CreativeScroll.pixelOffset, count);
            graphics.disableScissor();
            return;
        }
        FlyAnim fa = animatedgui$flyOf(slot);
        if (fa == null || fa.merge) {
            original.call(graphics, font, stack, x, y, count); // merge: count badge stays with the resting stack
            return;
        }
        float k = fa.remaining(Util.getMillis());
        int dx = Math.round((fa.fromX - slot.x) * k);
        int dy = Math.round((fa.fromY - slot.y) * k);
        original.call(graphics, font, stack, x + dx, y + dy, count);
    }

    /** After the grid slots are drawn, slide one extra incoming row up from the bottom (creative only). */
    @Inject(method = "extractSlots(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V", at = @At("TAIL"))
    private void animatedgui$creativeExtraRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (!CreativeScroll.active || !((Object) this instanceof CreativeModeInventoryScreen)) return;
        int extraRow = CreativeScroll.floorRow + CreativeScroll.ROWS;
        int y = CreativeScroll.GRID_TOP + CreativeScroll.ROWS * CreativeScroll.CELL - CreativeScroll.pixelOffset;
        graphics.enableScissor(CreativeScroll.GRID_LEFT, CreativeScroll.GRID_TOP, CreativeScroll.GRID_RIGHT, CreativeScroll.GRID_BOTTOM);
        for (int x = 0; x < 9; x++) {
            ItemStack stack = CreativeScroll.itemAt(extraRow, x);
            if (!stack.isEmpty()) {
                graphics.item(stack, CreativeScroll.GRID_LEFT + x * CreativeScroll.CELL, y);
            }
        }
        graphics.disableScissor();
    }

    @Unique
    private boolean animatedgui$creativeGrid(Slot slot) {
        return CreativeScroll.active
                && (Object) this instanceof CreativeModeInventoryScreen
                && slot.container == CreativeScroll.container;
    }

    /** The slot's live fly animation, or null if none / already landed (expired ones are dropped). */
    @Unique
    private FlyAnim animatedgui$flyOf(Slot slot) {
        FlyAnim fa = animatedgui$flying.get(slot);
        if (fa == null) return null;
        if (fa.remaining(Util.getMillis()) <= 0.0f) {
            animatedgui$flying.remove(slot);
            return null;
        }
        return fa;
    }
}
