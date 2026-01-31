package com.backend.testutil;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

public class MutableClock extends Clock {

    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    public MutableClock(Instant initial, ZoneId zone) {
        this.now = new AtomicReference<>(initial);
        this.zone = zone;
    }

    public void set(Instant instant) {
        now.set(instant);
    }

    public void plus(Duration d) {
        now.updateAndGet(i -> i.plus(d));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(now.get(), zone);
    }

    @Override
    public Instant instant() {
        return now.get();
    }
}
