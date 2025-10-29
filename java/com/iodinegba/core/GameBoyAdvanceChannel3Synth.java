package com.iodinegba.core;

import com.iodinegba.utils.TypedArrays;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public class GameBoyAdvanceChannel3Synth {
    private Sound sound;
    public int currentSampleLeft = 0;
    public int currentSampleRight = 0;
    private int lastSampleLookup = 0;
    private boolean canPlay = false;
    private int WAVERAMBankSpecified = 0;
    private int WAVERAMBankAccessed = 0x20;
    private int WaveRAMBankSize = 0x1F;
    private int totalLength = 0x100;
    private int patternType = 4;
    private int frequency = 0;
    private int FrequencyPeriod = 0x4000;
    private boolean consecutive = true;
    private int Enabled = 0;
    private int leftEnable = 0;
    private int rightEnable = 0;
    private int nr30 = 0;
    private int nr32 = 0;
    private int nr34 = 0;
    private int cachedSample = 0;
    private ByteBuffer PCM = TypedArrays.getUint8Array(0x40);
    private ShortBuffer PCM16 = TypedArrays.getUint16Array(0x20);
    private IntBuffer PCM32 = TypedArrays.getInt32Array(0x10);
    private ByteBuffer WAVERAM8 = TypedArrays.getUint8Array(0x20);
    private ShortBuffer WAVERAM16 = TypedArrays.getUint16Array(0x10);
    private IntBuffer WAVERAM32 = TypedArrays.getInt32Array(0x8);
    public int counter = 0;

    public GameBoyAdvanceChannel3Synth(Sound sound) {
        this.sound = sound;
    }

    public void disabled() {
        this.nr30 = 0;
        this.lastSampleLookup = 0;
        this.canPlay = false;
        this.WAVERAMBankSpecified = 0;
        this.WAVERAMBankAccessed = 0x20;
        this.WaveRAMBankSize = 0x1F;
        this.totalLength = 0x100;
        this.nr32 = 0;
        this.patternType = 4;
        this.frequency = 0;
        this.FrequencyPeriod = 0x4000;
        this.nr34 = 0;
        this.consecutive = true;
        this.Enabled = 0;
        this.counter = 0;
    }

    public void updateCache() {
        if (patternType != 3) {
            cachedSample = PCM.get(lastSampleLookup) >> patternType;
        } else {
            cachedSample = com.iodinegba.utils.Math.imul(PCM.get(lastSampleLookup), 3) >> 2;
        }
        outputLevelCache();
    }

    public void outputLevelCache() {
        int cachedSample = this.cachedSample & Enabled;
        currentSampleLeft = leftEnable & cachedSample;
        currentSampleRight = rightEnable & cachedSample;
    }

    public void setChannelOutputEnable(int data) {
        rightEnable = (data << 29) >> 31;
        leftEnable = (data << 25) >> 31;
    }

    public int readWAVE8(int address) {
        address += WAVERAMBankAccessed >> 1;
        return WAVERAM8.get(address) & 0xFF;
    }

    public void writeWAVE8(int address, int data) {
        address += WAVERAMBankAccessed >> 1;
        WAVERAM8.put(address, (byte) (data & 0xFF));
        address <<= 1;
        PCM.put(address, (byte) ((data >> 4) & 0xF));
        PCM.put(address | 1, (byte) (data & 0xF));
    }

    public void enableCheck() {
        if ((consecutive || totalLength > 0)) {
            Enabled = 0xF;
        } else {
            Enabled = 0;
        }
    }

    public void clockAudioLength() {
        if (totalLength > 1) {
            --totalLength;
        } else if (totalLength == 1) {
            totalLength = 0;
            enableCheck();
            sound.unsetNR52(0xFB);
        }
    }

    public void computeAudioChannel() {
        if (counter == 0) {
            if (canPlay) {
                lastSampleLookup = (lastSampleLookup + 1) & WaveRAMBankSize | WAVERAMBankSpecified;
            }
            counter = FrequencyPeriod;
        }
    }

    public int readSOUND3CNT_L() {
        return nr30;
    }

    public void writeSOUND3CNT_L(int data) {
        if (!canPlay && (data & 0x80) != 0) {
            lastSampleLookup = 0;
        }
        canPlay = (data & 0x80) != 0;
        WaveRAMBankSize = (data & 0x20) | 0x1F;
        WAVERAMBankSpecified = ((data & 0x40) >> 1) ^ (data & 0x20);
        WAVERAMBankAccessed = ((data & 0x40) >> 1) ^ 0x20;
        if (canPlay && (nr30 & 0x80) != 0 && !consecutive) {
            sound.setNR52(0x4);
        }
        nr30 = data & 0xFF;
    }

    public void writeSOUND3CNT_H0(int data) {
        totalLength = 0x100 - (data & 0xFF);
        enableCheck();
    }

    public int readSOUND3CNT_H() {
        return nr32;
    }

    public void writeSOUND3CNT_H1(int data) {
        switch (data >> 5) {
            case 0:
                patternType = 4;
                break;
            case 1:
                patternType = 0;
                break;
            case 2:
                patternType = 1;
                break;
            case 3:
                patternType = 2;
                break;
            default:
                patternType = 3;
        }
        nr32 = data & 0xFF;
    }

    public void writeSOUND3CNT_X0(int data) {
        frequency = (frequency & 0x700) | (data & 0xFF);
        FrequencyPeriod = (0x800 - frequency) << 3;
    }

    public int readSOUND3CNT_X() {
        return nr34;
    }

    public void writeSOUND3CNT_X1(int data) {
        if ((data & 0x80) != 0) {
            if (totalLength == 0) {
                totalLength = 0x100;
            }
            lastSampleLookup = 0;
            if ((data & 0x40) != 0) {
                sound.setNR52(0x4);
            }
        }
        consecutive = (data & 0x40) == 0x0;
        frequency = ((data & 0x7) << 8) | (frequency & 0xFF);
        FrequencyPeriod = (0x800 - frequency) << 3;
        enableCheck();
        nr34 = data & 0xFF;
    }
}
