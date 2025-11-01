package com.iodine.gba.graphics;

/**
 * GameBoyAdvanceOBJWindowCompositor - Object window compositor (converted from Compositor.js)
 * Copyright (C) 2012-2016 Grant Galitz
 */
public class GameBoyAdvanceOBJWindowCompositor {
    public GameBoyAdvanceRenderer gfx;
    public int[] buffer;
    public GameBoyAdvanceColorEffectsRenderer colorEffectsRenderer;
    public int[] OBJWindowBuffer;
    public int doEffects;

    public GameBoyAdvanceOBJWindowCompositor(GameBoyAdvanceRenderer gfx) {
        this.gfx = gfx;
        this.doEffects = 0;
    }

    public void initialize() {
        this.buffer = gfx.buffer;
        this.colorEffectsRenderer = gfx.colorEffectsRenderer;
        this.OBJWindowBuffer = gfx.objRenderer.scratchWindowBuffer;
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

        if (layers == 0) {
            for (int xStart = 0; xStart < 240; xStart++) {
                if (OBJWindowBuffer[xStart] < 0x3800000) {
                    buffer[xStart] = backdrop;
                }
            }
            return;
        }

        boolean hasSprites = (layers & 0x10) != 0;

        for (int xStart = 0; xStart < 240; xStart++) {
            // Only render pixels where OBJ window is active
            if (OBJWindowBuffer[xStart] < 0x3800000) {
                int currentPixel = backdrop;
                int lowerPixel = backdrop;

                if ((layers & 0x8) != 0) {
                    int[] result = compositeLayer(xStart | 0x400, currentPixel, lowerPixel);
                    currentPixel = result[0];
                    lowerPixel = result[1];
                }
                if ((layers & 0x4) != 0) {
                    int[] result = compositeLayer(xStart | 0x300, currentPixel, lowerPixel);
                    currentPixel = result[0];
                    lowerPixel = result[1];
                }
                if ((layers & 0x2) != 0) {
                    int[] result = compositeLayer(xStart | 0x200, currentPixel, lowerPixel);
                    currentPixel = result[0];
                    lowerPixel = result[1];
                }
                if ((layers & 0x1) != 0) {
                    int[] result = compositeLayer(xStart | 0x100, currentPixel, lowerPixel);
                    currentPixel = result[0];
                    lowerPixel = result[1];
                }

                if (hasSprites) {
                    int[] result = compositeLayer(xStart | 0x500, currentPixel, lowerPixel);
                    currentPixel = result[0];
                    lowerPixel = result[1];

                    if ((currentPixel & 0x400000) == 0) {
                        buffer[xStart] = currentPixel;
                    } else {
                        buffer[xStart] = colorEffectsRenderer.processPixelSprite(lowerPixel, currentPixel);
                    }
                } else {
                    buffer[xStart] = currentPixel;
                }
            }
        }
    }

    private void renderScanLineWithEffects(int layers) {
        int backdrop = gfx.backdrop;

        if (layers == 0) {
            for (int xStart = 0; xStart < 240; xStart++) {
                if (OBJWindowBuffer[xStart] < 0x3800000) {
                    buffer[xStart] = colorEffectsRenderer.processPixelNormal(0, backdrop);
                }
            }
            return;
        }

        boolean hasSprites = (layers & 0x10) != 0;

        for (int xStart = 0; xStart < 240; xStart++) {
            if (OBJWindowBuffer[xStart] < 0x3800000) {
                int currentPixel = backdrop;
                int lowerPixel = backdrop;

                if ((layers & 0x8) != 0) {
                    int[] result = compositeLayer(xStart | 0x400, currentPixel, lowerPixel);
                    currentPixel = result[0];
                    lowerPixel = result[1];
                }
                if ((layers & 0x4) != 0) {
                    int[] result = compositeLayer(xStart | 0x300, currentPixel, lowerPixel);
                    currentPixel = result[0];
                    lowerPixel = result[1];
                }
                if ((layers & 0x2) != 0) {
                    int[] result = compositeLayer(xStart | 0x200, currentPixel, lowerPixel);
                    currentPixel = result[0];
                    lowerPixel = result[1];
                }
                if ((layers & 0x1) != 0) {
                    int[] result = compositeLayer(xStart | 0x100, currentPixel, lowerPixel);
                    currentPixel = result[0];
                    lowerPixel = result[1];
                }

                if (hasSprites) {
                    int[] result = compositeLayer(xStart | 0x500, currentPixel, lowerPixel);
                    currentPixel = result[0];
                    lowerPixel = result[1];

                    if ((currentPixel & 0x400000) == 0) {
                        buffer[xStart] = colorEffectsRenderer.processPixelNormal(lowerPixel, currentPixel);
                    } else {
                        buffer[xStart] = colorEffectsRenderer.processPixelSprite(lowerPixel, currentPixel);
                    }
                } else {
                    buffer[xStart] = colorEffectsRenderer.processPixelNormal(lowerPixel, currentPixel);
                }
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
