package com.iodinegba.core;

public class Sound {
    private IOCore IOCore;
    private CoreExposed coreExposed;
    private DMAChannel dmaChannel1;
    private DMAChannel dmaChannel2;
    private int audioTicks = 0;
    private int audioIndex = 0;
    private int downsampleInputLeft = 0;
    private int downsampleInputRight = 0;
    private int audioResamplerFirstPassFactor = 380;
    private int nr60 = 0;
    private int nr61 = 0;
    private int nr62 = 0;
    private int nr63 = 0;
    private boolean soundMasterEnabled = false;
    private int mixerSoundBIAS = 0;
    private GameBoyAdvanceChannel1Synth channel1;
    private GameBoyAdvanceChannel2Synth channel2;
    private GameBoyAdvanceChannel3Synth channel3;
    private GameBoyAdvanceChannel4Synth channel4;
    private int CGBMixerOutputCacheLeft = 0;
    private int CGBMixerOutputCacheLeftFolded = 0;
    private int CGBMixerOutputCacheRight = 0;
    private int CGBMixerOutputCacheRightFolded = 0;
    private int AGBDirectSoundATimer = 0;
    private int AGBDirectSoundBTimer = 0;
    private int AGBDirectSoundA = 0;
    private int AGBDirectSoundAFolded = 0;
    private int AGBDirectSoundB = 0;
    private int AGBDirectSoundBFolded = 0;
    private int AGBDirectSoundAShifter = 0;
    private int AGBDirectSoundBShifter = 0;
    private boolean AGBDirectSoundALeftCanPlay = false;
    private boolean AGBDirectSoundBLeftCanPlay = false;
    private boolean AGBDirectSoundARightCanPlay = false;
    private boolean AGBDirectSoundBRightCanPlay = false;
    private int CGBOutputRatio = 2;
    private GameBoyAdvanceFIFO FIFOABuffer;
    private GameBoyAdvanceFIFO FIFOBBuffer;
    private int nr50 = 0;
    private int VinLeftChannelMasterVolume = 1;
    private int VinRightChannelMasterVolume = 1;
    private int nr51 = 0;
    private int nr52 = 0;
    private int audioClocksUntilNextEventCounter = 0;
    private int audioClocksUntilNextEvent = 0;
    private int sequencePosition = 0;
    private int sequencerClocks = 0x8000;
    private int PWMWidth = 0x200;
    private int PWMWidthOld = 0x200;
    private int PWMWidthShadow = 0x200;
    private int PWMBitDepthMask = 0x3FE;
    private int PWMBitDepthMaskShadow = 0x3FE;
    private int mixerOutputCacheLeft = 0;
    private int mixerOutputCacheRight = 0;

    public Sound(IOCore IOCore) {
        this.IOCore = IOCore;
    }

    public void initialize() {
        this.coreExposed = this.IOCore.coreExposed;
        this.dmaChannel1 = this.IOCore.dmaChannel1;
        this.dmaChannel2 = this.IOCore.dmaChannel2;
        this.audioTicks = 0;
        this.initializeSampling(380);
        this.initializeAudioStartState();
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
        this.nr62 = (!this.IOCore.SKIPBoot) ? 0 : 0xFF;
        this.nr63 = (!this.IOCore.SKIPBoot) ? 0 : 0x2;
        this.soundMasterEnabled = this.IOCore.SKIPBoot;
        this.mixerSoundBIAS = (!this.IOCore.SKIPBoot) ? 0 : 0x200;
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
        this.audioDisabled();
    }

    public void audioDisabled() {
        this.channel1.disabled();
        this.channel2.disabled();
        this.channel3.disabled();
        this.channel4.disabled();
        this.AGBDirectSoundAFIFOClear();
        this.AGBDirectSoundBFIFOClear();
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
        this.channel1.outputLevelCache();
        this.channel2.outputLevelCache();
        this.channel3.updateCache();
        this.channel4.updateCache();
    }

    public void addClocks(int clocks) {
        audioTicks += clocks;
    }

    public void audioJIT() {
        generateAudio(audioTicks);
        audioTicks = 0;
    }

    public void generateAudio(int numSamples) {
        int multiplier = 0;
        if (soundMasterEnabled && !IOCore.isStopped()) {
            for (int clockUpTo = 0; numSamples > 0;) {
                clockUpTo = Math.min(PWMWidth, numSamples);
                PWMWidth -= clockUpTo;
                numSamples -= clockUpTo;
                while (clockUpTo > 0) {
                    multiplier = Math.min(clockUpTo, audioResamplerFirstPassFactor - audioIndex);
                    clockUpTo -= multiplier;
                    audioIndex += multiplier;
                    downsampleInputLeft += mixerOutputCacheLeft * multiplier;
                    downsampleInputRight += mixerOutputCacheRight * multiplier;
                    if (audioIndex == audioResamplerFirstPassFactor) {
                        audioIndex = 0;
                        coreExposed.outputAudio(downsampleInputLeft, downsampleInputRight);
                        downsampleInputLeft = 0;
                        downsampleInputRight = 0;
                    }
                }
                if (PWMWidth == 0) {
                    computeNextPWMInterval();
                    PWMWidthOld = PWMWidthShadow;
                    PWMWidth = PWMWidthShadow;
                }
            }
        } else {
            while (numSamples > 0) {
                multiplier = Math.min(numSamples, audioResamplerFirstPassFactor - audioIndex);
                numSamples -= multiplier;
                audioIndex += multiplier;
                if (audioIndex == audioResamplerFirstPassFactor) {
                    audioIndex = 0;
                    coreExposed.outputAudio(downsampleInputLeft, downsampleInputRight);
                    downsampleInputLeft = 0;
                    downsampleInputRight = 0;
                }
            }
        }
    }

    private void computeNextPWMInterval() {
        for (int numSamples = PWMWidthOld, clockUpTo = 0; numSamples > 0; numSamples--) {
            clockUpTo = Math.min(audioClocksUntilNextEventCounter, Math.min(sequencerClocks, numSamples));
            audioClocksUntilNextEventCounter -= clockUpTo;
            sequencerClocks -= clockUpTo;
            numSamples -= clockUpTo;
            if (sequencerClocks == 0) {
                audioComputeSequencer();
                sequencerClocks = 0x8000;
            }
            if (audioClocksUntilNextEventCounter == 0) {
                computeAudioChannels();
            }
        }
        PWMBitDepthMask = PWMBitDepthMaskShadow;
        channel1.outputLevelCache();
        channel2.outputLevelCache();
        channel3.updateCache();
        channel4.updateCache();
        CGBMixerOutputLevelCache();
        mixerOutputLevelCache();
    }

    public void AGBDirectSoundTimer0ClockTick() {}
    public void AGBDirectSoundTimer1ClockTick() {}

    private void audioComputeSequencer() {
        switch (sequencePosition++) {
            case 0:
                clockAudioLength();
                break;
            case 2:
                clockAudioLength();
                channel1.clockAudioSweep();
                break;
            case 4:
                clockAudioLength();
                break;
            case 6:
                clockAudioLength();
                channel1.clockAudioSweep();
                break;
            case 7:
                clockAudioEnvelope();
                sequencePosition = 0;
        }
    }

    private void clockAudioLength() {
        channel1.clockAudioLength();
        channel2.clockAudioLength();
        channel3.clockAudioLength();
        channel4.clockAudioLength();
    }

    private void clockAudioEnvelope() {
        channel1.clockAudioEnvelope();
        channel2.clockAudioEnvelope();
        channel4.clockAudioEnvelope();
    }

    private void computeAudioChannels() {
        channel1.FrequencyCounter -= audioClocksUntilNextEvent;
        channel2.FrequencyCounter -= audioClocksUntilNextEvent;
        channel3.counter -= audioClocksUntilNextEvent;
        channel4.counter -= audioClocksUntilNextEvent;
        channel1.computeAudioChannel();
        channel2.computeAudioChannel();
        channel3.computeAudioChannel();
        channel4.computeAudioChannel();
        audioClocksUntilNextEventCounter = audioClocksUntilNextEvent = Math.min(channel1.FrequencyCounter, Math.min(channel2.FrequencyCounter, Math.min(channel3.counter, channel4.counter)));
    }

    private void CGBMixerOutputLevelCache() {
        CGBMixerOutputCacheLeft = (channel1.currentSampleLeft + channel2.currentSampleLeft + channel3.currentSampleLeft + channel4.currentSampleLeft) * VinLeftChannelMasterVolume;
        CGBMixerOutputCacheRight = (channel1.currentSampleRight + channel2.currentSampleRight + channel3.currentSampleRight + channel4.currentSampleRight) * VinRightChannelMasterVolume;
        CGBFolder();
    }

    private void CGBFolder() {
        CGBMixerOutputCacheLeftFolded = (CGBMixerOutputCacheLeft << CGBOutputRatio) >> 1;
        CGBMixerOutputCacheRightFolded = (CGBMixerOutputCacheRight << CGBOutputRatio) >> 1;
    }

    private void mixerOutputLevelCache() {
        mixerOutputCacheLeft = Math.min(Math.max(((AGBDirectSoundALeftCanPlay ? AGBDirectSoundAFolded : 0) +
                                                    (AGBDirectSoundBLeftCanPlay ? AGBDirectSoundBFolded : 0) +
                                                    CGBMixerOutputCacheLeftFolded + mixerSoundBIAS), 0), 0x3FF) & PWMBitDepthMask;
        mixerOutputCacheRight = Math.min(Math.max(((AGBDirectSoundARightCanPlay ? AGBDirectSoundAFolded : 0) +
                                                     (AGBDirectSoundBRightCanPlay ? AGBDirectSoundBFolded : 0) +
                                                     CGBMixerOutputCacheRightFolded + mixerSoundBIAS), 0), 0x3FF) & PWMBitDepthMask;
    }

    private void AGBDirectSoundAFIFOClear() {
        FIFOABuffer.clear();
        AGBDirectSoundATimerIncrement();
    }

    private void AGBDirectSoundBFIFOClear() {
        FIFOBBuffer.clear();
        AGBDirectSoundBTimerIncrement();
    }

    private void AGBDirectSoundATimerIncrement() {
        AGBDirectSoundA = FIFOABuffer.shift();
        checkFIFOAPendingSignal();
        AGBFIFOAFolder();
    }

    private void checkFIFOAPendingSignal() {
        if (FIFOABuffer.requestingDMA()) {
            dmaChannel1.requestDMA(0x10);
        }
    }

    private void AGBFIFOAFolder() {
        AGBDirectSoundAFolded = AGBDirectSoundA >> AGBDirectSoundAShifter;
    }

    private void AGBDirectSoundBTimerIncrement() {
        AGBDirectSoundB = FIFOBBuffer.shift();
        checkFIFOBPendingSignal();
        AGBFIFOBFolder();
    }

    private void checkFIFOBPendingSignal() {
        if (FIFOBBuffer.requestingDMA()) {
            dmaChannel2.requestDMA(0x20);
        }
    }

    private void AGBFIFOBFolder() {
        AGBDirectSoundBFolded = AGBDirectSoundB >> AGBDirectSoundBShifter;
    }

    public void setNR52(int data) {
        nr52 |= data;
    }

    public void unsetNR52(int data) {
        nr52 &= ~data;
    }
}
