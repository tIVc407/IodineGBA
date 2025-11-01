package com.iodine.gba.core;

public class GameBoyAdvanceIRQ {
    public GameBoyAdvanceIO IOCore;
    public int interruptEnable = 0;
    public int interruptFlags = 0;
    public int masterEnable = 0;

    public GameBoyAdvanceIRQ(GameBoyAdvanceIO ioCore) {
        this.IOCore = ioCore;
    }

    public void initialize() {
    }

    public int IRQMatch() {
        return (interruptEnable & interruptFlags & masterEnable) != 0 ? 1 : 0;
    }

    public int nextEventTime() {
        return Integer.MAX_VALUE;
    }

    public int nextIRQEventTime() {
        return Integer.MAX_VALUE;
    }

    public void requestIRQ(int irqType) {
        interruptFlags |= irqType;
        if (IRQMatch() != 0) {
            IOCore.cpu.triggerIRQ(1);
        }
    }
}
