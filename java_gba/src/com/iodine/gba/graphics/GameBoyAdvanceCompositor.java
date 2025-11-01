package com.iodine.gba.graphics;

/**
 * GameBoyAdvanceCompositor - Layer composition (converted from Compositor.js)
 * Copyright (C) 2012-2016 Grant Galitz
 *
 * This compositor combines multiple background layers and sprites into the final output,
 * handling priority ordering and color effects. The original JavaScript used dynamic code
 * generation to create 192 optimized functions. This Java version implements the same logic
 * using a more maintainable approach.
 */
public class GameBoyAdvanceCompositor {
    public GameBoyAdvanceRenderer gfx;
    public int[] buffer;
    public GameBoyAdvanceColorEffectsRenderer colorEffectsRenderer;
    public int doEffects;

    public GameBoyAdvanceCompositor(GameBoyAdvanceRenderer gfx) {
        this.gfx = gfx;
        this.doEffects = 0;
    }

    public void initialize() {
        this.buffer = gfx.buffer;
        this.colorEffectsRenderer = gfx.colorEffectsRenderer;
    }

    public void preprocess(int doEffects) {
        this.doEffects = doEffects;
    }

    public void renderScanLine(int layers) {
        if (doEffects == 0) {
            renderNormalScanLine(layers);
        } else {
            renderScanLineWithEffects(layers);
        }
    }

    private void renderNormalScanLine(int layers) {
        int backdrop = gfx.backdrop;

        // Handle the simple no-layer case
        if (layers == 0) {
            for (int xStart = 0; xStart < 240; xStart++) {
                buffer[xStart] = backdrop;
            }
            return;
        }

        // Check if we have sprites
        boolean hasSprites = (layers & 0x10) != 0;

        for (int xStart = 0; xStart < 240; xStart++) {
            int currentPixel = backdrop;
            int lowerPixel = backdrop;

            // Composite layers from back to front based on priority
            // Layers are stored in buffer at offsets: BG0=0x100, BG1=0x200, BG2=0x300, BG3=0x400, OBJ=0x500

            // BG3
            if ((layers & 0x8) != 0) {
                int[] result = compositeLayer(xStart | 0x400, currentPixel, lowerPixel);
                currentPixel = result[0];
                lowerPixel = result[1];
            }

            // BG2
            if ((layers & 0x4) != 0) {
                int[] result = compositeLayer(xStart | 0x300, currentPixel, lowerPixel);
                currentPixel = result[0];
                lowerPixel = result[1];
            }

            // BG1
            if ((layers & 0x2) != 0) {
                int[] result = compositeLayer(xStart | 0x200, currentPixel, lowerPixel);
                currentPixel = result[0];
                lowerPixel = result[1];
            }

            // BG0
            if ((layers & 0x1) != 0) {
                int[] result = compositeLayer(xStart | 0x100, currentPixel, lowerPixel);
                currentPixel = result[0];
                lowerPixel = result[1];
            }

            // OBJ (sprites)
            if (hasSprites) {
                int[] result = compositeLayer(xStart | 0x500, currentPixel, lowerPixel);
                currentPixel = result[0];
                lowerPixel = result[1];

                // Handle sprite semi-transparency
                if ((currentPixel & 0x400000) == 0) {
                    // Normal sprite - no special handling needed
                    buffer[xStart] = currentPixel;
                } else {
                    // Semi-transparent sprite - must handle blending
                    buffer[xStart] = colorEffectsRenderer.processPixelSprite(lowerPixel, currentPixel);
                }
            } else {
                // No sprites - just write the pixel
                buffer[xStart] = currentPixel;
            }
        }
    }

    private void renderScanLineWithEffects(int layers) {
        int backdrop = gfx.backdrop;

        // Handle the simple no-layer case
        if (layers == 0) {
            for (int xStart = 0; xStart < 240; xStart++) {
                buffer[xStart] = colorEffectsRenderer.processPixelNormal(0, backdrop);
            }
            return;
        }

        // Check if we have sprites
        boolean hasSprites = (layers & 0x10) != 0;

        for (int xStart = 0; xStart < 240; xStart++) {
            int currentPixel = backdrop;
            int lowerPixel = backdrop;

            // Composite layers from back to front based on priority
            // BG3
            if ((layers & 0x8) != 0) {
                int[] result = compositeLayer(xStart | 0x400, currentPixel, lowerPixel);
                currentPixel = result[0];
                lowerPixel = result[1];
            }

            // BG2
            if ((layers & 0x4) != 0) {
                int[] result = compositeLayer(xStart | 0x300, currentPixel, lowerPixel);
                currentPixel = result[0];
                lowerPixel = result[1];
            }

            // BG1
            if ((layers & 0x2) != 0) {
                int[] result = compositeLayer(xStart | 0x200, currentPixel, lowerPixel);
                currentPixel = result[0];
                lowerPixel = result[1];
            }

            // BG0
            if ((layers & 0x1) != 0) {
                int[] result = compositeLayer(xStart | 0x100, currentPixel, lowerPixel);
                currentPixel = result[0];
                lowerPixel = result[1];
            }

            // OBJ (sprites)
            if (hasSprites) {
                int[] result = compositeLayer(xStart | 0x500, currentPixel, lowerPixel);
                currentPixel = result[0];
                lowerPixel = result[1];

                // Handle sprite with effects
                if ((currentPixel & 0x400000) == 0) {
                    // Normal sprite with color effects
                    buffer[xStart] = colorEffectsRenderer.processPixelNormal(lowerPixel, currentPixel);
                } else {
                    // Semi-transparent sprite
                    buffer[xStart] = colorEffectsRenderer.processPixelSprite(lowerPixel, currentPixel);
                }
            } else {
                // No sprites - apply color effects
                buffer[xStart] = colorEffectsRenderer.processPixelNormal(lowerPixel, currentPixel);
            }
        }
    }

    /**
     * Composite a layer with priority checking.
     * Pixels have priority encoded in bits 25-23 (0x3800000 for working, 0x1800000 for current/lower).
     * Bit 25 (0x2000000) indicates transparent pixel.
     *
     * @param offset Buffer offset for the layer to composite
     * @param currentPixel Current top pixel
     * @param lowerPixel Current second pixel
     * @return Array of [newCurrentPixel, newLowerPixel]
     */
    private int[] compositeLayer(int offset, int currentPixel, int lowerPixel) {
        int workingPixel = buffer[offset];

        // Skip transparent pixels (bit 25 set)
        if ((workingPixel & 0x2000000) != 0) {
            return new int[]{currentPixel, lowerPixel};
        }

        // Check if working pixel has higher priority (lower value) than current
        if ((workingPixel & 0x3800000) <= (currentPixel & 0x1800000)) {
            // Working pixel becomes new current, current becomes new lower
            lowerPixel = currentPixel;
            currentPixel = workingPixel;
        }
        // Check if working pixel should be the new lower pixel
        else if ((workingPixel & 0x3800000) <= (lowerPixel & 0x1800000)) {
            lowerPixel = workingPixel;
        }

        return new int[]{currentPixel, lowerPixel};
    }
}
