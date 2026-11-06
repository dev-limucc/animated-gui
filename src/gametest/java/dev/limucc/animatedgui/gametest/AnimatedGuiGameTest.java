package dev.limucc.animatedgui.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.limucc.animatedgui.client.anim.Easing;
import dev.limucc.animatedgui.client.anim.FlyAnim;
import dev.limucc.animatedgui.client.anim.InventoryShiftProvider;
import dev.limucc.animatedgui.client.anim.ListScrollHit;
import dev.limucc.animatedgui.client.anim.CreativeScroll;
import dev.limucc.animatedgui.client.anim.PipCapture;
import dev.limucc.animatedgui.client.anim.ScreenAnimController;
import dev.limucc.animatedgui.client.anim.ScreenStyle;
import dev.limucc.animatedgui.client.config.AnimConfig;
import dev.limucc.animatedgui.client.config.AnimConfigManager;
import dev.limucc.animatedgui.client.gui.AnimatedGuiScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Runtime verification of every Animated GUI feature against a real client. Run with
 * {@code gradlew runClientGameTest "-Ptarget=<26.2|26.3>"}; screenshots land in build/run/clientGameTest/screenshots.
 * Lives in the test source set only — never shipped in the release jar.
 */
public class AnimatedGuiGameTest implements FabricClientGameTest {

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("AGUI-GAMETEST FAIL: " + what);
        System.out.println("[AGUI-GAMETEST] PASS: " + what);
    }

    static void info(String what) {
        System.out.println("[AGUI-GAMETEST] INFO: " + what);
    }

    // ── reflection helpers (protected/private vanilla fields; names are Mojang names on 26.x) ──────────────────
    static Object field(Object o, Class<?> c, String name) {
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    static int leftPos(AbstractContainerScreen<?> s) { return (Integer) field(s, AbstractContainerScreen.class, "leftPos"); }
    static int topPos(AbstractContainerScreen<?> s) { return (Integer) field(s, AbstractContainerScreen.class, "topPos"); }
    static Slot hovered(AbstractContainerScreen<?> s) { return (Slot) field(s, AbstractContainerScreen.class, "hoveredSlot"); }

    // ── context helpers ───────────────────────────────────────────────────────────────────────────────────────
    static Screen screen(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> mc.gui.screen());
    }

    static void cursorGui(ClientGameTestContext ctx, double gx, double gy) {
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(gx * scale, gy * scale);
    }

    /** A click a human would make: press, hold for a tick, release (26.3 only picks a stack up on release). */
    static void slowClick(ClientGameTestContext ctx) {
        ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTick();
        ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTick();
    }

    /** Click at panel-local (x, y) of the settings screen, which is drawn scaled by {@code fit} about the centre. */
    static void clickPanel(ClientGameTestContext ctx, float fit, int w, int h, double x, double y) {
        click(ctx, w / 2.0 + (x - w / 2.0) * fit, h / 2.0 + (y - h / 2.0) * fit);
    }

    static void click(ClientGameTestContext ctx, double gx, double gy) {
        cursorGui(ctx, gx, gy);
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTick();
    }

    static void resetCfg(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            AnimConfig c = AnimConfigManager.get();
            AnimConfig d = new AnimConfig();
            c.masterEnabled = true; c.reducedMotion = false;
            c.hotbar = d.hotbar; c.hotbarTrail = false; c.listScroll = d.listScroll; c.chat = d.chat;
            c.items = d.items; c.hoverFade = d.hoverFade; c.creativeScroll = d.creativeScroll; c.recipeBook = d.recipeBook;
            c.inventoryOpen = d.inventoryOpen; c.inventoryClose = d.inventoryClose;
            c.menuOpen = d.menuOpen; c.menuClose = d.menuClose; c.menuTransition = d.menuTransition;
            c.deniedScreens.clear();
            c.normalize();
        });
    }

    /** Make the open (and close) animation long and linear so a screenshot a few ticks in lands mid-animation. */
    static void slow(ClientGameTestContext ctx, ScreenStyle openStyle, ScreenStyle closeStyle, int ms) {
        ctx.runOnClient(mc -> {
            AnimConfig c = AnimConfigManager.get();
            c.inventoryOpen.style = openStyle; c.inventoryOpen.durationMs = ms; c.inventoryOpen.easing = Easing.LINEAR;
            c.inventoryClose.style = closeStyle; c.inventoryClose.durationMs = ms; c.inventoryClose.easing = Easing.LINEAR;
            // normalize() clamps to 2000 ms; keep within that
        });
    }

    static void pressInventoryKey(ClientGameTestContext ctx) {
        ctx.getInput().pressKey(options -> options.keyInventory);
    }

    static void closeScreenFast(ClientGameTestContext ctx) {
        resetCfg(ctx);
        ctx.runOnClient(mc -> {
            AnimConfig c = AnimConfigManager.get();
            c.inventoryClose.durationMs = 60; c.menuClose.durationMs = 60;
            c.menuOpen.durationMs = 60; c.inventoryOpen.durationMs = 60;
        });
        if (screen(ctx) != null) {
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(8);
            if (screen(ctx) != null) {
                // some screens ignore Esc; fall back to the real close
                ctx.runOnClient(mc -> mc.gui.setScreen(null));
                ctx.waitTicks(8);
            }
        }
        resetCfg(ctx);
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        // ═══ 0. every mixin must apply (injection targets resolve at runtime, a green build proves nothing) ═══
        ctx.runOnClient(mc -> MixinEnvironment.getCurrentEnvironment().audit());
        check(true, "MixinEnvironment.audit() passed (all mixins applied)");

        List<String> seen = new ArrayList<>();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            Screen s = mc.gui.screen();
            seen.add(s == null ? "null" : s.getClass().getSimpleName());
        });

        // Config sanity (no world needed): defaults, reduced motion, compat, deny cache.
        configUnitChecks(ctx);

        // Title screen -> Options -> back (menu transitions on a screen with no world behind it).
        titleScreen(ctx);

        try (var world = ctx.worldBuilder().create()) {
            ctx.waitTicks(40);
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("give @a minecraft:diamond 5");
            world.getServer().runCommand("give @a minecraft:iron_sword");
            world.getServer().runCommand("give @a minecraft:golden_apple 3");
            world.getServer().runCommand("give @a minecraft:cobblestone 64");
            ctx.waitTicks(10);
            ctx.takeScreenshot("00-world-baseline");

            survivalInventory(ctx, seen);
            hoverFade(ctx);
            itemGlide(ctx);
            containers(ctx, world);
            pauseAndOptions(ctx);
            recipeBook(ctx);
            hotbar(ctx);
            reducedMotion(ctx);
            deniedScreen(ctx);
            configScreen(ctx);
            creative(ctx, world, seen);
        }
        System.out.println("[AGUI-GAMETEST] ALL DONE");
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════════
    static double[] slotCenter(ClientGameTestContext ctx, int index) {
        return ctx.computeOnClient(mc -> {
            AbstractContainerScreen<?> s = (AbstractContainerScreen<?>) mc.gui.screen();
            Slot sl = s.getMenu().getSlot(index);
            return new double[]{leftPos(s) + sl.x + 8, topPos(s) + sl.y + 8};
        });
    }

    private void hoverFade(ClientGameTestContext ctx) {
        info("== slot hover cross-fade ==");
        resetCfg(ctx);
        ctx.runOnClient(mc -> {
            AnimConfig c = AnimConfigManager.get();
            c.inventoryOpen.durationMs = 40; c.inventoryClose.durationMs = 40;
            c.hoverFade.durationMs = 1500; c.hoverFade.easing = Easing.LINEAR;
        });
        pressInventoryKey(ctx);
        ctx.waitForScreen(InventoryScreen.class);
        ctx.waitTicks(12);
        double[] a = slotCenter(ctx, 36), b = slotCenter(ctx, 37);
        cursorGui(ctx, a[0], a[1]);
        ctx.waitTicks(2);
        int before = ScreenAnimController.hoverCrossfades;
        cursorGui(ctx, b[0], b[1]);
        ctx.waitTicks(3);
        ctx.takeScreenshot("16-slot-hover-crossfade-mid");
        check(ScreenAnimController.hoverCrossfades > before,
                "moving between slots cross-fades the highlight (" + (ScreenAnimController.hoverCrossfades - before) + " fade-out draws)");
        ctx.runOnClient(mc -> AnimConfigManager.get().hoverFade.enabled = false);
        int off = ScreenAnimController.hoverCrossfades;
        cursorGui(ctx, a[0], a[1]);
        ctx.waitTicks(3);
        check(ScreenAnimController.hoverCrossfades == off, "hover fade switched off -> vanilla highlight only");
        closeScreenFast(ctx);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════════
    private void titleScreen(ClientGameTestContext ctx) {
        info("== title screen transition ==");
        resetCfg(ctx);
        ctx.waitForScreen(TitleScreen.class);
        ctx.waitTicks(40);
        ctx.runOnClient(mc -> {
            AnimConfig c = AnimConfigManager.get();
            c.menuOpen.durationMs = 1500; c.menuOpen.easing = Easing.LINEAR;
            c.menuClose.durationMs = 1500; c.menuClose.easing = Easing.LINEAR;
            c.menuTransition = dev.limucc.animatedgui.client.anim.MenuTransition.SLIDE;
        });
        ctx.clickScreenButton("menu.options");
        ctx.waitTicks(8);
        ctx.takeScreenshot("39-title-to-options-exit-slide-mid");
        check(ctx.computeOnClient(mc -> ScreenAnimController.isClosing(mc.gui.screen())),
                "title -> options: the title screen is deferred and slides out");
        ctx.waitForScreen(OptionsScreen.class);
        ctx.waitTicks(8);
        ctx.takeScreenshot("39b-options-from-title-enter-slide-mid");
        float raw = ctx.computeOnClient(mc -> ScreenAnimController.rawProgress(mc.gui.screen()));
        check(raw > 0.02f && raw < 0.98f, "options slides in from the title screen (raw=" + raw + ")");
        ctx.waitTicks(40);
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitForScreen(TitleScreen.class);
        ctx.waitTicks(8);
        ctx.takeScreenshot("39c-back-to-title-mid");
        ctx.waitTicks(40);
        check(screen(ctx) instanceof TitleScreen, "options -> back returns to the title screen");
        resetCfg(ctx);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════════
    private void configUnitChecks(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            AnimConfig c = AnimConfigManager.get();
            check(c.items.on() && c.hotbar.on() && c.listScroll.on(), "defaults: motion features are on");
            c.reducedMotion = true;
            check(!c.items.on() && !c.hotbar.on() && !c.listScroll.on() && !c.chat.on() && !c.creativeScroll.on()
                    && !c.recipeBook.on(), "reduced motion switches every movement feature off");
            check(c.inventoryOpen.effStyle() == ScreenStyle.STILL_FADE && c.inventoryOpen.effMs() <= AnimConfig.REDUCED_MAX_MS,
                    "reduced motion: screens fade only, <= 100 ms");
            c.inventoryOpen.easing = Easing.ELASTIC;
            check(!c.inventoryOpen.effEasing().overshoots(), "reduced motion: overshoot easing replaced");
            c.inventoryOpen.easing = Easing.EASE_OUT;
            c.reducedMotion = false;
            check(c.items.on(), "reduced motion off restores item glide");
            check(c.items.yieldedTo == null && c.listScroll.yieldedTo == null,
                    "no Smooth Swapping / Smooth Scrolling installed -> nothing yields");
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════════
    private void survivalInventory(ClientGameTestContext ctx, List<String> seen) {
        info("== survival inventory ==");
        // A. SCALE, long + linear: mid-open shot (player model PiP must follow the panel, no per-frame bounds rewrite)
        slow(ctx, ScreenStyle.SCALE, ScreenStyle.FADE, 1800);
        int putsBefore = PipCapture.puts;
        pressInventoryKey(ctx);
        ctx.waitForScreen(InventoryScreen.class);
        ctx.waitTicks(12);
        ctx.takeScreenshot("10-survival-open-SCALE-mid");
        float raw = ctx.computeOnClient(mc -> ScreenAnimController.rawProgress(mc.gui.screen()));
        check(raw > 0.05f && raw < 0.95f, "open animation is mid-flight (raw=" + raw + ")");
        check(PipCapture.puts > putsBefore, "player-model PiP pose captured while animating (puts +" + (PipCapture.puts - putsBefore) + ")");
        check(ctx.computeOnClient(mc -> ScreenAnimController.compensating()), "mouse mapping is active mid-animation");
        float dim = ctx.computeOnClient(mc -> ScreenAnimController.backdropFactor(mc.gui.screen()));
        check(dim > 0.02f && dim < 0.98f, "the dim behind the inventory fades with the screen (factor=" + dim + ")");
        ctx.runOnClient(mc -> AnimConfigManager.get().backdropFade = false);
        check(ctx.computeOnClient(mc -> ScreenAnimController.backdropFactor(mc.gui.screen())) == 1.0f,
                "dim/blur fade switch off -> backdrop stays at full strength");
        ctx.runOnClient(mc -> AnimConfigManager.get().backdropFade = true);

        closeScreenFast(ctx);

        // control: a plain click with no animation running picks the stack up (proves the harness click path)
        resetCfg(ctx);
        pressInventoryKey(ctx);
        ctx.waitForScreen(InventoryScreen.class);
        ctx.waitTicks(20);
        AbstractContainerScreen<?> inv0 = (AbstractContainerScreen<?>) screen(ctx);
        Slot t0 = inv0.getMenu().slots.get(36);
        cursorGui(ctx, leftPos(inv0) + t0.x + 8, topPos(inv0) + t0.y + 8);
        ctx.waitTicks(2);
        slowClick(ctx);
        ctx.waitTick();
        info("control click: carried=" + ctx.computeOnClient(mc -> mc.player.containerMenu.getCarried().toString()));
        check(ctx.computeOnClient(mc -> !mc.player.containerMenu.getCarried().isEmpty()), "control: plain click picks up the stack");
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(2);
        closeScreenFast(ctx);

        // B. mouse compensation: hover/click land on what is DRAWN, not where the widget would sit un-animated
        slow(ctx, ScreenStyle.SCALE, ScreenStyle.FADE, 5000);
        pressInventoryKey(ctx);
        ctx.waitForScreen(InventoryScreen.class);
        ctx.waitFor(mc -> ScreenAnimController.rawProgress(mc.gui.screen()) >= 0.45f);
        AbstractContainerScreen<?> inv = (AbstractContainerScreen<?>) screen(ctx);
        Slot target = inv.getMenu().slots.get(36); // hotbar slot 0: holds the diamonds
        double lx = leftPos(inv) + target.x + 8, ly = topPos(inv) + target.y + 8;
        ctx.takeScreenshot("11-survival-mid-animation-before-aiming"); // (slow on some versions: aim only afterwards)
        double sx = ScreenAnimController.drawnX(lx), sy = ScreenAnimController.drawnY(ly);
        info("slot36 local=(" + lx + "," + ly + ") drawn=(" + sx + "," + sy + ")");
        check(Math.hypot(sx - lx, sy - ly) > 20, "drawn position differs from the un-animated one (test is meaningful)");
        cursorGui(ctx, sx, sy);
        ctx.waitTick();
        Slot hov = hovered(inv);
        check(hov == target, "hover resolves to the slot that is drawn under the cursor (" + (hov == null ? "null" : "slot " + hov.index) + ")");
        ctx.takeScreenshot("11a-hover-mid-animation-no-tooltip-while-animating");
        sx = ScreenAnimController.drawnX(lx); sy = ScreenAnimController.drawnY(ly); // screenshot is slow: re-aim
        cursorGui(ctx, sx, sy);
        ctx.waitTick();
        slowClick(ctx);
        ctx.waitTick();
        boolean carrying = ctx.computeOnClient(mc -> !mc.player.containerMenu.getCarried().isEmpty());
        check(carrying, "a click during the animation picks up the item that is drawn there");
        ctx.takeScreenshot("11b-survival-mid-animation-item-picked-up");
        sx = ScreenAnimController.drawnX(lx); sy = ScreenAnimController.drawnY(ly); // re-aim, the panel kept moving
        cursorGui(ctx, sx, sy);
        ctx.waitTick();
        slowClick(ctx); // put it back
        if (ctx.computeOnClient(mc -> !mc.player.containerMenu.getCarried().isEmpty())) {
            ctx.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 36, 0,
                    net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player));
            ctx.waitTick();
        }

        // C. closing mid-animation (deferred close still works), the closing screen swallows clicks,
        
        int posted = seen.size();
        pressInventoryKey(ctx);
        ctx.waitTicks(4);
        boolean closing = ctx.computeOnClient(mc -> ScreenAnimController.isClosing(mc.gui.screen()));
        info("after close key: screen=" + screen(ctx) + " seen=" + new ArrayList<>(seen.subList(posted, seen.size())));
        check(closing, "close is deferred and animating");
        ctx.takeScreenshot("12-survival-close-FADE-mid");
        cursorGui(ctx, sx, sy);
        ctx.waitTick();
        slowClick(ctx);
        boolean carried2 = ctx.computeOnClient(mc -> mc.player.containerMenu.getCarried().isEmpty());
        check(carried2, "a click on a screen that is animating out is swallowed");
        pressInventoryKey(ctx);
        ctx.waitTicks(2);
        check(ctx.computeOnClient(mc -> ScreenAnimController.isClosing(mc.gui.screen())), "pressing close again does not cut the exit animation short");
        ctx.runOnClient(mc -> AnimConfigManager.get().inventoryClose.durationMs = 200); // let it finish now
        ctx.waitTicks(10);
        check(screen(ctx) == null, "the screen closes once the exit animation has played");
        info("screens during close: " + new ArrayList<>(seen.subList(posted, seen.size())).stream().distinct().toList());

        // D. FADE open: slot items + player model + text must fade together
        slow(ctx, ScreenStyle.FADE, ScreenStyle.FADE, 1800);
        pressInventoryKey(ctx);
        ctx.waitForScreen(InventoryScreen.class);
        ctx.waitTicks(10);
        ctx.takeScreenshot("13-survival-open-FADE-mid-items-and-model-fade");
        check(PipCapture.lastAlpha > 0.05f && PipCapture.lastAlpha < 0.95f, "FADE: player model opacity follows the fade (alpha=" + PipCapture.lastAlpha + ")");
        ctx.waitTicks(40);
        ctx.takeScreenshot("14-survival-FADE-settled");
        closeScreenFast(ctx);

        // E. default settings, plain open+close, with real durations
        resetCfg(ctx);
        pressInventoryKey(ctx);
        ctx.waitForScreen(InventoryScreen.class);
        ctx.waitTicks(20);
        ctx.takeScreenshot("15-survival-open-default-settled");
        check(ctx.computeOnClient(mc -> ScreenAnimController.rawProgress(mc.gui.screen())) == 1.0f, "default open animation completes");
        check(!ctx.computeOnClient(mc -> ScreenAnimController.compensating()), "mouse mapping cleared once the animation settled");
        pressInventoryKey(ctx);
        ctx.waitTicks(15);
        check(screen(ctx) == null, "default close animation completes and closes the screen");
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════════
    private void itemGlide(ClientGameTestContext ctx) {
        info("== item glide (shift-click) ==");
        resetCfg(ctx);
        ctx.runOnClient(mc -> {
            AnimConfig c = AnimConfigManager.get();
            c.items.durationMs = 1500; c.items.easing = Easing.LINEAR;
            c.inventoryOpen.durationMs = 40;
        });
        pressInventoryKey(ctx);
        ctx.waitForScreen(InventoryScreen.class);
        ctx.waitTicks(10);
        AbstractContainerScreen<?> inv = (AbstractContainerScreen<?>) screen(ctx);
        Slot from = inv.getMenu().slots.get(36); // hotbar 0
        int created = FlyAnim.created;
        // a real shift-click: route it through the screen's own slotClicked(QUICK_MOVE), exactly what a shift+click does
        ctx.runOnClient(mc -> {
            try {
                var m = AbstractContainerScreen.class.getDeclaredMethod("slotClicked", Slot.class, int.class, int.class,
                        net.minecraft.world.inventory.ContainerInput.class);
                m.setAccessible(true);
                m.invoke(mc.gui.screen(), from, from.index, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
        ctx.waitTicks(4);
        ctx.takeScreenshot("20-item-glide-mid");
        info("FlyAnim.created before=" + created + " after=" + FlyAnim.created);
        check(FlyAnim.created > created, "shift-click spawned an item glide (+" + (FlyAnim.created - created) + ")");
        ctx.waitTicks(40);
        ctx.takeScreenshot("21-item-glide-landed");
        closeScreenFast(ctx);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════════
    private void containers(ClientGameTestContext ctx, net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world) {
        info("== chest / crafting table / furnace ==");
        BlockPos base = ctx.computeOnClient(mc -> mc.player.blockPosition());
        BlockPos chest = base.offset(2, 0, 0), table = base.offset(0, 0, 2), furnace = base.offset(-2, 0, 0);
        world.getServer().runCommand("setblock " + chest.getX() + " " + chest.getY() + " " + chest.getZ() + " minecraft:chest");
        world.getServer().runCommand("setblock " + table.getX() + " " + table.getY() + " " + table.getZ() + " minecraft:crafting_table");
        world.getServer().runCommand("setblock " + furnace.getX() + " " + furnace.getY() + " " + furnace.getZ() + " minecraft:furnace");
        world.getServer().runCommand("item replace block " + chest.getX() + " " + chest.getY() + " " + chest.getZ() + " container.0 with minecraft:emerald 12");
        world.getServer().runCommand("item replace block " + chest.getX() + " " + chest.getY() + " " + chest.getZ() + " container.5 with minecraft:gold_ingot 30");
        world.getServer().runCommand("item replace block " + chest.getX() + " " + chest.getY() + " " + chest.getZ() + " container.13 with minecraft:enchanted_golden_apple 2");
        ctx.waitTicks(10);

        record Case(String name, BlockPos pos, Class<? extends Screen> cls) {}
        for (Case cs : new Case[]{
                new Case("chest", chest, ContainerScreen.class),
                new Case("crafting-table", table, CraftingScreen.class),
                new Case("furnace", furnace, FurnaceScreen.class)}) {
            slow(ctx, ScreenStyle.SCALE, ScreenStyle.FADE, 1500);
            ctx.getInput().lookAt(cs.pos());
            ctx.waitTicks(3);
            ctx.getInput().pressKey(options -> options.keyUse);
            ctx.waitForScreen(cs.cls());
            ctx.waitTicks(8);
            ctx.takeScreenshot("30-" + cs.name() + "-open-mid");
            float raw = ctx.computeOnClient(mc -> ScreenAnimController.rawProgress(mc.gui.screen()));
            check(raw > 0.02f && raw < 0.98f, cs.name() + " opens with an animation (raw=" + raw + ")");
            ctx.waitTicks(30);
            ctx.takeScreenshot("31-" + cs.name() + "-settled");
            closeScreenFast(ctx);
            check(screen(ctx) == null, cs.name() + " closes");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════════
    private void pauseAndOptions(ClientGameTestContext ctx) {
        info("== pause menu / options / video settings ==");
        resetCfg(ctx);
        ctx.runOnClient(mc -> {
            AnimConfig c = AnimConfigManager.get();
            c.menuOpen.durationMs = 1500; c.menuOpen.easing = Easing.LINEAR; c.menuOpen.style = ScreenStyle.FADE;
            c.menuClose.durationMs = 1500; c.menuClose.easing = Easing.LINEAR; c.menuClose.style = ScreenStyle.FADE;
        });
        int blurBefore = ctx.computeOnClient(mc -> mc.options.getMenuBackgroundBlurriness());
        ctx.runOnClient(mc -> mc.options.menuBackgroundBlurriness().set(8));
        int scaledBefore = ScreenAnimController.blurScaled;
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitForScreen(PauseScreen.class);
        ctx.waitTicks(8);
        ctx.takeScreenshot("40-pause-open-FADE-mid");
        check(ScreenAnimController.blurScaled > scaledBefore,
                "the pause-menu blur radius is scaled down while the menu fades in (" + (ScreenAnimController.blurScaled - scaledBefore) + " frames)");
        ctx.runOnClient(mc -> mc.options.menuBackgroundBlurriness().set(blurBefore));
        float raw = ctx.computeOnClient(mc -> ScreenAnimController.rawProgress(mc.gui.screen()));
        check(raw > 0.02f && raw < 0.98f, "pause menu animates open (raw=" + raw + ")");
        ctx.waitTicks(40);

        // Pause -> Options: SLIDE carousel (old slides out, new slides in)
        resetCfg(ctx);
        ctx.runOnClient(mc -> {
            AnimConfig c = AnimConfigManager.get();
            c.menuOpen.durationMs = 1500; c.menuOpen.easing = Easing.LINEAR;
            c.menuClose.durationMs = 1500; c.menuClose.easing = Easing.LINEAR;
            c.menuTransition = dev.limucc.animatedgui.client.anim.MenuTransition.SLIDE;
        });
        ctx.clickScreenButton("menu.options");
        ctx.waitTicks(8);
        ctx.takeScreenshot("41-pause-to-options-exit-slide-mid");
        check(ctx.computeOnClient(mc -> ScreenAnimController.isClosing(mc.gui.screen())), "menu->menu navigation defers the old menu");
        ctx.waitForScreen(OptionsScreen.class);
        ctx.waitTicks(8);
        ctx.takeScreenshot("42-options-enter-slide-mid");
        ctx.waitTicks(40);

        resetCfg(ctx);
        ctx.clickScreenButton("options.video");
        ctx.waitForScreen(VideoSettingsScreen.class);
        ctx.waitTicks(25);
        ctx.takeScreenshot("43-video-settings");

        // list scroll: real wheel input, then the click hit-test must use the DISPLAYED position
        ctx.runOnClient(mc -> {
            AnimConfig c = AnimConfigManager.get();
            c.listScroll.durationMs = 900;
        });
        cursorGui(ctx, ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth()) / 2.0, 120);
        ctx.waitTick();
        ctx.getInput().scroll(-6);
        ctx.waitTicks(2);
        ctx.takeScreenshot("44-video-settings-list-scrolling");
        boolean ok = ctx.computeOnClient(mc -> {
            Screen s = mc.gui.screen();
            for (var child : s.children()) {
                if (child instanceof AbstractSelectionList<?> list && child instanceof ListScrollHit hit) {
                    double target = list.scrollAmount();
                    hit.animatedgui$hitBegin();
                    double shown = list.scrollAmount();
                    hit.animatedgui$hitEnd();
                    double after = list.scrollAmount();
                    System.out.println("[AGUI-GAMETEST] INFO: list target=" + target + " shown=" + shown + " after=" + after);
                    return target > 0 && shown < target && after == target;
                }
            }
            return false;
        });
        check(ok, "list click hit-test sees the displayed (smoothed) scroll position and restores the target");
        ctx.waitTicks(30);

        // back: Video -> Options -> Pause -> game
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitForScreen(OptionsScreen.class);
        ctx.waitTicks(25);
        ctx.takeScreenshot("45-back-to-options");
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitForScreen(PauseScreen.class);
        ctx.waitTicks(25);
        closeScreenFast(ctx);
        check(screen(ctx) == null, "back to the world");
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════════
    private void recipeBook(ClientGameTestContext ctx) {
        info("== recipe book ==");
        resetCfg(ctx);
        ctx.runOnClient(mc -> {
            AnimConfig c = AnimConfigManager.get();
            c.recipeBook.durationMs = 1500; c.recipeBook.easing = Easing.LINEAR;
            c.inventoryOpen.durationMs = 40;
        });
        pressInventoryKey(ctx);
        ctx.waitForScreen(InventoryScreen.class);
        ctx.waitTicks(10);
        ctx.takeScreenshot("50-inventory-before-recipe-book");
        ctx.runOnClient(mc -> {
            AbstractRecipeBookScreen<?> s = (AbstractRecipeBookScreen<?>) mc.gui.screen();
            Object comp = field(s, AbstractRecipeBookScreen.class, "recipeBookComponent");
            try {
                comp.getClass().getMethod("toggleVisibility").invoke(comp);
            } catch (ReflectiveOperationException e) {
                // method lives on the RecipeBookComponent superclass
                try {
                    Class<?> k = Class.forName("net.minecraft.client.gui.screens.recipebook.RecipeBookComponent");
                    k.getMethod("toggleVisibility").invoke(comp);
                } catch (ReflectiveOperationException e2) {
                    throw new RuntimeException(e2);
                }
            }
        });
        ctx.waitTicks(6);
        float shift = ctx.computeOnClient(mc -> ((InventoryShiftProvider) mc.gui.screen()).animatedgui$screenInvShift());
        check(Math.abs(shift) > 1.0f, "recipe book open: inventory is sliding aside (shift=" + shift + ")");
        ctx.takeScreenshot("51-recipe-book-opening-mid");
        ctx.waitTicks(40);
        ctx.takeScreenshot("52-recipe-book-open-settled");
        float settled = ctx.computeOnClient(mc -> ((InventoryShiftProvider) mc.gui.screen()).animatedgui$screenInvShift());
        check(Math.abs(settled) < 0.5f, "recipe book settled (shift=" + settled + ")");
        closeScreenFast(ctx);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════════
    private void hotbar(ClientGameTestContext ctx) {
        info("== hotbar selector slide ==");
        resetCfg(ctx);
        ctx.runOnClient(mc -> {
            AnimConfig c = AnimConfigManager.get();
            c.hotbar.durationMs = 1500; c.hotbar.easing = Easing.LINEAR; c.hotbarTrail = true;
            mc.player.getInventory().setSelectedSlot(0);
        });
        ctx.waitTicks(40);
        ctx.takeScreenshot("60-hotbar-slot0");
        ctx.getInput().scroll(-3);
        ctx.waitTicks(6);
        ctx.takeScreenshot("61-hotbar-selector-sliding");
        int sel = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
        check(sel != 0, "scroll wheel moved the hotbar selection (slot " + sel + ")");
        ctx.waitTicks(40);
        resetCfg(ctx);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════════
    private void reducedMotion(ClientGameTestContext ctx) {
        info("== reduced motion ==");
        resetCfg(ctx);
        slow(ctx, ScreenStyle.SCALE, ScreenStyle.SLIDE_UP, 1800);
        ctx.runOnClient(mc -> AnimConfigManager.get().reducedMotion = true);
        int puts = PipCapture.puts;
        pressInventoryKey(ctx);
        ctx.waitForScreen(InventoryScreen.class);
        ctx.waitTick();
        ctx.takeScreenshot("70-reduced-motion-open-first-ticks");
        ctx.waitTicks(6);
        check(ctx.computeOnClient(mc -> ScreenAnimController.rawProgress(mc.gui.screen())) == 1.0f,
                "reduced motion: the (1800 ms configured) open finishes within ~100 ms");
        ctx.takeScreenshot("71-reduced-motion-settled");
        pressInventoryKey(ctx);
        ctx.waitTicks(6);
        check(screen(ctx) == null, "reduced motion: close finishes within ~100 ms");
        ctx.runOnClient(mc -> AnimConfigManager.get().reducedMotion = false);
        // and back to normal: long again
        slow(ctx, ScreenStyle.SCALE, ScreenStyle.SLIDE_UP, 1800);
        pressInventoryKey(ctx);
        ctx.waitForScreen(InventoryScreen.class);
        ctx.waitTicks(4);
        check(ctx.computeOnClient(mc -> ScreenAnimController.rawProgress(mc.gui.screen())) < 1.0f,
                "reduced motion off: long animations are back");
        closeScreenFast(ctx);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════════
    private void deniedScreen(ClientGameTestContext ctx) {
        info("== denied screen ==");
        resetCfg(ctx);
        slow(ctx, ScreenStyle.SCALE, ScreenStyle.SLIDE_UP, 1800);
        ctx.runOnClient(mc -> AnimConfigManager.setDenied(InventoryScreen.class.getName(), true));
        pressInventoryKey(ctx);
        ctx.waitForScreen(InventoryScreen.class);
        ctx.waitTick();
        ctx.takeScreenshot("80-denied-InventoryScreen-open-full-size-immediately");
        check(ctx.computeOnClient(mc -> ScreenAnimController.rawProgress(mc.gui.screen())) == 1.0f, "denied screen is not animated on open");
        pressInventoryKey(ctx);
        ctx.waitTicks(2);
        check(screen(ctx) == null, "denied screen closes instantly (no deferred close)");
        // a different, non-denied screen is still animated
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitForScreen(PauseScreen.class);
        ctx.runOnClient(mc -> { AnimConfigManager.get().menuOpen.durationMs = 1500; });
        closeScreenFast(ctx);
        ctx.runOnClient(mc -> AnimConfigManager.setDenied(InventoryScreen.class.getName(), false));
        check(ctx.computeOnClient(mc -> !AnimConfigManager.get().deniedScreens.contains(InventoryScreen.class.getName())),
                "deny list entry removed again");
        resetCfg(ctx);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════════
    private void configScreen(ClientGameTestContext ctx) {
        info("== settings screen ==");
        resetCfg(ctx);
        ctx.setScreen(() -> new AnimatedGuiScreen(null));
        ctx.waitForScreen(AnimatedGuiScreen.class);
        ctx.waitTicks(15);
        int w = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
        int h = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
        final float fit = Math.min(1.0f, (w - 4) / 480.0f);
        info("settings panel fit scale = " + fit + " (gui " + w + "x" + h + ")");
        int panelTop = Math.max(8, h / 2 - 120);
        int sidebarX = w / 2 - 240 + 12;
        int tabTop = panelTop + 40;
        String[] names = {"general", "inventory", "game-menu", "chat", "screens"};
        for (int i = 0; i < names.length; i++) {
            clickPanel(ctx, fit, w, h, sidebarX + 40, tabTop + i * 28 + 12);
            ctx.waitTicks(14);
            ctx.takeScreenshot("90-config-tab-" + (i + 1) + "-" + names[i]);
        }
        // Screens tab: lists screen classes seen so far; flip one off and on
        int listTop = panelTop + 42 + 14;
        int toggleX = w / 2 - 240 + 118 + 12;
        ctx.runOnClient(mc -> info("known screens: " + AnimConfigManager.get().knownScreens));
        check(ctx.computeOnClient(mc -> !AnimConfigManager.get().knownScreens.isEmpty()), "screens seen in-game are remembered for the Screens tab");
        int deniedBefore = ctx.computeOnClient(mc -> AnimConfigManager.get().deniedScreens.size());
        clickPanel(ctx, fit, w, h, toggleX, listTop + 8);
        ctx.waitTicks(6);
        ctx.takeScreenshot("91-config-screens-first-entry-denied");
        int deniedAfter = ctx.computeOnClient(mc -> AnimConfigManager.get().deniedScreens.size());
        check(deniedAfter == deniedBefore + 1, "clicking a screen's switch adds it to the deny list");
        clickPanel(ctx, fit, w, h, toggleX, listTop + 8);
        ctx.waitTicks(6);
        check(ctx.computeOnClient(mc -> AnimConfigManager.get().deniedScreens.size()) == deniedBefore, "clicking again removes it");

        // General tab: toggle reduced motion with the real switch
        clickPanel(ctx, fit, w, h, sidebarX + 40, tabTop + 12);
        ctx.waitTicks(10);
        int rowH = 20;
        int rmY = panelTop + 42 + rowH + 8; // 2nd row (Reduced motion)
        clickPanel(ctx, fit, w, h, w / 2 - 240 + 118 + 88 + 12, rmY);
        ctx.waitTicks(10);
        check(ctx.computeOnClient(mc -> AnimConfigManager.get().reducedMotion), "Reduced motion switch works");
        ctx.takeScreenshot("92-config-general-reduced-motion-on");
        clickPanel(ctx, fit, w, h, w / 2 - 240 + 118 + 88 + 12, rmY);
        ctx.waitTicks(6);
        check(ctx.computeOnClient(mc -> !AnimConfigManager.get().reducedMotion), "Reduced motion switch toggles back off");

        // Simulated Smooth Swapping / Smooth Scrolling: the overlapping features yield and the screen says so
        ctx.runOnClient(mc -> {
            AnimConfig c = AnimConfigManager.get();
            c.items.yieldedTo = "Smooth Swapping";
            c.listScroll.yieldedTo = "Smooth Scrolling";
            check(!c.items.on() && !c.listScroll.on() && c.hotbar.on() && c.chat.on(),
                    "only the overlapping features yield (items, list scroll); the rest keep running");
        });
        ctx.waitTicks(4);
        ctx.takeScreenshot("93-config-general-listscroll-yielded");
        clickPanel(ctx, fit, w, h, sidebarX + 40, tabTop + 28 + 12);
        ctx.waitTicks(14);
        ctx.takeScreenshot("94-config-inventory-items-yielded");
        ctx.runOnClient(mc -> {
            AnimConfig c = AnimConfigManager.get();
            c.items.yieldedTo = null; c.listScroll.yieldedTo = null;
        });
        closeScreenFast(ctx);
        resetCfg(ctx);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════════════════════
    private void creative(ClientGameTestContext ctx, net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world, List<String> seen) {
        info("== creative inventory (issue #3) ==");
        resetCfg(ctx);
        world.getServer().runCommand("gamemode creative @a");
        ctx.waitTicks(12);
        seen.clear();
        // identical sequence to the BEFORE-fix baseline run: key, one tick, screenshot of the first frames
        pressInventoryKey(ctx);
        ctx.waitTick();
        ctx.takeScreenshot("100-creative-first-frame-AFTER-fix");
        ctx.waitTicks(6);
        info("creative open tick log: " + seen);
        check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof CreativeModeInventoryScreen), "creative screen is open");
        check(seen.stream().noneMatch(s -> s.equals("InventoryScreen")), "no phantom InventoryScreen tick while opening creative");
        ctx.waitTicks(15);
        ctx.takeScreenshot("101-creative-settled");
        closeScreenFast(ctx);

        // slow open: first frames must be a tiny/invisible panel at the centre, never at the top-left
        slow(ctx, ScreenStyle.SCALE, ScreenStyle.FADE, 1500);
        seen.clear();
        pressInventoryKey(ctx);
        ctx.waitTick();
        ctx.takeScreenshot("102-creative-first-frame-slow-SCALE");
        ctx.waitTicks(10);
        ctx.takeScreenshot("103-creative-open-SCALE-mid");
        check(seen.stream().noneMatch(s -> s.equals("InventoryScreen")), "no phantom InventoryScreen (slow open)");
        ctx.waitTicks(40);

        // creative pixel scroll + tab switch
        resetCfg(ctx);
        ctx.runOnClient(mc -> {
            AnimConfig c = AnimConfigManager.get();
            c.creativeScroll.durationMs = 1500; c.creativeScroll.easing = Easing.LINEAR;
            c.inventoryOpen.durationMs = 40;
        });
        AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) screen(ctx);
        if (!(cs instanceof CreativeModeInventoryScreen)) {
            closeScreenFast(ctx);
            pressInventoryKey(ctx);
            ctx.waitForScreen(CreativeModeInventoryScreen.class);
            ctx.waitTicks(10);
            cs = (AbstractContainerScreen<?>) screen(ctx);
        }
        // scroll the grid with the wheel
        cursorGui(ctx, leftPos(cs) + 9 + 40, topPos(cs) + 18 + 40);
        ctx.waitTick();
        ctx.getInput().scroll(-2);
        ctx.waitTicks(8);
        ctx.takeScreenshot("104-creative-pixel-scroll-mid");
        check(CreativeScroll.active, "creative grid is pixel-sliding mid-scroll (offset=" + CreativeScroll.pixelOffset + "px)");
        ctx.waitTicks(40);
        // tab switch: second top tab
        final AbstractContainerScreen<?> cs2 = (AbstractContainerScreen<?>) screen(ctx);
        click(ctx, leftPos(cs2) + 27 + 13, topPos(cs2) - 14);
        ctx.waitTicks(10);
        ctx.takeScreenshot("105-creative-tab-switch");
        ctx.getInput().scroll(-1);
        ctx.waitTicks(6);
        ctx.takeScreenshot("106-creative-scroll-after-tab-switch");
        closeScreenFast(ctx);
        check(screen(ctx) == null, "creative closes");
        resetCfg(ctx);
    }
}
