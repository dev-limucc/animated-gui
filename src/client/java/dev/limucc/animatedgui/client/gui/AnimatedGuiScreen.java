package dev.limucc.animatedgui.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Tabbed, animated settings screen in the Limucc flat style. A left sidebar (General / Inventory / Game menu /
 * Chat / Screens) switches the right-hand content, which slides in on each tab change; every toggle is an animated
 * pill switch — so the screen itself demonstrates the smoothness it adds to the rest of the game.
 *
 * <p>All user-facing text comes from the {@code animatedgui.*} lang keys.
 */
public class AnimatedGuiScreen extends Screen {

    private enum Tab { GENERAL, INVENTORY, GAME_MENU, CHAT, SCREENS }

    private static final String[] TAB_KEYS = {
            "animatedgui.tab.general", "animatedgui.tab.inventory", "animatedgui.tab.menu",
            "animatedgui.tab.chat", "animatedgui.tab.screens"};

    private static final int DUR_MIN = 20, DUR_MAX = 600, DUR_STEP = 10;
    private static final int ROW_H = 20;

    // palette
    private static final int PANEL = 0xEE15161B, EDGE = 0x22FFFFFF, SIDEBAR = 0x40000000;
    private static final int TAB_HOVER = 0x18FFFFFF, ACCENT = 0xFF3A6EA5;
    private static final int TXT = 0xFFE6E6EA, TXT_DIM = 0xFF74747C, HEAD = 0xFFB0B0B8;
    private static final int NOTE = 0xFFE0A050; // amber: "this feature stepped aside"

    private final Screen parent;

    private static final int PANEL_W = 480;
    /** Scale (<= 1) that fits the 480 px panel into a narrow window (e.g. 720p at GUI scale 3 is only 426 wide). */
    private float fit = 1.0f;
    private int midX, midY;

    private int panelLeft, panelRight, panelTop, panelBottom;
    private int sidebarX, sidebarW, contentLeft, contentRight, tabTop, contentTop, contentBottom;

    private Tab tab = Tab.GENERAL;
    private long tabSwitchMs = -100000L;
    private final Tween tabHighlight = new Tween();
    private boolean tabHighlightInit;

    private final FlatButton doneBtn = new FlatButton(0, 0, 0, 0, "");
    private final FlatButton transitionBtn = new FlatButton(0, 0, 0, 0, "");
    private List<Row> rows = new ArrayList<>();

    // Screens tab
    private List<String> screenList = new ArrayList<>();
    private List<ToggleSwitch> screenToggles = new ArrayList<>();
    private int screenScroll;

    public AnimatedGuiScreen(Screen parent) {
        super(Component.translatable("animatedgui.title"));
        this.parent = parent;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /** One configurable line: a toggle, and (for real features) easing / duration / optional style. */
    private final class Row {
        final String name;
        final AnimConfig.Feature feat;   // null for plain boolean rows (master / reduced motion / hotbar trail)
        final boolean hasStyle;
        final boolean isMaster;
        final BooleanSupplier get;
        final Consumer<Boolean> set;
        final ToggleSwitch toggle = new ToggleSwitch();
        final FlatButton easing = new FlatButton(0, 0, 0, 0, "");
        final FlatButton minus = new FlatButton(0, 0, 0, 0, "-");
        final FlatButton plus = new FlatButton(0, 0, 0, 0, "+");
        final FlatButton style = new FlatButton(0, 0, 0, 0, "");
        int y;

        Row(String name, AnimConfig.Feature feat, boolean hasStyle, boolean isMaster,
            BooleanSupplier get, Consumer<Boolean> set) {
            this.name = name; this.feat = feat; this.hasStyle = hasStyle; this.isMaster = isMaster;
            this.get = get; this.set = set;
        }
    }

    private Row featRow(String key, AnimConfig.Feature f, boolean hasStyle) {
        return new Row(tr(key), f, hasStyle, false, () -> f.enabled, v -> f.enabled = v);
    }

    private Row boolRow(String key, boolean master, BooleanSupplier get, Consumer<Boolean> set) {
        return new Row(tr(key), null, false, master, get, set);
    }

    @Override
    protected void init() {
        int cx = this.width / 2, cy = this.height / 2;
        midX = cx; midY = cy;
        fit = Math.min(1.0f, (this.width - 4) / (float) PANEL_W);
        panelLeft = cx - 240; panelRight = cx + 240;
        panelTop = Math.max(8, cy - 120); panelBottom = Math.min(this.height - 8, cy + 120);
        sidebarX = panelLeft + 12; sidebarW = 92;
        contentLeft = panelLeft + 118; contentRight = panelRight - 14;
        tabTop = panelTop + 40;
        contentTop = panelTop + 42;
        contentBottom = panelBottom - 30;
        doneBtn.label = tr("animatedgui.done");
        doneBtn.setBounds(panelRight - 92, panelBottom - 26, 78, 20);
        rebuildRows();
    }

    private void rebuildRows() {
        AnimConfig c = AnimConfigManager.get();
        rows = new ArrayList<>();
        switch (tab) {
            case GENERAL -> {
                rows.add(boolRow("animatedgui.row.master", true, () -> c.masterEnabled, v -> c.masterEnabled = v));
                rows.add(boolRow("animatedgui.row.reduced", false, () -> c.reducedMotion, v -> c.reducedMotion = v));
                rows.add(boolRow("animatedgui.row.backdrop", false, () -> c.backdropFade, v -> c.backdropFade = v));
                rows.add(featRow("animatedgui.row.hotbar", c.hotbar, false));
                rows.add(boolRow("animatedgui.row.trail", false, () -> c.hotbarTrail, v -> c.hotbarTrail = v));
                rows.add(featRow("animatedgui.row.listscroll", c.listScroll, false));
            }
            case INVENTORY -> {
                rows.add(featRow("animatedgui.row.items", c.items, false));
                rows.add(featRow("animatedgui.row.hover", c.hoverFade, false));
                rows.add(featRow("animatedgui.row.creative", c.creativeScroll, false));
                rows.add(featRow("animatedgui.row.recipe", c.recipeBook, false));
                rows.add(featRow("animatedgui.row.invopen", c.inventoryOpen, true));
                rows.add(featRow("animatedgui.row.invclose", c.inventoryClose, true));
            }
            case GAME_MENU -> {
                rows.add(featRow("animatedgui.row.menuopen", c.menuOpen, true));
                rows.add(featRow("animatedgui.row.menuclose", c.menuClose, true));
            }
            case CHAT -> rows.add(featRow("animatedgui.row.chat", c.chat, false));
            case SCREENS -> rebuildScreens(c);
        }
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int y = contentTop + i * ROW_H;
            r.y = y;
            r.toggle.setPosition(contentLeft + 88, y + 2);
            r.toggle.snapTo(r.get.getAsBoolean());
            r.easing.setBounds(contentLeft + 116, y + 1, 64, 14);
            r.minus.setBounds(contentLeft + 184, y + 1, 14, 14);
            r.plus.setBounds(contentLeft + 234, y + 1, 14, 14);
            r.style.setBounds(contentLeft + 252, y + 1, 88, 14);
        }
    }

    /** Screens tab: every screen class seen so far (plus anything hand-added to the deny list), newest last. */
    private void rebuildScreens(AnimConfig c) {
        Set<String> all = new LinkedHashSet<>(c.knownScreens);
        all.addAll(c.deniedScreens);
        // the settings screen itself is never listed
        all.remove(AnimatedGuiScreen.class.getName());
        screenList = new ArrayList<>(all);
        screenToggles = new ArrayList<>();
        for (String name : screenList) {
            ToggleSwitch t = new ToggleSwitch();
            t.snapTo(!isDenied(c, name));
            screenToggles.add(t);
        }
        screenScroll = Math.max(0, Math.min(screenScroll, maxScreenScroll()));
    }

    private static boolean isDenied(AnimConfig c, String className) {
        String simple = className.substring(className.lastIndexOf('.') + 1);
        return c.deniedScreens.contains(className) || c.deniedScreens.contains(simple);
    }

    private int screenRows() {
        return Math.max(1, (contentBottom - (contentTop + 14)) / ROW_H);
    }

    private int maxScreenScroll() {
        return Math.max(0, screenList.size() - screenRows());
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
        if (fit >= 1.0f) {
            extractPanel(g, mouseX, mouseY);
            return;
        }
        // narrow window: draw the whole panel scaled down about the centre, and map the mouse through the inverse
        g.pose().pushMatrix();
        g.pose().translate(midX, midY);
        g.pose().scale(fit, fit);
        g.pose().translate(-midX, -midY);
        extractPanel(g, (int) (midX + (mouseX - midX) / fit), (int) (midY + (mouseY - midY) / fit));
        g.pose().popMatrix();
    }

    private void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        AnimConfig c = AnimConfigManager.get();

        g.fill(panelLeft, panelTop, panelRight, panelBottom, PANEL);
        g.fill(panelLeft, panelTop, panelRight, panelTop + 1, EDGE);
        g.fill(sidebarX - 4, tabTop - 6, sidebarX + sidebarW + 4, panelBottom - 8, SIDEBAR);

        g.text(this.font, "§l" + tr("animatedgui.title"), panelLeft + 12, panelTop + 12, TXT);
        g.text(this.font, "§7" + tr("animatedgui.settings"), panelLeft + 12, panelTop + 24, TXT_DIM);

        // ── sidebar tabs with a sliding accent highlight ──
        long now = Util.getMillis();
        int selectedY = tabTop + tab.ordinal() * 28;
        if (!tabHighlightInit) { tabHighlight.snap(selectedY); tabHighlightInit = true; }
        tabHighlight.retarget(selectedY, now, 220, Easing.EASE_OUT);
        int hy = Math.round(tabHighlight.update(now));
        g.fill(sidebarX - 4, hy, sidebarX - 2, hy + 24, ACCENT);          // accent bar
        g.fill(sidebarX, hy, sidebarX + sidebarW, hy + 24, 0x553A6EA5);   // selected fill (faint accent)

        for (int i = 0; i < TAB_KEYS.length; i++) {
            int ty = tabTop + i * 28;
            boolean hov = mouseX >= sidebarX && mouseX <= sidebarX + sidebarW && mouseY >= ty && mouseY < ty + 24;
            if (hov && i != tab.ordinal()) g.fill(sidebarX, ty, sidebarX + sidebarW, ty + 24, TAB_HOVER);
            int col = i == tab.ordinal() ? 0xFFFFFFFF : (hov ? TXT : HEAD);
            g.text(this.font, tr(TAB_KEYS[i]), sidebarX + 8, ty + 8, col);
        }

        // ── content (slides in on tab change) ──
        float t = (now - tabSwitchMs) / 200.0f;
        float slide = (t >= 0 && t < 1) ? (1.0f - Easing.EASE_OUT.clampApply(t)) * 22.0f : 0.0f;
        g.enableScissor(contentLeft - 4, tabTop - 6, contentRight + 6, contentBottom + 2);
        g.pose().pushMatrix();
        g.pose().translate(slide, 0.0f);
        if (tab == Tab.SCREENS) extractScreens(g, mouseX, mouseY, c);
        else extractContent(g, mouseX, mouseY, c);
        g.pose().popMatrix();
        g.disableScissor();

        doneBtn.render(g, this.font, mouseX, mouseY, true);
    }

    private void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, AnimConfig c) {
        boolean master = c.masterEnabled;
        for (Row r : rows) {
            boolean state = r.get.getAsBoolean();
            boolean active = r.isMaster || master;                 // master row always active
            AnimConfig.Feature f = r.feat;
            boolean yielded = f != null && f.yieldedTo != null;
            boolean reducedOff = f != null && f.motion && c.reducedMotion;
            boolean settings = f != null && master && f.enabled && !yielded && !reducedOff;

            g.text(this.font, r.name, contentLeft, r.y + 4, active ? TXT : TXT_DIM);
            r.toggle.render(g, state, active && !yielded && !reducedOff);

            if (f != null) {
                if (yielded) {
                    // another mod owns this job — say so instead of showing dead controls
                    g.text(this.font, tr("animatedgui.yielded", f.yieldedTo), contentLeft + 116, r.y + 4, NOTE);
                    continue;
                }
                r.easing.label = f.easing.label();
                r.easing.render(g, this.font, mouseX, mouseY, settings);
                r.minus.render(g, this.font, mouseX, mouseY, settings);
                r.plus.render(g, this.font, mouseX, mouseY, settings);
                String val = f.durationMs + "ms";
                int vw = this.font.width(val);
                g.text(this.font, val, contentLeft + 216 - vw / 2, r.y + 4, settings ? 0xFFFFFF80 : TXT_DIM);
                if (r.hasStyle && f instanceof AnimConfig.ScreenFeature sf) {
                    r.style.label = sf.style.label();
                    r.style.render(g, this.font, mouseX, mouseY, settings);
                }
            }
        }

        int afterRows = contentTop + rows.size() * ROW_H;
        int infoY = afterRows + 8;
        if (tab == Tab.GAME_MENU) {
            // How navigating between menus animates (Pause→Options, a sub-screen's Back, …) — selectable.
            boolean active = c.masterEnabled && !c.reducedMotion;
            g.text(this.font, tr("animatedgui.row.transition"), contentLeft, afterRows + 4, c.masterEnabled ? TXT : TXT_DIM);
            transitionBtn.label = c.effectiveMenuTransition().label();
            transitionBtn.setBounds(contentLeft + 88, afterRows + 1, 110, 14);
            transitionBtn.render(g, this.font, mouseX, mouseY, active);
            infoY = afterRows + ROW_H + 8;
        }
        if (tab == Tab.GENERAL) {
            g.text(this.font, "§7" + tr("animatedgui.info.reduced"), contentLeft, infoY, c.reducedMotion ? NOTE : TXT_DIM);
            g.text(this.font, "§b> GitHub§7   github.com/dev-limucc/animated-gui", contentLeft, infoY + 17, 0xFF9098AA);
            g.text(this.font, "§a> Modrinth§7 modrinth.com/mod/animated-gui", contentLeft, infoY + 28, 0xFF9098AA);
        } else if (tab == Tab.CHAT) {
            g.text(this.font, "§7" + tr("animatedgui.info.chat1"), contentLeft, infoY, TXT_DIM);
            g.text(this.font, "§7" + tr("animatedgui.info.chat2"), contentLeft, infoY + 11, TXT_DIM);
        } else if (tab == Tab.INVENTORY) {
            g.text(this.font, "§8" + tr("animatedgui.info.inv1"), contentLeft, infoY, 0xFF606068);
            g.text(this.font, "§8" + tr("animatedgui.info.inv2"), contentLeft, infoY + 11, 0xFF606068);
        } else {
            g.text(this.font, "§8" + tr("animatedgui.info.menu"), contentLeft, infoY, 0xFF606068);
        }
    }

    private void extractScreens(GuiGraphicsExtractor g, int mouseX, int mouseY, AnimConfig c) {
        boolean master = c.masterEnabled;
        g.text(this.font, "§7" + tr("animatedgui.screens.hint"), contentLeft, contentTop, TXT_DIM);
        if (screenList.isEmpty()) {
            g.text(this.font, "§8" + tr("animatedgui.screens.empty1"), contentLeft, contentTop + 20, 0xFF606068);
            g.text(this.font, "§8" + tr("animatedgui.screens.empty2"), contentLeft, contentTop + 31, 0xFF606068);
            return;
        }
        int rowsShown = screenRows();
        int listTop = contentTop + 14;
        int textX = contentLeft + 32;
        int textW = contentRight - textX - 8;
        for (int i = 0; i < rowsShown; i++) {
            int idx = screenScroll + i;
            if (idx >= screenList.size()) break;
            String full = screenList.get(idx);
            boolean denied = isDenied(c, full);
            int y = listTop + i * ROW_H;
            ToggleSwitch tg = screenToggles.get(idx);
            tg.setPosition(contentLeft, y + 2);
            tg.render(g, !denied, master);

            String simple = full.substring(full.lastIndexOf('.') + 1).replace('$', '.');
            String pkg = full.contains(".") ? full.substring(0, full.lastIndexOf('.')) : "";
            int sw = this.font.width(simple);
            g.text(this.font, simple, textX, y + 4, master ? (denied ? TXT_DIM : TXT) : TXT_DIM);
            if (!pkg.isEmpty() && sw + 8 < textW) {
                String shown = this.font.plainSubstrByWidth(pkg, textW - sw - 8);
                g.text(this.font, shown, textX + sw + 6, y + 4, 0xFF4E4E56);
            }
        }
        // slim scrollbar when the list is longer than the window
        int max = maxScreenScroll();
        if (max > 0) {
            int trackTop = listTop, trackBottom = listTop + rowsShown * ROW_H;
            int barH = Math.max(12, (trackBottom - trackTop) * rowsShown / screenList.size());
            int barY = trackTop + (trackBottom - trackTop - barH) * screenScroll / max;
            g.fill(contentRight + 2, trackTop, contentRight + 4, trackBottom, 0x22FFFFFF);
            g.fill(contentRight + 2, barY, contentRight + 4, barY + barH, ACCENT);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.SCREENS && maxScreenScroll() > 0) {
            screenScroll = Math.max(0, Math.min(maxScreenScroll(), screenScroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        double mx = midX + (event.x() - midX) / fit, my = midY + (event.y() - midY) / fit;

        if (doneBtn.contains(mx, my)) { this.onClose(); return true; }

        for (int i = 0; i < TAB_KEYS.length; i++) {
            int ty = tabTop + i * 28;
            if (mx >= sidebarX && mx <= sidebarX + sidebarW && my >= ty && my < ty + 24) {
                selectTab(Tab.values()[i]);
                return true;
            }
        }

        AnimConfig c = AnimConfigManager.get();
        boolean master = c.masterEnabled;

        if (tab == Tab.SCREENS) {
            if (!master) return false;
            for (int i = 0; i < screenRows(); i++) {
                int idx = screenScroll + i;
                if (idx >= screenList.size()) break;
                if (screenToggles.get(idx).contains(mx, my)) {
                    String full = screenList.get(idx);
                    AnimConfigManager.setDenied(full, !isDenied(c, full));
                    save();
                    return true;
                }
            }
            return false;
        }

        for (Row r : rows) {
            AnimConfig.Feature f = r.feat;
            boolean yielded = f != null && f.yieldedTo != null;
            boolean reducedOff = f != null && f.motion && c.reducedMotion;
            if (r.toggle.contains(mx, my) && (r.isMaster || master) && !yielded && !reducedOff) {
                r.set.accept(!r.get.getAsBoolean());
                save();
                return true;
            }
            if (f == null || yielded) continue;
            boolean settings = master && f.enabled && !reducedOff;
            if (!settings) continue;
            if (r.easing.contains(mx, my)) { f.easing = f.easing.next(); save(); return true; }
            if (r.minus.contains(mx, my)) { f.durationMs = Math.max(DUR_MIN, f.durationMs - DUR_STEP); save(); return true; }
            if (r.plus.contains(mx, my)) { f.durationMs = Math.min(DUR_MAX, f.durationMs + DUR_STEP); save(); return true; }
            if (r.hasStyle && f instanceof AnimConfig.ScreenFeature sf && r.style.contains(mx, my)) {
                sf.style = sf.style.next(); save(); return true;
            }
        }
        if (tab == Tab.GAME_MENU && master && !c.reducedMotion && transitionBtn.contains(mx, my)) {
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
        this.minecraft.gui.setScreen(this.parent);
    }
}
