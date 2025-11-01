package com.iodine.gba.cpu;

import com.iodine.gba.memory.GameBoyAdvanceMemory;

/**
 * ARMInstructionSet - 32-bit ARM instruction decoder and executor
 * Implements the ARM7TDMI instruction set
 */
public class ARMInstructionSet {
    public GameBoyAdvanceCPU cpu;
    public GameBoyAdvanceMemory memory;
    public int[] registers;
    public CPSRFlags flags;

    // Pipeline
    public int fetch;
    public int decode;
    public int execute;

    public ARMInstructionSet(GameBoyAdvanceCPU cpu) {
        this.cpu = cpu;
        this.memory = cpu.memory;
        this.registers = cpu.registers;
        this.flags = cpu.branchFlags;
    }

    public void executeIteration() {
        // Fetch next instruction from PC
        int pc = registers[15];
        execute = memory.CPUReadARM(pc);

        // Check condition codes
        if (checkCondition(execute >>> 28)) {
            // Decode and execute based on instruction type
            decodeARM(execute);
        }

        // Advance PC
        registers[15] += 4;
        cpu.IOCore.wait.CPUInternalSingleCyclePrefetch();
    }

    public void executeBubble() {
        // Execute a pipeline bubble (NOP)
        cpu.IOCore.wait.CPUInternalSingleCyclePrefetch();
    }

    public int getCurrentFetchValue() {
        return execute;
    }

    public int getLR() {
        return registers[15] - 4;
    }

    public int getIRQLR() {
        return registers[15] - 4;
    }

    public void incrementProgramCounter() {
        registers[15] += 4;
    }

    public boolean checkCondition(int condition) {
        switch (condition) {
            case 0x0:  // EQ - Equal (Z set)
                return flags.getZeroFlag();
            case 0x1:  // NE - Not equal (Z clear)
                return !flags.getZeroFlag();
            case 0x2:  // CS/HS - Carry set / unsigned higher or same
                return flags.getCarryFlag();
            case 0x3:  // CC/LO - Carry clear / unsigned lower
                return !flags.getCarryFlag();
            case 0x4:  // MI - Negative (N set)
                return flags.getNegativeFlag();
            case 0x5:  // PL - Positive or zero (N clear)
                return !flags.getNegativeFlag();
            case 0x6:  // VS - Overflow (V set)
                return flags.getOverflowFlag();
            case 0x7:  // VC - No overflow (V clear)
                return !flags.getOverflowFlag();
            case 0x8:  // HI - Unsigned higher (C set and Z clear)
                return flags.getCarryFlag() && !flags.getZeroFlag();
            case 0x9:  // LS - Unsigned lower or same (C clear or Z set)
                return !flags.getCarryFlag() || flags.getZeroFlag();
            case 0xA:  // GE - Greater or equal (N == V)
                return flags.getNegativeFlag() == flags.getOverflowFlag();
            case 0xB:  // LT - Less than (N != V)
                return flags.getNegativeFlag() != flags.getOverflowFlag();
            case 0xC:  // GT - Greater than (Z clear and N == V)
                return !flags.getZeroFlag() && (flags.getNegativeFlag() == flags.getOverflowFlag());
            case 0xD:  // LE - Less than or equal (Z set or N != V)
                return flags.getZeroFlag() || (flags.getNegativeFlag() != flags.getOverflowFlag());
            case 0xE:  // AL - Always
                return true;
            case 0xF:  // Reserved (treated as NV in ARM7)
                return false;
            default:
                return false;
        }
    }

    public void decodeARM(int instruction) {
        // Decode ARM instruction based on bits 27-20 and 7-4
        int opcode = (instruction >>> 20) & 0xFF;
        int type = (instruction >>> 25) & 0x7;

        switch (type) {
            case 0:  // Data processing / PSR transfer / Multiply
                if ((instruction & 0x90) == 0x90) {
                    // Multiply or swap
                    if ((instruction & 0x60) == 0) {
                        // Multiply
                        executeMultiply(instruction);
                    } else {
                        // Swap or other
                        executeDataProcessing(instruction);
                    }
                } else {
                    // Data processing
                    executeDataProcessing(instruction);
                }
                break;
            case 1:  // Data processing immediate
                executeDataProcessing(instruction);
                break;
            case 2:  // Load/Store immediate offset
            case 3:  // Load/Store register offset
                executeLoadStore(instruction);
                break;
            case 4:  // Load/Store multiple
                executeLoadStoreMultiple(instruction);
                break;
            case 5:  // Branch and branch with link
                executeBranch(instruction);
                break;
            case 6:  // Coprocessor load/store
            case 7:  // Coprocessor operations / SWI
                if ((instruction & 0x0F000000) == 0x0F000000) {
                    // SWI
                    cpu.SWI();
                } else {
                    // Coprocessor (not implemented - undefined)
                    cpu.UNDEFINED();
                }
                break;
        }
    }

    public void executeDataProcessing(int instruction) {
        int opcode = (instruction >>> 21) & 0xF;
        int rn = (instruction >>> 16) & 0xF;
        int rd = (instruction >>> 12) & 0xF;
        boolean setFlags = (instruction & 0x100000) != 0;

        int operand1 = registers[rn];
        int operand2 = getOperand2(instruction);

        int result = 0;

        switch (opcode) {
            case 0x0:  // AND
                result = operand1 & operand2;
                if (setFlags) flags.setLogicFlags(result);
                registers[rd] = result;
                break;
            case 0x1:  // EOR
                result = operand1 ^ operand2;
                if (setFlags) flags.setLogicFlags(result);
                registers[rd] = result;
                break;
            case 0x2:  // SUB
                result = flags.setSUBFlags(operand1, operand2);
                if (!setFlags) result = operand1 - operand2;
                registers[rd] = result;
                break;
            case 0x3:  // RSB
                result = flags.setSUBFlags(operand2, operand1);
                if (!setFlags) result = operand2 - operand1;
                registers[rd] = result;
                break;
            case 0x4:  // ADD
                result = flags.setADDFlags(operand1, operand2);
                if (!setFlags) result = operand1 + operand2;
                registers[rd] = result;
                break;
            case 0x5:  // ADC
                result = flags.setADCFlags(operand1, operand2);
                if (!setFlags) result = operand1 + operand2 + (flags.carry);
                registers[rd] = result;
                break;
            case 0x6:  // SBC
                result = flags.setSBCFlags(operand1, operand2);
                if (!setFlags) result = operand1 - operand2 - (1 - flags.carry);
                registers[rd] = result;
                break;
            case 0x7:  // RSC
                result = flags.setSBCFlags(operand2, operand1);
                if (!setFlags) result = operand2 - operand1 - (1 - flags.carry);
                registers[rd] = result;
                break;
            case 0x8:  // TST
                result = operand1 & operand2;
                flags.setLogicFlags(result);
                break;
            case 0x9:  // TEQ
                result = operand1 ^ operand2;
                flags.setLogicFlags(result);
                break;
            case 0xA:  // CMP
                flags.setSUBFlags(operand1, operand2);
                break;
            case 0xB:  // CMN
                flags.setADDFlags(operand1, operand2);
                break;
            case 0xC:  // ORR
                result = operand1 | operand2;
                if (setFlags) flags.setLogicFlags(result);
                registers[rd] = result;
                break;
            case 0xD:  // MOV
                result = operand2;
                if (setFlags) flags.setLogicFlags(result);
                registers[rd] = result;
                break;
            case 0xE:  // BIC
                result = operand1 & ~operand2;
                if (setFlags) flags.setLogicFlags(result);
                registers[rd] = result;
                break;
            case 0xF:  // MVN
                result = ~operand2;
                if (setFlags) flags.setLogicFlags(result);
                registers[rd] = result;
                break;
        }
    }

    public int getOperand2(int instruction) {
        if ((instruction & 0x2000000) != 0) {
            // Immediate operand
            int imm = instruction & 0xFF;
            int rotate = ((instruction >>> 8) & 0xF) * 2;
            return (imm >>> rotate) | (imm << (32 - rotate));
        } else {
            // Register operand
            int rm = instruction & 0xF;
            int shift = (instruction >>> 5) & 0x3;
            int shiftAmount;

            if ((instruction & 0x10) != 0) {
                // Shift by register
                int rs = (instruction >>> 8) & 0xF;
                shiftAmount = registers[rs] & 0xFF;
            } else {
                // Shift by immediate
                shiftAmount = (instruction >>> 7) & 0x1F;
            }

            return performShift(registers[rm], shift, shiftAmount);
        }
    }

    public int performShift(int value, int shiftType, int amount) {
        switch (shiftType) {
            case 0:  // LSL
                return amount >= 32 ? 0 : value << amount;
            case 1:  // LSR
                return amount >= 32 ? 0 : value >>> amount;
            case 2:  // ASR
                return amount >= 32 ? (value >> 31) : value >> amount;
            case 3:  // ROR
                amount &= 31;
                return (value >>> amount) | (value << (32 - amount));
            default:
                return value;
        }
    }

    public void executeMultiply(int instruction) {
        int rd = (instruction >>> 16) & 0xF;
        int rn = (instruction >>> 12) & 0xF;
        int rs = (instruction >>> 8) & 0xF;
        int rm = instruction & 0xF;
        boolean setFlags = (instruction & 0x100000) != 0;
        boolean accumulate = (instruction & 0x200000) != 0;

        int result;
        if (accumulate) {
            result = cpu.performMUL32MLA(registers[rm], registers[rs]) + registers[rn];
        } else {
            result = cpu.performMUL32(registers[rm], registers[rs]);
        }

        registers[rd] = result;
        if (setFlags) {
            flags.setLogicFlags(result);
        }
    }

    public void executeLoadStore(int instruction) {
        int rn = (instruction >>> 16) & 0xF;
        int rd = (instruction >>> 12) & 0xF;
        boolean load = (instruction & 0x100000) != 0;
        boolean byteTransfer = (instruction & 0x400000) != 0;
        boolean preIndex = (instruction & 0x1000000) != 0;
        boolean up = (instruction & 0x800000) != 0;

        int offset = instruction & 0xFFF;
        int address = registers[rn];

        if (preIndex) {
            address = up ? address + offset : address - offset;
        }

        if (load) {
            if (byteTransfer) {
                registers[rd] = memory.CPURead8(address) & 0xFF;
            } else {
                registers[rd] = memory.CPURead32(address);
            }
        } else {
            if (byteTransfer) {
                memory.CPUWrite8(address, registers[rd] & 0xFF);
            } else {
                memory.CPUWrite32(address, registers[rd]);
            }
        }

        if (!preIndex) {
            address = up ? address + offset : address - offset;
            registers[rn] = address;
        }
    }

    public void executeLoadStoreMultiple(int instruction) {
        int rn = (instruction >>> 16) & 0xF;
        int regList = instruction & 0xFFFF;
        boolean load = (instruction & 0x100000) != 0;
        boolean up = (instruction & 0x800000) != 0;
        boolean preIndex = (instruction & 0x1000000) != 0;

        int address = registers[rn];

        for (int i = 0; i < 16; i++) {
            if ((regList & (1 << i)) != 0) {
                if (preIndex) {
                    address = up ? address + 4 : address - 4;
                }

                if (load) {
                    registers[i] = memory.CPURead32(address);
                } else {
                    memory.CPUWrite32(address, registers[i]);
                }

                if (!preIndex) {
                    address = up ? address + 4 : address - 4;
                }
            }
        }

        registers[rn] = address;
    }

    public void executeBranch(int instruction) {
        int offset = (instruction & 0xFFFFFF) << 2;
        // Sign extend
        if ((offset & 0x2000000) != 0) {
            offset |= 0xFC000000;
        }

        boolean link = (instruction & 0x1000000) != 0;

        if (link) {
            registers[14] = registers[15] - 4;
        }

        cpu.branch(registers[15] + offset);
    }
}
