package com.iodinegba.core;

public class GameBoyAdvanceChannel2Synth {
    private Sound sound;
    public int currentSampleLeft = 0;
    public int currentSampleRight = 0;
    private int CachedDuty = 0xF0000000;
    private int totalLength = 0x40;
    private int envelopeVolume = 0;
    private int frequency = 0;
    public int FrequencyTracker = 0x8000;
    private boolean consecutive = true;
    private boolean canPlay = false;
    private int Enabled = 0;
    private int envelopeSweeps = 0;
    private int envelopeSweepsLast = -1;
    public int FrequencyCounter = 0;
    private int DutyTracker = 0;
    private int leftEnable = 0;
    private int rightEnable = 0;
    private int nr21 = 0;
    private int nr22 = 0;
    private int nr24 = 0;
    private boolean envelopeType = false;

    public GameBoyAdvanceChannel2Synth(Sound sound) {
        this.sound = sound;
    }

    public void disabled() {
        this.nr21 = 0;
        this.CachedDuty = 0xF0000000;
        this.totalLength = 0x40;
        this.nr22 = 0;
        this.envelopeVolume = 0;
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
        if (totalLength > 1) {
            --totalLength;
        } else if (totalLength == 1) {
            totalLength = 0;
            enableCheck();
            sound.unsetNR52(0xFD);
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

    public void enableCheck() {
        if ((consecutive || totalLength > 0) && canPlay) {
            Enabled = 0xF;
        } else {
            Enabled = 0;
        }
    }

    public void volumeEnableCheck() {
        canPlay = (nr22 > 7);
        enableCheck();
    }

    public void outputLevelCache() {
        int duty = CachedDuty >> DutyTracker;
        int envelopeVolume = this.envelopeVolume & Enabled & duty;
        currentSampleLeft = leftEnable & envelopeVolume;
        currentSampleRight = rightEnable & envelopeVolume;
    }

    public void setChannelOutputEnable(int data) {
        rightEnable = (data << 30) >> 31;
        leftEnable = (data << 26) >> 31;
    }

    public int readSOUND2CNT_L0() {
        return nr21;
    }

    public void writeSOUND2CNT_L0(int data) {
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
        nr21 = data & 0xFF;
        enableCheck();
    }

    public int readSOUND2CNT_L1() {
        return nr22;
    }

    public void writeSOUND2CNT_L1(int data) {
        envelopeType = (data & 0x08) != 0;
        nr22 = data & 0xFF;
        volumeEnableCheck();
    }

    public void writeSOUND2CNT_H0(int data) {
        frequency = (frequency & 0x700) | (data & 0xFF);
        FrequencyTracker = (0x800 - frequency) << 4;
    }

    public int readSOUND2CNT_H() {
        return nr24;
    }

    public void writeSOUND2CNT_H1(int data) {
        if ((data & 0x80) != 0) {
            envelopeVolume = nr22 >> 4;
            envelopeSweepsLast = (nr22 & 0x7) - 1;
            if (totalLength == 0) {
                totalLength = 0x40;
            }
            if ((data & 0x40) != 0) {
                sound.setNR52(0x2);
            }
        }
        consecutive = (data & 0x40) == 0x0;
        frequency = ((data & 0x7) << 8) | (frequency & 0xFF);
        FrequencyTracker = (0x800 - frequency) << 4;
        nr24 = data & 0xFF;
        enableCheck();
    }
}
