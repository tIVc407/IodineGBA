package com.iodine.gba.graphics;

import java.util.Arrays;

/**
 * GameBoyAdvanceOBJRenderer - Sprite/object renderer (converted from OBJ.js)
 * Copyright (C) 2012-2015 Grant Galitz
 *
 * Handles sprite rendering for the GBA:
 * - 128 sprites (OBJ) per frame
 * - Normal and affine (rotation/scaling) sprites
 * - 256-color and 16-color palette modes
 * - Horizontal and vertical flipping
 * - Mosaic effects
 * - Object windows
 * - Cycle-accurate rendering limits
 */
public class GameBoyAdvanceOBJRenderer {
    public GameBoyAdvanceRenderer gfx;

    // OAM (Object Attribute Memory)
    public byte[] OAMRAM;
    public java.nio.ShortBuffer OAMRAM16;
    public java.nio.IntBuffer OAMRAM32;

    // OAM attribute table (128 sprites)
    public OAMEntry[] OAMTable;

    // Matrix transformation parameters (for affine sprites)
    public int[] OBJMatrixParameters;

    // Scratch buffers
    public int[] buffer;
    public int[] scratchBuffer;       // OBJ sprite line buffer (240 pixels)
    public int[] scratchWindowBuffer; // OBJ window buffer (240 pixels)
    public int[] scratchOBJBuffer;    // Temp buffer for current sprite

    // Palette references
    public int[] paletteOBJ256;
    public int[] paletteOBJ16;

    // VRAM references
    public byte[] VRAM;
    public java.nio.IntBuffer VRAM32;

    // Rendering state
    public int cyclesToRender;
    public int offset;  // Scratch buffer offset (0x500)

    // Size lookup tables
    public static final int[] lookupXSize = {
        // Square:
        8,  16, 32, 64,
        // Vertical Rectangle:
        16, 32, 32, 64,
        // Horizontal Rectangle:
        8,   8, 16, 32
    };

    public static final int[] lookupYSize = {
        // Square:
        8,  16, 32, 64,
        // Vertical Rectangle:
        8,   8, 16, 32,
        // Horizontal Rectangle:
        16, 32, 32, 64
    };

    // Reference to mosaic renderer
    public GameBoyAdvanceMosaicRenderer mosaicRenderer;

    /**
     * OAM Entry - Sprite attributes
     */
    public static class OAMEntry {
        public int ycoord;
        public int matrix2D;
        public int doubleSizeOrDisabled;
        public int mode;
        public int mosaic;
        public int monolithicPalette;
        public int shape;
        public int xcoord;
        public int matrixParameters;
        public int horizontalFlip;
        public int verticalFlip;
        public int size;
        public int tileNumber;
        public int priority;
        public int paletteNumber;
    }

    public GameBoyAdvanceOBJRenderer(GameBoyAdvanceRenderer gfx) {
        this.gfx = gfx;
    }

    public void initialize() {
        // Get references from parent renderer
        paletteOBJ256 = gfx.paletteOBJ256;
        paletteOBJ16 = gfx.paletteOBJ16;
        VRAM = gfx.VRAM;
        VRAM32 = gfx.VRAM32;
        buffer = gfx.buffer;
        mosaicRenderer = gfx.mosaicRenderer;

        // Initialize OAM RAM
        OAMRAM = new byte[0x400];
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(OAMRAM).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        OAMRAM16 = bb.asShortBuffer();
        OAMRAM32 = bb.asIntBuffer();

        // Initialize scratch buffers
        offset = 0x500;
        scratchBuffer = buffer;  // Uses main buffer at offset 0x500-0x5F0
        scratchWindowBuffer = new int[240];
        scratchOBJBuffer = new int[128];

        // Initialize OAM table (128 sprites)
        OAMTable = new OAMEntry[128];
        for (int i = 0; i < 128; i++) {
            OAMTable[i] = new OAMEntry();
        }

        // Initialize matrix parameters (for affine sprites)
        OBJMatrixParameters = new int[0x80];

        // Default cycle limit
        cyclesToRender = 1210;
    }

    /**
     * Render all sprites for the current scanline.
     */
    public void renderScanLine(int line) {
        performRenderLoop(line);
    }

    /**
     * Render loop - iterate through all sprites and draw visible ones.
     */
    public void performRenderLoop(int line) {
        // Clear scratch buffers
        clearScratch();

        // Render all sprites (128 max)
        int cycles = cyclesToRender;
        for (int objNumber = 0; objNumber < 0x80; objNumber++) {
            cycles = renderSprite(line, OAMTable[objNumber], cycles);
        }
    }

    /**
     * Clear the scratch buffers to transparency.
     */
    public void clearScratch() {
        // Clear sprite buffer
        for (int position = 0; position < 240; position++) {
            scratchBuffer[position + offset] = 0x3800000;
            scratchWindowBuffer[position] = 0x3800000;
        }
    }

    /**
     * Render a single sprite if it's visible on this scanline.
     */
    public int renderSprite(int line, OAMEntry sprite, int cycles) {
        if (isDrawable(sprite)) {
            if (sprite.mosaic != 0) {
                // Correct line number for mosaic
                line = line - mosaicRenderer.getOBJMosaicYOffset(line);
            }

            // Obtain horizontal size info
            int xSize = lookupXSize[(sprite.shape << 2) | sprite.size] << sprite.doubleSizeOrDisabled;

            // Obtain vertical size info
            int ySize = lookupYSize[(sprite.shape << 2) | sprite.size] << sprite.doubleSizeOrDisabled;

            // Obtain some offsets
            int ycoord = sprite.ycoord;
            int yOffset = line - ycoord;

            // Overflow Correction:
            // HW re-offsets any "negative" y-coord values to on-screen unsigned.
            // Also a bug triggers this on 8-bit ending coordinate overflow from large sprites.
            if (yOffset < 0 || (ycoord + ySize) > 0x100) {
                yOffset = yOffset + 0x100;
            }

            // Make a sprite line
            int ySize_mask = ySize - 1;
            if ((yOffset & ySize_mask) == yOffset) {
                // Compute clocks required to draw the sprite
                cycles = computeCycles(cycles, sprite.matrix2D, xSize);

                // If there's enough cycles, render
                if (cycles >= 0) {
                    switch (sprite.mode) {
                        case 0:
                            // Normal/Semi-transparent Sprite
                            renderRegularSprite(sprite, xSize, ySize, yOffset);
                            break;
                        case 1:
                            // Semi-transparent Sprite
                            renderSemiTransparentSprite(sprite, xSize, ySize, yOffset);
                            break;
                        case 2:
                            // OBJ-WIN Sprite
                            renderOBJWINSprite(sprite, xSize, ySize, yOffset);
                            break;
                    }
                }
            }
        }
        return cycles;
    }

    /**
     * Compute rendering cycles for the sprite.
     */
    public int computeCycles(int cycles, int matrix2D, int cyclesToSubtract) {
        if (matrix2D != 0) {
            // Scale & Rotation: sprites take more cycles
            cyclesToSubtract = (cyclesToSubtract << 1) + 10;
            cycles -= cyclesToSubtract;
        } else {
            // Regular Scrolling
            cycles -= cyclesToSubtract;
        }
        return cycles;
    }

    /**
     * Render a regular sprite.
     */
    public void renderRegularSprite(OAMEntry sprite, int xSize, int ySize, int yOffset) {
        if (sprite.matrix2D != 0) {
            // Scale & Rotation
            renderMatrixSprite(sprite, xSize, ySize + 1, yOffset);
        } else {
            // Regular Scrolling
            renderNormalSprite(sprite, xSize, ySize, yOffset);
        }
        // Copy OBJ scratch buffer to scratch line buffer
        outputSpriteToScratch(sprite, xSize);
    }

    /**
     * Render a semi-transparent sprite.
     */
    public void renderSemiTransparentSprite(OAMEntry sprite, int xSize, int ySize, int yOffset) {
        if (sprite.matrix2D != 0) {
            // Scale & Rotation
            renderMatrixSprite(sprite, xSize, ySize + 1, yOffset);
        } else {
            // Regular Scrolling
            renderNormalSprite(sprite, xSize, ySize, yOffset);
        }
        // Copy OBJ scratch buffer to scratch line buffer (semi-transparent)
        outputSemiTransparentSpriteToScratch(sprite, xSize);
    }

    /**
     * Render an OBJ-WIN sprite (object window).
     */
    public void renderOBJWINSprite(OAMEntry sprite, int xSize, int ySize, int yOffset) {
        if (sprite.matrix2D != 0) {
            // Scale & Rotation
            renderMatrixSpriteOBJWIN(sprite, xSize, ySize + 1, yOffset);
        } else {
            // Regular Scrolling
            renderNormalSpriteOBJWIN(sprite, xSize, ySize, yOffset);
        }
        // Copy OBJ scratch buffer to scratch obj-window line buffer
        outputSpriteToOBJWINScratch(sprite, xSize);
    }

    /**
     * Render a matrix (affine) sprite with rotation/scaling.
     */
    public void renderMatrixSprite(OAMEntry sprite, int xSize, int ySize, int yOffset) {
        int xDiff = -(xSize >> 1);
        int yDiff = yOffset - (ySize >> 1);
        int xSizeOriginal = xSize >> sprite.doubleSizeOrDisabled;
        int xSizeFixed = xSizeOriginal << 8;
        int ySizeOriginal = ySize >> sprite.doubleSizeOrDisabled;
        int ySizeFixed = ySizeOriginal << 8;

        // Get transformation matrix parameters
        int dx = OBJMatrixParameters[sprite.matrixParameters];
        int dmx = OBJMatrixParameters[sprite.matrixParameters + 1];
        int dy = OBJMatrixParameters[sprite.matrixParameters + 2];
        int dmy = OBJMatrixParameters[sprite.matrixParameters + 3];

        // Compute starting position
        int pa = dx * xDiff;
        int pb = dmx * yDiff;
        int pc = dy * xDiff;
        int pd = dmy * yDiff;

        int x = pa + pb + (xSizeFixed >> 1);
        int y = pc + pd + (ySizeFixed >> 1);

        // Render each pixel with affine transformation
        for (int position = 0; position < xSize; position++, x += dx, y += dy) {
            if (x >= 0 && y >= 0 && x < xSizeFixed && y < ySizeFixed) {
                // Coordinates in range, fetch pixel
                scratchOBJBuffer[position] = fetchMatrixPixel(sprite, x >> 8, y >> 8, xSizeOriginal);
            } else {
                // Coordinates outside of range, transparency defaulted
                scratchOBJBuffer[position] = 0x3800000;
            }
        }
    }

    /**
     * Render a matrix (affine) sprite for OBJ window.
     */
    public void renderMatrixSpriteOBJWIN(OAMEntry sprite, int xSize, int ySize, int yOffset) {
        int xDiff = -(xSize >> 1);
        int yDiff = yOffset - (ySize >> 1);
        int xSizeOriginal = xSize >> sprite.doubleSizeOrDisabled;
        int xSizeFixed = xSizeOriginal << 8;
        int ySizeOriginal = ySize >> sprite.doubleSizeOrDisabled;
        int ySizeFixed = ySizeOriginal << 8;

        // Get transformation matrix parameters
        int dx = OBJMatrixParameters[sprite.matrixParameters];
        int dmx = OBJMatrixParameters[sprite.matrixParameters + 1];
        int dy = OBJMatrixParameters[sprite.matrixParameters + 2];
        int dmy = OBJMatrixParameters[sprite.matrixParameters + 3];

        // Compute starting position
        int pa = dx * xDiff;
        int pb = dmx * yDiff;
        int pc = dy * xDiff;
        int pd = dmy * yDiff;

        int x = pa + pb + (xSizeFixed >> 1);
        int y = pc + pd + (ySizeFixed >> 1);

        // Render each pixel with affine transformation
        for (int position = 0; position < xSize; position++, x += dx, y += dy) {
            if (x >= 0 && y >= 0 && x < xSizeFixed && y < ySizeFixed) {
                // Coordinates in range, fetch pixel
                scratchOBJBuffer[position] = fetchMatrixPixelOBJWIN(sprite, x >> 8, y >> 8, xSizeOriginal);
            } else {
                // Coordinates outside of range, transparency defaulted
                scratchOBJBuffer[position] = 0;
            }
        }
    }

    /**
     * Fetch a pixel for matrix sprite.
     */
    public int fetchMatrixPixel(OAMEntry sprite, int x, int y, int xSize) {
        if (sprite.monolithicPalette != 0) {
            // 256 Colors / 1 Palette
            int address = tileNumberToAddress256(sprite.tileNumber, xSize, y);
            address += tileRelativeAddressOffset(x, y);
            return paletteOBJ256[VRAM[address] & 0xFF];
        } else {
            // 16 Colors / 16 palettes
            int address = tileNumberToAddress16(sprite.tileNumber, xSize, y);
            address += tileRelativeAddressOffset(x, y) >> 1;
            if ((x & 0x1) == 0) {
                return paletteOBJ16[sprite.paletteNumber | (VRAM[address] & 0xF)];
            } else {
                return paletteOBJ16[sprite.paletteNumber | ((VRAM[address] & 0xFF) >> 4)];
            }
        }
    }

    /**
     * Fetch a pixel for matrix sprite (OBJWIN mode).
     */
    public int fetchMatrixPixelOBJWIN(OAMEntry sprite, int x, int y, int xSize) {
        if (sprite.monolithicPalette != 0) {
            // 256 Colors / 1 Palette
            int address = tileNumberToAddress256(sprite.tileNumber, xSize, y);
            address += tileRelativeAddressOffset(x, y);
            return VRAM[address] & 0xFF;
        } else {
            // 16 Colors / 16 palettes
            int address = tileNumberToAddress16(sprite.tileNumber, xSize, y);
            address += tileRelativeAddressOffset(x, y) >> 1;
            if ((x & 0x1) == 0) {
                return VRAM[address] & 0xF;
            } else {
                return (VRAM[address] & 0xFF) >> 4;
            }
        }
    }

    /**
     * Compute tile-relative address offset.
     */
    public int tileRelativeAddressOffset(int x, int y) {
        return (((y & 7) + (x & ~7)) << 3) + (x & 0x7);
    }

    /**
     * Render a normal (non-affine) sprite.
     */
    public void renderNormalSprite(OAMEntry sprite, int xSize, int ySize, int yOffset) {
        if (sprite.verticalFlip != 0) {
            // Flip y-coordinate offset
            yOffset = ySize - yOffset;
        }

        if (sprite.monolithicPalette != 0) {
            // 256 Colors / 1 Palette
            int address = tileNumberToAddress256(sprite.tileNumber, xSize, yOffset);
            address += (yOffset & 7) << 3;
            render256ColorPaletteSprite(address, xSize);
        } else {
            // 16 Colors / 16 palettes
            int address = tileNumberToAddress16(sprite.tileNumber, xSize, yOffset);
            address += (yOffset & 7) << 2;
            render16ColorPaletteSprite(address, xSize, sprite.paletteNumber);
        }
    }

    /**
     * Render a normal (non-affine) sprite for OBJ window.
     */
    public void renderNormalSpriteOBJWIN(OAMEntry sprite, int xSize, int ySize, int yOffset) {
        if (sprite.verticalFlip != 0) {
            // Flip y-coordinate offset
            yOffset = ySize - yOffset;
        }

        if (sprite.monolithicPalette != 0) {
            // 256 Colors / 1 Palette
            int address = tileNumberToAddress256(sprite.tileNumber, xSize, yOffset);
            address += (yOffset & 7) << 3;
            render256ColorPaletteSpriteOBJWIN(address, xSize);
        } else {
            // 16 Colors / 16 palettes
            int address = tileNumberToAddress16(sprite.tileNumber, xSize, yOffset);
            address += (yOffset & 7) << 2;
            render16ColorPaletteSpriteOBJWIN(address, xSize);
        }
    }

    /**
     * Render a 256-color palette sprite line.
     */
    public void render256ColorPaletteSprite(int address, int xSize) {
        address = address >> 2;
        for (int objBufferPos = 0; objBufferPos < xSize; objBufferPos += 8) {
            int data = VRAM32.get(address);
            scratchOBJBuffer[objBufferPos] = paletteOBJ256[data & 0xFF];
            scratchOBJBuffer[objBufferPos + 1] = paletteOBJ256[(data >> 8) & 0xFF];
            scratchOBJBuffer[objBufferPos + 2] = paletteOBJ256[(data >> 16) & 0xFF];
            scratchOBJBuffer[objBufferPos + 3] = paletteOBJ256[data >>> 24];

            data = VRAM32.get(address + 1);
            scratchOBJBuffer[objBufferPos + 4] = paletteOBJ256[data & 0xFF];
            scratchOBJBuffer[objBufferPos + 5] = paletteOBJ256[(data >> 8) & 0xFF];
            scratchOBJBuffer[objBufferPos + 6] = paletteOBJ256[(data >> 16) & 0xFF];
            scratchOBJBuffer[objBufferPos + 7] = paletteOBJ256[data >>> 24];

            address += 0x10;
        }
    }

    /**
     * Render a 256-color palette sprite line for OBJ window.
     */
    public void render256ColorPaletteSpriteOBJWIN(int address, int xSize) {
        address = address >> 2;
        for (int objBufferPos = 0; objBufferPos < xSize; objBufferPos += 8) {
            int data = VRAM32.get(address);
            scratchOBJBuffer[objBufferPos] = data & 0xFF;
            scratchOBJBuffer[objBufferPos + 1] = (data >> 8) & 0xFF;
            scratchOBJBuffer[objBufferPos + 2] = (data >> 16) & 0xFF;
            scratchOBJBuffer[objBufferPos + 3] = data >>> 24;

            data = VRAM32.get(address + 1);
            scratchOBJBuffer[objBufferPos + 4] = data & 0xFF;
            scratchOBJBuffer[objBufferPos + 5] = (data >> 8) & 0xFF;
            scratchOBJBuffer[objBufferPos + 6] = (data >> 16) & 0xFF;
            scratchOBJBuffer[objBufferPos + 7] = data >>> 24;

            address += 0x10;
        }
    }

    /**
     * Render a 16-color palette sprite line.
     */
    public void render16ColorPaletteSprite(int address, int xSize, int paletteOffset) {
        address = address >> 2;
        for (int objBufferPos = 0; objBufferPos < xSize; objBufferPos += 8) {
            int data = VRAM32.get(address);
            scratchOBJBuffer[objBufferPos] = paletteOBJ16[paletteOffset | (data & 0xF)];
            scratchOBJBuffer[objBufferPos + 1] = paletteOBJ16[paletteOffset | ((data >> 4) & 0xF)];
            scratchOBJBuffer[objBufferPos + 2] = paletteOBJ16[paletteOffset | ((data >> 8) & 0xF)];
            scratchOBJBuffer[objBufferPos + 3] = paletteOBJ16[paletteOffset | ((data >> 12) & 0xF)];
            scratchOBJBuffer[objBufferPos + 4] = paletteOBJ16[paletteOffset | ((data >> 16) & 0xF)];
            scratchOBJBuffer[objBufferPos + 5] = paletteOBJ16[paletteOffset | ((data >> 20) & 0xF)];
            scratchOBJBuffer[objBufferPos + 6] = paletteOBJ16[paletteOffset | ((data >> 24) & 0xF)];
            scratchOBJBuffer[objBufferPos + 7] = paletteOBJ16[paletteOffset | (data >>> 28)];

            address += 0x8;
        }
    }

    /**
     * Render a 16-color palette sprite line for OBJ window.
     */
    public void render16ColorPaletteSpriteOBJWIN(int address, int xSize) {
        address = address >> 2;
        for (int objBufferPos = 0; objBufferPos < xSize; objBufferPos += 8) {
            int data = VRAM32.get(address);
            scratchOBJBuffer[objBufferPos] = data & 0xF;
            scratchOBJBuffer[objBufferPos + 1] = (data >> 4) & 0xF;
            scratchOBJBuffer[objBufferPos + 2] = (data >> 8) & 0xF;
            scratchOBJBuffer[objBufferPos + 3] = (data >> 12) & 0xF;
            scratchOBJBuffer[objBufferPos + 4] = (data >> 16) & 0xF;
            scratchOBJBuffer[objBufferPos + 5] = (data >> 20) & 0xF;
            scratchOBJBuffer[objBufferPos + 6] = (data >> 24) & 0xF;
            scratchOBJBuffer[objBufferPos + 7] = data >>> 28;

            address += 0x8;
        }
    }

    /**
     * Convert tile number to VRAM address for 256-color mode.
     */
    public int tileNumberToAddress256(int tileNumber, int xSize, int yOffset) {
        if ((gfx.displayControl & 0x40) == 0) {
            // 2D Mapping (32 8x8 tiles by 32 8x8 tiles)
            // Hardware ignores the LSB in this case
            tileNumber = (tileNumber & ~1) + ((yOffset >> 3) * 0x20);
        } else {
            // 1D Mapping
            // 256 Color Palette
            tileNumber = tileNumber + ((yOffset >> 3) * (xSize >> 2));
        }
        // Starting address of currently drawing sprite line
        return (tileNumber << 5) + 0x10000;
    }

    /**
     * Convert tile number to VRAM address for 16-color mode.
     */
    public int tileNumberToAddress16(int tileNumber, int xSize, int yOffset) {
        if ((gfx.displayControl & 0x40) == 0) {
            // 2D Mapping (32 8x8 tiles by 32 8x8 tiles)
            tileNumber = tileNumber + ((yOffset >> 3) * 0x20);
        } else {
            // 1D Mapping
            // 16 Color Palette
            tileNumber = tileNumber + ((yOffset >> 3) * (xSize >> 3));
        }
        // Starting address of currently drawing sprite line
        return (tileNumber << 5) + 0x10000;
    }

    /**
     * Output sprite to scratch buffer (normal mode).
     */
    public void outputSpriteToScratch(OAMEntry sprite, int xSize) {
        // Simulate x-coord wrap around logic
        int xcoord = sprite.xcoord;
        if (xcoord > (0x200 - xSize)) {
            xcoord = xcoord - 0x200;
        }

        // Perform the mosaic transform
        if (sprite.mosaic != 0) {
            mosaicRenderer.renderOBJMosaicHorizontal(xcoord, xSize);
        }

        // Resolve end point
        int xcoordEnd = Math.min(xcoord + xSize, 240);

        // Flag for compositor to ID the pixels as OBJ
        int bitFlags = (sprite.priority << 23) | 0x100000;

        if (sprite.horizontalFlip == 0 || sprite.matrix2D != 0) {
            // Normal
            outputSpriteNormal(xcoord, xcoordEnd, bitFlags);
        } else {
            // Flipped Horizontally
            outputSpriteFlipped(xcoord, xcoordEnd, bitFlags, xSize);
        }
    }

    /**
     * Output sprite to scratch buffer (semi-transparent mode).
     */
    public void outputSemiTransparentSpriteToScratch(OAMEntry sprite, int xSize) {
        // Simulate x-coord wrap around logic
        int xcoord = sprite.xcoord;
        if (xcoord > (0x200 - xSize)) {
            xcoord = xcoord - 0x200;
        }

        // Perform the mosaic transform
        if (sprite.mosaic != 0) {
            mosaicRenderer.renderOBJMosaicHorizontal(xcoord, xSize);
        }

        // Resolve end point
        int xcoordEnd = Math.min(xcoord + xSize, 240);

        // Flag for compositor to ID the pixels as OBJ (semi-transparent)
        int bitFlags = (sprite.priority << 23) | 0x500000;

        if (sprite.horizontalFlip == 0 || sprite.matrix2D != 0) {
            // Normal
            outputSpriteNormal(xcoord, xcoordEnd, bitFlags);
        } else {
            // Flipped Horizontally
            outputSpriteFlipped(xcoord, xcoordEnd, bitFlags, xSize);
        }
    }

    /**
     * Output sprite to OBJ window scratch buffer.
     */
    public void outputSpriteToOBJWINScratch(OAMEntry sprite, int xSize) {
        // Simulate x-coord wrap around logic
        int xcoord = sprite.xcoord;
        if (xcoord > (0x200 - xSize)) {
            xcoord = xcoord - 0x200;
        }

        // Perform the mosaic transform
        if (sprite.mosaic != 0) {
            mosaicRenderer.renderOBJMosaicHorizontal(xcoord, xSize);
        }

        // Resolve end point
        int xcoordEnd = Math.min(xcoord + xSize, 240);

        if (sprite.horizontalFlip == 0 || sprite.matrix2D != 0) {
            // Normal
            outputSpriteNormalOBJWIN(xcoord, xcoordEnd);
        } else {
            // Flipped Horizontally
            outputSpriteFlippedOBJWIN(xcoord, xcoordEnd, xSize);
        }
    }

    /**
     * Output sprite pixels (normal order).
     */
    public void outputSpriteNormal(int xcoord, int xcoordEnd, int bitFlags) {
        for (int xSource = 0; xcoord < xcoordEnd; xcoord++, xSource++) {
            int pixel = bitFlags | scratchOBJBuffer[xSource];
            // Overwrite by priority
            if (xcoord > -1 && (pixel & 0x3800000) < (scratchBuffer[xcoord + offset] & 0x3800000)) {
                scratchBuffer[xcoord + offset] = pixel;
            }
        }
    }

    /**
     * Output sprite pixels (flipped order).
     */
    public void outputSpriteFlipped(int xcoord, int xcoordEnd, int bitFlags, int xSize) {
        for (int xSource = xSize - 1; xcoord < xcoordEnd; xcoord++, xSource--) {
            int pixel = bitFlags | scratchOBJBuffer[xSource];
            // Overwrite by priority
            if (xcoord > -1 && (pixel & 0x3800000) < (scratchBuffer[xcoord + offset] & 0x3800000)) {
                scratchBuffer[xcoord + offset] = pixel;
            }
        }
    }

    /**
     * Output sprite pixels to OBJ window (normal order).
     */
    public void outputSpriteNormalOBJWIN(int xcoord, int xcoordEnd) {
        for (int xSource = 0; xcoord < xcoordEnd; xcoord++, xSource++) {
            if (xcoord > -1 && scratchOBJBuffer[xSource] != 0) {
                scratchWindowBuffer[xcoord] = 0;
            }
        }
    }

    /**
     * Output sprite pixels to OBJ window (flipped order).
     */
    public void outputSpriteFlippedOBJWIN(int xcoord, int xcoordEnd, int xSize) {
        for (int xSource = xSize - 1; xcoord < xcoordEnd; xcoord++, xSource--) {
            if (xcoord > -1 && scratchOBJBuffer[xSource] != 0) {
                scratchWindowBuffer[xcoord] = 0;
            }
        }
    }

    /**
     * Check if a sprite is drawable.
     */
    public boolean isDrawable(OAMEntry sprite) {
        // Make sure we pass some checks that real hardware does
        if (sprite.mode <= 2) {
            if (sprite.doubleSizeOrDisabled == 0 || sprite.matrix2D != 0) {
                if (sprite.shape < 3) {
                    if ((gfx.displayControl & 0x7) < 3 || sprite.tileNumber >= 0x200) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Set HBlank interval free status (affects cycle limit).
     */
    public void setHBlankIntervalFreeStatus(int data) {
        if (data != 0) {
            cyclesToRender = 954;
        } else {
            cyclesToRender = 1210;
        }
    }

    // OAM read/write methods

    /**
     * Read byte from OAM.
     */
    public int readOAM(int address) {
        return OAMRAM[address & 0x3FF] & 0xFF;
    }

    /**
     * Read 16-bit value from OAM.
     */
    public int readOAM16(int address) {
        return OAMRAM16.get(address & 0x1FF) & 0xFFFF;
    }

    /**
     * Read 32-bit value from OAM.
     */
    public int readOAM32(int address) {
        return OAMRAM32.get(address & 0xFF);
    }

    /**
     * Write 16-bit value to OAM.
     */
    public void writeOAM16(int address, int data) {
        OAMEntry OAMTable = this.OAMTable[address >> 2];

        switch (address & 0x3) {
            case 0:  // Attrib 0
                OAMTable.ycoord = data & 0xFF;
                OAMTable.matrix2D = data & 0x100;
                OAMTable.doubleSizeOrDisabled = (data & 0x200) >> 9;
                OAMTable.mode = (data >> 10) & 0x3;
                OAMTable.mosaic = data & 0x1000;
                OAMTable.monolithicPalette = data & 0x2000;
                OAMTable.shape = data >> 14;
                break;

            case 1:  // Attrib 1
                OAMTable.xcoord = data & 0x1FF;
                OAMTable.matrixParameters = (data >> 7) & 0x7C;
                OAMTable.horizontalFlip = data & 0x1000;
                OAMTable.verticalFlip = data & 0x2000;
                OAMTable.size = data >> 14;
                break;

            case 2:  // Attrib 2
                OAMTable.tileNumber = data & 0x3FF;
                OAMTable.priority = (data >> 10) & 0x3;
                OAMTable.paletteNumber = (data >> 8) & 0xF0;
                break;

            default:  // Scaling/Rotation Parameter
                OBJMatrixParameters[address >> 2] = (short)data;  // Sign extend
                break;
        }

        OAMRAM16.put(address & 0x1FF, (short)data);
    }

    /**
     * Write 32-bit value to OAM.
     */
    public void writeOAM32(int address, int data) {
        OAMEntry OAMTable = this.OAMTable[address >> 1];

        if ((address & 0x1) == 0) {
            // Attrib 0
            OAMTable.ycoord = data & 0xFF;
            OAMTable.matrix2D = data & 0x100;
            OAMTable.doubleSizeOrDisabled = (data & 0x200) >> 9;
            OAMTable.mode = (data >> 10) & 0x3;
            OAMTable.mosaic = data & 0x1000;
            OAMTable.monolithicPalette = data & 0x2000;
            OAMTable.shape = (data >> 14) & 0x3;

            // Attrib 1
            OAMTable.xcoord = (data >> 16) & 0x1FF;
            OAMTable.matrixParameters = (data >> 23) & 0x7C;
            OAMTable.horizontalFlip = data & 0x10000000;
            OAMTable.verticalFlip = data & 0x20000000;
            OAMTable.size = data >>> 30;
        } else {
            // Attrib 2
            OAMTable.tileNumber = data & 0x3FF;
            OAMTable.priority = (data >> 10) & 0x3;
            OAMTable.paletteNumber = (data >> 8) & 0xF0;

            // Scaling/Rotation Parameter
            OBJMatrixParameters[address >> 1] = data >> 16;
        }

        OAMRAM32.put(address & 0xFF, data);
    }
}
