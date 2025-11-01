package com.iodine.gba.graphics;

import com.iodine.gba.core.GameBoyAdvanceIO;

public class GameBoyAdvanceGraphics {
    public GameBoyAdvanceIO IOCore;

    public GameBoyAdvanceGraphics(GameBoyAdvanceIO ioCore) {
        this.IOCore = ioCore;
    }

    public void initialize() {
    }

    public void addClocks(int clocks) {
        // Simplified graphics timing
    }
}
