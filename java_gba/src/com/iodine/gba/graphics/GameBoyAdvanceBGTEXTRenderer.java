package com.iodine.gba.graphics;

/**
 * GameBoyAdvanceBGTEXTRenderer - Text/tile background renderer (converted from BGTEXT.js)
 * Copyright (C) 2012-2015 Grant Galitz
 *
 * Handles regular tile-based backgrounds for modes 0-1.
 * Supports:
 * - 4bpp (16-color) and 8bpp (256-color) tiles
 * - Horizontal and vertical scrolling
 * - Tile flipping (horizontal and vertical)
 * - Multiple screen sizes (32x32, 64x32, 32x64, 64x64 tiles)
 * - Mosaic effects
 */
public class GameBoyAdvanceBGTEXTRenderer {
    public GameBoyAdvanceRenderer gfx;
    public int BGLayer;
    public int offset;  // Buffer offset for this layer

    // References to parent renderer's data
    public byte[] VRAM;
    public java.nio.ShortBuffer VRAM16;
    public java.nio.IntBuffer VRAM32;
    public int[] palette16;
    public int[] palette256;
    public int[] buffer;

    // Scratch buffers for tile rendering
    public int[] tileFetched;  // 8 pixels of current tile

    // Scroll coordinates (9-bit values)
    public int BGXCoord;
    public int BGYCoord;

    // Rendering state
    public int do256;       // 0=4bpp, 1=8bpp
    public int doMosaic;    // Mosaic enable
    public int priorityFlag;

    // Screen configuration
    public int tileMode;    // 0=32x32, 1=64x32, 2=32x64, 3=64x64
    public int BGScreenBaseBlock;
    public int BGCharacterBaseBlock;

    // Reference to mosaic renderer
    public GameBoyAdvanceMosaicRenderer mosaicRenderer;

    public GameBoyAdvanceBGTEXTRenderer(GameBoyAdvanceRenderer gfx, int BGLayer) {
        this.gfx = gfx;
        this.BGLayer = BGLayer;
        this.offset = (BGLayer << 8) + 0x100;  // BG0=0x100, BG1=0x200, BG2=0x300, BG3=0x400
    }

    public void initialize() {
        // Get references from parent renderer
        VRAM = gfx.VRAM;
        VRAM16 = gfx.VRAM16;
        VRAM32 = gfx.VRAM32;
        palette16 = gfx.palette16;
        palette256 = gfx.palette256;
        buffer = gfx.buffer;
        mosaicRenderer = gfx.mosaicRenderer;

        // Initialize scratch buffer for tile fetching
        tileFetched = new int[8];

        // Initialize state
        BGXCoord = 0;
        BGYCoord = 0;
        do256 = 0;
        doMosaic = 0;

        // Initialize configuration
        screenSizePreprocess(0);
        priorityPreprocess(0);
        screenBaseBlockPreprocess(0);
        characterBaseBlockPreprocess(0);
    }

    /**
     * Render a scanline.
     * This is the main entry point called by the graphics renderer.
     */
    public void renderScanLine(int line) {
        // Apply mosaic Y offset if enabled
        if (doMosaic != 0) {
            line = line - mosaicRenderer.getMosaicYOffset(line);
        }

        // Calculate tile coordinates
        int yTileOffset = (line + BGYCoord) & 0x7;
        int yTileStart = (line + BGYCoord) >> 3;
        int xTileStart = BGXCoord >> 3;

        // Render the tiles based on color mode
        if (do256 != 0) {
            // 8-bit palette mode (256 colors)
            render8BITLine(yTileStart, xTileStart, yTileOffset);
        } else {
            // 4-bit palette mode (16 colors)
            render4BITLine(yTileStart, xTileStart, yTileOffset);
        }

        // Apply horizontal mosaic if enabled
        if (doMosaic != 0) {
            mosaicRenderer.renderMosaicHorizontal(offset);
        }
    }

    /**
     * Render a scanline in 8-bit color mode.
     */
    public void render8BITLine(int yTileStart, int xTileStart, int yTileOffset) {
        // Fetch first tile attributes
        int chrData = fetchTile(yTileStart, xTileStart);
        xTileStart++;

        // Get 8 pixels of data
        process8BitVRAM(chrData, yTileOffset);

        // Copy the buffered tile to line (handles partial first tile)
        fetchVRAMStart();

        // Render the rest of the tiles fast
        renderWholeTiles8BIT(xTileStart, yTileStart, yTileOffset);
    }

    /**
     * Render a scanline in 4-bit color mode.
     */
    public void render4BITLine(int yTileStart, int xTileStart, int yTileOffset) {
        // Fetch first tile attributes
        int chrData = fetchTile(yTileStart, xTileStart);
        xTileStart++;

        // Get 8 pixels of data
        process4BitVRAM(chrData, yTileOffset);

        // Copy the buffered tile to line (handles partial first tile)
        fetchVRAMStart();

        // Render the rest of the tiles fast
        renderWholeTiles4BIT(xTileStart, yTileStart, yTileOffset);
    }

    /**
     * Render whole tiles in 8-bit mode.
     * Process full 8 pixels at a time.
     */
    public void renderWholeTiles8BIT(int xTileStart, int yTileStart, int yTileOffset) {
        // Process full 8 pixels at a time
        for (int position = 8 - (BGXCoord & 0x7); position < 240; position += 8) {
            // Fetch tile attributes and get 8 pixels of data
            process8BitVRAM(fetchTile(yTileStart, xTileStart), yTileOffset);

            // Copy the buffered tile to line
            buffer[offset + position] = tileFetched[0];
            buffer[offset + position + 1] = tileFetched[1];
            buffer[offset + position + 2] = tileFetched[2];
            buffer[offset + position + 3] = tileFetched[3];
            buffer[offset + position + 4] = tileFetched[4];
            buffer[offset + position + 5] = tileFetched[5];
            buffer[offset + position + 6] = tileFetched[6];
            buffer[offset + position + 7] = tileFetched[7];

            // Increment tile counter
            xTileStart++;
        }
    }

    /**
     * Render whole tiles in 4-bit mode.
     * Process full 8 pixels at a time.
     */
    public void renderWholeTiles4BIT(int xTileStart, int yTileStart, int yTileOffset) {
        // Process full 8 pixels at a time
        for (int position = 8 - (BGXCoord & 0x7); position < 240; position += 8) {
            // Fetch tile attributes and get 8 pixels of data
            process4BitVRAM(fetchTile(yTileStart, xTileStart), yTileOffset);

            // Copy the buffered tile to line
            buffer[offset + position] = tileFetched[0];
            buffer[offset + position + 1] = tileFetched[1];
            buffer[offset + position + 2] = tileFetched[2];
            buffer[offset + position + 3] = tileFetched[3];
            buffer[offset + position + 4] = tileFetched[4];
            buffer[offset + position + 5] = tileFetched[5];
            buffer[offset + position + 6] = tileFetched[6];
            buffer[offset + position + 7] = tileFetched[7];

            // Increment tile counter
            xTileStart++;
        }
    }

    /**
     * Handle the first tile of the scan-line specially.
     * The first tile may be partial based on BGXCoord offset.
     */
    public void fetchVRAMStart() {
        int pixelPipelinePosition = BGXCoord & 0x7;

        // Use fall-through switch to copy pixels starting from the offset
        switch (pixelPipelinePosition) {
            case 0:
                buffer[offset + 0] = tileFetched[0];
            case 1:
                buffer[offset + 1 - pixelPipelinePosition] = tileFetched[1];
            case 2:
                buffer[offset + 2 - pixelPipelinePosition] = tileFetched[2];
            case 3:
                buffer[offset + 3 - pixelPipelinePosition] = tileFetched[3];
            case 4:
                buffer[offset + 4 - pixelPipelinePosition] = tileFetched[4];
            case 5:
                buffer[offset + 5 - pixelPipelinePosition] = tileFetched[5];
            case 6:
                buffer[offset + 6 - pixelPipelinePosition] = tileFetched[6];
            default:
                buffer[offset + 7 - pixelPipelinePosition] = tileFetched[7];
        }
    }

    /**
     * Fetch tile attributes from the tilemap.
     * Returns a 16-bit value containing tile number and flip/palette attributes.
     */
    public int fetchTile(int yTileStart, int xTileStart) {
        // Find the tile code to locate the tile block
        int address = computeTileNumber(yTileStart, xTileStart) + BGScreenBaseBlock;
        return VRAM16.get(address & 0x7FFF) & 0xFFFF;
    }

    /**
     * Compute the tile number based on screen size mode and tile coordinates.
     *
     * Screen size modes:
     * 0: 32x32 tiles (256x256 pixels)
     * 1: 64x32 tiles (512x256 pixels)
     * 2: 32x64 tiles (256x512 pixels)
     * 3: 64x64 tiles (512x512 pixels)
     */
    public int computeTileNumber(int yTile, int xTile) {
        int tileNumber = xTile & 0x1F;

        switch (tileMode) {
            case 0:  // 1x1 (32x32 tiles)
                tileNumber = tileNumber | ((yTile & 0x1F) << 5);
                break;
            case 1:  // 2x1 (64x32 tiles)
                tileNumber = tileNumber | (((xTile & 0x20) | (yTile & 0x1F)) << 5);
                break;
            case 2:  // 1x2 (32x64 tiles)
                tileNumber = tileNumber | ((yTile & 0x3F) << 5);
                break;
            case 3:  // 2x2 (64x64 tiles)
                tileNumber = tileNumber | (((xTile & 0x20) | (yTile & 0x1F)) << 5) | ((yTile & 0x20) << 6);
                break;
        }

        return tileNumber;
    }

    /**
     * Process a 4-bit tile from VRAM.
     * Handles vertical flip and calls render function.
     */
    public void process4BitVRAM(int chrData, int yOffset) {
        // Parse flip attributes, grab palette, and then output pixel
        int address = (chrData & 0x3FF) << 3;
        address += BGCharacterBaseBlock;

        if ((chrData & 0x800) == 0) {
            // No vertical flip
            address += yOffset;
        } else {
            // Vertical flip
            address += 7 - yOffset;
        }

        // Copy out our pixels (pass palette and flip info)
        render4BitVRAM(chrData >> 8, address);
    }

    /**
     * Render 8 pixels from 4-bit VRAM data.
     * Each pixel is 4 bits (16 colors from one of 16 palettes).
     */
    public void render4BitVRAM(int chrData, int address) {
        // Check if tile address is valid
        if (address < 0x4000) {
            // Tile address valid
            int paletteOffset = chrData & 0xF0;
            int data = VRAM32.get(address);

            if ((chrData & 0x4) == 0) {
                // Normal horizontal
                tileFetched[0] = palette16[paletteOffset | (data & 0xF)] | priorityFlag;
                tileFetched[1] = palette16[paletteOffset | ((data >> 4) & 0xF)] | priorityFlag;
                tileFetched[2] = palette16[paletteOffset | ((data >> 8) & 0xF)] | priorityFlag;
                tileFetched[3] = palette16[paletteOffset | ((data >> 12) & 0xF)] | priorityFlag;
                tileFetched[4] = palette16[paletteOffset | ((data >> 16) & 0xF)] | priorityFlag;
                tileFetched[5] = palette16[paletteOffset | ((data >> 20) & 0xF)] | priorityFlag;
                tileFetched[6] = palette16[paletteOffset | ((data >> 24) & 0xF)] | priorityFlag;
                tileFetched[7] = palette16[paletteOffset | (data >>> 28)] | priorityFlag;
            } else {
                // Flipped horizontally
                tileFetched[0] = palette16[paletteOffset | (data >>> 28)] | priorityFlag;
                tileFetched[1] = palette16[paletteOffset | ((data >> 24) & 0xF)] | priorityFlag;
                tileFetched[2] = palette16[paletteOffset | ((data >> 20) & 0xF)] | priorityFlag;
                tileFetched[3] = palette16[paletteOffset | ((data >> 16) & 0xF)] | priorityFlag;
                tileFetched[4] = palette16[paletteOffset | ((data >> 12) & 0xF)] | priorityFlag;
                tileFetched[5] = palette16[paletteOffset | ((data >> 8) & 0xF)] | priorityFlag;
                tileFetched[6] = palette16[paletteOffset | ((data >> 4) & 0xF)] | priorityFlag;
                tileFetched[7] = palette16[paletteOffset | (data & 0xF)] | priorityFlag;
            }
        } else {
            // Tile address invalid
            addressInvalidRender();
        }
    }

    /**
     * Process an 8-bit tile from VRAM.
     * Handles horizontal and vertical flip and calls render function.
     */
    public void process8BitVRAM(int chrData, int yOffset) {
        // Parse flip attributes and output pixels
        int address = (chrData & 0x3FF) << 4;
        address += BGCharacterBaseBlock;

        // Handle flip attributes
        switch (chrData & 0xC00) {
            case 0:  // No flip
                address += yOffset << 1;
                render8BitVRAMNormal(address);
                break;
            case 0x400:  // Horizontal flip
                address += yOffset << 1;
                render8BitVRAMFlipped(address);
                break;
            case 0x800:  // Vertical flip
                address += 14 - (yOffset << 1);
                render8BitVRAMNormal(address);
                break;
            default:  // Horizontal & Vertical flip
                address += 14 - (yOffset << 1);
                render8BitVRAMFlipped(address);
                break;
        }
    }

    /**
     * Render 8 pixels from 8-bit VRAM data (normal horizontal order).
     * Each pixel is 8 bits (256 colors).
     */
    public void render8BitVRAMNormal(int address) {
        if (address < 0x4000) {
            // Tile address valid - normal horizontal
            int data = VRAM32.get(address);
            tileFetched[0] = palette256[data & 0xFF] | priorityFlag;
            tileFetched[1] = palette256[(data >> 8) & 0xFF] | priorityFlag;
            tileFetched[2] = palette256[(data >> 16) & 0xFF] | priorityFlag;
            tileFetched[3] = palette256[data >>> 24] | priorityFlag;

            data = VRAM32.get(address + 1);
            tileFetched[4] = palette256[data & 0xFF] | priorityFlag;
            tileFetched[5] = palette256[(data >> 8) & 0xFF] | priorityFlag;
            tileFetched[6] = palette256[(data >> 16) & 0xFF] | priorityFlag;
            tileFetched[7] = palette256[data >>> 24] | priorityFlag;
        } else {
            // Tile address invalid
            addressInvalidRender();
        }
    }

    /**
     * Render 8 pixels from 8-bit VRAM data (flipped horizontal order).
     * Each pixel is 8 bits (256 colors).
     */
    public void render8BitVRAMFlipped(int address) {
        if (address < 0x4000) {
            // Tile address valid - flipped horizontally
            int data = VRAM32.get(address);
            tileFetched[4] = palette256[data >>> 24] | priorityFlag;
            tileFetched[5] = palette256[(data >> 16) & 0xFF] | priorityFlag;
            tileFetched[6] = palette256[(data >> 8) & 0xFF] | priorityFlag;
            tileFetched[7] = palette256[data & 0xFF] | priorityFlag;

            data = VRAM32.get(address + 1);
            tileFetched[0] = palette256[data >>> 24] | priorityFlag;
            tileFetched[1] = palette256[(data >> 16) & 0xFF] | priorityFlag;
            tileFetched[2] = palette256[(data >> 8) & 0xFF] | priorityFlag;
            tileFetched[3] = palette256[data & 0xFF] | priorityFlag;
        } else {
            // Tile address invalid
            addressInvalidRender();
        }
    }

    /**
     * Fill tile buffer with transparency for invalid tile addresses.
     * In GBA mode on NDS, we display transparency on invalid tiles.
     */
    public void addressInvalidRender() {
        // Transparency is 0x3800000 (bit 25 set) OR'd with priority
        int data = 0x3800000 | priorityFlag;
        tileFetched[0] = data;
        tileFetched[1] = data;
        tileFetched[2] = data;
        tileFetched[3] = data;
        tileFetched[4] = data;
        tileFetched[5] = data;
        tileFetched[6] = data;
        tileFetched[7] = data;
    }

    /**
     * Enable or disable mosaic effect for this layer.
     */
    public void setMosaicEnable(int doMosaic) {
        this.doMosaic = doMosaic;
    }

    /**
     * Select palette mode: 0=4bpp (16 colors), 1=8bpp (256 colors).
     */
    public void paletteModeSelect(int do256) {
        this.do256 = do256;
    }

    /**
     * Set the screen size mode.
     */
    public void screenSizePreprocess(int BGScreenSize) {
        this.tileMode = BGScreenSize;
    }

    /**
     * Set priority and layer flags for rendered pixels.
     */
    public void priorityPreprocess(int BGPriority) {
        // Priority in bits 23-24, layer flag in bit (0x10 + BGLayer)
        priorityFlag = (BGPriority << 23) | (1 << (BGLayer + 0x10));
    }

    /**
     * Set screen base block (tilemap location in VRAM).
     */
    public void screenBaseBlockPreprocess(int BGScreenBaseBlock) {
        this.BGScreenBaseBlock = BGScreenBaseBlock << 10;
    }

    /**
     * Set character base block (tile data location in VRAM).
     */
    public void characterBaseBlockPreprocess(int BGCharacterBaseBlock) {
        this.BGCharacterBaseBlock = BGCharacterBaseBlock << 12;
    }

    // Register write methods

    /**
     * Write to BGCNT register (lower 8 bits).
     * Controls priority, character base, mosaic, and palette mode.
     */
    public void writeBGCNT8_0(int data) {
        setMosaicEnable(data & 0x40);
        paletteModeSelect(data & 0x80);
        priorityPreprocess(data & 0x3);
        characterBaseBlockPreprocess((data & 0xC) >> 2);
    }

    /**
     * Write to BGCNT register (upper 8 bits).
     * Controls screen size and screen base block.
     */
    public void writeBGCNT8_1(int data) {
        screenSizePreprocess((data & 0xC0) >> 6);
        screenBaseBlockPreprocess(data & 0x1F);
    }

    /**
     * Write to BGCNT register (16 bits).
     */
    public void writeBGCNT16(int data) {
        setMosaicEnable(data & 0x40);
        paletteModeSelect(data & 0x80);
        priorityPreprocess(data & 0x3);
        characterBaseBlockPreprocess((data & 0xC) >> 2);
        screenSizePreprocess((data & 0xC000) >> 14);
        screenBaseBlockPreprocess((data >> 8) & 0x1F);
    }

    /**
     * Write to BGHOFS register (horizontal scroll, lower 8 bits).
     */
    public void writeBGHOFS8_0(int data) {
        BGXCoord = (BGXCoord & 0x100) | (data & 0xFF);
    }

    /**
     * Write to BGHOFS register (horizontal scroll, upper 8 bits).
     */
    public void writeBGHOFS8_1(int data) {
        BGXCoord = ((data & 0xFF) << 8) | (BGXCoord & 0xFF);
    }

    /**
     * Write to BGHOFS register (horizontal scroll, 16 bits).
     */
    public void writeBGHOFS16(int data) {
        BGXCoord = data & 0x1FF;
    }

    /**
     * Write to BGVOFS register (vertical scroll, lower 8 bits).
     */
    public void writeBGVOFS8_0(int data) {
        BGYCoord = (BGYCoord & 0x100) | (data & 0xFF);
    }

    /**
     * Write to BGVOFS register (vertical scroll, upper 8 bits).
     */
    public void writeBGVOFS8_1(int data) {
        BGYCoord = ((data & 0xFF) << 8) | (BGYCoord & 0xFF);
    }

    /**
     * Write to BGVOFS register (vertical scroll, 16 bits).
     */
    public void writeBGVOFS16(int data) {
        BGYCoord = data & 0x1FF;
    }

    /**
     * Write to both BGHOFS and BGVOFS registers (32 bits).
     */
    public void writeBGOFS32(int data) {
        BGXCoord = data & 0x1FF;
        BGYCoord = (data >> 16) & 0x1FF;
    }
}
