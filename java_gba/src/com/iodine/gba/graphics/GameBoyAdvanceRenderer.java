package com.iodine.gba.graphics;

import com.iodine.gba.core.GameBoyAdvanceIO;

public class GameBoyAdvanceRenderer {
    public GameBoyAdvanceIO IOCore;
    public int[] frameBuffer = new int[240 * 160];  // 240x160 display

    public GameBoyAdvanceRenderer(GameBoyAdvanceIO ioCore) {
        this.IOCore = ioCore;
    }

    public void initialize() {
        // Initialize renderer
    }

    public int[] getFrameBuffer() {
        return frameBuffer;
    }
}
