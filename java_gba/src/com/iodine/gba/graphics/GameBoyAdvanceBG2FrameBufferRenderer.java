package com.iodine.gba.graphics;

/**
 * GameBoyAdvanceBG2FrameBufferRenderer - Bitmap mode renderer (converted from BG2FrameBuffer.js)
 * Copyright (C) 2012-2016 Grant Galitz
 *
 * Handles GBA bitmap modes where pixels are stored directly in VRAM:
 * - Mode 3: 240x160, 16-bit BGR555 color, single framebuffer
 * - Mode 4: 240x160, 8-bit paletted, double-buffered
 * - Mode 5: 160x128, 16-bit BGR555 color, double-buffered
 */
public class GameBoyAdvanceBG2FrameBufferRenderer {
    public GameBoyAdvanceRenderer gfx;

    // References to parent renderer's data
    public int[] palette;
    public byte[] VRAM;
    public java.nio.ShortBuffer VRAM16;

    // Frame selection for double-buffered modes (mode 4 and 5)
    public int frameSelect;

    // Current pixel fetch function (changes based on mode)
    private PixelFetcher fetchPixel;

    // Functional interface for pixel fetching
    @FunctionalInterface
    private interface PixelFetcher {
        int fetch(int x, int y);
    }

    public GameBoyAdvanceBG2FrameBufferRenderer(GameBoyAdvanceRenderer gfx) {
        this.gfx = gfx;
    }

    public void initialize() {
        // Get references from parent renderer
        palette = gfx.palette256;
        VRAM = gfx.VRAM;
        VRAM16 = gfx.VRAM16;
        // Default to mode 3
        fetchPixel = this::fetchMode3Pixel;
        frameSelect = 0;
    }

    /**
     * Select the bitmap rendering mode.
     *
     * @param mode Bitmap mode (3, 4, or 5)
     */
    public void selectMode(int mode) {
        switch (mode) {
            case 3:
                fetchPixel = this::fetchMode3Pixel;
                break;
            case 4:
                fetchPixel = this::fetchMode4Pixel;
                break;
            case 5:
                fetchPixel = this::fetchMode5Pixel;
                break;
        }
    }

    /**
     * Fetch a pixel using the current mode's fetch function.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @return Pixel value (15-bit BGR555 or transparency)
     */
    public int getPixel(int x, int y) {
        return fetchPixel.fetch(x, y);
    }

    /**
     * Mode 3: 240x160, 16-bit direct color.
     * Single framebuffer at VRAM offset 0.
     *
     * @param x X coordinate (0-239)
     * @param y Y coordinate (0-159)
     * @return Pixel value (15-bit BGR555) or transparency (0x3800000)
     */
    private int fetchMode3Pixel(int x, int y) {
        // Check bounds (using unsigned comparison)
        if ((x & 0xFFFFFF00) == 0 && x < 240 && (y & 0xFFFFFF00) == 0 && y < 160) {
            // Calculate address: y * 240 + x
            int address = (y * 240 + x) & 0xFFFF;
            return VRAM16.get(address) & 0x7FFF;
        }
        // Out of range, output transparency
        return 0x3800000;
    }

    /**
     * Mode 4: 240x160, 8-bit paletted.
     * Two framebuffers: 0x0000 and 0xA000.
     *
     * @param x X coordinate (0-239)
     * @param y Y coordinate (0-159)
     * @return Palette color or transparency (0x3800000)
     */
    private int fetchMode4Pixel(int x, int y) {
        // Check bounds
        if ((x & 0xFFFFFF00) == 0 && x < 240 && (y & 0xFFFFFF00) == 0 && y < 160) {
            // Calculate address: frameSelect + y * 240 + x
            int address = (frameSelect + y * 240 + x) & 0x1FFFF;
            int paletteIndex = VRAM[address] & 0xFF;
            return palette[paletteIndex];
        }
        // Out of range, output transparency
        return 0x3800000;
    }

    /**
     * Mode 5: 160x128, 16-bit direct color.
     * Two framebuffers: 0x0000 and 0xA000.
     *
     * @param x X coordinate (0-159)
     * @param y Y coordinate (0-127)
     * @return Pixel value (15-bit BGR555) or transparency (0x3800000)
     */
    private int fetchMode5Pixel(int x, int y) {
        // Check bounds (160x128)
        if ((x & 0xFFFFFF00) == 0 && x < 160 && (y & 0xFFFFFF80) == 0 && y < 128) {
            // Calculate address: (frameSelect + y * 160 + x)
            // frameSelect is already in 16-bit word units, so divide by 2
            int address = ((frameSelect >> 1) + y * 160 + x) & 0xFFFF;
            return VRAM16.get(address) & 0x7FFF;
        }
        // Out of range, output transparency
        return 0x3800000;
    }

    /**
     * Write frame select register.
     * Bit 4 of DISPCNT controls which framebuffer is displayed in modes 4 and 5.
     *
     * @param value Frame select bit (shifted and masked)
     */
    public void writeFrameSelect(int value) {
        // Extract bit 31 (which is bit 4 of DISPCNT shifted left 27 positions)
        // and convert to framebuffer offset: 0x0000 or 0xA000
        int frameSelectBit = value >>> 31;
        frameSelect = frameSelectBit & 0xA000;
    }
}
