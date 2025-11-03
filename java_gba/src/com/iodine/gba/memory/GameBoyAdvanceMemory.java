package com.iodine.gba.memory;

import com.iodine.gba.core.GameBoyAdvanceIO;
import com.iodine.gba.core.GameBoyAdvanceJoyPad;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * GameBoyAdvanceMemory - Memory management system
 * Handles BIOS, WRAM, VRAM, OAM, Palette RAM, and I/O registers
 */
public class GameBoyAdvanceMemory {
    public GameBoyAdvanceIO IOCore;

    // BIOS (16KB)
    public byte[] BIOS;
    public ShortBuffer BIOS16;
    public IntBuffer BIOS32;

    // External WRAM (256KB)
    public byte[] externalRAM;
    public ShortBuffer externalRAM16;
    public IntBuffer externalRAM32;

    // Internal WRAM (32KB)
    public byte[] internalRAM;
    public ShortBuffer internalRAM16;
    public IntBuffer internalRAM32;

    // VRAM (96KB)
    public byte[] VRAM;
    public ShortBuffer VRAM16;
    public IntBuffer VRAM32;

    // OAM - Object Attribute Memory (1KB)
    public byte[] OAM;
    public ShortBuffer OAM16;
    public IntBuffer OAM32;

    // Palette RAM (1KB)
    public byte[] paletteRAM;
    public ShortBuffer paletteRAM16;
    public IntBuffer paletteRAM32;

    // I/O Registers buffer
    public byte[] ioRegisters;

    public int lastBIOSREAD = 0;
    public int WRAMControlFlags = 0x20;

    // Component references
    public GameBoyAdvanceWait wait;
    public GameBoyAdvanceJoyPad joypad;

    public GameBoyAdvanceMemory(GameBoyAdvanceIO ioCore) {
        this.IOCore = ioCore;
    }

    public int initialize() {
        // Load BIOS
        BIOS = new byte[0x4000];  // 16KB
        BIOS16 = ByteBuffer.wrap(BIOS).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        BIOS32 = ByteBuffer.wrap(BIOS).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();

        if (loadBIOS() == 1) {
            initializeRAM();
            return 1;
        } else {
            return 0;
        }
    }

    public int loadBIOS() {
        // Load BIOS from IOCore
        if (IOCore.BIOS != null && IOCore.BIOS.length >= 0x4000) {
            System.arraycopy(IOCore.BIOS, 0, BIOS, 0, 0x4000);
            return 1;
        }
        // Allow skip boot - fill with simple boot sequence
        if (IOCore.SKIPBoot) {
            // Fill with NOP instructions to allow boot skip
            for (int i = 0; i < BIOS.length; i++) {
                BIOS[i] = 0;
            }
            return 1;
        }
        System.out.println("WARNING: No BIOS loaded!");
        return 0;
    }

    public void initializeRAM() {
        // Initialize external WRAM (256KB)
        externalRAM = new byte[0x40000];
        externalRAM16 = ByteBuffer.wrap(externalRAM).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        externalRAM32 = ByteBuffer.wrap(externalRAM).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();

        // Initialize internal WRAM (32KB)
        internalRAM = new byte[0x8000];
        internalRAM16 = ByteBuffer.wrap(internalRAM).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        internalRAM32 = ByteBuffer.wrap(internalRAM).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();

        // Initialize VRAM (96KB)
        VRAM = new byte[0x18000];
        VRAM16 = ByteBuffer.wrap(VRAM).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        VRAM32 = ByteBuffer.wrap(VRAM).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();

        // Initialize OAM (1KB)
        OAM = new byte[0x400];
        OAM16 = ByteBuffer.wrap(OAM).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        OAM32 = ByteBuffer.wrap(OAM).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();

        // Initialize Palette RAM (1KB)
        paletteRAM = new byte[0x400];
        paletteRAM16 = ByteBuffer.wrap(paletteRAM).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        paletteRAM32 = ByteBuffer.wrap(paletteRAM).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();

        // Initialize I/O registers (1KB)
        ioRegisters = new byte[0x400];

        // Get component references
        wait = IOCore.wait;
        joypad = IOCore.joypad;
    }

    // Memory read methods
    public int CPURead8(int address) {
        address &= 0x0FFFFFFF;

        if (address < 0x4000) {
            // BIOS
            return BIOS[address] & 0xFF;
        } else if (address >= 0x02000000 && address < 0x03000000) {
            // External WRAM
            return externalRAM[address & 0x3FFFF] & 0xFF;
        } else if (address >= 0x03000000 && address < 0x04000000) {
            // Internal WRAM
            return internalRAM[address & 0x7FFF] & 0xFF;
        } else if (address >= 0x04000000 && address < 0x04000400) {
            // I/O Registers
            return readIORegister8(address);
        } else if (address >= 0x05000000 && address < 0x05000400) {
            // Palette RAM
            return paletteRAM[address & 0x3FF] & 0xFF;
        } else if (address >= 0x06000000 && address < 0x06018000) {
            // VRAM
            return VRAM[address & 0x1FFFF] & 0xFF;
        } else if (address >= 0x07000000 && address < 0x07000400) {
            // OAM
            return OAM[address & 0x3FF] & 0xFF;
        } else if (address >= 0x08000000 && address < 0x0E000000) {
            // Game Pak ROM
            return IOCore.cartridge.readROM8(address);
        } else if (address >= 0x0E000000 && address < 0x0E010000) {
            // Game Pak SRAM
            return IOCore.saves.readSRAM(address);
        }

        return 0;
    }

    public int CPURead16(int address) {
        address &= 0x0FFFFFFE;

        if (address < 0x4000) {
            // BIOS
            return BIOS16.get(address >> 1) & 0xFFFF;
        } else if (address >= 0x02000000 && address < 0x03000000) {
            // External WRAM
            return externalRAM16.get((address & 0x3FFFF) >> 1) & 0xFFFF;
        } else if (address >= 0x03000000 && address < 0x04000000) {
            // Internal WRAM
            return internalRAM16.get((address & 0x7FFF) >> 1) & 0xFFFF;
        } else if (address >= 0x04000000 && address < 0x04000400) {
            // I/O Registers
            return readIORegister16(address);
        } else if (address >= 0x05000000 && address < 0x05000400) {
            // Palette RAM
            return paletteRAM16.get((address & 0x3FF) >> 1) & 0xFFFF;
        } else if (address >= 0x06000000 && address < 0x06018000) {
            // VRAM
            return VRAM16.get((address & 0x1FFFF) >> 1) & 0xFFFF;
        } else if (address >= 0x07000000 && address < 0x07000400) {
            // OAM
            return OAM16.get((address & 0x3FF) >> 1) & 0xFFFF;
        } else if (address >= 0x08000000 && address < 0x0E000000) {
            // Game Pak ROM
            return IOCore.cartridge.readROM16(address);
        }

        return 0;
    }

    public int CPURead32(int address) {
        address &= 0x0FFFFFFC;

        if (address < 0x4000) {
            // BIOS
            return BIOS32.get(address >> 2);
        } else if (address >= 0x02000000 && address < 0x03000000) {
            // External WRAM
            return externalRAM32.get((address & 0x3FFFF) >> 2);
        } else if (address >= 0x03000000 && address < 0x04000000) {
            // Internal WRAM
            return internalRAM32.get((address & 0x7FFF) >> 2);
        } else if (address >= 0x04000000 && address < 0x04000400) {
            // I/O Registers
            return readIORegister32(address);
        } else if (address >= 0x05000000 && address < 0x05000400) {
            // Palette RAM
            return paletteRAM32.get((address & 0x3FF) >> 2);
        } else if (address >= 0x06000000 && address < 0x06018000) {
            // VRAM
            return VRAM32.get((address & 0x1FFFF) >> 2);
        } else if (address >= 0x07000000 && address < 0x07000400) {
            // OAM
            return OAM32.get((address & 0x3FF) >> 2);
        } else if (address >= 0x08000000 && address < 0x0E000000) {
            // Game Pak ROM
            return IOCore.cartridge.readROM32(address);
        }

        return 0;
    }

    // Memory write methods
    public void CPUWrite8(int address, int data) {
        address &= 0x0FFFFFFF;

        if (address >= 0x02000000 && address < 0x03000000) {
            // External WRAM
            wait.WRAMAccess();
            externalRAM[address & 0x3FFFF] = (byte) data;
        } else if (address >= 0x03000000 && address < 0x04000000) {
            // Internal WRAM
            wait.singleClock();
            internalRAM[address & 0x7FFF] = (byte) data;
        } else if (address >= 0x04000000 && address < 0x04000400) {
            // I/O Registers
            writeIORegister8(address, data);
        } else if (address >= 0x05000000 && address < 0x05000400) {
            // Palette RAM
            paletteRAM[address & 0x3FF] = (byte) data;
        } else if (address >= 0x06000000 && address < 0x06018000) {
            // VRAM
            VRAM[address & 0x1FFFF] = (byte) data;
        } else if (address >= 0x07000000 && address < 0x07000400) {
            // OAM
            OAM[address & 0x3FF] = (byte) data;
        } else if (address >= 0x0E000000 && address < 0x0E010000) {
            // Game Pak SRAM
            IOCore.saves.writeSRAM(address, data);
        }
    }

    public void CPUWrite16(int address, int data) {
        address &= 0x0FFFFFFE;

        if (address >= 0x02000000 && address < 0x03000000) {
            // External WRAM
            wait.WRAMAccess();
            externalRAM16.put((address & 0x3FFFF) >> 1, (short) data);
        } else if (address >= 0x03000000 && address < 0x04000000) {
            // Internal WRAM
            wait.singleClock();
            internalRAM16.put((address & 0x7FFF) >> 1, (short) data);
        } else if (address >= 0x04000000 && address < 0x04000400) {
            // I/O Registers
            writeIORegister16(address, data);
        } else if (address >= 0x05000000 && address < 0x05000400) {
            // Palette RAM
            paletteRAM16.put((address & 0x3FF) >> 1, (short) data);
        } else if (address >= 0x06000000 && address < 0x06018000) {
            // VRAM
            VRAM16.put((address & 0x1FFFF) >> 1, (short) data);
        } else if (address >= 0x07000000 && address < 0x07000400) {
            // OAM
            OAM16.put((address & 0x3FF) >> 1, (short) data);
        }
    }

    public void CPUWrite32(int address, int data) {
        address &= 0x0FFFFFFC;

        if (address >= 0x02000000 && address < 0x03000000) {
            // External WRAM
            wait.WRAMAccess32();
            externalRAM32.put((address & 0x3FFFF) >> 2, data);
        } else if (address >= 0x03000000 && address < 0x04000000) {
            // Internal WRAM
            wait.singleClock();
            internalRAM32.put((address & 0x7FFF) >> 2, data);
        } else if (address >= 0x04000000 && address < 0x04000400) {
            // I/O Registers
            writeIORegister32(address, data);
        } else if (address >= 0x05000000 && address < 0x05000400) {
            // Palette RAM
            paletteRAM32.put((address & 0x3FF) >> 2, data);
        } else if (address >= 0x06000000 && address < 0x06018000) {
            // VRAM
            VRAM32.put((address & 0x1FFFF) >> 2, data);
        } else if (address >= 0x07000000 && address < 0x07000400) {
            // OAM
            OAM32.put((address & 0x3FF) >> 2, data);
        }
    }

    // ARM/THUMB specific reads
    public int CPUReadARM(int address) {
        wait.CPUGetAccess32(address);
        return CPURead32(address);
    }

    public int CPUReadTHUMB(int address) {
        wait.CPUGetAccess16(address);
        return CPURead16(address);
    }

    public int readIORegister8(int address) {
        wait.singleClock();
        switch (address) {
            case 0x4000130:
                return joypad.readKeyStatus8_0();
            case 0x4000131:
                return joypad.readKeyStatus8_1();
            case 0x4000132:
                return joypad.readKeyControl8_0();
            case 0x4000133:
                return joypad.readKeyControl8_1();
            default:
                return 0;
        }
    }

    public int readIORegister16(int address) {
        wait.singleClock();
        switch (address & 0xFFFFFFFE) {
            case 0x4000130:
                return joypad.readKeyStatus16();
            case 0x4000132:
                return joypad.readKeyControl16();
            default:
                return 0;
        }
    }

    public int readIORegister32(int address) {
        wait.singleClock();
        if ((address & 0xFFFFFFFC) == 0x4000130) {
            return joypad.readKeyStatusControl32();
        }
        return 0;
    }

    public void writeIORegister8(int address, int data) {
        wait.singleClock();
        switch (address) {
            case 0x4000132:
                joypad.writeKeyControl8_0(data);
                break;
            case 0x4000133:
                joypad.writeKeyControl8_1(data);
                break;
        }
    }

    public void writeIORegister16(int address, int data) {
        wait.singleClock();
        if ((address & 0xFFFFFFFE) == 0x4000132) {
            joypad.writeKeyControl16(data);
        }
    }

    public void writeIORegister32(int address, int data) {
        wait.singleClock();
        if ((address & 0xFFFFFFFC) == 0x4000130) {
            joypad.writeKeyControl16(data >> 16);
        }
    }

    public int memoryReadDMA16(int address) {
        return CPURead16(address);
    }

    public void memoryWriteDMA16(int address, int data) {
        CPUWrite16(address, data);
    }

    public int memoryReadDMA32(int address) {
        return CPURead32(address);
    }

    public void memoryWriteDMA32(int address, int data) {
        CPUWrite32(address, data);
    }

    public int memoryReadDMAFull16(int address) {
        return CPURead16(address);
    }

    public int memoryReadDMAFull32(int address) {
        return CPURead32(address);
    }

    public void memoryWriteDMAFull16(int address, int data) {
        CPUWrite16(address, data);
    }

    public void memoryWriteDMAFull32(int address, int data) {
        CPUWrite32(address, data);
    }
}
