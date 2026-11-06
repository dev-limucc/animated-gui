package dev.limucc.animatedgui.client.compat;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Era facade: the only place that names the render-API types whose package moved between Minecraft versions
 * (26.3 relocated RenderPipeline / GpuTextureView / GpuSampler out of {@code com.mojang.blaze3d}). Everything else
 * in the mod is era-agnostic and reaches these through the constants and helpers below. One copy of this file
 * lives in each {@code src/client/versions/render-*} overlay; the build picks the one matching {@code -Ptarget}.
 */
public final class GfxCompat {

    private GfxCompat() {}

    /** {@code GuiGraphicsExtractor.innerBlit(pipeline, textureView, sampler, x0,y0,x1,y1, u0,u1,v0,v1, color)}. */
    public static final String INNER_BLIT =
            "innerBlit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lcom/mojang/renderpearl/api/textures/GpuSampler;IIIIFFFFI)V";

    /** The {@code GuiGraphicsExtractor.blitSprite(pipeline, sprite, x, y, w, h)} call the hotbar selector wraps. */
    public static final String BLIT_SPRITE_CALL =
            "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;"
                    + "Lnet/minecraft/resources/Identifier;IIII)V";

    /** Draw a sprite with an alpha multiplier; {@code pipeline} is the (era-specific) RenderPipeline object. */
    public static void blitSpriteAlpha(
            GuiGraphicsExtractor g, Object pipeline, Identifier sprite, int x, int y, int w, int h, float alpha) {
        g.blitSprite((RenderPipeline) pipeline, sprite, x, y, w, h, alpha);
    }
}
