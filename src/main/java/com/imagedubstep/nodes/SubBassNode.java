package com.imagedubstep.nodes;

import com.imagedubstep.core.*;
import com.imagedubstep.util.Biquad;

public class SubBassNode extends OneShotNode {
    private final double freqHz;      // fundamental
    private final double volume;      // 0..1
    private final double attack;      // seconds (anti-click / duck)
    private final double decay;       // seconds
    private final double harmonicMix; // 0..1 (2nd harmonic)
    private final double drive;       // 1..3.5 soft-sat
    private final double stereoWidth; // 0..~0.6 (small L/R phase skew)

    // Use separate HP filters per channel (avoid state sharing!)
    private final Biquad hpL;
    private final Biquad hpR;

    private double phase = 0.0; // base phase
    private static final double TWO_PI = 2.0 * Math.PI;

    // Back-compat ctor
    public SubBassNode(AudioContext ctx, double start, double decay, double freqHz, double volume) {
        this(ctx, start, decay, freqHz, volume,
             0.05,   // attack
             0.35,   // harmonic mix
             1.0,    // drive
             0.0);   // stereo width
    }

    public SubBassNode(AudioContext ctx, double start, double decay, double freqHz, double volume,
                       double attack, double harmonicMix, double drive, double stereoWidth) {
        super(ctx, start, Math.max(0.12, decay));
        this.freqHz      = clamp(freqHz, 20.0, 140.0);
        this.volume      = clamp(volume, 0.0, 1.0);
        this.attack      = clamp(attack, 0.0, 0.060); // cap 60ms
        this.decay       = Math.max(0.12, decay);
        this.harmonicMix = clamp(harmonicMix, 0.0, 1.0);
        this.drive       = clamp(drive, 1.0, 3.5);
        this.stereoWidth = clamp(stereoWidth, 0.0, 0.75);

        hpL = new Biquad(ctx.getSampleRate());
        hpR = new Biquad(ctx.getSampleRate());
        hpL.set(Biquad.Type.HIGHPASS, 20.0, 0.707);
        hpR.set(Biquad.Type.HIGHPASS, 20.0, 0.707);
    }

    @Override
    public void process(float[] L, float[] R, int n) {
        final double sr   = ctx.getSampleRate();
        final double tNow = ctx.currentTime();

        int startIndex = (int)Math.round((startTime - tNow) * sr);
        int endIndex   = (int)Math.round((stopTime  - tNow) * sr);
        if (endIndex <= 0) return;
        int from = Math.max(0, startIndex);
        int to   = Math.min(n, endIndex);
        if (from >= to) return;

        float[] l = new float[n];
        float[] r = new float[n];

        // Stereo width as small phase skew (radians)
        final double phaseSkew = stereoWidth * (Math.PI / 8.0); // up to ~22.5°
        final double dp = TWO_PI * freqHz / sr;

        // Final micro fade to GUARANTEE zero at note end (prevents clicks)
        final double fadeOut = Math.min(0.005, decay * 0.15); // 5 ms or 15% of note

        for (int i = from; i < to; i++) {
            final double tAbs = tNow + i / sr;
            final double rel  = tAbs - startTime;

            // Attack: smooth ramp (acts like duck if you set a larger attack)
            double envAtk = (attack <= 1e-4) ? 1.0 : Math.min(1.0, rel / attack);
            envAtk *= envAtk; // ease-in^2

            // Decay: gentle exponential towards -∞
            double x = clamp01(rel / decay);
            double envDec = Math.exp(-x * 4.5); // ~ -40 dB near end

            double env = envAtk * envDec;

            // Final cosine fade to hit EXACTLY zero at stopTime
            double tLeft = stopTime - tAbs;
            if (tLeft <= fadeOut && tLeft > 0.0) {
                double w = tLeft / fadeOut;               // 1 -> 0
                env *= 0.5 * (1.0 - Math.cos(Math.PI*w)); // 1 -> 0
            } else if (tLeft <= 0.0) {
                env = 0.0; // safety
            }

            // Advance & wrap phase (keep arguments small for sin(); avoids precision loss)
            phase += dp;
            if (phase >= TWO_PI) phase -= TWO_PI;
            double phaseL = phase;
            double phaseR = phase + phaseSkew;
            if (phaseR >= TWO_PI) phaseR -= TWO_PI;

            // Core: fundamental + a bit of 2nd harmonic
            double sL = Math.sin(phaseL);
            double sR = Math.sin(phaseR);
            double hL = Math.sin(phaseL + phaseL); // 2*phase
            double hR = Math.sin(phaseR + phaseR);

            double coreL = (1.0 - harmonicMix) * sL + harmonicMix * 0.5 * hL;
            double coreR = (1.0 - harmonicMix) * sR + harmonicMix * 0.5 * hR;

            // Soft saturation; stays linear at small env, musical at large env
            double yL = Math.tanh(drive * volume * env * coreL);
            double yR = Math.tanh(drive * volume * env * coreR);

            l[i] += (float) yL;
            r[i] += (float) yR;
        }

        // 20 Hz HP per channel (no shared state)
        hpL.process(l, l, n);
        hpR.process(r, r, n);

        for (int i = 0; i < n; i++) {
            L[i] += l[i];
            R[i] += r[i];
        }

        if (tNow + n / sr >= stopTime) done = true;
    }

    private static double clamp(double v, double lo, double hi) {
        return (v < lo) ? lo : (v > hi) ? hi : v;
    }
    private static double clamp01(double x) {
        return (x < 0.0) ? 0.0 : (x > 1.0) ? 1.0 : x;
    }
}
