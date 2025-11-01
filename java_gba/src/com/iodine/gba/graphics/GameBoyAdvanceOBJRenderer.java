package com.iodine.gba.graphics;

/**
 * GameBoyAdvanceOBJRenderer - Sprite/object renderer (stub, needs conversion from OBJ.js)
 */
public class GameBoyAdvanceOBJRenderer {
    public GameBoyAdvanceRenderer gfx;
    public byte[] OAM;

    public GameBoyAdvanceOBJRenderer(GameBoyAdvanceRenderer gfx) {
        this.gfx = gfx;
        this.OAM = new byte[0x400];  // 1KB OAM
    }

    public void initialize() {
        // TODO: Convert from OBJ.js
    }

    public void renderScanLine(int line) {
        // TODO: Convert from OBJ.js
    }

    public void setHBlankIntervalFreeStatus(int value) {
        // TODO: Convert from OBJ.js
    }

    public void writeOAM16(int address, int data) {
        // TODO: Convert from OBJ.js
        OAM[address] = (byte)(data & 0xFF);
        OAM[address + 1] = (byte)((data >> 8) & 0xFF);
    }

    public void writeOAM32(int address, int data) {
        // TODO: Convert from OBJ.js
        address = address << 1;
        OAM[address] = (byte)(data & 0xFF);
        OAM[address + 1] = (byte)((data >> 8) & 0xFF);
        OAM[address + 2] = (byte)((data >> 16) & 0xFF);
        OAM[address + 3] = (byte)((data >> 24) & 0xFF);
    }

    public int readOAM(int address) {
        return OAM[address] & 0xFF;
    }

    public int readOAM16(int address) {
        return (OAM[address] & 0xFF) | ((OAM[address + 1] & 0xFF) << 8);
    }

    public int readOAM32(int address) {
        address = address << 1;
        return (OAM[address] & 0xFF) |
               ((OAM[address + 1] & 0xFF) << 8) |
               ((OAM[address + 2] & 0xFF) << 16) |
               ((OAM[address + 3] & 0xFF) << 24);
    }
}
