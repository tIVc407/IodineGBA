package com.iodine.gba.audio;

import com.iodine.gba.core.GameBoyAdvanceIO;
import com.iodine.gba.core.GameBoyAdvanceEmulator;
import com.iodine.gba.memory.GameBoyAdvanceDMA1;
import com.iodine.gba.memory.GameBoyAdvanceDMA2;

public class GameBoyAdvanceSound {
    public GameBoyAdvanceIO IOCore;
    public GameBoyAdvanceEmulator coreExposed;
    private GameBoyAdvanceDMA1 dmaChannel1;
    private GameBoyAdvanceDMA2 dmaChannel2;
    public int audioTicks = 0;
    public int audioIndex = 0;
    public int downsampleInputLeft = 0;
    public int downsampleInputRight = 0;
    public int audioResamplerFirstPassFactor = 380;
    public int nr60 = 0;
    public int nr61 = 0;
    public int nr62 = 0;
    public int nr63 = 0;
    public boolean soundMasterEnabled = false;
    public int mixerSoundBIAS = 0;
    public GameBoyAdvanceChannel1Synth channel1;
    public GameBoyAdvanceChannel2Synth channel2;
    public GameBoyAdvanceChannel3Synth channel3;
    public GameBoyAdvanceChannel4Synth channel4;
    public int CGBMixerOutputCacheLeft = 0;
    public int CGBMixerOutputCacheLeftFolded = 0;
    public int CGBMixerOutputCacheRight = 0;
    public int CGBMixerOutputCacheRightFolded = 0;
    public int AGBDirectSoundATimer = 0;
    public int AGBDirectSoundBTimer = 0;
    public int AGBDirectSoundA = 0;
    public int AGBDirectSoundAFolded = 0;
    public int AGBDirectSoundB = 0;
    public int AGBDirectSoundBFolded = 0;
    public int AGBDirectSoundAShifter = 0;
    public int AGBDirectSoundBShifter = 0;
    public boolean AGBDirectSoundALeftCanPlay = false;
    public boolean AGBDirectSoundBLeftCanPlay = false;
    public boolean AGBDirectSoundARightCanPlay = false;
    public boolean AGBDirectSoundBRightCanPlay = false;
    public int CGBOutputRatio = 2;
    public GameBoyAdvanceFIFO FIFOABuffer;
    public GameBoyAdvanceFIFO FIFOBBuffer;
    public int nr50 = 0;
    public int VinLeftChannelMasterVolume = 1;
    public int VinRightChannelMasterVolume = 1;
    public int nr51 = 0;
    public int nr52 = 0;
    public int mixerOutputCacheLeft = 0;
    public int mixerOutputCacheRight = 0;
    public int audioClocksUntilNextEventCounter = 0;
    public int audioClocksUntilNextEvent = 0;
    public int sequencePosition = 0;
    public int sequencerClocks = 0x8000;
    public int PWMWidth = 0x200;
    public int PWMWidthOld = 0x200;
    public int PWMWidthShadow = 0x200;
    public int PWMBitDepthMask = 0x3FE;
    public int PWMBitDepthMaskShadow = 0x3FE;

    public GameBoyAdvanceSound(GameBoyAdvanceIO ioCore) {
        this.IOCore = ioCore;
    }

    public void initialize() {
        this.coreExposed = IOCore.coreExposed;
        this.dmaChannel1 = IOCore.dmaChannel1;
        this.dmaChannel2 = IOCore.dmaChannel2;
        this.audioTicks = 0;
        initializeSampling(380);
        initializeAudioStartState();
    }

    public void initializeOutput(int audioResamplerFirstPassFactor) {
        if (audioResamplerFirstPassFactor != this.audioResamplerFirstPassFactor) {
            initializeSampling(audioResamplerFirstPassFactor);
        }
    }

    public void initializeSampling(int audioResamplerFirstPassFactor) {
        this.audioIndex = 0;
        this.downsampleInputLeft = 0;
        this.downsampleInputRight = 0;
        this.audioResamplerFirstPassFactor = audioResamplerFirstPassFactor;
    }

    public void initializeAudioStartState() {
        this.nr60 = 0;
        this.nr61 = 0;
        this.nr62 = (!IOCore.SKIPBoot) ? 0 : 0xFF;
        this.nr63 = (!IOCore.SKIPBoot) ? 0 : 0x2;
        this.soundMasterEnabled = IOCore.SKIPBoot;
        this.mixerSoundBIAS = (!IOCore.SKIPBoot) ? 0 : 0x200;
        this.channel1 = new GameBoyAdvanceChannel1Synth(this);
        this.channel2 = new GameBoyAdvanceChannel2Synth(this);
        this.channel3 = new GameBoyAdvanceChannel3Synth(this);
        this.channel4 = new GameBoyAdvanceChannel4Synth(this);
        this.CGBMixerOutputCacheLeft = 0;
        this.CGBMixerOutputCacheLeftFolded = 0;
        this.CGBMixerOutputCacheRight = 0;
        this.CGBMixerOutputCacheRightFolded = 0;
        this.AGBDirectSoundATimer = 0;
        this.AGBDirectSoundBTimer = 0;
        this.AGBDirectSoundA = 0;
        this.AGBDirectSoundAFolded = 0;
        this.AGBDirectSoundB = 0;
        this.AGBDirectSoundBFolded = 0;
        this.AGBDirectSoundAShifter = 0;
        this.AGBDirectSoundBShifter = 0;
        this.AGBDirectSoundALeftCanPlay = false;
        this.AGBDirectSoundBLeftCanPlay = false;
        this.AGBDirectSoundARightCanPlay = false;
        this.AGBDirectSoundBRightCanPlay = false;
        this.CGBOutputRatio = 2;
        this.FIFOABuffer = new GameBoyAdvanceFIFO();
        this.FIFOBBuffer = new GameBoyAdvanceFIFO();
        audioDisabled();
    }

    public void audioDisabled() {
        channel1.disabled();
        channel2.disabled();
        channel3.disabled();
        channel4.disabled();
        AGBDirectSoundAFIFOClear();
        AGBDirectSoundBFIFOClear();
        this.nr50 = 0;
        this.VinLeftChannelMasterVolume = 1;
        this.VinRightChannelMasterVolume = 1;
        this.nr51 = 0;
        this.nr52 = 0;
        this.soundMasterEnabled = false;
        this.mixerOutputCacheLeft = this.mixerSoundBIAS;
        this.mixerOutputCacheRight = this.mixerSoundBIAS;
        this.audioClocksUntilNextEventCounter = 0;
        this.audioClocksUntilNextEvent = 0;
        this.sequencePosition = 0;
        this.sequencerClocks = 0x8000;
        this.PWMWidth = 0x200;
        this.PWMWidthOld = 0x200;
        this.PWMWidthShadow = 0x200;
        this.PWMBitDepthMask = 0x3FE;
        this.PWMBitDepthMaskShadow = 0x3FE;
        channel1.outputLevelCache();
        channel2.outputLevelCache();
        channel3.updateCache();
        channel4.updateCache();
    }

    public void audioEnabled() {
        this.nr52 = 0x80;
        this.soundMasterEnabled = true;
    }

    public void addClocks(int clocks) {
        this.audioTicks += clocks;
    }

    public void generateAudio(int numSamples) {
        int multiplier;
        if (this.soundMasterEnabled && !this.IOCore.isStopped()) {
            for (int clockUpTo = 0; numSamples > 0; ) {
                clockUpTo = Math.min(this.PWMWidth, numSamples);
                this.PWMWidth -= clockUpTo;
                numSamples -= clockUpTo;
                while (clockUpTo > 0) {
                    multiplier = Math.min(clockUpTo, this.audioResamplerFirstPassFactor - this.audioIndex);
                    clockUpTo -= multiplier;
                    this.audioIndex += multiplier;
                    this.downsampleInputLeft += this.mixerOutputCacheLeft * multiplier;
                    this.downsampleInputRight += this.mixerOutputCacheRight * multiplier;
                    if (this.audioIndex == this.audioResamplerFirstPassFactor) {
                        this.audioIndex = 0;
                        this.coreExposed.outputAudio(this.downsampleInputLeft, this.downsampleInputRight);
                        this.downsampleInputLeft = 0;
                        this.downsampleInputRight = 0;
                    }
                }
                if (this.PWMWidth == 0) {
                    computeNextPWMInterval();
                    this.PWMWidthOld = this.PWMWidthShadow;
                    this.PWMWidth = this.PWMWidthShadow;
                }
            }
        } else {
            while (numSamples > 0) {
                multiplier = Math.min(numSamples, this.audioResamplerFirstPassFactor - this.audioIndex);
                numSamples -= multiplier;
                this.audioIndex += multiplier;
                if (this.audioIndex == this.audioResamplerFirstPassFactor) {
                    this.audioIndex = 0;
                    this.coreExposed.outputAudio(this.downsampleInputLeft, this.downsampleInputRight);
                    this.downsampleInputLeft = 0;
                    this.downsampleInputRight = 0;
                }
            }
        }
    }

    public void audioJIT() {
        generateAudio(this.audioTicks);
        this.audioTicks = 0;
    }

    public void audioPSGJIT() {
        IOCore.updateTimerClocking();
        audioJIT();
    }

    public void computeNextPWMInterval() {
        for (int numSamples = this.PWMWidthOld, clockUpTo; numSamples > 0; --numSamples) {
            clockUpTo = Math.min(Math.min(this.audioClocksUntilNextEventCounter, this.sequencerClocks), numSamples);
            this.audioClocksUntilNextEventCounter -= clockUpTo;
            this.sequencerClocks -= clockUpTo;
            numSamples -= clockUpTo;
            if (this.sequencerClocks == 0) {
                audioComputeSequencer();
                this.sequencerClocks = 0x8000;
            }
            if (this.audioClocksUntilNextEventCounter == 0) {
                computeAudioChannels();
            }
        }
        this.PWMBitDepthMask = this.PWMBitDepthMaskShadow;
        this.channel1.outputLevelCache();
        this.channel2.outputLevelCache();
        this.channel3.updateCache();
        this.channel4.updateCache();
        CGBMixerOutputLevelCache();
        mixerOutputLevelCache();
    }

    public void audioComputeSequencer() {
        switch (this.sequencePosition++) {
            case 0:
                clockAudioLength();
                break;
            case 2:
                clockAudioLength();
                this.channel1.clockAudioSweep();
                break;
            case 4:
                clockAudioLength();
                break;
            case 6:
                clockAudioLength();
                this.channel1.clockAudioSweep();
                break;
            case 7:
                clockAudioEnvelope();
                this.sequencePosition = 0;
        }
    }

    public void clockAudioLength() {
        this.channel1.clockAudioLength();
        this.channel2.clockAudioLength();
        this.channel3.clockAudioLength();
        this.channel4.clockAudioLength();
    }

    public void clockAudioEnvelope() {
        this.channel1.clockAudioEnvelope();
        this.channel2.clockAudioEnvelope();
        this.channel4.clockAudioEnvelope();
    }

    public void computeAudioChannels() {
        this.channel1.FrequencyCounter -= this.audioClocksUntilNextEvent;
        this.channel2.FrequencyCounter -= this.audioClocksUntilNextEvent;
        this.channel3.counter -= this.audioClocksUntilNextEvent;
        this.channel4.counter -= this.audioClocksUntilNextEvent;
        this.channel1.computeAudioChannel();
        this.channel2.computeAudioChannel();
        this.channel3.computeAudioChannel();
        this.channel4.computeAudioChannel();
        this.audioClocksUntilNextEventCounter = this.audioClocksUntilNextEvent = Math.min(Math.min(this.channel1.FrequencyCounter, this.channel2.FrequencyCounter), Math.min(this.channel3.counter, this.channel4.counter));
    }

    public void CGBMixerOutputLevelCache() {
        this.CGBMixerOutputCacheLeft = (this.channel1.currentSampleLeft + this.channel2.currentSampleLeft + this.channel3.currentSampleLeft + this.channel4.currentSampleLeft) * this.VinLeftChannelMasterVolume;
        this.CGBMixerOutputCacheRight = (this.channel1.currentSampleRight + this.channel2.currentSampleRight + this.channel3.currentSampleRight + this.channel4.currentSampleRight) * this.VinRightChannelMasterVolume;
        CGBFolder();
    }

    public void writeWAVE8(int address, int data) {
        audioPSGJIT();
        channel3.writeWAVE8(address, data);
    }

    public int readWAVE8(int address) {
        audioPSGJIT();
        return channel3.readWAVE8(address);
    }

    public void writeFIFOA8(int data) {
        IOCore.updateTimerClocking();
        this.FIFOABuffer.push8(data);
        this.checkFIFOAPendingSignal();
    }

    public void writeFIFOB8(int data) {
        IOCore.updateTimerClocking();
        this.FIFOBBuffer.push8(data);
        this.checkFIFOBPendingSignal();
    }

    public void writeFIFOA16(int data) {
        IOCore.updateTimerClocking();
        this.FIFOABuffer.push16(data);
        this.checkFIFOAPendingSignal();
    }

    public void writeFIFOB16(int data) {
        IOCore.updateTimerClocking();
        this.FIFOBBuffer.push16(data);
        this.checkFIFOBPendingSignal();
    }

    public void writeFIFOA32(int data) {
        IOCore.updateTimerClocking();
        this.FIFOABuffer.push32(data);
        this.checkFIFOAPendingSignal();
    }

    public void writeFIFOB32(int data) {
        IOCore.updateTimerClocking();
        this.FIFOBBuffer.push32(data);
        this.checkFIFOBPendingSignal();
    }

    public void checkFIFOAPendingSignal() {
        if (this.FIFOABuffer.requestingDMA()) {
            this.dmaChannel1.soundFIFOARequest();
        }
    }

    public void checkFIFOBPendingSignal() {
        if (this.FIFOBBuffer.requestingDMA()) {
            this.dmaChannel2.soundFIFOBRequest();
        }
    }

    public void AGBDirectSoundAFIFOClear() {
        FIFOABuffer.clear();
        AGBDirectSoundATimerIncrement();
    }

    public void AGBDirectSoundBFIFOClear() {
        FIFOBBuffer.clear();
        AGBDirectSoundBTimerIncrement();
    }

    public void AGBDirectSoundTimer0ClockTick() {
        audioJIT();
        if (soundMasterEnabled) {
            if (AGBDirectSoundATimer == 0) {
                AGBDirectSoundATimerIncrement();
            }
            if (AGBDirectSoundBTimer == 0) {
                AGBDirectSoundBTimerIncrement();
            }
        }
    }

    public void AGBDirectSoundTimer1ClockTick() {
        audioJIT();
        if (soundMasterEnabled) {
            if (AGBDirectSoundATimer == 1) {
                AGBDirectSoundATimerIncrement();
            }
            if (AGBDirectSoundBTimer == 1) {
                AGBDirectSoundBTimerIncrement();
            }
        }
    }

    public int nextFIFOAEventTime() {
        int nextEventTime = 0x7FFFFFFF;
        if (soundMasterEnabled) {
            if (!this.FIFOABuffer.requestingDMA()) {
                int samplesUntilDMA = this.FIFOABuffer.samplesUntilDMATrigger();
                if (this.AGBDirectSoundATimer == 0) {
                    nextEventTime = this.IOCore.timer.nextTimer0Overflow(samplesUntilDMA);
                } else {
                    nextEventTime = this.IOCore.timer.nextTimer1Overflow(samplesUntilDMA);
                }
            } else {
                nextEventTime = 0;
            }
        }
        return nextEventTime;
    }

    public int nextFIFOBEventTime() {
        int nextEventTime = 0x7FFFFFFF;
        if (soundMasterEnabled) {
            if (!this.FIFOBBuffer.requestingDMA()) {
                int samplesUntilDMA = this.FIFOBBuffer.samplesUntilDMATrigger();
                if (this.AGBDirectSoundBTimer == 0) {
                    nextEventTime = this.IOCore.timer.nextTimer0Overflow(samplesUntilDMA);
                } else {
                    nextEventTime = this.IOCore.timer.nextTimer1Overflow(samplesUntilDMA);
                }
            } else {
                nextEventTime = 0;
            }
        }
        return nextEventTime;
    }

    public void AGBDirectSoundATimerIncrement() {
        AGBDirectSoundA = FIFOABuffer.shift();
        checkFIFOAPendingSignal();
        AGBFIFOAFolder();
    }

    public void AGBDirectSoundBTimerIncrement() {
        AGBDirectSoundB = FIFOBBuffer.shift();
        checkFIFOBPendingSignal();
        AGBFIFOBFolder();
    }

    public void AGBFIFOAFolder() {
        AGBDirectSoundAFolded = AGBDirectSoundA >> AGBDirectSoundAShifter;
    }

    public void AGBFIFOBFolder() {
        AGBDirectSoundBFolded = AGBDirectSoundB >> AGBDirectSoundBShifter;
    }

    public void CGBFolder() {
        this.CGBMixerOutputCacheLeftFolded = (this.CGBMixerOutputCacheLeft << this.CGBOutputRatio) >> 1;
        this.CGBMixerOutputCacheRightFolded = (this.CGBMixerOutputCacheRight << this.CGBOutputRatio) >> 1;
    }

    public void mixerOutputLevelCache() {
        this.mixerOutputCacheLeft = Math.min(Math.max((((this.AGBDirectSoundALeftCanPlay) ? this.AGBDirectSoundAFolded : 0) +
                ((this.AGBDirectSoundBLeftCanPlay) ? this.AGBDirectSoundBFolded : 0) +
                this.CGBMixerOutputCacheLeftFolded + this.mixerSoundBIAS), 0), 0x3FF) & this.PWMBitDepthMask;
        this.mixerOutputCacheRight = Math.min(Math.max((((this.AGBDirectSoundARightCanPlay) ? this.AGBDirectSoundAFolded : 0) +
                ((this.AGBDirectSoundBRightCanPlay) ? this.AGBDirectSoundBFolded : 0) +
                this.CGBMixerOutputCacheRightFolded + this.mixerSoundBIAS), 0), 0x3FF) & this.PWMBitDepthMask;
    }
}
