package com.iodine.gba.graphics;

import com.iodine.gba.core.GameBoyAdvanceIO;
import com.iodine.gba.memory.GameBoyAdvanceMemory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * GameBoyAdvanceRenderer - Graphics rendering engine (converted from Renderer.js)
 * Copyright (C) 2012-2016 Grant Galitz
 */
public class GameBoyAdvanceRenderer {
    public GameBoyAdvanceIO IOCore;
    public GameBoyAdvanceMemory memory;

    // Display control registers
    public int displayControl;  // DISPCNT
    public int display;
    public int greenSwap;
    public int WINOutside;

    // VRAM and Palette
    public byte[] VRAM;
    public java.nio.ShortBuffer VRAM16;
    public java.nio.IntBuffer VRAM32;
    public byte[] paletteRAM;
    public java.nio.ShortBuffer paletteRAM16;
    public java.nio.IntBuffer paletteRAM32;

    // Rendering buffers
    public int[] buffer;
    public int[] lineBuffer;
    public int[] frameBuffer;        // Internal buffer to composite to (38400 = 240*160)
    public byte[] swizzledFrame;     // Swizzled output buffer (115200 = 240*160*3 RGB)

    public int totalLinesPassed;
    public int queuedScanLines;
    public int lastUnrenderedLine;
    public int backdrop;

    // Palette storage (both BG and OAM in unified storage)
    public int[] palette256;
    public int[] paletteOBJ256;
    public int[] palette16;
    public int[] paletteOBJ16;

    // Sub-renderers
    public GameBoyAdvanceCompositor compositor;
    public GameBoyAdvanceBGTEXTRenderer bg0Renderer;
    public GameBoyAdvanceBGTEXTRenderer bg1Renderer;
    public GameBoyAdvanceBGTEXTRenderer bg2TextRenderer;
    public GameBoyAdvanceBGTEXTRenderer bg3TextRenderer;
    public GameBoyAdvanceAffineBGRenderer bgAffineRenderer0;
    public GameBoyAdvanceAffineBGRenderer bgAffineRenderer1;
    public GameBoyAdvanceBGMatrixRenderer bg2MatrixRenderer;
    public GameBoyAdvanceBGMatrixRenderer bg3MatrixRenderer;
    public GameBoyAdvanceBG2FrameBufferRenderer bg2FrameBufferRenderer;
    public GameBoyAdvanceOBJRenderer objRenderer;
    public GameBoyAdvanceWindowRenderer window0Renderer;
    public GameBoyAdvanceWindowRenderer window1Renderer;
    public GameBoyAdvanceOBJWindowRenderer objWindowRenderer;
    public GameBoyAdvanceMosaicRenderer mosaicRenderer;
    public GameBoyAdvanceColorEffectsRenderer colorEffectsRenderer;

    public GameBoyAdvanceRenderer(GameBoyAdvanceIO ioCore) {
        this.IOCore = ioCore;
    }

    public void initialize(boolean skippingBIOS) {
        memory = IOCore.memory;
        initializeIO(skippingBIOS);
        initializePaletteStorage();
        generateRenderers();
        initializeRenderers();
    }

    public void initializeIO(boolean skippingBIOS) {
        // Initialize Pre-Boot
        displayControl = 0x80;
        display = 0;
        greenSwap = 0;
        WINOutside = 0;

        // Use memory's VRAM and palette RAM
        VRAM = memory.VRAM;
        paletteRAM = memory.paletteRAM;

        // Create ByteBuffer views for 16-bit and 32-bit access
        VRAM16 = ByteBuffer.wrap(VRAM).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().asReadOnlyBuffer();
        VRAM32 = ByteBuffer.wrap(VRAM).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().asReadOnlyBuffer();
        paletteRAM16 = ByteBuffer.wrap(paletteRAM).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().asReadOnlyBuffer();
        paletteRAM32 = ByteBuffer.wrap(paletteRAM).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().asReadOnlyBuffer();

        // Allocate buffers
        buffer = new int[0x680];
        lineBuffer = buffer;  // First 240 elements used for line buffer
        frameBuffer = new int[38400];        // 240 * 160
        swizzledFrame = new byte[115200];    // 240 * 160 * 3 (RGB)

        totalLinesPassed = 0;
        queuedScanLines = 0;
        lastUnrenderedLine = 0;

        if (skippingBIOS) {
            // BIOS entered the ROM at line 0x7C
            lastUnrenderedLine = 0x7C;
        }

        backdrop = 0x3A00000;
    }

    public void initializePaletteStorage() {
        // Both BG and OAM in unified storage
        palette256 = new int[0x100];
        palette256[0] = 0x3800000;
        paletteOBJ256 = new int[0x100];
        paletteOBJ256[0] = 0x3800000;
        palette16 = new int[0x100];
        paletteOBJ16 = new int[0x100];
        for (int index = 0; index < 0x10; index++) {
            palette16[index << 4] = 0x3800000;
            paletteOBJ16[index << 4] = 0x3800000;
        }
    }

    public void generateRenderers() {
        compositor = new GameBoyAdvanceCompositor(this);
        bg0Renderer = new GameBoyAdvanceBGTEXTRenderer(this, 0);
        bg1Renderer = new GameBoyAdvanceBGTEXTRenderer(this, 1);
        bg2TextRenderer = new GameBoyAdvanceBGTEXTRenderer(this, 2);
        bg3TextRenderer = new GameBoyAdvanceBGTEXTRenderer(this, 3);
        bgAffineRenderer0 = new GameBoyAdvanceAffineBGRenderer(this, 2);
        bgAffineRenderer1 = new GameBoyAdvanceAffineBGRenderer(this, 3);
        bg2MatrixRenderer = new GameBoyAdvanceBGMatrixRenderer(this);
        bg3MatrixRenderer = new GameBoyAdvanceBGMatrixRenderer(this);
        bg2FrameBufferRenderer = new GameBoyAdvanceBG2FrameBufferRenderer(this);
        objRenderer = new GameBoyAdvanceOBJRenderer(this);
        window0Renderer = new GameBoyAdvanceWindowRenderer(new GameBoyAdvanceWindowCompositor(this));
        window1Renderer = new GameBoyAdvanceWindowRenderer(new GameBoyAdvanceWindowCompositor(this));
        objWindowRenderer = new GameBoyAdvanceOBJWindowRenderer(new GameBoyAdvanceOBJWindowCompositor(this));
        mosaicRenderer = new GameBoyAdvanceMosaicRenderer(buffer);
        colorEffectsRenderer = new GameBoyAdvanceColorEffectsRenderer(buffer);
    }

    public void initializeRenderers() {
        compositor.initialize();
        compositorPreprocess();
        bg0Renderer.initialize();
        bg1Renderer.initialize();
        bg2TextRenderer.initialize();
        bg3TextRenderer.initialize();
        bgAffineRenderer0.initialize();
        bgAffineRenderer1.initialize();
        bg2MatrixRenderer.initialize();
        bg3MatrixRenderer.initialize();
        bg2FrameBufferRenderer.initialize();
        objRenderer.initialize();
        window0Renderer.initialize();
        window1Renderer.initialize();
        objWindowRenderer.initialize();
    }

    public void swizzleFrameBuffer() {
        // Convert our dirty 15-bit (15-bit, with internal render flags above it)
        // framebuffer to an 8-bit buffer with separate indices for the RGB channels
        int bufferIndex = 0;
        for (int canvasIndex = 0; canvasIndex < 115200; bufferIndex++) {
            swizzledFrame[canvasIndex++] = (byte)(((frameBuffer[bufferIndex] & 0x1F) << 3) & 0xFF);      // Red
            swizzledFrame[canvasIndex++] = (byte)(((frameBuffer[bufferIndex] & 0x3E0) >> 2) & 0xFF);     // Green
            swizzledFrame[canvasIndex++] = (byte)(((frameBuffer[bufferIndex] & 0x7C00) >> 7) & 0xFF);    // Blue
        }
    }

    public void prepareFrame() {
        // Copy the internal frame buffer to the output buffer
        swizzleFrameBuffer();
        requestDraw();
    }

    public void requestDraw() {
        // Update graphics - copy swizzled frame to display
        if (IOCore.graphicsFrameCallback != null) {
            IOCore.graphicsFrameCallback.onFrame(swizzledFrame);
        }
    }

    public void graphicsJIT() {
        totalLinesPassed = 0;  // Mark frame for ensuring a JIT pass for the next framebuffer output
        graphicsJITScanlineGroup();
    }

    public void graphicsJITVBlank() {
        // JIT the graphics to v-blank framing
        totalLinesPassed = totalLinesPassed + queuedScanLines;
        graphicsJITScanlineGroup();
    }

    public void graphicsJITScanlineGroup() {
        // Normal rendering JIT, where we try to do groups of scanlines at once
        while (queuedScanLines > 0) {
            renderScanLine();
            incrementScanLine();
            queuedScanLines--;
        }
    }

    public void incrementScanLineQueue() {
        if (queuedScanLines < 160) {
            queuedScanLines++;
        } else {
            incrementScanLine();
        }
    }

    public void ensureFraming() {
        // Ensure JIT framing alignment
        if (totalLinesPassed < 160) {
            // Make sure our gfx are up-to-date
            graphicsJITVBlank();
            // Draw the frame
            prepareFrame();
        }
    }

    public void renderScanLine() {
        int line = lastUnrenderedLine;
        if ((displayControl & 0x80) == 0) {
            // Render with the current mode selected
            switch (displayControl & 0x7) {
                case 0:
                    // Mode 0
                    renderMode0(line);
                    break;
                case 1:
                    // Mode 1
                    renderMode1(line);
                    break;
                case 2:
                    // Mode 2
                    renderMode2(line);
                    break;
                default:
                    // Modes 3-5
                    renderModeFrameBuffer(line);
            }
            // Copy line to our framebuffer
            copyLineToFrameBuffer(line);
        } else {
            // Forced blank is on, rendering disabled
            renderForcedBlank(line);
        }
        // Update the affine bg counters
        updateReferenceCounters();
    }

    public void incrementScanLine() {
        if (lastUnrenderedLine < 159) {
            lastUnrenderedLine++;
        } else {
            lastUnrenderedLine = 0;
        }
    }

    public void renderMode0(int line) {
        // Mode 0 Rendering Selected
        int toRender = display & 0x1F;
        if ((toRender & 0x1) != 0) {
            // Render the BG0 layer
            bg0Renderer.renderScanLine(line);
        }
        if ((toRender & 0x2) != 0) {
            // Render the BG1 layer
            bg1Renderer.renderScanLine(line);
        }
        if ((toRender & 0x4) != 0) {
            // Render the BG2 layer
            bg2TextRenderer.renderScanLine(line);
        }
        if ((toRender & 0x8) != 0) {
            // Render the BG3 layer
            bg3TextRenderer.renderScanLine(line);
        }
        if ((toRender & 0x10) != 0) {
            // Render the sprite layer
            objRenderer.renderScanLine(line);
        }
        // Composite the non-windowed result
        compositeLayers(toRender);
        // Composite the windowed result
        compositeWindowedLayers(line, toRender);
    }

    public void renderMode1(int line) {
        // Mode 1 Rendering Selected
        int toRender = display & 0x17;
        if ((toRender & 0x1) != 0) {
            // Render the BG0 layer
            bg0Renderer.renderScanLine(line);
        }
        if ((toRender & 0x2) != 0) {
            // Render the BG1 layer
            bg1Renderer.renderScanLine(line);
        }
        if ((toRender & 0x4) != 0) {
            // Render the BG2 layer
            bgAffineRenderer0.renderScanLine2M(line);
        }
        if ((toRender & 0x10) != 0) {
            // Render the sprite layer
            objRenderer.renderScanLine(line);
        }
        // Composite the non-windowed result
        compositeLayers(toRender);
        // Composite the windowed result
        compositeWindowedLayers(line, toRender);
    }

    public void renderMode2(int line) {
        // Mode 2 Rendering Selected
        int toRender = display & 0x1C;
        if ((toRender & 0x4) != 0) {
            // Render the BG2 layer
            bgAffineRenderer0.renderScanLine2M(line);
        }
        if ((toRender & 0x8) != 0) {
            // Render the BG3 layer
            bgAffineRenderer1.renderScanLine3M(line);
        }
        if ((toRender & 0x10) != 0) {
            // Render the sprite layer
            objRenderer.renderScanLine(line);
        }
        // Composite the non-windowed result
        compositeLayers(toRender);
        // Composite the windowed result
        compositeWindowedLayers(line, toRender);
    }

    public void renderModeFrameBuffer(int line) {
        // Mode 3/4/5 Rendering Selected
        int toRender = display & 0x14;
        if ((toRender & 0x4) != 0) {
            bgAffineRenderer0.renderScanLine2F(line);
        }
        if ((toRender & 0x10) != 0) {
            // Render the sprite layer
            objRenderer.renderScanLine(line);
        }
        // Composite the non-windowed result
        compositeLayers(toRender);
        // Composite the windowed result
        compositeWindowedLayers(line, toRender);
    }

    public void compositeLayers(int toRender) {
        if ((display & 0xE0) > 0) {
            // Window registers can further disable background layers if one or more window layers enabled
            toRender = toRender & WINOutside;
        }
        // Composite the non-windowed result
        compositor.renderScanLine(toRender);
    }

    public void compositeWindowedLayers(int line, int toRender) {
        // Composite the windowed result
        if ((display & 0x90) == 0x90) {
            // Object Window
            objWindowRenderer.renderScanLine(line, toRender);
        }
        if ((display & 0x40) != 0) {
            // Window 1
            window1Renderer.renderScanLine(line, toRender);
        }
        if ((display & 0x20) != 0) {
            // Window 0
            window0Renderer.renderScanLine(line, toRender);
        }
    }

    public void copyLineToFrameBuffer(int line) {
        int offsetStart = line * 240;
        if (greenSwap == 0) {
            // Blit normally
            copyLineToFrameBufferNormal(offsetStart);
        } else {
            // Blit with green swap
            copyLineToFrameBufferGreenSwapped(offsetStart);
        }
    }

    public void renderForcedBlank(int line) {
        int offsetStart = line * 240;
        // Render a blank line (white)
        for (int position = 0; position < 240; position++) {
            frameBuffer[offsetStart++] = 0x7FFF;
        }
    }

    public void copyLineToFrameBufferNormal(int offsetStart) {
        // Render a line - copy from line buffer to frame buffer
        System.arraycopy(buffer, 0, frameBuffer, offsetStart, 240);
    }

    public void copyLineToFrameBufferGreenSwapped(int offsetStart) {
        // Render a line with green swap effect
        int position = 0;
        while (position < 240) {
            int pixel0 = buffer[position++];
            int pixel1 = buffer[position++];
            frameBuffer[offsetStart++] = (pixel0 & 0x7C1F) | (pixel1 & 0x3E0);
            frameBuffer[offsetStart++] = (pixel1 & 0x7C1F) | (pixel0 & 0x3E0);
        }
    }

    public void updateReferenceCounters() {
        if (lastUnrenderedLine == 159) {
            // Reset some affine bg counters on roll-over to line 0
            bgAffineRenderer0.resetReferenceCounters();
            bgAffineRenderer1.resetReferenceCounters();
        } else {
            // Increment the affine bg counters
            bgAffineRenderer0.incrementReferenceCounters();
            bgAffineRenderer1.incrementReferenceCounters();
        }
    }

    public void compositorPreprocess() {
        int controlBits = WINOutside & 0x20;
        if ((display & 0xE0) == 0) {
            controlBits = controlBits | 1;
        }
        compositor.preprocess(controlBits);
    }

    public void frameBufferModePreprocess(int displayControl) {
        displayControl = Math.min(displayControl & 0x7, 5);
        // Set up pixel fetcher ahead of time
        if (displayControl > 2) {
            bg2FrameBufferRenderer.selectMode(displayControl);
        }
    }

    // Register write methods
    public void writeDISPCNT8_0(int data) {
        graphicsJIT();
        bg2FrameBufferRenderer.writeFrameSelect((data & 0x10) << 27);
        objRenderer.setHBlankIntervalFreeStatus(data & 0x20);
        frameBufferModePreprocess(data);
        displayControl = data;
    }

    public void writeDISPCNT8_1(int data) {
        graphicsJIT();
        display = data & 0xFF;
        compositorPreprocess();
    }

    public void writeDISPCNT8_2(int data) {
        graphicsJIT();
        greenSwap = data & 0x01;
    }

    public void writeDISPCNT16(int data) {
        graphicsJIT();
        bg2FrameBufferRenderer.writeFrameSelect((data & 0x10) << 27);
        objRenderer.setHBlankIntervalFreeStatus(data & 0x20);
        frameBufferModePreprocess(data);
        displayControl = data;
        display = data >> 8;
        compositorPreprocess();
    }

    public void writeDISPCNT32(int data) {
        graphicsJIT();
        bg2FrameBufferRenderer.writeFrameSelect((data & 0x10) << 27);
        objRenderer.setHBlankIntervalFreeStatus(data & 0x20);
        frameBufferModePreprocess(data);
        displayControl = data;
        display = (data >> 8) & 0xFF;
        compositorPreprocess();
        greenSwap = data & 0x10000;
    }

    public int readDISPCNT() {
        return displayControl;
    }

    public byte[] getSwizzledFrame() {
        return swizzledFrame;
    }

    public int[] getFrameBuffer() {
        return frameBuffer;
    }

    // Palette write methods
    public void writePalette16(int address, int data) {
        graphicsJIT();
        memory.paletteRAM16.put(address & 0x1FF, (short)(data & 0xFFFF));
        data = data & 0x7FFF;
        writePalette256Color(address, data);
        writePalette16Color(address, data);
    }

    public void writePalette32(int address, int data) {
        graphicsJIT();
        memory.paletteRAM32.put(address & 0xFF, data);
        address = address << 1;
        int palette = data & 0x7FFF;
        writePalette256Color(address, palette);
        writePalette16Color(address, palette);
        palette = (data >> 16) & 0x7FFF;
        writePalette256Color(address | 1, palette);
        writePalette16Color(address | 1, palette);
    }

    public void writePalette256Color(int address, int palette) {
        if ((address & 0xFF) == 0) {
            palette = 0x3800000 | palette;
            if (address == 0) {
                backdrop = palette | 0x200000;
            }
        }
        if (address < 0x100) {
            palette256[address & 0xFF] = palette;
        } else {
            paletteOBJ256[address & 0xFF] = palette;
        }
    }

    public void writePalette16Color(int address, int palette) {
        if ((address & 0xF) == 0) {
            palette = 0x3800000 | palette;
        }
        if (address < 0x100) {
            // BG Layer Palette
            palette16[address & 0xFF] = palette;
        } else {
            // OBJ Layer Palette
            paletteOBJ16[address & 0xFF] = palette;
        }
    }

    public int readPalette16(int address) {
        return memory.paletteRAM16.get(address & 0x1FF) & 0xFFFF;
    }

    public int readPalette32(int address) {
        return memory.paletteRAM32.get(address & 0xFF);
    }

    public int readPalette8(int address) {
        return memory.paletteRAM[address & 0x3FF] & 0xFF;
    }

    // VRAM write methods
    public void writeVRAM16(int address, int data) {
        graphicsJIT();
        memory.VRAM16.put(address & 0xFFFF, (short)(data & 0xFFFF));
    }

    public void writeVRAM32(int address, int data) {
        graphicsJIT();
        memory.VRAM32.put(address & 0x7FFF, data);
    }

    public int readVRAM8(int address) {
        return memory.VRAM[address & 0x1FFFF] & 0xFF;
    }

    public int readVRAM16(int address) {
        return memory.VRAM16.get(address & 0xFFFF) & 0xFFFF;
    }

    public int readVRAM32(int address) {
        return memory.VRAM32.get(address & 0x7FFF);
    }

    // OAM access methods
    public void writeOAM16(int address, int data) {
        graphicsJIT();
        objRenderer.writeOAM16(address & 0x1FF, data & 0xFFFF);
    }

    public void writeOAM32(int address, int data) {
        graphicsJIT();
        objRenderer.writeOAM32(address & 0xFF, data);
    }

    public int readOAM(int address) {
        return objRenderer.readOAM(address);
    }

    public int readOAM16(int address) {
        return objRenderer.readOAM16(address);
    }

    public int readOAM32(int address) {
        return objRenderer.readOAM32(address);
    }

    // Background control register writes (BG0CNT-BG3CNT)
    public void writeBG0CNT8_0(int data) {
        graphicsJIT();
        bg0Renderer.writeBGCNT8_0(data);
    }

    public void writeBG0CNT8_1(int data) {
        graphicsJIT();
        bg0Renderer.writeBGCNT8_1(data);
    }

    public void writeBG0CNT16(int data) {
        graphicsJIT();
        bg0Renderer.writeBGCNT16(data);
    }

    public void writeBG1CNT8_0(int data) {
        graphicsJIT();
        bg1Renderer.writeBGCNT8_0(data);
    }

    public void writeBG1CNT8_1(int data) {
        graphicsJIT();
        bg1Renderer.writeBGCNT8_1(data);
    }

    public void writeBG1CNT16(int data) {
        graphicsJIT();
        bg1Renderer.writeBGCNT16(data);
    }

    public void writeBG0BG1CNT32(int data) {
        graphicsJIT();
        bg0Renderer.writeBGCNT16(data);
        bg1Renderer.writeBGCNT16(data >> 16);
    }

    public void writeBG2CNT8_0(int data) {
        graphicsJIT();
        bg2TextRenderer.writeBGCNT8_0(data);
        bgAffineRenderer0.setMosaicEnable(data & 0x40);
        bgAffineRenderer0.priorityPreprocess(data & 0x3);
        bg2MatrixRenderer.characterBaseBlockPreprocess((data & 0xC) >> 2);
    }

    public void writeBG2CNT8_1(int data) {
        graphicsJIT();
        bg2TextRenderer.writeBGCNT8_1(data);
        bg2MatrixRenderer.screenSizePreprocess((data & 0xC0) >> 6);
        bg2MatrixRenderer.screenBaseBlockPreprocess(data & 0x1F);
        bg2MatrixRenderer.displayOverflowPreprocess(data & 0x20);
    }

    public void writeBG2CNT16(int data) {
        graphicsJIT();
        bg2TextRenderer.writeBGCNT16(data);
        bgAffineRenderer0.setMosaicEnable(data & 0x40);
        bgAffineRenderer0.priorityPreprocess(data & 0x3);
        bg2MatrixRenderer.characterBaseBlockPreprocess((data & 0xC) >> 2);
        bg2MatrixRenderer.screenSizePreprocess((data & 0xC000) >> 14);
        int data8 = data >> 8;
        bg2MatrixRenderer.screenBaseBlockPreprocess(data8 & 0x1F);
        bg2MatrixRenderer.displayOverflowPreprocess(data8 & 0x20);
    }

    public void writeBG3CNT8_0(int data) {
        graphicsJIT();
        bg3TextRenderer.writeBGCNT8_0(data);
        bgAffineRenderer1.setMosaicEnable(data & 0x40);
        bgAffineRenderer1.priorityPreprocess(data & 0x3);
        bg3MatrixRenderer.characterBaseBlockPreprocess((data & 0xC) >> 2);
    }

    public void writeBG3CNT8_1(int data) {
        graphicsJIT();
        bg3TextRenderer.writeBGCNT8_1(data);
        bg3MatrixRenderer.screenSizePreprocess((data & 0xC0) >> 6);
        bg3MatrixRenderer.screenBaseBlockPreprocess(data & 0x1F);
        bg3MatrixRenderer.displayOverflowPreprocess(data & 0x20);
    }

    public void writeBG3CNT16(int data) {
        graphicsJIT();
        bg3TextRenderer.writeBGCNT16(data);
        bgAffineRenderer1.setMosaicEnable(data & 0x40);
        bgAffineRenderer1.priorityPreprocess(data & 0x3);
        bg3MatrixRenderer.characterBaseBlockPreprocess((data & 0xC) >> 2);
        bg3MatrixRenderer.screenSizePreprocess((data & 0xC000) >> 14);
        int data8 = data >> 8;
        bg3MatrixRenderer.screenBaseBlockPreprocess(data8 & 0x1F);
        bg3MatrixRenderer.displayOverflowPreprocess(data8 & 0x20);
    }

    public void writeBG2BG3CNT32(int data) {
        graphicsJIT();
        bg2TextRenderer.writeBGCNT16(data);
        bgAffineRenderer0.setMosaicEnable(data & 0x40);
        bgAffineRenderer0.priorityPreprocess(data & 0x3);
        bg2MatrixRenderer.characterBaseBlockPreprocess((data & 0xC) >> 2);
        bg2MatrixRenderer.screenSizePreprocess((data & 0xC000) >> 14);
        bg2MatrixRenderer.screenBaseBlockPreprocess((data >> 8) & 0x1F);
        bg2MatrixRenderer.displayOverflowPreprocess((data >> 8) & 0x20);

        data = data >> 16;
        bg3TextRenderer.writeBGCNT16(data);
        bgAffineRenderer1.setMosaicEnable(data & 0x40);
        bgAffineRenderer1.priorityPreprocess(data & 0x3);
        bg3MatrixRenderer.characterBaseBlockPreprocess((data & 0xC) >> 2);
        bg3MatrixRenderer.screenSizePreprocess((data & 0xC000) >> 14);
        int data8 = data >> 8;
        bg3MatrixRenderer.screenBaseBlockPreprocess(data8 & 0x1F);
        bg3MatrixRenderer.displayOverflowPreprocess(data8 & 0x20);
    }

    // Background offset writes (BG0HOFS-BG3VOFS) - delegated to renderers
    public void writeBG0HOFS8_0(int data) { graphicsJIT(); bg0Renderer.writeBGHOFS8_0(data); }
    public void writeBG0HOFS8_1(int data) { graphicsJIT(); bg0Renderer.writeBGHOFS8_1(data); }
    public void writeBG0HOFS16(int data) { graphicsJIT(); bg0Renderer.writeBGHOFS16(data); }
    public void writeBG0VOFS8_0(int data) { graphicsJIT(); bg0Renderer.writeBGVOFS8_0(data); }
    public void writeBG0VOFS8_1(int data) { graphicsJIT(); bg0Renderer.writeBGVOFS8_1(data); }
    public void writeBG0VOFS16(int data) { graphicsJIT(); bg0Renderer.writeBGVOFS16(data); }
    public void writeBG0OFS32(int data) { graphicsJIT(); bg0Renderer.writeBGOFS32(data); }

    public void writeBG1HOFS8_0(int data) { graphicsJIT(); bg1Renderer.writeBGHOFS8_0(data); }
    public void writeBG1HOFS8_1(int data) { graphicsJIT(); bg1Renderer.writeBGHOFS8_1(data); }
    public void writeBG1HOFS16(int data) { graphicsJIT(); bg1Renderer.writeBGHOFS16(data); }
    public void writeBG1VOFS8_0(int data) { graphicsJIT(); bg1Renderer.writeBGVOFS8_0(data); }
    public void writeBG1VOFS8_1(int data) { graphicsJIT(); bg1Renderer.writeBGVOFS8_1(data); }
    public void writeBG1VOFS16(int data) { graphicsJIT(); bg1Renderer.writeBGVOFS16(data); }
    public void writeBG1OFS32(int data) { graphicsJIT(); bg1Renderer.writeBGOFS32(data); }

    public void writeBG2HOFS8_0(int data) { graphicsJIT(); bg2TextRenderer.writeBGHOFS8_0(data); }
    public void writeBG2HOFS8_1(int data) { graphicsJIT(); bg2TextRenderer.writeBGHOFS8_1(data); }
    public void writeBG2HOFS16(int data) { graphicsJIT(); bg2TextRenderer.writeBGHOFS16(data); }
    public void writeBG2VOFS8_0(int data) { graphicsJIT(); bg2TextRenderer.writeBGVOFS8_0(data); }
    public void writeBG2VOFS8_1(int data) { graphicsJIT(); bg2TextRenderer.writeBGVOFS8_1(data); }
    public void writeBG2VOFS16(int data) { graphicsJIT(); bg2TextRenderer.writeBGVOFS16(data); }
    public void writeBG2OFS32(int data) { graphicsJIT(); bg2TextRenderer.writeBGOFS32(data); }

    public void writeBG3HOFS8_0(int data) { graphicsJIT(); bg3TextRenderer.writeBGHOFS8_0(data); }
    public void writeBG3HOFS8_1(int data) { graphicsJIT(); bg3TextRenderer.writeBGHOFS8_1(data); }
    public void writeBG3HOFS16(int data) { graphicsJIT(); bg3TextRenderer.writeBGHOFS16(data); }
    public void writeBG3VOFS8_0(int data) { graphicsJIT(); bg3TextRenderer.writeBGVOFS8_0(data); }
    public void writeBG3VOFS8_1(int data) { graphicsJIT(); bg3TextRenderer.writeBGVOFS8_1(data); }
    public void writeBG3VOFS16(int data) { graphicsJIT(); bg3TextRenderer.writeBGVOFS16(data); }
    public void writeBG3OFS32(int data) { graphicsJIT(); bg3TextRenderer.writeBGOFS32(data); }

    // BG2/BG3 affine transformation parameters (PA, PB, PC, PD)
    public void writeBG2PA8_0(int data) { graphicsJIT(); bgAffineRenderer0.writeBGPA8_0(data); }
    public void writeBG2PA8_1(int data) { graphicsJIT(); bgAffineRenderer0.writeBGPA8_1(data); }
    public void writeBG2PA16(int data) { graphicsJIT(); bgAffineRenderer0.writeBGPA16(data); }
    public void writeBG2PB8_0(int data) { graphicsJIT(); bgAffineRenderer0.writeBGPB8_0(data); }
    public void writeBG2PB8_1(int data) { graphicsJIT(); bgAffineRenderer0.writeBGPB8_1(data); }
    public void writeBG2PB16(int data) { graphicsJIT(); bgAffineRenderer0.writeBGPB16(data); }
    public void writeBG2PAB32(int data) { graphicsJIT(); bgAffineRenderer0.writeBGPAB32(data); }
    public void writeBG2PC8_0(int data) { graphicsJIT(); bgAffineRenderer0.writeBGPC8_0(data); }
    public void writeBG2PC8_1(int data) { graphicsJIT(); bgAffineRenderer0.writeBGPC8_1(data); }
    public void writeBG2PC16(int data) { graphicsJIT(); bgAffineRenderer0.writeBGPC16(data); }
    public void writeBG2PD8_0(int data) { graphicsJIT(); bgAffineRenderer0.writeBGPD8_0(data); }
    public void writeBG2PD8_1(int data) { graphicsJIT(); bgAffineRenderer0.writeBGPD8_1(data); }
    public void writeBG2PD16(int data) { graphicsJIT(); bgAffineRenderer0.writeBGPD16(data); }
    public void writeBG2PCD32(int data) { graphicsJIT(); bgAffineRenderer0.writeBGPCD32(data); }

    public void writeBG3PA8_0(int data) { graphicsJIT(); bgAffineRenderer1.writeBGPA8_0(data); }
    public void writeBG3PA8_1(int data) { graphicsJIT(); bgAffineRenderer1.writeBGPA8_1(data); }
    public void writeBG3PA16(int data) { graphicsJIT(); bgAffineRenderer1.writeBGPA16(data); }
    public void writeBG3PB8_0(int data) { graphicsJIT(); bgAffineRenderer1.writeBGPB8_0(data); }
    public void writeBG3PB8_1(int data) { graphicsJIT(); bgAffineRenderer1.writeBGPB8_1(data); }
    public void writeBG3PB16(int data) { graphicsJIT(); bgAffineRenderer1.writeBGPB16(data); }
    public void writeBG3PAB32(int data) { graphicsJIT(); bgAffineRenderer1.writeBGPAB32(data); }
    public void writeBG3PC8_0(int data) { graphicsJIT(); bgAffineRenderer1.writeBGPC8_0(data); }
    public void writeBG3PC8_1(int data) { graphicsJIT(); bgAffineRenderer1.writeBGPC8_1(data); }
    public void writeBG3PC16(int data) { graphicsJIT(); bgAffineRenderer1.writeBGPC16(data); }
    public void writeBG3PD8_0(int data) { graphicsJIT(); bgAffineRenderer1.writeBGPD8_0(data); }
    public void writeBG3PD8_1(int data) { graphicsJIT(); bgAffineRenderer1.writeBGPD8_1(data); }
    public void writeBG3PD16(int data) { graphicsJIT(); bgAffineRenderer1.writeBGPD16(data); }
    public void writeBG3PCD32(int data) { graphicsJIT(); bgAffineRenderer1.writeBGPCD32(data); }

    // BG2/BG3 reference point (X, Y)
    public void writeBG2X8_0(int data) { graphicsJIT(); bgAffineRenderer0.writeBGX8_0(data); }
    public void writeBG2X8_1(int data) { graphicsJIT(); bgAffineRenderer0.writeBGX8_1(data); }
    public void writeBG2X8_2(int data) { graphicsJIT(); bgAffineRenderer0.writeBGX8_2(data); }
    public void writeBG2X8_3(int data) { graphicsJIT(); bgAffineRenderer0.writeBGX8_3(data); }
    public void writeBG2X16_0(int data) { graphicsJIT(); bgAffineRenderer0.writeBGX16_0(data); }
    public void writeBG2X16_1(int data) { graphicsJIT(); bgAffineRenderer0.writeBGX16_1(data); }
    public void writeBG2X32(int data) { graphicsJIT(); bgAffineRenderer0.writeBGX32(data); }
    public void writeBG2Y8_0(int data) { graphicsJIT(); bgAffineRenderer0.writeBGY8_0(data); }
    public void writeBG2Y8_1(int data) { graphicsJIT(); bgAffineRenderer0.writeBGY8_1(data); }
    public void writeBG2Y8_2(int data) { graphicsJIT(); bgAffineRenderer0.writeBGY8_2(data); }
    public void writeBG2Y8_3(int data) { graphicsJIT(); bgAffineRenderer0.writeBGY8_3(data); }
    public void writeBG2Y16_0(int data) { graphicsJIT(); bgAffineRenderer0.writeBGY16_0(data); }
    public void writeBG2Y16_1(int data) { graphicsJIT(); bgAffineRenderer0.writeBGY16_1(data); }
    public void writeBG2Y32(int data) { graphicsJIT(); bgAffineRenderer0.writeBGY32(data); }

    public void writeBG3X8_0(int data) { graphicsJIT(); bgAffineRenderer1.writeBGX8_0(data); }
    public void writeBG3X8_1(int data) { graphicsJIT(); bgAffineRenderer1.writeBGX8_1(data); }
    public void writeBG3X8_2(int data) { graphicsJIT(); bgAffineRenderer1.writeBGX8_2(data); }
    public void writeBG3X8_3(int data) { graphicsJIT(); bgAffineRenderer1.writeBGX8_3(data); }
    public void writeBG3X16_0(int data) { graphicsJIT(); bgAffineRenderer1.writeBGX16_0(data); }
    public void writeBG3X16_1(int data) { graphicsJIT(); bgAffineRenderer1.writeBGX16_1(data); }
    public void writeBG3X32(int data) { graphicsJIT(); bgAffineRenderer1.writeBGX32(data); }
    public void writeBG3Y8_0(int data) { graphicsJIT(); bgAffineRenderer1.writeBGY8_0(data); }
    public void writeBG3Y8_1(int data) { graphicsJIT(); bgAffineRenderer1.writeBGY8_1(data); }
    public void writeBG3Y8_2(int data) { graphicsJIT(); bgAffineRenderer1.writeBGY8_2(data); }
    public void writeBG3Y8_3(int data) { graphicsJIT(); bgAffineRenderer1.writeBGY8_3(data); }
    public void writeBG3Y16_0(int data) { graphicsJIT(); bgAffineRenderer1.writeBGY16_0(data); }
    public void writeBG3Y16_1(int data) { graphicsJIT(); bgAffineRenderer1.writeBGY16_1(data); }
    public void writeBG3Y32(int data) { graphicsJIT(); bgAffineRenderer1.writeBGY32(data); }

    // Window coordinate writes
    public void writeWIN0XCOORDRight8(int data) { graphicsJIT(); window0Renderer.writeWINXCOORDRight8(data); }
    public void writeWIN0XCOORDLeft8(int data) { graphicsJIT(); window0Renderer.writeWINXCOORDLeft8(data); }
    public void writeWIN0XCOORD16(int data) { graphicsJIT(); window0Renderer.writeWINXCOORD16(data); }
    public void writeWIN1XCOORDRight8(int data) { graphicsJIT(); window1Renderer.writeWINXCOORDRight8(data); }
    public void writeWIN1XCOORDLeft8(int data) { graphicsJIT(); window1Renderer.writeWINXCOORDLeft8(data); }
    public void writeWIN1XCOORD16(int data) { graphicsJIT(); window1Renderer.writeWINXCOORD16(data); }
    public void writeWINXCOORD32(int data) {
        graphicsJIT();
        window0Renderer.writeWINXCOORD16(data & 0xFFFF);
        window1Renderer.writeWINXCOORD16(data >>> 16);
    }

    public void writeWIN0YCOORDBottom8(int data) { graphicsJIT(); window0Renderer.writeWINYCOORDBottom8(data); }
    public void writeWIN0YCOORDTop8(int data) { graphicsJIT(); window0Renderer.writeWINYCOORDTop8(data); }
    public void writeWIN0YCOORD16(int data) { graphicsJIT(); window0Renderer.writeWINYCOORD16(data); }
    public void writeWIN1YCOORDBottom8(int data) { graphicsJIT(); window1Renderer.writeWINYCOORDBottom8(data); }
    public void writeWIN1YCOORDTop8(int data) { graphicsJIT(); window1Renderer.writeWINYCOORDTop8(data); }
    public void writeWIN1YCOORD16(int data) { graphicsJIT(); window1Renderer.writeWINYCOORD16(data); }
    public void writeWINYCOORD32(int data) {
        graphicsJIT();
        window0Renderer.writeWINYCOORD16(data & 0xFFFF);
        window1Renderer.writeWINYCOORD16(data >>> 16);
    }

    // Window control writes
    public void writeWIN0IN8(int data) { graphicsJIT(); window0Renderer.writeWININ8(data); }
    public void writeWIN1IN8(int data) { graphicsJIT(); window1Renderer.writeWININ8(data); }
    public void writeWININ16(int data) {
        graphicsJIT();
        window0Renderer.writeWININ8(data & 0xFF);
        window1Renderer.writeWININ8(data >> 8);
    }

    public void writeWINOUT8(int data) {
        graphicsJIT();
        WINOutside = data;
        compositorPreprocess();
    }

    public void writeWINOBJIN8(int data) { graphicsJIT(); objWindowRenderer.writeWINOBJIN8(data); }

    public void writeWINOUT16(int data) {
        graphicsJIT();
        WINOutside = data;
        compositorPreprocess();
        objWindowRenderer.writeWINOBJIN8(data >> 8);
    }

    public void writeWINCONTROL32(int data) {
        graphicsJIT();
        window0Renderer.writeWININ8(data & 0xFF);
        window1Renderer.writeWININ8((data >> 8) & 0xFF);
        WINOutside = data >> 16;
        compositorPreprocess();
        objWindowRenderer.writeWINOBJIN8(data >>> 24);
    }

    // Mosaic writes
    public void writeMOSAIC8_0(int data) { graphicsJIT(); mosaicRenderer.writeMOSAIC8_0(data); }
    public void writeMOSAIC8_1(int data) { graphicsJIT(); mosaicRenderer.writeMOSAIC8_1(data); }
    public void writeMOSAIC16(int data) { graphicsJIT(); mosaicRenderer.writeMOSAIC16(data); }

    // Color effects (blending) writes
    public void writeBLDCNT8_0(int data) { graphicsJIT(); colorEffectsRenderer.writeBLDCNT8_0(data); }
    public void writeBLDCNT8_1(int data) { graphicsJIT(); colorEffectsRenderer.writeBLDCNT8_1(data); }
    public void writeBLDCNT16(int data) { graphicsJIT(); colorEffectsRenderer.writeBLDCNT16(data); }
    public void writeBLDALPHA8_0(int data) { graphicsJIT(); colorEffectsRenderer.writeBLDALPHA8_0(data); }
    public void writeBLDALPHA8_1(int data) { graphicsJIT(); colorEffectsRenderer.writeBLDALPHA8_1(data); }
    public void writeBLDALPHA16(int data) { graphicsJIT(); colorEffectsRenderer.writeBLDALPHA16(data); }
    public void writeBLDCNT32(int data) { graphicsJIT(); colorEffectsRenderer.writeBLDCNT32(data); }
    public void writeBLDY8(int data) { graphicsJIT(); colorEffectsRenderer.writeBLDY8(data); }
}
