package com.iodine.gba.core;

import java.util.ArrayList;
import java.util.List;

/**
 * GameBoyAdvanceEmulator - Main emulator shell that manages the emulation lifecycle
 * Handles timing, audio buffers, save state management, and user interaction
 */
public class GameBoyAdvanceEmulator {
    // Settings
    public boolean SKIPBoot = false;
    public int audioBufferUnderrunLimit = 100;
    public int audioBufferDynamicLimit = 32;
    public int audioBufferSize = 300;
    public double emulatorSpeed = 1.0;
    public int metricCollectionMinimum = 500;
    public boolean dynamicSpeed = false;
    public int overclockBlockLimit = 200;

    // State
    public int audioFound = 0;
    public int emulatorStatus = 0x10;  // {paused, saves loaded, fault found, loaded}
    public byte[] BIOS = null;
    public byte[] ROM = null;
    public int audioUpdateState = 1;
    public int timerIntervalRate = 16;  // milliseconds
    public long lastTimestamp = 0;
    public boolean dynamicSpeedRefresh = false;

    // Timing
    public int clocksPerSecond;
    public double clocksPerMilliSecond;
    public int CPUCyclesPerIteration;
    public int CPUCyclesTotal;
    public int clockCyclesSinceStart;
    public long metricStart;

    // Audio
    public int audioResamplerFirstPassFactor;
    public double audioDownSampleInputDivider;
    public int audioDestinationPosition;
    public int audioBufferContainAmount;
    public int audioBufferOverclockBlockAmount;
    public int audioBufferDynamicContainAmount;
    public float[] audioBuffer;

    // Callbacks
    public List<Runnable> startCallbacks = new ArrayList<>();
    public List<Runnable> endCallbacks = new ArrayList<>();
    public List<Runnable> terminationCallbacks = new ArrayList<>();

    // Core
    public GameBoyAdvanceIO IOCore;

    public GameBoyAdvanceEmulator() {
        calculateTimings();
    }

    public void startEmulation() {
        if (emulatorStatus >= 0x10) {
            emulatorStatus = emulatorStatus & 0xF;
            if ((emulatorStatus & 0x1) == 0 && BIOS != null && ROM != null) {
                if (initializeCore() == 0) {
                    // Failure to initialize
                    pause();
                    return;
                }
                importSave();
            }
            emulatorStatus = 5; // Set status to "running"
            invalidateMetrics();
            setBufferSpace();
        }
    }

    public void pause() {
        if (emulatorStatus < 0x10) {
            exportSave();
            emulatorStatus = emulatorStatus | 0x10;
        }
    }

    public void stop() {
        emulatorStatus = emulatorStatus & 0x1C;
        audioUpdateState = 1;
        pause();
    }

    public void restart() {
        if ((emulatorStatus & 0x1) == 0x1) {
            emulatorStatus = emulatorStatus & 0x1D;
            exportSave();
            if (initializeCore() == 0) {
                // Failure to initialize
                pause();
                return;
            }
            importSave();
            audioUpdateState = 1;
            processNewSpeed(1.0);
            setBufferSpace();
        }
    }

    public void timerCallback(long timestamp) {
        // Callback passes us a reference timestamp
        this.lastTimestamp = timestamp;
        switch (emulatorStatus) {
            case 5:
                iterationStartSequence();
                IOCore.enter(CPUCyclesTotal);
                iterationEndSequence();
                break;
            case 1:
                break;
            default:
                // Some pending error is preventing execution, so pause
                pause();
        }
    }

    public void iterationStartSequence() {
        calculateSpeedPercentage();
        emulatorStatus = emulatorStatus | 0x2;  // If the end routine doesn't unset this, then we are marked as having crashed
        audioUnderrunAdjustment();
        audioPushNewState();
        runStartJobs();
    }

    public void iterationEndSequence() {
        emulatorStatus = emulatorStatus & 0x1D;  // If core did not throw while running, unset the fatal error flag
        clockCyclesSinceStart = clockCyclesSinceStart + CPUCyclesTotal;
        submitAudioBuffer();
        runEndJobs();
    }

    public void runStartJobs() {
        for (Runnable callback : startCallbacks) {
            callback.run();
        }
    }

    public void runEndJobs() {
        for (Runnable callback : endCallbacks) {
            callback.run();
        }
    }

    public void runTerminationJobs() {
        for (Runnable callback : terminationCallbacks) {
            callback.run();
        }
        startCallbacks.clear();
        endCallbacks.clear();
        terminationCallbacks.clear();
    }

    public void attachROM(byte[] rom) {
        stop();
        this.ROM = rom;
    }

    public void attachBIOS(byte[] bios) {
        stop();
        this.BIOS = bios;
    }

    public String getGameName() {
        if ((emulatorStatus & 0x3) == 0x1) {
            return IOCore.cartridge.name;
        } else {
            return "";
        }
    }

    public void importSave() {
        // Save import stub - implement as needed
        emulatorStatus = emulatorStatus | 0x4;
    }

    public void exportSave() {
        // Save export stub - implement as needed
    }

    public void setSpeed(double speed) {
        if (!dynamicSpeed) {
            processNewSpeed(speed);
        }
    }

    public void processNewSpeed(double speed) {
        // 0.003 for the integer resampler limitations, 0x3F for int math limitations
        speed = Math.min(Math.max(speed, 0.003), 0x3F);
        if (speed != emulatorSpeed) {
            emulatorSpeed = speed;
            calculateTimings();
        }
    }

    public void invalidateMetrics() {
        clockCyclesSinceStart = 0;
        metricStart = 0;
    }

    public void resetMetrics() {
        clockCyclesSinceStart = 0;
        metricStart = lastTimestamp;
    }

    public void calculateTimings() {
        clocksPerSecond = (int) Math.min(emulatorSpeed * 0x1000000, 0x3F000000);
        clocksPerMilliSecond = clocksPerSecond / 1000.0;
        CPUCyclesPerIteration = (int) (clocksPerMilliSecond * timerIntervalRate);
        CPUCyclesTotal = CPUCyclesPerIteration;
        initializeAudioLogic();
        invalidateMetrics();
    }

    public void calculateSpeedPercentage() {
        if (metricStart != 0) {
            long timeDiff = Math.max(lastTimestamp - metricStart, 1);
            if (timeDiff >= metricCollectionMinimum) {
                // Calculate speed percentage and report if callback exists
                resetMetrics();
                dynamicSpeedRefresh = true;
            } else {
                dynamicSpeedRefresh = false;
            }
        } else {
            resetMetrics();
            dynamicSpeedRefresh = false;
        }
    }

    public int initializeCore() {
        // Wrap up any old internal instance callbacks
        runTerminationJobs();
        // Setup a new instance of the i/o core
        IOCore = new GameBoyAdvanceIO(SKIPBoot, this, BIOS, ROM);
        // Call the initialization procedure and get status code
        int allowInit = IOCore.initialize();
        // Append status code as play status flag for emulator runtime
        emulatorStatus = emulatorStatus | allowInit;
        return allowInit;
    }

    public void keyDown(int keyPressed) {
        if (emulatorStatus < 0x10 && keyPressed >= 0 && keyPressed <= 9) {
            IOCore.joypad.keyPress(keyPressed);
        }
    }

    public void keyUp(int keyReleased) {
        if (emulatorStatus < 0x10 && keyReleased >= 0 && keyReleased <= 9) {
            IOCore.joypad.keyRelease(keyReleased);
        }
    }

    public void initializeAudioLogic() {
        // Calculate the variables for the preliminary downsampler first
        audioResamplerFirstPassFactor = Math.min(clocksPerSecond / 44100, 0x7FFFFFFF / 0x3FF);
        audioDownSampleInputDivider = (2.0 / 0x3FF) / audioResamplerFirstPassFactor;
        initializeAudioBuffering();
        // Need to push the new resample factor
        audioUpdateState = 1;
    }

    public void initializeAudioBuffering() {
        audioDestinationPosition = 0;
        audioBufferContainAmount = (int) Math.max((clocksPerMilliSecond * audioBufferUnderrunLimit) / audioResamplerFirstPassFactor, 3) << 1;
        audioBufferOverclockBlockAmount = (int) Math.max((clocksPerMilliSecond * overclockBlockLimit) / audioResamplerFirstPassFactor, 3) << 1;
        audioBufferDynamicContainAmount = (int) Math.max((clocksPerMilliSecond * audioBufferDynamicLimit) / audioResamplerFirstPassFactor, 2) << 1;
        // Underrun logic will request at most 32 milliseconds of runtime per iteration, so set buffer size to 64 ms
        int audioNumSamplesTotal = (int) Math.max((clocksPerMilliSecond / audioResamplerFirstPassFactor) * 64, 4) << 1;
        if (audioBuffer == null || audioNumSamplesTotal > audioBuffer.length) {
            audioBuffer = new float[audioNumSamplesTotal];
        }
    }

    public void outputAudio(int downsampleInputLeft, int downsampleInputRight) {
        audioBuffer[audioDestinationPosition++] = (float)((downsampleInputLeft * audioDownSampleInputDivider) - 1.0);
        audioBuffer[audioDestinationPosition++] = (float)((downsampleInputRight * audioDownSampleInputDivider) - 1.0);
    }

    public void submitAudioBuffer() {
        // Audio submission stub - will be connected to audio system
        audioDestinationPosition = 0;
    }

    public void audioUnderrunAdjustment() {
        CPUCyclesTotal = CPUCyclesPerIteration;
        // Audio underrun adjustment logic would go here
    }

    public void audioPushNewState() {
        if (audioUpdateState != 0) {
            IOCore.sound.initializeOutput(audioResamplerFirstPassFactor);
            audioUpdateState = 0;
        }
    }

    public void setBufferSpace() {
        // Buffer space setup stub
    }
}
