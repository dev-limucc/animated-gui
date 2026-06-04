package dev.limucc.animatedgui.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.limucc.animatedgui.AnimatedGui;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads/saves {@link AnimConfig} to {@code config/animatedgui.json}. Pure client state, no server involved. */
public final class AnimConfigManager {

    private AnimConfigManager() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("animatedgui.json");

    private static AnimConfig instance = new AnimConfig();

    public static AnimConfig get() {
        return instance;
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            AnimConfig loaded = GSON.fromJson(r, AnimConfig.class);
            instance = (loaded != null) ? loaded : new AnimConfig();
        } catch (Exception e) {
            AnimatedGui.LOGGER.error("Failed to load Animated GUI config, using defaults.", e);
            instance = new AnimConfig();
        }
        instance.normalize();
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(instance, w);
        } catch (IOException e) {
            AnimatedGui.LOGGER.error("Failed to save Animated GUI config.", e);
        }
    }
}
