package com.imagedubstep.engine2;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BeatSequencer implements AutoCloseable {
    private final ScheduledExecutorService exec;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile int bpm = 140;           // beats per minute
    private volatile int subdivision = 4;     // 4 = 16th notes (4 steps per beat)
    private volatile BeatPattern pattern;
    private final NoteSink sink;

    // Bookkeeping
    private int stepIndex = 0;
    private long nextTickNanos;

    public BeatSequencer(NoteSink sink) {
        this.sink = Objects.requireNonNull(sink, "NoteSink required");
        // Single high-priority scheduler thread
        this.exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BeatSequencer");
            t.setDaemon(true);
            try { t.setPriority(Math.min(Thread.NORM_PRIORITY + 1, Thread.MAX_PRIORITY)); } catch (Exception ignored) {}
            return t;
        });
    }

    /** Replace pattern safely while running. */
    public void setPattern(BeatPattern pattern) {
        this.pattern = pattern;
        if (pattern != null) stepIndex = stepIndex % Math.max(1, pattern.steps);
    }

    public void setBpm(int bpm) {
        if (bpm <= 0) throw new IllegalArgumentException("bpm must be > 0");
        this.bpm = bpm;
    }

    /** Steps per beat: 1=quarter, 2=eighths, 4=sixteenths, etc. */
    public void setSubdivision(int subdivision) {
        if (subdivision <= 0) throw new IllegalArgumentException("subdivision must be > 0");
        this.subdivision = subdivision;
    }

    public boolean isRunning() { return running.get(); }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        stepIndex = 0;
        nextTickNanos = System.nanoTime();
        final long periodNanos = stepPeriodNanos();

        exec.scheduleAtFixedRate(() -> {
            if (!running.get()) return;
            long now = System.nanoTime();

            // If we're late/early, nudge next tick to reduce drift.
            long dt = now - nextTickNanos;
            if (Math.abs(dt) > periodNanos / 4) {
                nextTickNanos = now; // big jump: reanchor
            }
            tick(now);
            nextTickNanos += periodNanos;
        }, 0, periodNanos, TimeUnit.NANOSECONDS);
    }

    public void stop() {
        running.set(false);
    }

    private long stepPeriodNanos() {
        // 1 beat = 60/bpm seconds; step = beat/subdivision
        double secPerStep = (60.0 / bpm) / subdivision;
        return (long) (secPerStep * 1_000_000_000L);
    }

    private void tick(long whenNanos) {
        BeatPattern p = this.pattern;
        if (p == null || p.steps == 0) return;

        // trigger for each instrument if active
        fireIf(p.kick,  p.kickVel,  Instrument.KICK,  whenNanos);
        fireIf(p.snare, p.snareVel, Instrument.SNARE, whenNanos);
        fireIf(p.hihat, p.hihatVel, Instrument.HIHAT, whenNanos);
        fireIf(p.bass,  p.bassVel,  Instrument.BASS,  whenNanos);

        stepIndex = (stepIndex + 1) % p.steps;
    }

    private void fireIf(boolean[] lane, double[] vel, Instrument inst, long whenNanos) {
        if (lane[stepIndex]) {
            double v = (vel != null && vel.length > stepIndex) ? vel[stepIndex] : 1.0;
            sink.trigger(inst, stepIndex, v, whenNanos);
        }
    }

    @Override public void close() {
        stop();
        exec.shutdownNow();
    }
}