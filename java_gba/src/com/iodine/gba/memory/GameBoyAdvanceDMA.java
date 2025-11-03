package com.iodine.gba.memory;

import com.iodine.gba.core.GameBoyAdvanceIO;

public class GameBoyAdvanceDMA {
    public GameBoyAdvanceIO IOCore;
    private GameBoyAdvanceDMA0 dmaChannel0;
    private GameBoyAdvanceDMA1 dmaChannel1;
    private GameBoyAdvanceDMA2 dmaChannel2;
    private GameBoyAdvanceDMA3 dmaChannel3;
    private int currentMatch;
    private int fetch;

    public GameBoyAdvanceDMA(GameBoyAdvanceIO ioCore) {
        this.IOCore = ioCore;
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
        this.requestDMA(0x4);
    }

    public void gfxVBlankRequest() {
        this.requestDMA(0x2);
    }

    public void requestDMA(int DMAType) {
        this.dmaChannel0.requestDMA(DMAType);
        this.dmaChannel1.requestDMA(DMAType);
        this.dmaChannel2.requestDMA(DMAType);
        this.dmaChannel3.requestDMA(DMAType);
    }

    private int findLowestDMA() {
        if (this.dmaChannel0.getMatchStatus() != 0) {
            return 0;
        }
        if (this.dmaChannel1.getMatchStatus() != 0) {
            return 1;
        }
        if (this.dmaChannel2.getMatchStatus() != 0) {
            return 2;
        }
        if (this.dmaChannel3.getMatchStatus() != 0) {
            return 3;
        }
        return 4;
    }

    public void update() {
        int lowestDMAFound = this.findLowestDMA();
        if (lowestDMAFound < 4) {
            if (this.currentMatch == -1) {
                IOCore.flagDMA();
            }
            if (this.currentMatch != lowestDMAFound) {
                IOCore.wait.NonSequentialBroadcast();
                this.currentMatch = lowestDMAFound;
            }
        } else if (this.currentMatch != -1) {
            this.currentMatch = -1;
            IOCore.deflagDMA();
            IOCore.updateCoreSpill();
        }
    }

    public void perform() {
        switch (this.currentMatch) {
            case 0:
                this.dmaChannel0.handleDMACopy();
                break;
            case 1:
                this.dmaChannel1.handleDMACopy();
                break;
            case 2:
                this.dmaChannel2.handleDMACopy();
                break;
            default:
                this.dmaChannel3.handleDMACopy();
                break;
        }
    }

    public void updateFetch(int data) {
        this.fetch = data;
    }

    public int nextEventTime() {
        return Math.min(
                Math.min(this.dmaChannel0.nextEventTime(), this.dmaChannel1.nextEventTime()),
                Math.min(this.dmaChannel2.nextEventTime(), this.dmaChannel3.nextEventTime())
        );
    }
}
