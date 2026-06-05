package dev.limucc.animatedgui.client.gui;

import dev.limucc.animatedgui.client.anim.Easing;
import dev.limucc.animatedgui.client.anim.Tween;
import dev.limucc.animatedgui.client.config.AnimConfig;
import dev.limucc.animatedgui.client.config.AnimConfigManager;
import dev.limucc.animatedgui.client.gui.widget.FlatButton;
import dev.limucc.animatedgui.client.gui.widget.ToggleSwitch;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Tabbed, animated settings screen in the Limucc flat style. A left sidebar (General / Inventory / Game menu /
 * Chat) switches the right-hand content, which slides in on each tab change; every toggle is an animated pill
 * switch — so the screen itself demonstrates the smoothness it adds to the rest of the game.
 */
public class AnimatedGuiScreen extends Screen {

    private enum Tab { GENERAL, INVENTORY, GAME_MENU, CHAT }

    private static final String[] TAB_NAMES = {"General", "Inventory", "Game menu", "Chat"};

    private static final int DUR_MIN = 20, DUR_MAX = 600, DUR_STEP = 10;

    // palette
    private static final int PANEL = 0xEE15161B, EDGE = 0x22FFFFFF, SIDEBAR = 0x40000000;
    private static final int TAB_HOVER = 0x18FFFFFF, ACCENT = 0xFF3A6EA5;
    private static final int TXT = 0xFFE6E6EA, TXT_DIM = 0xFF74747C, HEAD = 0xFFB0B0B8;

    private final Screen parent;

    private int panelLeft, panelRight, panelTop, panelBottom;
    private int sidebarX, sidebarW, contentLeft, contentRight, tabTop, contentTop;

    private Tab tab = Tab.GENERAL;
    private long tabSwitchMs = -100000L;
    private final Tween tabHighlight = new Tween();
    private boolean tabHighlightInit;

    private final FlatButton doneBtn = new FlatButton(0, 0, 0, 0, "Done");
    private final FlatButton transitionBtn = new FlatButton(0, 0, 0, 0, "");
    private List<Row> rows = new ArrayList<>();

    public AnimatedGuiScreen(Screen parent) {
        super(Component.literal("Animated GUI"));
        this.parent = parent;
    }

    /** One configurable line: a toggle, and (for real features) easing / duration / optional style. */
    private final class Row {
        final String name;
        final AnimConfig.Feature feat;   // null for plain boolean rows (master / hotbar trail)
        final boolean hasStyle;
        final BooleanSupplier get;
        final Consumer<Boolean> set;
        final ToggleSwitch toggle = new ToggleSwitch();
        final FlatButton easing = new FlatButton(0, 0, 0, 0, "");
        final FlatButton minus = new FlatButton(0, 0, 0, 0, "-");
        final FlatButton plus = new FlatButton(0, 0, 0, 0, "+");
        final FlatButton style = new FlatButton(0, 0, 0, 0, "");
        int y;

        Row(String name, AnimConfig.Feature feat, boolean hasStyle, BooleanSupplier get, Consumer<Boolean> set) {
            this.name = name; this.feat = feat; this.hasStyle = hasStyle; this.get = get; this.set = set;
        }
    }

    private Row featRow(String name, AnimConfig.Feature f, boolean hasStyle) {
        return new Row(name, f, hasStyle, () -> f.enabled, v -> f.enabled = v);
    }

    private Row boolRow(String name, BooleanSupplier get, Consumer<Boolean> set) {
        return new Row(name, null, false, get, set);
    }

    @Override
    protected void init() {
        int cx = this.width / 2, cy = this.height / 2;
        panelLeft = cx - 240; panelRight = cx + 240;
        panelTop = Math.max(8, cy - 120); panelBottom = Math.min(this.height - 8, cy + 120);
        sidebarX = panelLeft + 12; sidebarW = 92;
        contentLeft = panelLeft + 118; contentRight = panelRight - 14;
        tabTop = panelTop + 40;
        contentTop = panelTop + 42;
        doneBtn.setBounds(panelRight - 92, panelBottom - 26, 78, 20);
        rebuildRows();
    }

    private void rebuildRows() {
        AnimConfig c = AnimConfigManager.get();
        rows = new ArrayList<>();
        switch (tab) {
            case GENERAL -> {
                rows.add(boolRow("Master", () -> c.masterEnabled, v -> c.masterEnabled = v));
                rows.add(featRow("Hotbar", c.hotbar, false));
                rows.add(boolRow("Trail", () -> c.hotbarTrail, v -> c.hotbarTrail = v));
                rows.add(featRow("List scroll", c.listScroll, false));
            }
            case INVENTORY -> {
                rows.add(featRow("Items", c.items, false));
                rows.add(featRow("Creative", c.creativeScroll, false));
                rows.add(featRow("Recipe book", c.recipeBook, false));
                rows.add(featRow("Inv open", c.inventoryOpen, true));
                rows.add(featRow("Inv close", c.inventoryClose, true));
            }
            case GAME_MENU -> {
                rows.add(featRow("Menu open", c.menuOpen, true));
                rows.add(featRow("Menu close", c.menuClose, true));
            }
            case CHAT -> rows.add(featRow("Chat", c.chat, false));
        }
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int y = contentTop + i * 26;
            r.y = y;
            r.toggle.setPosition(contentLeft + 88, y + 2);
            r.toggle.snapTo(r.get.getAsBoolean());
            r.easing.setBounds(contentLeft + 116, y + 1, 64, 14);
            r.minus.setBounds(contentLeft + 184, y + 1, 14, 14);
            r.plus.setBounds(contentLeft + 234, y + 1, 14, 14);
            r.style.setBounds(contentLeft + 252, y + 1, 88, 14);
        }
    }

    private void selectTab(Tab t) {
        if (t == tab) return;
        tab = t;
        tabSwitchMs = Util.getMillis();
        rebuildRows();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        AnimConfig c = AnimConfigManager.get();

        g.fill(panelLeft, panelTop, panelRight, panelBottom, PANEL);
        g.fill(panelLeft, panelTop, panelRight, panelTop + 1, EDGE);
        g.fill(sidebarX - 4, tabTop - 6, sidebarX + sidebarW + 4, panelBottom - 8, SIDEBAR);

        g.text(this.font, "§lAnimated GUI", panelLeft + 12, panelTop + 12, TXT);
        g.text(this.font, "§7settings", panelLeft + 12, panelTop + 24, TXT_DIM);

        // ── sidebar tabs with a sliding accent highlight ──
        long now = Util.getMillis();
        int selectedY = tabTop + tab.ordinal() * 30;
        if (!tabHighlightInit) { tabHighlight.snap(selectedY); tabHighlightInit = true; }
        tabHighlight.retarget(selectedY, now, 220, Easing.EASE_OUT);
        int hy = Math.round(tabHighlight.update(now));
        g.fill(sidebarX - 4, hy, sidebarX - 2, hy + 24, ACCENT);          // accent bar
        g.fill(sidebarX, hy, sidebarX + sidebarW, hy + 24, 0x553A6EA5);   // selected fill (faint accent)

        for (int i = 0; i < TAB_NAMES.length; i++) {
            int ty = tabTop + i * 30;
            boolean hov = mouseX >= sidebarX && mouseX <= sidebarX + sidebarW && mouseY >= ty && mouseY < ty + 24;
            if (hov && i != tab.ordinal()) g.fill(sidebarX, ty, sidebarX + sidebarW, ty + 24, TAB_HOVER);
            int col = i == tab.ordinal() ? 0xFFFFFFFF : (hov ? TXT : HEAD);
            g.text(this.font, TAB_NAMES[i], sidebarX + 8, ty + 8, col);
        }

        // ── content (slides in on tab change) ──
        float t = (now - tabSwitchMs) / 200.0f;
        float slide = (t >= 0 && t < 1) ? (1.0f - Easing.EASE_OUT.clampApply(t)) * 22.0f : 0.0f;
        g.enableScissor(contentLeft - 4, tabTop - 6, contentRight + 6, panelBottom - 30);
        g.pose().pushMatrix();
        g.pose().translate(slide, 0.0f);
        extractContent(g, mouseX, mouseY, c);
        g.pose().popMatrix();
        g.disableScissor();

        doneBtn.render(g, this.font, mouseX, mouseY, true);
    }

    private void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, AnimConfig c) {
        boolean master = c.masterEnabled;
        for (Row r : rows) {
            boolean isMaster = r.feat == null && "Master".equals(r.name);
            boolean state = r.get.getAsBoolean();
            boolean active = isMaster || master;                 // master row always active
            boolean settings = r.feat != null && master && r.feat.enabled;

            g.text(this.font, r.name, contentLeft, r.y + 4, active ? TXT : TXT_DIM);
            r.toggle.render(g, state, active);

            if (r.feat != null) {
                r.easing.label = r.feat.easing.label();
                r.easing.render(g, this.font, mouseX, mouseY, settings);
                r.minus.render(g, this.font, mouseX, mouseY, settings);
                r.plus.render(g, this.font, mouseX, mouseY, settings);
                String val = r.feat.durationMs + "ms";
                int vw = this.font.width(val);
                g.text(this.font, val, contentLeft + 216 - vw / 2, r.y + 4, settings ? 0xFFFFFF80 : TXT_DIM);
                if (r.hasStyle && r.feat instanceof AnimConfig.ScreenFeature sf) {
                    r.style.label = sf.style.label();
                    r.style.render(g, this.font, mouseX, mouseY, settings);
                }
            }
        }

        int afterRows = contentTop + rows.size() * 26;
        int infoY = afterRows + 10;
        if (tab == Tab.GAME_MENU) {
            // How navigating between menus animates (Pause→Options, a sub-screen's Back, …) — selectable.
            boolean active = c.masterEnabled;
            g.text(this.font, "Transition", contentLeft, afterRows + 4, active ? TXT : TXT_DIM);
            transitionBtn.label = c.menuTransition.label();
            transitionBtn.setBounds(contentLeft + 88, afterRows + 1, 110, 14);
            transitionBtn.render(g, this.font, mouseX, mouseY, active);
            infoY = afterRows + 26 + 10;
        }
        if (tab == Tab.GENERAL) {
            g.text(this.font, "§7Smooth, customizable animations for Minecraft's", contentLeft, infoY, TXT_DIM);
            g.text(this.font, "§7instant GUI. Master freezes everything to vanilla.", contentLeft, infoY + 11, TXT_DIM);
            g.text(this.font, "§fMade by §edev-limucc", contentLeft, infoY + 28, TXT);
            g.text(this.font, "§b> GitHub§7   github.com/dev-limucc/animated-gui", contentLeft, infoY + 41, 0xFF9098AA);
            g.text(this.font, "§a> Modrinth§7 modrinth.com/mod/animated-gui", contentLeft, infoY + 53, 0xFF9098AA);
        } else if (tab == Tab.CHAT) {
            g.text(this.font, "§7New chat messages slide up smoothly as they", contentLeft, infoY, TXT_DIM);
            g.text(this.font, "§7arrive, instead of popping in instantly.", contentLeft, infoY + 11, TXT_DIM);
        } else if (tab == Tab.INVENTORY) {
            g.text(this.font, "§8Slot, creative-scroll, recipe-book and the", contentLeft, infoY, 0xFF606068);
            g.text(this.font, "§8inventory open/close animations.", contentLeft, infoY + 11, 0xFF606068);
        } else {
            g.text(this.font, "§8Pause menu, options, title and other menus.", contentLeft, infoY, 0xFF606068);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;
        double mx = event.x(), my = event.y();

        if (doneBtn.contains(mx, my)) { this.onClose(); return true; }

        for (int i = 0; i < TAB_NAMES.length; i++) {
            int ty = tabTop + i * 30;
            if (mx >= sidebarX && mx <= sidebarX + sidebarW && my >= ty && my < ty + 24) {
                selectTab(Tab.values()[i]);
                return true;
            }
        }

        AnimConfig c = AnimConfigManager.get();
        boolean master = c.masterEnabled;
        for (Row r : rows) {
            boolean isMaster = r.feat == null && "Master".equals(r.name);
            if (r.toggle.contains(mx, my)) {
                r.set.accept(!r.get.getAsBoolean());
                save();
                return true;
            }
            if (r.feat == null) continue;
            boolean settings = master && r.feat.enabled;
            if (!settings) continue;
            if (r.easing.contains(mx, my)) { r.feat.easing = r.feat.easing.next(); save(); return true; }
            if (r.minus.contains(mx, my)) { r.feat.durationMs = Math.max(DUR_MIN, r.feat.durationMs - DUR_STEP); save(); return true; }
            if (r.plus.contains(mx, my)) { r.feat.durationMs = Math.min(DUR_MAX, r.feat.durationMs + DUR_STEP); save(); return true; }
            if (r.hasStyle && r.feat instanceof AnimConfig.ScreenFeature sf && r.style.contains(mx, my)) {
                sf.style = sf.style.next(); save(); return true;
            }
        }
        if (tab == Tab.GAME_MENU && master && transitionBtn.contains(mx, my)) {
            c.menuTransition = c.menuTransition.next();
            save();
            return true;
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
