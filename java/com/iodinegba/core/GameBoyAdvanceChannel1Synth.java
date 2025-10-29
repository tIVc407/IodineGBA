package com.iodinegba.core;

public class GameBoyAdvanceChannel1Synth {
    private Sound sound;
    public int currentSampleLeft = 0;
    public int currentSampleRight = 0;
    private boolean SweepFault = false;
    private int lastTimeSweep = 0;
    private int timeSweep = 0;
    private int frequencySweepDivider = 0;
    private boolean decreaseSweep = false;
    private int nr11 = 0;
    private int CachedDuty = 0xF0000000;
    private int totalLength = 0x40;
    private int nr12 = 0;
    private int envelopeVolume = 0;
    private int frequency = 0;
    public int FrequencyTracker = 0x8000;
    private int nr14 = 0;
    private boolean consecutive = true;
    private int ShadowFrequency = 0x8000;
    private boolean canPlay = false;
    private int Enabled = 0;
    private int envelopeSweeps = 0;
    private int envelopeSweepsLast = -1;
    public int FrequencyCounter = 0;
    private int DutyTracker = 0;
    private boolean Swept = false;
    private int leftEnable = 0;
    private int rightEnable = 0;
    private boolean envelopeType = false;
    private int nr10 = 0;

    public GameBoyAdvanceChannel1Synth(Sound sound) {
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
        if (totalLength > 1) {
            --totalLength;
        } else if (totalLength == 1) {
            totalLength = 0;
            enableCheck();
            sound.unsetNR52(0xFE);
        }
    }

    public void enableCheck() {
        if ((consecutive || totalLength > 0) && !SweepFault && canPlay) {
            Enabled = 0xF;
        } else {
            Enabled = 0;
        }
    }

    public void volumeEnableCheck() {
        canPlay = (nr12 > 7);
        enableCheck();
    }

    public void outputLevelCache() {
        int duty = CachedDuty >> DutyTracker;
        int envelopeVolume = this.envelopeVolume & Enabled & duty;
        currentSampleLeft = leftEnable & envelopeVolume;
        currentSampleRight = rightEnable & envelopeVolume;
    }

    public void setChannelOutputEnable(int data) {
        rightEnable = (data << 31) >> 31;
        leftEnable = (data << 27) >> 31;
    }

    public void clockAudioSweep() {
        if (!SweepFault && timeSweep > 0) {
            if (--timeSweep == 0) {
                runAudioSweep();
            }
        }
    }

    public void runAudioSweep() {
        if (lastTimeSweep > 0) {
            if (frequencySweepDivider > 0) {
                Swept = true;
                if (decreaseSweep) {
                    ShadowFrequency -= ShadowFrequency >> frequencySweepDivider;
                    frequency = ShadowFrequency & 0x7FF;
                    FrequencyTracker = (0x800 - frequency) << 4;
                } else {
                    ShadowFrequency += ShadowFrequency >> frequencySweepDivider;
                    frequency = ShadowFrequency;
                    if (ShadowFrequency <= 0x7FF) {
                        FrequencyTracker = (0x800 - frequency) << 4;
                        if ((ShadowFrequency + (ShadowFrequency >> frequencySweepDivider)) > 0x7FF) {
                            SweepFault = true;
                            enableCheck();
                            sound.unsetNR52(0xFE);
                        }
                    } else {
                        frequency &= 0x7FF;
                        SweepFault = true;
                        enableCheck();
                        sound.unsetNR52(0xFE);
                    }
                }
                timeSweep = lastTimeSweep;
            } else {
                SweepFault = true;
                enableCheck();
            }
        }
    }

    public void audioSweepPerformDummy() {
        if (frequencySweepDivider > 0) {
            if (!decreaseSweep) {
                int shadowFrequency = ShadowFrequency + (ShadowFrequency >> frequencySweepDivider);
                if (shadowFrequency <= 0x7FF) {
                    if ((shadowFrequency + (shadowFrequency >> frequencySweepDivider)) > 0x7FF) {
                        SweepFault = true;
                        enableCheck();
                        sound.unsetNR52(0xFE);
                    }
                } else {
                    SweepFault = true;
                    enableCheck();
                    sound.unsetNR52(0xFE);
                }
            }
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
                        envelopeSweeps = envelopeSweepsLast;
                    } else {
                        envelopeSweepsLast = -1;
                    }
                } else if (envelopeVolume < 0xF) {
                    ++envelopeVolume;
                    envelopeSweeps = envelopeSweepsLast;
                } else {
                    envelopeSweepsLast = -1;
                }
            }
        }
    }

    public void computeAudioChannel() {
        if (FrequencyCounter == 0) {
            FrequencyCounter = FrequencyTracker;
            DutyTracker = (DutyTracker + 4) & 0x1C;
        }
    }

    public int readSOUND1CNT8_0() {
        return nr10;
    }

    public void writeSOUND1CNT8_0(int data) {
        if (decreaseSweep && (data & 0x08) == 0) {
            if (Swept) {
                SweepFault = true;
            }
        }
        lastTimeSweep = (data & 0x70) >> 4;
        frequencySweepDivider = data & 0x07;
        decreaseSweep = (data & 0x08) != 0;
        nr10 = data & 0xFF;
        enableCheck();
    }

    public int readSOUND1CNT8_2() {
        return nr11;
    }

    public void writeSOUND1CNT8_2(int data) {
        switch ((data >> 6) & 0x3) {
            case 0:
                CachedDuty = 0xF0000000;
                break;
            case 1:
                CachedDuty = 0xF000000F;
                break;
            case 2:
                CachedDuty = 0xFFF0000F;
                break;
            default:
                CachedDuty = 0x0FFFFFF0;
        }
        totalLength = 0x40 - (data & 0x3F);
        nr11 = data & 0xFF;
        enableCheck();
    }

    public int readSOUND1CNT8_3() {
        return nr12;
    }

    public void writeSOUND1CNT8_3(int data) {
        envelopeType = (data & 0x08) != 0;
        nr12 = data & 0xFF;
        volumeEnableCheck();
    }

    public void writeSOUND1CNT_X0(int data) {
        frequency = (frequency & 0x700) | (data & 0xFF);
        FrequencyTracker = (0x800 - frequency) << 4;
    }

    public int readSOUND1CNTX8() {
        return nr14;
    }

    public void writeSOUND1CNT_X1(int data) {
        consecutive = (data & 0x40) == 0;
        frequency = ((data & 0x7) << 8) | (frequency & 0xFF);
        FrequencyTracker = (0x800 - frequency) << 4;
        if ((data & 0x80) != 0) {
            timeSweep = lastTimeSweep;
            Swept = false;
            envelopeVolume = nr12 >> 4;
            envelopeSweepsLast = (nr12 & 0x7) - 1;
            if (totalLength == 0) {
                totalLength = 0x40;
            }
            if (lastTimeSweep > 0 || frequencySweepDivider > 0) {
                sound.setNR52(0x1);
            } else {
                sound.unsetNR52(0xFE);
            }
            if ((data & 0x40) != 0) {
                sound.setNR52(0x1);
            }
            ShadowFrequency = frequency;
            SweepFault = false;
            audioSweepPerformDummy();
        }
        enableCheck();
        nr14 = data & 0xFF;
    }
}
