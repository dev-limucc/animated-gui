package dev.limucc.animatedgui.client.gui;

import dev.limucc.animatedgui.client.anim.ScreenStyle;
import dev.limucc.animatedgui.client.config.AnimConfig;
import dev.limucc.animatedgui.client.config.AnimConfigManager;
import dev.limucc.animatedgui.client.gui.widget.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Flat, dark, Sodium-inspired settings screen (the Limucc UI style) for tuning every animation. One row per
 * feature: enable toggle, optional movement style (menus), easing curve and a -/＋ duration stepper. Drawn by
 * hand and hit-tested in {@link #mouseClicked}; reached from the ModMenu mod list.
 */
public class AnimatedGuiScreen extends Screen {

    private static final int ROWS = 6;
    private static final int HOTBAR_ROW = 3;
    private static final String[] NAMES = {"Chat", "Item move", "Creative scroll", "Hotbar", "Menu open", "Menu close"};
    private static final boolean[] HAS_STYLE = {false, false, false, false, true, true};

    private static final int DUR_MIN = 20;
    private static final int DUR_MAX = 600;
    private static final int DUR_STEP = 10;

    private final Screen parent;
    private int panelLeft, panelRight;
    private final int[] rowY = new int[ROWS];

    private final FlatButton masterBtn = new FlatButton(0, 0, 0, 0, "");
    private final FlatButton doneBtn = new FlatButton(0, 0, 0, 0, "Done");
    private final FlatButton[] toggle = new FlatButton[ROWS];
    private final FlatButton[] style = new FlatButton[ROWS];
    private final FlatButton[] easing = new FlatButton[ROWS];
    private final FlatButton[] minus = new FlatButton[ROWS];
    private final FlatButton[] plus = new FlatButton[ROWS];

    private int nameX, toggleX, styleX, easingX, minusX, valueCenterX, plusX, headerY, masterY;

    public AnimatedGuiScreen(Screen parent) {
        super(Component.literal("Animated GUI"));
        this.parent = parent;
        for (int i = 0; i < ROWS; i++) {
            toggle[i] = new FlatButton(0, 0, 0, 0, "");
            style[i] = new FlatButton(0, 0, 0, 0, "");
            easing[i] = new FlatButton(0, 0, 0, 0, "");
            minus[i] = new FlatButton(0, 0, 0, 0, "-");
            plus[i] = new FlatButton(0, 0, 0, 0, "+");
        }
    }

    private static AnimConfig.Feature feat(int i) {
        AnimConfig c = AnimConfigManager.get();
        return switch (i) {
            case 0 -> c.chat;
            case 1 -> c.items;
            case 2 -> c.creativeScroll;
            case 3 -> c.hotbar;
            case 4 -> c.screenOpen;
            default -> c.screenClose;
        };
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        panelLeft = cx - 205;
        panelRight = cx + 205;

        masterY = 38;
        headerY = 58;
        int firstRow = 70;
        for (int i = 0; i < ROWS; i++) rowY[i] = firstRow + i * 24;

        nameX = panelLeft;
        toggleX = panelLeft + 92;
        styleX = panelLeft + 140;
        easingX = panelLeft + 238;
        minusX = panelLeft + 334;
        valueCenterX = panelLeft + 366;
        plusX = panelLeft + 388;

        masterBtn.setBounds(toggleX, masterY, 60, 18);
        for (int i = 0; i < ROWS; i++) {
            int y = rowY[i];
            toggle[i].setBounds(toggleX, y, 44, 18);
            style[i].setBounds(styleX, y, 92, 18);
            easing[i].setBounds(easingX, y, 92, 18);
            minus[i].setBounds(minusX, y, 16, 18);
            plus[i].setBounds(plusX, y, 16, 18);
        }
        doneBtn.setBounds(cx - 50, this.height - 28, 100, 20);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);

        g.fill(panelLeft - 10, 4, panelRight + 10, this.height - 4, 0xC0121214);
        g.fill(panelLeft - 10, 4, panelRight + 10, 5, 0x22FFFFFF);

        int tw = this.font.width(this.title);
        g.text(this.font, this.title, this.width / 2 - tw / 2, 10, 0xFFFFFFFF);
        String sub = "§7Smooth, fully customizable GUI animations";
        g.text(this.font, sub, this.width / 2 - this.font.width(sub) / 2, 22, 0xFFA0A0A0);

        AnimConfig c = AnimConfigManager.get();

        // Master switch
        g.text(this.font, "Master", nameX, masterY + 5, 0xFFE0E0E0);
        masterBtn.label = c.masterEnabled ? "§aON" : "§7OFF";
        masterBtn.render(g, this.font, mouseX, mouseY, true);
        String hint = c.masterEnabled ? "§8all animations active" : "§8everything frozen to vanilla";
        g.text(this.font, hint, easingX, masterY + 5, 0xFF808080);

        // Column headers
        g.text(this.font, "§8style", styleX, headerY, 0xFF808080);
        g.text(this.font, "§8curve", easingX, headerY, 0xFF808080);
        g.text(this.font, "§8duration", minusX - 4, headerY, 0xFF808080);

        boolean on = c.masterEnabled;
        for (int i = 0; i < ROWS; i++) {
            int y = rowY[i];
            AnimConfig.Feature f = feat(i);

            g.text(this.font, NAMES[i], nameX, y + 5, on ? 0xFFE0E0E0 : 0xFF707075);

            toggle[i].label = f.enabled ? "§aON" : "§7OFF";
            toggle[i].render(g, this.font, mouseX, mouseY, on);

            if (HAS_STYLE[i] && f instanceof AnimConfig.ScreenFeature sf) {
                style[i].label = sf.style.label();
                style[i].render(g, this.font, mouseX, mouseY, on && f.enabled);
            } else if (i == HOTBAR_ROW) {
                style[i].label = c.hotbarTrail ? "§aTrail" : "§7Trail";
                style[i].render(g, this.font, mouseX, mouseY, on && f.enabled);
            }

            easing[i].label = f.easing.label();
            easing[i].render(g, this.font, mouseX, mouseY, on && f.enabled);

            minus[i].render(g, this.font, mouseX, mouseY, on && f.enabled);
            plus[i].render(g, this.font, mouseX, mouseY, on && f.enabled);
            String val = f.durationMs + "ms";
            int vw = this.font.width(val);
            g.text(this.font, val, valueCenterX - vw / 2, y + 5, on && f.enabled ? 0xFFFFFF80 : 0xFF707075);
        }

        doneBtn.render(g, this.font, mouseX, mouseY, true);
        String credit = "§8Animated GUI by dev-limucc";
        g.text(this.font, credit, panelLeft, this.height - 24, 0xFF606066);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;
        double mx = event.x(), my = event.y();

        if (doneBtn.contains(mx, my)) { this.onClose(); return true; }

        AnimConfig c = AnimConfigManager.get();
        if (masterBtn.contains(mx, my)) { c.masterEnabled = !c.masterEnabled; save(); return true; }
        if (!c.masterEnabled) return false;

        for (int i = 0; i < ROWS; i++) {
            AnimConfig.Feature f = feat(i);
            if (toggle[i].contains(mx, my)) { f.enabled = !f.enabled; save(); return true; }
            if (!f.enabled) continue;
            if (HAS_STYLE[i] && f instanceof AnimConfig.ScreenFeature sf && style[i].contains(mx, my)) {
                sf.style = sf.style.next(); save(); return true;
            }
            if (i == HOTBAR_ROW && style[i].contains(mx, my)) {
                c.hotbarTrail = !c.hotbarTrail; save(); return true;
            }
            if (easing[i].contains(mx, my)) { f.easing = f.easing.next(); save(); return true; }
            if (minus[i].contains(mx, my)) { f.durationMs = Math.max(DUR_MIN, f.durationMs - DUR_STEP); save(); return true; }
            if (plus[i].contains(mx, my)) { f.durationMs = Math.min(DUR_MAX, f.durationMs + DUR_STEP); save(); return true; }
        }
        return false;
    }

    private void save() {
        AnimConfigManager.save();
    }

    @Override
    public void onClose() {
        AnimConfigManager.save();
        this.minecraft.setScreen(this.parent);
    }
}
