package com.iodine.gba.graphics;

import com.iodine.gba.core.GameBoyAdvanceIO;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public class GameBoyAdvanceBGTEXTRenderer {

    private GameBoyAdvanceRenderer renderer;
    private int bgNumber;

    // Buffers
    private int[] scratchBuffer;
    private int[] tileFetched;

    // BG control registers
    private int BGXCoord = 0;
    private int BGYCoord = 0;
    private int do256 = 0;
    private int doMosaic = 0;
    private int tileMode = 0;
    private int priorityFlag = 0;
    private int BGScreenBaseBlock = 0;
    private int BGCharacterBaseBlock = 0;

    // VRAM and palettes
    private byte[] VRAM;
    private ShortBuffer VRAM16;
    private IntBuffer VRAM32;
    private int[] palette16;
    private int[] palette256;


    public GameBoyAdvanceBGTEXTRenderer(GameBoyAdvanceRenderer renderer, int bgNumber) {
        this.renderer = renderer;
        this.bgNumber = bgNumber;
    }

    public void initialize() {
        this.VRAM = this.renderer.VRAM;
        this.VRAM16 = this.renderer.VRAM16;
        this.VRAM32 = this.renderer.VRAM32;
        this.palette16 = this.renderer.palette16;
        this.palette256 = this.renderer.palette256;
        int offset = (this.bgNumber << 8) + 0x100;
        this.scratchBuffer = this.renderer.buffer;
        this.tileFetched = new int[8];
        screenSizePreprocess(0);
        priorityPreprocess(0);
        screenBaseBlockPreprocess(0);
        characterBaseBlockPreprocess(0);
    }

    public void renderScanLine(int line) {
        if (this.doMosaic != 0) {
            //Correct line number for mosaic:
            line = line - (this.renderer.mosaicRenderer.getMosaicYOffset(line));
        }
        int yTileOffset = (line + this.BGYCoord) & 0x7;
        int yTileStart = (line + this.BGYCoord) >> 3;
        int xTileStart = this.BGXCoord >> 3;
        //Render the tiles:
        if (this.do256 != 0) {
            //8-bit palette mode:
            this.render8BITLine(yTileStart, xTileStart, yTileOffset);
        }
        else {
            //4-bit palette mode:
            this.render4BITLine(yTileStart, xTileStart, yTileOffset);
        }
        if (this.doMosaic != 0) {
            //Pixelize the line horizontally:
            this.renderer.mosaicRenderer.renderMosaicHorizontal((this.bgNumber << 8) + 0x100);
        }
    }

    private void render8BITLine(int yTileStart, int xTileStart, int yTileOffset) {
        int chrData = this.fetchTile(yTileStart, xTileStart);
        xTileStart = xTileStart + 1;
        this.process8BitVRAM(chrData, yTileOffset);
        this.fetchVRAMStart();
        this.renderWholeTiles8BIT(xTileStart, yTileStart, yTileOffset);
    }

    private void render4BITLine(int yTileStart, int xTileStart, int yTileOffset) {
        int chrData = this.fetchTile(yTileStart, xTileStart);
        xTileStart = xTileStart + 1;
        this.process4BitVRAM(chrData, yTileOffset);
        this.fetchVRAMStart();
        this.renderWholeTiles4BIT(xTileStart, yTileStart, yTileOffset);
    }

    private void renderWholeTiles8BIT(int xTileStart, int yTileStart, int yTileOffset) {
        for (int position = 8 - (this.BGXCoord & 0x7); position < 240; position += 8) {
            this.process8BitVRAM(this.fetchTile(yTileStart, xTileStart), yTileOffset);
            System.arraycopy(this.tileFetched, 0, this.scratchBuffer, position + (this.bgNumber << 8) + 0x100, 8);
            xTileStart = xTileStart + 1;
        }
    }

    private void renderWholeTiles4BIT(int xTileStart, int yTileStart, int yTileOffset) {
        for (int position = 8 - (this.BGXCoord & 0x7); position < 240; position += 8) {
            this.process4BitVRAM(this.fetchTile(yTileStart, xTileStart), yTileOffset);
            System.arraycopy(this.tileFetched, 0, this.scratchBuffer, position + (this.bgNumber << 8) + 0x100, 8);
            xTileStart = xTileStart + 1;
        }
    }

    private void fetchVRAMStart() {
        int pixelPipelinePosition = this.BGXCoord & 0x7;
        int offset = (this.bgNumber << 8) + 0x100;
        System.arraycopy(this.tileFetched, pixelPipelinePosition, this.scratchBuffer, offset, 8 - pixelPipelinePosition);
    }

    private int fetchTile(int yTileStart, int xTileStart) {
        int address = (this.computeTileNumber(yTileStart, xTileStart) + this.BGScreenBaseBlock);
        return this.VRAM16.get(address & 0x7FFF) & 0xFFFF;
    }

    private int computeTileNumber(int yTile, int xTile) {
        int tileNumber = xTile & 0x1F;
        switch (this.tileMode) {
            case 0:
                tileNumber = tileNumber | ((yTile & 0x1F) << 5);
                break;
            case 1:
                tileNumber = tileNumber | (((xTile & 0x20) | (yTile & 0x1F)) << 5);
                break;
            case 2:
                tileNumber = tileNumber | ((yTile & 0x3F) << 5);
                break;
            default:
                tileNumber = tileNumber | (((xTile & 0x20) | (yTile & 0x1F)) << 5) | ((yTile & 0x20) << 6);
        }
        return tileNumber;
    }

    private void process4BitVRAM(int chrData, int yOffset) {
        int address = (chrData & 0x3FF) << 3;
        address = address + this.BGCharacterBaseBlock;
        if ((chrData & 0x800) == 0) {
            address = address + yOffset;
        } else {
            address = address + 7 - yOffset;
        }
        this.render4BitVRAM(chrData >> 8, address);
    }

    private void render4BitVRAM(int chrData, int address) {
        if (address < 0x4000) {
            int paletteOffset = chrData & 0xF0;
            int data = this.VRAM32.get(address);
            if ((chrData & 0x4) == 0) {
                this.tileFetched[0] = this.palette16[paletteOffset | (data & 0xF)] | this.priorityFlag;
                this.tileFetched[1] = this.palette16[paletteOffset | ((data >> 4) & 0xF)] | this.priorityFlag;
                this.tileFetched[2] = this.palette16[paletteOffset | ((data >> 8) & 0xF)] | this.priorityFlag;
                this.tileFetched[3] = this.palette16[paletteOffset | ((data >> 12) & 0xF)] | this.priorityFlag;
                this.tileFetched[4] = this.palette16[paletteOffset | ((data >> 16) & 0xF)] | this.priorityFlag;
                this.tileFetched[5] = this.palette16[paletteOffset | ((data >> 20) & 0xF)] | this.priorityFlag;
                this.tileFetched[6] = this.palette16[paletteOffset | ((data >> 24) & 0xF)] | this.priorityFlag;
                this.tileFetched[7] = this.palette16[paletteOffset | (data >>> 28)] | this.priorityFlag;
            } else {
                this.tileFetched[0] = this.palette16[paletteOffset | (data >>> 28)] | this.priorityFlag;
                this.tileFetched[1] = this.palette16[paletteOffset | ((data >> 24) & 0xF)] | this.priorityFlag;
                this.tileFetched[2] = this.palette16[paletteOffset | ((data >> 20) & 0xF)] | this.priorityFlag;
                this.tileFetched[3] = this.palette16[paletteOffset | ((data >> 16) & 0xF)] | this.priorityFlag;
                this.tileFetched[4] = this.palette16[paletteOffset | ((data >> 12) & 0xF)] | this.priorityFlag;
                this.tileFetched[5] = this.palette16[paletteOffset | ((data >> 8) & 0xF)] | this.priorityFlag;
                this.tileFetched[6] = this.palette16[paletteOffset | ((data >> 4) & 0xF)] | this.priorityFlag;
                this.tileFetched[7] = this.palette16[paletteOffset | (data & 0xF)] | this.priorityFlag;
            }
        } else {
            this.addressInvalidRender();
        }
    }

    private void process8BitVRAM(int chrData, int yOffset) {
        int address = (chrData & 0x3FF) << 4;
        address = address + this.BGCharacterBaseBlock;
        switch (chrData & 0xC00) {
            case 0:
                address = address + (yOffset << 1);
                this.render8BitVRAMNormal(address);
                break;
            case 0x400:
                address = address + (yOffset << 1);
                this.render8BitVRAMFlipped(address);
                break;
            case 0x800:
                address = address + 14 - (yOffset << 1);
                this.render8BitVRAMNormal(address);
                break;
            default:
                address = address + 14 - (yOffset << 1);
                this.render8BitVRAMFlipped(address);
        }
    }

    private void render8BitVRAMNormal(int address) {
        if (address < 0x4000) {
            int data = this.VRAM32.get(address);
            this.tileFetched[0] = this.palette256[data & 0xFF] | this.priorityFlag;
            this.tileFetched[1] = this.palette256[(data >> 8) & 0xFF] | this.priorityFlag;
            this.tileFetched[2] = this.palette256[(data >> 16) & 0xFF] | this.priorityFlag;
            this.tileFetched[3] = this.palette256[data >>> 24] | this.priorityFlag;
            data = this.VRAM32.get(address + 1);
            this.tileFetched[4] = this.palette256[data & 0xFF] | this.priorityFlag;
            this.tileFetched[5] = this.palette256[(data >> 8) & 0xFF] | this.priorityFlag;
            this.tileFetched[6] = this.palette256[(data >> 16) & 0xFF] | this.priorityFlag;
            this.tileFetched[7] = this.palette256[data >>> 24] | this.priorityFlag;
        } else {
            this.addressInvalidRender();
        }
    }

    private void render8BitVRAMFlipped(int address) {
        if (address < 0x4000) {
            int data = this.VRAM32.get(address);
            this.tileFetched[4] = this.palette256[data >>> 24] | this.priorityFlag;
            this.tileFetched[5] = this.palette256[(data >> 16) & 0xFF] | this.priorityFlag;
            this.tileFetched[6] = this.palette256[(data >> 8) & 0xFF] | this.priorityFlag;
            this.tileFetched[7] = this.palette256[data & 0xFF] | this.priorityFlag;
            data = this.VRAM32.get(address + 1);
            this.tileFetched[0] = this.palette256[data >>> 24] | this.priorityFlag;
            this.tileFetched[1] = this.palette256[(data >> 16) & 0xFF] | this.priorityFlag;
            this.tileFetched[2] = this.palette256[(data >> 8) & 0xFF] | this.priorityFlag;
            this.tileFetched[3] = this.palette256[data & 0xFF] | this.priorityFlag;
        } else {
            this.addressInvalidRender();
        }
    }

    private void addressInvalidRender() {
        int data = this.renderer.backdrop | this.priorityFlag;
        for (int i = 0; i < 8; ++i) {
            this.tileFetched[i] = data;
        }
    }

    public void writeBGCNT8_0(int data) {
        setMosaicEnable(data & 0x40);
        paletteModeSelect(data & 0x80);
        priorityPreprocess(data & 0x3);
        characterBaseBlockPreprocess((data & 0xC) >> 2);
    }

    public void writeBGCNT8_1(int data) {
        screenSizePreprocess((data & 0xC0) >> 6);
        screenBaseBlockPreprocess(data & 0x1F);
    }

    public void writeBGCNT16(int data) {
        setMosaicEnable(data & 0x40);
        paletteModeSelect(data & 0x80);
        priorityPreprocess(data & 0x3);
        characterBaseBlockPreprocess((data & 0xC) >> 2);
        screenSizePreprocess((data & 0xC000) >> 14);
        screenBaseBlockPreprocess((data >> 8) & 0x1F);
    }

    public void writeBGHOFS8_0(int data) {
        this.BGXCoord = (this.BGXCoord & 0x100) | data;
    }

    public void writeBGHOFS8_1(int data) {
        this.BGXCoord = (data << 8) | (this.BGXCoord & 0xFF);
    }

    public void writeBGHOFS16(int data) {
        this.BGXCoord = data;
    }

    public void writeBGVOFS8_0(int data) {
        this.BGYCoord = (this.BGYCoord & 0x100) | data;
    }

    public void writeBGVOFS8_1(int data) {
        this.BGYCoord = (data << 8) | (this.BGYCoord & 0xFF);
    }

    public void writeBGVOFS16(int data) {
        this.BGYCoord = data;
    }

    public void writeBGOFS32(int data) {
        this.BGXCoord = data & 0x1FF;
        this.BGYCoord = data >> 16;
    }

    private void setMosaicEnable(int doMosaic) {
        this.doMosaic = doMosaic;
    }

    private void paletteModeSelect(int do256) {
        this.do256 = do256;
    }

    private void screenSizePreprocess(int BGScreenSize) {
        this.tileMode = BGScreenSize;
    }

    private void priorityPreprocess(int BGPriority) {
        this.priorityFlag = (BGPriority << 23) | (1 << (this.bgNumber + 0x10));
    }

    private void screenBaseBlockPreprocess(int BGScreenBaseBlock) {
        this.BGScreenBaseBlock = BGScreenBaseBlock << 10;
    }

    private void characterBaseBlockPreprocess(int BGCharacterBaseBlock) {
        this.BGCharacterBaseBlock = BGCharacterBaseBlock << 12;
    }
}
