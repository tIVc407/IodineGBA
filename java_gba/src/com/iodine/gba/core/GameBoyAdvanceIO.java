package com.iodine.gba.core;

import com.iodine.gba.cpu.*;
import com.iodine.gba.memory.*;
import com.iodine.gba.graphics.*;
import com.iodine.gba.audio.*;
import com.iodine.gba.cartridge.*;

/**
 * GameBoyAdvanceIO - Main I/O core that orchestrates all emulator components
 * This is the central hub that connects CPU, memory, graphics, audio, and peripherals
 */
public class GameBoyAdvanceIO {
    // State Machine Tracking
    public int systemStatus;
    public int cyclesToIterate;
    public int cyclesOveriteratedPreviously;
    public int accumulatedClocks;
    public int graphicsClocks;
    public int timerClocks;
    public int serialClocks;
    public int nextEventClocks;

    // Settings
    public boolean SKIPBoot;

    // References
    public GameBoyAdvanceEmulator coreExposed;
    public byte[] BIOS;
    public byte[] ROM;

    // Core Components
    public GameBoyAdvanceMemory memory;
    public GameBoyAdvanceDMA dma;
    public GameBoyAdvanceDMA0 dmaChannel0;
    public GameBoyAdvanceDMA1 dmaChannel1;
    public GameBoyAdvanceDMA2 dmaChannel2;
    public GameBoyAdvanceDMA3 dmaChannel3;
    public GameBoyAdvanceGraphics gfxState;
    public GameBoyAdvanceRenderer gfxRenderer;
    public GameBoyAdvanceSound sound;
    public GameBoyAdvanceTimer timer;
    public GameBoyAdvanceIRQ irq;
    public GameBoyAdvanceSerial serial;
    public GameBoyAdvanceJoyPad joypad;
    public GameBoyAdvanceCartridge cartridge;
    public GameBoyAdvanceSaves saves;
    public GameBoyAdvanceWait wait;
    public GameBoyAdvanceCPU cpu;

    // Graphics callback
    public com.iodine.gba.graphics.GraphicsFrameCallback graphicsFrameCallback;

    // Instruction Sets
    public ARMInstructionSet ARM;
    public THUMBInstructionSet THUMB;

    public GameBoyAdvanceIO(boolean skipBoot, GameBoyAdvanceEmulator exposed, byte[] bios, byte[] rom) {
        this.systemStatus = 0;
        this.cyclesToIterate = 0;
        this.cyclesOveriteratedPreviously = 0;
        this.accumulatedClocks = 0;
        this.graphicsClocks = 0;
        this.timerClocks = 0;
        this.serialClocks = 0;
        this.nextEventClocks = 0;

        this.SKIPBoot = skipBoot;
        this.coreExposed = exposed;
        this.BIOS = bios;
        this.ROM = rom;

        // Initialize all components
        this.memory = new GameBoyAdvanceMemory(this);
        this.dma = new GameBoyAdvanceDMA(this);
        this.dmaChannel0 = new GameBoyAdvanceDMA0(this);
        this.dmaChannel1 = new GameBoyAdvanceDMA1(this);
        this.dmaChannel2 = new GameBoyAdvanceDMA2(this);
        this.dmaChannel3 = new GameBoyAdvanceDMA3(this);
        this.gfxState = new GameBoyAdvanceGraphics(this);
        this.gfxRenderer = new GameBoyAdvanceRenderer(this);
        this.sound = new GameBoyAdvanceSound(this);
        this.timer = new GameBoyAdvanceTimer(this);
        this.irq = new GameBoyAdvanceIRQ(this);
        this.serial = new GameBoyAdvanceSerial(this);
        this.joypad = new GameBoyAdvanceJoyPad(this);
        this.cartridge = new GameBoyAdvanceCartridge(this);
        this.saves = new GameBoyAdvanceSaves(this);
        this.wait = new GameBoyAdvanceWait(this);
        this.cpu = new GameBoyAdvanceCPU(this);
    }

    public int initialize() {
        int allowInit = 1;
        // Initialize each component
        if (memory.initialize() == 1) {
            dma.initialize();
            dmaChannel0.initialize();
            dmaChannel1.initialize();
            dmaChannel2.initialize();
            dmaChannel3.initialize();
            gfxState.initialize();
            gfxRenderer.initialize(SKIPBoot);
            sound.initialize();
            timer.initialize();
            irq.initialize();
            serial.initialize();
            joypad.initialize();
            cartridge.initialize();
            saves.initialize();
            wait.initialize();
            cpu.initialize();
        } else {
            allowInit = 0;
        }
        return allowInit;
    }

    public void assignInstructionCoreReferences(ARMInstructionSet arm, THUMBInstructionSet thumb) {
        this.ARM = arm;
        this.THUMB = thumb;
    }

    public void enter(int CPUCyclesTotal) {
        // Find out how many clocks to iterate through this run
        cyclesToIterate = CPUCyclesTotal + cyclesOveriteratedPreviously;

        // Extra check to make sure we don't do stuff if we did too much last run
        if (cyclesToIterate > 0) {
            // Update our core event prediction
            updateCoreEventTime();
            // If clocks remaining, run iterator
            run();
            // Spill our core event clocking
            updateCoreClocking();
            // Ensure audio buffers at least once per iteration
            sound.audioJIT();
        }

        // If we clocked just a little too much, subtract the extra from the next run
        cyclesOveriteratedPreviously = cyclesToIterate;
    }

    public void run() {
        // Clock through the state machine
        while (true) {
            // Dispatch to optimized run loops
            switch (systemStatus & 0x84) {
                case 0:
                    // ARM instruction set
                    runARM();
                    break;
                case 0x4:
                    // THUMB instruction set
                    runTHUMB();
                    break;
                default:
                    // End of stepping
                    deflagIterationEnd();
                    return;
            }
        }
    }

    public void runARM() {
        // Clock through the state machine
        while (true) {
            // Handle the current system state selected
            switch (systemStatus) {
                case 0: // CPU Handle State (Normal ARM)
                    ARM.executeIteration();
                    break;
                case 1:
                case 2: // CPU Handle State (Bubble ARM)
                    ARM.executeBubble();
                    tickBubble();
                    break;
                default: // Handle lesser called / End of stepping
                    // Dispatch on IRQ/DMA/HALT/STOP/END bit flags
                    switch (systemStatus >> 2) {
                        case 0x2:
                            // IRQ Handle State
                            handleIRQARM();
                            break;
                        case 0x4:
                        case 0x6:
                            // DMA Handle State
                        case 0xC:
                        case 0xE:
                            // DMA Inside Halt State
                            handleDMA();
                            break;
                        case 0x8:
                        case 0xA:
                            // Handle Halt State
                            handleHalt();
                            break;
                        default: // Handle Stop State
                            // THUMB flagged stuff falls to here intentionally
                            // End of Stepping and/or CPU run loop switch
                            if ((systemStatus & 0x84) != 0) {
                                return;
                            }
                            handleStop();
                    }
            }
        }
    }

    public void runTHUMB() {
        // Clock through the state machine
        while (true) {
            // Handle the current system state selected
            switch (systemStatus) {
                case 4: // CPU Handle State (Normal THUMB)
                    THUMB.executeIteration();
                    break;
                case 5:
                case 6: // CPU Handle State (Bubble THUMB)
                    THUMB.executeBubble();
                    tickBubble();
                    break;
                default: // Handle lesser called / End of stepping
                    // Dispatch on IRQ/DMA/HALT/STOP/END bit flags
                    switch (systemStatus >> 2) {
                        case 0x3:
                            // IRQ Handle State
                            handleIRQThumb();
                            break;
                        case 0x5:
                        case 0x7:
                            // DMA Handle State
                        case 0xD:
                        case 0xF:
                            // DMA Inside Halt State
                            handleDMA();
                            break;
                        case 0x9:
                        case 0x11:
                            // Handle Halt State
                            handleHalt();
                            break;
                        default: // Handle Stop State
                            // ARM flagged stuff falls to here intentionally
                            // End of Stepping and/or CPU run loop switch
                            if ((systemStatus & 0x84) != 0x4) {
                                return;
                            }
                            handleStop();
                    }
            }
        }
    }

    public void updateCore(int clocks) {
        // This is used during normal/dma modes of operation
        accumulatedClocks += clocks;
        if (accumulatedClocks >= nextEventClocks) {
            updateCoreSpill();
        }
    }

    public void updateCoreSingle() {
        // This is used during normal/dma modes of operation
        accumulatedClocks += 1;
        if (accumulatedClocks >= nextEventClocks) {
            updateCoreSpill();
        }
    }

    public void updateCoreForce(int clocks) {
        // This is used during halt mode of operation
        accumulatedClocks += clocks;
        updateCoreSpill();
    }

    public void updateCoreSpill() {
        // Invalidate & recompute new event times
        updateCoreClocking();
        updateCoreEventTime();
    }

    public void updateCoreClocking() {
        int clocks = accumulatedClocks;
        // Decrement the clocks per iteration counter
        cyclesToIterate -= clocks;
        // Clock all components
        gfxState.addClocks(clocks - graphicsClocks);
        timer.addClocks(clocks - timerClocks);
        serial.addClocks(clocks - serialClocks);
        accumulatedClocks = 0;
        graphicsClocks = 0;
        timerClocks = 0;
        serialClocks = 0;
    }

    public void updateCoreEventTime() {
        // Predict how many clocks until the next DMA or IRQ event
        nextEventClocks = cyclesUntilNextEvent();
    }

    public int getRemainingCycles() {
        // Return the number of cycles left until iteration end
        if (cyclesToIterate < 1) {
            // Change our stepper to our end sequence
            flagIterationEnd();
            return 0;
        }
        return cyclesToIterate;
    }

    public void handleIRQARM() {
        if (systemStatus > 0x8) {
            // CPU Handle State (Bubble ARM)
            ARM.executeBubble();
            tickBubble();
        } else {
            // CPU Handle State (IRQ)
            cpu.IRQinARM();
        }
    }

    public void handleIRQThumb() {
        if (systemStatus > 0xC) {
            // CPU Handle State (Bubble THUMB)
            THUMB.executeBubble();
            tickBubble();
        } else {
            // CPU Handle State (IRQ)
            cpu.IRQinTHUMB();
        }
    }

    public void handleDMA() {
        // Loop our state status in here as an optimized iteration
        do {
            // Perform a DMA read and write
            dma.perform();
        } while ((systemStatus & 0x90) == 0x10);
    }

    public void handleHalt() {
        if (irq.IRQMatch() == 0) {
            // Clock up to next IRQ match or DMA
            updateCoreForce(cyclesUntilNextHALTEvent());
        } else {
            // Exit HALT promptly
            deflagHalt();
        }
    }

    public void handleStop() {
        // Update sound system to add silence to buffer
        sound.addClocks(getRemainingCycles());
        cyclesToIterate = 0;
    }

    public int cyclesUntilNextHALTEvent() {
        // Find the clocks to the next HALT leave or DMA event
        int haltClocks = irq.nextEventTime();
        int dmaClocks = dma.nextEventTime();
        return solveClosestTime(haltClocks, dmaClocks);
    }

    public int cyclesUntilNextEvent() {
        // Find the clocks to the next IRQ or DMA event
        int irqClocks = irq.nextIRQEventTime();
        int dmaClocks = dma.nextEventTime();
        return solveClosestTime(irqClocks, dmaClocks);
    }

    public int solveClosestTime(int clocks1, int clocks2) {
        // Find the clocks closest to the next event
        int clocks = getRemainingCycles();
        clocks = Math.min(clocks, Math.min(clocks1, clocks2));
        return clocks;
    }

    // Flag manipulation methods
    public void flagBubble() {
        systemStatus |= 0x2;
    }

    public void tickBubble() {
        systemStatus -= 1;
    }

    public void flagTHUMB() {
        systemStatus |= 0x4;
    }

    public void deflagTHUMB() {
        systemStatus &= 0xFB;
    }

    public void flagIRQ() {
        systemStatus |= 0x8;
    }

    public void deflagIRQ() {
        systemStatus &= 0xF7;
    }

    public void flagDMA() {
        systemStatus |= 0x10;
    }

    public void deflagDMA() {
        systemStatus &= 0xEF;
    }

    public void flagHalt() {
        systemStatus |= 0x20;
    }

    public void deflagHalt() {
        systemStatus &= 0xDF;
    }

    public void flagStop() {
        systemStatus |= 0x40;
    }

    public void deflagStop() {
        systemStatus &= 0xBF;
    }

    public void flagIterationEnd() {
        systemStatus |= 0x80;
    }

    public void deflagIterationEnd() {
        systemStatus &= 0x7F;
    }

    public boolean isStopped() {
        return (systemStatus & 0x40) != 0;
    }

    public boolean inDMA() {
        return (systemStatus & 0x10) != 0;
    }

    public int getCurrentFetchValue() {
        int fetch = 0;
        if ((systemStatus & 0x10) == 0) {
            fetch = cpu.getCurrentFetchValue();
        } else {
            fetch = dma.getCurrentFetchValue();
        }
        return fetch;
    }

    public void updateGraphicsClocking() {
        // Update graphics clocking to ensure accurate timing
        if (graphicsClocks > 0) {
            gfxState.addClocks(graphicsClocks);
            graphicsClocks = 0;
        }
    }

    public void updateTimerClocking() {
        sound.audioJIT();
    }
}
