package com.iodine.gba.graphics;

/**
 * GameBoyAdvanceColorEffectsRenderer - Color blending/effects renderer (converted from ColorEffects.js)
 * Copyright (C) 2012-2016 Grant Galitz
 *
 * Handles alpha blending, brightness increase/decrease effects on GBA graphics layers.
 * The GBA supports:
 * - Alpha blending between two target layers
 * - Brightness increase (fade to white)
 * - Brightness decrease (fade to black)
 */
public class GameBoyAdvanceColorEffectsRenderer {
    public int[] buffer;

    // Effect control registers
    public int effectsTarget1;           // Which layers are target 1 (bits 16-21 correspond to layers)
    public int colorEffectsType;         // 0=none, 1=alpha blend, 2=brightness up, 3=brightness down
    public int effectsTarget2;           // Which layers are target 2

    // Alpha blending amounts (0-16, where 16 is full)
    public int alphaBlendAmountTarget1;
    public int alphaBlendAmountTarget2;

    // Brightness effect amount (0-16)
    public int brightnessEffectAmount;

    public GameBoyAdvanceColorEffectsRenderer(int[] buffer) {
        this.buffer = buffer;
        this.effectsTarget1 = 0;
        this.colorEffectsType = 0;
        this.effectsTarget2 = 0;
        this.alphaBlendAmountTarget1 = 0;
        this.alphaBlendAmountTarget2 = 0;
        this.brightnessEffectAmount = 0;
    }

    /**
     * Alpha blend two pixels together.
     * Blends topPixel with lowerPixel using the blend amounts.
     *
     * @param topPixel Top layer pixel (15-bit BGR555)
     * @param lowerPixel Lower layer pixel (15-bit BGR555)
     * @return Blended pixel (15-bit BGR555)
     */
    public int alphaBlend(int topPixel, int lowerPixel) {
        // Extract BGR components (5 bits each)
        int b1 = (topPixel >> 10) & 0x1F;
        int g1 = (topPixel >> 5) & 0x1F;
        int r1 = topPixel & 0x1F;
        int b2 = (lowerPixel >> 10) & 0x1F;
        int g2 = (lowerPixel >> 5) & 0x1F;
        int r2 = lowerPixel & 0x1F;

        // Blend each component: (c1 * amount1 + c2 * amount2) / 16
        b1 = b1 * alphaBlendAmountTarget1;
        g1 = g1 * alphaBlendAmountTarget1;
        r1 = r1 * alphaBlendAmountTarget1;
        b2 = b2 * alphaBlendAmountTarget2;
        g2 = g2 * alphaBlendAmountTarget2;
        r2 = r2 * alphaBlendAmountTarget2;

        // Clamp to 5-bit range and combine
        int b = Math.min((b1 + b2) >> 4, 0x1F);
        int g = Math.min((g1 + g2) >> 4, 0x1F);
        int r = Math.min((r1 + r2) >> 4, 0x1F);

        return (b << 10) | (g << 5) | r;
    }

    /**
     * Increase brightness of a pixel (fade to white).
     *
     * @param topPixel Input pixel (15-bit BGR555)
     * @return Brightened pixel (15-bit BGR555)
     */
    public int brightnessIncrease(int topPixel) {
        // Extract BGR components
        int b1 = (topPixel >> 10) & 0x1F;
        int g1 = (topPixel >> 5) & 0x1F;
        int r1 = topPixel & 0x1F;

        // Add (max - current) * amount / 16 to each component
        b1 += ((0x1F - b1) * brightnessEffectAmount) >> 4;
        g1 += ((0x1F - g1) * brightnessEffectAmount) >> 4;
        r1 += ((0x1F - r1) * brightnessEffectAmount) >> 4;

        return (b1 << 10) | (g1 << 5) | r1;
    }

    /**
     * Decrease brightness of a pixel (fade to black).
     *
     * @param topPixel Input pixel (15-bit BGR555)
     * @return Darkened pixel (15-bit BGR555)
     */
    public int brightnessDecrease(int topPixel) {
        // Extract BGR components
        int b1 = (topPixel >> 10) & 0x1F;
        int g1 = (topPixel >> 5) & 0x1F;
        int r1 = topPixel & 0x1F;

        // Multiply by (16 - amount) / 16
        int decreaseMultiplier = 0x10 - brightnessEffectAmount;
        b1 = (b1 * decreaseMultiplier) >> 4;
        g1 = (g1 * decreaseMultiplier) >> 4;
        r1 = (r1 * decreaseMultiplier) >> 4;

        return (b1 << 10) | (g1 << 5) | r1;
    }

    /**
     * Process a pixel with normal color effects (non-sprite).
     * Checks if the pixel is in target 1 and applies the appropriate effect.
     *
     * @param lowerPixel Lower layer pixel
     * @param topPixel Top layer pixel
     * @return Processed pixel
     */
    public int processPixelNormal(int lowerPixel, int topPixel) {
        // Check if top pixel is in target 1
        if ((topPixel & effectsTarget1) != 0) {
            switch (colorEffectsType) {
                case 1:  // Alpha blending
                    // Check if lower pixel is in target 2 and pixels are different
                    if ((lowerPixel & effectsTarget2) != 0 && topPixel != lowerPixel) {
                        return alphaBlend(topPixel, lowerPixel);
                    }
                    break;
                case 2:  // Brightness increase
                    return brightnessIncrease(topPixel);
                case 3:  // Brightness decrease
                    return brightnessDecrease(topPixel);
            }
        }
        // No effect or effect doesn't apply - return original pixel
        return topPixel & 0x7FFF;  // Mask off priority bits
    }

    /**
     * Process a pixel from a semi-transparent sprite.
     * Semi-transparent sprites always blend with the layer below.
     *
     * @param lowerPixel Lower layer pixel
     * @param topPixel Top layer pixel (sprite)
     * @return Processed pixel
     */
    public int processPixelSprite(int lowerPixel, int topPixel) {
        // Check if lower pixel is in target 2 for sprite blending
        if ((lowerPixel & effectsTarget2) != 0) {
            return alphaBlend(topPixel, lowerPixel);
        }
        // Otherwise check if sprite is in target 1 for brightness effects
        else if ((topPixel & effectsTarget1) != 0) {
            switch (colorEffectsType) {
                case 2:  // Brightness increase
                    return brightnessIncrease(topPixel);
                case 3:  // Brightness decrease
                    return brightnessDecrease(topPixel);
            }
        }
        // No effect - return original pixel
        return topPixel & 0x7FFF;
    }

    // Register write methods

    /**
     * Write to BLDCNT register bits 0-7.
     * Controls which layers are target 1 and the color effect type.
     */
    public void writeBLDCNT8_0(int data) {
        // Select target 1 (bits 0-5) and color effects mode (bits 6-7)
        effectsTarget1 = (data & 0x3F) << 16;
        colorEffectsType = data >> 6;
    }

    /**
     * Write to BLDCNT register bits 8-15.
     * Controls which layers are target 2.
     */
    public void writeBLDCNT8_1(int data) {
        // Select target 2 (bits 0-5)
        effectsTarget2 = (data & 0x3F) << 16;
    }

    /**
     * Write to BLDCNT register (16-bit).
     */
    public void writeBLDCNT16(int data) {
        // Select target 1 and color effects mode
        effectsTarget1 = (data & 0x3F) << 16;
        colorEffectsType = (data >> 6) & 0x3;
        // Select target 2
        effectsTarget2 = (data & 0x3F00) << 8;
    }

    /**
     * Write to BLDALPHA register bits 0-7.
     * Controls blending amount for target 1 (EVA).
     */
    public void writeBLDALPHA8_0(int data) {
        int alphaBlendAmountTarget1Scratch = data & 0x1F;
        alphaBlendAmountTarget1 = Math.min(alphaBlendAmountTarget1Scratch, 0x10);
    }

    /**
     * Write to BLDALPHA register bits 8-15.
     * Controls blending amount for target 2 (EVB).
     */
    public void writeBLDALPHA8_1(int data) {
        int alphaBlendAmountTarget2Scratch = data & 0x1F;
        alphaBlendAmountTarget2 = Math.min(alphaBlendAmountTarget2Scratch, 0x10);
    }

    /**
     * Write to BLDALPHA register (16-bit).
     */
    public void writeBLDALPHA16(int data) {
        int alphaBlendAmountTarget1Scratch = data & 0x1F;
        alphaBlendAmountTarget1 = Math.min(alphaBlendAmountTarget1Scratch, 0x10);
        int alphaBlendAmountTarget2Scratch = (data >> 8) & 0x1F;
        alphaBlendAmountTarget2 = Math.min(alphaBlendAmountTarget2Scratch, 0x10);
    }

    /**
     * Write to BLDCNT and BLDALPHA registers (32-bit).
     */
    public void writeBLDCNT32(int data) {
        // Select target 1 and color effects mode
        effectsTarget1 = (data & 0x3F) << 16;
        colorEffectsType = (data >> 6) & 0x3;
        // Select target 2
        effectsTarget2 = (data & 0x3F00) << 8;
        // Set alpha blend amounts
        int alphaBlendAmountTarget1Scratch = (data >> 16) & 0x1F;
        alphaBlendAmountTarget1 = Math.min(alphaBlendAmountTarget1Scratch, 0x10);
        int alphaBlendAmountTarget2Scratch = (data >> 24) & 0x1F;
        alphaBlendAmountTarget2 = Math.min(alphaBlendAmountTarget2Scratch, 0x10);
    }

    /**
     * Write to BLDY register (8-bit).
     * Controls brightness increase/decrease amount (EVY).
     */
    public void writeBLDY8(int data) {
        brightnessEffectAmount = Math.min(data & 0x1F, 0x10);
    }
}
