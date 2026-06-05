package dev.limucc.animatedgui.client.gui.widget;

import dev.limucc.animatedgui.client.anim.Easing;
import dev.limucc.animatedgui.client.anim.Tween;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/**
 * A little pill switch whose knob slides and whose track colour lerps between off (grey) and on (green) — so
 * every toggle in the settings screen visibly animates, letting the user feel the mod's smoothness on each click.
 */
public class ToggleSwitch {

    public int x, y;
    public final int w = 24;
    public final int h = 12;

    private final Tween knob = new Tween();
    private boolean init;

    private static final int OFF_BG = 0xFF45454F;
    private static final int ON_BG  = 0xFF46B45F;
    private static final int DIS_BG = 0xFF28282E;

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void render(GuiGraphicsExtractor g, boolean state, boolean enabled) {
        long now = Util.getMillis();
        float target = state ? 1.0f : 0.0f;
        if (!init) { knob.snap(target); init = true; }
        knob.retarget(target, now, 160, Easing.EASE_OUT);
        float k = knob.update(now);

        int bg = !enabled ? DIS_BG : lerp(OFF_BG, ON_BG, k);
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + w, y + 1, 0x22FFFFFF);            // top highlight
        g.fill(x, y + h - 1, x + w, y + h, 0x33000000);    // bottom shade

        int ks = h - 4;
        int kx = x + 2 + Math.round(k * (w - ks - 4));
        int kc = enabled ? 0xFFF1F1F4 : 0xFF6A6A70;
        g.fill(kx, y + 2, kx + ks, y + 2 + ks, kc);
        g.fill(kx, y + 2, kx + ks, y + 3, 0x66FFFFFF);
        g.fill(kx, y + 1 + ks, kx + ks, y + 2 + ks, 0x44000000);
    }

    /** Snap to a state with no animation (used when a tab is (re)built so toggles don't slide in from off). */
    public void snapTo(boolean state) {
        knob.snap(state ? 1.0f : 0.0f);
        init = true;
    }

    private static int lerp(int a, int b, float t) {
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24)
                | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8)
                | Math.round(ab + (bb - ab) * t);
    }
}
