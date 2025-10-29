package com.iodinegba.core;

public class DMA {
    private IOCore IOCore;
    private DMAChannel dmaChannel0;
    private DMAChannel dmaChannel1;
    private DMAChannel dmaChannel2;
    private DMAChannel dmaChannel3;
    private int currentMatch = -1;
    private int fetch = 0;

    public DMA(IOCore IOCore) {
        this.IOCore = IOCore;
    }

    public void initialize() {
        this.dmaChannel0 = IOCore.dmaChannel0;
        this.dmaChannel1 = IOCore.dmaChannel1;
        this.dmaChannel2 = IOCore.dmaChannel2;
        this.dmaChannel3 = IOCore.dmaChannel3;
        this.currentMatch = -1;
        this.fetch = 0;
    }

    public int getCurrentFetchValue() {
        return this.fetch;
    }

    public void gfxHBlankRequest() {
        requestDMA(0x4);
    }

    public void gfxVBlankRequest() {
        requestDMA(0x2);
    }

    public void requestDMA(int DMAType) {
        dmaChannel0.requestDMA(DMAType);
        dmaChannel1.requestDMA(DMAType);
        dmaChannel2.requestDMA(DMAType);
        dmaChannel3.requestDMA(DMAType);
    }

    public int findLowestDMA() {
        if (dmaChannel0.getMatchStatus() != 0) {
            return 0;
        }
        if (dmaChannel1.getMatchStatus() != 0) {
            return 1;
        }
        if (dmaChannel2.getMatchStatus() != 0) {
            return 2;
        }
        if (dmaChannel3.getMatchStatus() != 0) {
            return 3;
        }
        return 4;
    }

    public void update() {
        int lowestDMAFound = findLowestDMA();
        if (lowestDMAFound < 4) {
            if (currentMatch == -1) {
                IOCore.flagDMA();
            }
            if (currentMatch != lowestDMAFound) {
                IOCore.wait.NonSequentialBroadcast();
                currentMatch = lowestDMAFound;
            }
        } else if (currentMatch != -1) {
            currentMatch = -1;
            IOCore.deflagDMA();
            IOCore.updateCoreSpill();
        }
    }

    public void perform() {
        switch (currentMatch) {
            case 0:
                dmaChannel0.handleDMACopy();
                break;
            case 1:
                dmaChannel1.handleDMACopy();
                break;
            case 2:
                dmaChannel2.handleDMACopy();
                break;
            default:
                dmaChannel3.handleDMACopy();
        }
    }

    public void updateFetch(int data) {
        this.fetch = data;
    }

    public int nextEventTime() {
        int clocks = Math.min(dmaChannel0.nextEventTime(), dmaChannel1.nextEventTime());
        clocks = Math.min(clocks, dmaChannel2.nextEventTime());
        clocks = Math.min(clocks, dmaChannel3.nextEventTime());
        return clocks;
    }
}
