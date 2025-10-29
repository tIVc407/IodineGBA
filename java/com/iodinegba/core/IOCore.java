package com.iodinegba.core;

public class IOCore {
    public IRQ irq;
    public Sound sound;
    public DMA dma;
    public DMAChannel dmaChannel0;
    public DMAChannel dmaChannel1;
    public DMAChannel dmaChannel2;
    public DMAChannel dmaChannel3;
    public Wait wait;
    public CoreExposed coreExposed;
    public Timer timer;
    public Memory memory;
    public GfxState gfxState;
    public CPU cpu;
    public boolean SKIPBoot = true;

    public IOCore(CoreExposed coreExposed) {
        this.irq = new IRQ(this);
        this.sound = new Sound(this);
        this.dma = new DMA(this);
        this.dmaChannel0 = new DMAChannel(this, 0);
        this.dmaChannel1 = new DMAChannel(this, 1);
        this.dmaChannel2 = new DMAChannel(this, 2);
        this.dmaChannel3 = new DMAChannel(this, 3);
        this.wait = new Wait();
        this.coreExposed = coreExposed;
        this.timer = new Timer(this);
        this.memory = new Memory();
        this.gfxState = new GfxState();
        this.cpu = new CPU();
    }

    public void deflagStop() {
        // Placeholder
    }

    public void updateTimerClocking() {
        // Placeholder
    }

    public void updateCoreEventTime() {
        // Placeholder
    }



    public void flagDMA() {
        // Placeholder
    }

    public void deflagDMA() {
        // Placeholder
    }

    public void updateCoreSpill() {
        // Placeholder
    }

    public void updateCoreClocking() {
        // Placeholder
    }

    public boolean isStopped() {
        return false;
    }

    public void updateCoreSpillRetain() {
        // Placeholder
    }
}
