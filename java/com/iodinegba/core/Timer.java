package com.iodinegba.core;

public class Timer {
    private IOCore IOCore;

    private static final int[] prescalarLookup = { 0, 6, 8, 10 };

    public int timer0Counter = 0;
    public int timer0Reload = 0;
    public int timer0Control = 0;
    public boolean timer0Enabled = false;
    public boolean timer0IRQ = false;
    public int timer0Precounter = 0;
    public int timer0Prescalar = 1;
    public int timer0PrescalarShifted = 0;

    public int timer1Counter = 0;
    public int timer1Reload = 0;
    public int timer1Control = 0;
    public boolean timer1Enabled = false;
    public boolean timer1IRQ = false;
    public int timer1Precounter = 0;
    public int timer1Prescalar = 1;
    public int timer1PrescalarShifted = 0;
    public boolean timer1CountUp = false;

    public int timer2Counter = 0;
    public int timer2Reload = 0;
    public int timer2Control = 0;
    public boolean timer2Enabled = false;
    public boolean timer2IRQ = false;
    public int timer2Precounter = 0;
    public int timer2Prescalar = 1;
    public int timer2PrescalarShifted = 0;
    public boolean timer2CountUp = false;

    public int timer3Counter = 0;
    public int timer3Reload = 0;
    public int timer3Control = 0;
    public boolean timer3Enabled = false;
    public boolean timer3IRQ = false;
    public int timer3Precounter = 0;
    public int timer3Prescalar = 1;
    public int timer3PrescalarShifted = 0;
    public boolean timer3CountUp = false;

    public boolean timer1UseMainClocks = false;
    public boolean timer1UseChainedClocks = false;
    public boolean timer2UseMainClocks = false;
    public boolean timer2UseChainedClocks = false;
    public boolean timer3UseMainClocks = false;
    public boolean timer3UseChainedClocks = false;

    public Timer(IOCore IOCore) {
        this.IOCore = IOCore;
    }

    public void initialize() {
        timer0Counter = 0;
        timer0Reload = 0;
        timer0Control = 0;
        timer0Enabled = false;
        timer0IRQ = false;
        timer0Precounter = 0;
        timer0Prescalar = 1;
        timer0PrescalarShifted = 0;
        timer1Counter = 0;
        timer1Reload = 0;
        timer1Control = 0;
        timer1Enabled = false;
        timer1IRQ = false;
        timer1Precounter = 0;
        timer1Prescalar = 1;
        timer1PrescalarShifted = 0;
        timer1CountUp = false;
        timer2Counter = 0;
        timer2Reload = 0;
        timer2Control = 0;
        timer2Enabled = false;
        timer2IRQ = false;
        timer2Precounter = 0;
        timer2Prescalar = 1;
        timer2PrescalarShifted = 0;
        timer2CountUp = false;
        timer3Counter = 0;
        timer3Reload = 0;
        timer3Control = 0;
        timer3Enabled = false;
        timer3IRQ = false;
        timer3Precounter = 0;
        timer3Prescalar = 1;
        timer3PrescalarShifted = 0;
        timer3CountUp = false;
        timer1UseMainClocks = false;
        timer1UseChainedClocks = false;
        timer2UseMainClocks = false;
        timer2UseChainedClocks = false;
        timer3UseMainClocks = false;
        timer3UseChainedClocks = false;
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

    public void clockTimer0(int clocks) {
        if (timer0Enabled) {
            timer0Precounter += clocks;
            while (timer0Precounter >= timer0Prescalar) {
                int iterations = Math.min(timer0Precounter >> timer0PrescalarShifted, 0x10000 - timer0Counter);
                timer0Precounter -= iterations << timer0PrescalarShifted;
                timer0Counter += iterations;
                if (timer0Counter > 0xFFFF) {
                    timer0Counter = timer0Reload;
                    timer0ExternalTriggerCheck();
                    timer1ClockUpTickCheck();
                }
            }
        }
    }

    public void clockTimer1(int clocks) {
        if (timer1UseMainClocks) {
            timer1Precounter += clocks;
            while (timer1Precounter >= timer1Prescalar) {
                int iterations = Math.min(timer1Precounter >> timer1PrescalarShifted, 0x10000 - timer1Counter);
                timer1Precounter -= iterations << timer1PrescalarShifted;
                timer1Counter += iterations;
                if (timer1Counter > 0xFFFF) {
                    timer1Counter = timer1Reload;
                    timer1ExternalTriggerCheck();
                    timer2ClockUpTickCheck();
                }
            }
        }
    }

    public void clockTimer2(int clocks) {
        if (timer2UseMainClocks) {
            timer2Precounter += clocks;
            while (timer2Precounter >= timer2Prescalar) {
                int iterations = Math.min(timer2Precounter >> timer2PrescalarShifted, 0x10000 - timer2Counter);
                timer2Precounter -= iterations << timer2PrescalarShifted;
                timer2Counter += iterations;
                if (timer2Counter > 0xFFFF) {
                    timer2Counter = timer2Reload;
                    timer2ExternalTriggerCheck();
                    timer3ClockUpTickCheck();
                }
            }
        }
    }

    public void clockTimer3(int clocks) {
        if (timer3UseMainClocks) {
            timer3Precounter += clocks;
            while (timer3Precounter >= timer3Prescalar) {
                int iterations = Math.min(timer3Precounter >> timer3PrescalarShifted, 0x10000 - timer3Counter);
                timer3Precounter -= iterations << timer3PrescalarShifted;
                timer3Counter += iterations;
                if (timer3Counter > 0xFFFF) {
                    timer3Counter = timer3Reload;
                    timer3ExternalTriggerCheck();
                }
            }
        }
    }

    public void timer1ClockUpTickCheck() {
        if (timer1UseChainedClocks) {
            timer1Counter++;
            if (timer1Counter > 0xFFFF) {
                timer1Counter = timer1Reload;
                timer1ExternalTriggerCheck();
                timer2ClockUpTickCheck();
            }
        }
    }

    public void timer2ClockUpTickCheck() {
        if (timer2UseChainedClocks) {
            timer2Counter++;
            if (timer2Counter > 0xFFFF) {
                timer2Counter = timer2Reload;
                timer2ExternalTriggerCheck();
                timer3ClockUpTickCheck();
            }
        }
    }

    public void timer3ClockUpTickCheck() {
        if (timer3UseChainedClocks) {
            timer3Counter++;
            if (timer3Counter > 0xFFFF) {
                timer3Counter = timer3Reload;
                timer3ExternalTriggerCheck();
            }
        }
    }

    public void timer0ExternalTriggerCheck() {
        if (timer0IRQ) {
            IOCore.irq.requestIRQ(0x08);
        }
        IOCore.sound.AGBDirectSoundTimer0ClockTick();
    }

    public void timer1ExternalTriggerCheck() {
        if (timer1IRQ) {
            IOCore.irq.requestIRQ(0x10);
        }
        IOCore.sound.AGBDirectSoundTimer1ClockTick();
    }

    public void timer2ExternalTriggerCheck() {
        if (timer2IRQ) {
            IOCore.irq.requestIRQ(0x20);
        }
    }

    public void timer3ExternalTriggerCheck() {
        if (timer3IRQ) {
            IOCore.irq.requestIRQ(0x40);
        }
    }

    public void writeTM0CNT8_0(int data) {
        IOCore.updateTimerClocking();
        IOCore.sound.audioJIT();
        timer0Reload = (timer0Reload & 0xFF00) | (data & 0xFF);
        IOCore.updateCoreEventTime();
    }

    public void writeTM0CNT8_1(int data) {
        IOCore.updateTimerClocking();
        IOCore.sound.audioJIT();
        timer0Reload = (timer0Reload & 0xFF) | ((data & 0xFF) << 8);
        IOCore.updateCoreEventTime();
    }

    public void writeTM0CNT8_2(int data) {
        IOCore.updateTimerClocking();
        IOCore.sound.audioJIT();
        timer0Control = data & 0xFF;
        if ((data & 0x80) != 0) {
            if (!timer0Enabled) {
                timer0Counter = timer0Reload;
                timer0Enabled = true;
                timer0Precounter = 0;
            }
        } else {
            timer0Enabled = false;
        }
        timer0IRQ = (data & 0x40) != 0;
        timer0PrescalarShifted = prescalarLookup[data & 0x03];
        timer0Prescalar = 1 << timer0PrescalarShifted;
        IOCore.updateCoreEventTime();
    }

    public int nextTimer0OverflowBase() {
        int countUntilReload = 0x10000 - timer0Counter;
        countUntilReload *= timer0Prescalar;
        countUntilReload -= timer0Precounter;
        return countUntilReload;
    }

    public int nextTimer0OverflowSingle() {
        int eventTime = 0x7FFFFFFF;
        if (timer0Enabled) {
            eventTime = nextTimer0OverflowBase();
        }
        return eventTime;
    }

    public int nextAudioTimerOverflow() {
        int timer0 = nextTimer0OverflowSingle();
        int timer1 = nextTimer1OverflowSingle();
        return Math.min(timer0, timer1);
    }

    public int nextTimer1OverflowSingle() {
        int eventTime = 0x7FFFFFFF;
        if (timer1Enabled) {
            if (timer1CountUp) {
                int countUntilReload = 0x10000 - timer1Counter;
                eventTime = nextTimer0Overflow(countUntilReload);
            } else {
                eventTime = nextTimer1OverflowBase();
            }
        }
        return eventTime;
    }

    public int nextTimer0Overflow(int numOverflows) {
        int eventTime = 0x7FFFFFFF;
        if (timer0Enabled) {
            numOverflows -= 1;
            int countUntilReload = nextTimer0OverflowBase();
            int reloadClocks = 0x10000 - timer0Reload;
            reloadClocks *= timer0Prescalar;
            reloadClocks *= numOverflows;
            eventTime = Math.min(countUntilReload + reloadClocks, 0x7FFFFFFF);
        }
        return eventTime;
    }

    public int nextTimer1OverflowBase() {
        int countUntilReload = 0x10000 - timer1Counter;
        countUntilReload *= timer1Prescalar;
        countUntilReload -= timer1Precounter;
        return countUntilReload;
    }

    public int nextTimer2OverflowSingle() {
        int eventTime = 0x7FFFFFFF;
        if (timer2Enabled) {
            if (timer2CountUp) {
                int countUntilReload = 0x10000 - timer2Counter;
                eventTime = nextTimer1Overflow(countUntilReload);
            } else {
                eventTime = nextTimer2OverflowBase();
            }
        }
        return eventTime;
    }

    public int nextTimer1Overflow(int numOverflows) {
        int eventTime = 0x7FFFFFFF;
        if (timer1Enabled) {
            if (timer1CountUp) {
                int countUntilReload = 0x10000 - timer1Counter;
                eventTime = nextTimer0Overflow(countUntilReload + numOverflows);
            } else {
                numOverflows -= 1;
                int countUntilReload = nextTimer1OverflowBase();
                int reloadClocks = 0x10000 - timer1Reload;
                reloadClocks *= timer1Prescalar;
                reloadClocks *= numOverflows;
                eventTime = Math.min(countUntilReload + reloadClocks, 0x7FFFFFFF);
            }
        }
        return eventTime;
    }

    public int nextTimer2OverflowBase() {
        int countUntilReload = 0x10000 - timer2Counter;
        countUntilReload *= timer2Prescalar;
        countUntilReload -= timer2Precounter;
        return countUntilReload;
    }

    public int nextTimer3OverflowSingle() {
        int eventTime = 0x7FFFFFFF;
        if (timer3Enabled) {
            if (timer3CountUp) {
                int countUntilReload = 0x10000 - timer3Counter;
                eventTime = nextTimer2Overflow(countUntilReload);
            } else {
                eventTime = nextTimer3OverflowBase();
            }
        }
        return eventTime;
    }

    public int nextTimer2Overflow(int numOverflows) {
        int eventTime = 0x7FFFFFFF;
        if (timer2Enabled) {
            if (timer2CountUp) {
                int countUntilReload = 0x10000 - timer2Counter;
                eventTime = nextTimer1Overflow(countUntilReload + numOverflows);
            } else {
                numOverflows -= 1;
                int countUntilReload = nextTimer2OverflowBase();
                int reloadClocks = 0x10000 - timer2Reload;
                reloadClocks *= timer2Prescalar;
                reloadClocks *= numOverflows;
                eventTime = Math.min(countUntilReload + reloadClocks, 0x7FFFFFFF);
            }
        }
        return eventTime;
    }

    public int nextTimer3OverflowBase() {
        int countUntilReload = 0x10000 - timer3Counter;
        countUntilReload *= timer3Prescalar;
        countUntilReload -= timer3Precounter;
        return countUntilReload;
    }

    public void preprocessTimer1() {
        timer1UseMainClocks = timer1Enabled && !timer1CountUp;
        timer1UseChainedClocks = timer1Enabled && timer1CountUp;
    }

    public void preprocessTimer2() {
        timer2UseMainClocks = timer2Enabled && !timer2CountUp;
        timer2UseChainedClocks = timer2Enabled && timer2CountUp;
    }

    public void preprocessTimer3() {
        timer3UseMainClocks = timer3Enabled && !timer3CountUp;
        timer3UseChainedClocks = timer3Enabled && timer3CountUp;
    }

    public int nextTimer0IRQEventTime() {
        if (timer0Enabled && timer0IRQ) {
            return nextTimer0OverflowSingle();
        }
        return 0x7FFFFFFF;
    }

    public int nextTimer1IRQEventTime() {
        if (timer1Enabled && timer1IRQ) {
            return nextTimer1OverflowSingle();
        }
        return 0x7FFFFFFF;
    }

    public int nextTimer2IRQEventTime() {
        if (timer2Enabled && timer2IRQ) {
            return nextTimer2OverflowSingle();
        }
        return 0x7FFFFFFF;
    }

    public int nextTimer3IRQEventTime() {
        if (timer3Enabled && timer3IRQ) {
            return nextTimer3OverflowSingle();
        }
        return 0x7FFFFFFF;
    }
}
