# Animated Limucc UI — style library

A reusable GUI style for Fabric mods (Minecraft 26.1.x) by **dev-limucc**. This is the look and feel used by
**Animated GUI**: a flat, Sodium-inspired dark UI with **custom, framerate-independent animations** — sliding
highlights, pill toggles that lerp on/off, and screens that scale/slide/fade in and out.

It has **two layers**, and you can take either on its own:

- **The look** — flat dark translucent panels, no vanilla button textures, accent-blue hover, everything
  hand-drawn in `extractRenderState(...)` and hit-tested in `mouseClicked(...)`.
- **The motion** — every animated value is one retargetable, wall-clock `Tween` run through a named `Easing`
  curve; screens enter/leave via a `ScreenStyle` pose transform. Nothing ever jumps, at any framerate.

> This builds on the static [Limucc UI style](https://github.com/dev-limucc) (flat panels + `FlatButton`) and
> adds the animation system on top. If you only want flat static menus, the look section below is enough.

---

## Using this as a style reference

If you're picking the style up for a **new** mod, you need four files — they're self-contained (they depend only
on `GuiGraphicsExtractor`, `Util.getMillis()`, and each other):

| Copy this | Role |
| --- | --- |
| [`anim/Easing.java`](src/client/java/dev/limucc/animatedgui/client/anim/Easing.java) | The named curves (`Smooth`, `Ease-out`, `Overshoot`, `Bounce`, …). Zero deps. |
| [`anim/Tween.java`](src/client/java/dev/limucc/animatedgui/client/anim/Tween.java) | One retargetable time-based interpolation. **Every** animation is one of these. |
| [`gui/widget/FlatButton.java`](src/client/java/dev/limucc/animatedgui/client/gui/widget/FlatButton.java) | The flat hand-drawn button (struct the screen draws + hit-tests). |
| [`gui/widget/ToggleSwitch.java`](src/client/java/dev/limucc/animatedgui/client/gui/widget/ToggleSwitch.java) | Animated pill switch — knob slides, track colour lerps off↔on. |

Then follow the **screen skeleton** (§6) for the page itself, and reach for `ScreenStyle` / `MenuTransition`
(§7) if you also want screens to animate in and out. Everything in this doc is verbatim from the reference
implementation, so it compiles as-is on 26.1.2.

The full, working reference is [`AnimatedGuiScreen.java`](src/client/java/dev/limucc/animatedgui/client/gui/AnimatedGuiScreen.java)
— a tabbed settings screen that demonstrates the whole kit.

![Settings screen in the Animated Limucc UI style](Gallery/Settings%20UI%20of%20the%20mod.png)

---

## 1. Principles

- **One draw method, no vanilla widgets.** Don't add `Button`/`Checkbox` widgets. Draw the whole screen by hand
  in `extractRenderState(g, mouseX, mouseY, a)` and detect clicks in `mouseClicked(...)`. Total control, perfect
  consistency. (`EditBox` for text entry is the one vanilla widget that blends in fine.)
- **Flat, dark, translucent.** Plain `g.fill(...)` rectangles for a panel, with a 1px **top highlight**
  (`0x22FFFFFF`) and a 1px **bottom shade** (`0x44000000`) for just-enough depth. No gradients, no textures.
- **Accent blue is the signature.** `0xFF3A6EA5` is the hover/selected colour throughout.
- **Every animation is a `Tween`.** Never hand-roll a per-frame counter. Make a `Tween`, `retarget(...)` it when
  the goal changes, `update(now)` it once per frame, draw at `current()`. This is what makes it never jump.
- **Wall-clock time, not ticks/frames.** Drive everything from `Util.getMillis()` so motion is identical at 30
  or 300 FPS. Durations are in milliseconds.
- **Colours are ARGB ints** — `0xAARRGGBB`. Translucency is the alpha byte. Lerp channel-by-channel (see
  `ToggleSwitch.lerp`).
- **Degrade to vanilla cleanly.** Each feature has its own enable toggle and a master switch freezes the lot
  back to instant vanilla — animations are an enhancement, never a dependency.

---

## 2. Palette (exact values)

```text
── panels ──────────────────────────────────────────────
Panel background      0xEE15161B   (settings panel; ~93% opaque near-black)
Panel background alt  0xC0121214   (lighter-weight popups; ~75% opaque)
Panel top edge        0x22FFFFFF   (faint white highlight line)
Sidebar fill          0x40000000   (recessed column behind the tabs)

── accent ──────────────────────────────────────────────
Accent blue           0xFF3A6EA5   (hover / selected — THE signature colour)
Accent fill (faint)   0x553A6EA5   (selected-row wash)
Tab hover wash        0x18FFFFFF

── buttons (FlatButton) ────────────────────────────────
Button idle           0xFF26262B
Button hover          0xFF3A6EA5
Button disabled       0xFF1C1C20
Button top highlight  0x22FFFFFF
Button bottom shade   0x44000000

── toggle (ToggleSwitch) ───────────────────────────────
Track off             0xFF45454F
Track on              0xFF46B45F   (green)
Track disabled        0xFF28282E
Knob                  0xFFF1F1F4
Knob disabled         0xFF6A6A70

── text ────────────────────────────────────────────────
Text normal           0xFFE6E6EA
Text bright/selected  0xFFFFFFFF
Text dim              0xFF74747C
Header label          0xFFB0B0B8   (also use a "§7" prefix)
Note / hint           0xFF606068   (or "§8" inline)
Accent value (yellow) 0xFFFFFF80   (the "current value" readout)
Link text             0xFF9098AA
```

You can also use Minecraft `§` codes inside any drawn string — `§a` green, `§7` gray, `§8` dark gray,
`§e` yellow, `§l` bold, `§r` reset. `g.text(...)` honours them.

---

## 3. The motion engine

### 3.1 `Easing` — the named curves

Curves map normalized progress `t ∈ [0,1]` to an eased `[0,1]` (a couple overshoot past 1 by design). Kept
small and **named in plain language** so the label is what the user cycles through in settings.

```java
public enum Easing {
    LINEAR("Linear")        { @Override public float apply(float t) { return t; } },
    SINE("Smooth")          { @Override public float apply(float t) { return -(float) (Math.cos(Math.PI * t) - 1) / 2f; } },
    EASE_OUT("Ease-out")    { @Override public float apply(float t) { float u = 1 - t; return 1 - u * u * u; } },
    EASE_IN("Ease-in")      { @Override public float apply(float t) { return t * t * t; } },
    EASE_IN_OUT("Ease-in-out") {
        @Override public float apply(float t) {
            return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2f;
        }
    },
    BACK("Overshoot")       {
        @Override public float apply(float t) {
            final float c1 = 1.70158f, c3 = c1 + 1; float u = t - 1;
            return 1 + c3 * u * u * u + c1 * u * u;
        }
    },
    ELASTIC("Elastic")      {
        @Override public float apply(float t) {
            if (t == 0 || t == 1) return t;
            final float c4 = (float) (2 * Math.PI / 3);
            return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1);
        }
    },
    BOUNCE("Bounce")        {
        @Override public float apply(float t) {
            final float n1 = 7.5625f, d1 = 2.75f;
            if (t < 1 / d1)        return n1 * t * t;
            else if (t < 2 / d1)   { t -= 1.5f / d1;  return n1 * t * t + 0.75f; }
            else if (t < 2.5 / d1) { t -= 2.25f / d1; return n1 * t * t + 0.9375f; }
            else                   { t -= 2.625f / d1; return n1 * t * t + 0.984375f; }
        }
    };

    private final String label;
    Easing(String label) { this.label = label; }
    public String label() { return label; }

    public abstract float apply(float t);
    /** apply() with t clamped to [0,1] first. */
    public float clampApply(float t) { return apply(t < 0 ? 0 : (t > 1 ? 1 : t)); }
    public Easing next() { Easing[] v = values(); return v[(ordinal() + 1) % v.length]; }
}
```

Feel guide: `Smooth` is the safe default for HUD glides; `Ease-out` for things arriving; `Ease-in-out` for
open↔close pairs; **`Overshoot`/`Elastic` on a `Scale` open is the satisfying "pop."**

### 3.2 `Tween` — one retargetable interpolation

The heart of the system. Holds a `current` value that glides toward a `target` over a fixed millisecond
duration. **When the target changes mid-flight, `retarget` re-anchors the curve at the current value — so there
is never a jump.** The hotbar selector, chat slide, creative scroll and screen transitions all rely on this.

```java
public final class Tween {
    private float start, end, current;
    private long startMs;
    private int durationMs = 1;
    private Easing easing = Easing.LINEAR;
    private boolean active;

    public Tween() {}
    public Tween(float initial) { snap(initial); }

    /** Jump straight to value with no animation (adopt an external change). */
    public void snap(float value) { this.start = this.end = this.current = value; this.active = false; }

    public float current() { return current; }
    public float start()   { return start; }   // segment origin — handy for motion-blur trails
    public boolean isActive() { return active; }

    /** Aim at target; if it differs from where we're heading, start a fresh eased segment. */
    public void retarget(float target, long now, int durationMs, Easing easing) {
        if (active && Math.abs(target - end) < 1.0e-4f) return;
        if (!active && Math.abs(target - current) < 1.0e-4f) { this.end = target; return; }
        this.start = current; this.end = target; this.startMs = now;
        this.durationMs = Math.max(1, durationMs); this.easing = easing; this.active = true;
    }

    /** Advance to now and return the current value. */
    public float update(long now) {
        if (!active) return current;
        float t = (now - startMs) / (float) durationMs;
        if (t >= 1.0f)      { current = end; active = false; }
        else if (t > 0.0f)  { current = start + (end - start) * easing.apply(t); }
        return current;
    }
}
```

The per-frame ritual is always the same:

```java
long now = Util.getMillis();
tween.retarget(goalValue, now, durationMs, Easing.EASE_OUT); // cheap to call every frame
float v = tween.update(now);                                 // draw at v
```

For one-shot timelines that don't need retargeting (e.g. "this slide started N ms ago"), it's fine to compute
progress inline instead — see the content-slide in §6:
`float t = (now - startMs) / durationMs; float p = Easing.EASE_OUT.clampApply(t);`

---

## 4. The `FlatButton` kit (copy verbatim)

Not a real widget — a struct the owning screen draws and hit-tests. Dark idle, accent-blue on hover, 1px top
highlight + bottom shade.

```java
public class FlatButton {
    public int x, y, w, h;
    public String label;

    private static final int BG       = 0xFF26262B;
    private static final int BG_HOVER = 0xFF3A6EA5; // accent blue
    private static final int BG_OFF   = 0xFF1C1C20;
    private static final int TEXT     = 0xFFFFFFFF;
    private static final int TEXT_OFF = 0xFF6A6A70;

    public FlatButton(int x, int y, int w, int h, String label) {
        this.x = x; this.y = y; this.w = w; this.h = h; this.label = label;
    }
    public void setBounds(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }
    public boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    public void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, boolean enabled) {
        boolean hovered = enabled && contains(mouseX, mouseY);
        int bg = !enabled ? BG_OFF : (hovered ? BG_HOVER : BG);
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + w, y + 1, 0x22FFFFFF);          // top highlight
        g.fill(x, y + h - 1, x + w, y + h, 0x44000000);  // bottom shade
        int tw = font.width(label);
        g.text(font, label, x + (w - tw) / 2, y + (h - 8) / 2, enabled ? TEXT : TEXT_OFF);
    }
}
```

Use it for cycle-buttons too: set `label` to the current enum's `.label()` each frame and advance the enum with
`.next()` on click (that's how easing curve / style / transition are picked).

---

## 5. The `ToggleSwitch` kit (copy verbatim)

A pill switch whose knob **slides** and whose track colour **lerps** between grey (off) and green (on) — so every
toggle visibly animates and the user feels the smoothness on each click.

```java
public class ToggleSwitch {
    public int x, y;
    public final int w = 24, h = 12;

    private final Tween knob = new Tween();
    private boolean init;

    private static final int OFF_BG = 0xFF45454F;
    private static final int ON_BG  = 0xFF46B45F;
    private static final int DIS_BG = 0xFF28282E;

    public void setPosition(int x, int y) { this.x = x; this.y = y; }
    public boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }

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

    /** Snap to a state with no animation (use when a tab is (re)built so toggles don't slide in from off). */
    public void snapTo(boolean state) { knob.snap(state ? 1.0f : 0.0f); init = true; }

    private static int lerp(int a, int b, float t) {
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
             | (Math.round(ag + (bg - ag) * t) << 8)  |  Math.round(ab + (bb - ab) * t);
    }
}
```

That channel-by-channel `lerp(argb, argb, t)` is the reusable way to animate **any** colour in this style.

---

## 6. Screen skeleton

The canonical page: a centred flat panel, a left **sidebar of tabs with a sliding accent highlight**, and a
right content column whose rows **slide in** on each tab change. Hand-drawn, hit-tested. (Full version:
[`AnimatedGuiScreen.java`](src/client/java/dev/limucc/animatedgui/client/gui/AnimatedGuiScreen.java).)

```java
public class MyScreen extends Screen {
    private static final int PANEL = 0xEE15161B, EDGE = 0x22FFFFFF, SIDEBAR = 0x40000000;
    private static final int ACCENT = 0xFF3A6EA5, TXT = 0xFFE6E6EA, HEAD = 0xFFB0B0B8;

    private int panelLeft, panelRight, panelTop, panelBottom, sidebarX, sidebarW, contentLeft, tabTop;
    private int tab = 0;
    private long tabSwitchMs = -100000L;        // far in the past = "no slide in progress"
    private final Tween tabHighlight = new Tween();
    private boolean tabHighlightInit;

    @Override protected void init() {            // recompute layout from this.width/this.height
        int cx = width / 2, cy = height / 2;
        panelLeft = cx - 240; panelRight = cx + 240;
        panelTop = Math.max(8, cy - 120); panelBottom = Math.min(height - 8, cy + 120);
        sidebarX = panelLeft + 12; sidebarW = 92;
        contentLeft = panelLeft + 118; tabTop = panelTop + 40;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        long now = Util.getMillis();

        g.fill(panelLeft, panelTop, panelRight, panelBottom, PANEL);          // panel
        g.fill(panelLeft, panelTop, panelRight, panelTop + 1, EDGE);          // top edge
        g.fill(sidebarX - 4, tabTop - 6, sidebarX + sidebarW + 4, panelBottom - 8, SIDEBAR);

        // ── sidebar: a single accent bar that GLIDES to the selected tab ──
        int selectedY = tabTop + tab * 30;
        if (!tabHighlightInit) { tabHighlight.snap(selectedY); tabHighlightInit = true; }
        tabHighlight.retarget(selectedY, now, 220, Easing.EASE_OUT);
        int hy = Math.round(tabHighlight.update(now));
        g.fill(sidebarX - 4, hy, sidebarX - 2, hy + 24, ACCENT);              // accent edge
        g.fill(sidebarX, hy, sidebarX + sidebarW, hy + 24, 0x553A6EA5);       // faint fill
        // ... draw tab labels, hover wash on the hovered (non-selected) tab ...

        // ── content slides in 22px from the left over 200ms on each tab change ──
        float t = (now - tabSwitchMs) / 200.0f;
        float slide = (t >= 0 && t < 1) ? (1.0f - Easing.EASE_OUT.clampApply(t)) * 22.0f : 0.0f;
        g.enableScissor(contentLeft - 4, tabTop - 6, panelRight - 8, panelBottom - 30);
        g.pose().pushMatrix();
        g.pose().translate(slide, 0.0f);
        drawContent(g, mouseX, mouseY);
        g.pose().popMatrix();
        g.disableScissor();
    }

    private void selectTab(int t) { if (t != tab) { tab = t; tabSwitchMs = Util.getMillis(); /* rebuild rows */ } }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;
        double mx = event.x(), my = event.y();
        // hit-test tabs / toggles / buttons here, mutate state, return true if handled
        return false;
    }
}
```

Two patterns worth lifting:

- **Sliding selection highlight** — one `Tween` chasing `selectedY`; the bar glides between tabs instead of
  snapping. Reuse for any "selected item" indicator.
- **Slide-in content** — stamp `tabSwitchMs = now` on change, derive a decaying offset from elapsed time, wrap
  the content draw in a `pose().translate(...)` inside a scissor so it clips to the panel.

A "row" in the settings screen bundles a label + `ToggleSwitch` + cycle-`FlatButton`s for easing/duration/style
— see the `Row` inner class in the reference for the full layout maths.

---

## 7. Screens that animate in and out

To make whole screens enter/leave (not just widgets inside one), two enums do the work. Each is a pose transform
parameterized by progress; opening plays progress 0→1, closing 1→0, so one transform serves both directions. The
pose is a `Matrix3x2f` (26.1.2's 2D matrix stack).

### `ScreenStyle` — how a single screen enters/leaves

`Scale` · `Fade` · `Slide up` · `Slide down` · `Slide left` · `Slide right`
([`ScreenStyle.java`](src/client/java/dev/limucc/animatedgui/client/anim/ScreenStyle.java))

```java
SCALE.apply(pose, p, w, h);   // p=1 fully shown (identity), p→0 hidden; scales about screen centre
// FADE barely scales (0.96→1.0) and drops opacity instead (fades() == true)
// SLIDE_* translate by (1-p) * dimension * 0.85
```

Because `p` is the **already-eased** value, picking `Overshoot`/`Elastic` makes `Scale` overshoot into a pop.

### `MenuTransition` — navigating between two menus

`Slide` (carousel) · `Zoom` · `Fade` · `Swap`
([`MenuTransition.java`](src/client/java/dev/limucc/animatedgui/client/anim/MenuTransition.java))

Every style is **single-screen**: the outgoing menu plays its exit, then — over the same never-blanking backdrop
— the incoming menu plays its enter. Only one screen renders at a time, so there's no ghosting and nothing
fragile to crash in a heavily-modded GUI pipeline. `applyExit/exitAlpha` are driven by the close timeline,
`applyEnter/enterAlpha` by the open timeline.

---

## 8. Do / Don't

| Do | Don't |
| --- | --- |
| Make every animated value a `Tween`; `retarget` it freely each frame. | Hand-roll per-frame `+= speed` counters — they jump on retarget and vary with FPS. |
| Drive time from `Util.getMillis()`; durations in ms. | Tie motion to tick count or frame count. |
| Draw + hit-test by hand in `extractRenderState` / `mouseClicked`. | Mix in vanilla `Button`/`Checkbox` widgets (except `EditBox`). |
| `snap(...)` / `snapTo(...)` when adopting an external/rebuilt state so it doesn't slide in from zero. | Let toggles animate from off every time a tab is rebuilt. |
| Keep curves named in plain language; cycle them with `.next()`. | Expose raw cubic-bezier params to the user. |
| Give each feature its own toggle + a master switch that restores vanilla. | Make the animation a hard dependency with no off-ramp. |
| Clip slide-in content with `enableScissor` so it never bleeds past the panel. | Translate content without scissoring — it spills outside the frame. |

---

## 9. 26.1.2 draw-API cheatsheet

The render arg is a `GuiGraphicsExtractor g` (MC 26.1.2's deferred `extractRenderState` pipeline):

```java
g.fill(x0, y0, x1, y1, argb);              // filled rect; this is 90% of the UI
g.text(font, "string", x, y, argb);         // text; honours § colour codes; font = this.font
font.width("string");                       // measure for centring
g.item(stack, x, y);                         // item/block icon (3D blocks, 2D items) — for item lists
g.enableScissor(x0, y0, x1, y1);  …  g.disableScissor();   // clip region
g.pose().pushMatrix(); g.pose().translate(dx, dy); g.pose().scale(s, s); … g.pose().popMatrix();
// pose() is a Matrix3x2f stack; ScreenStyle/MenuTransition mutate it directly for screen transforms
Util.getMillis();                            // wall-clock ms — the time source for every Tween
```

---

## File map

**Portable kit** (copy into any mod — self-contained):
```
anim/Easing.java            named curves
anim/Tween.java             retargetable interpolation
anim/ScreenStyle.java       per-screen enter/leave transforms
anim/MenuTransition.java    menu→menu navigation transforms
gui/widget/FlatButton.java  flat hand-drawn button
gui/widget/ToggleSwitch.java animated pill switch
```

**Reference wiring** (read for patterns; mod-specific — not meant to copy wholesale):
```
gui/AnimatedGuiScreen.java  the tabbed settings screen (full skeleton + Row layout)
config/AnimConfig*.java      per-feature enable/duration/easing/style, serialized to JSON
anim/FlyAnim.java            item slot→slot glide model
mixin/*.java                 how each feature hooks the vanilla render pipeline
```

---

MIT © dev-limucc — reuse freely.
