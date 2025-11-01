package com.iodine.gba.graphics;

import com.iodine.gba.core.GameBoyAdvanceIO;
import com.iodine.gba.memory.GameBoyAdvanceMemory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * GameBoyAdvanceRenderer - Graphics rendering engine
 * Supports Mode 3 (16-bit bitmap), Mode 4 (8-bit paletted), and sprite rendering
 */
public class GameBoyAdvanceRenderer {
    public GameBoyAdvanceIO IOCore;
    public GameBoyAdvanceMemory memory;

    // Frame buffer (240x160 in RGB888 format for display)
    public int[] frameBuffer = new int[240 * 160];

    // Display control registers
    public int displayControl;  // DISPCNT - 0x04000000
    public int displayStatus;   // DISPSTAT - 0x04000004

    // Background control
    public int[] bgControl = new int[4];      // BG0CNT-BG3CNT
    public int[] bgHOffset = new int[4];      // BG horizontal offsets
    public int[] bgVOffset = new int[4];      // BG vertical offsets

    // Sprite/OBJ control
    public int[] objAttributes = new int[128 * 8];  // 128 sprites, 8 bytes each

    // Backdrop color
    public int backdrop = 0x000000;

    public GameBoyAdvanceRenderer(GameBoyAdvanceIO ioCore) {
        this.IOCore = ioCore;
    }

    public void initialize() {
        memory = IOCore.memory;
        displayControl = 0x80;  // Forced blank initially
        displayStatus = 0;

        // Clear frame buffer to backdrop color
        for (int i = 0; i < frameBuffer.length; i++) {
            frameBuffer[i] = backdrop;
        }
    }

    public int[] getFrameBuffer() {
        return frameBuffer;
    }

    /**
     * Render a single scanline based on current display mode
     */
    public void renderScanLine(int line) {
        if ((displayControl & 0x80) != 0) {
            // Forced blank - render white
            renderForcedBlank(line);
            return;
        }

        int mode = displayControl & 0x7;

        switch (mode) {
            case 0:
            case 1:
            case 2:
                // Tile-based modes - render backdrop for now
                renderBackdrop(line);
                renderSprites(line);
                break;
            case 3:
                // Mode 3: 240x160 16-bit bitmap
                renderMode3(line);
                renderSprites(line);
                break;
            case 4:
                // Mode 4: 240x160 8-bit paletted bitmap
                renderMode4(line);
                renderSprites(line);
                break;
            case 5:
                // Mode 5: 160x128 16-bit bitmap
                renderMode5(line);
                renderSprites(line);
                break;
            default:
                renderBackdrop(line);
        }
    }

    /**
     * Render forced blank (white screen)
     */
    private void renderForcedBlank(int line) {
        int offset = line * 240;
        for (int x = 0; x < 240; x++) {
            frameBuffer[offset + x] = 0xFFFFFF;
        }
    }

    /**
     * Render backdrop color
     */
    private void renderBackdrop(int line) {
        // Read backdrop color from palette RAM (first entry)
        int backdropColor = memory.paletteRAM16.get(0) & 0xFFFF;
        int rgb = convert15bitTo24bit(backdropColor);

        int offset = line * 240;
        for (int x = 0; x < 240; x++) {
            frameBuffer[offset + x] = rgb;
        }
    }

    /**
     * Mode 3: 240x160 16-bit direct color bitmap
     * VRAM is used as a direct framebuffer
     */
    private void renderMode3(int line) {
        int offset = line * 240;
        int vramOffset = line * 240;

        for (int x = 0; x < 240; x++) {
            // Read 16-bit color from VRAM
            int color15 = memory.VRAM16.get(vramOffset + x) & 0xFFFF;
            // Convert 15-bit BGR to 24-bit RGB
            frameBuffer[offset + x] = convert15bitTo24bit(color15);
        }
    }

    /**
     * Mode 4: 240x160 8-bit paletted bitmap
     * VRAM contains palette indices, colors from palette RAM
     */
    private void renderMode4(int line) {
        int offset = line * 240;
        int vramOffset = line * 240;

        // Check which frame buffer is active (bit 4 of DISPCNT)
        int frameSelect = (displayControl & 0x10) != 0 ? 0xA000 : 0;

        for (int x = 0; x < 240; x++) {
            // Read palette index from VRAM
            int paletteIndex = memory.VRAM[vramOffset + x + frameSelect] & 0xFF;
            // Read color from palette RAM
            int color15 = memory.paletteRAM16.get(paletteIndex) & 0xFFFF;
            // Convert to 24-bit RGB
            frameBuffer[offset + x] = convert15bitTo24bit(color15);
        }
    }

    /**
     * Mode 5: 160x128 16-bit bitmap (smaller, scalable)
     */
    private void renderMode5(int line) {
        if (line >= 128) {
            renderBackdrop(line);
            return;
        }

        int offset = line * 240;
        int vramOffset = line * 160;

        // Check which frame buffer is active
        int frameSelect = (displayControl & 0x10) != 0 ? 0xA000 : 0;

        // Render center 160 pixels, leaving 40 pixels border on each side
        for (int x = 0; x < 40; x++) {
            frameBuffer[offset + x] = backdrop;
        }

        for (int x = 0; x < 160; x++) {
            int color15 = memory.VRAM16.get(vramOffset + x + (frameSelect / 2)) & 0xFFFF;
            frameBuffer[offset + 40 + x] = convert15bitTo24bit(color15);
        }

        for (int x = 200; x < 240; x++) {
            frameBuffer[offset + x] = backdrop;
        }
    }

    /**
     * Render sprites (OBJ layer) - Basic implementation
     */
    private void renderSprites(int line) {
        if ((displayControl & 0x1000) == 0) {
            return;  // OBJ layer disabled
        }

        // Read OAM (Object Attribute Memory)
        // Each sprite has 8 bytes of attributes
        for (int sprite = 0; sprite < 128; sprite++) {
            int oamOffset = sprite * 4;  // 4 x 16-bit values per sprite

            // Attribute 0: Y position, shape, mode
            int attr0 = memory.OAM16.get(oamOffset) & 0xFFFF;
            int yPos = attr0 & 0xFF;
            int objMode = (attr0 >>> 8) & 0x3;

            // Skip if sprite is disabled
            if (objMode == 2) continue;  // Disabled/hidden

            // Attribute 1: X position, size
            int attr1 = memory.OAM16.get(oamOffset + 1) & 0xFFFF;
            int xPos = attr1 & 0x1FF;

            // Attribute 2: Tile index, palette, priority
            int attr2 = memory.OAM16.get(oamOffset + 2) & 0xFFFF;
            int tileIndex = attr2 & 0x3FF;
            boolean use256Color = (attr0 & 0x2000) != 0;

            // Get sprite size
            int shape = (attr0 >>> 14) & 0x3;
            int size = (attr1 >>> 14) & 0x3;
            int[] dimensions = getSpriteDimensions(shape, size);
            int width = dimensions[0];
            int height = dimensions[1];

            // Check if sprite intersects current scanline
            if (line >= yPos && line < yPos + height) {
                // Simplified sprite rendering - just mark presence
                // Full implementation would render tile data
            }
        }
    }

    /**
     * Get sprite dimensions based on shape and size
     */
    private int[] getSpriteDimensions(int shape, int size) {
        // Size: 0=small, 1=medium, 2=large, 3=huge
        // Shape: 0=square, 1=horizontal, 2=vertical

        // Square sprites
        if (shape == 0) {
            switch (size) {
                case 0: return new int[]{8, 8};
                case 1: return new int[]{16, 16};
                case 2: return new int[]{32, 32};
                case 3: return new int[]{64, 64};
            }
        }
        // Horizontal sprites
        else if (shape == 1) {
            switch (size) {
                case 0: return new int[]{16, 8};
                case 1: return new int[]{32, 8};
                case 2: return new int[]{32, 16};
                case 3: return new int[]{64, 32};
            }
        }
        // Vertical sprites
        else if (shape == 2) {
            switch (size) {
                case 0: return new int[]{8, 16};
                case 1: return new int[]{8, 32};
                case 2: return new int[]{16, 32};
                case 3: return new int[]{32, 64};
            }
        }

        return new int[]{8, 8};
    }

    /**
     * Convert 15-bit BGR555 to 24-bit RGB888
     */
    private int convert15bitTo24bit(int color15) {
        int r = (color15 & 0x1F) << 3;        // Red (bits 0-4)
        int g = ((color15 >>> 5) & 0x1F) << 3;  // Green (bits 5-9)
        int b = ((color15 >>> 10) & 0x1F) << 3; // Blue (bits 10-14)

        // Expand 5-bit to 8-bit by replicating top bits
        r |= (r >>> 5);
        g |= (g >>> 5);
        b |= (b >>> 5);

        return (r << 16) | (g << 8) | b;
    }

    /**
     * Prepare frame for output (called on VBlank)
     */
    public void prepareFrame() {
        // Frame is already in RGB888 format, ready for display
        // In original, this would swizzle the buffer
    }

    // Display control register (DISPCNT) access
    public void writeDISPCNT(int value) {
        displayControl = value & 0xFFFF;
    }

    public int readDISPCNT() {
        return displayControl;
    }
}
