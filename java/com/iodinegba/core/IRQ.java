package com.iodinegba.core;

public class IRQ {
    private IOCore IOCore;
    private int interruptsEnabled = 0;
    private int interruptsRequested = 0;
    private int IME = 0;
    private GfxState gfxState;
    private Timer timer;

    public IRQ(IOCore IOCore) {
        this.IOCore = IOCore;
        this.gfxState = this.IOCore.gfxState;
        this.timer = this.IOCore.timer;
    }

    public int IRQMatch() {
        return interruptsEnabled & interruptsRequested;
    }

    public void checkForIRQFire() {
        IOCore.cpu.triggerIRQ(interruptsEnabled & interruptsRequested & IME);
    }

    public void requestIRQ(int irqLineToSet) {
        interruptsRequested |= irqLineToSet;
        checkForIRQFire();
    }

    public void writeIME(int data) {
        IOCore.updateCoreClocking();
        IME = (data << 31) >> 31;
        checkForIRQFire();
        IOCore.updateCoreEventTime();
    }

    public void writeIE16(int data) {
        IOCore.updateCoreClocking();
        interruptsEnabled = data & 0x3FFF;
        checkForIRQFire();
        IOCore.updateCoreEventTime();
    }

    public void writeIF16(int data) {
        IOCore.updateCoreClocking();
        interruptsRequested &= ~data;
        checkForIRQFire();
        IOCore.updateCoreEventTime();
    }

    public int readIME() {
        return IME & 0x1;
    }

    public int readIE16() {
        return interruptsEnabled;
    }

    public int readIF16() {
        IOCore.updateCoreSpillRetain();
        return interruptsRequested;
    }

    public int nextIRQEventTime() {
        int clocks = 0x7FFFFFFF;
        if (IME != 0) {
            clocks = nextEventTime();
        }
        return clocks;
    }

    private int nextEventTime() {
        int clocks = 0x7FFFFFFF;
        if ((interruptsEnabled & 0x1) != 0) {
            clocks = gfxState.nextVBlankEventTime();
        }
        if ((interruptsEnabled & 0x2) != 0) {
            clocks = Math.min(clocks, gfxState.nextHBlankDMAEventTime());
        }
        if ((interruptsEnabled & 0x4) != 0) {
            //clocks = Math.min(clocks, gfxState.nextVCounterIRQEventTime());
        }
        if ((interruptsEnabled & 0x8) != 0) {
            clocks = Math.min(clocks, timer.nextTimer0IRQEventTime());
        }
        if ((interruptsEnabled & 0x10) != 0) {
            clocks = Math.min(clocks, timer.nextTimer1IRQEventTime());
        }
        if ((interruptsEnabled & 0x20) != 0) {
            clocks = Math.min(clocks, timer.nextTimer2IRQEventTime());
        }
        if ((interruptsEnabled & 0x40) != 0) {
            clocks = Math.min(clocks, timer.nextTimer3IRQEventTime());
        }
        return clocks;
    }
}
