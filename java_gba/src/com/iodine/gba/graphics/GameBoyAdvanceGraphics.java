package com.iodine.gba.graphics;

import com.iodine.gba.core.GameBoyAdvanceIO;
import com.iodine.gba.memory.*;

/**
 * GameBoyAdvanceGraphics - LCD controller (converted from Graphics.js)
 * Copyright (C) 2012-2015 Grant Galitz
 */
public class GameBoyAdvanceGraphics {
    public GameBoyAdvanceIO IOCore;
    public GameBoyAdvanceRenderer gfxRenderer;
    public GameBoyAdvanceDMA dma;
    public GameBoyAdvanceDMA3 dmaChannel3;
    public com.iodine.gba.core.GameBoyAdvanceIRQ irq;
    public GameBoyAdvanceWait wait;

    // State
    public boolean renderedScanLine;
    public int statusFlags;
    public int IRQFlags;
    public int VCounter;
    public int currentScanLine;
    public int LCDTicks;

    public GameBoyAdvanceGraphics(GameBoyAdvanceIO ioCore) {
        this.IOCore = ioCore;
    }

    public void initialize() {
        gfxRenderer = IOCore.gfxRenderer;
        dma = IOCore.dma;
        dmaChannel3 = IOCore.dmaChannel3;
        irq = IOCore.irq;
        wait = IOCore.wait;
        initializeState();
    }

    public void initializeState() {
        // Initialize Pre-Boot
        renderedScanLine = false;
        statusFlags = 0;
        IRQFlags = 0;
        VCounter = 0;
        currentScanLine = 0;
        LCDTicks = 0;
        if (IOCore.SKIPBoot) {
            // BIOS entered the ROM at line 0x7C
            currentScanLine = 0x7C;
        }
    }

    public void addClocks(int clocks) {
        // Call this when clocking the state some more
        LCDTicks = LCDTicks + clocks;
        clockLCDState();
    }

    public void clockLCDState() {
        if (LCDTicks >= 960) {
            clockScanLine();              // Line finishes drawing at clock 960
            clockLCDStatePostRender();    // Check for hblank and clocking into next line
        }
    }

    public void clockScanLine() {
        if (!renderedScanLine) {          // If we rendered the scanline, don't run this again
            renderedScanLine = true;      // Mark rendering
            if (currentScanLine < 160) {
                gfxRenderer.incrementScanLineQueue();  // Tell the gfx JIT to queue another line to draw
            }
        }
    }

    public void clockLCDStatePostRender() {
        if (LCDTicks >= 1006) {
            // HBlank Event Occurred
            updateHBlank();
            if (LCDTicks >= 1232) {
                // Clocking to next line occurred
                clockLCDNextLine();
            }
        }
    }

    public void clockLCDNextLine() {
        // We've now overflowed the LCD scan line state machine counter
        renderedScanLine = false;                 // Unmark line render
        statusFlags = statusFlags & 0x5;          // Un-mark HBlank
        // De-clock for starting on new scan-line
        LCDTicks = LCDTicks - 1232;               // We start out at the beginning of the next line
        // Increment scanline counter
        currentScanLine = currentScanLine + 1;    // Increment to the next scan line
        // Handle switching in/out of vblank
        if (currentScanLine >= 160) {
            // Handle special case scan lines of vblank
            switch (currentScanLine) {
                case 160:
                    updateVBlankStart();          // Update state for start of vblank
                case 161:
                    dmaChannel3.gfxDisplaySyncRequest();  // Display Sync. DMA trigger
                    break;
                case 162:
                    dmaChannel3.gfxDisplaySyncEnableCheck();  // Display Sync. DMA reset on start of line 162
                    break;
                case 227:
                    statusFlags = statusFlags & 0x6;  // Un-mark VBlank on start of last vblank line
                    break;
                case 228:
                    currentScanLine = 0;          // Reset scan-line to zero (First line of draw)
            }
        } else if (currentScanLine > 1) {
            dmaChannel3.gfxDisplaySyncRequest();  // Display Sync. DMA trigger
        }
        checkVCounter();                          // We're on a new scan line, so check the VCounter for match
        isRenderingCheckPreprocess();             // Update a check value
        // Recursive clocking of the LCD state
        clockLCDState();
    }

    public void updateHBlank() {
        if ((statusFlags & 0x2) == 0) {           // If we were last in HBlank, don't run this again
            statusFlags = statusFlags | 0x2;      // Mark HBlank
            if ((IRQFlags & 0x10) != 0) {
                irq.requestIRQ(0x2);              // Check for IRQ
            }
            if (currentScanLine < 160) {
                dma.gfxHBlankRequest();           // Check for HDMA Trigger
            }
            isRenderingCheckPreprocess();         // Update a check value
        }
    }

    public void checkVCounter() {
        if (currentScanLine == VCounter) {        // Check for VCounter match
            statusFlags = statusFlags | 0x4;
            if ((IRQFlags & 0x20) != 0) {         // Check for VCounter IRQ
                irq.requestIRQ(0x4);
            }
        } else {
            statusFlags = statusFlags & 0x3;
        }
    }

    public int nextVBlankIRQEventTime() {
        int nextEventTime = 0x7FFFFFFF;
        if ((IRQFlags & 0x8) != 0) {
            // Only give a time if we're allowed to irq
            nextEventTime = nextVBlankEventTime();
        }
        return nextEventTime;
    }

    public int nextHBlankEventTime() {
        int time = LCDTicks;
        if (time < 1006) {
            // Haven't reached hblank yet, so hblank offset - current
            time = 1006 - time;
        } else {
            // We're in hblank, so it's end clock - current + next scanline hblank offset
            time = 2238 - time;
        }
        return time;
    }

    public int nextHBlankIRQEventTime() {
        int nextEventTime = 0x7FFFFFFF;
        if ((IRQFlags & 0x10) != 0) {
            // Only give a time if we're allowed to irq
            nextEventTime = nextHBlankEventTime();
        }
        return nextEventTime;
    }

    public int nextVCounterIRQEventTime() {
        int nextEventTime = 0x7FFFFFFF;
        if ((IRQFlags & 0x20) != 0) {
            // Only give a time if we're allowed to irq
            nextEventTime = nextVCounterEventTime();
        }
        return nextEventTime;
    }

    public int nextVBlankEventTime() {
        int nextEventTime = currentScanLine;
        if (nextEventTime < 160) {
            // Haven't reached vblank yet, so vblank offset - current
            nextEventTime = 160 - nextEventTime;
        } else {
            // We're in vblank, so it's end clock - current + next frame vblank offset
            nextEventTime = 388 - nextEventTime;
        }
        // Convert line count to clocks
        nextEventTime = convertScanlineToClocks(nextEventTime);
        // Subtract scanline offset from clocks
        nextEventTime = nextEventTime - LCDTicks;
        return nextEventTime;
    }

    public int nextHBlankDMAEventTime() {
        int nextEventTime = nextHBlankEventTime();
        if (currentScanLine > 159 || (currentScanLine == 159 && LCDTicks >= 1006)) {
            // No HBlank DMA in VBlank
            int linesToSkip = 227 - currentScanLine;
            linesToSkip = convertScanlineToClocks(linesToSkip);
            nextEventTime = nextEventTime + linesToSkip;
        }
        return nextEventTime;
    }

    public int nextVCounterEventTime() {
        int nextEventTime = 0x7FFFFFFF;
        if (VCounter <= 227) {
            // Only match lines within screen or vblank
            nextEventTime = VCounter - currentScanLine;
            if (nextEventTime <= 0) {
                nextEventTime = nextEventTime + 228;
            }
            nextEventTime = convertScanlineToClocks(nextEventTime);
            nextEventTime = nextEventTime - LCDTicks;
        }
        return nextEventTime;
    }

    public int nextDisplaySyncEventTime(int delay) {
        int nextEventTime = 0x7FFFFFFF;
        if (currentScanLine >= 161 || delay != 0) {
            // Skip to line 2 metrics
            nextEventTime = 230 - currentScanLine;
            nextEventTime = convertScanlineToClocks(nextEventTime);
            nextEventTime = nextEventTime - LCDTicks;
        } else if (currentScanLine == 0) {
            // Doesn't start until line 2
            nextEventTime = 2464 - LCDTicks;
        } else {
            // Line 2 through line 161
            nextEventTime = 1232 - LCDTicks;
        }
        return nextEventTime;
    }

    public int convertScanlineToClocks(int lines) {
        return lines * 1232;
    }

    public void updateVBlankStart() {
        statusFlags = statusFlags | 0x1;           // Mark VBlank
        if ((IRQFlags & 0x8) != 0) {               // Check for VBlank IRQ
            irq.requestIRQ(0x1);
        }
        gfxRenderer.ensureFraming();
        dma.gfxVBlankRequest();
    }

    public void isRenderingCheckPreprocess() {
        boolean isInVisibleLines = ((gfxRenderer.displayControl & 0x80) == 0 && (statusFlags & 0x1) == 0);
        int isRendering = (isInVisibleLines && (statusFlags & 0x2) == 0) ? 2 : 1;
        int isOAMRendering = (isInVisibleLines && ((statusFlags & 0x2) == 0 || (gfxRenderer.displayControl & 0x20) == 0)) ? 2 : 1;
        wait.updateRenderStatus(isRendering, isOAMRendering);
    }

    // Register access methods
    public void writeDISPSTAT8_0(int data) {
        IOCore.updateCoreClocking();
        // Only LCD IRQ generation enablers can be set here
        IRQFlags = data & 0x38;
        IOCore.updateCoreEventTime();
    }

    public void writeDISPSTAT8_1(int data) {
        data = data & 0xFF;
        // V-Counter match value
        if (data != VCounter) {
            IOCore.updateCoreClocking();
            VCounter = data;
            checkVCounter();
            IOCore.updateCoreEventTime();
        }
    }

    public void writeDISPSTAT16(int data) {
        IOCore.updateCoreClocking();
        // Only LCD IRQ generation enablers can be set here
        IRQFlags = data & 0x38;
        data = (data >> 8) & 0xFF;
        // V-Counter match value
        if (data != VCounter) {
            VCounter = data;
            checkVCounter();
        }
        IOCore.updateCoreEventTime();
    }

    public int readDISPSTAT8_0() {
        IOCore.updateGraphicsClocking();
        return (statusFlags | IRQFlags);
    }

    public int readDISPSTAT8_1() {
        return VCounter;
    }

    public int readDISPSTAT8_2() {
        IOCore.updateGraphicsClocking();
        return currentScanLine;
    }

    public int readDISPSTAT16_0() {
        IOCore.updateGraphicsClocking();
        return ((VCounter << 8) | statusFlags | IRQFlags);
    }

    public int readDISPSTAT32() {
        IOCore.updateGraphicsClocking();
        return ((currentScanLine << 16) | (VCounter << 8) | statusFlags | IRQFlags);
    }
}
