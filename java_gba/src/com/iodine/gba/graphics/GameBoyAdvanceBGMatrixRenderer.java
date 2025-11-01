package com.iodine.gba.graphics;

/**
 * GameBoyAdvanceBGMatrixRenderer - Matrix background renderer (converted from BGMatrix.js)
 * Copyright (C) 2012-2015 Grant Galitz
 *
 * Handles tile-based rendering for affine backgrounds (modes 1-2).
 * Affine backgrounds use a tile map with 8bpp tiles and can be rotated/scaled.
 * Supports configurable screen sizes and overflow modes.
 */
public class GameBoyAdvanceBGMatrixRenderer {
    public GameBoyAdvanceRenderer gfx;

    // References to parent renderer's data
    public byte[] VRAM;
    public int[] palette;

    // Screen configuration
    public int mapSize;             // Tile map dimensions (16, 32, 64, or 128 tiles)
    public int mapSizeComparer;     // Mask for pixel coordinates (map size in pixels - 1)
    public int BGScreenBaseBlock;   // Tile map base address in VRAM
    public int BGCharacterBaseBlock; // Tile data base address in VRAM
    public int BGDisplayOverflow;   // Overflow mode (0=clamp, 1=wrap)

    // Current pixel fetch function (changes based on overflow mode)
    private PixelFetcher fetchPixel;

    @FunctionalInterface
    private interface PixelFetcher {
        int fetch(int x, int y);
    }

    public GameBoyAdvanceBGMatrixRenderer(GameBoyAdvanceRenderer gfx) {
        this.gfx = gfx;
    }

    public void initialize() {
        // Get references from parent renderer
        VRAM = gfx.VRAM;
        palette = gfx.palette256;
        // Initialize with default values
        screenSizePreprocess(0);
        screenBaseBlockPreprocess(0);
        characterBaseBlockPreprocess(0);
        displayOverflowProcess(0);
    }

    /**
     * Fetch a tile number from the tile map.
     *
     * @param x Tile X coordinate (in tiles, not pixels)
     * @param y Tile Y coordinate (in tiles, not pixels)
     * @return Tile number (8-bit index into tile data)
     */
    private int fetchTile(int x, int y) {
        // Compute address for tile VRAM:  tileNumber = x + y * mapSize
        int tileNumber = x + y * mapSize;
        return VRAM[(tileNumber + BGScreenBaseBlock) & 0xFFFF] & 0xFF;
    }

    /**
     * Compute the VRAM address of a pixel within the character data.
     *
     * @param x Pixel X coordinate
     * @param y Pixel Y coordinate
     * @return VRAM address of the pixel
     */
    private int computeScreenAddress(int x, int y) {
        // Get tile number from map
        int address = fetchTile(x >> 3, y >> 3) << 6;  // Each tile is 64 bytes (8x8 @ 8bpp)
        // Add character base
        address += BGCharacterBaseBlock;
        // Add offset within tile: (y % 8) * 8 + (x % 8)
        address += (y & 0x7) << 3;
        address += x & 0x7;
        return address;
    }

    /**
     * Fetch a pixel with overflow (wrapping).
     * Coordinates wrap around at the map boundaries.
     *
     * @param x Pixel X coordinate
     * @param y Pixel Y coordinate
     * @return Palette color
     */
    private int fetchPixelOverflow(int x, int y) {
        // Wrap coordinates using mapSizeComparer mask
        int address = computeScreenAddress(x & mapSizeComparer, y & mapSizeComparer);
        return palette[VRAM[address & 0xFFFF] & 0xFF];
    }

    /**
     * Fetch a pixel without overflow (clamping).
     * Out-of-bounds coordinates return transparency.
     *
     * @param x Pixel X coordinate
     * @param y Pixel Y coordinate
     * @return Palette color or transparency (0x3800000)
     */
    private int fetchPixelNoOverflow(int x, int y) {
        // Check if coordinates are in bounds
        if (x != (x & mapSizeComparer) || y != (y & mapSizeComparer)) {
            // Out of bounds with no overflow allowed
            return 0x3800000;
        }
        int address = computeScreenAddress(x, y);
        return palette[VRAM[address & 0xFFFF] & 0xFF];
    }

    /**
     * Get a pixel using the current overflow mode.
     *
     * @param x Pixel X coordinate
     * @param y Pixel Y coordinate
     * @return Pixel value
     */
    public int getPixel(int x, int y) {
        return fetchPixel.fetch(x, y);
    }

    // Configuration methods called from renderer when registers are written

    /**
     * Set the screen base block (tile map location in VRAM).
     *
     * @param BGScreenBaseBlock Screen base block (0-31)
     */
    public void screenBaseBlockPreprocess(int BGScreenBaseBlock) {
        this.BGScreenBaseBlock = BGScreenBaseBlock << 11;  // * 2KB
    }

    /**
     * Set the character base block (tile data location in VRAM).
     *
     * @param BGCharacterBaseBlock Character base block (0-3)
     */
    public void characterBaseBlockPreprocess(int BGCharacterBaseBlock) {
        this.BGCharacterBaseBlock = BGCharacterBaseBlock << 14;  // * 16KB
    }

    /**
     * Set the screen size.
     *
     * @param BGScreenSize Screen size (0=128x128, 1=256x256, 2=512x512, 3=1024x1024)
     */
    public void screenSizePreprocess(int BGScreenSize) {
        // Map size in tiles: 16, 32, 64, or 128
        mapSize = 0x10 << BGScreenSize;
        // Map size in pixels - 1 (for masking)
        mapSizeComparer = (mapSize << 3) - 1;
    }

    /**
     * Set display overflow mode and update fetch function.
     *
     * @param doOverflow Overflow mode (0=clamp, non-zero=wrap)
     */
    public void displayOverflowPreprocess(int doOverflow) {
        if (doOverflow != BGDisplayOverflow) {
            displayOverflowProcess(doOverflow);
        }
    }

    /**
     * Update the overflow mode and select the appropriate fetch function.
     *
     * @param doOverflow Overflow mode (0=clamp, non-zero=wrap)
     */
    private void displayOverflowProcess(int doOverflow) {
        BGDisplayOverflow = doOverflow;
        if (doOverflow != 0) {
            fetchPixel = this::fetchPixelOverflow;
        } else {
            fetchPixel = this::fetchPixelNoOverflow;
        }
    }
}
