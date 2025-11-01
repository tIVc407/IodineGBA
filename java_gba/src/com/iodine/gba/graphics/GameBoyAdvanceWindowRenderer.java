package com.iodine.gba.graphics;

/**
 * GameBoyAdvanceWindowRenderer - Window renderer (converted from Window.js)
 * Copyright (C) 2012-2015 Grant Galitz
 *
 * The GBA supports two rectangular windows (WIN0 and WIN1) that can selectively
 * enable or disable layers within a defined screen region. Windows have configurable
 * X and Y coordinates and control which layers are visible inside the window.
 */
public class GameBoyAdvanceWindowRenderer {
    public GameBoyAdvanceWindowCompositor compositor;

    // Window coordinates
    public int WINXCoordRight;    // Right edge (0-240)
    public int WINXCoordLeft;     // Left edge (0-240)
    public int WINYCoordBottom;   // Bottom edge (0-160)
    public int WINYCoordTop;      // Top edge (0-160)

    // Layer masking and color effects control
    public int windowDisplayControl;  // Which layers are enabled in this window

    public GameBoyAdvanceWindowRenderer(GameBoyAdvanceWindowCompositor compositor) {
        this.compositor = compositor;
    }

    public void initialize() {
        // Initialize the compositor
        compositor.initialize();
        // Initialize window coordinates
        WINXCoordRight = 0;
        WINXCoordLeft = 0;
        WINYCoordBottom = 0;
        WINYCoordTop = 0;
        // Layer masking & color effects control
        windowDisplayControl = 0;
        // Update the color effects status in the compositor
        preprocess();
    }

    /**
     * Render the window for the current scanline.
     * Only renders within the window boundaries.
     *
     * @param line Current scanline (0-159)
     * @param toRender Layer mask indicating which layers to render
     */
    public void renderScanLine(int line, int toRender) {
        // Windowing can disable layers further
        toRender = toRender & windowDisplayControl;

        // Check if we're doing windowing for the current line
        if (checkYRange(line)) {
            // Windowing is active for the current line
            int right = WINXCoordRight;
            int left = WINXCoordLeft;

            if (left <= right) {
                // Windowing is left to right as expected
                left = Math.min(left, 240);
                right = Math.min(right, 240);
                // Render from left coordinate to right coordinate
                compositor.renderScanLine(left, right, toRender);
            } else {
                // Invalid horizontal windowing coordinates, so invert horizontal windowing range
                // This creates a "wrap-around" effect
                left = Math.min(left, 240);
                right = Math.min(right, 240);
                // Render pixel 0 to right coordinate
                compositor.renderScanLine(0, right, toRender);
                // Render left coordinate to last pixel
                compositor.renderScanLine(left, 240, toRender);
            }
        }
    }

    /**
     * Check if the current scanline is within the window's vertical range.
     *
     * @param line Current scanline
     * @return true if line is within the window's Y range
     */
    private boolean checkYRange(int line) {
        int bottom = WINYCoordBottom;
        int top = WINYCoordTop;

        if (top <= bottom) {
            // Windowing is top to bottom as expected
            return (line >= top && line < bottom);
        } else {
            // Invalid vertical windowing coordinates, so invert vertical windowing range
            return (line < top || line >= bottom);
        }
    }

    /**
     * Update the color effects status in the compositor.
     */
    private void preprocess() {
        // Bit 5 of windowDisplayControl controls color effects enable
        compositor.preprocess(windowDisplayControl & 0x20);
    }

    // Register write methods

    public void writeWINXCOORDRight8(int data) {
        WINXCoordRight = data;
    }

    public void writeWINXCOORDLeft8(int data) {
        WINXCoordLeft = data;
    }

    public void writeWINXCOORD16(int data) {
        WINXCoordRight = data & 0xFF;
        WINXCoordLeft = data >> 8;
    }

    public void writeWINYCOORDBottom8(int data) {
        WINYCoordBottom = data;
    }

    public void writeWINYCOORDTop8(int data) {
        WINYCoordTop = data;
    }

    public void writeWINYCOORD16(int data) {
        WINYCoordBottom = data & 0xFF;
        WINYCoordTop = data >> 8;
    }

    public void writeWININ8(int data) {
        // Layer masking & color effects control
        windowDisplayControl = data;
        // Update the color effects status in the compositor
        preprocess();
    }
}
