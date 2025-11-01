package com.iodine.gba.graphics;

import com.iodine.gba.core.GameBoyAdvanceIO;

/**
 * GameBoyAdvanceGraphics - LCD controller with HBlank/VBlank timing
 * Manages scanline rendering and IRQ generation
 */
public class GameBoyAdvanceGraphics {
    public GameBoyAdvanceIO IOCore;
    public GameBoyAdvanceRenderer gfxRenderer;

    // Timing
    public int LCDTicks;
    public int currentScanLine;
    public boolean renderedScanLine;

    // Status flags
    public int statusFlags;  // Bit 0: VBlank, Bit 1: HBlank, Bit 2: VCounter
    public int IRQFlags;     // IRQ enable flags
    public int VCounter;     // VCounter match value

    public GameBoyAdvanceGraphics(GameBoyAdvanceIO ioCore) {
        this.IOCore = ioCore;
    }

    public void initialize() {
        gfxRenderer = IOCore.gfxRenderer;
        LCDTicks = 0;
        currentScanLine = 0;
        renderedScanLine = false;
        statusFlags = 0;
        IRQFlags = 0;
        VCounter = 0;

        if (IOCore.SKIPBoot) {
            // BIOS entered the ROM at line 0x7C
            currentScanLine = 0x7C;
        }
    }

    public void addClocks(int clocks) {
        LCDTicks += clocks;
        clockLCDState();
    }

    public void clockLCDState() {
        if (LCDTicks >= 960) {
            // Line finishes drawing at clock 960
            clockScanLine();
            clockLCDStatePostRender();
        }
    }

    public void clockScanLine() {
        if (!renderedScanLine) {
            renderedScanLine = true;
            if (currentScanLine < 160) {
                // Tell renderer to render this scanline
                gfxRenderer.renderScanLine(currentScanLine);
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
        // Move to next scanline
        renderedScanLine = false;
        statusFlags = statusFlags & 0x5;  // Un-mark HBlank

        // De-clock for starting on new scan-line
        LCDTicks = LCDTicks - 1232;

        // Increment scanline counter
        currentScanLine = currentScanLine + 1;

        // Handle switching in/out of vblank
        if (currentScanLine >= 160) {
            switch (currentScanLine) {
                case 160:
                    updateVBlankStart();
                    break;
                case 228:
                    currentScanLine = 0;  // Reset scan-line to zero
                    break;
            }
        }

        checkVCounter();
        // Recursive clocking of the LCD state
        clockLCDState();
    }

    public void updateHBlank() {
        if ((statusFlags & 0x2) == 0) {
            statusFlags = statusFlags | 0x2;  // Mark HBlank
            if ((IRQFlags & 0x10) != 0) {
                IOCore.irq.requestIRQ(0x2);  // HBlank IRQ
            }
        }
    }

    public void updateVBlankStart() {
        statusFlags = statusFlags | 0x1;  // Mark VBlank
        if ((IRQFlags & 0x8) != 0) {
            IOCore.irq.requestIRQ(0x1);  // VBlank IRQ
        }
        // Prepare frame for display
        gfxRenderer.prepareFrame();
    }

    public void checkVCounter() {
        if (currentScanLine == VCounter) {
            statusFlags = statusFlags | 0x4;
            if ((IRQFlags & 0x20) != 0) {
                IOCore.irq.requestIRQ(0x4);  // VCounter IRQ
            }
        } else {
            statusFlags = statusFlags & 0x3;
        }
    }

    // DISPSTAT register (0x04000004) read
    public int readDISPSTAT16() {
        return ((VCounter & 0xFF) << 8) | (IRQFlags & 0x38) | (statusFlags & 0x7);
    }

    // DISPSTAT register write
    public void writeDISPSTAT16(int value) {
        IRQFlags = value & 0x38;
        VCounter = (value >>> 8) & 0xFF;
    }

    // VCOUNT register (0x04000006) read
    public int readVCOUNT() {
        return currentScanLine & 0xFF;
    }
}
