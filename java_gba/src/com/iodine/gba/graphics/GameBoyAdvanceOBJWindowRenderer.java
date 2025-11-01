package com.iodine.gba.graphics;

/**
 * GameBoyAdvanceOBJWindowRenderer - Object window renderer (converted from OBJWindow.js)
 * Copyright (C) 2012-2015 Grant Galitz
 *
 * The GBA supports an "object window" where sprites marked as "obj-win" act as a mask
 * for which layers are visible. Any pixel where an obj-win sprite is rendered can have
 * different layer visibility settings than the rest of the screen.
 */
public class GameBoyAdvanceOBJWindowRenderer {
    public GameBoyAdvanceOBJWindowCompositor compositor;

    // Layer masking and color effects control for OBJ window
    public int WINOBJOutside;

    public GameBoyAdvanceOBJWindowRenderer(GameBoyAdvanceOBJWindowCompositor compositor) {
        this.compositor = compositor;
    }

    public void initialize() {
        // Initialize the compositor
        compositor.initialize();
        // Layer masking & color effects control
        WINOBJOutside = 0;
        // Update the color effects status in the compositor
        preprocess();
    }

    /**
     * Render the OBJ window for the current scanline.
     * Windowing occurs where there is a non-transparent "obj-win" sprite.
     *
     * @param line Current scanline (0-159) - not used in this implementation
     * @param toRender Layer mask indicating which layers to render
     */
    public void renderScanLine(int line, int toRender) {
        // Windowing can disable layers further
        toRender = toRender & WINOBJOutside;
        // Windowing occurs where there is a non-transparent "obj-win" sprite
        // The compositor checks the OBJ window buffer to determine which pixels to render
        compositor.renderScanLine(toRender);
    }

    /**
     * Write to WINOBJ register.
     * Controls which layers are enabled inside the OBJ window.
     */
    public void writeWINOBJIN8(int data) {
        // Layer masking & color effects control
        WINOBJOutside = data;
        // Update the color effects status in the compositor
        preprocess();
    }

    /**
     * Update the color effects status in the compositor.
     */
    private void preprocess() {
        // Bit 5 of WINOBJOutside controls color effects enable
        compositor.preprocess(WINOBJOutside & 0x20);
    }
}
