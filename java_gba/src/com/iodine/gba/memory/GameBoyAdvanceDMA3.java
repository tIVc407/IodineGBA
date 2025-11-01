package com.iodine.gba.memory;

import com.iodine.gba.core.GameBoyAdvanceIO;

public class GameBoyAdvanceDMA3 {
    public GameBoyAdvanceIO IOCore;

    public GameBoyAdvanceDMA3(GameBoyAdvanceIO ioCore) {
        this.IOCore = ioCore;
    }

    public void initialize() {
    }

    public void gfxDisplaySyncRequest() {
        // TODO: Convert from DMA3.js - Display Sync DMA trigger
    }

    public void gfxDisplaySyncEnableCheck() {
        // TODO: Convert from DMA3.js - Display Sync DMA reset
    }
}
