package com.iodine.gba.core;

public class GameBoyAdvanceSerial {
    public GameBoyAdvanceIO IOCore;

    public GameBoyAdvanceSerial(GameBoyAdvanceIO ioCore) {
        this.IOCore = ioCore;
    }

    public void initialize() {
    }

    public void addClocks(int clocks) {
        // Simplified serial timing
    }
}
