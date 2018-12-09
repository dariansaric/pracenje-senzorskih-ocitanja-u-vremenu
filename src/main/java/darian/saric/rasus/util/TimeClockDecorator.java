package darian.saric.rasus.util;

import darian.saric.rasus.network.EmulatedSystemClock;

public class TimeClockDecorator {
    private EmulatedSystemClock emulatedSystemClock;
    private long offset = 0;

    public TimeClockDecorator(EmulatedSystemClock emulatedSystemClock) {
        this.emulatedSystemClock = emulatedSystemClock;
    }

//    public EmulatedSystemClock getEmulatedSystemClock() {
//        return emulatedSystemClock;
//    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public long currentTimeMillis() {
        return emulatedSystemClock.currentTimeMillis() + offset;
    }
}
