package com.iodine.gba.cartridge;

import com.iodine.gba.core.GameBoyAdvanceIO;

public class GameBoyAdvanceCartridge {
    public GameBoyAdvanceIO IOCore;
    public byte[] ROM;
    public String name = "Unknown";

    public GameBoyAdvanceCartridge(GameBoyAdvanceIO ioCore) {
        this.IOCore = ioCore;
    }

    public void initialize() {
        // Load ROM from IOCore
        if (IOCore.ROM != null) {
            ROM = IOCore.ROM;
            // Extract game name from header (offset 0xA0, 12 bytes)
            if (ROM.length >= 0xAC) {
                byte[] nameBytes = new byte[12];
                System.arraycopy(ROM, 0xA0, nameBytes, 0, 12);
                name = new String(nameBytes).trim();
            }
        } else {
            ROM = new byte[0];
        }
    }

    public int readROM8(int address) {
        int offset = (address & 0x01FFFFFF);
        if (offset < ROM.length) {
            return ROM[offset] & 0xFF;
        }
        return 0xFF;
    }

    public int readROM16(int address) {
        int offset = (address & 0x01FFFFFE);
        if (offset + 1 < ROM.length) {
            return ((ROM[offset] & 0xFF) | ((ROM[offset + 1] & 0xFF) << 8));
        }
        return 0xFFFF;
    }

    public int readROM32(int address) {
        int offset = (address & 0x01FFFFFC);
        if (offset + 3 < ROM.length) {
            return ((ROM[offset] & 0xFF) |
                    ((ROM[offset + 1] & 0xFF) << 8) |
                    ((ROM[offset + 2] & 0xFF) << 16) |
                    ((ROM[offset + 3] & 0xFF) << 24));
        }
        return 0xFFFFFFFF;
    }
}
