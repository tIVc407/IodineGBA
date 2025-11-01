package com.iodine.gba.cartridge;

import com.iodine.gba.core.GameBoyAdvanceIO;

public class GameBoyAdvanceSaves {
    public GameBoyAdvanceIO IOCore;
    public byte[] SRAM;

    public GameBoyAdvanceSaves(GameBoyAdvanceIO ioCore) {
        this.IOCore = ioCore;
    }

    public void initialize() {
        // Initialize SRAM (64KB max)
        SRAM = new byte[0x10000];
    }

    public int readSRAM(int address) {
        int offset = address & 0xFFFF;
        return SRAM[offset] & 0xFF;
    }

    public void writeSRAM(int address, int data) {
        int offset = address & 0xFFFF;
        SRAM[offset] = (byte) data;
    }
}
