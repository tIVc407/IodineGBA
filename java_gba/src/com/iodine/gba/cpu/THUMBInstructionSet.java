package com.iodine.gba.cpu;

import com.iodine.gba.memory.GameBoyAdvanceMemory;

/**
 * THUMBInstructionSet - 16-bit THUMB instruction decoder and executor
 * Implements the compressed THUMB instruction set of ARM7TDMI
 */
public class THUMBInstructionSet {
    public GameBoyAdvanceCPU cpu;
    public GameBoyAdvanceMemory memory;
    public int[] registers;
    public CPSRFlags flags;

    // Pipeline
    public int fetch;
    public int decode;
    public int execute;

    public THUMBInstructionSet(GameBoyAdvanceCPU cpu) {
        this.cpu = cpu;
        this.memory = cpu.memory;
        this.registers = cpu.registers;
        this.flags = cpu.branchFlags;
    }

    public void executeIteration() {
        // Fetch next instruction from PC
        int pc = registers[15];
        execute = memory.CPUReadTHUMB(pc) & 0xFFFF;

        // Decode and execute THUMB instruction
        decodeTHUMB(execute);

        // Advance PC
        registers[15] += 2;
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
        return registers[15] - 2;
    }

    public int getIRQLR() {
        return registers[15] - 2;
    }

    public void incrementProgramCounter() {
        registers[15] += 2;
    }

    public void decodeTHUMB(int instruction) {
        // Decode based on the upper bits
        int opcode = (instruction >>> 8) & 0xFF;

        if ((instruction & 0xF800) == 0x1800) {
            // Add/subtract
            executeAddSubtract(instruction);
        } else if ((instruction & 0xE000) == 0x0000) {
            // Shift by immediate
            executeShiftImmediate(instruction);
        } else if ((instruction & 0xE000) == 0x2000) {
            // Add/subtract/compare/move immediate
            executeImmediateOps(instruction);
        } else if ((instruction & 0xFC00) == 0x4000) {
            // ALU operations
            executeALU(instruction);
        } else if ((instruction & 0xFC00) == 0x4400) {
            // Hi register operations/branch exchange
            executeHiRegisterOps(instruction);
        } else if ((instruction & 0xF800) == 0x4800) {
            // PC-relative load
            executePCRelativeLoad(instruction);
        } else if ((instruction & 0xF200) == 0x5000) {
            // Load/store with register offset
            executeLoadStoreRegOffset(instruction);
        } else if ((instruction & 0xE000) == 0x6000) {
            // Load/store with immediate offset
            executeLoadStoreImmOffset(instruction);
        } else if ((instruction & 0xF000) == 0x8000) {
            // Load/store halfword
            executeLoadStoreHalfword(instruction);
        } else if ((instruction & 0xF000) == 0x9000) {
            // SP-relative load/store
            executeSPRelativeLoadStore(instruction);
        } else if ((instruction & 0xF000) == 0xA000) {
            // Load address
            executeLoadAddress(instruction);
        } else if ((instruction & 0xFF00) == 0xB000) {
            // Add offset to SP / Push/Pop
            if ((instruction & 0x0F00) == 0x0000) {
                executeAddOffsetToSP(instruction);
            } else {
                executePushPop(instruction);
            }
        } else if ((instruction & 0xF000) == 0xC000) {
            // Multiple load/store
            executeMultipleLoadStore(instruction);
        } else if ((instruction & 0xFF00) == 0xDF00) {
            // Software interrupt
            cpu.SWI();
        } else if ((instruction & 0xF000) == 0xD000) {
            // Conditional branch
            executeConditionalBranch(instruction);
        } else if ((instruction & 0xF800) == 0xE000) {
            // Unconditional branch
            executeUnconditionalBranch(instruction);
        } else if ((instruction & 0xF000) == 0xF000) {
            // Long branch with link
            executeLongBranchLink(instruction);
        } else {
            // Unknown instruction
            cpu.UNDEFINED();
        }
    }

    public void executeShiftImmediate(int instruction) {
        int opcode = (instruction >>> 11) & 0x3;
        int offset = (instruction >>> 6) & 0x1F;
        int rs = (instruction >>> 3) & 0x7;
        int rd = instruction & 0x7;

        int value = registers[rs];
        int result;

        switch (opcode) {
            case 0:  // LSL
                result = offset == 0 ? value : value << offset;
                break;
            case 1:  // LSR
                result = offset == 0 ? 0 : value >>> offset;
                break;
            case 2:  // ASR
                result = offset == 0 ? (value >> 31) : value >> offset;
                break;
            default:
                result = value;
        }

        registers[rd] = result;
        flags.setLogicFlags(result);
    }

    public void executeAddSubtract(int instruction) {
        boolean immediate = (instruction & 0x400) != 0;
        boolean subtract = (instruction & 0x200) != 0;
        int rn = (instruction >>> 6) & 0x7;
        int rs = (instruction >>> 3) & 0x7;
        int rd = instruction & 0x7;

        int operand1 = registers[rs];
        int operand2 = immediate ? rn : registers[rn];

        if (subtract) {
            registers[rd] = flags.setSUBFlags(operand1, operand2);
        } else {
            registers[rd] = flags.setADDFlags(operand1, operand2);
        }
    }

    public void executeImmediateOps(int instruction) {
        int opcode = (instruction >>> 11) & 0x3;
        int rd = (instruction >>> 8) & 0x7;
        int offset = instruction & 0xFF;

        switch (opcode) {
            case 0:  // MOV
                registers[rd] = offset;
                flags.setLogicFlags(offset);
                break;
            case 1:  // CMP
                flags.setSUBFlags(registers[rd], offset);
                break;
            case 2:  // ADD
                registers[rd] = flags.setADDFlags(registers[rd], offset);
                break;
            case 3:  // SUB
                registers[rd] = flags.setSUBFlags(registers[rd], offset);
                break;
        }
    }

    public void executeALU(int instruction) {
        int opcode = (instruction >>> 6) & 0xF;
        int rs = (instruction >>> 3) & 0x7;
        int rd = instruction & 0x7;

        int operand1 = registers[rd];
        int operand2 = registers[rs];
        int result;

        switch (opcode) {
            case 0x0:  // AND
                result = operand1 & operand2;
                flags.setLogicFlags(result);
                registers[rd] = result;
                break;
            case 0x1:  // EOR
                result = operand1 ^ operand2;
                flags.setLogicFlags(result);
                registers[rd] = result;
                break;
            case 0x2:  // LSL
                result = operand1 << (operand2 & 0xFF);
                flags.setLogicFlags(result);
                registers[rd] = result;
                break;
            case 0x3:  // LSR
                result = operand1 >>> (operand2 & 0xFF);
                flags.setLogicFlags(result);
                registers[rd] = result;
                break;
            case 0x4:  // ASR
                result = operand1 >> (operand2 & 0xFF);
                flags.setLogicFlags(result);
                registers[rd] = result;
                break;
            case 0x5:  // ADC
                registers[rd] = flags.setADCFlags(operand1, operand2);
                break;
            case 0x6:  // SBC
                registers[rd] = flags.setSBCFlags(operand1, operand2);
                break;
            case 0x7:  // ROR
                int shift = operand2 & 0xFF;
                result = (operand1 >>> shift) | (operand1 << (32 - shift));
                flags.setLogicFlags(result);
                registers[rd] = result;
                break;
            case 0x8:  // TST
                result = operand1 & operand2;
                flags.setLogicFlags(result);
                break;
            case 0x9:  // NEG
                registers[rd] = flags.setSUBFlags(0, operand2);
                break;
            case 0xA:  // CMP
                flags.setSUBFlags(operand1, operand2);
                break;
            case 0xB:  // CMN
                flags.setADDFlags(operand1, operand2);
                break;
            case 0xC:  // ORR
                result = operand1 | operand2;
                flags.setLogicFlags(result);
                registers[rd] = result;
                break;
            case 0xD:  // MUL
                result = cpu.performMUL32(operand1, operand2);
                flags.setLogicFlags(result);
                registers[rd] = result;
                break;
            case 0xE:  // BIC
                result = operand1 & ~operand2;
                flags.setLogicFlags(result);
                registers[rd] = result;
                break;
            case 0xF:  // MVN
                result = ~operand2;
                flags.setLogicFlags(result);
                registers[rd] = result;
                break;
        }
    }

    public void executeHiRegisterOps(int instruction) {
        int opcode = (instruction >>> 8) & 0x3;
        boolean h1 = (instruction & 0x80) != 0;
        boolean h2 = (instruction & 0x40) != 0;
        int rs = ((instruction >>> 3) & 0x7) | (h2 ? 0x8 : 0);
        int rd = (instruction & 0x7) | (h1 ? 0x8 : 0);

        switch (opcode) {
            case 0:  // ADD
                registers[rd] = registers[rd] + registers[rs];
                if (rd == 15) {
                    cpu.branch(registers[15] & ~1);
                }
                break;
            case 1:  // CMP
                flags.setSUBFlags(registers[rd], registers[rs]);
                break;
            case 2:  // MOV
                registers[rd] = registers[rs];
                if (rd == 15) {
                    cpu.branch(registers[15] & ~1);
                }
                break;
            case 3:  // BX
                int address = registers[rs];
                if ((address & 1) != 0) {
                    cpu.branch(address & ~1);
                } else {
                    cpu.enterARM();
                    cpu.branch(address & ~3);
                }
                break;
        }
    }

    public void executePCRelativeLoad(int instruction) {
        int rd = (instruction >>> 8) & 0x7;
        int offset = (instruction & 0xFF) << 2;
        int address = (registers[15] & ~2) + offset;
        registers[rd] = memory.CPURead32(address);
    }

    public void executeLoadStoreRegOffset(int instruction) {
        int ro = (instruction >>> 6) & 0x7;
        int rb = (instruction >>> 3) & 0x7;
        int rd = instruction & 0x7;
        boolean load = (instruction & 0x800) != 0;
        boolean byteTransfer = (instruction & 0x400) != 0;

        int address = registers[rb] + registers[ro];

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
    }

    public void executeLoadStoreImmOffset(int instruction) {
        int offset = (instruction >>> 6) & 0x1F;
        int rb = (instruction >>> 3) & 0x7;
        int rd = instruction & 0x7;
        boolean load = (instruction & 0x800) != 0;
        boolean byteTransfer = (instruction & 0x1000) != 0;

        int address = registers[rb] + (byteTransfer ? offset : offset << 2);

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
    }

    public void executeLoadStoreHalfword(int instruction) {
        int offset = ((instruction >>> 6) & 0x1F) << 1;
        int rb = (instruction >>> 3) & 0x7;
        int rd = instruction & 0x7;
        boolean load = (instruction & 0x800) != 0;

        int address = registers[rb] + offset;

        if (load) {
            registers[rd] = memory.CPURead16(address) & 0xFFFF;
        } else {
            memory.CPUWrite16(address, registers[rd] & 0xFFFF);
        }
    }

    public void executeSPRelativeLoadStore(int instruction) {
        int rd = (instruction >>> 8) & 0x7;
        int offset = (instruction & 0xFF) << 2;
        boolean load = (instruction & 0x800) != 0;

        int address = registers[13] + offset;

        if (load) {
            registers[rd] = memory.CPURead32(address);
        } else {
            memory.CPUWrite32(address, registers[rd]);
        }
    }

    public void executeLoadAddress(int instruction) {
        int rd = (instruction >>> 8) & 0x7;
        int offset = (instruction & 0xFF) << 2;
        boolean sp = (instruction & 0x800) != 0;

        if (sp) {
            registers[rd] = registers[13] + offset;
        } else {
            registers[rd] = (registers[15] & ~2) + offset;
        }
    }

    public void executeAddOffsetToSP(int instruction) {
        int offset = (instruction & 0x7F) << 2;
        boolean negative = (instruction & 0x80) != 0;

        if (negative) {
            registers[13] -= offset;
        } else {
            registers[13] += offset;
        }
    }

    public void executePushPop(int instruction) {
        boolean load = (instruction & 0x800) != 0;
        boolean pcOrLr = (instruction & 0x100) != 0;
        int regList = instruction & 0xFF;

        int address = registers[13];

        if (load) {
            // POP
            for (int i = 0; i < 8; i++) {
                if ((regList & (1 << i)) != 0) {
                    registers[i] = memory.CPURead32(address);
                    address += 4;
                }
            }
            if (pcOrLr) {
                int pc = memory.CPURead32(address);
                address += 4;
                cpu.branch(pc & ~1);
            }
            registers[13] = address;
        } else {
            // PUSH
            int count = Integer.bitCount(regList) + (pcOrLr ? 1 : 0);
            address -= count * 4;
            int tempAddress = address;

            for (int i = 0; i < 8; i++) {
                if ((regList & (1 << i)) != 0) {
                    memory.CPUWrite32(tempAddress, registers[i]);
                    tempAddress += 4;
                }
            }
            if (pcOrLr) {
                memory.CPUWrite32(tempAddress, registers[14]);
            }
            registers[13] = address;
        }
    }

    public void executeMultipleLoadStore(int instruction) {
        int rb = (instruction >>> 8) & 0x7;
        int regList = instruction & 0xFF;
        boolean load = (instruction & 0x800) != 0;

        int address = registers[rb];

        for (int i = 0; i < 8; i++) {
            if ((regList & (1 << i)) != 0) {
                if (load) {
                    registers[i] = memory.CPURead32(address);
                } else {
                    memory.CPUWrite32(address, registers[i]);
                }
                address += 4;
            }
        }

        registers[rb] = address;
    }

    public void executeConditionalBranch(int instruction) {
        int condition = (instruction >>> 8) & 0xF;
        int offset = (instruction & 0xFF) << 1;

        // Sign extend
        if ((offset & 0x100) != 0) {
            offset |= 0xFFFFFE00;
        }

        // Check condition using ARM condition checker
        ARMInstructionSet arm = cpu.ARM;
        if (arm.checkCondition(condition)) {
            cpu.branch(registers[15] + offset);
        }
    }

    public void executeUnconditionalBranch(int instruction) {
        int offset = (instruction & 0x7FF) << 1;

        // Sign extend
        if ((offset & 0x800) != 0) {
            offset |= 0xFFFFF000;
        }

        cpu.branch(registers[15] + offset);
    }

    public void executeLongBranchLink(int instruction) {
        boolean secondInstruction = (instruction & 0x800) != 0;
        int offset = instruction & 0x7FF;

        if (secondInstruction) {
            // Second instruction - complete the branch
            int temp = registers[15] - 2;
            cpu.branch((registers[14] + (offset << 1)) & ~1);
            registers[14] = temp | 1;
        } else {
            // First instruction - store high part
            offset <<= 12;
            // Sign extend
            if ((offset & 0x400000) != 0) {
                offset |= 0xFF800000;
            }
            registers[14] = registers[15] + offset;
        }
    }
}
