package com.iodine.gba.graphics;

/**
 * GameBoyAdvanceMosaicRenderer - Mosaic effect renderer (converted from Mosaic.js)
 * Copyright (C) 2012-2015 Grant Galitz
 *
 * The mosaic effect creates a pixelated/blocky appearance by repeating pixels
 * in a grid pattern. The GBA supports separate mosaic settings for backgrounds
 * and sprites, with configurable horizontal and vertical sizes.
 */
public class GameBoyAdvanceMosaicRenderer {
    public int[] buffer;

    // Background mosaic settings (0-15, where 0 = disabled, 1-15 = block size)
    public int BGMosaicHSize;   // Horizontal size for backgrounds
    public int BGMosaicVSize;   // Vertical size for backgrounds

    // Object (sprite) mosaic settings
    public int OBJMosaicHSize;  // Horizontal size for sprites
    public int OBJMosaicVSize;  // Vertical size for sprites

    public GameBoyAdvanceMosaicRenderer(int[] buffer) {
        this.buffer = buffer;
        this.BGMosaicHSize = 0;
        this.BGMosaicVSize = 0;
        this.OBJMosaicHSize = 0;
        this.OBJMosaicVSize = 0;
    }

    /**
     * Render horizontal mosaic effect on a background layer.
     * Repeats pixels horizontally based on the mosaic size setting.
     *
     * @param offset Buffer offset for the layer to process
     */
    public void renderMosaicHorizontal(int offset) {
        int mosaicBlur = BGMosaicHSize + 1;
        if (mosaicBlur > 1) {  // Don't perform useless loop if mosaic disabled
            int currentPixel = 0;
            for (int position = 0; position < 240; position++) {
                if ((position % mosaicBlur) == 0) {
                    // Start of a new mosaic block - sample the pixel
                    currentPixel = buffer[position | offset];
                } else {
                    // Inside a mosaic block - repeat the sampled pixel
                    buffer[position | offset] = currentPixel;
                }
            }
        }
    }

    /**
     * Render horizontal mosaic effect on sprites.
     * Works similarly to background mosaic but with sprite-specific settings.
     *
     * @param xOffset Horizontal offset of the sprite
     * @param xSize Width of the sprite
     */
    public void renderOBJMosaicHorizontal(int xOffset, int xSize) {
        int mosaicBlur = OBJMosaicHSize + 1;
        if (mosaicBlur > 1) {  // Don't perform useless loop if mosaic disabled
            int currentPixel = 0x3800000;  // Transparent
            // Start at the mosaic-aligned position
            for (int position = xOffset % mosaicBlur; position < xSize; position++) {
                if ((position % mosaicBlur) == 0) {
                    // Start of a new mosaic block - sample the pixel
                    currentPixel = buffer[position | 0x600];
                }
                // Always write (even at block start) to apply mosaic
                buffer[position | 0x600] = currentPixel;
            }
        }
    }

    /**
     * Get the vertical mosaic offset for backgrounds.
     * Returns how many lines back to sample from for the mosaic effect.
     *
     * @param line Current scanline
     * @return Offset in lines (0 = use current line, >0 = use earlier line)
     */
    public int getMosaicYOffset(int line) {
        return line % (BGMosaicVSize + 1);
    }

    /**
     * Get the vertical mosaic offset for sprites.
     *
     * @param line Current scanline
     * @return Offset in lines
     */
    public int getOBJMosaicYOffset(int line) {
        return line % (OBJMosaicVSize + 1);
    }

    // Register write methods

    /**
     * Write to MOSAIC register bits 0-7.
     * Controls background mosaic horizontal and vertical sizes.
     */
    public void writeMOSAIC8_0(int data) {
        BGMosaicHSize = data & 0xF;
        BGMosaicVSize = data >> 4;
    }

    /**
     * Write to MOSAIC register bits 8-15.
     * Controls sprite mosaic horizontal and vertical sizes.
     */
    public void writeMOSAIC8_1(int data) {
        OBJMosaicHSize = data & 0xF;
        OBJMosaicVSize = data >> 4;
    }

    /**
     * Write to MOSAIC register (16-bit).
     * Bits 0-3: BG H size, 4-7: BG V size, 8-11: OBJ H size, 12-15: OBJ V size
     */
    public void writeMOSAIC16(int data) {
        BGMosaicHSize = data & 0xF;
        BGMosaicVSize = (data >> 4) & 0xF;
        OBJMosaicHSize = (data >> 8) & 0xF;
        OBJMosaicVSize = data >> 12;
    }
}
