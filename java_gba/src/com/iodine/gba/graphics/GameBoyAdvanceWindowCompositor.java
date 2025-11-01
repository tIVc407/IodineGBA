package com.iodine.gba.graphics;

/**
 * GameBoyAdvanceWindowCompositor - Window compositor (converted from Compositor.js)
 * Copyright (C) 2012-2016 Grant Galitz
 */
public class GameBoyAdvanceWindowCompositor {
    public GameBoyAdvanceRenderer gfx;
    public int[] buffer;
    public GameBoyAdvanceColorEffectsRenderer colorEffectsRenderer;
    public int doEffects;

    public GameBoyAdvanceWindowCompositor(GameBoyAdvanceRenderer gfx) {
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

    public void renderScanLine(int xStart, int xEnd, int layers) {
        if (doEffects == 0) {
            renderNormalScanLine(xStart, xEnd, layers);
        } else {
            renderScanLineWithEffects(xStart, xEnd, layers);
        }
    }

    private void renderNormalScanLine(int xStart, int xEnd, int layers) {
        int backdrop = gfx.backdrop;

        // Handle the simple no-layer case
        if (layers == 0) {
            for (int x = xStart; x < xEnd; x++) {
                buffer[x] = backdrop;
            }
            return;
        }

        boolean hasSprites = (layers & 0x10) != 0;

        for (int x = xStart; x < xEnd; x++) {
            int currentPixel = backdrop;
            int lowerPixel = backdrop;

            // Composite layers from back to front
            if ((layers & 0x8) != 0) {
                int[] result = compositeLayer(x | 0x400, currentPixel, lowerPixel);
                currentPixel = result[0];
                lowerPixel = result[1];
            }
            if ((layers & 0x4) != 0) {
                int[] result = compositeLayer(x | 0x300, currentPixel, lowerPixel);
                currentPixel = result[0];
                lowerPixel = result[1];
            }
            if ((layers & 0x2) != 0) {
                int[] result = compositeLayer(x | 0x200, currentPixel, lowerPixel);
                currentPixel = result[0];
                lowerPixel = result[1];
            }
            if ((layers & 0x1) != 0) {
                int[] result = compositeLayer(x | 0x100, currentPixel, lowerPixel);
                currentPixel = result[0];
                lowerPixel = result[1];
            }

            if (hasSprites) {
                int[] result = compositeLayer(x | 0x500, currentPixel, lowerPixel);
                currentPixel = result[0];
                lowerPixel = result[1];

                if ((currentPixel & 0x400000) == 0) {
                    buffer[x] = currentPixel;
                } else {
                    buffer[x] = colorEffectsRenderer.processPixelSprite(lowerPixel, currentPixel);
                }
            } else {
                buffer[x] = currentPixel;
            }
        }
    }

    private void renderScanLineWithEffects(int xStart, int xEnd, int layers) {
        int backdrop = gfx.backdrop;

        if (layers == 0) {
            for (int x = xStart; x < xEnd; x++) {
                buffer[x] = colorEffectsRenderer.processPixelNormal(0, backdrop);
            }
            return;
        }

        boolean hasSprites = (layers & 0x10) != 0;

        for (int x = xStart; x < xEnd; x++) {
            int currentPixel = backdrop;
            int lowerPixel = backdrop;

            if ((layers & 0x8) != 0) {
                int[] result = compositeLayer(x | 0x400, currentPixel, lowerPixel);
                currentPixel = result[0];
                lowerPixel = result[1];
            }
            if ((layers & 0x4) != 0) {
                int[] result = compositeLayer(x | 0x300, currentPixel, lowerPixel);
                currentPixel = result[0];
                lowerPixel = result[1];
            }
            if ((layers & 0x2) != 0) {
                int[] result = compositeLayer(x | 0x200, currentPixel, lowerPixel);
                currentPixel = result[0];
                lowerPixel = result[1];
            }
            if ((layers & 0x1) != 0) {
                int[] result = compositeLayer(x | 0x100, currentPixel, lowerPixel);
                currentPixel = result[0];
                lowerPixel = result[1];
            }

            if (hasSprites) {
                int[] result = compositeLayer(x | 0x500, currentPixel, lowerPixel);
                currentPixel = result[0];
                lowerPixel = result[1];

                if ((currentPixel & 0x400000) == 0) {
                    buffer[x] = colorEffectsRenderer.processPixelNormal(lowerPixel, currentPixel);
                } else {
                    buffer[x] = colorEffectsRenderer.processPixelSprite(lowerPixel, currentPixel);
                }
            } else {
                buffer[x] = colorEffectsRenderer.processPixelNormal(lowerPixel, currentPixel);
            }
        }
    }

    private int[] compositeLayer(int offset, int currentPixel, int lowerPixel) {
        int workingPixel = buffer[offset];

        if ((workingPixel & 0x2000000) != 0) {
            return new int[]{currentPixel, lowerPixel};
        }

        if ((workingPixel & 0x3800000) <= (currentPixel & 0x1800000)) {
            lowerPixel = currentPixel;
            currentPixel = workingPixel;
        } else if ((workingPixel & 0x3800000) <= (lowerPixel & 0x1800000)) {
            lowerPixel = workingPixel;
        }

        return new int[]{currentPixel, lowerPixel};
    }
}
