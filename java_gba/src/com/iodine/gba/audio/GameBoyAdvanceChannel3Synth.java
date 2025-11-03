package com.iodine.gba.audio;

public class GameBoyAdvanceChannel3Synth {
    public GameBoyAdvanceSound sound;
    public int currentSampleLeft = 0;
    public int currentSampleRight = 0;
    public int lastSampleLookup = 0;
    public boolean canPlay = false;
    public int WAVERAMBankSpecified = 0;
    public int WAVERAMBankAccessed = 0x20;
    public int WaveRAMBankSize = 0x1F;
    public int totalLength = 0x100;
    public int patternType = 4;
    public int frequency = 0;
    public int FrequencyPeriod = 0x4000;
    public boolean consecutive = true;
    public int Enabled = 0;
    public int leftEnable = 0;
    public int rightEnable = 0;
    public int nr30 = 0;
    public int nr31 = 0;
    public int nr32 = 0;
    public int nr33 = 0;
    public int nr34 = 0;
    public int cachedSample = 0;
    public byte[] PCM = new byte[0x40];
    public byte[] WAVERAM8 = new byte[0x20];
    public int counter = 0;

    public GameBoyAdvanceChannel3Synth(GameBoyAdvanceSound sound) {
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
        this.nr33 = 0;
        this.frequency = 0;
        this.FrequencyPeriod = 0x4000;
        this.nr34 = 0;
        this.consecutive = true;
        this.Enabled = 0;
        this.counter = 0;
    }

    public void updateCache() {
        if (this.patternType != 3) {
            this.cachedSample = this.PCM[this.lastSampleLookup] >> this.patternType;
        } else {
            this.cachedSample = (this.PCM[this.lastSampleLookup] * 3) >> 2;
        }
        outputLevelCache();
    }

    public void outputLevelCache() {
        int cachedSample = this.cachedSample & this.Enabled;
        this.currentSampleLeft = this.leftEnable & cachedSample;
        this.currentSampleRight = this.rightEnable & cachedSample;
    }

    public void setChannelOutputEnable(int data) {
        this.rightEnable = (data << 29) >> 31;
        this.leftEnable = (data << 25) >> 31;
    }

    public int readWAVE8(int address) {
        address = (address + (this.WAVERAMBankAccessed >> 1));
        return this.WAVERAM8[address] & 0xFF;
    }

    public void writeWAVE8(int address, int data) {
        address += this.WAVERAMBankAccessed >> 1;
        this.WAVERAM8[address] = (byte)(data & 0xFF);
        address <<= 1;
        this.PCM[address] = (byte)((data >> 4) & 0xF);
        this.PCM[address | 1] = (byte)(data & 0xF);
    }

    public void enableCheck() {
        if (this.consecutive || this.totalLength > 0) {
            this.Enabled = 0xF;
        } else {
            this.Enabled = 0;
        }
    }

    public void clockAudioLength() {
        if (this.totalLength > 1) {
            this.totalLength--;
        } else if (this.totalLength == 1) {
            this.totalLength = 0;
            enableCheck();
            this.sound.nr52 &= 0xFB;
        }
    }

    public void computeAudioChannel() {
        if (this.counter == 0) {
            if (this.canPlay) {
                this.lastSampleLookup = ((this.lastSampleLookup + 1) & this.WaveRAMBankSize) | this.WAVERAMBankSpecified;
            }
            this.counter = this.FrequencyPeriod;
        }
    }

    public int readSOUND3CNT_L() {
        return this.nr30;
    }

    public void writeSOUND3CNT_L(int data) {
        if (!this.canPlay && (data & 0x80) != 0) {
            this.lastSampleLookup = 0;
        }
        this.canPlay = (data & 0x80) != 0;
        this.WaveRAMBankSize = (data & 0x20) | 0x1F;
        this.WAVERAMBankSpecified = ((data & 0x40) >> 1) ^ (data & 0x20);
        this.WAVERAMBankAccessed = ((data & 0x40) >> 1) ^ 0x20;
        if (this.canPlay && (this.nr30 & 0x80) != 0 && !this.consecutive) {
            this.sound.nr52 |= 0x4;
        }
        this.nr30 = data & 0xFF;
    }

    public void writeSOUND3CNT_H0(int data) {
        this.totalLength = 0x100 - (data & 0xFF);
        enableCheck();
    }

    public int readSOUND3CNT_H() {
        return this.nr32;
    }

    public void writeSOUND3CNT_H1(int data) {
        data &= 0xFF;
        switch (data >> 5) {
            case 0:
                this.patternType = 4;
                break;
            case 1:
                this.patternType = 0;
                break;
            case 2:
                this.patternType = 1;
                break;
            case 3:
                this.patternType = 2;
                break;
            default:
                this.patternType = 3;
        }
        this.nr32 = data;
    }

    public void writeSOUND3CNT_X0(int data) {
        this.frequency = (this.frequency & 0x700) | (data & 0xFF);
        this.FrequencyPeriod = (0x800 - this.frequency) << 3;
    }

    public int readSOUND3CNT_X() {
        return this.nr34;
    }

    public void writeSOUND3CNT_X1(int data) {
        if ((data & 0x80) != 0) {
            if (this.totalLength == 0) {
                this.totalLength = 0x100;
            }
            this.lastSampleLookup = 0;
            if ((data & 0x40) != 0) {
                this.sound.nr52 |= 0x4;
            }
        }
        this.consecutive = (data & 0x40) == 0;
        this.frequency = ((data & 0x7) << 8) | (this.frequency & 0xFF);
        this.FrequencyPeriod = (0x800 - this.frequency) << 3;
        enableCheck();
        this.nr34 = data & 0xFF;
    }
}
