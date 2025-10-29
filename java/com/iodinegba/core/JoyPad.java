package com.iodinegba.core;

public class JoyPad {
    private IOCore IOCore;
    public int keyInput = 0x3FF;
    public int keyInterrupt = 0;

    public JoyPad(IOCore IOCore) {
        this.IOCore = IOCore;
    }

    public void initialize() {
        this.keyInput = 0x3FF;
        this.keyInterrupt = 0;
    }

    public void keyPress(int keyPressed) {
        keyPressed = 1 << keyPressed;
        this.keyInput &= ~keyPressed;
        this.checkForMatch();
    }

    public void keyRelease(int keyReleased) {
        keyReleased = 1 << keyReleased;
        this.keyInput |= keyReleased;
        this.checkForMatch();
    }

    public void checkForMatch() {
        if ((this.keyInterrupt & 0x8000) != 0) {
            if (((~this.keyInput) & this.keyInterrupt & 0x3FF) == (this.keyInterrupt & 0x3FF)) {
                this.IOCore.deflagStop();
                this.checkForIRQ();
            }
        } else if (((~this.keyInput) & this.keyInterrupt & 0x3FF) != 0) {
            this.IOCore.deflagStop();
            this.checkForIRQ();
        }
    }

    public void checkForIRQ() {
        if ((this.keyInterrupt & 0x4000) != 0) {
            this.IOCore.irq.requestIRQ(0x1000);
        }
    }

    public int readKeyStatus8_0() {
        return this.keyInput & 0xFF;
    }

    public int readKeyStatus8_1() {
        return this.keyInput >> 8;
    }

    public int readKeyStatus16() {
        return this.keyInput;
    }

    public void writeKeyControl8_0(int data) {
        this.keyInterrupt &= 0xC300;
        data &= 0xFF;
        this.keyInterrupt |= data;
    }

    public void writeKeyControl8_1(int data) {
        this.keyInterrupt &= 0xFF;
        data &= 0xC3;
        this.keyInterrupt |= (data << 8);
    }

    public void writeKeyControl16(int data) {
        this.keyInterrupt = data & 0xC3FF;
    }

    public int readKeyControl8_0() {
        return this.keyInterrupt & 0xFF;
    }

    public int readKeyControl8_1() {
        return this.keyInterrupt >> 8;
    }

    public int readKeyControl16() {
        return this.keyInterrupt;
    }

    public int readKeyStatusControl32() {
        return this.keyInput | (this.keyInterrupt << 16);
    }
}
