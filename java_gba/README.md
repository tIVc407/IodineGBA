# IodineGBA - Java Edition

A Game Boy Advance emulator written in vanilla Java, converted from the original JavaScript IodineGBA emulator by Grant Galitz.

## Features

- **Full ARM7TDMI CPU emulation** - Both ARM and THUMB instruction sets
- **Complete memory system** - BIOS, WRAM, VRAM, OAM, Palette RAM, and cartridge support
- **Graphics rendering** - 240x160 display with frame buffer
- **Input handling** - Full controller support via keyboard
- **Save system** - SRAM support for game saves
- **Swing GUI** - User-friendly interface with file loading

## Requirements

- Java 8 or higher
- GBA BIOS file (optional, can skip boot)
- GBA ROM files (.gba)

## Building

### Linux/Mac:
```bash
chmod +x compile.sh
./compile.sh
```

### Windows:
```bash
javac -d bin -sourcepath src src/com/iodine/gba/Main.java
```

## Running

### Linux/Mac:
```bash
chmod +x run.sh
./run.sh
```

### Windows:
```bash
java -cp bin com.iodine.gba.Main
```

## Controls

| Key | GBA Button |
|-----|------------|
| Arrow Keys | D-Pad |
| Z | A Button |
| X | B Button |
| Enter | Start |
| Backspace | Select |
| A | R Shoulder |
| S | L Shoulder |

## Usage

1. Launch the emulator
2. Click "Load BIOS" and select your GBA BIOS file (or skip if using boot skip mode)
3. Click "Load ROM" and select a GBA ROM file
4. Click "Start" to begin emulation
5. Use keyboard controls to play

## Architecture

The emulator is organized into several packages:

- **com.iodine.gba.core** - Core emulation engine (IOCore, Emulator shell, peripherals)
- **com.iodine.gba.cpu** - ARM7TDMI CPU implementation (ARM/THUMB instruction sets)
- **com.iodine.gba.memory** - Memory management system (BIOS, RAM, VRAM, DMA, Wait states)
- **com.iodine.gba.graphics** - Graphics rendering pipeline
- **com.iodine.gba.audio** - Audio synthesis (PSG + Direct Sound)
- **com.iodine.gba.cartridge** - ROM loading and save management
- **com.iodine.gba.ui** - Swing-based GUI

## Components

### CPU
- **ARM Instruction Set** - 32-bit instructions with conditional execution
- **THUMB Instruction Set** - 16-bit compressed instructions
- **CPSR Flags** - Status register with N, Z, C, V flags
- **Register Banking** - 7 CPU modes with banked registers

### Memory
- **BIOS** - 16KB boot ROM
- **External WRAM** - 256KB
- **Internal WRAM** - 32KB
- **VRAM** - 96KB video RAM
- **OAM** - 1KB object attribute memory
- **Palette RAM** - 1KB color palette
- **Cartridge** - Up to 32MB ROM

### Peripherals
- **DMA** - 4 channel Direct Memory Access
- **Timers** - 4 hardware timers
- **IRQ** - Interrupt controller
- **Serial I/O** - Link cable emulation
- **JoyPad** - Controller input

## Testing

To test the emulator:

1. Place test ROMs in the `roms/` directory
2. Load a commercial or homebrew ROM
3. Verify that:
   - The ROM boots correctly
   - Graphics display properly
   - Input responds
   - The game runs at a reasonable speed

## Known Limitations

This is a simplified but functional implementation. Some advanced features may not be fully implemented:

- Audio system is stubbed (no sound output yet)
- Graphics modes are simplified
- Some I/O registers are not fully implemented
- DMA is simplified

## Performance

The emulator should run at approximately 60 FPS on modern hardware. Performance may vary depending on the complexity of the ROM being emulated.

## Credits

- **Original IodineGBA** - Grant Galitz (2012-2019)
- **Java Conversion** - 2025
- Licensed under MIT License

## License

MIT License - See original IodineGBA project for full license text.

Copyright (C) 2012-2019 Grant Galitz

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
