package com.iodine.gba.audio;

public class GameBoyAdvanceChannel1Synth {
    public GameBoyAdvanceSound sound;
    public int currentSampleLeft = 0;
    public int currentSampleRight = 0;
    public boolean SweepFault = false;
    public int lastTimeSweep = 0;
    public int timeSweep = 0;
    public int frequencySweepDivider = 0;
    public boolean decreaseSweep = false;
    public int nr10 = 0;
    public int nr11 = 0;
    public int CachedDuty = 0xF0000000;
    public int totalLength = 0x40;
    public int nr12 = 0;
    public int envelopeVolume = 0;
    public boolean envelopeType = false;
    public int frequency = 0;
    public int FrequencyTracker = 0x8000;
    public int nr14 = 0;
    public boolean consecutive = true;
    public int ShadowFrequency = 0x8000;
    public boolean canPlay = false;
    public int Enabled = 0;
    public int envelopeSweeps = 0;
    public int envelopeSweepsLast = -1;
    public int FrequencyCounter = 0;
    public int DutyTracker = 0;
    public boolean Swept = false;
    public int leftEnable = 0;
    public int rightEnable = 0;

    public GameBoyAdvanceChannel1Synth(GameBoyAdvanceSound sound) {
        this.sound = sound;
    }

    public void disabled() {
        this.nr10 = 0;
        this.SweepFault = false;
        this.lastTimeSweep = 0;
        this.timeSweep = 0;
        this.frequencySweepDivider = 0;
        this.decreaseSweep = false;
        this.nr11 = 0;
        this.CachedDuty = 0xF0000000;
        this.totalLength = 0x40;
        this.nr12 = 0;
        this.envelopeVolume = 0;
        this.frequency = 0;
        this.FrequencyTracker = 0x8000;
        this.nr14 = 0;
        this.consecutive = true;
        this.ShadowFrequency = 0x8000;
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
            this.sound.nr52 &= 0xFE;
        }
    }

    public void enableCheck() {
        if ((this.consecutive || this.totalLength > 0) && !this.SweepFault && this.canPlay) {
            this.Enabled = 0xF;
        } else {
            this.Enabled = 0;
        }
    }

    public void volumeEnableCheck() {
        this.canPlay = this.nr12 > 7;
        enableCheck();
    }

    public void outputLevelCache() {
        int duty = this.CachedDuty >> this.DutyTracker;
        int envelopeVolume = this.envelopeVolume & this.Enabled & duty;
        this.currentSampleLeft = this.leftEnable & envelopeVolume;
        this.currentSampleRight = this.rightEnable & envelopeVolume;
    }

    public void setChannelOutputEnable(int data) {
        this.rightEnable = (data << 31) >> 31;
        this.leftEnable = (data << 27) >> 31;
    }

    public void clockAudioSweep() {
        if (!this.SweepFault && this.timeSweep > 0) {
            this.timeSweep--;
            if (this.timeSweep == 0) {
                runAudioSweep();
            }
        }
    }

    public void runAudioSweep() {
        if (this.lastTimeSweep > 0) {
            if (this.frequencySweepDivider > 0) {
                this.Swept = true;
                if (this.decreaseSweep) {
                    this.ShadowFrequency -= this.ShadowFrequency >> this.frequencySweepDivider;
                    this.frequency = this.ShadowFrequency & 0x7FF;
                    this.FrequencyTracker = (0x800 - this.frequency) << 4;
                } else {
                    this.ShadowFrequency += this.ShadowFrequency >> this.frequencySweepDivider;
                    this.frequency = this.ShadowFrequency;
                    if (this.ShadowFrequency <= 0x7FF) {
                        this.FrequencyTracker = (0x800 - this.frequency) << 4;
                        if ((this.ShadowFrequency + (this.ShadowFrequency >> this.frequencySweepDivider)) > 0x7FF) {
                            this.SweepFault = true;
                            enableCheck();
                            this.sound.nr52 &= 0xFE;
                        }
                    } else {
                        this.frequency &= 0x7FF;
                        this.SweepFault = true;
                        enableCheck();
                        this.sound.nr52 &= 0xFE;
                    }
                }
                this.timeSweep = this.lastTimeSweep;
            } else {
                this.SweepFault = true;
                enableCheck();
            }
        }
    }

    public void audioSweepPerformDummy() {
        if (this.frequencySweepDivider > 0) {
            if (!this.decreaseSweep) {
                int channel1ShadowFrequency = this.ShadowFrequency + (this.ShadowFrequency >> this.frequencySweepDivider);
                if (channel1ShadowFrequency <= 0x7FF) {
                    if ((channel1ShadowFrequency + (channel1ShadowFrequency >> this.frequencySweepDivider)) > 0x7FF) {
                        this.SweepFault = true;
                        enableCheck();
                        this.sound.nr52 &= 0xFE;
                    }
                } else {
                    this.SweepFault = true;
                    enableCheck();
                    this.sound.nr52 &= 0xFE;
                }
            }
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

    public int readSOUND1CNT8_0() {
        return this.nr10;
    }

    public void writeSOUND1CNT8_0(int data) {
        if (this.decreaseSweep && (data & 0x08) == 0) {
            if (this.Swept) {
                this.SweepFault = true;
            }
        }
        this.lastTimeSweep = (data & 0x70) >> 4;
        this.frequencySweepDivider = data & 0x07;
        this.decreaseSweep = (data & 0x08) != 0;
        this.nr10 = data & 0xFF;
        enableCheck();
    }

    public int readSOUND1CNT8_2() {
        return this.nr11;
    }

    public void writeSOUND1CNT8_2(int data) {
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
        this.nr11 = data & 0xFF;
        enableCheck();
    }

    public int readSOUND1CNT8_3() {
        return this.nr12;
    }

    public void writeSOUND1CNT8_3(int data) {
        this.envelopeType = (data & 0x08) != 0;
        this.nr12 = data & 0xFF;
        volumeEnableCheck();
    }

    public void writeSOUND1CNT_X0(int data) {
        this.frequency = (this.frequency & 0x700) | (data & 0xFF);
        this.FrequencyTracker = (0x800 - this.frequency) << 4;
    }

    public int readSOUND1CNTX8() {
        return this.nr14;
    }

    public void writeSOUND1CNT_X1(int data) {
        this.consecutive = (data & 0x40) == 0;
        this.frequency = ((data & 0x7) << 8) | (this.frequency & 0xFF);
        this.FrequencyTracker = (0x800 - this.frequency) << 4;
        if ((data & 0x80) != 0) {
            this.timeSweep = this.lastTimeSweep;
            this.Swept = false;
            this.envelopeVolume = this.nr12 >> 4;
            this.envelopeSweepsLast = (this.nr12 & 0x7) - 1;
            if (this.totalLength == 0) {
                this.totalLength = 0x40;
            }
            if (this.lastTimeSweep > 0 || this.frequencySweepDivider > 0) {
                this.sound.nr52 |= 0x1;
            } else {
                this.sound.nr52 &= 0xFE;
            }
            if ((data & 0x40) != 0) {
                this.sound.nr52 |= 0x1;
            }
            this.ShadowFrequency = this.frequency;
            this.SweepFault = false;
            audioSweepPerformDummy();
        }
        enableCheck();
        this.nr14 = data & 0xFF;
    }
}
