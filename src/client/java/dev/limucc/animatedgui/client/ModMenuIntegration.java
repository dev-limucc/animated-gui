package dev.limucc.animatedgui.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.limucc.animatedgui.client.gui.AnimatedGuiScreen;

/** Opens the Animated GUI settings screen from the ModMenu mod list. */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return AnimatedGuiScreen::new;
    }
}
