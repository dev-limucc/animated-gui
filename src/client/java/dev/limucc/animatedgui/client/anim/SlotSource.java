package dev.limucc.animatedgui.client.anim;

import net.minecraft.world.item.Item;

/**
 * A slot that just gave up some of an item, remembered for a short window so a destination slot that fills a
 * few frames later (server-authoritative moves like shift-click round-trip through the server) can still be
 * matched back to where the item came from.
 */
public final class SlotSource {

    public final Item item;
    public final int x;
    public final int y;
    public final long time;

    public SlotSource(Item item, int x, int y, long time) {
        this.item = item;
        this.x = x;
        this.y = y;
        this.time = time;
    }
}
