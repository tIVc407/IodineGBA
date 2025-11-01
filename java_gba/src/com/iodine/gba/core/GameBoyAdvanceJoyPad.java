package com.iodine.gba.core;

public class GameBoyAdvanceJoyPad {
    public GameBoyAdvanceIO IOCore;
    public int keyStatus = 0x3FF;  // All keys released (bits set)

    /*
     * Button mapping:
     * 0: A
     * 1: B
     * 2: Select
     * 3: Start
     * 4: Right
     * 5: Left
     * 6: Up
     * 7: Down
     * 8: R
     * 9: L
     */

    public GameBoyAdvanceJoyPad(GameBoyAdvanceIO ioCore) {
        this.IOCore = ioCore;
    }

    public void initialize() {
    }

    public void keyPress(int key) {
        if (key >= 0 && key <= 9) {
            keyStatus &= ~(1 << key);  // Clear bit (pressed)
        }
    }

    public void keyRelease(int key) {
        if (key >= 0 && key <= 9) {
            keyStatus |= (1 << key);  // Set bit (released)
        }
    }

    public int readKeyStatus() {
        return keyStatus;
    }
}
