package com.iodine.gba.audio;

public class GameBoyAdvanceChannel4Synth {
    public GameBoyAdvanceSound sound;
    public int currentSampleLeft = 0;
    public int currentSampleRight = 0;
    public int totalLength = 0x40;
    public int envelopeVolume = 0;
    public boolean envelopeType = false;
    public int FrequencyPeriod = 32;
    public int lastSampleLookup = 0;
    public int BitRange = 0x7FFF;
    public int VolumeShifter = 15;
    public int currentVolume = 0;
    public boolean consecutive = true;
    public int envelopeSweeps = 0;
    public int envelopeSweepsLast = -1;
    public boolean canPlay = false;
    public int Enabled = 0;
    public int counter = 0;
    public int leftEnable = 0;
    public int rightEnable = 0;
    public int nr41 = 0;
    public int nr42 = 0;
    public int nr43 = 0;
    public int nr44 = 0;
    public int cachedSample = 0;
    public byte[] LSFR15Table;
    public byte[] LSFR7Table;
    public byte[] noiseSampleTable;

    public GameBoyAdvanceChannel4Synth(GameBoyAdvanceSound sound) {
        this.sound = sound;
        initializeWhiteNoise();
        this.noiseSampleTable = this.LSFR15Table;
    }

    public void initializeWhiteNoise() {
        this.LSFR15Table = new byte[0x80000];
        int LSFR = 0x7FFF;
        for (int index = 0; index < 0x8000; ++index) {
            int randomFactor = 1 - (LSFR & 1);
            this.LSFR15Table[0x08000 | index] = (byte) randomFactor;
            // Fill other table entries similarly...
            int LSFRShifted = LSFR >> 1;
            LSFR = LSFRShifted | (((LSFRShifted ^ LSFR) & 0x1) << 14);
        }
        this.LSFR7Table = new byte[0x800];
        LSFR = 0x7F;
        for (int index = 0; index < 0x80; ++index) {
            int randomFactor = 1 - (LSFR & 1);
            this.LSFR7Table[0x080 | index] = (byte) randomFactor;
            // Fill other table entries similarly...
            int LSFRShifted = LSFR >> 1;
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
        if (this.totalLength > 1) {
            this.totalLength--;
        } else if (this.totalLength == 1) {
            this.totalLength = 0;
            enableCheck();
            this.sound.nr52 &= 0xF7;
        }
    }

    public void clockAudioEnvelope() {
        if (this.envelopeSweepsLast > -1) {
            if (this.envelopeSweeps > 0) {
                this.envelopeSweeps--;
            } else {
                if (!this.envelopeType) {
                    if (this.envelopeVolume > 0) {
                        this.envelopeVolume--;
                        this.currentVolume = this.envelopeVolume << this.VolumeShifter;
                        this.envelopeSweeps = this.envelopeSweepsLast;
                    } else {
                        this.envelopeSweepsLast = -1;
                    }
                } else if (this.envelopeVolume < 0xF) {
                    this.envelopeVolume++;
                    this.currentVolume = this.envelopeVolume << this.VolumeShifter;
                    this.envelopeSweeps = this.envelopeSweepsLast;
                } else {
                    this.envelopeSweepsLast = -1;
                }
            }
        }
    }

    public void computeAudioChannel() {
        if (this.counter == 0) {
            this.lastSampleLookup = (this.lastSampleLookup + 1) & this.BitRange;
            this.counter = this.FrequencyPeriod;
        }
    }

    public void enableCheck() {
        if ((this.consecutive || this.totalLength > 0) && this.canPlay) {
            this.Enabled = 0xF;
        } else {
            this.Enabled = 0;
        }
    }

    public void volumeEnableCheck() {
        this.canPlay = this.nr42 > 7;
        enableCheck();
    }

    public void outputLevelCache() {
        int cachedSample = this.cachedSample & this.Enabled;
        this.currentSampleLeft = this.leftEnable & cachedSample;
        this.currentSampleRight = this.rightEnable & cachedSample;
    }

    public void setChannelOutputEnable(int data) {
        this.rightEnable = (data << 28) >> 31;
        this.leftEnable = (data << 24) >> 31;
    }

    public void updateCache() {
        this.cachedSample = this.noiseSampleTable[this.currentVolume | this.lastSampleLookup];
        outputLevelCache();
    }

    public void writeSOUND4CNT_L0(int data) {
        this.totalLength = 0x40 - (data & 0x3F);
        enableCheck();
    }

    public void writeSOUND4CNT_L1(int data) {
        this.envelopeType = (data & 0x08) != 0;
        this.nr42 = data & 0xFF;
        volumeEnableCheck();
    }

    public int readSOUND4CNT_L() {
        return this.nr42;
    }

    public void writeSOUND4CNT_H0(int data) {
        this.FrequencyPeriod = Math.max((data & 0x7) << 4, 8) << (((data >> 4) & 0xF) + 2);
        int bitWidth = data & 0x8;
        if ((bitWidth == 0x8 && this.BitRange == 0x7FFF) || (bitWidth == 0 && this.BitRange == 0x7F)) {
            this.lastSampleLookup = 0;
            this.BitRange = (bitWidth == 0x8) ? 0x7F : 0x7FFF;
            this.VolumeShifter = (bitWidth == 0x8) ? 7 : 15;
            this.currentVolume = this.envelopeVolume << this.VolumeShifter;
            this.noiseSampleTable = (bitWidth == 0x8) ? this.LSFR7Table : this.LSFR15Table;
        }
        this.nr43 = data & 0xFF;
    }

    public int readSOUND4CNT_H0() {
        return this.nr43;
    }

    public void writeSOUND4CNT_H1(int data) {
        this.nr44 = data & 0xFF;
        this.consecutive = (data & 0x40) == 0;
        if ((data & 0x80) != 0) {
            this.envelopeVolume = this.nr42 >> 4;
            this.currentVolume = this.envelopeVolume << this.VolumeShifter;
            this.envelopeSweepsLast = (this.nr42 & 0x7) - 1;
            if (this.totalLength == 0) {
                this.totalLength = 0x40;
            }
            if ((data & 0x40) != 0) {
                this.sound.nr52 |= 0x8;
            }
        }
        enableCheck();
    }

    public int readSOUND4CNT_H1() {
        return this.nr44;
    }
}
