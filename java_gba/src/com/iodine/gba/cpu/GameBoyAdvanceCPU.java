package com.iodine.gba.cpu;

import com.iodine.gba.core.GameBoyAdvanceIO;
import com.iodine.gba.memory.GameBoyAdvanceMemory;
import com.iodine.gba.memory.GameBoyAdvanceWait;

/**
 * GameBoyAdvanceCPU - ARM7TDMI CPU Core
 * Manages registers, CPU modes, and instruction execution
 */
public class GameBoyAdvanceCPU {
    public GameBoyAdvanceIO IOCore;
    public GameBoyAdvanceMemory memory;
    public GameBoyAdvanceWait wait;

    // Multiply 64-bit result scratch registers
    public int mul64ResultHigh;
    public int mul64ResultLow;

    // R0-R15 Registers (R15 = PC)
    public int[] registers = new int[16];

    // Banked register sets for different CPU modes
    public int[] registersUSR = new int[7];  // R8-R14 for User/System mode
    public int[] registersFIQ = new int[7];  // R8-R14 for FIQ mode
    public int[] registersSVC = new int[2];  // R13-R14 for Supervisor mode
    public int[] registersABT = new int[2];  // R13-R14 for Abort mode
    public int[] registersIRQ = new int[2];  // R13-R14 for IRQ mode
    public int[] registersUND = new int[2];  // R13-R14 for Undefined mode

    // CPSR and SPSR
    public CPSRFlags branchFlags;
    public int modeFlags = 0xD3;

    // Banked SPSR Registers
    public int[] SPSR = new int[5];  // FIQ, IRQ, Supervisor, Abort, Undefined

    public int triggeredIRQ = 0;  // Pending IRQ found

    // Instruction sets
    public ARMInstructionSet ARM;
    public THUMBInstructionSet THUMB;

    public GameBoyAdvanceCPU(GameBoyAdvanceIO ioCore) {
        this.IOCore = ioCore;
    }

    public void initialize() {
        memory = IOCore.memory;
        wait = IOCore.wait;
        mul64ResultHigh = 0;
        mul64ResultLow = 0;
        initializeRegisters();
        ARM = new ARMInstructionSet(this);
        THUMB = new THUMBInstructionSet(this);
        IOCore.assignInstructionCoreReferences(ARM, THUMB);
    }

    public void initializeRegisters() {
        // Initialize SPSR
        SPSR[0] = 0xD3;  // FIQ
        SPSR[1] = 0xD3;  // IRQ
        SPSR[2] = 0xD3;  // Supervisor
        SPSR[3] = 0xD3;  // Abort
        SPSR[4] = 0xD3;  // Undefined

        // Initialize CPSR flags
        branchFlags = new CPSRFlags();
        modeFlags = 0xD3;
        triggeredIRQ = 0;

        // Pre-initialize stack pointers if no BIOS loaded
        if (IOCore.SKIPBoot) {
            HLEReset();
        }

        // Start in fully bubbled pipeline mode
        IOCore.flagBubble();
    }

    public void HLEReset() {
        registersSVC[0] = 0x3007FE0;
        registersIRQ[0] = 0x3007FA0;
        registers[13] = 0x3007F00;
        registers[15] = 0x8000000;
        modeFlags = modeFlags | 0x1f;
    }

    public void branch(int branchTo) {
        // Branch to new address
        registers[15] = branchTo;
        // Mark pipeline as invalid
        IOCore.flagBubble();
        // Next PC fetch has to update the address bus
        wait.NonSequentialBroadcastClear();
    }

    public void triggerIRQ(int didFire) {
        triggeredIRQ = didFire;
        assertIRQ();
    }

    public void assertIRQ() {
        if (triggeredIRQ != 0 && (modeFlags & 0x80) == 0) {
            IOCore.flagIRQ();
        }
    }

    public int getCurrentFetchValue() {
        if ((modeFlags & 0x20) != 0) {
            return THUMB.getCurrentFetchValue();
        } else {
            return ARM.getCurrentFetchValue();
        }
    }

    public void enterARM() {
        modeFlags = modeFlags & 0xdf;
        THUMBBitModify(false);
    }

    public void enterTHUMB() {
        modeFlags = modeFlags | 0x20;
        THUMBBitModify(true);
    }

    public int getLR() {
        // Get the previous instruction address
        if ((modeFlags & 0x20) != 0) {
            return THUMB.getLR();
        } else {
            return ARM.getLR();
        }
    }

    public void THUMBBitModify(boolean isThumb) {
        if (isThumb) {
            IOCore.flagTHUMB();
        } else {
            IOCore.deflagTHUMB();
        }
    }

    public void IRQinARM() {
        // Mode bits are set to IRQ
        switchMode(0x12);
        // Save link register
        registers[14] = ARM.getIRQLR();
        // Disable IRQ
        modeFlags = modeFlags | 0x80;
        // IRQ exception vector
        branch(0x18);
        // Deflag IRQ from state
        IOCore.deflagIRQ();
    }

    public void IRQinTHUMB() {
        // Mode bits are set to IRQ
        switchMode(0x12);
        // Save link register
        registers[14] = THUMB.getIRQLR();
        // Disable IRQ
        modeFlags = modeFlags | 0x80;
        // Exception always enter ARM mode
        enterARM();
        // IRQ exception vector
        branch(0x18);
        // Deflag IRQ from state
        IOCore.deflagIRQ();
    }

    public void SWI() {
        // Mode bits are set to SWI
        switchMode(0x13);
        // Save link register
        registers[14] = getLR();
        // Disable IRQ
        modeFlags = modeFlags | 0x80;
        // Exception always enter ARM mode
        enterARM();
        // SWI exception vector
        branch(0x8);
    }

    public void UNDEFINED() {
        // Mode bits are set to UND
        switchMode(0x1B);
        // Save link register
        registers[14] = getLR();
        // Disable IRQ
        modeFlags = modeFlags | 0x80;
        // Exception always enter ARM mode
        enterARM();
        // Undefined exception vector
        branch(0x4);
    }

    public int SPSRtoCPSR() {
        // Used for leaving an exception and returning to the previous state
        int bank = 1;
        switch (modeFlags & 0x1f) {
            case 0x12:  // IRQ
                break;
            case 0x13:  // Supervisor
                bank = 2;
                break;
            case 0x11:  // FIQ
                bank = 0;
                break;
            case 0x17:  // Abort
                bank = 3;
                break;
            case 0x1B:  // Undefined
                bank = 4;
                break;
            default:  // User & system lacks SPSR
                return modeFlags & 0x20;
        }
        int spsr = SPSR[bank];
        branchFlags.setNZCV(spsr << 20);
        switchRegisterBank(spsr & 0x1F);
        modeFlags = spsr & 0xFF;
        assertIRQ();
        THUMBBitModify((spsr & 0x20) != 0);
        return spsr & 0x20;
    }

    public void switchMode(int newMode) {
        CPSRtoSPSR(newMode);
        switchRegisterBank(newMode);
        modeFlags = (modeFlags & 0xe0) | newMode;
    }

    public void CPSRtoSPSR(int newMode) {
        // Used for entering an exception and saving the previous state
        int spsr = modeFlags & 0xFF;
        spsr = spsr | (branchFlags.getNZCV() >> 20);
        switch (newMode) {
            case 0x12:  // IRQ
                SPSR[1] = spsr;
                break;
            case 0x13:  // Supervisor
                SPSR[2] = spsr;
                break;
            case 0x11:  // FIQ
                SPSR[0] = spsr;
                break;
            case 0x17:  // Abort
                SPSR[3] = spsr;
                break;
            case 0x1B:  // Undefined
                SPSR[4] = spsr;
                break;
        }
    }

    public void switchRegisterBank(int newMode) {
        // Save current mode's banked registers
        switch (modeFlags & 0x1F) {
            case 0x10:  // User
            case 0x1F:  // System
                registersUSR[0] = registers[8];
                registersUSR[1] = registers[9];
                registersUSR[2] = registers[10];
                registersUSR[3] = registers[11];
                registersUSR[4] = registers[12];
                registersUSR[5] = registers[13];
                registersUSR[6] = registers[14];
                break;
            case 0x11:  // FIQ
                registersFIQ[0] = registers[8];
                registersFIQ[1] = registers[9];
                registersFIQ[2] = registers[10];
                registersFIQ[3] = registers[11];
                registersFIQ[4] = registers[12];
                registersFIQ[5] = registers[13];
                registersFIQ[6] = registers[14];
                break;
            case 0x12:  // IRQ
                registersUSR[0] = registers[8];
                registersUSR[1] = registers[9];
                registersUSR[2] = registers[10];
                registersUSR[3] = registers[11];
                registersUSR[4] = registers[12];
                registersIRQ[0] = registers[13];
                registersIRQ[1] = registers[14];
                break;
            case 0x13:  // Supervisor
                registersUSR[0] = registers[8];
                registersUSR[1] = registers[9];
                registersUSR[2] = registers[10];
                registersUSR[3] = registers[11];
                registersUSR[4] = registers[12];
                registersSVC[0] = registers[13];
                registersSVC[1] = registers[14];
                break;
            case 0x17:  // Abort
                registersUSR[0] = registers[8];
                registersUSR[1] = registers[9];
                registersUSR[2] = registers[10];
                registersUSR[3] = registers[11];
                registersUSR[4] = registers[12];
                registersABT[0] = registers[13];
                registersABT[1] = registers[14];
                break;
            case 0x1B:  // Undefined
                registersUSR[0] = registers[8];
                registersUSR[1] = registers[9];
                registersUSR[2] = registers[10];
                registersUSR[3] = registers[11];
                registersUSR[4] = registers[12];
                registersUND[0] = registers[13];
                registersUND[1] = registers[14];
                break;
        }

        // Load new mode's banked registers
        switch (newMode) {
            case 0x10:  // User
            case 0x1F:  // System
                registers[8] = registersUSR[0];
                registers[9] = registersUSR[1];
                registers[10] = registersUSR[2];
                registers[11] = registersUSR[3];
                registers[12] = registersUSR[4];
                registers[13] = registersUSR[5];
                registers[14] = registersUSR[6];
                break;
            case 0x11:  // FIQ
                registers[8] = registersFIQ[0];
                registers[9] = registersFIQ[1];
                registers[10] = registersFIQ[2];
                registers[11] = registersFIQ[3];
                registers[12] = registersFIQ[4];
                registers[13] = registersFIQ[5];
                registers[14] = registersFIQ[6];
                break;
            case 0x12:  // IRQ
                registers[8] = registersUSR[0];
                registers[9] = registersUSR[1];
                registers[10] = registersUSR[2];
                registers[11] = registersUSR[3];
                registers[12] = registersUSR[4];
                registers[13] = registersIRQ[0];
                registers[14] = registersIRQ[1];
                break;
            case 0x13:  // Supervisor
                registers[8] = registersUSR[0];
                registers[9] = registersUSR[1];
                registers[10] = registersUSR[2];
                registers[11] = registersUSR[3];
                registers[12] = registersUSR[4];
                registers[13] = registersSVC[0];
                registers[14] = registersSVC[1];
                break;
            case 0x17:  // Abort
                registers[8] = registersUSR[0];
                registers[9] = registersUSR[1];
                registers[10] = registersUSR[2];
                registers[11] = registersUSR[3];
                registers[12] = registersUSR[4];
                registers[13] = registersABT[0];
                registers[14] = registersABT[1];
                break;
            case 0x1B:  // Undefined
                registers[8] = registersUSR[0];
                registers[9] = registersUSR[1];
                registers[10] = registersUSR[2];
                registers[11] = registersUSR[3];
                registers[12] = registersUSR[4];
                registers[13] = registersUND[0];
                registers[14] = registersUND[1];
                break;
        }
    }

    public int performMUL32(int rs, int rd) {
        // Predict the internal cycle time
        if ((rd >>> 8) == 0 || (rd >>> 8) == 0xFFFFFF) {
            IOCore.wait.CPUInternalSingleCyclePrefetch();
        } else if ((rd >>> 16) == 0 || (rd >>> 16) == 0xFFFF) {
            IOCore.wait.CPUInternalCyclePrefetch(2);
        } else if ((rd >>> 24) == 0 || (rd >>> 24) == 0xFF) {
            IOCore.wait.CPUInternalCyclePrefetch(3);
        } else {
            IOCore.wait.CPUInternalCyclePrefetch(4);
        }
        return rs * rd;  // Java handles 32-bit multiplication correctly
    }

    public int performMUL32MLA(int rs, int rd) {
        // Predict the internal cycle time
        if ((rd >>> 8) == 0 || (rd >>> 8) == 0xFFFFFF) {
            IOCore.wait.CPUInternalCyclePrefetch(2);
        } else if ((rd >>> 16) == 0 || (rd >>> 16) == 0xFFFF) {
            IOCore.wait.CPUInternalCyclePrefetch(3);
        } else if ((rd >>> 24) == 0 || (rd >>> 24) == 0xFF) {
            IOCore.wait.CPUInternalCyclePrefetch(4);
        } else {
            IOCore.wait.CPUInternalCyclePrefetch(5);
        }
        return rs * rd;
    }
}
