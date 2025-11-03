package com.iodine.gba.core;

public class GameBoyAdvanceTimer {
    public GameBoyAdvanceIO IOCore;

    private final int[] prescalarLookup = { 0, 6, 8, 10 };

    private int timer0Counter;
    private int timer0Reload;
    private int timer0Control;
    private boolean timer0Enabled;
    private boolean timer0IRQ;
    private int timer0Precounter;
    private int timer0Prescalar;
    private int timer0PrescalarShifted;

    private int timer1Counter;
    private int timer1Reload;
    private int timer1Control;
    private boolean timer1Enabled;
    private boolean timer1IRQ;
    private int timer1Precounter;
    private int timer1Prescalar;
    private int timer1PrescalarShifted;
    private boolean timer1CountUp;

    private int timer2Counter;
    private int timer2Reload;
    private int timer2Control;
    private boolean timer2Enabled;
    private boolean timer2IRQ;
    private int timer2Precounter;
    private int timer2Prescalar;
    private int timer2PrescalarShifted;
    private boolean timer2CountUp;

    private int timer3Counter;
    private int timer3Reload;
    private int timer3Control;
    private boolean timer3Enabled;
    private boolean timer3IRQ;
    private int timer3Precounter;
    private int timer3Prescalar;
    private int timer3PrescalarShifted;
    private boolean timer3CountUp;

    private boolean timer1UseMainClocks;
    private boolean timer1UseChainedClocks;
    private boolean timer2UseMainClocks;
    private boolean timer2UseChainedClocks;
    private boolean timer3UseMainClocks;
    private boolean timer3UseChainedClocks;

    public GameBoyAdvanceTimer(GameBoyAdvanceIO ioCore) {
        this.IOCore = ioCore;
    }

    public void initialize() {
        this.timer0Counter = 0;
        this.timer0Reload = 0;
        this.timer0Control = 0;
        this.timer0Enabled = false;
        this.timer0IRQ = false;
        this.timer0Precounter = 0;
        this.timer0Prescalar = 1;
        this.timer0PrescalarShifted = 0;

        this.timer1Counter = 0;
        this.timer1Reload = 0;
        this.timer1Control = 0;
        this.timer1Enabled = false;
        this.timer1IRQ = false;
        this.timer1Precounter = 0;
        this.timer1Prescalar = 1;
        this.timer1PrescalarShifted = 0;
        this.timer1CountUp = false;

        this.timer2Counter = 0;
        this.timer2Reload = 0;
        this.timer2Control = 0;
        this.timer2Enabled = false;
        this.timer2IRQ = false;
        this.timer2Precounter = 0;
        this.timer2Prescalar = 1;
        this.timer2PrescalarShifted = 0;
        this.timer2CountUp = false;

        this.timer3Counter = 0;
        this.timer3Reload = 0;
        this.timer3Control = 0;
        this.timer3Enabled = false;
        this.timer3IRQ = false;
        this.timer3Precounter = 0;
        this.timer3Prescalar = 1;
        this.timer3PrescalarShifted = 0;
        this.timer3CountUp = false;

        this.timer1UseMainClocks = false;
        this.timer1UseChainedClocks = false;
        this.timer2UseMainClocks = false;
        this.timer2UseChainedClocks = false;
        this.timer3UseMainClocks = false;
        this.timer3UseChainedClocks = false;
    }

    public void addClocks(int clocks) {
        clockSoundTimers(clocks);
        clockTimer2(clocks);
        clockTimer3(clocks);
    }

    public void clockSoundTimers(int audioClocks) {
        for (int predictedClocks = 0, overflowClocks = 0; audioClocks > 0; audioClocks -= predictedClocks) {
            overflowClocks = nextAudioTimerOverflow();
            predictedClocks = Math.min(audioClocks, overflowClocks);
            clockTimer0(predictedClocks);
            clockTimer1(predictedClocks);
            IOCore.sound.addClocks(predictedClocks);
            if (overflowClocks == predictedClocks) {
                IOCore.sound.audioJIT();
            }
        }
    }

    public void timer1ClockUpTickCheck() {
        if (this.timer1UseChainedClocks) {
            this.timer1Counter++;
            if (this.timer1Counter > 0xFFFF) {
                this.timer1Counter = this.timer1Reload;
                timer1ExternalTriggerCheck();
                timer2ClockUpTickCheck();
            }
        }
    }

    public void timer2ClockUpTickCheck() {
        if (this.timer2UseChainedClocks) {
            this.timer2Counter++;
            if (this.timer2Counter > 0xFFFF) {
                this.timer2Counter = this.timer2Reload;
                timer2ExternalTriggerCheck();
                timer3ClockUpTickCheck();
            }
        }
    }

    public void timer3ClockUpTickCheck() {
        if (this.timer3UseChainedClocks) {
            this.timer3Counter++;
            if (this.timer3Counter > 0xFFFF) {
                this.timer3Counter = this.timer3Reload;
                timer3ExternalTriggerCheck();
            }
        }
    }

    public void timer0ExternalTriggerCheck() {
        if (this.timer0IRQ) {
            IOCore.irq.requestIRQ(0x08);
        }
        IOCore.sound.AGBDirectSoundTimer0ClockTick();
    }

    public void timer1ExternalTriggerCheck() {
        if (this.timer1IRQ) {
            IOCore.irq.requestIRQ(0x10);
        }
        IOCore.sound.AGBDirectSoundTimer1ClockTick();
    }

    public void timer2ExternalTriggerCheck() {
        if (this.timer2IRQ) {
            IOCore.irq.requestIRQ(0x20);
        }
    }

    public void timer3ExternalTriggerCheck() {
        if (this.timer3IRQ) {
            IOCore.irq.requestIRQ(0x40);
        }
    }

    public int nextAudioTimerOverflow() {
        int timer0 = nextTimer0OverflowSingle();
        int timer1 = nextTimer1OverflowSingle();
        return Math.min(timer0, timer1);
    }

    public void writeTM0CNT8_0(int data) {
        IOCore.updateTimerClocking();
        IOCore.sound.audioJIT();
        this.timer0Reload = (this.timer0Reload & 0xFF00) | (data & 0xFF);
        IOCore.updateCoreEventTime();
    }

    public void writeTM0CNT8_1(int data) {
        IOCore.updateTimerClocking();
        IOCore.sound.audioJIT();
        this.timer0Reload = (this.timer0Reload & 0xFF) | ((data & 0xFF) << 8);
        IOCore.updateCoreEventTime();
    }

    public void writeTM0CNT8_2(int data) {
        IOCore.updateTimerClocking();
        IOCore.sound.audioJIT();
        this.timer0Control = data & 0xFF;
        if ((data & 0x80) != 0) {
            if (!this.timer0Enabled) {
                this.timer0Counter = this.timer0Reload;
                this.timer0Enabled = true;
                this.timer0Precounter = 0;
            }
        } else {
            this.timer0Enabled = false;
        }
        this.timer0IRQ = ((data & 0x40) != 0);
        this.timer0PrescalarShifted = this.prescalarLookup[data & 0x03];
        this.timer0Prescalar = 1 << this.timer0PrescalarShifted;
        IOCore.updateCoreEventTime();
    }

    public int nextTimer0OverflowSingle() {
        if (timer0Enabled) {
            return nextTimer0OverflowBase();
        }
        return 0x7FFFFFFF;
    }

    public int nextTimer1OverflowSingle() {
        if (timer1Enabled) {
            if (timer1CountUp) {
                int countUntilReload = 0x10000 - timer1Counter;
                return nextTimer0Overflow(countUntilReload);
            } else {
                return nextTimer1OverflowBase();
            }
        }
        return 0x7FFFFFFF;
    }

    public void clockTimer1(int clocks) {
        if (this.timer1UseMainClocks) {
            this.timer1Precounter += clocks;
            while (this.timer1Precounter >= this.timer1Prescalar) {
                int iterations = Math.min(this.timer1Precounter >> this.timer1PrescalarShifted, 0x10000 - this.timer1Counter);
                this.timer1Precounter -= iterations << this.timer1PrescalarShifted;
                this.timer1Counter += iterations;
                if (this.timer1Counter > 0xFFFF) {
                    this.timer1Counter = this.timer1Reload;
                    timer1ExternalTriggerCheck();
                    timer2ClockUpTickCheck();
                }
            }
        }
    }

    public void clockTimer2(int clocks) {
        if (this.timer2UseMainClocks) {
            this.timer2Precounter += clocks;
            while (this.timer2Precounter >= this.timer2Prescalar) {
                int iterations = Math.min(this.timer2Precounter >> this.timer2PrescalarShifted, 0x10000 - this.timer2Counter);
                this.timer2Precounter -= iterations << this.timer2PrescalarShifted;
                this.timer2Counter += iterations;
                if (this.timer2Counter > 0xFFFF) {
                    this.timer2Counter = this.timer2Reload;
                    timer2ExternalTriggerCheck();
                    timer3ClockUpTickCheck();
                }
            }
        }
    }

    public void clockTimer3(int clocks) {
        if (this.timer3UseMainClocks) {
            this.timer3Precounter += clocks;
            while (this.timer3Precounter >= this.timer3Prescalar) {
                int iterations = Math.min(this.timer3Precounter >> this.timer3PrescalarShifted, 0x10000 - this.timer3Counter);
                this.timer3Precounter -= iterations << this.timer3PrescalarShifted;
                this.timer3Counter += iterations;
                if (this.timer3Counter > 0xFFFF) {
                    this.timer3Counter = this.timer3Reload;
                    timer3ExternalTriggerCheck();
                }
            }
        }
    }

    public void clockTimer0(int clocks) {
        if (this.timer0Enabled) {
            this.timer0Precounter += clocks;
            while (this.timer0Precounter >= this.timer0Prescalar) {
                int iterations = Math.min(this.timer0Precounter >> this.timer0PrescalarShifted, 0x10000 - this.timer0Counter);
                this.timer0Precounter -= iterations << this.timer0PrescalarShifted;
                this.timer0Counter += iterations;
                if (this.timer0Counter > 0xFFFF) {
                    this.timer0Counter = this.timer0Reload;
                    timer0ExternalTriggerCheck();
                    timer1ClockUpTickCheck();
                }
            }
        }
    }

    public int nextTimer0Overflow(int numOverflows) {
        if (timer0Enabled) {
            numOverflows = (numOverflows - 1);
            int countUntilReload = nextTimer0OverflowBase();
            int reloadClocks = (0x10000 - timer0Reload);
            reloadClocks = reloadClocks * timer0Prescalar;
            reloadClocks = reloadClocks * numOverflows;
            return Math.min(countUntilReload + reloadClocks, 0x7FFFFFFF);
        }
        return 0x7FFFFFFF;
    }

    public int nextTimer1Overflow(int numOverflows) {
        if (timer1Enabled) {
            int reloadClocks = (0x10000 - timer1Reload);
            if (timer1CountUp) {
                int countUntilReload = (0x10000 - timer1Counter);
                reloadClocks = reloadClocks * numOverflows;
                int eventTime = Math.min(countUntilReload + reloadClocks, 0x7FFFFFFF);
                return nextTimer0Overflow(eventTime);
            } else {
                numOverflows = (numOverflows - 1);
                int countUntilReload = nextTimer1OverflowBase();
                reloadClocks = reloadClocks * timer1Prescalar;
                reloadClocks = reloadClocks * numOverflows;
                return Math.min(countUntilReload + reloadClocks, 0x7FFFFFFF);
            }
        }
        return 0x7FFFFFFF;
    }

    private int nextTimer0OverflowBase() {
        int countUntilReload = (0x10000 - timer0Counter);
        countUntilReload = countUntilReload * timer0Prescalar;
        countUntilReload = countUntilReload - timer0Precounter;
        return countUntilReload;
    }

    private int nextTimer1OverflowBase() {
        int countUntilReload = (0x10000 - timer1Counter);
        countUntilReload = countUntilReload * timer1Prescalar;
        countUntilReload = countUntilReload - timer1Precounter;
        return countUntilReload;
    }
}
