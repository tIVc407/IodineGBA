package com.iodinegba.core;

import com.iodinegba.utils.TypedArrays;
import java.nio.ByteBuffer;

public class GameBoyAdvanceChannel4Synth {
    private Sound sound;
    public int currentSampleLeft = 0;
    public int currentSampleRight = 0;
    private int totalLength = 0x40;
    private int envelopeVolume = 0;
    private int FrequencyPeriod = 32;
    private int lastSampleLookup = 0;
    private int BitRange = 0x7FFF;
    private int VolumeShifter = 15;
    private int currentVolume = 0;
    private boolean consecutive = true;
    private int envelopeSweeps = 0;
    private int envelopeSweepsLast = -1;
    private boolean canPlay = false;
    private int Enabled = 0;
    public int counter = 0;
    private int leftEnable = 0;
    private int rightEnable = 0;
    private int nr42 = 0;
    private int nr43 = 0;
    private int nr44 = 0;
    private int cachedSample = 0;
    private ByteBuffer LSFR15Table;
    private ByteBuffer LSFR7Table;
    private ByteBuffer noiseSampleTable;
    private boolean envelopeType = false;

    public GameBoyAdvanceChannel4Synth(Sound sound) {
        this.sound = sound;
        this.intializeWhiteNoise();
        this.noiseSampleTable = this.LSFR15Table;
    }

    public void intializeWhiteNoise() {
        this.LSFR15Table = TypedArrays.getUint8Array(0x80000);
        int LSFR = 0x7FFF;
        int LSFRShifted = 0x3FFF;
        for (int index = 0; index < 0x8000; ++index) {
            int randomFactor = 1 - (LSFR & 1);
            this.LSFR15Table.put(0x08000 | index, (byte) randomFactor);
            this.LSFR15Table.put(0x10000 | index, (byte) (randomFactor * 0x2));
            this.LSFR15Table.put(0x18000 | index, (byte) (randomFactor * 0x3));
            this.LSFR15Table.put(0x20000 | index, (byte) (randomFactor * 0x4));
            this.LSFR15Table.put(0x28000 | index, (byte) (randomFactor * 0x5));
            this.LSFR15Table.put(0x30000 | index, (byte) (randomFactor * 0x6));
            this.LSFR15Table.put(0x38000 | index, (byte) (randomFactor * 0x7));
            this.LSFR15Table.put(0x40000 | index, (byte) (randomFactor * 0x8));
            this.LSFR15Table.put(0x48000 | index, (byte) (randomFactor * 0x9));
            this.LSFR15Table.put(0x50000 | index, (byte) (randomFactor * 0xA));
            this.LSFR15Table.put(0x58000 | index, (byte) (randomFactor * 0xB));
            this.LSFR15Table.put(0x60000 | index, (byte) (randomFactor * 0xC));
            this.LSFR15Table.put(0x68000 | index, (byte) (randomFactor * 0xD));
            this.LSFR15Table.put(0x70000 | index, (byte) (randomFactor * 0xE));
            this.LSFR15Table.put(0x78000 | index, (byte) (randomFactor * 0xF));
            LSFRShifted = LSFR >> 1;
            LSFR = LSFRShifted | (((LSFRShifted ^ LSFR) & 0x1) << 14);
        }
        this.LSFR7Table = TypedArrays.getUint8Array(0x800);
        LSFR = 0x7F;
        for (int index = 0; index < 0x80; ++index) {
            int randomFactor = 1 - (LSFR & 1);
            this.LSFR7Table.put(0x080 | index, (byte) randomFactor);
            this.LSFR7Table.put(0x100 | index, (byte) (randomFactor * 0x2));
            this.LSFR7Table.put(0x180 | index, (byte) (randomFactor * 0x3));
            this.LSFR7Table.put(0x200 | index, (byte) (randomFactor * 0x4));
            this.LSFR7Table.put(0x280 | index, (byte) (randomFactor * 0x5));
            this.LSFR7Table.put(0x300 | index, (byte) (randomFactor * 0x6));
            this.LSFR7Table.put(0x380 | index, (byte) (randomFactor * 0x7));
            this.LSFR7Table.put(0x400 | index, (byte) (randomFactor * 0x8));
            this.LSFR7Table.put(0x480 | index, (byte) (randomFactor * 0x9));
            this.LSFR7Table.put(0x500 | index, (byte) (randomFactor * 0xA));
            this.LSFR7Table.put(0x580 | index, (byte) (randomFactor * 0xB));
            this.LSFR7Table.put(0x600 | index, (byte) (randomFactor * 0xC));
            this.LSFR7Table.put(0x680 | index, (byte) (randomFactor * 0xD));
            this.LSFR7Table.put(0x700 | index, (byte) (randomFactor * 0xE));
            this.LSFR7Table.put(0x780 | index, (byte) (randomFactor * 0xF));
            LSFRShifted = LSFR >> 1;
            LSFR = LSFRShifted | (((LSFRShifted ^ LSFR) & 0x1) << 6);
        }
    }

    public void disabled() {
        this.totalLength = 0x40;
        this.nr42 = 0;
        this.envelopeVolume = 0;
        this.nr43 = 0;
        this.FrequencyPeriod = 32;
        this.lastSampleLookup = 0;
        this.BitRange = 0x7FFF;
        this.VolumeShifter = 15;
        this.currentVolume = 0;
        this.noiseSampleTable = this.LSFR15Table;
        this.nr44 = 0;
        this.consecutive = true;
        this.envelopeSweeps = 0;
        this.envelopeSweepsLast = -1;
        this.canPlay = false;
        this.Enabled = 0;
        this.counter = 0;
    }

    public void clockAudioLength() {
        if (totalLength > 1) {
            --totalLength;
        } else if (totalLength == 1) {
            totalLength = 0;
            enableCheck();
            sound.unsetNR52(0xF7);
        }
    }

    public void clockAudioEnvelope() {
        if (envelopeSweepsLast > -1) {
            if (envelopeSweeps > 0) {
                --envelopeSweeps;
            } else {
                if (!envelopeType) {
                    if (envelopeVolume > 0) {
                        --envelopeVolume;
                        currentVolume = envelopeVolume << VolumeShifter;
                        envelopeSweeps = envelopeSweepsLast;
                    } else {
                        envelopeSweepsLast = -1;
                    }
                } else if (envelopeVolume < 0xF) {
                    ++envelopeVolume;
                    currentVolume = envelopeVolume << VolumeShifter;
                    envelopeSweeps = envelopeSweepsLast;
                } else {
                    envelopeSweepsLast = -1;
                }
            }
        }
    }

    public void computeAudioChannel() {
        if (counter == 0) {
            lastSampleLookup = (lastSampleLookup + 1) & BitRange;
            counter = FrequencyPeriod;
        }
    }

    public void enableCheck() {
        if ((consecutive || totalLength > 0) && canPlay) {
            Enabled = 0xF;
        } else {
            Enabled = 0;
        }
    }

    public void volumeEnableCheck() {
        canPlay = (nr42 > 7);
        enableCheck();
    }

    public void outputLevelCache() {
        int cachedSample = this.cachedSample & Enabled;
        currentSampleLeft = leftEnable & cachedSample;
        currentSampleRight = rightEnable & cachedSample;
    }

    public void setChannelOutputEnable(int data) {
        rightEnable = (data << 28) >> 31;
        leftEnable = (data << 24) >> 31;
    }

    public void updateCache() {
        cachedSample = noiseSampleTable.get(currentVolume | lastSampleLookup);
        outputLevelCache();
    }

    public void writeSOUND4CNT_L0(int data) {
        totalLength = 0x40 - (data & 0x3F);
        enableCheck();
    }

    public void writeSOUND4CNT_L1(int data) {
        envelopeType = (data & 0x08) != 0;
        nr42 = data & 0xFF;
        volumeEnableCheck();
    }

    public int readSOUND4CNT_L() {
        return nr42;
    }

    public void writeSOUND4CNT_H0(int data) {
        FrequencyPeriod = Math.max((data & 0x7) << 4, 8) << ((data >> 4) & 0xF);
        int bitWidth = data & 0x8;
        if ((bitWidth == 0x8 && BitRange == 0x7FFF) || (bitWidth == 0 && BitRange == 0x7F)) {
            lastSampleLookup = 0;
            BitRange = (bitWidth == 0x8) ? 0x7F : 0x7FFF;
            VolumeShifter = (bitWidth == 0x8) ? 7 : 15;
            currentVolume = envelopeVolume << VolumeShifter;
            noiseSampleTable = (bitWidth == 0x8) ? LSFR7Table : LSFR15Table;
        }
        nr43 = data & 0xFF;
    }

    public int readSOUND4CNT_H0() {
        return nr43;
    }

    public void writeSOUND4CNT_H1(int data) {
        nr44 = data & 0xFF;
        consecutive = (data & 0x40) == 0x0;
        if ((data & 0x80) != 0) {
            envelopeVolume = nr42 >> 4;
            currentVolume = envelopeVolume << VolumeShifter;
            envelopeSweepsLast = (nr42 & 0x7) - 1;
            if (totalLength == 0) {
                totalLength = 0x40;
            }
            if ((data & 0x40) != 0) {
                sound.setNR52(0x8);
            }
        }
        enableCheck();
    }

    public int readSOUND4CNT_H1() {
        return nr44;
    }
}
