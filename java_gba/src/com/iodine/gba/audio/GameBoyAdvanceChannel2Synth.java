package com.iodine.gba.audio;

public class GameBoyAdvanceChannel2Synth {
    public GameBoyAdvanceSound sound;
    public int currentSampleLeft = 0;
    public int currentSampleRight = 0;
    public int CachedDuty = 0xF0000000;
    public int totalLength = 0x40;
    public int envelopeVolume = 0;
    public boolean envelopeType = false;
    public int frequency = 0;
    public int FrequencyTracker = 0x8000;
    public boolean consecutive = true;
    public int ShadowFrequency = 0x8000;
    public boolean canPlay = false;
    public int Enabled = 0;
    public int envelopeSweeps = 0;
    public int envelopeSweepsLast = -1;
    public int FrequencyCounter = 0;
    public int DutyTracker = 0;
    public int leftEnable = 0;
    public int rightEnable = 0;
    public int nr21 = 0;
    public int nr22 = 0;
    public int nr23 = 0;
    public int nr24 = 0;

    public GameBoyAdvanceChannel2Synth(GameBoyAdvanceSound sound) {
        this.sound = sound;
    }

    public void disabled() {
        this.nr21 = 0;
        this.CachedDuty = 0xF0000000;
        this.totalLength = 0x40;
        this.nr22 = 0;
        this.envelopeVolume = 0;
        this.nr23 = 0;
        this.frequency = 0;
        this.FrequencyTracker = 0x8000;
        this.nr24 = 0;
        this.consecutive = true;
        this.canPlay = false;
        this.Enabled = 0;
        this.envelopeSweeps = 0;
        this.envelopeSweepsLast = -1;
        this.FrequencyCounter = 0;
        this.DutyTracker = 0;
    }

    public void clockAudioLength() {
        if (this.totalLength > 1) {
            this.totalLength--;
        } else if (this.totalLength == 1) {
            this.totalLength = 0;
            enableCheck();
            this.sound.nr52 &= 0xFD;
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
                        this.envelopeSweeps = this.envelopeSweepsLast;
                    } else {
                        this.envelopeSweepsLast = -1;
                    }
                } else if (this.envelopeVolume < 0xF) {
                    this.envelopeVolume++;
                    this.envelopeSweeps = this.envelopeSweepsLast;
                } else {
                    this.envelopeSweepsLast = -1;
                }
            }
        }
    }

    public void computeAudioChannel() {
        if (this.FrequencyCounter == 0) {
            this.FrequencyCounter = this.FrequencyTracker;
            this.DutyTracker = (this.DutyTracker + 4) & 0x1C;
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
        this.canPlay = this.nr22 > 7;
        enableCheck();
    }

    public void outputLevelCache() {
        int duty = this.CachedDuty >> this.DutyTracker;
        int envelopeVolume = this.envelopeVolume & this.Enabled & duty;
        this.currentSampleLeft = this.leftEnable & envelopeVolume;
        this.currentSampleRight = this.rightEnable & envelopeVolume;
    }

    public void setChannelOutputEnable(int data) {
        this.rightEnable = (data << 30) >> 31;
        this.leftEnable = (data << 26) >> 31;
    }

    public int readSOUND2CNT_L0() {
        return this.nr21;
    }

    public void writeSOUND2CNT_L0(int data) {
        switch ((data >> 6) & 0x3) {
            case 0:
                this.CachedDuty = 0xF0000000;
                break;
            case 1:
                this.CachedDuty = 0xF000000F;
                break;
            case 2:
                this.CachedDuty = 0xFFF0000F;
                break;
            default:
                this.CachedDuty = 0x0FFFFFF0;
        }
        this.totalLength = 0x40 - (data & 0x3F);
        this.nr21 = data & 0xFF;
        enableCheck();
    }

    public int readSOUND2CNT_L1() {
        return this.nr22;
    }

    public void writeSOUND2CNT_L1(int data) {
        this.envelopeType = (data & 0x08) != 0;
        this.nr22 = data & 0xFF;
        volumeEnableCheck();
    }

    public void writeSOUND2CNT_H0(int data) {
        this.frequency = (this.frequency & 0x700) | (data & 0xFF);
        this.FrequencyTracker = (0x800 - this.frequency) << 4;
    }

    public int readSOUND2CNT_H() {
        return this.nr24;
    }

    public void writeSOUND2CNT_H1(int data) {
        if ((data & 0x80) != 0) {
            this.envelopeVolume = this.nr22 >> 4;
            this.envelopeSweepsLast = (this.nr22 & 0x7) - 1;
            if (this.totalLength == 0) {
                this.totalLength = 0x40;
            }
            if ((data & 0x40) != 0) {
                this.sound.nr52 |= 0x2;
            }
        }
        this.consecutive = (data & 0x40) == 0;
        this.frequency = ((data & 0x7) << 8) | (this.frequency & 0xFF);
        this.FrequencyTracker = (0x800 - this.frequency) << 4;
        this.nr24 = data & 0xFF;
        enableCheck();
    }
}
