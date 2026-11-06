package dev.limucc.animatedgui.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.limucc.animatedgui.AnimatedGui;
import dev.limucc.animatedgui.client.compat.ModCompat;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Loads/saves {@link AnimConfig} to {@code config/animatedgui.json}. Pure client state, no server involved. */
public final class AnimConfigManager {

    private AnimConfigManager() {}

    /** How many "seen" screen classes we remember for the settings screen's Screens tab. */
    private static final int MAX_KNOWN = 48;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("animatedgui.json");

    private static AnimConfig instance = new AnimConfig();

    /** Bumped whenever the deny list changes so {@code ScreenAnimController}'s per-class cache knows to refill. */
    private static int denyVersion;
    private static boolean dirty;
    private static final Set<String> knownLookup = new HashSet<>();

    public static AnimConfig get() {
        return instance;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                AnimConfig loaded = GSON.fromJson(r, AnimConfig.class);
                instance = (loaded != null) ? loaded : new AnimConfig();
            } catch (Exception e) {
                AnimatedGui.LOGGER.error("Failed to load Animated GUI config, using defaults.", e);
                instance = new AnimConfig();
            }
        } else {
            instance = new AnimConfig();
        }
        instance.normalize();
        knownLookup.clear();
        knownLookup.addAll(instance.knownScreens);
        ModCompat.apply(instance);
        denyVersion++;
        if (!Files.exists(CONFIG_PATH)) save();
    }

    public static void save() {
        dirty = false;
        try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(instance, w);
        } catch (IOException e) {
            AnimatedGui.LOGGER.error("Failed to save Animated GUI config.", e);
        }
    }

    // ── per-screen deny list ──────────────────────────────────────────────────────

    /** Incremented on every deny-list edit; callers cache per-class answers and refill when it changes. */
    public static int denyVersion() {
        return denyVersion;
    }

    /** True if {@code screenClass} (fully-qualified name) or its simple name is on the deny list. */
    public static boolean isDenied(Class<?> screenClass) {
        var denied = instance.deniedScreens;
        if (denied.isEmpty()) return false;
        return denied.contains(screenClass.getName()) || denied.contains(screenClass.getSimpleName());
    }

    /** Flip a screen class on/off the deny list (matches either spelling already present). */
    public static void setDenied(String className, boolean deny) {
        var denied = instance.deniedScreens;
        String simple = className.substring(className.lastIndexOf('.') + 1);
        denied.remove(className);
        denied.remove(simple);
        if (deny) denied.add(className);
        denyVersion++;
        dirty = true;
    }

    /** Remember that a screen of this class was shown, so the Screens tab can list it. Cheap after the first time. */
    public static void noteScreen(Class<?> screenClass) {
        String name = screenClass.getName();
        if (!knownLookup.add(name)) return;
        var known = instance.knownScreens;
        known.add(name);
        while (known.size() > MAX_KNOWN) {
            // forget the oldest entry that isn't denied (denied ones must stay visible so they can be re-enabled)
            int drop = -1;
            for (int i = 0; i < known.size(); i++) {
                String k = known.get(i);
                if (!instance.deniedScreens.contains(k)) { drop = i; break; }
            }
            if (drop < 0) break;
            knownLookup.remove(known.remove(drop));
        }
        dirty = true;
    }

    /** Writes the config if something changed behind the user's back (screens seen / deny list). */
    public static void saveIfDirty() {
        if (dirty) save();
    }
}
