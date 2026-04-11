package com.imagedubstep.util;

/** Minimal polyBLEP helpers: band-limits discontinuities for saw/square. */
public final class PolyBLEP {

    private PolyBLEP() {}

    /** polyBLEP correction for a discontinuity at t=0 (phase 0..1), dt=f/sr */
    public static double polyBLEP(double t, double dt) {
        if (t < dt) {
            t /= dt;
            return t + t - t*t - 1.0;           // 2t - t^2 - 1
        } else if (t > 1.0 - dt) {
            t = (t - 1.0) / dt;
            return t*t + t + t + 1.0;          // t^2 + 2t + 1
        }
        return 0.0;
    }

    /** One step of a polyBLEP saw oscillator. Keeps phase in [0,1). */
    public static double sawStep(Phase p, double freq, double sr) {
        double dt = freq / sr;
        double t  = p.value;
        double y  = 2.0 * t - 1.0;             // naïve saw
        y -= polyBLEP(t, dt);                  // fix reset at t=0
        t += dt;
        if (t >= 1.0) t -= 1.0;
        p.value = t;
        return y;
    }

    /** One step of a polyBLEP square osc (50% duty). */
    public static double squareStep(Phase p, double freq, double sr) {
        double dt = freq / sr;
        double t  = p.value;
        double y  = (t < 0.5) ? 1.0 : -1.0;    // naïve square
        y += polyBLEP(t, dt);                  // rising edge at t=0
        double tFall = t + 0.5;
        if (tFall >= 1.0) tFall -= 1.0;
        y -= polyBLEP(tFall, dt);              // falling edge at t=0.5
        t += dt;
        if (t >= 1.0) t -= 1.0;
        p.value = t;
        return y;
    }

    /** Mutable phase holder so we can advance phase in helpers. */
    public static final class Phase { public double value; }
}
