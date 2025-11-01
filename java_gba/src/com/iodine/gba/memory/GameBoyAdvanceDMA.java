package com.iodine.gba.memory;

import com.iodine.gba.core.GameBoyAdvanceIO;

/**
 * GameBoyAdvanceDMA - DMA controller manager
 */
public class GameBoyAdvanceDMA {
    public GameBoyAdvanceIO IOCore;

    public GameBoyAdvanceDMA(GameBoyAdvanceIO ioCore) {
        this.IOCore = ioCore;
    }

    public void initialize() {
    }

    public void perform() {
        // Simplified DMA - just deflag
        IOCore.deflagDMA();
    }

    public int nextEventTime() {
        return Integer.MAX_VALUE;
    }

    public int getCurrentFetchValue() {
        return 0;
    }

    public void gfxHBlankRequest() {
        // TODO: Convert from DMA.js - HBlank DMA trigger
    }

    public void gfxVBlankRequest() {
        // TODO: Convert from DMA.js - VBlank DMA trigger
    }
}
