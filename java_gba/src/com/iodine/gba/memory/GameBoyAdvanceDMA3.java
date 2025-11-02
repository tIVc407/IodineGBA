package com.iodine.gba.memory;

import com.iodine.gba.core.GameBoyAdvanceIO;
import com.iodine.gba.core.GameBoyAdvanceIRQ;
import com.iodine.gba.graphics.GameBoyAdvanceGraphics;

public class GameBoyAdvanceDMA3 {
    public GameBoyAdvanceIO IOCore;
    private GameBoyAdvanceDMA DMACore;
    private GameBoyAdvanceMemory memory;
    private GameBoyAdvanceIRQ irq;
    private GameBoyAdvanceGraphics gfxState;

    private final int[] DMA_ENABLE_TYPE = {
            0x1,
            0x2,
            0x4,
            0x20
    };

    private int enabled;
    private int pending;
    private int source;
    private int sourceShadow;
    private int destination;
    private int destinationShadow;
    private int wordCount;
    private int wordCountShadow;
    private int irqFlagging;
    private int dmaType;
    private boolean is32Bit;
    private boolean repeat;
    private int sourceControl;
    private int destinationControl;
    private int displaySyncEnableDelay;

    public GameBoyAdvanceDMA3(GameBoyAdvanceIO ioCore) {
        this.IOCore = ioCore;
    }

    public void initialize() {
        this.DMACore = IOCore.dma;
        this.memory = IOCore.memory;
        this.irq = IOCore.irq;
        this.gfxState = IOCore.gfxState;
        this.enabled = 0;
        this.pending = 0;
        this.source = 0;
        this.sourceShadow = 0;
        this.destination = 0;
        this.destinationShadow = 0;
        this.wordCount = 0;
        this.wordCountShadow = 0;
        this.irqFlagging = 0;
        this.dmaType = 0;
        this.is32Bit = false;
        this.repeat = false;
        this.sourceControl = 0;
        this.destinationControl = 0;
        this.displaySyncEnableDelay = 0;
    }

    private void validateDMASource(int address) {
        if (address >= 0x2000000) {
            this.source = address;
        }
    }

    public void writeDMASource8_0(int data) {
        int source = this.source & 0xFFFFF00;
        data &= 0xFF;
        source |= data;
        this.validateDMASource(source);
    }

    public void writeDMASource8_1(int data) {
        int source = this.source & 0xFFF00FF;
        data &= 0xFF;
        source |= (data << 8);
        this.validateDMASource(source);
    }

    public void writeDMASource8_2(int data) {
        int source = this.source & 0xF00FFFF;
        data &= 0xFF;
        source |= (data << 16);
        this.validateDMASource(source);
    }

    public void writeDMASource8_3(int data) {
        int source = this.source & 0xFFFFFF;
        data &= 0xF;
        source |= (data << 24);
        this.validateDMASource(source);
    }

    public void writeDMASource16_0(int data) {
        int source = this.source & 0xFFF0000;
        data &= 0xFFFF;
        source |= data;
        this.validateDMASource(source);
    }

    public void writeDMASource16_1(int data) {
        int source = this.source & 0xFFFF;
        data &= 0xFFF;
        source |= (data << 16);
        this.validateDMASource(source);
    }

    public void writeDMASource32(int data) {
        int source = data & 0xFFFFFFF;
        this.validateDMASource(source);
    }

    public void writeDMADestination8_0(int data) {
        this.destination &= 0xFFFFF00;
        data &= 0xFF;
        this.destination |= data;
    }

    public void writeDMADestination8_1(int data) {
        this.destination &= 0xFFF00FF;
        data &= 0xFF;
        this.destination |= (data << 8);
    }

    public void writeDMADestination8_2(int data) {
        this.destination &= 0xF00FFFF;
        data &= 0xFF;
        this.destination |= (data << 16);
    }

    public void writeDMADestination8_3(int data) {
        this.destination &= 0xFFFFFF;
        data &= 0xF;
        this.destination |= (data << 24);
    }

    public void writeDMADestination16_0(int data) {
        this.destination &= 0xFFF0000;
        data &= 0xFFFF;
        this.destination |= data;
    }

    public void writeDMADestination16_1(int data) {
        this.destination &= 0xFFFF;
        data &= 0xFFF;
        this.destination |= (data << 16);
    }

    public void writeDMADestination32(int data) {
        this.destination = data & 0xFFFFFFF;
    }

    public void writeDMAWordCount8_0(int data) {
        this.wordCount &= 0xFF00;
        data &= 0xFF;
        this.wordCount |= data;
    }

    public void writeDMAWordCount8_1(int data) {
        this.wordCount &= 0xFF;
        data &= 0xFF;
        this.wordCount |= (data << 8);
    }

    public void writeDMAWordCount16(int data) {
        this.wordCount = data & 0xFFFF;
    }

    public void writeDMAControl8_0(int data) {
        this.destinationControl = (data >> 5) & 0x3;
        this.sourceControl &= 0x2;
        this.sourceControl |= ((data >> 7) & 0x1);
    }

    public void writeDMAControl8_1(int data) {
        IOCore.updateCoreClocking();
        this.sourceControl = (this.sourceControl & 0x1) | ((data & 0x1) << 1);
        this.repeat = (data & 0x2) != 0;
        this.is32Bit = (data & 0x4) != 0;
        this.dmaType = (data >> 4) & 0x3;
        this.irqFlagging = data & 0x40;
        this.enableDMAChannel((data & 0x80) != 0);
        IOCore.updateCoreEventTime();
    }

    public void writeDMAControl16(int data) {
        IOCore.updateCoreClocking();
        this.destinationControl = (data >> 5) & 0x3;
        this.sourceControl = (data >> 7) & 0x3;
        this.repeat = ((data >> 9) & 0x1) != 0;
        this.is32Bit = ((data >> 10) & 0x1) != 0;
        this.dmaType = (data >> 12) & 0x3;
        this.irqFlagging = (data >> 14) & 0x1;
        this.enableDMAChannel((data & 0x8000) != 0);
        IOCore.updateCoreEventTime();
    }

    public void writeDMAControl32(int data) {
        this.writeDMAWordCount16(data);
        this.writeDMAControl16(data >> 16);
    }

    public int readDMAControl8_0() {
        int data = this.destinationControl << 5;
        data |= ((this.sourceControl & 0x1) << 7);
        return data;
    }

    public int readDMAControl8_1() {
        int data = this.sourceControl >> 1;
        if (this.repeat) data |= 0x2;
        if (this.is32Bit) data |= 0x4;
        data |= (this.dmaType << 4);
        data |= this.irqFlagging;
        if (this.enabled != 0) {
            data |= 0x80;
        }
        return data;
    }

    public int readDMAControl16() {
        int data = this.destinationControl << 5;
        data |= (this.sourceControl << 7);
        if (this.repeat) data |= (0x1 << 9);
        if (this.is32Bit) data |= (0x1 << 10);
        data |= (this.dmaType << 12);
        if (this.irqFlagging != 0) data |= (0x1 << 14);
        if (this.enabled != 0) {
            data |= 0x8000;
        }
        return data;
    }

    public int getMatchStatus() {
        return this.enabled & this.pending;
    }

    public void gfxDisplaySyncRequest() {
        this.requestDMA(0x20 ^ this.displaySyncEnableDelay);
    }

    public void gfxDisplaySyncEnableCheck() {
        if (this.enabled == 0x20) {
            if (this.displaySyncEnableDelay == 0x20) {
                this.displaySyncEnableDelay = 0;
            } else {
                this.enabled = 0;
                this.DMACore.update();
            }
        }
    }

    public void requestDMA(int DMAType) {
        if ((this.enabled & DMAType) != 0) {
            this.pending = DMAType;
            this.DMACore.update();
        }
    }

    public void enableDMAChannel(boolean enabled) {
        if (enabled) {
            if (this.enabled == 0) {
                switch (this.dmaType) {
                    case 0:
                        this.pending = 0x1;
                        break;
                    case 0x3:
                        this.displaySyncEnableDelay = 0x20;
                        break;
                }
                this.wordCountShadow = this.wordCount;
                this.sourceShadow = this.source;
                this.destinationShadow = this.destination;
            }
            this.enabled = this.DMA_ENABLE_TYPE[this.dmaType];
            this.pending &= this.enabled;
        } else {
            this.enabled = 0;
        }
        this.DMACore.update();
    }

    public void handleDMACopy() {
        int source = this.sourceShadow;
        int destination = this.destinationShadow;
        if (this.is32Bit) {
            this.copy32(source, destination);
        } else {
            this.copy16(source, destination);
        }
    }

    private void copy16(int source, int destination) {
        int data = this.memory.memoryReadDMAFull16(source);
        this.memory.memoryWriteDMAFull16(destination, data);
        this.decrementWordCount(source, destination, 2);
        this.DMACore.updateFetch(data | (data << 16));
    }

    private void copy32(int source, int destination) {
        int data = this.memory.memoryReadDMAFull32(source);
        this.memory.memoryWriteDMAFull32(destination, data);
        this.decrementWordCount(source, destination, 4);
        this.DMACore.updateFetch(data);
    }

    private void decrementWordCount(int source, int destination, int transferred) {
        int wordCountShadow = (this.wordCountShadow - 1) & 0xFFFF;
        if (wordCountShadow == 0) {
            wordCountShadow = this.finalizeDMA(source, destination, transferred);
        } else {
            this.incrementDMAAddresses(source, destination, transferred);
        }
        this.wordCountShadow = wordCountShadow;
    }

    private int finalizeDMA(int source, int destination, int transferred) {
        int wordCountShadow = 0;
        this.pending = 0;
        if (!this.repeat || this.enabled == 0x1) {
            this.enabled = 0;
        } else {
            wordCountShadow = this.wordCount;
        }
        this.DMACore.update();
        this.checkIRQTrigger();
        this.finalDMAAddresses(source, destination, transferred);
        return wordCountShadow;
    }

    private void checkIRQTrigger() {
        if (this.irqFlagging != 0) {
            this.irq.requestIRQ(0x800);
        }
    }

    private void finalDMAAddresses(int source, int destination, int transferred) {
        switch (this.sourceControl) {
            case 0:
            case 3:
                this.sourceShadow = source + transferred;
                break;
            case 1:
                this.sourceShadow = source - transferred;
                break;
        }
        switch (this.destinationControl) {
            case 0:
                this.destinationShadow = destination + transferred;
                break;
            case 1:
                this.destinationShadow = destination - transferred;
                break;
            case 3:
                this.destinationShadow = this.destination;
                break;
        }
    }

    private void incrementDMAAddresses(int source, int destination, int transferred) {
        switch (this.sourceControl) {
            case 0:
            case 3:
                this.sourceShadow = source + transferred;
                break;
            case 1:
                this.sourceShadow = source - transferred;
                break;
        }
        switch (this.destinationControl) {
            case 0:
            case 3:
                this.destinationShadow = destination + transferred;
                break;
            case 1:
                this.destinationShadow = destination - transferred;
                break;
        }
    }

    public int nextEventTime() {
        int clocks = 0x7FFFFFFF;
        switch (this.enabled) {
            case 0x2:
                clocks = gfxState.nextVBlankEventTime();
                break;
            case 0x4:
                clocks = gfxState.nextHBlankDMAEventTime();
                break;
            case 0x20:
                clocks = gfxState.nextDisplaySyncEventTime(this.displaySyncEnableDelay);
                break;
        }
        return clocks;
    }
}
