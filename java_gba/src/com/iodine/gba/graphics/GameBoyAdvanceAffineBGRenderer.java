package com.iodine.gba.graphics;

/**
 * GameBoyAdvanceAffineBGRenderer - Affine background renderer (converted from AffineBG.js)
 * Copyright (C) 2012-2015 Grant Galitz
 *
 * Handles affine (rotation/scaling) backgrounds for modes 1-2.
 * Uses a 2x2 transformation matrix to map screen coordinates to texture coordinates.
 * The matrix is:
 *   [dx  dmx]   (PA PB)
 *   [dy  dmy]   (PC PD)
 *
 * Reference point (BGReferenceX, BGReferenceY) defines the origin for the transformation.
 */
public class GameBoyAdvanceAffineBGRenderer {
    public GameBoyAdvanceRenderer gfx;
    public int BGLayer;
    public int offset;  // Buffer offset for this layer

    // Sub-renderers (shared with parent)
    public GameBoyAdvanceBGMatrixRenderer bg2MatrixRenderer;
    public GameBoyAdvanceBGMatrixRenderer bg3MatrixRenderer;
    public GameBoyAdvanceBG2FrameBufferRenderer bg2FrameBufferRenderer;
    public GameBoyAdvanceMosaicRenderer mosaicRenderer;

    // Affine transformation matrix parameters (8.8 fixed point)
    public int BGdx;   // PA: Horizontal scaling/rotation (d screenX / d textureX)
    public int BGdmx;  // PB: Vertical shearing (d screenX / d textureY)
    public int BGdy;   // PC: Horizontal shearing (d screenY / d textureX)
    public int BGdmy;  // PD: Vertical scaling/rotation (d screenY / d textureY)

    // Reference point (28.4 fixed point)
    public int BGReferenceX;  // X origin for transformation
    public int BGReferenceY;  // Y origin for transformation

    // Current rendering counters (updated per scanline)
    public int pb;  // Current X position (starts at BGReferenceX)
    public int pd;  // Current Y position (starts at BGReferenceY)

    // Rendering state
    public int[] buffer;
    public int priorityFlag;
    public int doMosaic;

    public GameBoyAdvanceAffineBGRenderer(GameBoyAdvanceRenderer gfx, int BGLayer) {
        this.gfx = gfx;
        this.BGLayer = BGLayer;
        this.offset = (BGLayer << 8) + 0x100;  // BG2=0x300, BG3=0x400
    }

    public void initialize() {
        // Get references from parent renderer
        bg2MatrixRenderer = gfx.bg2MatrixRenderer;
        bg3MatrixRenderer = gfx.bg3MatrixRenderer;
        bg2FrameBufferRenderer = gfx.bg2FrameBufferRenderer;
        mosaicRenderer = gfx.mosaicRenderer;
        buffer = gfx.buffer;

        // Initialize transformation matrix to identity
        BGdx = 0x100;   // 1.0 in 8.8 fixed point
        BGdmx = 0;
        BGdy = 0;
        BGdmy = 0x100;  // 1.0 in 8.8 fixed point

        // Initialize reference point
        BGReferenceX = 0;
        BGReferenceY = 0;

        // Initialize counters
        pb = 0;
        pd = 0;

        doMosaic = 0;
        priorityPreprocess(0);
        offsetReferenceCounters();
    }

    /**
     * Render scanline for BG2 using matrix mode (tiles).
     * Used in modes 1-2.
     */
    public void renderScanLine2M(int line) {
        int x = pb;
        int y = pd;

        // Apply mosaic correction if enabled
        if (doMosaic != 0) {
            int mosaicY = mosaicRenderer.getMosaicYOffset(line);
            x -= BGdmx * mosaicY;
            y -= BGdmy * mosaicY;
        }

        // Render each pixel using affine transformation
        for (int position = 0; position < 240; position++, x += BGdx, y += BGdy) {
            // Fetch pixel from matrix renderer (fixed point >> 8 to get integer coords)
            buffer[offset + position] = priorityFlag | bg2MatrixRenderer.getPixel(x >> 8, y >> 8);
        }

        // Apply horizontal mosaic if enabled
        if (doMosaic != 0) {
            mosaicRenderer.renderMosaicHorizontal(offset);
        }
    }

    /**
     * Render scanline for BG3 using matrix mode (tiles).
     * Used in mode 2.
     */
    public void renderScanLine3M(int line) {
        int x = pb;
        int y = pd;

        // Apply mosaic correction if enabled
        if (doMosaic != 0) {
            int mosaicY = mosaicRenderer.getMosaicYOffset(line);
            x -= BGdmx * mosaicY;
            y -= BGdmy * mosaicY;
        }

        // Render each pixel using affine transformation
        for (int position = 0; position < 240; position++, x += BGdx, y += BGdy) {
            buffer[offset + position] = priorityFlag | bg3MatrixRenderer.getPixel(x >> 8, y >> 8);
        }

        // Apply horizontal mosaic if enabled
        if (doMosaic != 0) {
            mosaicRenderer.renderMosaicHorizontal(offset);
        }
    }

    /**
     * Render scanline for BG2 using framebuffer mode (bitmap).
     * Used in modes 3-5.
     */
    public void renderScanLine2F(int line) {
        int x = pb;
        int y = pd;

        // Apply mosaic correction if enabled
        if (doMosaic != 0) {
            int mosaicY = mosaicRenderer.getMosaicYOffset(line);
            x -= BGdmx * mosaicY;
            y -= BGdmy * mosaicY;
        }

        // Render each pixel using affine transformation
        for (int position = 0; position < 240; position++, x += BGdx, y += BGdy) {
            buffer[offset + position] = priorityFlag | bg2FrameBufferRenderer.getPixel(x >> 8, y >> 8);
        }

        // Apply horizontal mosaic if enabled
        if (doMosaic != 0) {
            mosaicRenderer.renderMosaicHorizontal(offset);
        }
    }

    /**
     * Offset reference counters for skipped scanlines.
     * Called during initialization or when catching up after VBlank.
     */
    public void offsetReferenceCounters() {
        int end = gfx.lastUnrenderedLine;
        pb = BGReferenceX + BGdmx * end;
        pd = BGReferenceY + BGdmy * end;
    }

    /**
     * Increment reference counters for next scanline.
     * Called after rendering each scanline.
     */
    public void incrementReferenceCounters() {
        pb += BGdmx;
        pd += BGdmy;
    }

    /**
     * Reset reference counters to reference point.
     * Called at start of frame (line 0) or when reference Y is written.
     */
    public void resetReferenceCounters() {
        pb = BGReferenceX;
        pd = BGReferenceY;
    }

    /**
     * Enable or disable mosaic effect for this layer.
     */
    public void setMosaicEnable(int doMosaic) {
        this.doMosaic = doMosaic;
    }

    /**
     * Set priority and layer flags for rendered pixels.
     */
    public void priorityPreprocess(int BGPriority) {
        // Priority in bits 23-24, layer flag in bit (0x10 + BGLayer)
        priorityFlag = (BGPriority << 23) | (1 << (0x10 + BGLayer));
    }

    // Affine transformation matrix register writes
    // All values are 8.8 fixed point signed

    public void writeBGPA8_0(int data) {
        BGdx = (BGdx & 0xFFFFFF00) | (data & 0xFF);
    }

    public void writeBGPA8_1(int data) {
        // Sign extend from 8 bits to 16 bits, then combine
        int signExtended = (data << 24) >> 16;
        BGdx = signExtended | (BGdx & 0xFF);
    }

    public void writeBGPA16(int data) {
        // Sign extend from 16 bits to 32 bits
        BGdx = (short)data;  // Java sign extends automatically
    }

    public void writeBGPB8_0(int data) {
        BGdmx = (BGdmx & 0xFFFFFF00) | (data & 0xFF);
    }

    public void writeBGPB8_1(int data) {
        int signExtended = (data << 24) >> 16;
        BGdmx = signExtended | (BGdmx & 0xFF);
    }

    public void writeBGPB16(int data) {
        BGdmx = (short)data;
    }

    public void writeBGPAB32(int data) {
        BGdx = (short)data;
        BGdmx = (short)(data >> 16);
    }

    public void writeBGPC8_0(int data) {
        BGdy = (BGdy & 0xFFFFFF00) | (data & 0xFF);
    }

    public void writeBGPC8_1(int data) {
        int signExtended = (data << 24) >> 16;
        BGdy = signExtended | (BGdy & 0xFF);
    }

    public void writeBGPC16(int data) {
        BGdy = (short)data;
    }

    public void writeBGPD8_0(int data) {
        BGdmy = (BGdmy & 0xFFFFFF00) | (data & 0xFF);
    }

    public void writeBGPD8_1(int data) {
        int signExtended = (data << 24) >> 16;
        BGdmy = signExtended | (BGdmy & 0xFF);
    }

    public void writeBGPD16(int data) {
        BGdmy = (short)data;
    }

    public void writeBGPCD32(int data) {
        BGdy = (short)data;
        BGdmy = (short)(data >> 16);
    }

    // Reference point register writes (28.4 fixed point signed)
    // X writes do NOT reset counters (hardware quirk)
    // Y writes DO reset counters

    public void writeBGX8_0(int data) {
        BGReferenceX = (BGReferenceX & 0xFFFFFF00) | (data & 0xFF);
    }

    public void writeBGX8_1(int data) {
        BGReferenceX = (BGReferenceX & 0xFFFF00FF) | ((data & 0xFF) << 8);
    }

    public void writeBGX8_2(int data) {
        BGReferenceX = (BGReferenceX & 0xFF00FFFF) | ((data & 0xFF) << 16);
    }

    public void writeBGX8_3(int data) {
        // Sign extend from 4 bits to 28 bits (bits 27-24)
        int signExtended = (data << 28) >> 4;
        BGReferenceX = signExtended | (BGReferenceX & 0xFFFFFF);
    }

    public void writeBGX16_0(int data) {
        BGReferenceX = (BGReferenceX & 0xFFFF0000) | (data & 0xFFFF);
    }

    public void writeBGX16_1(int data) {
        // Sign extend from 12 bits to 28 bits (bits 27-16)
        int signExtended = (data << 20) >> 4;
        BGReferenceX = (BGReferenceX & 0xFFFF) | signExtended;
    }

    public void writeBGX32(int data) {
        // Sign extend from 28 bits to 32 bits
        BGReferenceX = (data << 4) >> 4;
    }

    public void writeBGY8_0(int data) {
        BGReferenceY = (BGReferenceY & 0xFFFFFF00) | (data & 0xFF);
        resetReferenceCounters();
    }

    public void writeBGY8_1(int data) {
        BGReferenceY = (BGReferenceY & 0xFFFF00FF) | ((data & 0xFF) << 8);
        resetReferenceCounters();
    }

    public void writeBGY8_2(int data) {
        BGReferenceY = (BGReferenceY & 0xFF00FFFF) | ((data & 0xFF) << 16);
        resetReferenceCounters();
    }

    public void writeBGY8_3(int data) {
        int signExtended = (data << 28) >> 4;
        BGReferenceY = signExtended | (BGReferenceY & 0xFFFFFF);
        resetReferenceCounters();
    }

    public void writeBGY16_0(int data) {
        BGReferenceY = (BGReferenceY & 0xFFFF0000) | (data & 0xFFFF);
        resetReferenceCounters();
    }

    public void writeBGY16_1(int data) {
        int signExtended = (data << 20) >> 4;
        BGReferenceY = (BGReferenceY & 0xFFFF) | signExtended;
        resetReferenceCounters();
    }

    public void writeBGY32(int data) {
        BGReferenceY = (data << 4) >> 4;
        resetReferenceCounters();
    }
}
