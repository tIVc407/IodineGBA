package com.iodinegba.core;

public class DMAChannel {
    private IOCore IOCore;
    private int channelNum;
    private int[] DMA_ENABLE_TYPE;
    private int enabled = 0;
    private int pending = 0;
    private int source = 0;
    private int sourceShadow = 0;
    private int destination = 0;
    private int destinationShadow = 0;
    private int wordCount = 0;
    private int wordCountShadow = 0;
    private int irqFlagging = 0;
    private int dmaType = 0;
    private int is32Bit = 0;
    private int repeat = 0;
    private int sourceControl = 0;
    private int destinationControl = 0;
    private DMA DMACore;
    private Memory memory;
    private GfxState gfxState;
    private IRQ irq;

    public DMAChannel(IOCore IOCore, int channelNum) {
        this.IOCore = IOCore;
        this.channelNum = channelNum;
        switch (channelNum) {
            case 0:
                this.DMA_ENABLE_TYPE = new int[]{0x1, 0x2, 0x4, 0x40};
                break;
            case 1:
                this.DMA_ENABLE_TYPE = new int[]{0x1, 0x2, 0x4, 0x8};
                break;
            case 2:
                this.DMA_ENABLE_TYPE = new int[]{0x1, 0x2, 0x4, 0x10};
                break;
            case 3:
                this.DMA_ENABLE_TYPE = new int[]{0x1, 0x2, 0x4, 0x20};
                break;
        }
        this.DMACore = this.IOCore.dma;
        this.memory = this.IOCore.memory;
        this.gfxState = this.IOCore.gfxState;
        this.irq = this.IOCore.irq;
    }

    public int getMatchStatus() {
        return enabled & pending;
    }

    public void requestDMA(int DMAType) {
        if ((enabled & DMAType) != 0) {
            pending = DMAType;
            DMACore.update();
        }
    }

    public void handleDMACopy() {
        int source = this.sourceShadow;
        int destination = this.destinationShadow;
        if (is32Bit == 4) {
            copy32(source, destination);
        } else {
            copy16(source, destination);
        }
    }

    public int nextEventTime() {
        int clocks = 0x7FFFFFFF;
        switch (enabled) {
            case 0x2:
                clocks = gfxState.nextVBlankEventTime();
                break;
            case 0x4:
                clocks = gfxState.nextHBlankDMAEventTime();
                break;
        }
        return clocks;
    }

    public void enableDMAChannel(int enabled) {
        if (enabled != 0) {
            if (this.enabled == 0) {
                pending = 0x1;
                wordCountShadow = wordCount;
                sourceShadow = source;
                destinationShadow = destination;
            }
            this.enabled = DMA_ENABLE_TYPE[dmaType];
            pending &= this.enabled;
        } else {
            this.enabled = 0;
        }
        DMACore.update();
    }

    private void copy16(int source, int destination) {
        int data = memory.memoryReadDMA16(source);
        memory.memoryWriteDMA16(destination, data);
        decrementWordCount(source, destination, 2);
        DMACore.updateFetch(data | (data << 16));
    }

    private void copy32(int source, int destination) {
        int data = memory.memoryReadDMA32(source);
        memory.memoryWriteDMA32(destination, data);
        decrementWordCount(source, destination, 4);
        DMACore.updateFetch(data);
    }

    private void decrementWordCount(int source, int destination, int transferred) {
        wordCountShadow = (wordCountShadow - 1) & 0x3FFF;
        if (wordCountShadow == 0) {
            wordCountShadow = finalizeDMA(source, destination, transferred);
        } else {
            incrementDMAAddresses(source, destination, transferred);
        }
    }

    private int finalizeDMA(int source, int destination, int transferred) {
        pending = 0;
        int wordCountShadow = 0;
        if (repeat == 0 || enabled == 0x1) {
            enabled = 0;
        } else {
            wordCountShadow = wordCount;
        }
        DMACore.update();
        checkIRQTrigger();
        finalDMAAddresses(source, destination, transferred);
        return wordCountShadow;
    }

    private void checkIRQTrigger() {
        if (irqFlagging != 0) {
            irq.requestIRQ(0x100 << channelNum);
        }
    }

    private void finalDMAAddresses(int source, int destination, int transferred) {
        switch (sourceControl) {
            case 0:
            case 3:
                sourceShadow = source + transferred;
                break;
            case 1:
                sourceShadow = source - transferred;
                break;
        }
        switch (destinationControl) {
            case 0:
                destinationShadow = destination + transferred;
                break;
            case 1:
                destinationShadow = destination - transferred;
                break;
            case 3:
                destinationShadow = this.destination;
                break;
        }
    }

    private void incrementDMAAddresses(int source, int destination, int transferred) {
        switch (sourceControl) {
            case 0:
            case 3:
                sourceShadow = source + transferred;
                break;
            case 1:
                sourceShadow = source - transferred;
                break;
        }
        switch (destinationControl) {
            case 0:
            case 3:
                destinationShadow = destination + transferred;
                break;
            case 1:
                destinationShadow = destination - transferred;
                break;
        }
    }
}
